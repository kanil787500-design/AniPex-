package com.example.engine3d

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ParticleSystem3D(private val maxParticles: Int = 1500) {

  // x, y, z, vx, vy, vz, life, maxLife, size, r, g, b, a
  private val px = FloatArray(maxParticles)
  private val py = FloatArray(maxParticles)
  private val pz = FloatArray(maxParticles)
  private val vx = FloatArray(maxParticles)
  private val vy = FloatArray(maxParticles)
  private val vz = FloatArray(maxParticles)
  private val life = FloatArray(maxParticles)
  private val maxLife = FloatArray(maxParticles)
  private val size = FloatArray(maxParticles)
  private val cr = FloatArray(maxParticles)
  private val cg = FloatArray(maxParticles)
  private val cb = FloatArray(maxParticles)
  private val ca = FloatArray(maxParticles)
  private val gravity = FloatArray(maxParticles)

  private var cursor = 0
  private var activeCount = 0

  // Interleaved upload buffer: x, y, z, size, r, g, b, a (8 floats)
  private val gpuFloats = FloatArray(maxParticles * 8)
  private val gpuBuffer: FloatBuffer =
    ByteBuffer.allocateDirect(gpuFloats.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
  private var vboId = 0
  private var vaoId = 0

  fun initGl() {
    val vaos = IntArray(1)
    val vbos = IntArray(1)
    GLES30.glGenVertexArrays(1, vaos, 0)
    GLES30.glGenBuffers(1, vbos, 0)
    vaoId = vaos[0]
    vboId = vbos[0]

    GLES30.glBindVertexArray(vaoId)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, gpuFloats.size * 4, null, GLES30.GL_DYNAMIC_DRAW)

    val stride = 8 * 4
    // 0: pos
    GLES30.glEnableVertexAttribArray(0)
    GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
    // 1: size
    GLES30.glEnableVertexAttribArray(1)
    GLES30.glVertexAttribPointer(1, 1, GLES30.GL_FLOAT, false, stride, 3 * 4)
    // 2: color
    GLES30.glEnableVertexAttribArray(2)
    GLES30.glVertexAttribPointer(2, 4, GLES30.GL_FLOAT, false, stride, 4 * 4)

    GLES30.glBindVertexArray(0)
  }

  fun spawn(
    x: Float,
    y: Float,
    z: Float,
    vxVal: Float,
    vyVal: Float,
    vzVal: Float,
    lifetime: Float,
    pSize: Float,
    r: Float,
    g: Float,
    b: Float,
    a: Float = 1f,
    grav: Float = 0f,
  ) {
    val idx = cursor
    cursor = (cursor + 1) % maxParticles
    px[idx] = x; py[idx] = y; pz[idx] = z
    vx[idx] = vxVal; vy[idx] = vyVal; vz[idx] = vzVal
    life[idx] = lifetime; maxLife[idx] = lifetime
    size[idx] = pSize
    cr[idx] = r; cg[idx] = g; cb[idx] = b; ca[idx] = a
    gravity[idx] = grav
  }

  fun emitSlashBurst(
    cx: Float,
    cy: Float,
    cz: Float,
    yawDeg: Float,
    r: Float,
    g: Float,
    b: Float,
    count: Int = 26,
  ) {
    val yawRad = yawDeg * Math3D.DEG_TO_RAD
    for (i in 0 until count) {
      val arcAngle = yawRad + (i.toFloat() / count - 0.5f) * 2.2f
      val dist = 1.6f + Random.nextFloat() * 1.1f
      val sx = cx + kotlin.math.sin(arcAngle) * dist
      val sz = cz + kotlin.math.cos(arcAngle) * dist
      val sy = cy + 0.8f + (Random.nextFloat() - 0.4f) * 0.8f
      spawn(
        sx, sy, sz,
        kotlin.math.sin(arcAngle) * 4.2f,
        1.2f + Random.nextFloat() * 2.5f,
        kotlin.math.cos(arcAngle) * 4.2f,
        0.38f + Random.nextFloat() * 0.25f,
        0.34f + Random.nextFloat() * 0.22f,
        r, g, b, 1f, -2.5f,
      )
    }
  }

  fun emitElementalVortex(cx: Float, cy: Float, cz: Float, r: Float, g: Float, b: Float, count: Int = 65) {
    for (i in 0 until count) {
      val a = (i.toFloat() / count) * 2f * PI.toFloat() * 2f
      val rad = 1.0f + (i.toFloat() / count) * 4.8f
      spawn(
        cx + cos(a) * rad,
        cy + 0.3f + (i % 5) * 0.35f,
        cz + sin(a) * rad,
        -sin(a) * 6.0f,
        3.5f + Random.nextFloat() * 3.0f,
        cos(a) * 6.0f,
        0.7f + Random.nextFloat() * 0.5f,
        0.45f + Random.nextFloat() * 0.3f,
        r, g, b, 1f, -1.5f,
      )
    }
  }

  fun emitShockwaveRing(cx: Float, cy: Float, cz: Float, radius: Float, r: Float, g: Float, b: Float, count: Int = 72) {
    for (i in 0 until count) {
      val a = (i.toFloat() / count) * 2f * PI.toFloat()
      val speed = 6.5f + Random.nextFloat() * 3.5f
      spawn(
        cx + cos(a) * 0.6f,
        cy + 0.25f,
        cz + sin(a) * 0.6f,
        cos(a) * speed * (radius / 5f),
        0.8f + Random.nextFloat() * 1.4f,
        sin(a) * speed * (radius / 5f),
        0.65f + Random.nextFloat() * 0.35f,
        0.52f,
        r, g, b, 1f, -1.2f,
      )
    }
  }

  fun updateAndDraw(dt: Float, vpMatrix: FloatArray, screenWidth: Float, shaderProgram: Int) {
    var outIdx = 0
    activeCount = 0
    for (i in 0 until maxParticles) {
      val l = life[i]
      if (l <= 0f) continue
      val nextL = l - dt
      life[i] = nextL
      if (nextL <= 0f) continue

      vy[i] += gravity[i] * dt
      px[i] += vx[i] * dt
      py[i] += vy[i] * dt
      pz[i] += vz[i] * dt

      val ratio = (nextL / maxLife[i]).coerceIn(0f, 1f)
      gpuFloats[outIdx++] = px[i]
      gpuFloats[outIdx++] = py[i]
      gpuFloats[outIdx++] = pz[i]
      gpuFloats[outIdx++] = size[i] * (0.4f + 0.6f * ratio)
      gpuFloats[outIdx++] = cr[i]
      gpuFloats[outIdx++] = cg[i]
      gpuFloats[outIdx++] = cb[i]
      gpuFloats[outIdx++] = ca[i] * ratio
      activeCount++
    }

    if (activeCount == 0 || vaoId == 0) return

    gpuBuffer.clear()
    gpuBuffer.put(gpuFloats, 0, activeCount * 8)
    gpuBuffer.position(0)

    GLES30.glUseProgram(shaderProgram)
    val vpLoc = GLES30.glGetUniformLocation(shaderProgram, "uVP")
    val scaleLoc = GLES30.glGetUniformLocation(shaderProgram, "uScreenScale")
    GLES30.glUniformMatrix4fv(vpLoc, 1, false, vpMatrix, 0)
    GLES30.glUniform1f(scaleLoc, screenWidth * 0.45f)

    GLES30.glEnable(GLES30.GL_BLEND)
    GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
    GLES30.glDepthMask(false)

    GLES30.glBindVertexArray(vaoId)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vboId)
    GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, activeCount * 8 * 4, gpuBuffer)
    GLES30.glDrawArrays(GLES30.GL_POINTS, 0, activeCount)
    GLES30.glBindVertexArray(0)

    GLES30.glDepthMask(true)
    GLES30.glDisable(GLES30.GL_BLEND)
  }
}
