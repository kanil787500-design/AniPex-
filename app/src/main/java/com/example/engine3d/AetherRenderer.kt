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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class AetherRenderer(private val worldState: GameWorldState) : GLSurfaceView.Renderer {

  private var celProgram = 0
  private var outlineProgram = 0
  private var shadowDepthProgram = 0
  private var skyProgram = 0
  private var waterProgram = 0
  private var particleProgram = 0
  private var shadowProgram = 0
  private var bloomExtractProgram = 0
  private var bloomBlurProgram = 0
  private var compositeProgram = 0

  private lateinit var textures: ProceduralTextures.TextureSet
  private lateinit var terrainMesh: GpuMesh
  private lateinit var sanctuaryDaisMesh: GpuMesh
  private lateinit var arenaFloorMesh: GpuMesh
  private lateinit var waterMesh: GpuMesh
  private lateinit var skyDomeMesh: GpuMesh
  private lateinit var boxMesh: GpuMesh
  private lateinit var sphereMesh: GpuMesh
  private lateinit var boulderMeshA: GpuMesh
  private lateinit var boulderMeshB: GpuMesh
  private lateinit var animeHeadMesh: GpuMesh
  private lateinit var hairCrownMesh: GpuMesh
  private lateinit var limbMesh: GpuMesh
  private lateinit var boneLimbMesh: GpuMesh
  private lateinit var footBootMesh: GpuMesh
  private lateinit var handGloveMesh: GpuMesh
  private lateinit var torsoMesh: GpuMesh
  private lateinit var openCoatMesh: GpuMesh
  private lateinit var skirtMesh: GpuMesh
  private lateinit var hairLockMesh: GpuMesh
  private lateinit var swordBladeMesh: GpuMesh
  private lateinit var slashArcMesh: GpuMesh
  private lateinit var grassTuftMesh: GpuMesh
  private lateinit var treeTrunkMesh: GpuMesh
  private lateinit var treeCanopyCardsMesh: GpuMesh
  private lateinit var bushCardsMesh: GpuMesh
  private lateinit var shadowQuadMesh: GpuMesh
  private lateinit var coneMesh: GpuMesh
  private lateinit var cylinderMesh: GpuMesh
  private lateinit var crystalMesh: GpuMesh
  private lateinit var torusMesh: GpuMesh
  private var quadVao = 0

  // Offscreen FBOs for HDR Bloom, Supersampling (Direct 3x Mod) & Filmic Compositing
  private var viewW = 1280
  private var viewH = 720
  private var sceneW = 1280
  private var sceneH = 720
  private var lastQualityTier = worldState.graphicsQuality
  private var sceneFbo = 0
  private var sceneColorTex = 0
  private var sceneDepthRb = 0
  private var bloomFboA = 0
  private var bloomTexA = 0
  private var bloomFboB = 0
  private var bloomTexB = 0
  private var bloomReady = false

  // Directional Sun Shadow Map FBO (2048x2048 Ultra-Crisp)
  private val shadowSize = 2048
  private var shadowFbo = 0
  private var shadowDepthTex = 0
  private var shadowReady = false

  private lateinit var rigRenderer: CharacterRigRenderer
  private val particles = ParticleSystem3D(2600)

  private val viewMat = FloatArray(16)
  private val projMat = FloatArray(16)
  private val vpMat = FloatArray(16)
  private val lightViewMat = FloatArray(16)
  private val lightProjMat = FloatArray(16)
  private val lightVpMat = FloatArray(16)
  private val skyVpMat = FloatArray(16)
  private val modelMat = FloatArray(16)
  private val mvpMat = FloatArray(16)
  private val normalMat = FloatArray(9)

  // Sun light vector (normalized) & screen-space sun shaft projection
  private val sunDir = floatArrayOf(0.50f, 0.75f, -0.43f)
  private var sunScreenX = 0.5f
  private var sunScreenY = 0.5f
  private var sunVisible = 0f

  private var screenW = 1920f
  private var screenH = 1080f
  private var currentFov = 48f
  private var lastTimeNs = 0L
  private var animTime = 0f
  private var hudEmitAccum = 0f

  // Player simulation state
  private var playerX = 0f
  private var playerY = 0f
  private var playerZ = 6f
  private var playerYaw = 16f
  private var playerVelX = 0f
  private var playerVelZ = 0f
  private var playerVelY = 0f
  private var horizSpeed = 0f
  private var gaitPhase = 0f
  private var coyoteTimer = 0f
  private var landingSquash = 0f
  private var prevInputYaw = 180f
  private var leanZ = 0f
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

  private data class TreePlacement(val x: Float, val z: Float, val scale: Float, val rotY: Float, val isSakura: Boolean)

  private val treePlacements =
    listOf(
      TreePlacement(-15.5f, -10.5f, 1.30f, 15f, true),
      TreePlacement(-15.5f, 8.5f, 1.25f, 75f, true),
      TreePlacement(15.5f, -11.0f, 1.35f, 130f, true),
      TreePlacement(15.5f, 7.5f, 1.20f, 200f, false),
      TreePlacement(-14.0f, 23.0f, 1.38f, 42f, true),
      TreePlacement(12.0f, 25.0f, 1.25f, 95f, false),
      TreePlacement(42.0f, -18.0f, 1.50f, 165f, false),
      TreePlacement(-42.0f, 14.0f, 1.45f, 255f, true),
      TreePlacement(32.0f, -40.5f, 1.35f, 305f, false),
      TreePlacement(-32.5f, -38.5f, 1.40f, 60f, true),
      TreePlacement(52.0f, 32.0f, 1.45f, 115f, false),
      TreePlacement(-46.0f, -14.0f, 1.35f, 210f, true),
      TreePlacement(-7.8f, -22.5f, 1.15f, 285f, true), // Downtown West sidewalk sakura
      TreePlacement(7.8f, -40.5f, 1.18f, 35f, true), // Downtown East sidewalk sakura
      TreePlacement(-25.0f, -2.0f, 1.28f, 145f, false),
      TreePlacement(26.0f, -2.5f, 1.32f, 240f, true),
    )

  // Modern Curved Streetlamps along the Sanctuary Boulevard & NTE Downtown Avenue
  private val streetLampPositions =
    listOf(
      Triple(-6.5f, -9.0f, 0f),
      Triple(6.5f, -9.0f, 180f),
      Triple(-6.5f, -15.0f, 0f),
      Triple(6.5f, -15.0f, 180f),
      Triple(-10.5f, 4.5f, 45f),
      Triple(10.5f, 4.5f, 135f),
      Triple(-5.0f, -24.5f, 0f),
      Triple(5.0f, -24.5f, 180f),
      Triple(-5.0f, -36.5f, 0f),
      Triple(5.0f, -36.5f, 180f),
      Triple(-5.0f, -49.0f, 0f),
      Triple(5.0f, -49.0f, 180f),
    )

  private val bushPositions =
    listOf(
      Triple(-11.8f, -5.5f, 1.15f),
      Triple(11.8f, -5.5f, 1.15f),
      Triple(-11.5f, 6.2f, 1.25f),
      Triple(11.5f, 6.2f, 1.25f),
      Triple(-5.8f, -17.5f, 1.20f),
      Triple(5.8f, -17.5f, 1.20f),
      Triple(17.5f, 13.5f, 1.35f),
      Triple(19.5f, 25.0f, 1.30f),
      Triple(-18.5f, -12.0f, 1.25f),
      Triple(18.5f, -13.0f, 1.30f),
    )

  private val boulderPositions =
    listOf(
      floatArrayOf(-15f, -3f, 1.35f),
      floatArrayOf(16f, 16f, 1.55f),
      floatArrayOf(11f, 30f, 1.40f),
      floatArrayOf(-26f, -24f, 1.85f),
      floatArrayOf(28f, -19f, 1.65f),
      floatArrayOf(-33f, 35f, 1.90f),
    )

  private val windUpdrafts =
    listOf(
      Pair(14f, 10f),
      Pair(-20f, -18f),
      Pair(30f, -12f),
    )

  private val grassTufts: List<FloatArray> =
    run {
      val rng = Random(2026)
      val list = ArrayList<FloatArray>(145)
      var attempts = 0
      while (list.size < 145 && attempts < 800) {
        attempts++
        val gx = (rng.nextFloat() - 0.5f) * 95f
        val gz = (rng.nextFloat() - 0.5f) * 95f
        if (Math3D.distXZ(gx, gz, 0f, 0f) < 13.5f) continue
        if (abs(gx) < 16f && gz in -60f..-14f) continue // keep NTE downtown avenue & sidewalks clear
        if (gz in -35f..-23f && abs(gx) < 42f) continue // keep E-W downtown avenue clear
        if (Math3D.distXZ(gx, gz, 32f, 24f) < 22f) continue
        val gy = Math3D.openWorldHeight(gx, gz)
        val sc = 0.85f + rng.nextFloat() * 0.78f
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

  private val openWorldObstacles: List<CylinderObstacle> =
    buildList {
      for (i in 0 until 8) {
        val a = (i * 45f) * Math3D.DEG_TO_RAD
        add(CylinderObstacle(cos(a) * 12.5f, sin(a) * 12.5f, 0.65f))
      }
      add(CylinderObstacle(-3.4f, -18f, 0.55f))
      add(CylinderObstacle(3.4f, -18f, 0.55f))
      for (wp in worldState.waypoints) {
        add(CylinderObstacle(wp.x, wp.z, 0.85f))
      }
      for (t in treePlacements) {
        add(CylinderObstacle(t.x, t.z, 0.45f * t.scale))
      }
      for (b in boulderPositions) {
        add(CylinderObstacle(b[0], b[1], b[2] * 0.85f))
      }
      for (lamp in streetLampPositions) {
        add(CylinderObstacle(lamp.first, lamp.second, 0.30f))
      }
      for (bld in CityDistrictRenderer.urbanBuildings) {
        add(CylinderObstacle(bld.x, bld.z, maxOf(bld.w, bld.d) * 0.55f))
      }
      for (car in CityDistrictRenderer.parkedCars) {
        add(CylinderObstacle(car.x, car.z, 1.45f))
      }
      for (px in CityDistrictRenderer.monorailPylonsX) {
        add(CylinderObstacle(px, -45.5f, 1.05f))
      }
      for (px in CityDistrictRenderer.skybridgePylonsX) {
        add(CylinderObstacle(px, CityDistrictRenderer.SKYBRIDGE_Z, 0.55f))
      }
      for (kiosk in CityDistrictRenderer.cityKiosks) {
        add(CylinderObstacle(kiosk.first, kiosk.second, 0.90f))
      }
    }

  private val raidObstacles: List<CylinderObstacle> =
    buildList {
      add(CylinderObstacle(0f, 0f, 2.0f))
      for (i in 0 until 8) {
        val a = (i * 45f) * Math3D.DEG_TO_RAD
        add(CylinderObstacle(cos(a) * 23f, sin(a) * 23f, 1.05f))
      }
    }

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
    shadowDepthProgram = Shaders.compileProgram(Shaders.SHADOW_DEPTH_VERTEX, Shaders.SHADOW_DEPTH_FRAGMENT)
    skyProgram = Shaders.compileProgram(Shaders.SKY_VERTEX, Shaders.SKY_FRAGMENT)
    waterProgram = Shaders.compileProgram(Shaders.WATER_VERTEX, Shaders.WATER_FRAGMENT)
    particleProgram = Shaders.compileProgram(Shaders.PARTICLE_VERTEX, Shaders.PARTICLE_FRAGMENT)
    shadowProgram = Shaders.compileProgram(Shaders.CEL_VERTEX, Shaders.SHADOW_FRAGMENT)
    bloomExtractProgram = Shaders.compileProgram(Shaders.QUAD_VERTEX, Shaders.BLOOM_EXTRACT_FRAGMENT)
    bloomBlurProgram = Shaders.compileProgram(Shaders.QUAD_VERTEX, Shaders.BLOOM_BLUR_FRAGMENT)
    compositeProgram = Shaders.compileProgram(Shaders.QUAD_VERTEX, Shaders.COMPOSITE_FRAGMENT)

    textures = ProceduralTextures.createAll()
    terrainMesh = MeshBuilder.createOpenWorldTerrain(152)
    sanctuaryDaisMesh = MeshBuilder.createSanctuaryDais()
    arenaFloorMesh = MeshBuilder.createArenaFloor()
    waterMesh = MeshBuilder.createWaterDisc()
    skyDomeMesh = MeshBuilder.createSkyDome()
    boxMesh = MeshBuilder.createBox()
    sphereMesh = MeshBuilder.createSphere()
    boulderMeshA = MeshBuilder.createBoulder(seed = 7)
    boulderMeshB = MeshBuilder.createBoulder(seed = 23)
    animeHeadMesh = MeshBuilder.createAnimeHead()
    hairCrownMesh = MeshBuilder.createAnimeHairCrown()
    limbMesh = MeshBuilder.createTaperedLimb()
    boneLimbMesh = MeshBuilder.createBoneLimb()
    footBootMesh = MeshBuilder.createFootBoot()
    handGloveMesh = MeshBuilder.createHandGloveMesh()
    torsoMesh = MeshBuilder.createSculptedTorso()
    openCoatMesh = MeshBuilder.createOpenTrenchCoat()
    skirtMesh = MeshBuilder.createFlaredSkirt()
    hairLockMesh = MeshBuilder.createHairLock()
    swordBladeMesh = MeshBuilder.createSwordBlade()
    slashArcMesh = MeshBuilder.createSlashArc()
    grassTuftMesh = MeshBuilder.createGrassTuft()
    treeTrunkMesh = MeshBuilder.createBranchingTreeTrunk()
    treeCanopyCardsMesh = MeshBuilder.createTreeCanopyCards()
    bushCardsMesh = MeshBuilder.createBushCards()
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
        hairCrownMesh = hairCrownMesh,
        limbMesh = limbMesh,
        boneLimbMesh = boneLimbMesh,
        footBootMesh = footBootMesh,
        handGloveMesh = handGloveMesh,
        torsoMesh = torsoMesh,
        openCoatMesh = openCoatMesh,
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
    setupShadowMapFramebuffer()

    enemies.forEach { it.y = Math3D.walkableWorldHeight(it.x, it.z) }
    worldState.npcs.forEach { it.y = Math3D.walkableWorldHeight(it.x, it.z) }
    playerY = Math3D.walkableWorldHeight(playerX, playerZ)
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

  private fun setupShadowMapFramebuffer() {
    releaseShadowFramebuffer()
    if (shadowDepthProgram == 0) {
      shadowReady = false
      return
    }
    try {
      val fbos = IntArray(1)
      val texs = IntArray(1)
      GLES30.glGenFramebuffers(1, fbos, 0)
      GLES30.glGenTextures(1, texs, 0)
      shadowFbo = fbos[0]
      shadowDepthTex = texs[0]

      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, shadowDepthTex)
      GLES30.glTexImage2D(
        GLES30.GL_TEXTURE_2D,
        0,
        GLES30.GL_DEPTH_COMPONENT24,
        shadowSize,
        shadowSize,
        0,
        GLES30.GL_DEPTH_COMPONENT,
        GLES30.GL_UNSIGNED_INT,
        null,
      )
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_MODE, GLES30.GL_NONE)

      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
      GLES30.glFramebufferTexture2D(
        GLES30.GL_FRAMEBUFFER,
        GLES30.GL_DEPTH_ATTACHMENT,
        GLES30.GL_TEXTURE_2D,
        shadowDepthTex,
        0,
      )
      GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_NONE), 0)
      GLES30.glReadBuffer(GLES30.GL_NONE)
      val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
      shadowReady = (status == GLES30.GL_FRAMEBUFFER_COMPLETE)
    } catch (_: Exception) {
      shadowReady = false
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }
  }

  private fun releaseShadowFramebuffer() {
    if (shadowFbo != 0) {
      GLES30.glDeleteFramebuffers(1, intArrayOf(shadowFbo), 0)
      GLES30.glDeleteTextures(1, intArrayOf(shadowDepthTex), 0)
      shadowFbo = 0
      shadowDepthTex = 0
    }
    shadowReady = false
  }

  private fun updateSceneDimensions(w: Int, h: Int) {
    val scale =
      when (worldState.graphicsQuality) {
        GraphicsQualityTier.ULTRA_3X -> 1.25f
        GraphicsQualityTier.HIGH_2X -> 1.0f
        GraphicsQualityTier.STANDARD_1X -> 0.75f
      }
    sceneW = (w * scale).toInt().coerceIn(64, 2400)
    sceneH = (h * scale).toInt().coerceIn(64, 1440)
    lastQualityTier = worldState.graphicsQuality
  }

  private fun setupBloomFramebuffers(w: Int, h: Int) {
    releaseBloomFramebuffers()
    updateSceneDimensions(w, h)
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

      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneColorTex)
      GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, sceneW, sceneH, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
      GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

      GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, sceneDepthRb)
      GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT24, sceneW, sceneH)

      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFbo)
      GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, sceneColorTex, 0)
      GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, sceneDepthRb)
      val sceneStatus = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)

      val bw = (sceneW / 4).coerceAtLeast(64)
      val bh = (sceneH / 4).coerceAtLeast(64)
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

    // Dynamically apply Direct 3x / 2x / 1x Graphics Mod resolution tier if toggled in HUD
    if (worldState.graphicsQuality != lastQualityTier) {
      setupBloomFramebuffers(viewW, viewH)
    }

    val azRad = worldState.celLightAzimuthDeg * Math3D.DEG_TO_RAD
    val elRad = 48f * Math3D.DEG_TO_RAD
    val cosEl = cos(elRad)
    sunDir[0] = cos(azRad) * cosEl
    sunDir[1] = sin(elRad)
    sunDir[2] = -sin(azRad) * cosEl

    updateSimulation(dt)

    if (shadowReady) {
      renderShadowPass()
    } else {
      Matrix.setIdentityM(lightVpMat, 0)
    }

    if (bloomReady) {
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, sceneFbo)
      GLES30.glViewport(0, 0, sceneW, sceneH)
      renderScene(dt)
      runBloomAndComposite()
    } else {
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
      GLES30.glViewport(0, 0, viewW, viewH)
      renderScene(dt)
    }
  }

  private fun renderShadowPass() {
    val isRaid = worldState.mode == WorldMode.RAID_ARENA
    val focusX = if (isRaid) 0f else playerX
    val focusY = if (isRaid) 0f else playerY
    val focusZ = if (isRaid) 0f else playerZ

    val texelWorld = 72f / shadowSize
    val snappedX = floor(focusX / texelWorld) * texelWorld
    val snappedZ = floor(focusZ / texelWorld) * texelWorld

    Matrix.setLookAtM(
      lightViewMat, 0,
      snappedX + sunDir[0] * 58f,
      focusY + sunDir[1] * 58f,
      snappedZ + sunDir[2] * 58f,
      snappedX, focusY, snappedZ,
      0f, 1f, 0f,
    )
    Matrix.orthoM(lightProjMat, 0, -36f, 36f, -36f, 36f, 4f, 130f)
    Matrix.multiplyMM(lightVpMat, 0, lightProjMat, 0, lightViewMat, 0)

    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, shadowFbo)
    GLES30.glViewport(0, 0, shadowSize, shadowSize)
    GLES30.glColorMask(false, false, false, false)
    GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
    GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
    GLES30.glPolygonOffset(2.5f, 5.0f)

    if (isRaid) {
      for (i in 0 until 8) {
        val a = (i * 45f) * Math3D.DEG_TO_RAD
        drawShadowMesh(limbMesh, cos(a) * 23f, 4.5f, sin(a) * 23f, 1.85f, 9f, 1.85f)
      }
      if ((worldState.activeRaidRoom?.bossHp ?: 1) > 0) {
        val bossElem = ElementType.entries.firstOrNull { it.label == worldState.activeRaidRoom?.bossElement } ?: ElementType.AERO
        rigRenderer.beginShadowPass(shadowDepthProgram, lightVpMat)
        rigRenderer.drawRaidBoss(
          vpMat, celProgram, outlineProgram, textures.stone, textures.rune,
          bossElem, worldState.activeRaidRoom?.phase ?: 1, animTime, bossHitFlash, bossTelegraphTimer,
        )
        rigRenderer.endShadowPass()
      }
    } else {
      for (i in 0 until 8) {
        val a = (i * 45f) * Math3D.DEG_TO_RAD
        val px = cos(a) * 12.5f
        val pz = sin(a) * 12.5f
        val py = Math3D.openWorldHeight(px, pz)
        val h = if (i % 2 == 0) 5.6f else 3.8f
        drawShadowMesh(limbMesh, px, py + h * 0.5f, pz, 1.15f, h, 1.15f)
      }
      val archZ = -18f
      val archY = Math3D.openWorldHeight(0f, archZ)
      drawShadowMesh(limbMesh, -3.4f, archY + 3.2f, archZ, 0.85f, 6.4f, 0.85f)
      drawShadowMesh(limbMesh, 3.4f, archY + 3.2f, archZ, 0.85f, 6.4f, 0.85f)
      drawShadowMesh(boxMesh, 0f, archY + 6.4f, archZ, 9.2f, 0.72f, 1.15f)

      // NTE Urban Buildings, Pedestrian Skybridge & Elevated Monorail Viaduct cast skyline shadows
      for (bld in CityDistrictRenderer.urbanBuildings) {
        val by = Math3D.openWorldHeight(bld.x, bld.z)
        drawShadowMesh(boxMesh, bld.x, by + bld.h * 0.5f - 0.5f, bld.z, bld.w, bld.h, bld.d, rotY = bld.rotY)
      }
      drawShadowMesh(boxMesh, 0f, 7.0f, CityDistrictRenderer.SKYBRIDGE_Z, 17.6f, 0.65f, 2.3f)
      for (px in CityDistrictRenderer.skybridgePylonsX) {
        drawShadowMesh(cylinderMesh, px, 0f, CityDistrictRenderer.SKYBRIDGE_Z, 0.45f, 7.0f, 0.45f)
      }
      drawShadowMesh(boxMesh, 0f, 14.5f, -45.5f, 92f, 1.4f, 3.2f)
      for (px in CityDistrictRenderer.monorailPylonsX) {
        drawShadowMesh(cylinderMesh, px, 0f, -45.5f, 1.25f, 14.2f, 1.25f)
      }

      // Sculpted Branching Tree Trunks + Alpha-Cutout Dappled Leaf Shadows!
      for (t in treePlacements) {
        if (Math3D.distXZ(t.x, t.z, playerX, playerZ) > 54f) continue
        val ty = Math3D.openWorldHeight(t.x, t.z)
        drawShadowMesh(treeTrunkMesh, t.x, ty, t.z, t.scale, t.scale, t.scale, rotY = t.rotY)
        val cardTex = if (t.isSakura) textures.sakuraCard else textures.foliageCard
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        drawShadowMesh(
          treeCanopyCardsMesh, t.x, ty, t.z, t.scale, t.scale, t.scale,
          rotY = t.rotY, windSway = 0.03f, alphaCutout = true, alphaTex = cardTex,
        )
        GLES30.glEnable(GLES30.GL_CULL_FACE)
      }

      for ((idx, b) in boulderPositions.withIndex()) {
        val bx = b[0]; val bz = b[1]; val sc = b[2]
        if (Math3D.distXZ(bx, bz, playerX, playerZ) > 50f) continue
        val by = Math3D.openWorldHeight(bx, bz)
        drawShadowMesh(if (idx % 2 == 0) boulderMeshA else boulderMeshB, bx, by + 0.45f * sc, bz, sc, sc * 0.75f, sc)
      }
      for (e in enemies) {
        if (e.hp > 0 && Math3D.distXZ(e.x, e.z, playerX, playerZ) < 42f) {
          rigRenderer.beginShadowPass(shadowDepthProgram, lightVpMat)
          rigRenderer.drawMonster(vpMat, celProgram, outlineProgram, textures.stone, textures.rune, e, animTime)
          rigRenderer.endShadowPass()
        }
      }
      for (npc in worldState.npcs) {
        val dist = Math3D.distXZ(npc.x, npc.z, playerX, playerZ)
        if (dist < 38f) {
          rigRenderer.beginShadowPass(shadowDepthProgram, lightVpMat)
          rigRenderer.drawAnimeNpc(vpMat, celProgram, outlineProgram, textures, npc, animTime, dist < 3.5f)
          rigRenderer.endShadowPass()
        }
      }
    }

    rigRenderer.beginShadowPass(shadowDepthProgram, lightVpMat)
    rigRenderer.drawAnimeHero(
      vpMat = vpMat,
      celProgram = celProgram,
      outlineProgram = outlineProgram,
      textures = textures,
      heroId = worldState.activeHeroId,
      weaponId = worldState.activeWeaponId,
      state = buildHeroAnimState(),
    )
    rigRenderer.endShadowPass()

    GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
    GLES30.glColorMask(true, true, true, true)
    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
  }

  private fun drawShadowMesh(
    mesh: GpuMesh,
    tx: Float,
    ty: Float,
    tz: Float,
    sx: Float,
    sy: Float,
    sz: Float,
    rotY: Float = 0f,
    windSway: Float = 0f,
    alphaCutout: Boolean = false,
    alphaTex: Int = 0,
  ) {
    Matrix.setIdentityM(modelMat, 0)
    Matrix.translateM(modelMat, 0, tx, ty, tz)
    if (rotY != 0f) Matrix.rotateM(modelMat, 0, rotY, 0f, 1f, 0f)
    Matrix.scaleM(modelMat, 0, sx, sy, sz)
    Matrix.multiplyMM(mvpMat, 0, lightVpMat, 0, modelMat, 0)
    GLES30.glUseProgram(shadowDepthProgram)
    GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(shadowDepthProgram, "uLightMVP"), 1, false, mvpMat, 0)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(shadowDepthProgram, "uTime"), animTime)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(shadowDepthProgram, "uWindSway"), windSway)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(shadowDepthProgram, "uAlphaCutout"), if (alphaCutout) 1f else 0f)
    if (alphaCutout && alphaTex != 0) {
      GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, alphaTex)
      GLES30.glUniform1i(GLES30.glGetUniformLocation(shadowDepthProgram, "uTex"), 0)
    }
    mesh.draw()
  }

  private fun runBloomAndComposite() {
    val bw = (sceneW / 4).coerceAtLeast(64)
    val bh = (sceneH / 4).coerceAtLeast(64)

    GLES30.glDisable(GLES30.GL_DEPTH_TEST)
    GLES30.glDisable(GLES30.GL_CULL_FACE)
    GLES30.glBindVertexArray(quadVao)

    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFboA)
    GLES30.glViewport(0, 0, bw, bh)
    GLES30.glUseProgram(bloomExtractProgram)
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneColorTex)
    GLES30.glUniform1i(GLES30.glGetUniformLocation(bloomExtractProgram, "uSceneTex"), 0)
    GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_INT, 0)

    GLES30.glUseProgram(bloomBlurProgram)
    val texStepLoc = GLES30.glGetUniformLocation(bloomBlurProgram, "uTexelStep")
    val blurTexLoc = GLES30.glGetUniformLocation(bloomBlurProgram, "uTex")

    for (pass in 0 until 2) {
      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFboB)
      GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bloomTexA)
      GLES30.glUniform1i(blurTexLoc, 0)
      GLES30.glUniform2f(texStepLoc, 1.25f / bw, 0f)
      GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_INT, 0)

      GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bloomFboA)
      GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bloomTexB)
      GLES30.glUniform2f(texStepLoc, 0f, 1.25f / bh)
      GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_INT, 0)
    }

    GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    GLES30.glViewport(0, 0, viewW, viewH)
    GLES30.glUseProgram(compositeProgram)
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sceneColorTex)
    GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uSceneTex"), 0)
    GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bloomTexA)
    GLES30.glUniform1i(GLES30.glGetUniformLocation(compositeProgram, "uBloomTex"), 1)
    GLES30.glUniform2f(GLES30.glGetUniformLocation(compositeProgram, "uTexelSize"), 1f / sceneW, 1f / sceneH)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProgram, "uQualityScale"), worldState.graphicsQuality.scale.toFloat())
    GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProgram, "uCinemaMode"), if (worldState.cinematicMode) 1f else 0f)
    GLES30.glUniform2f(GLES30.glGetUniformLocation(compositeProgram, "uSunScreenPos"), sunScreenX, sunScreenY)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(compositeProgram, "uSunVisible"), sunVisible)
    GLES30.glDrawElements(GLES30.GL_TRIANGLES, 6, GLES30.GL_UNSIGNED_INT, 0)

    GLES30.glBindVertexArray(0)
    GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    GLES30.glEnable(GLES30.GL_CULL_FACE)
  }

  private fun updateSimulation(dt: Float) {
    val mode = worldState.mode
    if (mode != lastMode) {
      lastMode = mode
      playerVelX = 0f
      playerVelZ = 0f
      playerVelY = 0f
      isGrounded = true
      isGliding = false
      coyoteTimer = 0f
      landingSquash = 0f
      if (mode == WorldMode.RAID_ARENA) {
        playerX = 0f; playerZ = 14f; playerY = 0f
        localDamageDealt = 0
        bossAttackCycle = 0f; bossTelegraphTimer = 0f
      } else {
        playerX = 0f; playerZ = 6f
        playerY = Math3D.walkableWorldHeight(playerX, playerZ)
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
    if (coyoteTimer > 0f) coyoteTimer = (coyoteTimer - dt).coerceAtLeast(0f)
    if (landingSquash > 0f) landingSquash = (landingSquash - dt * 4.8f).coerceAtLeast(0f)

    while (true) {
      val cmd = worldState.commandQueue.poll() ?: break
      handleCommand(cmd, stats)
    }

    val ix = worldState.moveInputX
    val iy = worldState.moveInputY
    val inputMag = sqrt(ix * ix + iy * iy).coerceAtMost(1f)
    val sprinting = inputMag > 0.82f && stamina > 5f && !isGliding
    if (sprinting) stamina = (stamina - 16f * dt).coerceAtLeast(0f)
    else if (isGliding) stamina = (stamina - 6.5f * dt).coerceAtLeast(0f)
    else stamina = (stamina + 22f * dt).coerceAtMost(100f)

    val maxSpeed =
      when {
        dashTimer > 0f -> 18.5f
        isGliding -> 9.4f
        sprinting -> 10.6f
        else -> 6.8f
      }

    var targetVx = 0f
    var targetVz = 0f
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

      val speedScalar = if (dashTimer > 0f) 1f else inputMag
      targetVx = nx * maxSpeed * speedScalar
      targetVz = nz * maxSpeed * speedScalar

      val targetYaw = atan2(nx, nz) * Math3D.RAD_TO_DEG
      val turnDiff = (((targetYaw - prevInputYaw + 540f) % 360f) - 180f)
      leanZ = Math3D.lerp(leanZ, (turnDiff / 45f).coerceIn(-1f, 1f), (dt * 8f).coerceIn(0f, 1f))
      prevInputYaw = targetYaw
      playerYaw = Math3D.lerpAngleDeg(playerYaw, targetYaw, (dt * 15f).coerceIn(0f, 1f))
    } else {
      leanZ = Math3D.lerp(leanZ, 0f, (dt * 10f).coerceIn(0f, 1f))
    }

    val accelRate =
      when {
        dashTimer > 0f -> 36f
        isGrounded -> if (inputMag > 0.05f) 16f else 13f
        else -> if (inputMag > 0.05f) 5.5f else 2.2f
      }
    val blend = (accelRate * dt).coerceIn(0f, 1f)
    playerVelX = Math3D.lerp(playerVelX, targetVx, blend)
    playerVelZ = Math3D.lerp(playerVelZ, targetVz, blend)

    playerX += playerVelX * dt
    playerZ += playerVelZ * dt

    if (mode == WorldMode.RAID_ARENA) {
      val resolved = Math3D.resolveObstacles(playerX, playerZ, 0.45f, raidObstacles)
      playerX = resolved.first
      playerZ = resolved.second
      val r = sqrt(playerX * playerX + playerZ * playerZ)
      if (r > 23.5f) {
        playerX *= 23.5f / r
        playerZ *= 23.5f / r
      }
    } else {
      val resolved = Math3D.resolveObstacles(playerX, playerZ, 0.45f, openWorldObstacles)
      playerX = resolved.first.coerceIn(-98f, 98f)
      playerZ = resolved.second.coerceIn(-98f, 98f)
    }

    horizSpeed = sqrt(playerVelX * playerVelX + playerVelZ * playerVelZ)
    if (isGrounded && horizSpeed > 0.15f) {
      val prevCycle = floor(gaitPhase / PI.toFloat()).toInt()
      gaitPhase += dt * (4.8f + horizSpeed * 1.15f)
      val nextCycle = floor(gaitPhase / PI.toFloat()).toInt()
      if (nextCycle != prevCycle && horizSpeed > 2.2f) {
        particles.spawn(
          playerX, playerY + 0.08f, playerZ,
          (Random.nextFloat() - 0.5f) * 1.1f, 0.7f, (Random.nextFloat() - 0.5f) * 1.1f,
          0.36f, 0.24f + horizSpeed * 0.015f, 0.88f, 0.90f, 0.80f, 0.52f, -1.2f,
        )
      }
    }

    val floorY = if (mode == WorldMode.RAID_ARENA) 0f else Math3D.walkableWorldHeight(playerX, playerZ)

    if (mode == WorldMode.OPEN_WORLD) {
      for (ring in windUpdrafts) {
        val d = Math3D.distXZ(playerX, playerZ, ring.first, ring.second)
        val ringBaseY = Math3D.walkableWorldHeight(ring.first, ring.second)
        if (d < 3.6f && playerY < ringBaseY + 13.5f && (!isGrounded || playerVelY > 0.5f || inputMag > 0.05f)) {
          isGrounded = false
          isGliding = true
          stamina = (stamina + 14f * dt).coerceAtMost(100f)
          playerVelY = Math3D.lerp(playerVelY, 9.6f, (dt * 5.8f).coerceIn(0f, 1f))
          if (Random.nextFloat() < 0.65f) {
            particles.spawn(
              ring.first + (Random.nextFloat() - 0.5f) * 2.8f,
              playerY + Random.nextFloat() * 1.2f,
              ring.second + (Random.nextFloat() - 0.5f) * 2.8f,
              0f, 6.5f, 0f,
              0.7f, 0.22f, 0.38f, 0.96f, 0.88f, 0.75f,
            )
          }
        }
      }
    }

    if (isGrounded) {
      val drop = playerY - floorY
      if (drop > 0.30f) {
        isGrounded = false
        coyoteTimer = 0.14f
      } else {
        playerY = floorY
      }
    } else {
      val grav = if (isGliding && playerVelY < 0f) -4.2f else -28.0f
      playerVelY += grav * dt
      if (isGliding && playerVelY < -2.6f) playerVelY = -2.6f
      playerY += playerVelY * dt
      if (playerY <= floorY) {
        val impactSpeed = abs(playerVelY)
        playerY = floorY
        playerVelY = 0f
        isGrounded = true
        isGliding = false
        coyoteTimer = 0f
        if (impactSpeed > 6.5f) {
          landingSquash = ((impactSpeed - 5.5f) / 15f).coerceIn(0.22f, 1f)
          particles.emitShockwaveRing(playerX, playerY + 0.08f, playerZ, 1.4f + impactSpeed * 0.1f, 0.85f, 0.92f, 0.96f, 18)
        }
      }
    }

    if (Random.nextFloat() < 0.76f) {
      val px = playerX + (Random.nextFloat() - 0.5f) * 32f
      val pz = playerZ + (Random.nextFloat() - 0.5f) * 32f
      val py = playerY + 1.2f + Random.nextFloat() * 8f
      val isPetal = mode == WorldMode.OPEN_WORLD && Random.nextFloat() < 0.50f
      particles.spawn(
        px, py, pz,
        0.9f + Random.nextFloat() * 0.9f, -0.4f, 0.35f,
        2.8f, if (isPetal) 0.25f else 0.19f,
        if (isPetal) 1.0f else 0.58f,
        if (isPetal) 0.74f else 0.96f,
        if (isPetal) 0.86f else 0.92f,
        0.78f,
      )
    }

    if (mode == WorldMode.OPEN_WORLD) {
      updateOpenWorldEnemies(dt)
      updateNpcs(dt)
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
        playerY = Math3D.walkableWorldHeight(cmd.x, cmd.z)
        playerVelX = 0f; playerVelZ = 0f; playerVelY = 0f
        isGrounded = true; isGliding = false; landingSquash = 0.25f
        particles.emitElementalVortex(playerX, playerY, playerZ, 0.35f, 0.95f, 1f, 65)
      }
      GameInputCommand.JumpOrGlide -> {
        if (isGrounded || coyoteTimer > 0f) {
          isGrounded = false
          coyoteTimer = 0f
          playerVelY = 11.4f
          lastActionTag = "jump"
          particles.emitShockwaveRing(playerX, playerY + 0.08f, playerZ, 1.3f, 0.86f, 0.95f, 1.0f, 16)
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
      is GameInputCommand.ApplyNpcBenefit -> {
        when (cmd.rewardType) {
          "heal" -> {
            playerHp = playerMaxHp
            particles.emitElementalVortex(playerX, playerY + 0.3f, playerZ, 0.32f, 0.96f, 0.65f, 48)
          }
          "energy" -> {
            energy = (energy + 35f).coerceAtMost(100f)
            particles.emitElementalVortex(playerX, playerY + 0.3f, playerZ, 0.35f, 0.92f, 1.0f, 48)
          }
          "stamina" -> {
            stamina = 100f
            particles.emitShockwaveRing(playerX, playerY + 0.1f, playerZ, 2.2f, 1.0f, 0.88f, 0.35f, 36)
          }
          else -> {
            energy = (energy + 45f).coerceAtMost(100f)
            stamina = 100f
            playerHp = (playerHp + 180).coerceAtMost(playerMaxHp)
            particles.emitElementalVortex(playerX, playerY + 0.3f, playerZ, 1.0f, 0.85f, 0.38f, 56)
          }
        }
      }
    }
  }

  private fun updateNpcs(dt: Float) {
    for (npc in worldState.npcs) {
      val dist = Math3D.distXZ(playerX, playerZ, npc.x, npc.z)
      if (dist < 3.5f) {
        val facePlayer = Math3D.yawToward(npc.x, npc.z, playerX, playerZ)
        npc.yaw = Math3D.lerpAngleDeg(npc.yaw, facePlayer, (dt * 7f).coerceIn(0f, 1f))
      } else if (npc.moveSpeed > 0.05f) {
        val tx = if (npc.walkForward) npc.patrolX else npc.homeX
        val tz = if (npc.walkForward) npc.patrolZ else npc.homeZ
        val dx = tx - npc.x
        val dz = tz - npc.z
        val remain = sqrt(dx * dx + dz * dz)
        if (remain < 0.35f) {
          npc.walkForward = !npc.walkForward
        } else {
          val targetYaw = Math3D.yawToward(npc.x, npc.z, tx, tz)
          npc.yaw = Math3D.lerpAngleDeg(npc.yaw, targetYaw, (dt * 6f).coerceIn(0f, 1f))
          npc.x += (dx / remain) * npc.moveSpeed * dt
          npc.z += (dz / remain) * npc.moveSpeed * dt
          npc.y = Math3D.walkableWorldHeight(npc.x, npc.z)
          npc.gaitPhase += dt * (4.2f + npc.moveSpeed * 1.4f)
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
          e.y = Math3D.walkableWorldHeight(e.x, e.z)
        }
        continue
      }

      val dist = Math3D.distXZ(playerX, playerZ, e.x, e.z)
      if (dist < 14f && dist > 2.2f) {
        e.yaw = Math3D.yawToward(e.x, e.z, playerX, playerZ)
        val spd = if (e.isGolem) 2.2f else 3.4f
        e.x += sin(e.yaw * Math3D.DEG_TO_RAD) * spd * dt
        e.z += cos(e.yaw * Math3D.DEG_TO_RAD) * spd * dt
        e.y = Math3D.walkableWorldHeight(e.x, e.z)
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

  private fun buildHeroAnimState(): HeroAnimState {
    return HeroAnimState(
      x = playerX,
      y = playerY,
      z = playerZ,
      yawDeg = playerYaw,
      horizSpeed = horizSpeed,
      velY = playerVelY,
      isGrounded = isGrounded,
      isGliding = isGliding,
      landingSquash = landingSquash,
      leanX = 0f,
      leanZ = leanZ,
      gaitPhase = gaitPhase,
      animTime = animTime,
      attackTimer = attackTimer,
      comboStep = comboStep,
    )
  }

  private fun renderScene(dt: Float) {
    val isRaid = worldState.mode == WorldMode.RAID_ARENA
    if (isRaid) {
      GLES30.glClearColor(0.06f, 0.07f, 0.14f, 1f)
    } else {
      GLES30.glClearColor(0.42f, 0.72f, 0.96f, 1f)
    }
    GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

    val targetFov =
      (if (worldState.cinematicMode) 42.5f else 48.0f) +
        (horizSpeed / 10.6f).coerceIn(0f, 1f) * 6.5f +
        (if (dashTimer > 0f) 5.5f else 0f) +
        (if (isGliding) 4.0f else 0f)
    currentFov = Math3D.lerp(currentFov, targetFov, (dt * 7.5f).coerceIn(0f, 1f))
    Matrix.perspectiveM(projMat, 0, currentFov, screenW / screenH.coerceAtLeast(1f), 0.35f, 340f)

    val yawRad = worldState.camYaw * Math3D.DEG_TO_RAD
    val pitchRad = worldState.camPitch * Math3D.DEG_TO_RAD
    val dist = if (worldState.cinematicMode) (worldState.camDistance * 0.88f).coerceAtLeast(3.0f) else worldState.camDistance
    val lookTargetY = playerY + if (worldState.cinematicMode) 1.24f else 1.18f
    val camX = playerX + sin(yawRad) * cos(pitchRad) * dist
    val camZ = playerZ + cos(yawRad) * cos(pitchRad) * dist
    var camY = lookTargetY + sin(pitchRad) * dist
    val groundUnderCam = if (isRaid) 0.5f else Math3D.walkableWorldHeight(camX, camZ) + 0.8f
    if (camY < groundUnderCam) camY = groundUnderCam

    Matrix.setLookAtM(viewMat, 0, camX, camY, camZ, playerX, lookTargetY, playerZ, 0f, 1f, 0f)
    Matrix.multiplyMM(vpMat, 0, projMat, 0, viewMat, 0)

    // Project Sun position into screen UV for AAA radial light shafts in COMPOSITE_FRAGMENT
    val sunWorld = floatArrayOf(camX + sunDir[0] * 140f, camY + sunDir[1] * 140f, camZ + sunDir[2] * 140f, 1f)
    val sunClip = FloatArray(4)
    Matrix.multiplyMV(sunClip, 0, vpMat, 0, sunWorld, 0)
    if (!isRaid && sunClip[3] > 0.1f) {
      val sx = (sunClip[0] / sunClip[3]) * 0.5f + 0.5f
      val sy = (sunClip[1] / sunClip[3]) * 0.5f + 0.5f
      sunScreenX = sx
      sunScreenY = sy
      val edgeDist = maxOf(abs(sx - 0.5f), abs(sy - 0.5f))
      sunVisible = (1f - Math3D.smoothstep(0.45f, 1.05f, edgeDist)).coerceIn(0f, 1f)
    } else {
      sunVisible = 0f
    }

    // 1. Skybox Dome
    GLES30.glDepthMask(false)
    GLES30.glCullFace(GLES30.GL_FRONT)
    Matrix.setIdentityM(modelMat, 0)
    Matrix.translateM(modelMat, 0, camX, camY, camZ)
    Matrix.multiplyMM(skyVpMat, 0, vpMat, 0, modelMat, 0)
    GLES30.glUseProgram(skyProgram)
    GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(skyProgram, "uMVP"), 1, false, skyVpMat, 0)
    if (isRaid) {
      GLES30.glUniform3f(GLES30.glGetUniformLocation(skyProgram, "uZenithColor"), 0.05f, 0.05f, 0.14f)
      GLES30.glUniform3f(GLES30.glGetUniformLocation(skyProgram, "uHorizonColor"), 0.20f, 0.14f, 0.32f)
    } else {
      GLES30.glUniform3f(GLES30.glGetUniformLocation(skyProgram, "uZenithColor"), 0.10f, 0.34f, 0.78f)
      GLES30.glUniform3f(GLES30.glGetUniformLocation(skyProgram, "uHorizonColor"), 0.70f, 0.84f, 0.96f)
    }
    GLES30.glUniform3f(GLES30.glGetUniformLocation(skyProgram, "uSunDir"), sunDir[0], sunDir[1], sunDir[2])
    GLES30.glUniform1f(GLES30.glGetUniformLocation(skyProgram, "uTime"), animTime)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(skyProgram, "uIsRaid"), if (isRaid) 1f else 0f)
    skyDomeMesh.draw()
    GLES30.glCullFace(GLES30.GL_BACK)
    GLES30.glDepthMask(true)

    // Shared cel/PBR lighting, PCF shadow map, quality scale, cinema mode & atmospheric fog uniforms
    GLES30.glUseProgram(celProgram)
    GLES30.glUniform3f(GLES30.glGetUniformLocation(celProgram, "uLightDir"), sunDir[0], sunDir[1], sunDir[2])
    GLES30.glUniform3f(GLES30.glGetUniformLocation(celProgram, "uCameraPos"), camX, camY, camZ)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uTime"), animTime)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uQualityScale"), worldState.graphicsQuality.scale.toFloat())
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uCinemaMode"), if (worldState.cinematicMode) 1f else 0f)
    GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(celProgram, "uLightVP"), 1, false, lightVpMat, 0)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uShadowTexelSize"), 1f / shadowSize)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uUseShadow"), if (shadowReady) 1f else 0f)
    GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, if (shadowReady) shadowDepthTex else textures.white)
    GLES30.glUniform1i(GLES30.glGetUniformLocation(celProgram, "uShadowMap"), 1)
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

    if (isRaid) {
      GLES30.glUniform3f(GLES30.glGetUniformLocation(celProgram, "uFogColor"), 0.14f, 0.12f, 0.28f)
      GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uFogDensity"), 0.0085f)
    } else {
      GLES30.glUniform3f(GLES30.glGetUniformLocation(celProgram, "uFogColor"), 0.70f, 0.84f, 0.96f)
      GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uFogDensity"), 0.0056f)
    }

    if (isRaid) {
      renderRaidArenaScene()
    } else {
      renderOpenWorldScene(camX, camY, camZ)
    }

    // Local Hero + Contact Shadow
    val floorUnderPlayer = if (isRaid) 0f else Math3D.walkableWorldHeight(playerX, playerZ)
    val heightOffFloor = (playerY - floorUnderPlayer).coerceAtLeast(0f)
    val shadowScale = (1.30f - heightOffFloor * 0.08f).coerceIn(0.45f, 1.30f)
    drawContactShadow(playerX, floorUnderPlayer + 0.04f, playerZ, shadowScale)

    rigRenderer.drawAnimeHero(
      vpMat = vpMat,
      celProgram = celProgram,
      outlineProgram = outlineProgram,
      textures = textures,
      heroId = worldState.activeHeroId,
      weaponId = worldState.activeWeaponId,
      state = buildHeroAnimState(),
    )

    particles.updateAndDraw(dt = dt, vpMatrix = vpMat, screenWidth = screenW, shaderProgram = particleProgram)
  }

  private fun renderOpenWorldScene(camX: Float, camY: Float, camZ: Float) {
    // 1. Sculpted Open-World Terrain (MaterialMode = 1f: wind-rippled meadows + cliff strata)
    drawStaticMesh(
      terrainMesh, textures.grass, 0f, 0f, 0f, 1f, 1f, 1f,
      1f, 1f, 1f, specStrength = 0.04f, outline = false, uvScale = 1.4f, materialMode = 1f,
    )

    // 2. Stepped NTE Urban-Fantasy Sanctuary Plaza Dais + Avenue Boulevard Walkway
    drawStaticMesh(
      sanctuaryDaisMesh, textures.plazaTile, 0f, 0f, 0f, 1f, 1f, 1f,
      0.95f, 0.96f, 1f, specStrength = 0.35f, outline = false, uvScale = 2.4f,
    )
    drawStaticMesh(torusMesh, textures.rune, 0f, 0.60f, 0f, 7.6f, 0.14f, 7.6f, 0.35f, 0.92f, 1f, emissive = 0.72f, outline = false)

    // Paved NTE Avenue Boulevard leading North from Dais to the Astral Torii Gate
    drawStaticMesh(
      boxMesh, textures.plazaTile, 0f, 0.06f, -15.5f,
      7.4f, 0.18f, 14.5f, 0.88f, 0.91f, 0.98f, specStrength = 0.30f, outline = false, uvScale = 2.2f,
    )
    // Avenue Glowing Cyan Curb Strip Accents
    for (dir in listOf(-1f, 1f)) {
      drawStaticMesh(
        boxMesh, textures.white, dir * 3.75f, 0.14f, -15.5f,
        0.16f, 0.18f, 14.5f, 0.35f, 0.94f, 0.98f, emissive = 0.62f, outline = false,
      )
    }

    // 3. Shimmering Moonlit Lake + Scenic Pier Deck
    drawStaticMesh(
      boxMesh, textures.stone, 20.5f, -1.25f, 18.0f,
      6.5f, 0.35f, 2.8f, 0.76f, 0.64f, 0.56f, specStrength = 0.22f, rotY = -36f, outline = false,
    )
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
    GLES30.glUniform3f(GLES30.glGetUniformLocation(waterProgram, "uLightDir"), sunDir[0], sunDir[1], sunDir[2])
    GLES30.glUniform3f(GLES30.glGetUniformLocation(waterProgram, "uFogColor"), 0.70f, 0.84f, 0.96f)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(waterProgram, "uFogDensity"), 0.0056f)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(waterProgram, "uTime"), animTime)
    waterMesh.draw()
    GLES30.glDepthMask(true)
    GLES30.glDisable(GLES30.GL_BLEND)

    // 4. NTE Downtown City District (Wet Asphalt Grid, Crosswalks, Skybridge Gantry, Building LED Screens, Banners, Train & Cruisers)
    CityDistrictRenderer.render(
      animTime = animTime,
      textures = textures,
      boxMesh = boxMesh,
      sphereMesh = sphereMesh,
      cylinderMesh = cylinderMesh,
      coneMesh = coneMesh,
      torusMesh = torusMesh,
      streetLampPositions = streetLampPositions,
      drawStaticMesh = { mesh, texId, tx, ty, tz, sx, sy, sz, r, g, b, emissive, specStrength, outline, rotX, rotY, uvScale, windSway, materialMode, alphaCutout ->
        drawStaticMesh(
          mesh = mesh, texId = texId, tx = tx, ty = ty, tz = tz,
          sx = sx, sy = sy, sz = sz, r = r, g = g, b = b,
          emissive = emissive, specStrength = specStrength, outline = outline,
          rotX = rotX, rotY = rotY, uvScale = uvScale, windSway = windSway,
          materialMode = materialMode, alphaCutout = alphaCutout,
        )
      },
    )

    // 5. Modern Curved Urban Boulevard Streetlamps with Warm Amber Bloom Globes
    for (lamp in streetLampPositions) {
      val lx = lamp.first; val lz = lamp.second; val rot = lamp.third
      val ly = Math3D.walkableWorldHeight(lx, lz)
      val rad = rot * Math3D.DEG_TO_RAD
      val armOffX = cos(rad) * 0.65f
      val armOffZ = -sin(rad) * 0.65f
      drawStaticMesh(cylinderMesh, textures.stone, lx, ly, lz, 0.16f, 4.6f, 0.16f, 0.26f, 0.30f, 0.42f, specStrength = 0.65f, outline = false)
      drawStaticMesh(boxMesh, textures.stone, lx + armOffX * 0.5f, ly + 4.55f, lz + armOffZ * 0.5f, 0.95f, 0.14f, 0.14f, 0.28f, 0.32f, 0.45f, rotY = rot, outline = false)
      drawStaticMesh(sphereMesh, textures.white, lx + armOffX, ly + 4.42f, lz + armOffZ, 0.34f, 0.24f, 0.34f, 1.0f, 0.88f, 0.56f, emissive = 1.25f, outline = false)
    }

    // 6. Central Sanctuary Stone Pillars + Astral Crystals
    for (i in 0 until 8) {
      val a = (i * 45f) * Math3D.DEG_TO_RAD
      val px = cos(a) * 12.5f
      val pz = sin(a) * 12.5f
      val py = Math3D.openWorldHeight(px, pz)
      val h = if (i % 2 == 0) 5.6f else 3.8f
      drawStaticMesh(limbMesh, textures.stone, px, py + h * 0.5f, pz, 1.15f, h, 1.15f, 0.86f, 0.90f, 0.96f, specStrength = 0.42f, outline = false)
      drawStaticMesh(torusMesh, textures.white, px, py + h, pz, 0.72f, 0.72f, 0.72f, 0.96f, 0.80f, 0.36f, specStrength = 0.88f, outline = false, materialMode = 5f)
      if (i % 2 == 1) {
        drawStaticMesh(
          crystalMesh, textures.rune,
          px, py + h + 0.95f + sin(animTime * 2f + i) * 0.12f, pz,
          0.42f, 0.75f, 0.42f, 0.35f, 0.95f, 1f, emissive = 1.05f, rotY = animTime * 45f, outline = false,
        )
      }
    }

    // 7. Grand Astral Torii Arch framing the Northern Raid Gate path
    val archZ = -18f
    val archY = Math3D.openWorldHeight(0f, archZ)
    drawStaticMesh(limbMesh, textures.stone, -3.4f, archY + 3.2f, archZ, 0.85f, 6.4f, 0.85f, 0.82f, 0.85f, 0.92f, specStrength = 0.38f, outline = false)
    drawStaticMesh(limbMesh, textures.stone, 3.4f, archY + 3.2f, archZ, 0.85f, 6.4f, 0.85f, 0.82f, 0.85f, 0.92f, specStrength = 0.38f, outline = false)
    drawStaticMesh(boxMesh, textures.stone, 0f, archY + 6.4f, archZ, 9.2f, 0.72f, 1.15f, 0.28f, 0.34f, 0.52f, specStrength = 0.52f, outline = false)
    drawStaticMesh(boxMesh, textures.rune, 0f, archY + 5.2f, archZ, 7.6f, 0.42f, 0.72f, 0.96f, 0.80f, 0.36f, emissive = 0.55f, outline = false)

    // 8. Sculpted Mossy Cliff Boulders & Companion Stone Lanterns (photoreal granite relief)
    for ((idx, b) in boulderPositions.withIndex()) {
      val bx = b[0]; val bz = b[1]; val sc = b[2]
      val by = Math3D.openWorldHeight(bx, bz)
      drawStaticMesh(
        if (idx % 2 == 0) boulderMeshA else boulderMeshB,
        textures.stone, bx, by + 0.42f * sc, bz,
        sc, sc * 0.75f, sc, 0.74f, 0.76f, 0.80f, specStrength = 0.22f, rotY = idx * 55f, outline = false,
      )
      drawStaticMesh(cylinderMesh, textures.stone, bx + sc * 1.1f, by, bz + sc * 0.6f, 0.4f, 0.9f, 0.4f, 0.75f, 0.78f, 0.85f, outline = false)
      drawStaticMesh(crystalMesh, textures.rune, bx + sc * 1.1f, by + 1.15f, bz + sc * 0.6f, 0.22f, 0.32f, 0.22f, 1.0f, 0.82f, 0.35f, emissive = 1.15f, outline = false)
    }

    // 9. Interactive Anemo Wind Updraft Rings
    for ((idx, ring) in windUpdrafts.withIndex()) {
      val wy = Math3D.walkableWorldHeight(ring.first, ring.second)
      drawStaticMesh(
        torusMesh, textures.rune,
        ring.first, wy + 0.35f, ring.second,
        3.2f, 0.25f, 3.2f, 0.32f, 0.96f, 0.88f,
        emissive = 1.05f, rotY = animTime * (65f + idx * 15f), outline = false,
      )
      drawStaticMesh(
        torusMesh, textures.rune,
        ring.first, wy + 2.6f + sin(animTime * 3f + idx) * 0.4f, ring.second,
        2.3f, 0.16f, 2.3f, 0.45f, 0.98f, 0.92f,
        emissive = 0.95f, rotY = -animTime * 90f, outline = false,
      )
    }

    // 10. Floating Sky Islands (photoreal granite undersides)
    for ((idx, isl) in floatingIslands.withIndex()) {
      val hoverY = isl[1] + sin(animTime * 0.9f + idx) * 0.8f
      val sc = isl[3]
      drawStaticMesh(coneMesh, textures.stone, isl[0], hoverY - sc * 0.6f, isl[2], sc, -sc * 0.9f, sc, 0.72f, 0.74f, 0.82f, specStrength = 0.24f, outline = false)
      drawStaticMesh(cylinderMesh, textures.grass, isl[0], hoverY - 0.5f, isl[2], sc * 1.05f, 1.0f, sc * 1.05f, 0.82f, 0.92f, 0.82f, specStrength = 0.12f, outline = false)
      drawStaticMesh(crystalMesh, textures.rune, isl[0], hoverY + 2.5f, isl[2], 1.1f, 2.4f, 1.1f, 0.35f, 0.95f, 1f, emissive = 1.0f, rotY = animTime * 35f, outline = false)
    }

    // 11. Gnarled Branching Trees + Lush Alpha-Cutout Foliage Spray Canopies & Flowering Shrubs
    for (t in treePlacements) {
      val ty = Math3D.openWorldHeight(t.x, t.z)
      // Organic flared trunk & diverging branches (PBR bump bark, no thick cartoon ink)
      drawStaticMesh(
        treeTrunkMesh, textures.bark,
        t.x, ty, t.z, t.scale, t.scale, t.scale,
        0.94f, 0.88f, 0.84f, specStrength = 0.12f, rotY = t.rotY, outline = false,
      )
    }
    // Render crossed foliage spray cards double-sided with alpha-cutout
    GLES30.glDisable(GLES30.GL_CULL_FACE)
    for (t in treePlacements) {
      val ty = Math3D.openWorldHeight(t.x, t.z)
      val cardTex = if (t.isSakura) textures.sakuraCard else textures.foliageCard
      drawStaticMesh(
        treeCanopyCardsMesh, cardTex,
        t.x, ty, t.z, t.scale, t.scale, t.scale,
        1f, 1f, 1f, specStrength = 0.08f, rotY = t.rotY, windSway = 0.035f, outline = false, alphaCutout = true,
      )
    }
    // Lush flowering avenue/lakeside shrubs
    for ((idx, b) in bushPositions.withIndex()) {
      val by = Math3D.openWorldHeight(b.first, b.second)
      val cardTex = if (idx % 2 == 0) textures.sakuraCard else textures.foliageCard
      drawStaticMesh(
        bushCardsMesh, cardTex,
        b.first, by, b.second, b.third, b.third, b.third,
        1f, 1f, 1f, specStrength = 0.08f, rotY = idx * 42f, windSway = 0.05f, outline = false, alphaCutout = true,
      )
    }
    // Swaying 3D grass tufts
    for (g in grassTufts) {
      drawStaticMesh(
        grassTuftMesh, textures.white,
        g[0], g[1], g[2],
        g[3], g[3], g[3],
        0.74f, 0.92f, 0.68f,
        specStrength = 0.10f, outline = false, rotY = g[4], windSway = 0.24f,
      )
    }
    GLES30.glEnable(GLES30.GL_CULL_FACE)

    // 12. Teleport Waypoints, Treasure Chests, Raid Portal Monolith & Enemies
    for (wp in worldState.waypoints) {
      val unlocked = wp.id in worldState.unlockedWaypoints
      val wy = Math3D.openWorldHeight(wp.x, wp.z)
      drawStaticMesh(cylinderMesh, textures.stone, wp.x, wy, wp.z, 1.25f, 0.45f, 1.25f, 0.82f, 0.86f, 0.95f, specStrength = 0.45f, outline = false)
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
        outline = false,
      )
    }

    for (c in worldState.chests) {
      if (c.id in worldState.openedChests) continue
      val cy = Math3D.openWorldHeight(c.x, c.z)
      drawStaticMesh(boxMesh, textures.stone, c.x, cy + 0.45f, c.z, 1.15f, 0.8f, 0.85f, 0.98f, 0.78f, 0.28f, emissive = 0.4f, specStrength = 0.85f, outline = false, materialMode = 5f)
      drawStaticMesh(crystalMesh, textures.rune, c.x, cy + 1.5f + sin(animTime * 3f) * 0.15f, c.z, 0.35f, 0.55f, 0.35f, 1f, 0.88f, 0.35f, emissive = 1.1f, rotY = animTime * 90f, outline = false)
    }

    val (px, pz) = worldState.raidPortalPos
    val py = Math3D.openWorldHeight(px, pz)
    drawStaticMesh(torusMesh, textures.rune, px, py + 2.8f, pz, 2.5f, 2.5f, 2.5f, 0.55f, 0.48f, 1.0f, emissive = 1.25f, rotX = 90f, rotY = animTime * 35f, outline = false)
    drawStaticMesh(torusMesh, textures.rune, px, py + 2.8f, pz, 1.75f, 1.75f, 1.75f, 0.35f, 0.92f, 1.0f, emissive = 1.15f, rotX = 90f, rotY = -animTime * 55f, outline = false)
    drawStaticMesh(crystalMesh, textures.rune, px, py + 2.8f, pz, 0.85f, 1.6f, 0.85f, 0.78f, 0.62f, 1.0f, emissive = 1.3f, rotY = -animTime * 60f, outline = false)

    for (e in enemies) {
      if (e.hp > 0) {
        val gy = Math3D.walkableWorldHeight(e.x, e.z)
        drawContactShadow(e.x, gy + 0.04f, e.z, if (e.isGolem) 2.2f else 1.2f)
        rigRenderer.drawMonster(vpMat, celProgram, outlineProgram, textures.stone, textures.rune, e, animTime)
      }
    }

    // 13. Living Anime City & Sanctuary NPCs (Sidewalk Strollers, Cafe Barista, Metro Guides & Vanguard)
    for (npc in worldState.npcs) {
      val dist = Math3D.distXZ(playerX, playerZ, npc.x, npc.z)
      if (dist < 65f) {
        val ny = Math3D.walkableWorldHeight(npc.x, npc.z)
        drawContactShadow(npc.x, ny + 0.04f, npc.z, 1.15f)
        rigRenderer.drawAnimeNpc(vpMat, celProgram, outlineProgram, textures, npc, animTime, playerNearby = dist < 3.5f)
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
      val remoteSpeed = if (member.lastAction == "idle") 0f else 6.5f
      rigRenderer.drawAnimeHero(
        vpMat = vpMat,
        celProgram = celProgram,
        outlineProgram = outlineProgram,
        textures = textures,
        heroId = member.heroId,
        weaponId = GameCatalog.heroById(member.heroId).defaultWeaponId,
        state =
          HeroAnimState(
            x = member.posX.toFloat(),
            y = member.posY.toFloat(),
            z = member.posZ.toFloat(),
            yawDeg = member.yaw.toFloat(),
            horizSpeed = remoteSpeed,
            velY = 0f,
            isGrounded = true,
            isGliding = false,
            landingSquash = 0f,
            leanX = 0f,
            leanZ = 0f,
            gaitPhase = animTime * 9f,
            animTime = animTime,
            attackTimer = if (member.lastAction.startsWith("attack") || member.lastAction == "skill" || member.lastAction == "burst") 0.25f else 0f,
            comboStep = 1,
          ),
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
    GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(shadowProgram, "uLightVP"), 1, false, lightVpMat, 0)
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
    materialMode: Float = 0f,
    alphaCutout: Boolean = false,
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
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uMaterialMode"), materialMode)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uAlphaCutout"), if (alphaCutout) 1f else 0f)
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
    GLES30.glUniform1i(GLES30.glGetUniformLocation(celProgram, "uTex"), 0)
    mesh.draw()

    if (outline) {
      GLES30.glCullFace(GLES30.GL_FRONT)
      GLES30.glUseProgram(outlineProgram)
      GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(outlineProgram, "uMVP"), 1, false, mvpMat, 0)
      GLES30.glUniform1f(GLES30.glGetUniformLocation(outlineProgram, "uOutlineWidth"), 0.013f)
      GLES30.glUniform4f(GLES30.glGetUniformLocation(outlineProgram, "uOutlineColor"), 0.09f, 0.10f, 0.20f, 1f)
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
      if (nearby == null) {
        for (npc in worldState.npcs) {
          if (Math3D.distXZ(playerX, playerZ, npc.x, npc.z) < 3.5f) {
            nearby = InteractiveTarget("npc", npc.id, "Talk: ${npc.name}")
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
