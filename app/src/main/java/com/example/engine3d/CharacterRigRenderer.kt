package com.example.engine3d

import android.opengl.GLES30
import android.opengl.Matrix
import com.example.data.ElementType
import com.example.data.GameCatalog
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class CharacterRigRenderer(
  private val boxMesh: GpuMesh,
  private val sphereMesh: GpuMesh,
  private val animeHeadMesh: GpuMesh,
  private val limbMesh: GpuMesh,
  private val torsoMesh: GpuMesh,
  private val skirtMesh: GpuMesh,
  private val hairLockMesh: GpuMesh,
  private val swordBladeMesh: GpuMesh,
  private val slashArcMesh: GpuMesh,
  private val coneMesh: GpuMesh,
  private val cylinderMesh: GpuMesh,
  private val crystalMesh: GpuMesh,
  private val torusMesh: GpuMesh,
) {
  private val modelMat = FloatArray(16)
  private val partMat = FloatArray(16)
  private val mvpMat = FloatArray(16)
  private val normalMat = FloatArray(9)

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
    rimStrength: Float = 0.58f,
    specStrength: Float = 0.25f,
    outlineWidth: Float = 0.014f,
    uvScale: Float = 1f,
  ) {
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
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
    GLES30.glUniform1i(GLES30.glGetUniformLocation(celProgram, "uTex"), 0)
    mesh.draw()

    if (outlineWidth > 0.001f) {
      GLES30.glCullFace(GLES30.GL_FRONT)
      GLES30.glUseProgram(outlineProgram)
      GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(outlineProgram, "uMVP"), 1, false, mvpMat, 0)
      GLES30.glUniform1f(GLES30.glGetUniformLocation(outlineProgram, "uOutlineWidth"), outlineWidth)
      GLES30.glUniform4f(GLES30.glGetUniformLocation(outlineProgram, "uOutlineColor"), 0.10f, 0.08f, 0.18f, 1f)
      mesh.draw()
      GLES30.glCullFace(GLES30.GL_BACK)
    }
  }

  private fun setTransform(
    base: FloatArray,
    tx: Float,
    ty: Float,
    tz: Float,
    rx: Float = 0f,
    ry: Float = 0f,
    rz: Float = 0f,
    sx: Float = 1f,
    sy: Float = 1f,
    sz: Float = 1f,
  ) {
    Matrix.setIdentityM(partMat, 0)
    Matrix.translateM(partMat, 0, base, 0, tx, ty, tz)
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
    x: Float,
    y: Float,
    z: Float,
    yawDeg: Float,
    moveSpeed: Float,
    animTime: Float,
    attackTimer: Float,
    comboStep: Int,
    isGliding: Boolean,
  ) {
    val arch = GameCatalog.heroById(heroId)
    val weapon = GameCatalog.weaponById(weaponId)

    val (hairR, hairG, hairB) =
      when (heroId) {
        "lyra" -> Triple(0.95f, 0.28f, 0.22f)
        "seraphina" -> Triple(0.84f, 0.80f, 0.99f)
        else -> Triple(0.78f, 0.88f, 0.99f)
      }
    val (coatR, coatG, coatB) =
      when (heroId) {
        "lyra" -> Triple(0.72f, 0.14f, 0.22f)
        "seraphina" -> Triple(0.28f, 0.20f, 0.56f)
        else -> Triple(0.14f, 0.24f, 0.44f)
      }
    val faceTex =
      when (heroId) {
        "lyra" -> textures.animeFacePyro
        "seraphina" -> textures.animeFaceCryo
        else -> textures.animeFaceAero
      }
    val elemRgb = colorHexToRgb(arch.primaryColorHex)
    val bladeRgb = colorHexToRgb(weapon.bladeColorHex)

    val runFactor = (moveSpeed / 8f).coerceIn(0f, 1f)
    val stride = sin(animTime * 11.5f) * 38f * runFactor
    val bob = abs(cos(animTime * 11.5f)) * 0.075f * runFactor + sin(animTime * 2.5f) * 0.02f
    val forwardLean = if (isGliding) 24f else runFactor * 14f

    Matrix.setIdentityM(modelMat, 0)
    Matrix.translateM(modelMat, 0, x, y + bob, z)
    Matrix.rotateM(modelMat, 0, yawDeg, 0f, 1f, 0f)
    Matrix.rotateM(modelMat, 0, forwardLean, 1f, 0f, 0f)

    // Tapered Thighs + Armored Knee-High Boots
    for ((side, sAngle) in listOf(-0.13f to stride, 0.13f to -stride)) {
      setTransform(modelMat, side, 0.56f, 0f, rx = sAngle, sx = 0.17f, sy = 0.40f, sz = 0.17f)
      drawCelPart(limbMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, 0.14f, 0.16f, 0.26f, rimColor = elemRgb)
      // Boot shaft with slight knee bend
      setTransform(modelMat, side, 0.22f, sin(sAngle * 0.017f) * 0.06f, rx = sAngle * 0.7f, sx = 0.15f, sy = 0.40f, sz = 0.16f)
      drawCelPart(limbMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, 0.10f, 0.11f, 0.18f, specStrength = 0.55f, rimColor = elemRgb)
      // Gold knee guard ring
      setTransform(modelMat, side, 0.40f, 0.02f, rx = sAngle * 0.85f, sx = 0.09f, sy = 0.09f, sz = 0.09f)
      drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, textures.white, 0.96f, 0.80f, 0.36f, specStrength = 0.8f, outlineWidth = 0f)
    }

    // Flared Pleated Anime Coat-Tail / Mantle
    val coatFlare = runFactor * 16f + sin(animTime * 6f) * 4f
    setTransform(modelMat, 0f, 0.72f, -0.02f, rx = coatFlare * 0.35f, sx = 0.52f, sy = 0.44f, sz = 0.44f)
    drawCelPart(skirtMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, coatR, coatG, coatB, rimColor = elemRgb)

    // Sculpted Athletic Anime Torso
    setTransform(modelMat, 0f, 1.07f, 0f, sx = 0.48f, sy = 0.48f, sz = 0.44f)
    drawCelPart(torsoMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, coatR, coatG, coatB, rimColor = elemRgb)

    // Ornate Collar Scarf & Belt Emblem
    setTransform(modelMat, 0f, 1.28f, 0.02f, rx = 12f, sx = 0.19f, sy = 0.14f, sz = 0.18f)
    drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, elemRgb[0], elemRgb[1], elemRgb[2], emissive = 0.22f, rimColor = elemRgb)
    setTransform(modelMat, 0f, 0.88f, 0.14f, ry = 45f, sx = 0.11f, sy = 0.11f, sz = 0.08f)
    drawCelPart(crystalMesh, vpMat, celProgram, outlineProgram, textures.rune, 1f, 0.86f, 0.36f, emissive = 0.7f, outlineWidth = 0f)

    // Flowing Twin Cape Ribbons + Vision Crystal on Back
    val capeWave = 16f + runFactor * 28f + sin(animTime * 6.5f) * 7f
    setTransform(modelMat, -0.11f, 1.00f, -0.18f, rx = capeWave, rz = -7f, sx = 0.22f, sy = 0.62f, sz = 0.08f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, elemRgb[0], elemRgb[1], elemRgb[2], emissive = 0.16f, rimColor = elemRgb)
    setTransform(modelMat, 0.11f, 1.00f, -0.18f, rx = capeWave + 3f, rz = 7f, sx = 0.22f, sy = 0.62f, sz = 0.08f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, coatR * 1.1f, coatG * 1.1f, coatB * 1.1f, rimColor = elemRgb)

    setTransform(modelMat, 0f, 1.18f, -0.21f, ry = animTime * 75f, sx = 0.15f, sy = 0.15f, sz = 0.15f)
    drawCelPart(crystalMesh, vpMat, celProgram, outlineProgram, textures.rune, elemRgb[0], elemRgb[1], elemRgb[2], emissive = 1.05f, specStrength = 0.85f, outlineWidth = 0.01f)

    // Neck + Expressive Sculpted Anime Head (with painted anime eyes & blush!)
    setTransform(modelMat, 0f, 1.35f, 0f, sx = 0.12f, sy = 0.16f, sz = 0.12f)
    drawCelPart(limbMesh, vpMat, celProgram, outlineProgram, textures.white, 0.99f, 0.89f, 0.83f, specStrength = 0f, outlineWidth = 0.01f)

    setTransform(modelMat, 0f, 1.56f, 0.02f, sx = 0.42f, sy = 0.44f, sz = 0.40f)
    drawCelPart(animeHeadMesh, vpMat, celProgram, outlineProgram, faceTex, 1f, 1f, 1f, specStrength = 0.04f, rimColor = elemRgb, outlineWidth = 0.012f)

    // Layered Stylized Anime Hair Crown + Sweeping Forehead Bangs + Side Locks + Back Ponytail
    setTransform(modelMat, 0f, 1.66f, -0.04f, sx = 0.47f, sy = 0.42f, sz = 0.47f)
    drawCelPart(sphereMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, hairR, hairG, hairB, specStrength = 0.48f, rimColor = elemRgb)
    // Forehead bangs (angled locks framing face above eyes)
    setTransform(modelMat, -0.10f, 1.72f, 0.18f, rx = -22f, rz = 18f, sx = 0.16f, sy = 0.22f, sz = 0.12f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairR, hairG, hairB, specStrength = 0.45f, rimColor = elemRgb, outlineWidth = 0.01f)
    setTransform(modelMat, 0.02f, 1.74f, 0.20f, rx = -25f, rz = -6f, sx = 0.18f, sy = 0.23f, sz = 0.12f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairR, hairG, hairB, specStrength = 0.45f, rimColor = elemRgb, outlineWidth = 0.01f)
    setTransform(modelMat, 0.13f, 1.71f, 0.17f, rx = -20f, rz = -20f, sx = 0.16f, sy = 0.22f, sz = 0.12f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairR, hairG, hairB, specStrength = 0.45f, rimColor = elemRgb, outlineWidth = 0.01f)
    // Twin cheek-framing side locks
    setTransform(modelMat, -0.20f, 1.64f, 0.08f, rx = 6f, rz = 10f, sx = 0.14f, sy = 0.36f, sz = 0.14f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairR, hairG, hairB, specStrength = 0.45f, rimColor = elemRgb)
    setTransform(modelMat, 0.20f, 1.64f, 0.08f, rx = 6f, rz = -10f, sx = 0.14f, sy = 0.36f, sz = 0.14f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairR, hairG, hairB, specStrength = 0.45f, rimColor = elemRgb)
    // Flowing layered back hair / ponytail
    val hairFlow = 22f + runFactor * 22f + sin(animTime * 5f) * 6f
    setTransform(modelMat, 0f, 1.62f, -0.19f, rx = hairFlow, sx = 0.32f, sy = 0.55f, sz = 0.22f)
    drawCelPart(hairLockMesh, vpMat, celProgram, outlineProgram, textures.white, hairR, hairG, hairB, specStrength = 0.48f, rimColor = elemRgb)

    // Arms + Shoulder Pauldrons + Weapon Swing
    val swingAngle =
      if (attackTimer > 0f) {
        val p = 1f - (attackTimer / 0.38f).coerceIn(0f, 1f)
        when (comboStep % 3) {
          0 -> -85f + p * 185f
          1 -> 90f - p * 190f
          else -> -120f + p * 250f
        }
      } else {
        -stride * 0.82f
      }
    val swingYaw = if (attackTimer > 0f) (if (comboStep % 2 == 0) -38f else 38f) else 0f

    // Shoulder Pauldrons
    setTransform(modelMat, -0.26f, 1.24f, 0f, rz = 18f, sx = 0.16f, sy = 0.12f, sz = 0.16f)
    drawCelPart(sphereMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, 0.94f, 0.78f, 0.36f, specStrength = 0.85f)
    setTransform(modelMat, 0.26f, 1.24f, 0f, rz = -18f, sx = 0.16f, sy = 0.12f, sz = 0.16f)
    drawCelPart(sphereMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, 0.94f, 0.78f, 0.36f, specStrength = 0.85f)

    // Left Arm (Tapered Sleeve + Gauntlet)
    setTransform(modelMat, -0.29f, 1.05f, 0f, rx = stride * 0.8f, rz = 8f, sx = 0.14f, sy = 0.42f, sz = 0.14f)
    drawCelPart(limbMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, coatR, coatG, coatB, rimColor = elemRgb)

    // Right Arm
    setTransform(modelMat, 0.29f, 1.05f, 0.04f, rx = swingAngle, ry = swingYaw, rz = -8f, sx = 0.14f, sy = 0.42f, sz = 0.14f)
    drawCelPart(limbMesh, vpMat, celProgram, outlineProgram, textures.heroCloth, coatR, coatG, coatB, rimColor = elemRgb)

    // Sculpted Signature Weapon
    if (heroId == "seraphina" || weaponId == "astral_codex") {
      val cy = 1.28f + sin(animTime * 4f) * 0.09f
      setTransform(modelMat, 0.48f, cy, 0.36f, ry = animTime * 115f, sx = 0.20f, sy = 0.28f, sz = 0.20f)
      drawCelPart(crystalMesh, vpMat, celProgram, outlineProgram, textures.rune, bladeRgb[0], bladeRgb[1], bladeRgb[2], emissive = 1.05f, specStrength = 0.9f)
      setTransform(modelMat, 0.48f, cy, 0.36f, rx = 68f, ry = -animTime * 90f, sx = 0.34f, sy = 0.34f, sz = 0.34f)
      drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, textures.rune, 1f, 0.86f, 0.42f, emissive = 0.85f, outlineWidth = 0f)
    } else {
      val swordRx = 74f + swingAngle
      // Double-edged beveled blade
      setTransform(modelMat, 0.33f, 0.94f, 0.20f, rx = swordRx, ry = swingYaw, sx = 0.18f, sy = 1.55f, sz = 0.06f)
      drawCelPart(swordBladeMesh, vpMat, celProgram, outlineProgram, textures.rune, bladeRgb[0], bladeRgb[1], bladeRgb[2], emissive = 0.82f, specStrength = 0.95f, rimColor = bladeRgb)
      // Ornate gold crossguard & grip
      setTransform(modelMat, 0.33f, 0.94f, 0.20f, rx = swordRx, ry = swingYaw, sx = 0.38f, sy = 0.07f, sz = 0.09f)
      drawCelPart(limbMesh, vpMat, celProgram, outlineProgram, textures.white, 0.98f, 0.82f, 0.34f, specStrength = 0.9f)
    }

    // 3D Glowing Combat Slash Crescent Ribbon during swing!
    if (attackTimer > 0f) {
      val slashRoll = when (comboStep % 3) {
        0 -> 14f
        1 -> -18f
        else -> 4f
      }
      setTransform(modelMat, 0f, 1.05f, 0.25f, rx = slashRoll, ry = swingYaw * 0.5f, sx = 1.05f, sy = 1f, sz = 1.05f)
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

    // Multi-Wing Astral Glider when airborne gliding!
    if (isGliding) {
      val flap = sin(animTime * 5.2f) * 7f
      for (dir in listOf(-1f, 1f)) {
        setTransform(modelMat, dir * 0.72f, 1.38f, -0.24f, rx = 16f, rz = -dir * (16f + flap), sx = 1.20f, sy = 0.05f, sz = 0.42f)
        drawCelPart(swordBladeMesh, vpMat, celProgram, outlineProgram, textures.rune, elemRgb[0], elemRgb[1], elemRgb[2], emissive = 0.85f)
        setTransform(modelMat, dir * 0.58f, 1.24f, -0.28f, rx = 24f, rz = -dir * (28f + flap), sx = 0.92f, sy = 0.04f, sz = 0.34f)
        drawCelPart(swordBladeMesh, vpMat, celProgram, outlineProgram, textures.rune, 1f, 0.88f, 0.45f, emissive = 0.75f)
      }
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

    Matrix.setIdentityM(modelMat, 0)
    Matrix.translateM(modelMat, 0, enemy.x, enemy.y + bob, enemy.z)
    Matrix.rotateM(modelMat, 0, enemy.yaw, 0f, 1f, 0f)

    if (enemy.isGolem) {
      // Sculpted Ancient Ruin Guardian
      setTransform(modelMat, -0.35f, 0.52f, 0f, sx = 0.36f, sy = 0.95f, sz = 0.36f)
      drawCelPart(limbMesh, vpMat, celProgram, outlineProgram, stoneTex, 0.36f, 0.40f, 0.52f, emissive = flash, rimColor = rgb)
      setTransform(modelMat, 0.35f, 0.52f, 0f, sx = 0.36f, sy = 0.95f, sz = 0.36f)
      drawCelPart(limbMesh, vpMat, celProgram, outlineProgram, stoneTex, 0.36f, 0.40f, 0.52f, emissive = flash, rimColor = rgb)
      // Armored chest
      setTransform(modelMat, 0f, 1.52f, 0f, sx = 1.45f, sy = 1.25f, sz = 1.15f)
      drawCelPart(torsoMesh, vpMat, celProgram, outlineProgram, stoneTex, 0.42f, 0.46f, 0.58f, emissive = flash, specStrength = 0.5f, rimColor = rgb)
      // Glowing Visor Core & Orbiting Halo
      setTransform(modelMat, 0f, 1.58f, 0.46f, ry = animTime * 80f, sx = 0.42f, sy = 0.42f, sz = 0.42f)
      drawCelPart(crystalMesh, vpMat, celProgram, outlineProgram, runeTex, rgb[0], rgb[1], rgb[2], emissive = 1.1f)
      setTransform(modelMat, 0f, 2.35f, 0f, rx = 75f, ry = animTime * 45f, sx = 0.72f, sy = 0.72f, sz = 0.72f)
      drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, runeTex, rgb[0], rgb[1], rgb[2], emissive = 0.9f, outlineWidth = 0f)
      // Broad armored shoulders
      setTransform(modelMat, -0.92f, 1.72f, 0f, rz = 16f, sx = 0.48f, sy = 0.85f, sz = 0.48f)
      drawCelPart(limbMesh, vpMat, celProgram, outlineProgram, stoneTex, 0.34f, 0.38f, 0.48f, emissive = flash, rimColor = rgb)
      setTransform(modelMat, 0.92f, 1.72f, 0f, rz = -16f, sx = 0.48f, sy = 0.85f, sz = 0.48f)
      drawCelPart(limbMesh, vpMat, celProgram, outlineProgram, stoneTex, 0.34f, 0.38f, 0.48f, emissive = flash, rimColor = rgb)
    } else {
      // Translucent-sheen Elemental Slime Spirit with Crown Horn
      setTransform(modelMat, 0f, 0.55f, 0f, sx = 1.12f, sy = 0.86f, sz = 1.12f)
      drawCelPart(sphereMesh, vpMat, celProgram, outlineProgram, runeTex, rgb[0], rgb[1], rgb[2], emissive = 0.45f + flash, specStrength = 0.9f, rimColor = rgb)
      setTransform(modelMat, 0f, 0.98f, 0f, sx = 0.24f, sy = 0.36f, sz = 0.24f)
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

    Matrix.setIdentityM(modelMat, 0)
    Matrix.translateM(modelMat, 0, 0f, hover, 0f)
    Matrix.rotateM(modelMat, 0, sin(animTime * 0.8f) * 16f, 0f, 1f, 0f)

    // Sculpted Armored Torso & Flowing Lower Mantle
    setTransform(modelMat, 0f, 1.75f, 0f, sx = 2.6f, sy = 2.6f, sz = 2.2f)
    drawCelPart(torsoMesh, vpMat, celProgram, outlineProgram, stoneTex, 0.20f, 0.22f, 0.36f, emissive = em * 0.4f, specStrength = 0.75f, rimColor = rgb, outlineWidth = 0.024f)
    setTransform(modelMat, 0f, 0.25f, 0f, sx = 2.4f, sy = 1.8f, sz = 2.2f)
    drawCelPart(skirtMesh, vpMat, celProgram, outlineProgram, runeTex, 0.16f, 0.18f, 0.30f, emissive = em * 0.35f, rimColor = rgb)

    // Radiant Boss Heart Crystal
    setTransform(modelMat, 0f, 1.85f, 0.82f, ry = animTime * 95f, sx = 0.72f, sy = 1.0f, sz = 0.72f)
    drawCelPart(crystalMesh, vpMat, celProgram, outlineProgram, runeTex, rgb[0], rgb[1], rgb[2], emissive = 1.25f, specStrength = 0.95f, outlineWidth = 0.015f)

    // Crowned Helmet + Sweeping Horns
    setTransform(modelMat, 0f, 3.45f, 0f, sx = 1.10f, sy = 1.20f, sz = 1.10f)
    drawCelPart(sphereMesh, vpMat, celProgram, outlineProgram, stoneTex, 0.15f, 0.18f, 0.30f, specStrength = 0.8f, rimColor = rgb, outlineWidth = 0.022f)
    setTransform(modelMat, -0.72f, 4.15f, 0f, rz = 26f, sx = 0.30f, sy = 1.45f, sz = 0.30f)
    drawCelPart(coneMesh, vpMat, celProgram, outlineProgram, runeTex, rgb[0], rgb[1], rgb[2], emissive = 0.95f)
    setTransform(modelMat, 0.72f, 4.15f, 0f, rz = -26f, sx = 0.30f, sy = 1.45f, sz = 0.30f)
    drawCelPart(coneMesh, vpMat, celProgram, outlineProgram, runeTex, rgb[0], rgb[1], rgb[2], emissive = 0.95f)

    // Astral Blade Wings
    for (dir in listOf(-1f, 1f)) {
      val wingSway = sin(animTime * 2.6f) * 8f
      setTransform(modelMat, dir * 2.2f, 2.3f, -0.45f, rx = 14f, rz = -dir * (38f + wingSway), sx = 0.52f, sy = 3.4f, sz = 0.20f)
      drawCelPart(swordBladeMesh, vpMat, celProgram, outlineProgram, runeTex, rgb[0], rgb[1], rgb[2], emissive = 0.95f)
    }

    // Orbiting Astral Rings
    setTransform(modelMat, 0f, 1.9f, 0f, rx = 68f + sin(animTime) * 12f, ry = animTime * 55f, sx = 3.3f, sy = 3.3f, sz = 3.3f)
    drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, runeTex, rgb[0], rgb[1], rgb[2], emissive = 1.05f, outlineWidth = 0f)
    setTransform(modelMat, 0f, 1.9f, 0f, rx = -62f, ry = -animTime * 42f, sx = 4.2f, sy = 4.2f, sz = 4.2f)
    drawCelPart(torusMesh, vpMat, celProgram, outlineProgram, runeTex, 1f, 0.86f, 0.42f, emissive = 0.90f, outlineWidth = 0f)

    // Ground Telegraph AoE Danger Ring when charging
    if (telegraphPct > 0.02f) {
      Matrix.setIdentityM(modelMat, 0)
      Matrix.translateM(modelMat, 0, 0f, 0.08f, 0f)
      val ringScale = 3.5f + telegraphPct * 6.5f
      setTransform(modelMat, 0f, 0f, 0f, sx = ringScale, sy = 0.25f, sz = ringScale)
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
