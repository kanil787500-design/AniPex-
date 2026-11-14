package com.example.engine3d

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class GpuMesh(
  val vao: Int,
  val indexCount: Int,
) {
  fun draw() {
    GLES30.glBindVertexArray(vao)
    GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)
    GLES30.glBindVertexArray(0)
  }
}

object MeshBuilder {
  // Stride: pos(3) + normal(3) + uv(2) + color(4) = 12 floats = 48 bytes
  private const val FLOATS_PER_VERTEX = 12
  private const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4

  private fun uploadMesh(vertices: FloatArray, indices: IntArray): GpuMesh {
    val vaos = IntArray(1)
    val vbos = IntArray(1)
    val ibos = IntArray(1)
    GLES30.glGenVertexArrays(1, vaos, 0)
    GLES30.glGenBuffers(1, vbos, 0)
    GLES30.glGenBuffers(1, ibos, 0)

    val vao = vaos[0]
    GLES30.glBindVertexArray(vao)

    val vBuf: FloatBuffer =
      ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    vBuf.put(vertices).position(0)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[0])
    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vBuf, GLES30.GL_STATIC_DRAW)

    val iBuf: IntBuffer =
      ByteBuffer.allocateDirect(indices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
    iBuf.put(indices).position(0)
    GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibos[0])
    GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, iBuf, GLES30.GL_STATIC_DRAW)

    GLES30.glEnableVertexAttribArray(0)
    GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, STRIDE_BYTES, 0)
    GLES30.glEnableVertexAttribArray(1)
    GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, STRIDE_BYTES, 3 * 4)
    GLES30.glEnableVertexAttribArray(2)
    GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, STRIDE_BYTES, 6 * 4)
    GLES30.glEnableVertexAttribArray(3)
    GLES30.glVertexAttribPointer(3, 4, GLES30.GL_FLOAT, false, STRIDE_BYTES, 8 * 4)

    GLES30.glBindVertexArray(0)
    return GpuMesh(vao, indices.size)
  }

  fun createFullscreenQuadVao(): Int {
    val data =
      floatArrayOf(
        -1f, -1f, 0f, 0f,
        1f, -1f, 1f, 0f,
        1f, 1f, 1f, 1f,
        -1f, 1f, 0f, 1f,
      )
    val idx = intArrayOf(0, 1, 2, 0, 2, 3)
    val vaos = IntArray(1); val vbos = IntArray(1); val ibos = IntArray(1)
    GLES30.glGenVertexArrays(1, vaos, 0)
    GLES30.glGenBuffers(1, vbos, 0)
    GLES30.glGenBuffers(1, ibos, 0)
    GLES30.glBindVertexArray(vaos[0])

    val vBuf = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    vBuf.put(data).position(0)
    GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[0])
    GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, vBuf, GLES30.GL_STATIC_DRAW)

    val iBuf = ByteBuffer.allocateDirect(idx.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
    iBuf.put(idx).position(0)
    GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, ibos[0])
    GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, idx.size * 4, iBuf, GLES30.GL_STATIC_DRAW)

    GLES30.glEnableVertexAttribArray(0)
    GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0)
    GLES30.glEnableVertexAttribArray(1)
    GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, 8)
    GLES30.glBindVertexArray(0)
    return vaos[0]
  }

  fun createOpenWorldTerrain(gridSize: Int = 152, worldSpan: Float = 220f): GpuMesh {
    val vertsPerRow = gridSize + 1
    val verts = FloatArray(vertsPerRow * vertsPerRow * FLOATS_PER_VERTEX)
    val indices = IntArray(gridSize * gridSize * 6)
    val half = worldSpan * 0.5f
    val step = worldSpan / gridSize

    var vi = 0
    for (gz in 0..gridSize) {
      val z = -half + gz * step
      for (gx in 0..gridSize) {
        val x = -half + gx * step
        val y = Math3D.openWorldHeight(x, z)
        val n = Math3D.openWorldNormal(x, z)
        val dist = sqrt(x * x + z * z)
        val slope = 1f - n[1]

        // Vibrant Genshin/Wuthering meadow palette with sandy shoreline & paved ruins
        var r = 0.40f
        var g = 0.80f
        var b = 0.42f
        if (dist < 15f) {
          val t = ((dist - 11f) / 4f).coerceIn(0f, 1f)
          r = Math3D.lerp(0.78f, 0.40f, t)
          g = Math3D.lerp(0.81f, 0.80f, t)
          b = Math3D.lerp(0.88f, 0.42f, t)
        } else if (y < -1.1f) {
          // Sandy / turquoise lakebed
          val t = ((-y - 1.1f) / 2.2f).coerceIn(0f, 1f)
          r = Math3D.lerp(0.42f, 0.72f, t)
          g = Math3D.lerp(0.78f, 0.74f, t)
          b = Math3D.lerp(0.46f, 0.56f, t)
        } else if (slope > 0.20f || y > 9f) {
          val t = ((slope - 0.16f) * 2.5f).coerceIn(0f, 1f)
          r = Math3D.lerp(0.44f, 0.50f, t)
          g = Math3D.lerp(0.76f, 0.53f, t)
          b = Math3D.lerp(0.46f, 0.62f, t)
        }

        verts[vi++] = x; verts[vi++] = y; verts[vi++] = z
        verts[vi++] = n[0]; verts[vi++] = n[1]; verts[vi++] = n[2]
        verts[vi++] = gx * 0.28f; verts[vi++] = gz * 0.28f
        verts[vi++] = r; verts[vi++] = g; verts[vi++] = b; verts[vi++] = 1f
      }
    }

    var ii = 0
    for (gz in 0 until gridSize) {
      for (gx in 0 until gridSize) {
        val tl = gz * vertsPerRow + gx
        val tr = tl + 1
        val bl = tl + vertsPerRow
        val br = bl + 1
        indices[ii++] = tl; indices[ii++] = bl; indices[ii++] = tr
        indices[ii++] = tr; indices[ii++] = bl; indices[ii++] = br
      }
    }
    return uploadMesh(verts, indices)
  }

  fun createWaterDisc(radius: Float = 24f, rings: Int = 20, sectors: Int = 44): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for (r in 0..rings) {
      val frac = r.toFloat() / rings
      val rad = frac * radius
      for (s in 0..sectors) {
        val a = (s.toFloat() / sectors) * 2f * PI.toFloat()
        val x = cos(a) * rad
        val z = sin(a) * rad
        val u = 0.5f + cos(a) * frac * 0.5f
        val v = 0.5f + sin(a) * frac * 0.5f
        verts.addAll(listOf(x, 0f, z, 0f, 1f, 0f, u, v, 1f, 1f, 1f, 1f))
      }
    }
    val stride = sectors + 1
    for (r in 0 until rings) {
      for (s in 0 until sectors) {
        val a = r * stride + s
        val b = a + stride
        indices.addAll(listOf(a, a + 1, b, a + 1, b + 1, b))
      }
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  fun createArenaFloor(radius: Float = 26f, segments: Int = 64): GpuMesh {
    val verts = FloatArray((segments + 2) * FLOATS_PER_VERTEX)
    val indices = IntArray(segments * 3)
    var vi = 0
    verts[vi++] = 0f; verts[vi++] = 0f; verts[vi++] = 0f
    verts[vi++] = 0f; verts[vi++] = 1f; verts[vi++] = 0f
    verts[vi++] = 0.5f; verts[vi++] = 0.5f
    verts[vi++] = 0.78f; verts[vi++] = 0.84f; verts[vi++] = 1.0f; verts[vi++] = 1f

    for (i in 0..segments) {
      val a = (i.toFloat() / segments) * (2f * PI.toFloat())
      val cx = cos(a) * radius
      val cz = sin(a) * radius
      verts[vi++] = cx; verts[vi++] = 0f; verts[vi++] = cz
      verts[vi++] = 0f; verts[vi++] = 1f; verts[vi++] = 0f
      verts[vi++] = 0.5f + cos(a) * 4f; verts[vi++] = 0.5f + sin(a) * 4f
      verts[vi++] = 0.55f; verts[vi++] = 0.64f; verts[vi++] = 0.90f; verts[vi++] = 1f
    }

    var ii = 0
    for (i in 1..segments) {
      indices[ii++] = 0; indices[ii++] = i + 1; indices[ii++] = i
    }
    return uploadMesh(verts, indices)
  }

  fun createShadowQuad(): GpuMesh {
    val verts =
      floatArrayOf(
        -0.5f, 0f, 0.5f, 0f, 1f, 0f, 0f, 1f, 1f, 1f, 1f, 1f,
        0.5f, 0f, 0.5f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f, 1f,
        0.5f, 0f, -0.5f, 0f, 1f, 0f, 1f, 0f, 1f, 1f, 1f, 1f,
        -0.5f, 0f, -0.5f, 0f, 1f, 0f, 0f, 0f, 1f, 1f, 1f, 1f,
      )
    val idx = intArrayOf(0, 1, 2, 0, 2, 3)
    return uploadMesh(verts, idx)
  }

  fun createSkyDome(radius: Float = 190f, rings: Int = 20, sectors: Int = 32): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for (r in 0..rings) {
      val phi = (r.toFloat() / rings) * PI.toFloat()
      val y = cos(phi) * radius
      val ringR = sin(phi) * radius
      for (s in 0..sectors) {
        val theta = (s.toFloat() / sectors) * 2f * PI.toFloat()
        val x = cos(theta) * ringR
        val z = sin(theta) * ringR
        verts.addAll(listOf(x, y, z, 0f, 1f, 0f, s.toFloat() / sectors, r.toFloat() / rings, 1f, 1f, 1f, 1f))
      }
    }
    val stride = sectors + 1
    for (r in 0 until rings) {
      for (s in 0 until sectors) {
        val a = r * stride + s
        val b = a + stride
        indices.addAll(listOf(a, b, a + 1, a + 1, b, b + 1))
      }
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  fun createSphere(radius: Float = 0.5f, rings: Int = 20, sectors: Int = 28): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for (r in 0..rings) {
      val v = r.toFloat() / rings
      val phi = v * PI.toFloat()
      val ny = cos(phi)
      val sinPhi = sin(phi)
      for (s in 0..sectors) {
        val u = s.toFloat() / sectors
        val theta = u * 2f * PI.toFloat()
        val nx = cos(theta) * sinPhi
        val nz = sin(theta) * sinPhi
        verts.addAll(listOf(nx * radius, ny * radius, nz * radius, nx, ny, nz, u, v, 1f, 1f, 1f, 1f))
      }
    }
    val stride = sectors + 1
    for (r in 0 until rings) {
      for (s in 0 until sectors) {
        val a = r * stride + s
        val b = a + stride
        indices.addAll(listOf(a, a + 1, b, a + 1, b + 1, b))
      }
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  /**
   * Studio-sculpted anime head with smooth front eye/cheek plane, 3D anime nose ridge,
   * tapered V-chin jawline, and front-projected (+Z) UVs calibrated for 2048px starlight eyes.
   */
  fun createAnimeHead(radius: Float = 0.5f, rings: Int = 34, sectors: Int = 40): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for (r in 0..rings) {
      val vFrac = r.toFloat() / rings
      val phi = vFrac * PI.toFloat()
      val ny = cos(phi)
      val sinPhi = sin(phi)
      val jawPinch =
        when {
          ny < -0.10f -> (1.0f + (ny + 0.10f) * 0.38f).coerceAtLeast(0.52f)
          else -> 1.0f
        }
      for (s in 0..sectors) {
        val uFrac = s.toFloat() / sectors
        val theta = uFrac * 2f * PI.toFloat()
        val nx = cos(theta) * sinPhi * jawPinch
        val nzBase = sin(theta) * sinPhi

        // Smooth eye/cheek front plane + gentle chin tuck + sculpted anime nose bridge
        var nz =
          when {
            ny < -0.24f && nzBase > 0f -> nzBase * 0.86f
            nzBase > 0.14f && ny in -0.20f..0.38f -> nzBase * 0.91f
            nzBase < 0f -> nzBase * 1.06f
            else -> nzBase
          }
        if (nzBase > 0.65f && kotlin.math.abs(nx) < 0.12f && ny in -0.22f..0.06f) {
          val noseProfileX = cos((nx / 0.12f) * (PI.toFloat() * 0.5f)).coerceAtLeast(0f)
          val noseProfileY = (1f - kotlin.math.abs(ny + 0.10f) / 0.16f).coerceIn(0f, 1f)
          nz += 0.062f * noseProfileX * noseProfileY
        }

        val nLen = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.001f)
        val faceU = if (nzBase > 0.04f) (0.50f + nx * 0.52f).coerceIn(0.04f, 0.96f) else 0.04f
        val faceV = if (nzBase > 0.04f) (0.49f - ny * 0.55f).coerceIn(0.04f, 0.96f) else 0.04f
        verts.addAll(
          listOf(
            nx * radius, ny * radius, nz * radius,
            nx / nLen, ny / nLen, nz / nLen,
            faceU, faceV,
            1f, 1f, 1f, 1f,
          )
        )
      }
    }
    val stride = sectors + 1
    for (r in 0 until rings) {
      for (s in 0 until sectors) {
        val a = r * stride + s
        val b = a + stride
        indices.addAll(listOf(a, a + 1, b, a + 1, b + 1, b))
      }
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  /** Smooth tapered capsule / limb centered along Y from -0.5 to +0.5 */
  fun createTaperedLimb(rBot: Float = 0.35f, rTop: Float = 0.5f, height: Float = 1f, rings: Int = 8, sectors: Int = 14): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for (r in 0..rings) {
      val v = r.toFloat() / rings
      val y = (0.5f - v) * height
      // Smooth rounded end-caps + tapered waist
      val cap = sin(v * PI.toFloat()).coerceAtLeast(0.25f)
      val rad = Math3D.lerp(rTop, rBot, v) * (0.72f + 0.28f * cap)
      for (s in 0..sectors) {
        val u = s.toFloat() / sectors
        val a = u * 2f * PI.toFloat()
        val nx = cos(a)
        val nz = sin(a)
        verts.addAll(listOf(nx * rad, y, nz * rad, nx, (0.5f - v) * 0.25f, nz, u, v, 1f, 1f, 1f, 1f))
      }
    }
    val stride = sectors + 1
    for (r in 0 until rings) {
      for (s in 0 until sectors) {
        val a = r * stride + s
        val b = a + stride
        indices.addAll(listOf(a, a + 1, b, a + 1, b + 1, b))
      }
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  /** Sculpted anime torso (narrow waist -> athletic chest -> tapered shoulders & collar) */
  fun createSculptedTorso(): GpuMesh {
    val rings = 12
    val sectors = 20
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for (r in 0..rings) {
      val v = r.toFloat() / rings
      val y = 0.5f - v // +0.5 shoulders to -0.5 waist
      val rx =
        when {
          v < 0.16f -> Math3D.lerp(0.23f, 0.53f, v / 0.16f)
          v < 0.64f -> Math3D.lerp(0.53f, 0.32f, (v - 0.16f) / 0.48f)
          else -> Math3D.lerp(0.32f, 0.41f, (v - 0.64f) / 0.36f)
        }
      val rz = rx * (if (v in 0.16f..0.48f) 0.68f else 0.62f)
      for (s in 0..sectors) {
        val u = s.toFloat() / sectors
        val a = u * 2f * PI.toFloat()
        val ca = cos(a); val sa = sin(a)
        verts.addAll(listOf(ca * rx, y, sa * rz, ca, 0.15f * (0.5f - v), sa, u, v, 1f, 1f, 1f, 1f))
      }
    }
    val stride = sectors + 1
    for (r in 0 until rings) {
      for (s in 0 until sectors) {
        val a = r * stride + s
        val b = a + stride
        indices.addAll(listOf(a, a + 1, b, a + 1, b + 1, b))
      }
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  /** Sculpted tapered anime glove/hand centered at wrist top (0,0,0) extending down to -1.0 */
  fun createHandGloveMesh(): GpuMesh {
    val rings = 6
    val sectors = 12
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for (r in 0..rings) {
      val v = r.toFloat() / rings
      val y = -v * 1.0f
      val taper = sin((0.15f + v * 0.85f) * PI.toFloat()).coerceAtLeast(0.18f)
      val rx = (0.48f - v * 0.24f) * taper
      val rz = (0.58f - v * 0.26f) * taper
      for (s in 0..sectors) {
        val u = s.toFloat() / sectors
        val a = u * 2f * PI.toFloat()
        val ca = cos(a); val sa = sin(a)
        verts.addAll(listOf(ca * rx, y, sa * rz, ca, -0.25f * v, sa, u, v, 1f, 1f, 1f, 1f))
      }
    }
    val stride = sectors + 1
    for (r in 0 until rings) {
      for (s in 0 until sectors) {
        val a = r * stride + s
        val b = a + stride
        indices.addAll(listOf(a, a + 1, b, a + 1, b + 1, b))
      }
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  /** Flared pleated anime coat-tail / mantle cone segment */
  fun createFlaredSkirt(rTop: Float = 0.28f, rBot: Float = 0.56f, height: Float = 1f, segments: Int = 20): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for (r in 0..4) {
      val v = r / 4f
      val y = (0.5f - v) * height
      val baseR = Math3D.lerp(rTop, rBot, v)
      for (s in 0..segments) {
        val u = s.toFloat() / segments
        val a = u * 2f * PI.toFloat()
        // Subtle pleated ripple
        val pleat = 1f + sin(a * 8f) * 0.06f * v
        val rad = baseR * pleat
        val nx = cos(a); val nz = sin(a)
        verts.addAll(listOf(nx * rad, y, nz * rad, nx, 0.28f, nz, u, v, 1f, 1f, 1f, 1f))
      }
    }
    val stride = segments + 1
    for (r in 0 until 4) {
      for (s in 0 until segments) {
        val a = r * stride + s
        val b = a + stride
        indices.addAll(listOf(a, a + 1, b, a + 1, b + 1, b))
      }
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  /** Tapered curved anime hair lock / spike */
  fun createHairLock(segments: Int = 8): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    val rings = 6
    for (r in 0..rings) {
      val v = r.toFloat() / rings
      val y = -v * 1.0f // hangs downward from origin
      val zCurve = sin(v * PI.toFloat() * 0.7f) * 0.14f
      val rad = (1f - v * 0.92f) * sin((0.2f + v * 0.8f) * PI.toFloat() * 0.6f).coerceAtLeast(0.08f) * 0.5f
      for (s in 0..segments) {
        val u = s.toFloat() / segments
        val a = u * 2f * PI.toFloat()
        val nx = cos(a); val nz = sin(a)
        verts.addAll(listOf(nx * rad, y, nz * rad * 0.65f + zCurve, nx, -0.2f, nz, u, v, 1f, 1f, 1f, 1f))
      }
    }
    val stride = segments + 1
    for (r in 0 until rings) {
      for (s in 0 until segments) {
        val a = r * stride + s
        val b = a + stride
        indices.addAll(listOf(a, a + 1, b, a + 1, b + 1, b))
      }
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  /** Double-edged beveled anime sword blade from y=0 (hilt) to y=1 (tip) */
  fun createSwordBlade(): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    fun addTri(p0: FloatArray, p1: FloatArray, p2: FloatArray, c: FloatArray) {
      val ux = p1[0] - p0[0]; val uy = p1[1] - p0[1]; val uz = p1[2] - p0[2]
      val vx = p2[0] - p0[0]; val vy = p2[1] - p0[1]; val vz = p2[2] - p0[2]
      var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
      val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
      nx /= len; ny /= len; nz /= len
      val base = verts.size / FLOATS_PER_VERTEX
      for (p in listOf(p0, p1, p2)) {
        verts.addAll(listOf(p[0], p[1], p[2], nx, ny, nz, 0.5f, p[1], c[0], c[1], c[2], 1f))
      }
      indices.addAll(listOf(base, base + 1, base + 2))
    }
    val bL = floatArrayOf(-0.5f, 0f, 0f); val bR = floatArrayOf(0.5f, 0f, 0f)
    val bF = floatArrayOf(0f, 0f, 0.5f); val bB = floatArrayOf(0f, 0f, -0.5f)
    val mL = floatArrayOf(-0.62f, 0.72f, 0f); val mR = floatArrayOf(0.62f, 0.72f, 0f)
    val mF = floatArrayOf(0f, 0.72f, 0.5f); val mB = floatArrayOf(0f, 0.72f, -0.5f)
    val tip = floatArrayOf(0f, 1.0f, 0f)
    val edge = floatArrayOf(1f, 1f, 1f)
    val ridge = floatArrayOf(0.86f, 0.94f, 1f)

    // Lower blade bevels
    addTri(bL, mF, bF, edge); addTri(bL, mL, mF, edge)
    addTri(bF, mR, bR, edge); addTri(bF, mF, mR, edge)
    addTri(bR, mB, bB, edge); addTri(bR, mR, mB, edge)
    addTri(bB, mL, bL, edge); addTri(bB, mB, mL, edge)
    // Pointed tip bevels
    addTri(mL, tip, mF, ridge); addTri(mF, tip, mR, ridge)
    addTri(mR, tip, mB, ridge); addTri(mB, tip, mL, ridge)

    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  /** Wide sweeping 3D crescent ribbon arc for combat slash trails */
  fun createSlashArc(segments: Int = 24): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for (i in 0..segments) {
      val t = i.toFloat() / segments
      val angle = (-0.85f + t * 1.7f) * PI.toFloat() * 0.65f
      val taper = sin(t * PI.toFloat())
      val rIn = 1.15f + (1f - taper) * 0.35f
      val rOut = 2.55f
      val s = sin(angle); val c = cos(angle)
      verts.addAll(listOf(s * rIn, 0f, c * rIn, 0f, 1f, 0f, t, 0f, 1f, 1f, 1f, 0.25f))
      verts.addAll(listOf(s * rOut, 0.08f * taper, c * rOut, 0f, 1f, 0f, t, 1f, 1f, 1f, 1f, taper))
    }
    for (i in 0 until segments) {
      val a = i * 2; val b = a + 1; val c = a + 2; val d = a + 3
      indices.addAll(listOf(a, b, c, c, b, d))
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  /** Crossed 3D stylized grass tuft swaying in the meadow breeze */
  fun createGrassTuft(): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    val blades = 5
    for (i in 0 until blades) {
      val a = (i.toFloat() / blades) * PI.toFloat()
      val dx = cos(a) * 0.22f
      val dz = sin(a) * 0.22f
      val tipX = cos(a + 0.5f) * 0.14f
      val tipZ = sin(a + 0.5f) * 0.14f
      val h = 0.42f + (i % 3) * 0.12f
      val base = verts.size / FLOATS_PER_VERTEX
      verts.addAll(listOf(-dx, 0f, -dz, 0f, 1f, 0f, 0f, 1f, 0.28f, 0.62f, 0.32f, 1f))
      verts.addAll(listOf(dx, 0f, dz, 0f, 1f, 0f, 1f, 1f, 0.28f, 0.62f, 0.32f, 1f))
      verts.addAll(listOf(tipX, h, tipZ, 0f, 1f, 0f, 0.5f, 0f, 0.66f, 0.96f, 0.48f, 1f))
      indices.addAll(listOf(base, base + 1, base + 2))
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  fun createBox(hx: Float = 0.5f, hy: Float = 0.5f, hz: Float = 0.5f): GpuMesh {
    val faces =
      arrayOf(
        floatArrayOf(-hx, -hy, hz, 0f, 0f, 1f, 0f, 1f),
        floatArrayOf(hx, -hy, hz, 0f, 0f, 1f, 1f, 1f),
        floatArrayOf(hx, hy, hz, 0f, 0f, 1f, 1f, 0f),
        floatArrayOf(-hx, hy, hz, 0f, 0f, 1f, 0f, 0f),
        floatArrayOf(hx, -hy, -hz, 0f, 0f, -1f, 0f, 1f),
        floatArrayOf(-hx, -hy, -hz, 0f, 0f, -1f, 1f, 1f),
        floatArrayOf(-hx, hy, -hz, 0f, 0f, -1f, 1f, 0f),
        floatArrayOf(hx, hy, -hz, 0f, 0f, -1f, 0f, 0f),
        floatArrayOf(hx, -hy, hz, 1f, 0f, 0f, 0f, 1f),
        floatArrayOf(hx, -hy, -hz, 1f, 0f, 0f, 1f, 1f),
        floatArrayOf(hx, hy, -hz, 1f, 0f, 0f, 1f, 0f),
        floatArrayOf(hx, hy, hz, 1f, 0f, 0f, 0f, 0f),
        floatArrayOf(-hx, -hy, -hz, -1f, 0f, 0f, 0f, 1f),
        floatArrayOf(-hx, -hy, hz, -1f, 0f, 0f, 1f, 1f),
        floatArrayOf(-hx, hy, hz, -1f, 0f, 0f, 1f, 0f),
        floatArrayOf(-hx, hy, -hz, -1f, 0f, 0f, 0f, 0f),
        floatArrayOf(-hx, hy, hz, 0f, 1f, 0f, 0f, 1f),
        floatArrayOf(hx, hy, hz, 0f, 1f, 0f, 1f, 1f),
        floatArrayOf(hx, hy, -hz, 0f, 1f, 0f, 1f, 0f),
        floatArrayOf(-hx, hy, -hz, 0f, 1f, 0f, 0f, 0f),
        floatArrayOf(-hx, -hy, -hz, 0f, -1f, 0f, 0f, 1f),
        floatArrayOf(hx, -hy, -hz, 0f, -1f, 0f, 1f, 1f),
        floatArrayOf(hx, -hy, hz, 0f, -1f, 0f, 1f, 0f),
        floatArrayOf(-hx, -hy, hz, 0f, -1f, 0f, 0f, 0f),
      )
    val verts = FloatArray(24 * FLOATS_PER_VERTEX)
    var vi = 0
    for (v in faces) {
      for (f in v) verts[vi++] = f
      verts[vi++] = 1f; verts[vi++] = 1f; verts[vi++] = 1f; verts[vi++] = 1f
    }
    val indices = IntArray(36)
    var ii = 0
    for (f in 0 until 6) {
      val base = f * 4
      indices[ii++] = base; indices[ii++] = base + 1; indices[ii++] = base + 2
      indices[ii++] = base; indices[ii++] = base + 2; indices[ii++] = base + 3
    }
    return uploadMesh(verts, indices)
  }

  fun createCone(radius: Float = 0.5f, height: Float = 1.0f, segments: Int = 18): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for (i in 0..segments) {
      val u = i.toFloat() / segments
      val a = u * 2f * PI.toFloat()
      val nx = cos(a); val nz = sin(a)
      val ny = radius / height
      val len = sqrt(nx * nx + ny * ny + nz * nz)
      verts.addAll(listOf(nx * radius, 0f, nz * radius, nx / len, ny / len, nz / len, u, 1f, 1f, 1f, 1f, 1f))
      verts.addAll(listOf(0f, height, 0f, nx / len, ny / len, nz / len, u, 0f, 1f, 1f, 1f, 1f))
    }
    for (i in 0 until segments) {
      val b0 = i * 2; val t0 = b0 + 1; val b1 = (i + 1) * 2
      indices.addAll(listOf(b0, t0, b1))
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  fun createCylinder(radius: Float = 0.3f, height: Float = 1.0f, segments: Int = 16): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for (i in 0..segments) {
      val u = i.toFloat() / segments
      val a = u * 2f * PI.toFloat()
      val nx = cos(a); val nz = sin(a)
      verts.addAll(listOf(nx * radius, 0f, nz * radius, nx, 0f, nz, u, 1f, 1f, 1f, 1f, 1f))
      verts.addAll(listOf(nx * radius, height, nz * radius, nx, 0f, nz, u, 0f, 1f, 1f, 1f, 1f))
    }
    for (i in 0 until segments) {
      val b0 = i * 2; val t0 = b0 + 1; val b1 = b0 + 2; val t1 = b0 + 3
      indices.addAll(listOf(b0, t0, b1, b1, t0, t1))
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  fun createDiamondCrystal(radius: Float = 0.5f, height: Float = 1.4f): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    val sides = 8
    val midY = height * 0.45f
    fun addTri(p0: FloatArray, p1: FloatArray, p2: FloatArray) {
      val ux = p1[0] - p0[0]; val uy = p1[1] - p0[1]; val uz = p1[2] - p0[2]
      val vx = p2[0] - p0[0]; val vy = p2[1] - p0[1]; val vz = p2[2] - p0[2]
      var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
      val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
      nx /= len; ny /= len; nz /= len
      val base = verts.size / FLOATS_PER_VERTEX
      for (p in listOf(p0, p1, p2)) {
        verts.addAll(listOf(p[0], p[1], p[2], nx, ny, nz, 0.5f, 0.5f, 1f, 1f, 1f, 1f))
      }
      indices.addAll(listOf(base, base + 1, base + 2))
    }
    val top = floatArrayOf(0f, height, 0f)
    val bot = floatArrayOf(0f, -height * 0.4f, 0f)
    for (i in 0 until sides) {
      val a0 = (i.toFloat() / sides) * 2f * PI.toFloat()
      val a1 = ((i + 1).toFloat() / sides) * 2f * PI.toFloat()
      val m0 = floatArrayOf(cos(a0) * radius, midY, sin(a0) * radius)
      val m1 = floatArrayOf(cos(a1) * radius, midY, sin(a1) * radius)
      addTri(m0, top, m1)
      addTri(m1, bot, m0)
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  fun createTorusRing(majorR: Float = 1.0f, minorR: Float = 0.08f, majorSeg: Int = 36, minorSeg: Int = 10): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for (i in 0..majorSeg) {
      val u = i.toFloat() / majorSeg
      val theta = u * 2f * PI.toFloat()
      val ct = cos(theta); val st = sin(theta)
      for (j in 0..minorSeg) {
        val v = j.toFloat() / minorSeg
        val phi = v * 2f * PI.toFloat()
        val cp = cos(phi); val sp = sin(phi)
        val x = (majorR + minorR * cp) * ct
        val y = minorR * sp
        val z = (majorR + minorR * cp) * st
        val nx = cp * ct; val ny = sp; val nz = cp * st
        verts.addAll(listOf(x, y, z, nx, ny, nz, u, v, 1f, 1f, 1f, 1f))
      }
    }
    val stride = minorSeg + 1
    for (i in 0 until majorSeg) {
      for (j in 0 until minorSeg) {
        val a = i * stride + j; val b = a + stride
        indices.addAll(listOf(a, b, a + 1, a + 1, b, b + 1))
      }
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  /**
   * Top-joint-pivoted bone limb from y = 0 (parent joint) down to y = -length (child joint)
   * with rounded joint caps so hierarchical limbs bend seamlessly without detaching.
   */
  fun createBoneLimb(
    rTop: Float = 0.50f,
    rBot: Float = 0.36f,
    length: Float = 1.0f,
    rings: Int = 8,
    sectors: Int = 14,
  ): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for (r in 0..rings) {
      val v = r.toFloat() / rings
      val y = -v * length
      val cap = sin(v * PI.toFloat()).coerceAtLeast(0.22f)
      val rad = Math3D.lerp(rTop, rBot, v) * (0.74f + 0.26f * cap)
      for (s in 0..sectors) {
        val u = s.toFloat() / sectors
        val a = u * 2f * PI.toFloat()
        val nx = cos(a)
        val nz = sin(a)
        verts.addAll(listOf(nx * rad, y, nz * rad, nx, (0.5f - v) * 0.25f, nz, u, v, 1f, 1f, 1f, 1f))
      }
    }
    val stride = sectors + 1
    for (r in 0 until rings) {
      for (s in 0 until sectors) {
        val a = r * stride + s
        val b = a + stride
        indices.addAll(listOf(a, a + 1, b, a + 1, b + 1, b))
      }
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  /**
   * Sculpted anime boot pivoted at the ankle joint (0, 0, 0), extending forward (+Z) and slightly down (-Y).
   */
  fun createFootBoot(): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    fun addTri(p0: FloatArray, p1: FloatArray, p2: FloatArray) {
      val ux = p1[0] - p0[0]; val uy = p1[1] - p0[1]; val uz = p1[2] - p0[2]
      val vx = p2[0] - p0[0]; val vy = p2[1] - p0[1]; val vz = p2[2] - p0[2]
      var nx = uy * vz - uz * vy
      var ny = uz * vx - ux * vz
      var nz = ux * vy - uy * vx
      val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
      nx /= len; ny /= len; nz /= len
      val base = verts.size / FLOATS_PER_VERTEX
      for (p in listOf(p0, p1, p2)) {
        verts.addAll(listOf(p[0], p[1], p[2], nx, ny, nz, 0.5f, 0.5f, 1f, 1f, 1f, 1f))
      }
      indices.addAll(listOf(base, base + 1, base + 2))
    }
    fun addQuad(p0: FloatArray, p1: FloatArray, p2: FloatArray, p3: FloatArray) {
      addTri(p0, p1, p2)
      addTri(p0, p2, p3)
    }

    // Boot heel & sole (-Y bottom = -0.11, ankle top = +0.05, heel Z = -0.07, toe Z = +0.23)
    val hbl = floatArrayOf(-0.065f, -0.11f, -0.07f)
    val hbr = floatArrayOf(0.065f, -0.11f, -0.07f)
    val tbl = floatArrayOf(-0.055f, -0.11f, 0.22f)
    val tbr = floatArrayOf(0.055f, -0.11f, 0.22f)

    val htl = floatArrayOf(-0.055f, 0.05f, -0.06f)
    val htr = floatArrayOf(0.055f, 0.05f, -0.06f)
    val mtl = floatArrayOf(-0.060f, 0.02f, 0.08f)
    val mtr = floatArrayOf(0.060f, 0.02f, 0.08f)
    val ttl = floatArrayOf(-0.045f, -0.04f, 0.23f)
    val ttr = floatArrayOf(0.045f, -0.04f, 0.23f)

    // Top ankle cuff & instep & toe cap
    addQuad(htl, mtl, mtr, htr)
    addQuad(mtl, ttl, ttr, mtr)
    // Toe front
    addQuad(ttl, tbl, tbr, ttr)
    // Heel back
    addQuad(htr, hbr, hbl, htl)
    // Left side
    addQuad(htl, hbl, tbl, ttl)
    addTri(htl, ttl, mtl)
    // Right side
    addQuad(ttr, tbr, hbr, htr)
    addTri(ttr, htr, mtr)
    // Bottom sole
    addQuad(tbl, hbl, hbr, tbr)

    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  /**
   * Sculpted multi-frequency displaced rock / tree-canopy cluster with analytical vertex normals.
   */
  fun createBoulder(seed: Int = 11, radius: Float = 1.0f, rings: Int = 14, sectors: Int = 20): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    val phase = seed * 1.37f
    fun displace(nx: Float, ny: Float, nz: Float): Float {
      val n1 = sin(nx * 3.1f + phase) * cos(nz * 2.9f - phase * 0.6f) * 0.16f
      val n2 = cos(ny * 4.2f + nx * 2.7f + phase) * 0.09f
      val n3 = sin((nx - nz) * 5.8f + phase * 1.7f) * 0.05f
      return radius * (1.0f + n1 + n2 + n3)
    }

    for (r in 0..rings) {
      val v = r.toFloat() / rings
      val phi = v * PI.toFloat()
      val ny = cos(phi)
      val sinPhi = sin(phi)
      for (s in 0..sectors) {
        val u = s.toFloat() / sectors
        val theta = u * 2f * PI.toFloat()
        val nx = cos(theta) * sinPhi
        val nz = sin(theta) * sinPhi
        val rad = displace(nx, ny, nz)
        // Approximate perturbed normal slightly upward for pleasant anime foliage/rock cel shading
        val gx = nx + sin(ny * 4f + phase) * 0.14f
        val gy = ny + 0.12f
        val gz = nz + cos(nx * 4f - phase) * 0.14f
        val glen = sqrt(gx * gx + gy * gy + gz * gz).coerceAtLeast(0.001f)
        verts.addAll(
          listOf(
            nx * rad, ny * rad * 0.86f, nz * rad,
            gx / glen, gy / glen, gz / glen,
            u * 1.5f, v * 1.5f,
            1f, 1f, 1f, 1f,
          )
        )
      }
    }
    val stride = sectors + 1
    for (r in 0 until rings) {
      for (s in 0 until sectors) {
        val a = r * stride + s
        val b = a + stride
        indices.addAll(listOf(a, a + 1, b, a + 1, b + 1, b))
      }
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  /**
   * Stepped circular marble courtyard dais matching Math3D.walkableWorldHeight at origin.
   */
  fun createSanctuaryDais(
    outerRadius: Float = 9.6f,
    innerRadius: Float = 7.8f,
    height: Float = 0.55f,
    sectors: Int = 48,
  ): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    // Vertex 0: top center
    verts.addAll(listOf(0f, height, 0f, 0f, 1f, 0f, 0.5f, 0.5f, 0.88f, 0.90f, 0.96f, 1f))
    // Inner top edge ring: 1 .. sectors+1
    for (s in 0..sectors) {
      val a = (s.toFloat() / sectors) * 2f * PI.toFloat()
      val cx = cos(a); val cz = sin(a)
      verts.addAll(
        listOf(
          cx * innerRadius, height, cz * innerRadius,
          0f, 1f, 0f,
          0.5f + cx * 2f, 0.5f + cz * 2f,
          0.82f, 0.85f, 0.94f, 1f,
        )
      )
    }
    // Outer sloped ground ring: sectors+2 .. 2*sectors+2
    for (s in 0..sectors) {
      val a = (s.toFloat() / sectors) * 2f * PI.toFloat()
      val cx = cos(a); val cz = sin(a)
      val ny = 0.72f
      val nr = 0.69f
      verts.addAll(
        listOf(
          cx * outerRadius, -0.05f, cz * outerRadius,
          cx * nr, ny, cz * nr,
          0.5f + cx * 2.5f, 0.5f + cz * 2.5f,
          0.66f, 0.70f, 0.84f, 1f,
        )
      )
    }
    // Top disc triangles (CCW looking down from +Y)
    for (s in 0 until sectors) {
      indices.addAll(listOf(0, s + 2, s + 1))
    }
    // Outer sloped step skirt
    val innerBase = 1
    val outerBase = sectors + 2
    for (s in 0 until sectors) {
      val a = innerBase + s
      val b = outerBase + s
      indices.addAll(listOf(a, a + 1, b, a + 1, b + 1, b))
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  /**
   * Sculpted anime hair crown wrapping temples, crown, and jagged nape fringe (never a plain ball).
   */
  fun createAnimeHairCrown(radius: Float = 0.54f, rings: Int = 14, sectors: Int = 26): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    for (r in 0..rings) {
      val v = r.toFloat() / rings
      // Phi from 0 (crown top) to ~0.72*PI (past ears/nape)
      val basePhi = v * 0.72f * PI.toFloat()
      for (s in 0..sectors) {
        val u = s.toFloat() / sectors
        val theta = u * 2f * PI.toFloat()
        val frontness = sin(theta).coerceAtLeast(0f) // +Z is face front
        // Raise hairline on the front forehead so face eyes stay clear, sweep down around back/sides
        val jagged = if (v > 0.65f) sin(theta * 9f) * 0.08f else 0f
        val phi = (basePhi * (1f - frontness * 0.48f) + jagged).coerceIn(0f, 0.85f * PI.toFloat())
        val ny = cos(phi)
        val sinPhi = sin(phi)
        val fluff = 1.0f + sin(v * PI.toFloat()) * 0.07f
        val nx = cos(theta) * sinPhi * fluff
        val nz = sin(theta) * sinPhi * (if (sin(theta) < 0f) 1.12f else 1.0f)
        val nLen = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.001f)
        verts.addAll(
          listOf(
            nx * radius, ny * radius, nz * radius,
            nx / nLen, ny / nLen, nz / nLen,
            u, v,
            1f, 1f, 1f, 1f,
          )
        )
      }
    }
    val stride = sectors + 1
    for (r in 0 until rings) {
      for (s in 0 until sectors) {
        val a = r * stride + s
        val b = a + stride
        indices.addAll(listOf(a, a + 1, b, a + 1, b + 1, b))
      }
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  /**
   * Open-front asymmetrical NTE-style anime trenchcoat / flared long jacket tail (leaves +Z front open).
   */
  fun createOpenTrenchCoat(
    rTop: Float = 0.24f,
    rBot: Float = 0.56f,
    height: Float = 1.0f,
    segments: Int = 22,
  ): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    val startTheta = (0.72 * PI).toFloat()
    val endTheta = (2.28 * PI).toFloat()
    for (r in 0..5) {
      val v = r / 5f
      val y = -v * height // hangs downward from waist pivot (y = 0)
      val baseR = Math3D.lerp(rTop, rBot, v)
      for (s in 0..segments) {
        val u = s.toFloat() / segments
        val a = Math3D.lerp(startTheta, endTheta, u)
        // Asymmetrical NTE coat-tail hem angle
        val hemDrop = 1.0f + sin(a - 0.5f) * 0.14f * v
        val fold = 1.0f + sin(a * 7f) * 0.055f * v
        val rad = baseR * fold
        val nx = cos(a)
        val nz = sin(a) * 0.86f
        verts.addAll(listOf(nx * rad, y * hemDrop, nz * rad, nx, 0.22f, nz, u, v, 1f, 1f, 1f, 1f))
      }
    }
    val stride = segments + 1
    for (r in 0 until 5) {
      for (s in 0 until segments) {
        val a = r * stride + s
        val b = a + stride
        indices.addAll(listOf(a, a + 1, b, a + 1, b + 1, b))
      }
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  /**
   * Gnarled anime tree trunk with flared ground roots + curved main trunk + 3 diverging branches.
   */
  fun createBranchingTreeTrunk(): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()

    fun addTube(
      points: List<FloatArray>,
      radii: List<Float>,
      sectors: Int = 10,
      rootFlare: Boolean = false,
    ) {
      val baseVert = verts.size / FLOATS_PER_VERTEX
      for (i in points.indices) {
        val v = i.toFloat() / (points.size - 1)
        val p = points[i]
        val rad = radii[i]
        for (s in 0..sectors) {
          val u = s.toFloat() / sectors
          val a = u * 2f * PI.toFloat()
          val flare = if (rootFlare && i == 0) (1.0f + sin(a * 4f) * 0.25f) else 1.0f
          val nx = cos(a)
          val nz = sin(a)
          verts.addAll(
            listOf(
              p[0] + nx * rad * flare,
              p[1],
              p[2] + nz * rad * flare,
              nx, 0.12f, nz,
              u * 1.5f, v * 2.5f,
              1f, 1f, 1f, 1f,
            )
          )
        }
      }
      val stride = sectors + 1
      for (i in 0 until points.size - 1) {
        for (s in 0 until sectors) {
          val a = baseVert + i * stride + s
          val b = a + stride
          indices.addAll(listOf(a, b, a + 1, a + 1, b, b + 1))
        }
      }
    }

    // Main gnarled trunk with flared roots
    addTube(
      points =
        listOf(
          floatArrayOf(0f, -0.15f, 0f),
          floatArrayOf(0.06f, 0.75f, -0.04f),
          floatArrayOf(-0.08f, 1.75f, 0.08f),
          floatArrayOf(0.04f, 2.75f, 0.02f),
          floatArrayOf(0f, 3.65f, 0f),
        ),
      radii = listOf(0.46f, 0.28f, 0.23f, 0.18f, 0.10f),
      rootFlare = true,
    )

    // Forked primary anime branches extending to canopy hubs
    addTube(
      points =
        listOf(
          floatArrayOf(-0.05f, 1.95f, 0.04f),
          floatArrayOf(-0.55f, 2.55f, 0.36f),
          floatArrayOf(-1.12f, 3.12f, 0.74f),
        ),
      radii = listOf(0.16f, 0.11f, 0.06f),
    )
    addTube(
      points =
        listOf(
          floatArrayOf(0.04f, 2.25f, -0.02f),
          floatArrayOf(0.58f, 2.78f, -0.34f),
          floatArrayOf(1.10f, 3.22f, -0.68f),
        ),
      radii = listOf(0.15f, 0.10f, 0.06f),
    )
    addTube(
      points =
        listOf(
          floatArrayOf(0.02f, 2.40f, 0.05f),
          floatArrayOf(0.28f, 2.95f, 0.56f),
          floatArrayOf(0.52f, 3.40f, 0.92f),
        ),
      radii = listOf(0.13f, 0.09f, 0.05f),
    )

    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  /**
   * 42 crossed & outward-tilted painterly anime leaf/blossom spray cards around the 4 tree hubs,
   * with baked ambient occlusion (rich cool interior shade -> warm sunlit crown tips).
   */
  fun createTreeCanopyCards(): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    val rng = kotlin.random.Random(707)

    val hubs =
      listOf(
        floatArrayOf(0f, 3.85f, 0f, 1.55f),
        floatArrayOf(-1.12f, 3.15f, 0.74f, 1.25f),
        floatArrayOf(1.10f, 3.25f, -0.68f, 1.25f),
        floatArrayOf(0.52f, 3.45f, 0.92f, 1.05f),
      )

    fun addCard(cx: Float, cy: Float, cz: Float, cardSize: Float, yaw: Float, pitch: Float, shade: Float) {
      val half = cardSize * 0.5f
      val cyaw = cos(yaw); val syaw = sin(yaw)
      val cpit = cos(pitch); val spit = sin(pitch)

      // Compute approximate outward normal from hub center
      val ny = (cy - 3.1f).coerceIn(-0.4f, 1.0f)
      val nLen = sqrt(cx * cx + ny * ny + cz * cz).coerceAtLeast(0.1f)
      val nx = cx / nLen
      val nyn = (ny / nLen + 0.35f).coerceAtMost(1f)
      val nz = cz / nLen

      val corners =
        arrayOf(
          floatArrayOf(-half, -half, 0f, 0f, 1f),
          floatArrayOf(half, -half, 0f, 1f, 1f),
          floatArrayOf(half, half, 0f, 1f, 0f),
          floatArrayOf(-half, half, 0f, 0f, 0f),
        )
      val base = verts.size / FLOATS_PER_VERTEX
      for (c in corners) {
        val lx = c[0]
        val ly0 = c[1]
        val lz0 = c[2]
        // Pitch around X
        val ly1 = ly0 * cpit - lz0 * spit
        val lz1 = ly0 * spit + lz0 * cpit
        // Yaw around Y
        val rx = lx * cyaw + lz1 * syaw
        val rz = -lx * syaw + lz1 * cyaw
        val colBoost = (shade + (ly1 / cardSize) * 0.16f).coerceIn(0.64f, 1.12f)
        verts.addAll(
          listOf(
            cx + rx, cy + ly1, cz + rz,
            nx, nyn, nz,
            c[3], c[4],
            colBoost, colBoost, (colBoost + 0.04f).coerceAtMost(1.12f), 1f,
          )
        )
      }
      indices.addAll(listOf(base, base + 1, base + 2, base, base + 2, base + 3))
    }

    for (hub in hubs) {
      val hx = hub[0]; val hy = hub[1]; val hz = hub[2]; val hr = hub[3]
      val count = 11
      for (i in 0 until count) {
        val a = (i.toFloat() / count) * 2f * PI.toFloat() + rng.nextFloat() * 0.45f
        val dist = (0.20f + rng.nextFloat() * 0.78f) * hr
        val oy = (rng.nextFloat() - 0.32f) * hr * 0.85f
        val cx = hx + cos(a) * dist
        val cy = hy + oy
        val cz = hz + sin(a) * dist
        val cardSize = 1.45f + rng.nextFloat() * 0.72f
        val shade = (0.74f + ((cy - 2.4f) / 2.2f) * 0.32f).coerceIn(0.68f, 1.08f)
        // Crossed dual planes per spray puff so it looks lush from every angle!
        val yaw = a + rng.nextFloat() * 0.8f
        addCard(cx, cy, cz, cardSize, yaw, 0.32f, shade)
        addCard(cx, cy, cz, cardSize * 0.92f, yaw + 1.35f, -0.28f, shade * 0.96f)
      }
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }

  /**
   * Lush low flower/shrub cluster made of 10 angled alpha-cutout leaf cards.
   */
  fun createBushCards(): GpuMesh {
    val verts = ArrayList<Float>()
    val indices = ArrayList<Int>()
    val rng = kotlin.random.Random(303)
    for (i in 0 until 10) {
      val a = (i.toFloat() / 10f) * 2f * PI.toFloat()
      val r = 0.25f + (i % 2) * 0.28f
      val cx = cos(a) * r
      val cz = sin(a) * r
      val cy = 0.45f + rng.nextFloat() * 0.25f
      val half = 0.56f
      val yaw = a + 0.5f
      val cyaw = cos(yaw); val syaw = sin(yaw)
      val base = verts.size / FLOATS_PER_VERTEX
      val corners =
        arrayOf(
          floatArrayOf(-half, -half, 0f, 1f, 0.78f),
          floatArrayOf(half, -half, 1f, 1f, 0.78f),
          floatArrayOf(half, half, 1f, 0f, 1.08f),
          floatArrayOf(-half, half, 0f, 0f, 1.08f),
        )
      for (c in corners) {
        val rx = c[0] * cyaw
        val rz = -c[0] * syaw
        verts.addAll(
          listOf(
            cx + rx, cy + c[1], cz + rz,
            cx, 0.85f, cz,
            c[2], c[3],
            c[4], c[4], c[4], 1f,
          )
        )
      }
      indices.addAll(listOf(base, base + 1, base + 2, base, base + 2, base + 3))
    }
    return uploadMesh(verts.toFloatArray(), indices.toIntArray())
  }
}
