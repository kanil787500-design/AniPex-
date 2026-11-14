package com.example.engine3d

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.data.ElementType
import com.example.data.GameCatalog
import com.example.data.HeroCombatStats
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class AetherRenderer(private val worldState: GameWorldState) : GLSurfaceView.Renderer {

  private var celProgram = 0
  private var outlineProgram = 0
  private var skyProgram = 0
  private var waterProgram = 0
  private var particleProgram = 0
  private var shadowProgram = 0
  private var bloomExtractProgram = 0
  private var bloomBlurProgram = 0
  private var compositeProgram = 0

  private lateinit var textures: ProceduralTextures.TextureSet
  private lateinit var terrainMesh: GpuMesh
  private lateinit var arenaFloorMesh: GpuMesh
  private lateinit var waterMesh: GpuMesh
  private lateinit var skyDomeMesh: GpuMesh
  private lateinit var boxMesh: GpuMesh
  private lateinit var sphereMesh: GpuMesh
  private lateinit var animeHeadMesh: GpuMesh
  private lateinit var limbMesh: GpuMesh
  private lateinit var torsoMesh: GpuMesh
  private lateinit var skirtMesh: GpuMesh
  private lateinit var hairLockMesh: GpuMesh
  private lateinit var swordBladeMesh: GpuMesh
  private lateinit var slashArcMesh: GpuMesh
  private lateinit var grassTuftMesh: GpuMesh
  private lateinit var shadowQuadMesh: GpuMesh
  private lateinit var coneMesh: GpuMesh
  private lateinit var cylinderMesh: GpuMesh
  private lateinit var crystalMesh: GpuMesh
  private lateinit var torusMesh: GpuMesh
  private var quadVao = 0

  // Offscreen FBOs for HDR Bloom & Filmic Compositing
  private var viewW = 1280
  private var viewH = 720
  private var sceneFbo = 0
  private var sceneColorTex = 0
  private var sceneDepthRb = 0
  private var bloomFboA = 0
  private var bloomTexA = 0
  private var bloomFboB = 0
  private var bloomTexB = 0
  private var bloomReady = false

  private lateinit var rigRenderer: CharacterRigRenderer
  private val particles = ParticleSystem3D(2400)

  private val viewMat = FloatArray(16)
  private val projMat = FloatArray(16)
  private val vpMat = FloatArray(16)
  private val skyVpMat = FloatArray(16)
  private val modelMat = FloatArray(16)
  private val mvpMat = FloatArray(16)
  private val normalMat = FloatArray(9)

  private var screenW = 1920f
  private var screenH = 1080f
  private var lastTimeNs = 0L
  private var animTime = 0f
  private var hudEmitAccum = 0f

  // Player simulation state
  private var playerX = 0f
  private var playerY = 0f
  private var playerZ = 6f
  private var playerYaw = 180f
  private var playerVelY = 0f
  private var isGrounded = true
  private var isGliding = false
  private var stamina = 100f
  private var energy = 65f
  private var skillCooldown = 0f
  private var burstCooldown = 0f
  private var dashTimer = 0f
  private var attackTimer = 0f
  private var comboStep = 0
  private var playerHp = 1280
  private var playerMaxHp = 1280
  private var localDamageDealt = 0
  private var lastActionTag = "idle"
  private var lastMode = WorldMode.OPEN_WORLD

  private var bossHitFlash = 0f
  private var bossTelegraphTimer = 0f
  private var bossAttackCycle = 0f

  private data class WorldDamagePopup(
    val id: Long,
    val text: String,
    val wx: Float,
    var wy: Float,
    val wz: Float,
    val colorHex: Long,
    val isReaction: Boolean,
    var age: Float = 0f,
  )

  private val popups = ArrayList<WorldDamagePopup>()
  private var nextPopupId = 1L

  private val treePositions =
    listOf(
      Triple(-18f, -12f, 1.3f), Triple(-24f, 6f, 1.15f), Triple(20f, -14f, 1.4f),
      Triple(24f, 4f, 1.2f), Triple(-14f, 24f, 1.35f), Triple(12f, 26f, 1.15f),
      Triple(44f, -18f, 1.5f), Triple(-44f, 14f, 1.4f), Triple(32f, -38f, 1.3f),
      Triple(-30f, -36f, 1.35f), Triple(52f, 34f, 1.45f), Triple(-48f, -16f, 1.3f),
      Triple(-10f, -44f, 1.35f), Triple(16f, -40f, 1.25f),
    )

  private val grassTufts: List<FloatArray> =
    run {
      val rng = Random(2026)
      val list = ArrayList<FloatArray>(110)
      var attempts = 0
      while (list.size < 110 && attempts < 500) {
        attempts++
        val gx = (rng.nextFloat() - 0.5f) * 95f
        val gz = (rng.nextFloat() - 0.5f) * 95f
        if (Math3D.distXZ(gx, gz, 0f, 0f) < 15f) continue
        if (Math3D.distXZ(gx, gz, 32f, 24f) < 22f) continue
        val gy = Math3D.openWorldHeight(gx, gz)
        val sc = 0.85f + rng.nextFloat() * 0.75f
        val rot = rng.nextFloat() * 360f
        list.add(floatArrayOf(gx, gy, gz, sc, rot))
      }
      list
    }

  private val floatingIslands =
    listOf(
      floatArrayOf(-28f, 25f, -32f, 6.8f),
      floatArrayOf(34f, 30f, -26f, 8.2f),
      floatArrayOf(0f, 38f, -54f, 11.5f),
      floatArrayOf(-42f, 23f, 28f, 6.0f),
    )

  private val enemies =
    mutableListOf(
      WorldEnemy("e1", "Aero Slime", ElementType.AERO, 14f, 0f, -10f, 14f, -10f, 0f, 1400, 1400, false),
      WorldEnemy("e2", "Pyro Slime", ElementType.PYRO, -15f, 0f, -14f, -15f, -14f, 90f, 1600, 1600, false),
      WorldEnemy("e3", "Cryo Sentinel", ElementType.CRYO, 22f, 0f, -22f, 22f, -22f, 180f, 3800, 3800, true),
      WorldEnemy("e4", "Ruin Colossus", ElementType.PYRO, -28f, 0f, 18f, -28f, 18f, 45f, 4400, 4400, true),
      WorldEnemy("e5", "Tempest Wisp", ElementType.AERO, 36f, 0f, -24f, 36f, -24f, 220f, 2100, 2100, false),
      WorldEnemy("e6", "Obsidian Warden", ElementType.CRYO, -16f, 0f, -34f, -16f, -34f, 120f, 4800, 4800, true),
    )

  override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
    GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    GLES30.glEnable(GLES30.GL_CULL_FACE)
    GLES30.glCullFace(GLES30.GL_BACK)

    celProgram = Shaders.compileProgram(Shaders.CEL_VERTEX, Shaders.CEL_FRAGMENT)
    outlineProgram = Shaders.compileProgram(Shaders.OUTLINE_VERTEX, Shaders.OUTLINE_FRAGMENT)
    skyProgram = Shaders.compileProgram(Shaders.SKY_VERTEX, Shaders.SKY_FRAGMENT)
    waterProgram = Shaders.compileProgram(Shaders.WATER_VERTEX, Shaders.WATER_FRAGMENT)
    particleProgram = Shaders.compileProgram(Shaders.PARTICLE_VERTEX, Shaders.PARTICLE_FRAGMENT)
    shadowProgram = Shaders.compileProgram(Shaders.CEL_VERTEX, Shaders.SHADOW_FRAGMENT)
    bloomExtractProgram = Shaders.compileProgram(Shaders.QUAD_VERTEX, Shaders.BLOOM_EXTRACT_FRAGMENT)
    bloomBlurProgram = Shaders.compileProgram(Shaders.QUAD_VERTEX, Shaders.BLOOM_BLUR_FRAGMENT)
    compositeProgram = Shaders.compileProgram(Shaders.QUAD_VERTEX, Shaders.COMPOSITE_FRAGMENT)

    textures = ProceduralTextures.createAll()
    terrainMesh = MeshBuilder.createOpenWorldTerrain()
    arenaFloorMesh = MeshBuilder.createArenaFloor()
    waterMesh = MeshBuilder.createWaterDisc()
    skyDomeMesh = MeshBuilder.createSkyDome()
    boxMesh = MeshBuilder.createBox()
    sphereMesh = MeshBuilder.createSphere()
    animeHeadMesh = MeshBuilder.createAnimeHead()
    limbMesh = MeshBuilder.createTaperedLimb()
    torsoMesh = MeshBuilder.createSculptedTorso()
    skirtMesh = MeshBuilder.createFlaredSkirt()
    hairLockMesh = MeshBuilder.createHairLock()
    swordBladeMesh = MeshBuilder.createSwordBlade()
    slashArcMesh = MeshBuilder.createSlashArc()
    grassTuftMesh = MeshBuilder.createGrassTuft()
    shadowQuadMesh = MeshBuilder.createShadowQuad()
    coneMesh = MeshBuilder.createCone()
    cylinderMesh = MeshBuilder.createCylinder()
    crystalMesh = MeshBuilder.createDiamondCrystal()
    torusMesh = MeshBuilder.createTorusRing()
    quadVao = MeshBuilder.createFullscreenQuadVao()

    rigRenderer =
      CharacterRigRenderer(
        boxMesh = boxMesh,
        sphereMesh = sphereMesh,
        animeHeadMesh = animeHeadMesh,
        limbMesh = limbMesh,
        torsoMesh = torsoMesh,
        skirtMesh = skirtMesh,
        hairLockMesh = hairLockMesh,
        swordBladeMesh = swordBladeMesh,
        slashArcMesh = slashArcMesh,
        coneMesh = coneMesh,
        cylinderMesh = cylinderMesh,
        crystalMesh = crystalMesh,
        torusMesh = torusMesh,
      )
    particles.initGl()

    enemies.forEach { it.y = Math3D.openWorldHeight(it.x, it.z) }
    playerY = Math3D.openWorldHeight(playerX, playerZ)
    lastTimeNs = System.nanoTime()
  }

  override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
    viewW = width.coerceAtLeast(1)
    viewH = height.coerceAtLeast(1)
    GLES30.glViewport(0, 0, viewW, viewH)
    screenW = viewW.toFloat()
    screenH = viewH.toFloat()
    val aspect = screenW / screenH
    Matrix.perspectiveM(projMat, 0, 50f, aspect, 0.35f, 340f)
    setupBloomFramebuffers(viewW, viewH)
  }

  private fun setupBloomFramebuffers(w: Int, h: Int) {
    releaseBloomFramebuffers()
    if (bloomExtractProgram == 0 || bloomBlurProgram == 0 || compositeProgram == 0) {
      bloomReady = false
      return
    }
    try {
      val fbos = IntArray(3)
      val texs = IntArray(3)
      val rbs = IntArray(1)
      GLES30.glGenFramebuffers(3, fbos, 0)
      GLES30.glGenTextures(3, texs, 0)
      GLES30.glGenRenderbuffers(1, rbs, 0)

      sceneFbo = fbos[0]; sceneColorTex = texs[0]; sceneDepthRb = rbs[0]
      bloomFboA = fbos[1]; bloomTexA = texs[1]
      bloomFboB = fbos[2]; bloomTexB = texs[2]

      // Full-res scene FBO + depth buffer
      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneColorTex)
      GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

      GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, sceneDepthRb)
      GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, w, h)

      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFbo)
      GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, sceneColorTex, 0)
      GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, sceneDepthRb)
      val sceneStatus = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)

      // Quarter-res bloom ping-pong FBOs
      val bw = (w / 4).coerceAtLeast(64)
      val bh = (h / 4).coerceAtLeast(64)
      for ((fbo, tex) in listOf(bloomFboA to bloomTexA, bloomFboB to bloomTexB)) {
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, bw, bh, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex, 0)
      }

      val bloomStatus = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
      bloomReady = (sceneStatus == GLES30.GL_FRAMEBUFFER_COMPLETE && bloomStatus == GLES30.GL_FRAMEBUFFER_COMPLETE)
    } catch (_: Exception) {
      bloomReady = false
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }
  }

  private fun releaseBloomFramebuffers() {
    if (sceneFbo != 0) {
      GLES30.glDeleteFramebuffers(3, intArrayOf(sceneFbo, bloomFboA, bloomFboB), 0)
      GLES30.glDeleteTextures(3, intArrayOf(sceneColorTex, bloomTexA, bloomTexB), 0)
      GLES30.glDeleteRenderbuffers(1, intArrayOf(sceneDepthRb), 0)
      sceneFbo = 0; bloomFboA = 0; bloomFboB = 0
      sceneColorTex = 0; bloomTexA = 0; bloomTexB = 0; sceneDepthRb = 0
    }
    bloomReady = false
  }

  override fun onDrawFrame(gl: GL10?) {
    val now = System.nanoTime()
    val dt = ((now - lastTimeNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
    lastTimeNs = now
    animTime += dt

    updateSimulation(dt)

    if (bloomReady) {
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFbo)
      GLES30.glViewport(0, 0, viewW, viewH)
      renderScene(dt)
      runBloomAndComposite()
    } else {
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
      GLES30.glViewport(0, 0, viewW, viewH)
      renderScene(dt)
    }
  }

  private fun runBloomAndComposite() {
    val bw = (viewW / 4).coerceAtLeast(64)
    val bh = (viewH / 4).coerceAtLeast(64)

    GLES30.glDisable(GLES30.GL_DEPTH_TEST)
    GLES30.glDisable(GLES30.GL_CULL_FACE)
    GLES30.glBindVertexArray(quadVao)

    // 1. Extract bright HDR highlights -> bloomFboA
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFboA)
    GLES30.glViewport(0, 0, bw, bh)
    GLES30.glUseProgram(bloomExtractProgram)
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneColorTex)
    GLES30.glUniform1i(GLES30.glGetUniformLocation(bloomExtractProgram, "uSceneTex"), 0)
    GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_INT, 0)

    // 2. Separable Gaussian blur ping-pong (horizontal -> B, vertical -> A)
    GLES30.glUseProgram(bloomBlurProgram)
    val texStepLoc = GLES30.glGetUniformLocation(bloomBlurProgram, "uTexelStep")
    val blurTexLoc = GLES30.glGetUniformLocation(bloomBlurProgram, "uTex")

    for (pass in 0 until 2) {
      // Horizontal: A -> B
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFboB)
      GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bloomTexA)
      GLES30.glUniform1i(blurTexLoc, 0)
      GLES30.glUniform2f(texStepLoc, 1.35f / bw, 0f)
      GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_INT, 0)

      // Vertical: B -> A
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFboA)
      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bloomTexB)
      GLES30.glUniform2f(texStepLoc, 0f, 1.35f / bh)
      GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_INT, 0)
    }

    // 3. Final Composite (Scene + Bloom + ACES Filmic Tone Mapping + Vignette) -> Screen
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    GLES30.glViewport(0, 0, viewW, viewH)
    GLES30.glUseProgram(compositeProgram)
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneColorTex)
    GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uSceneTex"), 0)
    GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bloomTexA)
    GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uBloomTex"), 1)
    GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_INT, 0)

    GLES30.glBindVertexArray(0)
    GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    GLES30.glEnable(GLES30.GL_CULL_FACE)
  }

  private fun updateSimulation(dt: Float) {
    val mode = worldState.mode
    if (mode != lastMode) {
      lastMode = mode
      if (mode == WorldMode.RAID_ARENA) {
        playerX = 0f; playerZ = 14f; playerY = 0f; playerVelY = 0f
        isGrounded = true; isGliding = false
        localDamageDealt = 0
        bossAttackCycle = 0f; bossTelegraphTimer = 0f
      } else {
        playerX = 0f; playerZ = 6f
        playerY = Math3D.openWorldHeight(playerX, playerZ)
        playerVelY = 0f; isGrounded = true; isGliding = false
      }
    }

    val stats = worldState.heroStats
    if (stats != null) {
      val prevMax = playerMaxHp
      playerMaxHp = stats.maxHp
      if (prevMax != playerMaxHp && playerHp > playerMaxHp) playerHp = playerMaxHp
      if (playerHp <= 0) playerHp = playerMaxHp
    }

    if (skillCooldown > 0f) skillCooldown = (skillCooldown - dt).coerceAtLeast(0f)
    if (burstCooldown > 0f) burstCooldown = (burstCooldown - dt).coerceAtLeast(0f)
    if (attackTimer > 0f) attackTimer = (attackTimer - dt).coerceAtLeast(0f)
    if (dashTimer > 0f) dashTimer = (dashTimer - dt).coerceAtLeast(0f)
    if (bossHitFlash > 0f) bossHitFlash = (bossHitFlash - dt).coerceAtLeast(0f)

    while (true) {
      val cmd = worldState.commandQueue.poll() ?: break
      handleCommand(cmd, stats)
    }

    val ix = worldState.moveInputX
    val iy = worldState.moveInputY
    val inputMag = sqrt(ix * ix + iy * iy).coerceAtMost(1f)
    val sprinting = inputMag > 0.82f && stamina > 5f && !isGliding
    if (sprinting) stamina = (stamina - 16f * dt).coerceAtLeast(0f)
    else if (isGliding) stamina = (stamina - 8f * dt).coerceAtLeast(0f)
    else stamina = (stamina + 22f * dt).coerceAtMost(100f)

    val moveSpeed =
      when {
        dashTimer > 0f -> 19f
        isGliding -> 9.2f
        sprinting -> 10.4f
        else -> 6.8f
      } * (if (dashTimer > 0f) 1f else inputMag)

    if (inputMag > 0.05f || dashTimer > 0f) {
      val camRad = worldState.camYaw * Math3D.DEG_TO_RAD
      val fwdX = -sin(camRad)
      val fwdZ = -cos(camRad)
      val rightX = cos(camRad)
      val rightZ = -sin(camRad)

      val dirX = if (dashTimer > 0f && inputMag < 0.1f) sin(playerYaw * Math3D.DEG_TO_RAD) else (fwdX * (-iy) + rightX * ix)
      val dirZ = if (dashTimer > 0f && inputMag < 0.1f) cos(playerYaw * Math3D.DEG_TO_RAD) else (fwdZ * (-iy) + rightZ * ix)
      val len = sqrt(dirX * dirX + dirZ * dirZ).coerceAtLeast(0.001f)
      val nx = dirX / len
      val nz = dirZ / len

      playerX += nx * moveSpeed * dt
      playerZ += nz * moveSpeed * dt

      val targetYaw = kotlin.math.atan2(nx, nz) * Math3D.RAD_TO_DEG
      playerYaw = Math3D.lerpAngleDeg(playerYaw, targetYaw, (dt * 14f).coerceIn(0f, 1f))

      if (isGrounded && Random.nextFloat() < 0.42f) {
        particles.spawn(
          playerX, playerY + 0.1f, playerZ,
          (Random.nextFloat() - 0.5f) * 1.2f, 0.8f, (Random.nextFloat() - 0.5f) * 1.2f,
          0.35f, 0.25f, 0.85f, 0.88f, 0.78f, 0.5f, -1f,
        )
      }
    }

    if (mode == WorldMode.RAID_ARENA) {
      val r = sqrt(playerX * playerX + playerZ * playerZ)
      if (r > 23.5f) {
        playerX *= 23.5f / r
        playerZ *= 23.5f / r
      }
    } else {
      playerX = playerX.coerceIn(-98f, 98f)
      playerZ = playerZ.coerceIn(-98f, 98f)
    }

    val floorY = if (mode == WorldMode.RAID_ARENA) 0f else Math3D.openWorldHeight(playerX, playerZ)
    if (!isGrounded) {
      val grav = if (isGliding && playerVelY < 0f) -2.4f else -24f
      playerVelY += grav * dt
      if (isGliding && playerVelY < -2.6f) playerVelY = -2.6f
      playerY += playerVelY * dt
      if (playerY <= floorY) {
        playerY = floorY
        playerVelY = 0f
        isGrounded = true
        isGliding = false
      }
    } else {
      playerY = floorY
    }

    // Lush drifting sakura petals & elemental wind motes
    if (Random.nextFloat() < 0.72f) {
      val px = playerX + (Random.nextFloat() - 0.5f) * 32f
      val pz = playerZ + (Random.nextFloat() - 0.5f) * 32f
      val py = playerY + 1.2f + Random.nextFloat() * 8f
      val isPetal = mode == WorldMode.OPEN_WORLD && Random.nextFloat() < 0.45f
      particles.spawn(
        px, py, pz,
        0.9f + Random.nextFloat() * 0.9f, -0.4f, 0.35f,
        2.8f, if (isPetal) 0.24f else 0.19f,
        if (isPetal) 1.0f else 0.58f,
        if (isPetal) 0.74f else 0.96f,
        if (isPetal) 0.86f else 0.92f,
        0.78f,
      )
    }

    if (mode == WorldMode.OPEN_WORLD) {
      updateOpenWorldEnemies(dt)
    } else {
      updateRaidBossSimulation(dt)
    }

    val iter = popups.iterator()
    while (iter.hasNext()) {
      val p = iter.next()
      p.age += dt
      p.wy += 1.4f * dt
      if (p.age > 1.15f) iter.remove()
    }

    hudEmitAccum += dt
    if (hudEmitAccum >= 0.065f) {
      hudEmitAccum = 0f
      emitHudSnapshot()
    }
  }

  private fun handleCommand(cmd: GameInputCommand, stats: HeroCombatStats?) {
    val arch = GameCatalog.heroById(worldState.activeHeroId)
    val rgb = CharacterRigRenderer.colorHexToRgb(arch.primaryColorHex)

    when (cmd) {
      is GameInputCommand.TeleportTo -> {
        playerX = cmd.x; playerZ = cmd.z
        playerY = Math3D.openWorldHeight(cmd.x, cmd.z)
        playerVelY = 0f; isGrounded = true; isGliding = false
        particles.emitElementalVortex(playerX, playerY, playerZ, 0.35f, 0.95f, 1f, 65)
      }
      GameInputCommand.JumpOrGlide -> {
        if (isGrounded) {
          isGrounded = false
          playerVelY = 10.6f
          lastActionTag = "jump"
        } else {
          isGliding = !isGliding && stamina > 5f
          lastActionTag = if (isGliding) "glide" else "fall"
        }
      }
      GameInputCommand.Dash -> {
        if (stamina >= 15f) {
          stamina -= 15f
          dashTimer = 0.24f
          isGliding = false
          lastActionTag = "dash"
          particles.emitSlashBurst(playerX, playerY, playerZ, playerYaw + 180f, rgb[0], rgb[1], rgb[2], 28)
        }
      }
      GameInputCommand.NormalAttack -> {
        attackTimer = 0.38f
        comboStep = (comboStep + 1) % 3
        isGliding = false
        lastActionTag = "attack_$comboStep"
        particles.emitSlashBurst(playerX, playerY, playerZ, playerYaw, rgb[0], rgb[1], rgb[2], 36)
        performPlayerHit(
          range = 4.6f,
          multiplier = (stats?.normalMultiplier ?: 1.0f) * (1f + comboStep * 0.18f),
          shieldMult = 0.6f,
          energyGain = 9f,
          isSkillOrBurst = false,
        )
      }
      GameInputCommand.ElementalSkill -> {
        if (skillCooldown <= 0f) {
          skillCooldown = 5.5f
          attackTimer = 0.42f
          lastActionTag = "skill"
          particles.emitElementalVortex(playerX, playerY, playerZ, rgb[0], rgb[1], rgb[2], 78)
          if (arch.element == ElementType.CRYO) {
            playerHp = (playerHp + (playerMaxHp * 0.22f).toInt()).coerceAtMost(playerMaxHp)
          }
          performPlayerHit(
            range = 6.2f,
            multiplier = stats?.skillMultiplier ?: 2.2f,
            shieldMult = 1.8f,
            energyGain = 24f,
            isSkillOrBurst = true,
          )
        }
      }
      GameInputCommand.ElementalBurst -> {
        if (energy >= 100f && burstCooldown <= 0f) {
          energy = 0f
          burstCooldown = 10f
          attackTimer = 0.45f
          lastActionTag = "burst"
          particles.emitShockwaveRing(playerX, playerY, playerZ, 9.5f, rgb[0], rgb[1], rgb[2], 110)
          particles.emitElementalVortex(playerX, playerY + 0.5f, playerZ, 1f, 0.9f, 0.5f, 85)
          performPlayerHit(
            range = 9.5f,
            multiplier = stats?.burstMultiplier ?: 3.8f,
            shieldMult = 3.0f,
            energyGain = 0f,
            isSkillOrBurst = true,
          )
        }
      }
    }
  }

  private fun performPlayerHit(
    range: Float,
    multiplier: Float,
    shieldMult: Float,
    energyGain: Float,
    isSkillOrBurst: Boolean,
  ) {
    val arch = GameCatalog.heroById(worldState.activeHeroId)
    val stats = worldState.heroStats
    val baseAtk = stats?.attack ?: 180
    val critRate = stats?.critRatePct ?: 20
    val critDmg = stats?.critDamagePct ?: 75
    val isCrit = Random.nextInt(100) < critRate
    val critFactor = if (isCrit) 1f + (critDmg / 100f) else 1f
    val rawDmg = (baseAtk * multiplier * critFactor * (0.94f + Random.nextFloat() * 0.12f)).toInt().coerceAtLeast(45)

    if (worldState.mode == WorldMode.OPEN_WORLD) {
      for (e in enemies) {
        if (e.hp <= 0) continue
        if (Math3D.distXZ(playerX, playerZ, e.x, e.z) <= range) {
          var finalDmg = rawDmg
          var reactionTag: String? = null
          if (isSkillOrBurst && e.appliedElement != null && e.appliedElement != arch.element) {
            reactionTag =
              when {
                arch.element == ElementType.AERO || e.appliedElement == ElementType.AERO -> "SWIRL!"
                (arch.element == ElementType.PYRO && e.appliedElement == ElementType.CRYO) ||
                  (arch.element == ElementType.CRYO && e.appliedElement == ElementType.PYRO) -> "MELT x1.5!"
                else -> "RESONANCE!"
              }
            finalDmg = (finalDmg * 1.45f).toInt()
            e.appliedElement = null
          } else if (isSkillOrBurst) {
            e.appliedElement = arch.element
          }

          e.hp = (e.hp - finalDmg).coerceAtLeast(0)
          e.hitFlash = 0.25f
          energy = (energy + energyGain).coerceAtMost(100f)
          popups.add(
            WorldDamagePopup(
              id = nextPopupId++,
              text = if (isCrit) "$finalDmg!" else "$finalDmg",
              wx = e.x, wy = e.y + 1.8f, wz = e.z,
              colorHex = arch.primaryColorHex,
              isReaction = isCrit,
            )
          )
          if (reactionTag != null) {
            popups.add(
              WorldDamagePopup(
                id = nextPopupId++,
                text = reactionTag,
                wx = e.x + 0.5f, wy = e.y + 2.5f, wz = e.z,
                colorHex = 0xFFFFD166,
                isReaction = true,
              )
            )
          }
          if (e.hp <= 0) {
            e.respawnTimer = 14f
            particles.emitShockwaveRing(e.x, e.y, e.z, 4f, 1f, 0.85f, 0.35f, 50)
            worldState.onEnemySlain?.invoke(e.name)
          }
        }
      }
    } else {
      val distToBoss = Math3D.distXZ(playerX, playerZ, 0f, 0f)
      if (distToBoss <= range + 2.8f) {
        val room = worldState.activeRaidRoom
        val bossBroken = (room?.bossShield ?: 0) <= 0
        val finalDmg = if (bossBroken) (rawDmg * 1.35f).toInt() else rawDmg
        val shieldDmg = if (bossBroken) 0 else (finalDmg * shieldMult).toInt()

        localDamageDealt += finalDmg
        bossHitFlash = 0.25f
        energy = (energy + energyGain).coerceAtMost(100f)

        popups.add(
          WorldDamagePopup(
            id = nextPopupId++,
            text = if (isCrit) "CRIT $finalDmg!" else "$finalDmg",
            wx = (Random.nextFloat() - 0.5f) * 1.8f,
            wy = 3.2f,
            wz = (Random.nextFloat() - 0.5f) * 1.8f,
            colorHex = if (bossBroken) 0xFFFFD166 else arch.primaryColorHex,
            isReaction = isCrit || bossBroken,
          )
        )
        worldState.onBossHit?.invoke(finalDmg, shieldDmg)
      }
    }
  }

  private fun updateOpenWorldEnemies(dt: Float) {
    for (e in enemies) {
      if (e.hitFlash > 0f) e.hitFlash = (e.hitFlash - dt).coerceAtLeast(0f)
      if (e.hp <= 0) {
        e.respawnTimer -= dt
        if (e.respawnTimer <= 0f) {
          e.hp = e.maxHp
          e.x = e.homeX; e.z = e.homeZ
          e.y = Math3D.openWorldHeight(e.x, e.z)
        }
        continue
      }

      val dist = Math3D.distXZ(playerX, playerZ, e.x, e.z)
      if (dist < 14f && dist > 2.2f) {
        e.yaw = Math3D.yawToward(e.x, e.z, playerX, playerZ)
        val spd = if (e.isGolem) 2.2f else 3.4f
        e.x += sin(e.yaw * Math3D.DEG_TO_RAD) * spd * dt
        e.z += cos(e.yaw * Math3D.DEG_TO_RAD) * spd * dt
        e.y = Math3D.openWorldHeight(e.x, e.z)
      } else if (dist <= 2.2f) {
        e.attackTimer += dt
        if (e.attackTimer > 1.8f) {
          e.attackTimer = 0f
          if (dashTimer <= 0f) {
            val dmg = if (e.isGolem) 110 else 65
            playerHp = (playerHp - dmg).coerceAtLeast(40)
          }
        }
      }
    }
  }

  private fun updateRaidBossSimulation(dt: Float) {
    val room = worldState.activeRaidRoom ?: return
    if (room.status != "active") return
    bossAttackCycle += dt
    if (bossAttackCycle > 4.5f) {
      bossTelegraphTimer = ((bossAttackCycle - 4.5f) / 2.2f).coerceIn(0f, 1f)
      if (bossAttackCycle >= 6.7f) {
        bossAttackCycle = 0f
        bossTelegraphTimer = 0f
        val rgb = CharacterRigRenderer.colorHexToRgb(
          ElementType.entries.firstOrNull { it.label == room.bossElement }?.colorHex ?: 0xFF34E8C6
        )
        particles.emitShockwaveRing(0f, 0.3f, 0f, 10f, rgb[0], rgb[1], rgb[2], 95)
        if (Math3D.distXZ(playerX, playerZ, 0f, 0f) < 9.5f && dashTimer <= 0f && isGrounded) {
          playerHp = (playerHp - 210).coerceAtLeast(100)
        }
      }
    } else {
      bossTelegraphTimer = 0f
    }
  }

  private fun renderScene(dt: Float) {
    val isRaid = worldState.mode == WorldMode.RAID_ARENA
    if (isRaid) {
      GLES30.glClearColor(0.06f, 0.07f, 0.14f, 1f)
    } else {
      GLES30.glClearColor(0.42f, 0.72f, 0.96f, 1f)
    }
    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

    val yawRad = worldState.camYaw * Math3D.DEG_TO_RAD
    val pitchRad = worldState.camPitch * Math3D.DEG_TO_RAD
    val dist = worldState.camDistance
    val lookTargetY = playerY + 1.38f
    val camX = playerX + sin(yawRad) * cos(pitchRad) * dist
    val camZ = playerZ + cos(yawRad) * cos(pitchRad) * dist
    var camY = lookTargetY + sin(pitchRad) * dist
    val groundUnderCam = if (isRaid) 0.5f else Math3D.openWorldHeight(camX, camZ) + 0.8f
    if (camY < groundUnderCam) camY = groundUnderCam

    Matrix.setLookAtM(viewMat, 0, camX, camY, camZ, playerX, lookTargetY, playerZ, 0f, 1f, 0f)
    Matrix.multiplyMM(vpMat, 0, projMat, 0, viewMat, 0)

    // 1. Skybox Dome with sun halo, clouds, and mountain horizon
    GLES30.glDepthMask(false)
    GLES30.glCullFace(GLES30.GL_FRONT)
    Matrix.setIdentityM(modelMat, 0)
    Matrix.translateM(modelMat, 0, camX, camY, camZ)
    Matrix.multiplyMM(skyVpMat, 0, vpMat, 0, modelMat, 0)
    GLES30.glUseProgram(skyProgram)
    GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(skyProgram, "uMVP"), 1, false, skyVpMat, 0)
    if (isRaid) {
      GLES30.glUniform3f(GLES30.glGetUniformLocation(skyProgram, "uZenithColor"), 0.06f, 0.05f, 0.16f)
      GLES30.glUniform3f(GLES30.glGetUniformLocation(skyProgram, "uHorizonColor"), 0.22f, 0.16f, 0.36f)
    } else {
      GLES30.glUniform3f(GLES30.glGetUniformLocation(skyProgram, "uZenithColor"), 0.14f, 0.48f, 0.94f)
      GLES30.glUniform3f(GLES30.glGetUniformLocation(skyProgram, "uHorizonColor"), 0.72f, 0.89f, 1.0f)
    }
    GLES30.glUniform3f(GLES30.glGetUniformLocation(skyProgram, "uSunDir"), 0.52f, 0.62f, -0.58f)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(skyProgram, "uTime"), animTime)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(skyProgram, "uIsRaid"), if (isRaid) 1f else 0f)
    skyDomeMesh.draw()
    GLES30.glCullFace(GLES30.GL_BACK)
    GLES30.glDepthMask(true)

    // Shared cel lighting & atmospheric fog uniforms
    GLES30.glUseProgram(celProgram)
    GLES30.glUniform3f(GLES30.glGetUniformLocation(celProgram, "uLightDir"), 0.52f, 0.78f, -0.45f)
    GLES30.glUniform3f(GLES30.glGetUniformLocation(celProgram, "uCameraPos"), camX, camY, camZ)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uTime"), animTime)
    if (isRaid) {
      GLES30.glUniform3f(GLES30.glGetUniformLocation(celProgram, "uFogColor"), 0.14f, 0.12f, 0.28f)
      GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uFogDensity"), 0.009f)
    } else {
      GLES30.glUniform3f(GLES30.glGetUniformLocation(celProgram, "uFogColor"), 0.72f, 0.89f, 1.0f)
      GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uFogDensity"), 0.0075f)
    }

    // 2. Render Open World or Raid Arena
    if (isRaid) {
      renderRaidArenaScene()
    } else {
      renderOpenWorldScene(camX, camY, camZ)
    }

    // 3. Render Local Player Character + Soft Contact Shadow
    val floorUnderPlayer = if (isRaid) 0f else Math3D.openWorldHeight(playerX, playerZ)
    drawContactShadow(playerX, floorUnderPlayer + 0.04f, playerZ, 1.35f)

    val moveMag = sqrt(worldState.moveInputX * worldState.moveInputX + worldState.moveInputY * worldState.moveInputY)
    rigRenderer.drawAnimeHero(
      vpMat = vpMat,
      celProgram = celProgram,
      outlineProgram = outlineProgram,
      textures = textures,
      heroId = worldState.activeHeroId,
      weaponId = worldState.activeWeaponId,
      x = playerX,
      y = playerY,
      z = playerZ,
      yawDeg = playerYaw,
      moveSpeed = moveMag * 8f,
      animTime = animTime,
      attackTimer = attackTimer,
      comboStep = comboStep,
      isGliding = isGliding,
    )

    // 4. Render GPU Additive Particles
    particles.updateAndDraw(dt = dt, vpMatrix = vpMat, screenWidth = screenW, shaderProgram = particleProgram)
  }

  private fun renderOpenWorldScene(camX: Float, camY: Float, camZ: Float) {
    // Sculpted Open-World Terrain
    drawStaticMesh(
      terrainMesh, textures.grass, 0f, 0f, 0f, 1f, 1f, 1f,
      1f, 1f, 1f, specStrength = 0.04f, outline = false, uvScale = 1.4f,
    )

    // Shimmering Moonlit Lake (East-South Basin)
    GLES30.glEnable(GLES30.GL_BLEND)
    GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
    GLES30.glDepthMask(false)
    Matrix.setIdentityM(modelMat, 0)
    Matrix.translateM(modelMat, 0, 32f, -1.65f, 24f)
    Matrix.multiplyMM(mvpMat, 0, vpMat, 0, modelMat, 0)
    GLES30.glUseProgram(waterProgram)
    GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(waterProgram, "uMVP"), 1, false, mvpMat, 0)
    GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(waterProgram, "uModel"), 1, false, modelMat, 0)
    GLES30.glUniform3f(GLES30.glGetUniformLocation(waterProgram, "uCameraPos"), camX, camY, camZ)
    GLES30.glUniform3f(GLES30.glGetUniformLocation(waterProgram, "uLightDir"), 0.52f, 0.78f, -0.45f)
    GLES30.glUniform3f(GLES30.glGetUniformLocation(waterProgram, "uFogColor"), 0.72f, 0.89f, 1.0f)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(waterProgram, "uFogDensity"), 0.0075f)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(waterProgram, "uTime"), animTime)
    waterMesh.draw()
    GLES30.glDepthMask(true)
    GLES30.glDisable(GLES30.GL_BLEND)

    // Central Sanctuary Courtyard: Carved Stone Pillars + Gold Rings + Glowing Shrine Lanterns
    drawStaticMesh(torusMesh, textures.rune, 0f, 0.06f, 0f, 9.5f, 0.15f, 9.5f, 0.35f, 0.92f, 1f, emissive = 0.65f, outline = false)
    for (i in 0 until 8) {
      val a = (i * 45f) * Math3D.DEG_TO_RAD
      val px = cos(a) * 12.5f
      val pz = sin(a) * 12.5f
      val py = Math3D.openWorldHeight(px, pz)
      val h = if (i % 2 == 0) 5.6f else 3.8f
      drawStaticMesh(limbMesh, textures.stone, px, py + h * 0.5f, pz, 1.15f, h, 1.15f, 0.86f, 0.90f, 0.98f, specStrength = 0.35f, outline = true)
      // Gold Astral Capital & Floating Lantern Crystal on alternating pillars
      drawStaticMesh(torusMesh, textures.white, px, py + h, pz, 0.72f, 0.72f, 0.72f, 0.96f, 0.80f, 0.36f, specStrength = 0.85f, outline = false)
      if (i % 2 == 1) {
        drawStaticMesh(crystalMesh, textures.rune, px, py + h + 0.95f + sin(animTime * 2f + i) * 0.12f, pz, 0.42f, 0.75f, 0.42f, 0.35f, 0.95f, 1f, emissive = 1.0f, rotY = animTime * 45f, outline = false)
      }
    }

    // Grand Astral Torii Arch framing the Northern Raid Gate path
    val archZ = -18f
    val archY = Math3D.openWorldHeight(0f, archZ)
    drawStaticMesh(limbMesh, textures.stone, -3.4f, archY + 3.2f, archZ, 0.85f, 6.4f, 0.85f, 0.82f, 0.85f, 0.94f)
    drawStaticMesh(limbMesh, textures.stone, 3.4f, archY + 3.2f, archZ, 0.85f, 6.4f, 0.85f, 0.82f, 0.85f, 0.94f)
    drawStaticMesh(boxMesh, textures.stone, 0f, archY + 6.4f, archZ, 9.2f, 0.72f, 1.15f, 0.28f, 0.34f, 0.56f, specStrength = 0.5f)
    drawStaticMesh(boxMesh, textures.rune, 0f, archY + 5.2f, archZ, 7.6f, 0.42f, 0.72f, 0.96f, 0.80f, 0.36f, emissive = 0.45f)

    // Swaying 3D Stylized Grass Tufts across the sunlit hills
    GLES30.glDisable(GLES30.GL_CULL_FACE)
    for (g in grassTufts) {
      drawStaticMesh(
        grassTuftMesh, textures.white,
        g[0], g[1], g[2],
        g[3], g[3], g[3],
        0.90f, 1.0f, 0.85f,
        specStrength = 0.05f, outline = false, rotY = g[4], windSway = 0.22f,
      )
    }
    GLES30.glEnable(GLES30.GL_CULL_FACE)

    // Floating Sky Islands with Waterfalls/Crystals
    for ((idx, isl) in floatingIslands.withIndex()) {
      val hoverY = isl[1] + sin(animTime * 0.9f + idx) * 0.8f
      val sc = isl[3]
      drawStaticMesh(coneMesh, textures.stone, isl[0], hoverY - sc * 0.6f, isl[2], sc, -sc * 0.9f, sc, 0.62f, 0.66f, 0.80f, outline = true)
      drawStaticMesh(cylinderMesh, textures.grass, isl[0], hoverY - 0.5f, isl[2], sc * 1.05f, 1.0f, sc * 1.05f, 0.45f, 0.85f, 0.52f, outline = true)
      drawStaticMesh(crystalMesh, textures.rune, isl[0], hoverY + 2.5f, isl[2], 1.1f, 2.4f, 1.1f, 0.35f, 0.95f, 1f, emissive = 1.0f, rotY = animTime * 35f, outline = false)
    }

    // Lush Multi-Cluster Sakura & Emerald Pine Trees with Bark Trunks
    for ((idx, t) in treePositions.withIndex()) {
      val tx = t.first; val tz = t.second; val sc = t.third
      val ty = Math3D.openWorldHeight(tx, tz)
      drawStaticMesh(limbMesh, textures.bark, tx, ty + 1.5f * sc, tz, 0.55f * sc, 3.2f * sc, 0.55f * sc, 0.92f, 0.88f, 0.85f, specStrength = 0.08f, outline = true)
      val isSakura = idx % 2 == 0
      val leafTex = if (isSakura) textures.sakuraFoliage else textures.foliage
      val (r, g, b) = if (isSakura) Triple(1.0f, 0.94f, 0.98f) else Triple(0.92f, 1.0f, 0.94f)
      drawStaticMesh(sphereMesh, leafTex, tx, ty + 3.8f * sc, tz, 3.4f * sc, 2.3f * sc, 3.4f * sc, r, g, b, specStrength = 0.05f, windSway = 0.045f, outline = true)
      drawStaticMesh(sphereMesh, leafTex, tx - 1.1f * sc, ty + 3.2f * sc, tz + 0.8f * sc, 2.3f * sc, 1.8f * sc, 2.3f * sc, r, g, b, specStrength = 0.05f, windSway = 0.045f, outline = true)
      drawStaticMesh(sphereMesh, leafTex, tx + 1.1f * sc, ty + 3.3f * sc, tz - 0.7f * sc, 2.2f * sc, 1.8f * sc, 2.2f * sc, r, g, b, specStrength = 0.05f, windSway = 0.045f, outline = true)
    }

    // Teleport Waypoints
    for (wp in worldState.waypoints) {
      val unlocked = wp.id in worldState.unlockedWaypoints
      val wy = Math3D.openWorldHeight(wp.x, wp.z)
      drawStaticMesh(cylinderMesh, textures.stone, wp.x, wy, wp.z, 1.25f, 0.45f, 1.25f, 0.82f, 0.86f, 0.95f, outline = true)
      drawStaticMesh(
        crystalMesh, textures.rune,
        wp.x, wy + 2.2f + sin(animTime * 2.5f) * 0.2f, wp.z,
        0.65f, 1.5f, 0.65f,
        if (unlocked) 0.25f else 0.95f,
        if (unlocked) 0.95f else 0.32f,
        if (unlocked) 1.0f else 0.35f,
        emissive = 1.15f,
        specStrength = 0.9f,
        rotY = animTime * 75f,
      )
    }

    // Astral Treasure Chests
    for (c in worldState.chests) {
      if (c.id in worldState.openedChests) continue
      val cy = Math3D.openWorldHeight(c.x, c.z)
      drawStaticMesh(boxMesh, textures.stone, c.x, cy + 0.45f, c.z, 1.15f, 0.8f, 0.85f, 0.98f, 0.78f, 0.28f, emissive = 0.4f, specStrength = 0.8f, outline = true)
      drawStaticMesh(crystalMesh, textures.rune, c.x, cy + 1.5f + sin(animTime * 3f) * 0.15f, c.z, 0.35f, 0.55f, 0.35f, 1f, 0.88f, 0.35f, emissive = 1.1f, rotY = animTime * 90f)
    }

    // Co-op Raid Portal Monolith
    val (px, pz) = worldState.raidPortalPos
    val py = Math3D.openWorldHeight(px, pz)
    drawStaticMesh(torusMesh, textures.rune, px, py + 2.8f, pz, 2.5f, 2.5f, 2.5f, 0.55f, 0.48f, 1.0f, emissive = 1.25f, rotX = 90f, rotY = animTime * 35f, outline = false)
    drawStaticMesh(torusMesh, textures.rune, px, py + 2.8f, pz, 1.75f, 1.75f, 1.75f, 0.35f, 0.92f, 1.0f, emissive = 1.15f, rotX = 90f, rotY = -animTime * 55f, outline = false)
    drawStaticMesh(crystalMesh, textures.rune, px, py + 2.8f, pz, 0.85f, 1.6f, 0.85f, 0.78f, 0.62f, 1.0f, emissive = 1.3f, rotY = -animTime * 60f, outline = false)

    // Roaming Open-World Enemies + Contact Shadows
    for (e in enemies) {
      if (e.hp > 0) {
        val gy = Math3D.openWorldHeight(e.x, e.z)
        drawContactShadow(e.x, gy + 0.04f, e.z, if (e.isGolem) 2.2f else 1.2f)
        rigRenderer.drawMonster(vpMat, celProgram, outlineProgram, textures.stone, textures.rune, e, animTime)
      }
    }
  }

  private fun renderRaidArenaScene() {
    drawStaticMesh(arenaFloorMesh, textures.arenaObsidian, 0f, 0f, 0f, 1f, 1f, 1f, 0.88f, 0.92f, 1f, emissive = 0.16f, specStrength = 0.65f, outline = false)

    for (i in 0 until 8) {
      val a = (i * 45f) * Math3D.DEG_TO_RAD
      val px = cos(a) * 23f
      val pz = sin(a) * 23f
      drawStaticMesh(limbMesh, textures.stone, px, 4.5f, pz, 1.85f, 9f, 1.85f, 0.25f, 0.26f, 0.44f, specStrength = 0.5f, outline = true)
      drawStaticMesh(crystalMesh, textures.rune, px, 10.2f, pz, 0.85f, 1.45f, 0.85f, 0.42f, 0.88f, 1f, emissive = 1.15f, rotY = animTime * 50f, outline = false)
    }

    val room = worldState.activeRaidRoom
    val bossElem = ElementType.entries.firstOrNull { it.label == room?.bossElement } ?: ElementType.AERO
    if ((room?.bossHp ?: 1) > 0) {
      drawContactShadow(0f, 0.04f, 0f, 4.5f)
      rigRenderer.drawRaidBoss(
        vpMat = vpMat,
        celProgram = celProgram,
        outlineProgram = outlineProgram,
        stoneTex = textures.stone,
        runeTex = textures.rune,
        element = bossElem,
        phase = room?.phase ?: 1,
        animTime = animTime,
        hitFlash = bossHitFlash,
        telegraphPct = bossTelegraphTimer,
      )
    }

    for (member in worldState.remoteRaidMembers) {
      if (member.userId == worldState.localUserId) continue
      drawContactShadow(member.posX.toFloat(), 0.04f, member.posZ.toFloat(), 1.35f)
      rigRenderer.drawAnimeHero(
        vpMat = vpMat,
        celProgram = celProgram,
        outlineProgram = outlineProgram,
        textures = textures,
        heroId = member.heroId,
        weaponId = GameCatalog.heroById(member.heroId).defaultWeaponId,
        x = member.posX.toFloat(),
        y = member.posY.toFloat(),
        z = member.posZ.toFloat(),
        yawDeg = member.yaw.toFloat(),
        moveSpeed = if (member.lastAction == "idle") 0f else 5f,
        animTime = animTime,
        attackTimer = if (member.lastAction.startsWith("attack") || member.lastAction == "skill" || member.lastAction == "burst") 0.25f else 0f,
        comboStep = 1,
        isGliding = false,
      )
    }
  }

  private fun drawContactShadow(x: Float, y: Float, z: Float, scale: Float) {
    if (shadowProgram == 0) return
    Matrix.setIdentityM(modelMat, 0)
    Matrix.translateM(modelMat, 0, x, y, z)
    Matrix.scaleM(modelMat, 0, scale, 1f, scale)
    Matrix.multiplyMM(mvpMat, 0, vpMat, 0, modelMat, 0)
    Math3D.computeNormalMatrix(modelMat, normalMat)

    GLES30.glEnable(GLES30.GL_BLEND)
    GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
    GLES30.glDepthMask(false)
    GLES30.glUseProgram(shadowProgram)
    GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(shadowProgram, "uMVP"), 1, false, mvpMat, 0)
    GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(shadowProgram, "uModel"), 1, false, modelMat, 0)
    GLES30.glUniformMatrix3fv(GLES30.glGetUniformLocation(shadowProgram, "uNormalMat"), 1, false, normalMat, 0)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(shadowProgram, "uWindSway"), 0f)
    shadowQuadMesh.draw()
    GLES30.glDepthMask(true)
    GLES30.glDisable(GLES30.GL_BLEND)
  }

  private fun drawStaticMesh(
    mesh: GpuMesh,
    texId: Int,
    tx: Float,
    ty: Float,
    tz: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    r: Float,
    g: Float,
    b: Float,
    emissive: Float = 0f,
    specStrength: Float = 0.22f,
    outline: Boolean = true,
    rotX: Float = 0f,
    rotY: Float = 0f,
    uvScale: Float = 1f,
    windSway: Float = 0f,
  ) {
    Matrix.setIdentityM(modelMat, 0)
    Matrix.translateM(modelMat, 0, tx, ty, tz)
    if (rotY != 0f) Matrix.rotateM(modelMat, 0, rotY, 0f, 1f, 0f)
    if (rotX != 0f) Matrix.rotateM(modelMat, 0, rotX, 1f, 0f, 0f)
    Matrix.scaleM(modelMat, 0, sx, sy, sz)
    Matrix.multiplyMM(mvpMat, 0, vpMat, 0, modelMat, 0)
    Math3D.computeNormalMatrix(modelMat, normalMat)

    GLES30.glUseProgram(celProgram)
    GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(celProgram, "uMVP"), 1, false, mvpMat, 0)
    GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(celProgram, "uModel"), 1, false, modelMat, 0)
    GLES30.glUniformMatrix3fv(GLES30.glGetUniformLocation(celProgram, "uNormalMat"), 1, false, normalMat, 0)
    GLES30.glUniform4f(GLES30.glGetUniformLocation(celProgram, "uTint"), r, g, b, 1f)
    GLES30.glUniform3f(GLES30.glGetUniformLocation(celProgram, "uRimColor"), 0.80f, 0.94f, 1f)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uRimStrength"), 0.36f)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uSpecStrength"), specStrength)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uEmissive"), emissive)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uUvScale"), uvScale)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uWindSway"), windSway)
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
    GLES30.glUniform1i(GLES30.glGetUniformLocation(celProgram, "uTex"), 0)
    mesh.draw()

    if (outline) {
      GLES30.glCullFace(GLES30.GL_FRONT)
      GLES30.glUseProgram(outlineProgram)
      GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(outlineProgram, "uMVP"), 1, false, mvpMat, 0)
      GLES30.glUniform1f(GLES30.glGetUniformLocation(outlineProgram, "uOutlineWidth"), 0.015f)
      GLES30.glUniform4f(GLES30.glGetUniformLocation(outlineProgram, "uOutlineColor"), 0.10f, 0.11f, 0.22f, 1f)
      mesh.draw()
      GLES30.glCullFace(GLES30.GL_BACK)
    }
  }

  private fun emitHudSnapshot() {
    var nearby: InteractiveTarget? = null
    if (worldState.mode == WorldMode.OPEN_WORLD) {
      val (px, pz) = worldState.raidPortalPos
      if (Math3D.distXZ(playerX, playerZ, px, pz) < 4.5f) {
        nearby = InteractiveTarget("raid_portal", "portal", "Enter Co-op Raid Sanctum")
      }
      if (nearby == null) {
        for (c in worldState.chests) {
          if (c.id !in worldState.openedChests && Math3D.distXZ(playerX, playerZ, c.x, c.z) < 3.6f) {
            nearby = InteractiveTarget("chest", c.id, "Open ${c.name}")
            break
          }
        }
      }
      if (nearby == null) {
        for (wp in worldState.waypoints) {
          if (wp.id !in worldState.unlockedWaypoints && Math3D.distXZ(playerX, playerZ, wp.x, wp.z) < 4.0f) {
            nearby = InteractiveTarget("waypoint", wp.id, "Resonate: ${wp.name}")
            break
          }
        }
      }
    }

    val projected = ArrayList<FloatingDamageText>(popups.size)
    val worldPt = FloatArray(4)
    val clipPt = FloatArray(4)
    for (p in popups) {
      worldPt[0] = p.wx; worldPt[1] = p.wy; worldPt[2] = p.wz; worldPt[3] = 1f
      Matrix.multiplyMV(clipPt, 0, vpMat, 0, worldPt, 0)
      if (clipPt[3] > 0.1f) {
        val ndcX = clipPt[0] / clipPt[3]
        val ndcY = clipPt[1] / clipPt[3]
        val sx = (ndcX * 0.5f + 0.5f) * screenW
        val sy = (1f - (ndcY * 0.5f + 0.5f)) * screenH
        projected.add(FloatingDamageText(p.id, p.text, sx, sy, p.colorHex, p.isReaction, p.age))
      }
    }

    worldState.onHudUpdate?.invoke(
      HudSnapshot(
        mode = worldState.mode,
        playerHp = playerHp,
        playerMaxHp = playerMaxHp,
        stamina = stamina,
        energy = energy,
        skillCooldown = skillCooldown,
        burstCooldown = burstCooldown,
        isGliding = isGliding,
        isGrounded = isGrounded,
        playerX = playerX,
        playerZ = playerZ,
        playerYaw = playerYaw,
        camYaw = worldState.camYaw,
        nearbyTarget = nearby,
        floatingNumbers = projected,
        bossTelegraphPct = bossTelegraphTimer,
        localDamageDealt = localDamageDealt,
        lastAction = lastActionTag,
      )
    )
  }
}
