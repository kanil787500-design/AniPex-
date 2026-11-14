package com.example.engine3d

import android.opengl.GLES30
import android.opengl.Matrix
import com.example.data.ElementType
import com.example.data.GameCatalog
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class HeroAnimState(
  val x: Float,
  val y: Float,
  val z: Float,
  val yawDeg: Float,
  val horizSpeed: Float,
  val velY: Float,
  val isGrounded: Boolean,
  val isGliding: Boolean,
  val landingSquash: Float,
  val leanX: Float,
  val leanZ: Float,
  val gaitPhase: Float,
  val animTime: Float,
  val attackTimer: Float,
  val comboStep: Int,
)

class CharacterRigRenderer(
  private val boxMesh: GpuMesh,
  private val sphereMesh: GpuMesh,
  private val animeHeadMesh: GpuMesh,
  private val hairCrownMesh: GpuMesh,
  private val limbMesh: GpuMesh,
  private val boneLimbMesh: GpuMesh,
  private val footBootMesh: GpuMesh,
  private val handGloveMesh: GpuMesh,
  private val torsoMesh: GpuMesh,
  private val openCoatMesh: GpuMesh,
  private val skirtMesh: GpuMesh,
  private val hairLockMesh: GpuMesh,
  private val swordBladeMesh: GpuMesh,
  private val slashArcMesh: GpuMesh,
  private val coneMesh: GpuMesh,
  private val cylinderMesh: GpuMesh,
  private val crystalMesh: GpuMesh,
  private val torusMesh: GpuMesh,
) {
  private val rootMat = FloatArray(16)
  private val pelvisMat = FloatArray(16)
  private val torsoMat = FloatArray(16)
  private val headMat = FloatArray(16)
  private val jointAMat = FloatArray(16)
  private val jointBMat = FloatArray(16)
  private val jointCMat = FloatArray(16)
  private val partMat = FloatArray(16)
  private val mvpMat = FloatArray(16)
  private val normalMat = FloatArray(9)

  private var isShadowPass = false
  private var shadowDepthProgram = 0
  private val activeLightVp = FloatArray(16)

  fun beginShadowPass(shadowProgram: Int, lightVp: FloatArray) {
    isShadowPass = true
    shadowDepthProgram = shadowProgram
    System.arraycopy(lightVp, 0, activeLightVp, 0, 16)
  }

  fun endShadowPass() {
    isShadowPass = false
  }

  fun drawCelPart(
    mesh: GpuMesh,
    vpMat: FloatArray,
    celProgram: Int,
    outlineProgram: Int,
    texId: Int,
    r: Float,
    g: Float,
    b: Float,
    emissive: Float = 0f,
    rimColor: FloatArray = floatArrayOf(0.8f, 0.92f, 1f),
    rimStrength: Float = 0.56f,
    specStrength: Float = 0.28f,
    outlineWidth: Float = 0.011f,
    uvScale: Float = 1f,
    materialMode: Float = 0f,
    doubleSided: Boolean = false,
  ) {
    if (isShadowPass) {
      if (shadowDepthProgram == 0) return
      Matrix.multiplyMM(mvpMat, 0, activeLightVp, 0, partMat, 0)
      GLES30.glUseProgram(shadowDepthProgram)
      GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(shadowDepthProgram, "uLightMVP"), 1, false, mvpMat, 0)
      GLES30.glUniform1f(GLES30.glGetUniformLocation(shadowDepthProgram, "uWindSway"), 0f)
      GLES30.glUniform1f(GLES30.glGetUniformLocation(shadowDepthProgram, "uAlphaCutout"), 0f)
      if (doubleSided) GLES30.glDisable(GLES30.GL_CULL_FACE)
      mesh.draw()
      if (doubleSided) GLES30.glEnable(GLES30.GL_CULL_FACE)
      return
    }

    Matrix.multiplyMM(mvpMat, 0, vpMat, 0, partMat, 0)
    Math3D.computeNormalMatrix(partMat, normalMat)

    GLES30.glUseProgram(celProgram)
    GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(celProgram, "uMVP"), 1, false, mvpMat, 0)
    GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(celProgram, "uModel"), 1, false, partMat, 0)
    GLES30.glUniformMatrix3fv(GLES30.glGetUniformLocation(celProgram, "uNormalMat"), 1, false, normalMat, 0)
    GLES30.glUniform4f(GLES30.glGetUniformLocation(celProgram, "uTint"), r, g, b, 1f)
    GLES30.glUniform3fv(GLES30.glGetUniformLocation(celProgram, "uRimColor"), 1, rimColor, 0)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uRimStrength"), rimStrength)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uSpecStrength"), specStrength)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uEmissive"), emissive)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uUvScale"), uvScale)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uWindSway"), 0f)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uMaterialMode"), materialMode)
    GLES30.glUniform1f(GLES30.glGetUniformLocation(celProgram, "uAlphaCutout"), 0f)
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
    GLES30.glUniform1i(GLES30.glGetUniformLocation(celProgram, "uTex"), 0)
    if (doubleSided) GLES30.glDisable(GLES30.GL_CULL_FACE)
    mesh.draw()
    if (doubleSided) GLES30.glEnable(GLES30.GL_CULL_FACE)

    if (outlineWidth > 0.001f) {
      GLES30.glCullFace(GLES30.GL_FRONT)
      GLES30.glUseProgram(outlineProgram)
      GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(outlineProgram, "uMVP"), 1, false, mvpMat, 0)
      GLES30.glUniform1f(GLES30.glGetUniformLocation(outlineProgram, "uOutlineWidth"), outlineWidth)
      GLES30.glUniform4f(GLES30.glGetUniformLocation(outlineProgram, "uOutlineColor"), 0.09f, 0.07f, 0.14f, 1f)
      mesh.draw()
      GLES30.glCullFace(GLES30.GL_BACK)
    }
  }

  private fun setJoint(
    outMat: FloatArray,
    parent: FloatArray,
    tx: Float,
    ty: Float,
    tz: Float,
    rx: Float = 0f,
    ry: Float = 0f,
    rz: Float = 0f,
  ) {
    Matrix.translateM(outMat, 0, parent, 0, tx, ty, tz)
    if (ry != 0f) Matrix.rotateM(outMat, 0, ry, 0f, 1f, 0f)
    if (rx != 0f) Matrix.rotateM(outMat, 0, rx, 1f, 0f, 0f)
    if (rz != 0f) Matrix.rotateM(outMat, 0, rz, 0f, 0f, 1f)
  }

  private fun setPartFromJoint(
    joint: FloatArray,
    tx: Float = 0f,
    ty: Float = 0f,
    tz: Float = 0f,
    rx: Float = 0f,
    ry: Float = 0f,
    rz: Float = 0f,
    sx: Float = 1f,
    sy: Float = 1f,
    sz: Float = 1f,
  ) {
    if (tx != 0f || ty != 0f || tz != 0f) {
      Matrix.translateM(partMat, 0, joint, 0, tx, ty, tz)
    } else {
      System.arraycopy(joint, 0, partMat, 0, 16)
    }
    if (ry != 0f) Matrix.rotateM(partMat, 0, ry, 0f, 1f, 0f)
    if (rx != 0f) Matrix.rotateM(partMat, 0, rx, 1f, 0f, 0f)
    if (rz != 0f) Matrix.rotateM(partMat, 0, rz, 0f, 0f, 1f)
    Matrix.scaleM(partMat, 0, sx, sy, sz)
  }

  fun drawAnimeHero(
    vpMat: FloatArray,
    celProgram: Int,
    outlineProgram: Int,
    textures: ProceduralTextures.TextureSet,
    heroId: String,
    weaponId: String,
    state: HeroAnimState,
  ) {
    val arch = GameCatalog.heroById(heroId)
    val weapon = GameCatalog.weaponById(weaponId)

    val (hairR, hairG, hairB) =
      when (heroId) {
        "lyra" -> Triple(0.95f, 0.25f, 0.24f)
        "seraphina" -> Triple(0.84f, 0.78f, 0.99f)
        else -> Triple(0.84f, 0.91f, 0.99f)
      }
    val (coatR, coatG, coatB) =
      when (heroId) {
        "lyra" -> Triple(0.68f, 0.12f, 0.20f)
        "seraphina" -> Triple(0.24f, 0.18f, 0.50f)
        else -> Triple(0.11f, 0.16f, 0.28f)
      }
    val (shirtR, shirtG, shirtB) =
      when (heroId) {
        "lyra" -> Triple(0.18f, 0.16f, 0.24f)
        "seraphina" -> Triple(0.92f, 0.93f, 0.99f)
        else -> Triple(0.92f, 0.95f, 0.99f)
      }
    val faceTex =
      when (heroId) {
        "lyra" -> textures.animeFacePyro
        "seraphina" -> textures.animeFaceCryo
        else -> textures.animeFaceAero
      }
    val elemRgb = colorHexToRgb(arch.primaryColorHex)
    val bladeRgb = colorHexToRgb(weapon.bladeColorHex)

    val speed = state.horizSpeed
    val animTime = state.animTime
    val phase = state.gaitPhase
    val walkBlend = (speed / 3.8f).coerceIn(0f, 1f)
    val runBlend = ((speed - 2.6f) / 4.8f).coerceIn(0f, 1f)
    val sprintBlend = ((speed - 7.4f) / 3.0f).coerceIn(0f, 1f)

    // Sleek 6.5-head anime NTE proportions (longer slender legs, slimmer waist, refined head)
    val thighLen = 0.43f
    val shinLen = 0.43f
    val squashDip = state.landingSquash * 0.18f
    val strideBob =
      if (state.isGrounded) {
        (abs(cos(phase)) * 0.062f - 0.022f) * walkBlend + sin(animTime * 2.4f) * 0.012f * (1f - walkBlend)
      } else {
        0f
      }
    val pelvisHeight = 0.96f - squashDip + strideBob
    val forwardLean =
      when {
        state.isGliding -> 24f
        !state.isGrounded -> (6f - state.velY * 0.9f).coerceIn(-12f, 20f)
        else -> walkBlend * 5f + runBlend * 11f + sprintBlend * 9f + state.landingSquash * 16f
      }
    val hipYawTwist = if (state.isGrounded) cos(phase) * (5f * walkBlend + 5f * runBlend) else 0f

    Matrix.setIdentityM(rootMat, 0)
    Matrix.translateM(rootMat, 0, state.x, state.y, state.z)
    Matrix.rotateM(rootMat, 0, state.yawDeg, 0f, 1f, 0f)

    // Root Pelvis Joint
    setJoint(
      pelvisMat, rootMat,
      0f, pelvisHeight, 0f,
      rx = forwardLean * 0.25f,
      ry = hipYawTwist,
      rz = state.leanZ * 10f,
    )

    // Pelvis shorts / skirt-base trim
    setPartFromJoint(pelvisMat, ty = 0.01f, sx = 0.24f, sy = 0.14f, sz = 0.18f)
    drawCelPart(torsoMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, 0.10f, 0.11f, 0.18f, rimColor = elemRgb)

    // --- LEGS (Hip -> Thigh -> Knee -> Shin -> Ankle -> High-Top NTE Tech Boot) ---
    for ((idx, hipX) in listOf(-0.105f, 0.105f).withIndex()) {
      val legPhase = if (idx == 0) phase else phase + PI.toFloat()
      val s = sin(legPhase)
      val c = cos(legPhase)

      val hipRx: Float
      val kneeRx: Float
      val ankleRx: Float
      if (!state.isGrounded) {
        if (state.isGliding) {
          hipRx = if (idx == 0) 18f else 26f
          kneeRx = if (idx == 0) 14f else 22f
          ankleRx = 18f
        } else if (state.velY > 0.6f) {
          hipRx = if (idx == 0) -48f else 22f
          kneeRx = if (idx == 0) 68f else 18f
          ankleRx = if (idx == 0) 10f else 22f
        } else {
          hipRx = if (idx == 0) -18f else -8f
          kneeRx = if (idx == 0) 32f else 24f
          ankleRx = -6f
        }
      } else {
        val swingAmp = 24f * walkBlend + 20f * runBlend + 13f * sprintBlend
        val rawHip = -s * swingAmp
        val swingKnee = max(0f, c) * (34f * walkBlend + 28f * runBlend + 18f * sprintBlend)
        val pushKnee = max(0f, -s) * (14f * runBlend)
        val idleKnee = (1f - walkBlend) * 5f + state.landingSquash * 48f
        kneeRx = (swingKnee + pushKnee + idleKnee).coerceIn(0f, 88f)
        hipRx = rawHip - state.landingSquash * 24f
        ankleRx = -(hipRx + kneeRx) * 0.42f
      }

      // Thigh (sleek pants for Kaelen; thigh-high contrast + luminous skin gap for Lyra/Seraphina)
      setJoint(jointAMat, pelvisMat, hipX, -0.02f, 0f, rx = hipRx)
      if (heroId == "kaelen") {
        setPartFromJoint(jointAMat, sx = 0.125f, sy = thighLen, sz = 0.125f)
        drawCelPart(boneLimbMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, 0.11f, 0.13f, 0.21f, rimColor = elemRgb)
      } else {
        // Soft anime thigh skin with glowing NTE tactical garter ring on left leg
        setPartFromJoint(jointAMat, sx = 0.115f, sy = thighLen, sz = 0.115f)
        drawCelPart(boneLimbMesh, vpMat, celProgram, outlineProgram, textures.white, 0.99f, 0.89f, 0.84f, specStrength = 0.02f, materialMode = 2f)
        if (idx == 0) {
          setPartFromJoint(jointAMat, ty = -thighLen * 0.42f, rx = 90f, sx = 0.068f, sy = 0.068f, sz = 0.068f)
          drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, textures.rune, elemRgb[0], elemRgb[1], elemRgb[2], emissive = 0.95f, outlineWidth = 0f)
        }
      }

      // Knee -> Shin (stockings/boots + neon cuff + gold knee trim)
      setJoint(jointBMat, jointAMat, 0f, -thighLen, 0f, rx = kneeRx)
      setPartFromJoint(jointBMat, sx = 0.068f, sy = 0.068f, sz = 0.068f)
      drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, textures.white, elemRgb[0], elemRgb[1], elemRgb[2], emissive = 0.65f, specStrength = 0.8f, outlineWidth = 0f)

      setPartFromJoint(jointBMat, sx = 0.115f, sy = shinLen, sz = 0.12f)
      drawCelPart(boneLimbMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, 0.09f, 0.10f, 0.17f, specStrength = 0.55f, rimColor = elemRgb)

      // Ankle -> Sculpted Urban Combat Boot + Glowing Neon Sole Accent
      setJoint(jointCMat, jointBMat, 0f, -shinLen, 0f, rx = ankleRx)
      setPartFromJoint(jointCMat, sx = 0.92f, sy = 0.96f, sz = 0.96f)
      drawCelPart(footBootMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, 0.10f, 0.11f, 0.18f, specStrength = 0.68f, rimColor = elemRgb)
      setPartFromJoint(jointCMat, ty = -0.095f, tz = 0.06f, sx = 0.09f, sy = 0.022f, sz = 0.24f)
      drawCelPart(boxMesh, vpMat, celProgram, outlineProgram, textures.white, elemRgb[0], elemRgb[1], elemRgb[2], emissive = 0.95f, outlineWidth = 0f)
    }

    // --- SPINE / TAILORED NTE OUTFIT (Inner Vest/Shirt + Open-Front Trenchcoat Tail + Lapels) ---
    val torsoYaw =
      if (state.attackTimer > 0f) {
        val p = 1f - (state.attackTimer / 0.38f).coerceIn(0f, 1f)
        if (state.comboStep % 2 == 0) -25f + p * 55f else 25f - p * 55f
      } else {
        -hipYawTwist * 0.7f
      }
    setJoint(torsoMat, pelvisMat, 0f, 0.03f, 0f, rx = forwardLean * 0.75f, ry = torsoYaw)

    // Open-front fluttering NTE trenchcoat lower tail (double-sided so inner lining renders cleanly)
    val coatWave = walkBlend * 14f + runBlend * 14f + (if (!state.isGrounded) 20f else 0f) + sin(animTime * 6f) * 3f
    setPartFromJoint(torsoMat, ty = 0.04f, rx = coatWave * 0.55f, sx = 0.66f, sy = 0.52f, sz = 0.56f)
    drawCelPart(openCoatMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, coatR, coatG, coatB, rimColor = elemRgb, doubleSided = true)

    // Inner tailored shirt / vest torso
    setPartFromJoint(torsoMat, ty = 0.23f, sx = 0.37f, sy = 0.44f, sz = 0.35f)
    drawCelPart(torsoMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, shirtR, shirtG, shirtB, rimColor = elemRgb)

    // Sleek NTE Tactical Waist Belt + Glowing Vision Buckle
    setPartFromJoint(torsoMat, ty = 0.045f, rx = 90f, sx = 0.145f, sy = 0.120f, sz = 0.18f)
    drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, textures.stone, 0.14f, 0.15f, 0.22f, specStrength = 0.72f, outlineWidth = 0.005f)
    setPartFromJoint(torsoMat, ty = 0.050f, tz = 0.125f, ry = 45f, sx = 0.055f, sy = 0.055f, sz = 0.035f)
    drawCelPart(crystalMesh, vpMat, celProgram, outlineProgram, textures.rune, elemRgb[0], elemRgb[1], elemRgb[2], emissive = 1.10f, outlineWidth = 0f)

    // Pleated NTE Combat Skirt layer for Lyra & Seraphina for rich layered body silhouette
    if (heroId != "kaelen") {
      setPartFromJoint(torsoMat, ty = -0.05f, rx = coatWave * 0.35f, sx = 0.44f, sy = 0.26f, sz = 0.38f)
      drawCelPart(skirtMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, coatR * 0.9f, coatG * 0.9f, coatB * 0.9f, rimColor = elemRgb, outlineWidth = 0.007f, doubleSided = true)
    }

    // Contrasting open-front jacket left & right side lapels + glowing chest necktie/vision crest
    for (dir in listOf(-1f, 1f)) {
      setPartFromJoint(torsoMat, tx = dir * 0.11f, ty = 0.25f, tz = 0.055f, rz = dir * 8f, sx = 0.14f, sy = 0.42f, sz = 0.21f)
      drawCelPart(torsoMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, coatR, coatG, coatB, rimColor = elemRgb, outlineWidth = 0.008f)
      // Neon NTE trim stripe on jacket lapel
      setPartFromJoint(torsoMat, tx = dir * 0.088f, ty = 0.26f, tz = 0.142f, rz = dir * 8f, sx = 0.018f, sy = 0.36f, sz = 0.02f)
      drawCelPart(boxMesh, vpMat, celProgram, outlineProgram, textures.white, elemRgb[0], elemRgb[1], elemRgb[2], emissive = 1.05f, outlineWidth = 0f)
    }

    // High popped NTE jacket collar scarf & glowing Vision Core
    setPartFromJoint(torsoMat, ty = 0.43f, tz = -0.01f, rx = 14f, sx = 0.165f, sy = 0.11f, sz = 0.155f)
    drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, elemRgb[0], elemRgb[1], elemRgb[2], emissive = 0.30f, rimColor = elemRgb)
    setPartFromJoint(torsoMat, ty = 0.06f, tz = 0.12f, ry = 45f, sx = 0.085f, sy = 0.085f, sz = 0.06f)
    drawCelPart(crystalMesh, vpMat, celProgram, outlineProgram, textures.rune, 1f, 0.86f, 0.36f, emissive = 0.90f, outlineWidth = 0f)

    // Flowing asymmetric shoulder scarf / twin cape ribbons
    val fallLift = if (!state.isGrounded) (-state.velY * 3.0f).coerceIn(-10f, 38f) else 0f
    val capeWave = 14f + walkBlend * 18f + runBlend * 20f + fallLift + sin(animTime * 6.5f) * (5f + runBlend * 6f)
    setPartFromJoint(torsoMat, tx = -0.09f, ty = 0.24f, tz = -0.14f, rx = capeWave, rz = -8f, sx = 0.17f, sy = 0.56f, sz = 0.06f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, elemRgb[0], elemRgb[1], elemRgb[2], emissive = 0.22f, rimColor = elemRgb)
    setPartFromJoint(torsoMat, tx = 0.09f, ty = 0.24f, tz = -0.14f, rx = capeWave + 4f, rz = 8f, sx = 0.17f, sy = 0.56f, sz = 0.06f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, coatR * 1.15f, coatG * 1.15f, coatB * 1.15f, rimColor = elemRgb)

    // Floating Vision Sigil on back
    setPartFromJoint(torsoMat, ty = 0.34f, tz = -0.18f, ry = animTime * 75f, sx = 0.11f, sy = 0.11f, sz = 0.11f)
    drawCelPart(crystalMesh, vpMat, celProgram, outlineProgram, textures.rune, elemRgb[0], elemRgb[1], elemRgb[2], emissive = 1.10f, specStrength = 0.85f, outlineWidth = 0.008f)

    // --- NECK, SCULPTED ANIME HEAD (Ultra-Clear Face Shader!) & MULTI-LAYER ANGEL-RING HAIR ---
    setJoint(headMat, torsoMat, 0f, 0.46f, 0f, rx = -forwardLean * 0.45f)
    setPartFromJoint(headMat, ty = 0.04f, sx = 0.084f, sy = 0.12f, sz = 0.084f)
    drawCelPart(limbMesh, vpMat, celProgram, outlineProgram, textures.white, 0.96f, 0.84f, 0.80f, specStrength = 0f, outlineWidth = 0.0055f, materialMode = 2f)

    // Refined crystal-clear anime head (0.32m) with luminous shadow-safe skin shader (materialMode = 2f)
    setPartFromJoint(headMat, ty = 0.215f, tz = 0.01f, sx = 0.312f, sy = 0.328f, sz = 0.298f)
    drawCelPart(animeHeadMesh, vpMat, celProgram, outlineProgram, faceTex, 1f, 1f, 1f, specStrength = 0f, rimColor = elemRgb, rimStrength = 0.18f, outlineWidth = 0.0062f, materialMode = 2f)

    // Stylized anime ears tucked cleanly behind side-locks
    for (dir in listOf(-1f, 1f)) {
      setPartFromJoint(headMat, tx = dir * 0.145f, ty = 0.20f, tz = -0.012f, rz = -dir * 12f, sx = 0.030f, sy = 0.064f, sz = 0.045f)
      drawCelPart(sphereMesh, vpMat, celProgram, outlineProgram, textures.white, 0.99f, 0.88f, 0.83f, specStrength = 0f, outlineWidth = 0.0045f, materialMode = 2f)
    }

    // Glowing NTE Cyber-Magic Headset / Earring accents
    setPartFromJoint(headMat, tx = -0.160f, ty = 0.21f, tz = -0.005f, ry = 90f, sx = 0.050f, sy = 0.050f, sz = 0.050f)
    drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, textures.white, elemRgb[0], elemRgb[1], elemRgb[2], emissive = 0.95f, outlineWidth = 0f)
    setPartFromJoint(headMat, tx = 0.160f, ty = 0.21f, tz = -0.005f, ry = 90f, sx = 0.050f, sy = 0.050f, sz = 0.050f)
    drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, textures.white, elemRgb[0], elemRgb[1], elemRgb[2], emissive = 0.95f, outlineWidth = 0f)

    // Sculpted Anime Hair Crown (with Angel-Ring Hair Shader materialMode = 3f!)
    setPartFromJoint(headMat, ty = 0.228f, tz = -0.008f, sx = 0.320f, sy = 0.334f, sz = 0.320f)
    drawCelPart(hairCrownMesh, vpMat, celProgram, outlineProgram, textures.white, hairR, hairG, hairB, specStrength = 0.78f, rimColor = elemRgb, outlineWidth = 0.0088f, materialMode = 3f)

    // Elevated Parted Forehead Fringe (frames forehead high so both eyes & brows remain 100% unobstructed)
    setPartFromJoint(headMat, tx = -0.112f, ty = 0.372f, tz = 0.116f, rx = -28f, rz = 32f, sx = 0.095f, sy = 0.125f, sz = 0.072f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairR, hairG, hairB, specStrength = 0.74f, rimColor = elemRgb, outlineWidth = 0.006f, materialMode = 3f)
    setPartFromJoint(headMat, tx = -0.048f, ty = 0.386f, tz = 0.140f, rx = -34f, rz = 22f, sx = 0.084f, sy = 0.095f, sz = 0.068f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairR, hairG, hairB, specStrength = 0.74f, rimColor = elemRgb, outlineWidth = 0.006f, materialMode = 3f)
    setPartFromJoint(headMat, tx = 0.006f, ty = 0.388f, tz = 0.145f, rx = -36f, rz = -12f, sx = 0.062f, sy = 0.088f, sz = 0.062f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairR, hairG, hairB, specStrength = 0.74f, rimColor = elemRgb, outlineWidth = 0.006f, materialMode = 3f)
    setPartFromJoint(headMat, tx = 0.060f, ty = 0.384f, tz = 0.138f, rx = -32f, rz = -22f, sx = 0.088f, sy = 0.102f, sz = 0.068f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairR, hairG, hairB, specStrength = 0.74f, rimColor = elemRgb, outlineWidth = 0.006f, materialMode = 3f)
    setPartFromJoint(headMat, tx = 0.116f, ty = 0.368f, tz = 0.114f, rx = -28f, rz = -32f, sx = 0.095f, sy = 0.125f, sz = 0.072f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairR, hairG, hairB, specStrength = 0.74f, rimColor = elemRgb, outlineWidth = 0.006f, materialMode = 3f)

    // Cute Anime Ahoge / Top Hair Strand
    val ahogeSway = sin(animTime * 4.5f) * 8f
    setPartFromJoint(headMat, tx = 0.0f, ty = 0.395f, tz = 0.02f, rx = -140f + ahogeSway, rz = 16f, sx = 0.055f, sy = 0.13f, sz = 0.055f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairR, hairG, hairB, specStrength = 0.65f, outlineWidth = 0.0055f, materialMode = 3f)

    // Cheek-framing Tapered Side Locks (tucked neatly outward so face cheeks & eyes stay vivid)
    setPartFromJoint(headMat, tx = -0.154f, ty = 0.285f, tz = 0.066f, rx = 4f, rz = 16f, sx = 0.088f, sy = 0.27f, sz = 0.086f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairR, hairG, hairB, specStrength = 0.72f, rimColor = elemRgb, outlineWidth = 0.0065f, materialMode = 3f)
    setPartFromJoint(headMat, tx = 0.154f, ty = 0.285f, tz = 0.066f, rx = 4f, rz = -16f, sx = 0.088f, sy = 0.27f, sz = 0.086f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairR, hairG, hairB, specStrength = 0.72f, rimColor = elemRgb, outlineWidth = 0.0065f, materialMode = 3f)

    // Hero-Specific Flowing Back Hair / Twin-Tails
    val hairFlow = 16f + walkBlend * 16f + runBlend * 18f + fallLift * 0.8f + sin(animTime * 5.2f) * 6f
    if (heroId == "lyra") {
      // Dynamic Flame Twin-Tails
      for (dir in listOf(-1f, 1f)) {
        setPartFromJoint(headMat, tx = dir * 0.14f, ty = 0.29f, tz = -0.11f, rx = hairFlow + 8f, rz = dir * 24f, sx = 0.14f, sy = 0.44f, sz = 0.13f)
        drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairR, hairG, hairB, specStrength = 0.72f, rimColor = elemRgb, outlineWidth = 0.009f, materialMode = 3f)
      }
    } else {
      val backLen = if (heroId == "seraphina") 0.54f else 0.40f
      val backW = if (heroId == "seraphina") 0.24f else 0.19f
      setPartFromJoint(headMat, ty = 0.26f, tz = -0.14f, rx = hairFlow, sx = backW, sy = backLen, sz = 0.16f)
      drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairR, hairG, hairB, specStrength = 0.72f, rimColor = elemRgb, outlineWidth = 0.009f, materialMode = 3f)
    }

    // --- ARMS (Shoulder -> Upper Arm -> Elbow -> Forearm -> Hand + Weapon) ---
    val upperArmLen = 0.26f
    val forearmLen = 0.24f

    // Sleek Shoulder Epaulettes
    setPartFromJoint(torsoMat, tx = -0.21f, ty = 0.39f, rz = 16f, sx = 0.11f, sy = 0.08f, sz = 0.11f)
    drawCelPart(sphereMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, 0.96f, 0.82f, 0.42f, specStrength = 0.85f)
    setPartFromJoint(torsoMat, tx = 0.21f, ty = 0.39f, rz = -16f, sx = 0.11f, sy = 0.08f, sz = 0.11f)
    drawCelPart(sphereMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, 0.96f, 0.82f, 0.42f, specStrength = 0.85f)

    val leftShoulderRx: Float
    val leftElbowRx: Float
    val leftShoulderRz: Float
    if (state.isGliding) {
      leftShoulderRx = -135f
      leftElbowRx = -25f
      leftShoulderRz = 26f
    } else if (!state.isGrounded) {
      leftShoulderRx = -35f
      leftElbowRx = -40f
      leftShoulderRz = 32f
    } else {
      leftShoulderRx = sin(phase) * (22f * walkBlend + 18f * runBlend + 10f * sprintBlend)
      leftElbowRx = -(16f + walkBlend * 18f + runBlend * 32f + max(0f, -sin(phase)) * 18f)
      leftShoulderRz = 10f
    }
    setJoint(jointAMat, torsoMat, -0.215f, 0.37f, 0f, rx = leftShoulderRx, rz = leftShoulderRz)
    setPartFromJoint(jointAMat, sx = 0.105f, sy = upperArmLen, sz = 0.105f)
    drawCelPart(boneLimbMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, coatR, coatG, coatB, rimColor = elemRgb)
    setJoint(jointBMat, jointAMat, 0f, -upperArmLen, 0f, rx = leftElbowRx)
    setPartFromJoint(jointBMat, sx = 0.090f, sy = forearmLen, sz = 0.090f)
    drawCelPart(boneLimbMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, 0.12f, 0.13f, 0.20f, specStrength = 0.45f, rimColor = elemRgb)
    // Glowing wrist bracer cuff + sculpted glove hand
    setPartFromJoint(jointBMat, ty = -forearmLen + 0.02f, sx = 0.052f, sy = 0.03f, sz = 0.052f)
    drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, textures.white, elemRgb[0], elemRgb[1], elemRgb[2], emissive = 0.72f, outlineWidth = 0f)
    setPartFromJoint(jointBMat, ty = -forearmLen, sx = 0.088f, sy = 0.105f, sz = 0.088f)
    drawCelPart(handGloveMesh, vpMat, celProgram, outlineProgram, textures.white, 0.98f, 0.88f, 0.82f, materialMode = 2f, outlineWidth = 0.006f)

    val rightShoulderRx: Float
    val rightShoulderRy: Float
    val rightShoulderRz: Float
    val rightElbowRx: Float
    if (state.attackTimer > 0f) {
      val p = 1f - (state.attackTimer / 0.38f).coerceIn(0f, 1f)
      rightShoulderRx =
        when (state.comboStep % 3) {
          0 -> -115f + p * 145f
          1 -> 35f - p * 155f
          else -> -135f + p * 190f
        }
      rightShoulderRy = if (state.comboStep % 2 == 0) -34f else 34f
      rightShoulderRz = -22f
      rightElbowRx = -24f
    } else if (state.isGliding) {
      rightShoulderRx = -135f
      rightShoulderRy = 0f
      rightShoulderRz = -26f
      rightElbowRx = -25f
    } else if (!state.isGrounded) {
      rightShoulderRx = -35f
      rightShoulderRy = 0f
      rightShoulderRz = -32f
      rightElbowRx = -40f
    } else {
      rightShoulderRx = -sin(phase) * (22f * walkBlend + 18f * runBlend + 10f * sprintBlend)
      rightShoulderRy = 0f
      rightShoulderRz = -10f
      rightElbowRx = -(16f + walkBlend * 18f + runBlend * 32f + max(0f, sin(phase)) * 18f)
    }

    setJoint(jointAMat, torsoMat, 0.215f, 0.37f, 0f, rx = rightShoulderRx, ry = rightShoulderRy, rz = rightShoulderRz)
    setPartFromJoint(jointAMat, sx = 0.105f, sy = upperArmLen, sz = 0.105f)
    drawCelPart(boneLimbMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, coatR, coatG, coatB, rimColor = elemRgb)
    setJoint(jointBMat, jointAMat, 0f, -upperArmLen, 0f, rx = rightElbowRx)
    setPartFromJoint(jointBMat, sx = 0.090f, sy = forearmLen, sz = 0.090f)
    drawCelPart(boneLimbMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, 0.12f, 0.13f, 0.20f, specStrength = 0.45f, rimColor = elemRgb)
    setPartFromJoint(jointBMat, ty = -forearmLen + 0.02f, sx = 0.052f, sy = 0.03f, sz = 0.052f)
    drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, textures.white, elemRgb[0], elemRgb[1], elemRgb[2], emissive = 0.72f, outlineWidth = 0f)
    setPartFromJoint(jointBMat, ty = -forearmLen, sx = 0.088f, sy = 0.105f, sz = 0.088f)
    drawCelPart(handGloveMesh, vpMat, celProgram, outlineProgram, textures.white, 0.98f, 0.88f, 0.82f, materialMode = 2f, outlineWidth = 0.006f)

    // Hand Attachment -> Sleek Anime Katana/Blade or Floating Astral Catalyst
    if (heroId == "seraphina" || weaponId == "astral_codex") {
      val cy = 0.34f + sin(animTime * 4f) * 0.08f
      setPartFromJoint(torsoMat, tx = 0.44f, ty = cy, tz = 0.32f, ry = animTime * 115f, sx = 0.17f, sy = 0.25f, sz = 0.17f)
      drawCelPart(crystalMesh, vpMat, celProgram, outlineProgram, textures.rune, bladeRgb[0], bladeRgb[1], bladeRgb[2], emissive = 1.05f, specStrength = 0.9f)
      setPartFromJoint(torsoMat, tx = 0.44f, ty = cy, tz = 0.32f, rx = 68f, ry = -animTime * 90f, sx = 0.28f, sy = 0.28f, sz = 0.28f)
      drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, textures.rune, 1f, 0.86f, 0.42f, emissive = 0.85f, outlineWidth = 0f)
    } else {
      setJoint(jointCMat, jointBMat, 0f, -forearmLen, 0.02f, rx = 86f)
      setPartFromJoint(jointCMat, sx = 0.14f, sy = 1.42f, sz = 0.05f)
      drawCelPart(swordBladeMesh, vpMat, celProgram, outlineProgram, textures.rune, bladeRgb[0], bladeRgb[1], bladeRgb[2], emissive = 0.85f, specStrength = 0.95f, rimColor = bladeRgb)
      setPartFromJoint(jointCMat, sx = 0.30f, sy = 0.06f, sz = 0.08f)
      drawCelPart(limbMesh, vpMat, celProgram, outlineProgram, textures.white, 0.98f, 0.82f, 0.34f, specStrength = 0.9f)
    }

    // 3D Glowing Combat Slash Crescent Ribbon (skipped during shadow pass)
    if (!isShadowPass && state.attackTimer > 0f) {
      val slashRoll =
        when (state.comboStep % 3) {
          0 -> 14f
          1 -> -18f
          else -> 4f
        }
      setPartFromJoint(torsoMat, ty = 0.18f, tz = 0.24f, rx = slashRoll, ry = rightShoulderRy * 0.5f, sx = 1.05f, sy = 1f, sz = 1.05f)
      GLES30.glEnable(GLES30.GL_BLEND)
      GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
      GLES30.glDisable(GLES30.GL_CULL_FACE)
      drawCelPart(
        slashArcMesh, vpMat, celProgram, outlineProgram,
        textures.white, elemRgb[0], elemRgb[1], elemRgb[2],
        emissive = 1.45f, rimStrength = 0.2f, specStrength = 0f, outlineWidth = 0f,
      )
      GLES30.glEnable(GLES30.GL_CULL_FACE)
      GLES30.glDisable(GLES30.GL_BLEND)
    }

    // Multi-Wing Astral Glider when airborne gliding
    if (state.isGliding) {
      val flap = sin(animTime * 5.2f) * 7f
      for (dir in listOf(-1f, 1f)) {
        setPartFromJoint(torsoMat, tx = dir * 0.70f, ty = 0.46f, tz = -0.20f, rx = 16f, rz = -dir * (16f + flap), sx = 1.18f, sy = 0.05f, sz = 0.40f)
        drawCelPart(swordBladeMesh, vpMat, celProgram, outlineProgram, textures.rune, elemRgb[0], elemRgb[1], elemRgb[2], emissive = 0.85f)
        setPartFromJoint(torsoMat, tx = dir * 0.56f, ty = 0.32f, tz = -0.24f, rx = 24f, rz = -dir * (28f + flap), sx = 0.90f, sy = 0.04f, sz = 0.32f)
        drawCelPart(swordBladeMesh, vpMat, celProgram, outlineProgram, textures.rune, 1f, 0.88f, 0.45f, emissive = 0.75f)
      }
    }
  }

  /**
   * Renders a full articulated NTE/Genshin-style Anime NPC (pedestrian stroller, cafe barista,
   * metro guide, technician, or vanguard sentinel) with crystal-clear face & expressive gestures.
   */
  fun drawAnimeNpc(
    vpMat: FloatArray,
    celProgram: Int,
    outlineProgram: Int,
    textures: ProceduralTextures.TextureSet,
    npc: WorldNpc,
    animTime: Float,
    playerNearby: Boolean,
  ) {
    val hairRgb = colorHexToRgb(npc.hairColorHex)
    val coatRgb = colorHexToRgb(npc.coatColorHex)
    val accRgb = colorHexToRgb(npc.accentColorHex)
    val faceTex =
      when (npc.faceVariant) {
        1 -> textures.animeFaceNpcAmber
        2 -> textures.animeFaceNpcViolet
        else -> textures.animeFaceNpcCyan
      }

    val isWalking = npc.moveSpeed > 0.05f && !playerNearby
    val walkBlend = if (isWalking) 1f else 0f
    val phase = npc.gaitPhase
    val bob = if (isWalking) abs(cos(phase)) * 0.045f else sin(animTime * 2.2f + npc.homeX) * 0.014f

    Matrix.setIdentityM(rootMat, 0)
    Matrix.translateM(rootMat, 0, npc.x, npc.y, npc.z)
    Matrix.rotateM(rootMat, 0, npc.yaw, 0f, 1f, 0f)

    setJoint(pelvisMat, rootMat, 0f, 0.93f + bob, 0f, rx = walkBlend * 4f)

    // Legs & boots
    val thighLen = 0.41f
    val shinLen = 0.41f
    for ((idx, hipX) in listOf(-0.10f, 0.10f).withIndex()) {
      val legPhase = if (idx == 0) phase else phase + PI.toFloat()
      val s = sin(legPhase)
      val c = cos(legPhase)
      val hipRx = if (isWalking) -s * 22f else 0f
      val kneeRx = if (isWalking) max(0f, c) * 32f else 4f

      setJoint(jointAMat, pelvisMat, hipX, -0.02f, 0f, rx = hipRx)
      setPartFromJoint(jointAMat, sx = 0.115f, sy = thighLen, sz = 0.115f)
      drawCelPart(boneLimbMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, 0.14f, 0.16f, 0.24f, rimColor = accRgb, outlineWidth = 0.007f)

      setJoint(jointBMat, jointAMat, 0f, -thighLen, 0f, rx = kneeRx)
      setPartFromJoint(jointBMat, sx = 0.105f, sy = shinLen, sz = 0.110f)
      drawCelPart(boneLimbMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, 0.10f, 0.12f, 0.19f, rimColor = accRgb, outlineWidth = 0.007f)

      setJoint(jointCMat, jointBMat, 0f, -shinLen, 0f)
      setPartFromJoint(jointCMat, sx = 0.90f, sy = 0.92f, sz = 0.94f)
      drawCelPart(footBootMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, 0.12f, 0.14f, 0.22f, rimColor = accRgb, outlineWidth = 0.007f)
    }

    // Tailored NPC Torso, Open Coat & Accent Scarf/Collar
    setJoint(torsoMat, pelvisMat, 0f, 0.03f, 0f, rx = walkBlend * 6f)
    setPartFromJoint(torsoMat, ty = 0.22f, sx = 0.36f, sy = 0.43f, sz = 0.34f)
    drawCelPart(torsoMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, 0.92f, 0.94f, 0.98f, rimColor = accRgb, outlineWidth = 0.007f)

    for (dir in listOf(-1f, 1f)) {
      setPartFromJoint(torsoMat, tx = dir * 0.105f, ty = 0.23f, tz = 0.05f, rz = dir * 8f, sx = 0.135f, sy = 0.41f, sz = 0.20f)
      drawCelPart(torsoMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, coatRgb[0], coatRgb[1], coatRgb[2], rimColor = accRgb, outlineWidth = 0.007f)
    }
    setPartFromJoint(torsoMat, ty = 0.04f, rx = walkBlend * 10f, sx = 0.56f, sy = 0.40f, sz = 0.50f)
    drawCelPart(openCoatMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, coatRgb[0], coatRgb[1], coatRgb[2], rimColor = accRgb, outlineWidth = 0.007f, doubleSided = true)

    // Glowing NPC Role Badge / Collar Pin
    setPartFromJoint(torsoMat, ty = 0.36f, tz = 0.135f, ry = 45f, sx = 0.06f, sy = 0.06f, sz = 0.035f)
    drawCelPart(crystalMesh, vpMat, celProgram, outlineProgram, textures.white, accRgb[0], accRgb[1], accRgb[2], emissive = 1.0f, outlineWidth = 0f)

    // Neck & Clear Anime Head
    setJoint(headMat, torsoMat, 0f, 0.45f, 0f, ry = if (playerNearby) sin(animTime * 2.5f) * 8f else 0f)
    setPartFromJoint(headMat, ty = 0.04f, sx = 0.080f, sy = 0.11f, sz = 0.080f)
    drawCelPart(limbMesh, vpMat, celProgram, outlineProgram, textures.white, 0.96f, 0.84f, 0.80f, specStrength = 0f, outlineWidth = 0.005f, materialMode = 2f)
    setPartFromJoint(headMat, ty = 0.21f, tz = 0.01f, sx = 0.302f, sy = 0.318f, sz = 0.290f)
    drawCelPart(animeHeadMesh, vpMat, celProgram, outlineProgram, faceTex, 1f, 1f, 1f, specStrength = 0f, rimColor = accRgb, rimStrength = 0.18f, outlineWidth = 0.006f, materialMode = 2f)

    // Hair Crown + High Unobstructed Bangs + Style Silhouette
    setPartFromJoint(headMat, ty = 0.222f, tz = -0.008f, sx = 0.312f, sy = 0.326f, sz = 0.312f)
    drawCelPart(hairCrownMesh, vpMat, celProgram, outlineProgram, textures.white, hairRgb[0], hairRgb[1], hairRgb[2], specStrength = 0.70f, rimColor = accRgb, outlineWidth = 0.008f, materialMode = 3f)
    setPartFromJoint(headMat, tx = -0.095f, ty = 0.372f, tz = 0.118f, rx = -28f, rz = 28f, sx = 0.09f, sy = 0.11f, sz = 0.07f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairRgb[0], hairRgb[1], hairRgb[2], specStrength = 0.70f, outlineWidth = 0.0055f, materialMode = 3f)
    setPartFromJoint(headMat, tx = 0.095f, ty = 0.372f, tz = 0.118f, rx = -28f, rz = -28f, sx = 0.09f, sy = 0.11f, sz = 0.07f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairRgb[0], hairRgb[1], hairRgb[2], specStrength = 0.70f, outlineWidth = 0.0055f, materialMode = 3f)

    when (npc.hairStyle) {
      1 -> {
        for (dir in listOf(-1f, 1f)) {
          setPartFromJoint(headMat, tx = dir * 0.135f, ty = 0.28f, tz = -0.10f, rx = 18f, rz = dir * 22f, sx = 0.12f, sy = 0.38f, sz = 0.11f)
          drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairRgb[0], hairRgb[1], hairRgb[2], specStrength = 0.70f, outlineWidth = 0.007f, materialMode = 3f)
        }
      }
      2 -> {
        setPartFromJoint(headMat, ty = 0.25f, tz = -0.13f, rx = 14f, sx = 0.21f, sy = 0.44f, sz = 0.15f)
        drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairRgb[0], hairRgb[1], hairRgb[2], specStrength = 0.70f, outlineWidth = 0.007f, materialMode = 3f)
      }
      else -> {
        setPartFromJoint(headMat, ty = 0.23f, tz = -0.13f, rx = 18f, sx = 0.16f, sy = 0.22f, sz = 0.13f)
        drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairRgb[0], hairRgb[1], hairRgb[2], specStrength = 0.70f, outlineWidth = 0.007f, materialMode = 3f)
      }
    }

    // Arms (waves warmly if player is nearby!)
    val upperArmLen = 0.25f
    val forearmLen = 0.23f
    val leftShoulderRx = if (isWalking) sin(phase) * 20f else 0f
    val rightShoulderRx =
      if (playerNearby) -125f + sin(animTime * 6f) * 14f
      else if (isWalking) -sin(phase) * 20f
      else 0f
    val rightShoulderRz = if (playerNearby) -22f else -10f

    setJoint(jointAMat, torsoMat, -0.205f, 0.36f, 0f, rx = leftShoulderRx, rz = 10f)
    setPartFromJoint(jointAMat, sx = 0.098f, sy = upperArmLen, sz = 0.098f)
    drawCelPart(boneLimbMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, coatRgb[0], coatRgb[1], coatRgb[2], rimColor = accRgb, outlineWidth = 0.007f)
    setJoint(jointBMat, jointAMat, 0f, -upperArmLen, 0f, rx = -18f)
    setPartFromJoint(jointBMat, sx = 0.084f, sy = forearmLen, sz = 0.084f)
    drawCelPart(boneLimbMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, 0.14f, 0.16f, 0.24f, outlineWidth = 0.007f)
    setPartFromJoint(jointBMat, ty = -forearmLen, sx = 0.080f, sy = 0.095f, sz = 0.080f)
    drawCelPart(handGloveMesh, vpMat, celProgram, outlineProgram, textures.white, 0.98f, 0.88f, 0.82f, materialMode = 2f, outlineWidth = 0.005f)

    setJoint(jointAMat, torsoMat, 0.205f, 0.36f, 0f, rx = rightShoulderRx, rz = rightShoulderRz)
    setPartFromJoint(jointAMat, sx = 0.098f, sy = upperArmLen, sz = 0.098f)
    drawCelPart(boneLimbMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, coatRgb[0], coatRgb[1], coatRgb[2], rimColor = accRgb, outlineWidth = 0.007f)
    setJoint(jointBMat, jointAMat, 0f, -upperArmLen, 0f, rx = if (playerNearby) -24f else -18f)
    setPartFromJoint(jointBMat, sx = 0.084f, sy = forearmLen, sz = 0.084f)
    drawCelPart(boneLimbMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, 0.14f, 0.16f, 0.24f, outlineWidth = 0.007f)
    setPartFromJoint(jointBMat, ty = -forearmLen, sx = 0.080f, sy = 0.095f, sz = 0.080f)
    drawCelPart(handGloveMesh, vpMat, celProgram, outlineProgram, textures.white, 0.98f, 0.88f, 0.82f, materialMode = 2f, outlineWidth = 0.005f)

    // Floating cyan interaction diamond marker above interactive NPC when nearby
    if (!isShadowPass && playerNearby) {
      setPartFromJoint(headMat, ty = 0.56f + sin(animTime * 4f) * 0.04f, ry = animTime * 90f, sx = 0.10f, sy = 0.15f, sz = 0.10f)
      drawCelPart(crystalMesh, vpMat, celProgram, outlineProgram, textures.white, accRgb[0], accRgb[1], accRgb[2], emissive = 1.35f, outlineWidth = 0f)
    }
  }

  fun drawMonster(
    vpMat: FloatArray,
    celProgram: Int,
    outlineProgram: Int,
    stoneTex: Int,
    runeTex: Int,
    enemy: WorldEnemy,
    animTime: Float,
  ) {
    val rgb = colorHexToRgb(enemy.element.colorHex)
    val flash = if (enemy.hitFlash > 0f) 0.9f else 0.2f
    val bob = sin(animTime * 3.5f + enemy.homeX) * 0.12f

    Matrix.setIdentityM(rootMat, 0)
    Matrix.translateM(rootMat, 0, enemy.x, enemy.y + bob, enemy.z)
    Matrix.rotateM(rootMat, 0, enemy.yaw, 0f, 1f, 0f)

    if (enemy.isGolem) {
      setPartFromJoint(rootMat, tx = -0.35f, ty = 0.52f, sx = 0.36f, sy = 0.95f, sz = 0.36f)
      drawCelPart(limbMesh, vpMat, celProgram, outlineProgram, stoneTex, 0.36f, 0.40f, 0.52f, emissive = flash, rimColor = rgb)
      setPartFromJoint(rootMat, tx = 0.35f, ty = 0.52f, sx = 0.36f, sy = 0.95f, sz = 0.36f)
      drawCelPart(limbMesh, vpMat, celProgram, outlineProgram, stoneTex, 0.36f, 0.40f, 0.52f, emissive = flash, rimColor = rgb)
      setPartFromJoint(rootMat, ty = 1.52f, sx = 1.45f, sy = 1.25f, sz = 1.15f)
      drawCelPart(torsoMesh, vpMat, celProgram, outlineProgram, stoneTex, 0.42f, 0.46f, 0.58f, emissive = flash, specStrength = 0.5f, rimColor = rgb)
      setPartFromJoint(rootMat, ty = 1.58f, tz = 0.46f, ry = animTime * 80f, sx = 0.42f, sy = 0.42f, sz = 0.42f)
      drawCelPart(crystalMesh, vpMat, celProgram, outlineProgram, runeTex, rgb[0], rgb[1], rgb[2], emissive = 1.1f)
      setPartFromJoint(rootMat, ty = 2.35f, rx = 75f, ry = animTime * 45f, sx = 0.72f, sy = 0.72f, sz = 0.72f)
      drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, runeTex, rgb[0], rgb[1], rgb[2], emissive = 0.9f, outlineWidth = 0f)
      setPartFromJoint(rootMat, tx = -0.92f, ty = 1.72f, rz = 16f, sx = 0.48f, sy = 0.85f, sz = 0.48f)
      drawCelPart(limbMesh, vpMat, celProgram, outlineProgram, stoneTex, 0.34f, 0.38f, 0.48f, emissive = flash, rimColor = rgb)
      setPartFromJoint(rootMat, tx = 0.92f, ty = 1.72f, rz = -16f, sx = 0.48f, sy = 0.85f, sz = 0.48f)
      drawCelPart(limbMesh, vpMat, celProgram, outlineProgram, stoneTex, 0.34f, 0.38f, 0.48f, emissive = flash, rimColor = rgb)
    } else {
      setPartFromJoint(rootMat, ty = 0.55f, sx = 1.12f, sy = 0.86f, sz = 1.12f)
      drawCelPart(sphereMesh, vpMat, celProgram, outlineProgram, runeTex, rgb[0], rgb[1], rgb[2], emissive = 0.45f + flash, specStrength = 0.9f, rimColor = rgb)
      setPartFromJoint(rootMat, ty = 0.98f, sx = 0.24f, sy = 0.36f, sz = 0.24f)
      drawCelPart(crystalMesh, vpMat, celProgram, outlineProgram, runeTex, 1f, 0.95f, 0.65f, emissive = 0.85f)
    }
  }

  fun drawRaidBoss(
    vpMat: FloatArray,
    celProgram: Int,
    outlineProgram: Int,
    stoneTex: Int,
    runeTex: Int,
    element: ElementType,
    phase: Int,
    animTime: Float,
    hitFlash: Float,
    telegraphPct: Float,
  ) {
    val rgb = colorHexToRgb(element.colorHex)
    val hover = 1.65f + sin(animTime * 2.2f) * 0.35f
    val em = 0.38f + hitFlash * 0.7f + (phase - 1) * 0.2f

    Matrix.setIdentityM(rootMat, 0)
    Matrix.translateM(rootMat, 0, 0f, hover, 0f)
    Matrix.rotateM(rootMat, 0, sin(animTime * 0.8f) * 16f, 0f, 1f, 0f)

    setPartFromJoint(rootMat, ty = 1.75f, sx = 2.6f, sy = 2.6f, sz = 2.2f)
    drawCelPart(torsoMesh, vpMat, celProgram, outlineProgram, stoneTex, 0.20f, 0.22f, 0.36f, emissive = em * 0.4f, specStrength = 0.75f, rimColor = rgb, outlineWidth = 0.024f)
    setPartFromJoint(rootMat, ty = 0.25f, sx = 2.4f, sy = 1.8f, sz = 2.2f)
    drawCelPart(skirtMesh, vpMat, celProgram, outlineProgram, runeTex, 0.16f, 0.18f, 0.30f, emissive = em * 0.35f, rimColor = rgb)

    setPartFromJoint(rootMat, ty = 1.85f, tz = 0.82f, ry = animTime * 95f, sx = 0.72f, sy = 1.0f, sz = 0.72f)
    drawCelPart(crystalMesh, vpMat, celProgram, outlineProgram, runeTex, rgb[0], rgb[1], rgb[2], emissive = 1.25f, specStrength = 0.95f, outlineWidth = 0.015f)

    setPartFromJoint(rootMat, ty = 3.45f, sx = 1.10f, sy = 1.20f, sz = 1.10f)
    drawCelPart(sphereMesh, vpMat, celProgram, outlineProgram, stoneTex, 0.15f, 0.18f, 0.30f, specStrength = 0.8f, rimColor = rgb, outlineWidth = 0.022f)
    setPartFromJoint(rootMat, tx = -0.72f, ty = 4.15f, rz = 26f, sx = 0.30f, sy = 1.45f, sz = 0.30f)
    drawCelPart(coneMesh, vpMat, celProgram, outlineProgram, runeTex, rgb[0], rgb[1], rgb[2], emissive = 0.95f)
    setPartFromJoint(rootMat, tx = 0.72f, ty = 4.15f, rz = -26f, sx = 0.30f, sy = 1.45f, sz = 0.30f)
    drawCelPart(coneMesh, vpMat, celProgram, outlineProgram, runeTex, rgb[0], rgb[1], rgb[2], emissive = 0.95f)

    for (dir in listOf(-1f, 1f)) {
      val wingSway = sin(animTime * 2.6f) * 8f
      setPartFromJoint(rootMat, tx = dir * 2.2f, ty = 2.3f, tz = -0.45f, rx = 14f, rz = -dir * (38f + wingSway), sx = 0.52f, sy = 3.4f, sz = 0.20f)
      drawCelPart(swordBladeMesh, vpMat, celProgram, outlineProgram, runeTex, rgb[0], rgb[1], rgb[2], emissive = 0.95f)
    }

    setPartFromJoint(rootMat, ty = 1.9f, rx = 68f + sin(animTime) * 12f, ry = animTime * 55f, sx = 3.3f, sy = 3.3f, sz = 3.3f)
    drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, runeTex, rgb[0], rgb[1], rgb[2], emissive = 1.05f, outlineWidth = 0f)
    setPartFromJoint(rootMat, ty = 1.9f, rx = -62f, ry = -animTime * 42f, sx = 4.2f, sy = 4.2f, sz = 4.2f)
    drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, runeTex, 1f, 0.86f, 0.42f, emissive = 0.90f, outlineWidth = 0f)

    if (!isShadowPass && telegraphPct > 0.02f) {
      Matrix.setIdentityM(partMat, 0)
      Matrix.translateM(partMat, 0, 0f, 0.08f, 0f)
      val ringScale = 3.5f + telegraphPct * 6.5f
      Matrix.scaleM(partMat, 0, ringScale, 0.25f, ringScale)
      drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, runeTex, 1.0f, 0.22f, 0.18f, emissive = 1.4f, outlineWidth = 0f)
    }
  }

  companion object {
    fun colorHexToRgb(hex: Long): FloatArray {
      val r = ((hex shr 16) and 0xFF) / 255f
      val g = ((hex shr 8) and 0xFF) / 255f
      val b = (hex and 0xFF) / 255f
      return floatArrayOf(r, g, b)
    }
  }
}
