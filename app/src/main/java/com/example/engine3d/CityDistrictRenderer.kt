package com.example.engine3d

import kotlin.math.cos
import kotlin.math.sin

data class UrbanBuildingSpec(
  val x: Float,
  val z: Float,
  val w: Float,
  val h: Float,
  val d: Float,
  val rotY: Float,
  val awningR: Float,
  val awningG: Float,
  val awningB: Float,
  val cyanNeon: Boolean,
  val hasBillboard: Boolean = false,
  val verticalBillboard: Boolean = false,
)

data class ParkedCarSpec(
  val x: Float,
  val z: Float,
  val yaw: Float,
  val r: Float,
  val g: Float,
  val b: Float,
)

/**
 * Modular NTE-style urban metropolis renderer:
 * - Wet reflective rain-puddle asphalt intersections & Scramble Crosswalks
 * - Elevated NTE Pedestrian Skybridge / Welcome Gantry (z = -20.5f) with digital billboard & hanging banners
 * - Multi-part glass/steel skyscrapers featuring animated Jumbotron LED screens (materialMode = 6f)
 * - Cantilevered double-sided streetlamp festival banners
 * - Maglev Sky-Rail viaduct + animated 3-car transit train & cross-street hover-cruisers
 */
object CityDistrictRenderer {

  val urbanBuildings =
    listOf(
      UrbanBuildingSpec(-22.0f, -19.5f, 9.5f, 15.0f, 8.0f, 22f, 0.16f, 0.68f, 0.78f, cyanNeon = true, hasBillboard = true, verticalBillboard = false),
      UrbanBuildingSpec(22.0f, -19.5f, 9.5f, 16.0f, 8.0f, -22f, 0.88f, 0.34f, 0.46f, cyanNeon = false, hasBillboard = true, verticalBillboard = true),
      UrbanBuildingSpec(-25.0f, 15.5f, 10.5f, 12.0f, 8.5f, 75f, 0.28f, 0.56f, 0.88f, cyanNeon = true, hasBillboard = false),
      UrbanBuildingSpec(-16.5f, -38.5f, 9.0f, 18.5f, 8.5f, 90f, 0.18f, 0.78f, 0.82f, cyanNeon = true, hasBillboard = true, verticalBillboard = false),
      UrbanBuildingSpec(16.5f, -38.5f, 9.2f, 17.5f, 8.5f, -90f, 0.88f, 0.42f, 0.56f, cyanNeon = false, hasBillboard = true, verticalBillboard = true),
      UrbanBuildingSpec(-16.5f, -51.5f, 9.5f, 22.0f, 9.0f, 90f, 0.28f, 0.54f, 0.92f, cyanNeon = true, hasBillboard = true, verticalBillboard = true),
      UrbanBuildingSpec(16.5f, -51.5f, 9.5f, 23.5f, 9.0f, -90f, 0.92f, 0.56f, 0.22f, cyanNeon = false, hasBillboard = true, verticalBillboard = false),
      UrbanBuildingSpec(-28.5f, -36.5f, 10.5f, 15.5f, 8.5f, 0f, 0.20f, 0.82f, 0.72f, cyanNeon = true, hasBillboard = true, verticalBillboard = false),
      UrbanBuildingSpec(28.5f, -36.5f, 10.5f, 17.5f, 8.5f, 0f, 0.84f, 0.36f, 0.68f, cyanNeon = false, hasBillboard = true, verticalBillboard = true),
      UrbanBuildingSpec(-36.5f, -52.5f, 12.5f, 27.5f, 11.0f, 18f, 0.24f, 0.52f, 0.86f, cyanNeon = true, hasBillboard = false),
      UrbanBuildingSpec(36.5f, -52.5f, 12.5f, 29.0f, 11.0f, -18f, 0.86f, 0.36f, 0.56f, cyanNeon = false, hasBillboard = false),
      UrbanBuildingSpec(-17.5f, -66.0f, 11.0f, 30.0f, 10.0f, 45f, 0.22f, 0.84f, 0.94f, cyanNeon = true, hasBillboard = true, verticalBillboard = false),
      UrbanBuildingSpec(17.5f, -66.0f, 11.0f, 32.0f, 10.0f, -45f, 0.94f, 0.34f, 0.62f, cyanNeon = false, hasBillboard = true, verticalBillboard = true),
    )

  val parkedCars =
    listOf(
      ParkedCarSpec(-2.9f, -22.5f, 0f, 0.18f, 0.76f, 0.94f),
      ParkedCar(2.9f, -36.0f, 180f, 0.92f, 0.28f, 0.54f),
    ).let {
      listOf(
        ParkedCarSpec(-2.9f, -22.5f, 0f, 0.18f, 0.76f, 0.94f),
        ParkedCarSpec(2.9f, -36.0f, 180f, 0.92f, 0.28f, 0.54f),
      )
    }

  val monorailPylonsX = listOf(-30f, -10f, 10f, 30f)
  val cityKiosks = listOf(Pair(-5.8f, -23.5f), Pair(5.8f, -35.0f))
  val skybridgePylonsX = listOf(-7.8f, 7.8f)
  const val SKYBRIDGE_Z = -20.5f

  // Helper dummy constructor alias avoided cleanly below
  private fun ParkedCar(x: Float, z: Float, yaw: Float, r: Float, g: Float, b: Float) =
    ParkedCarSpec(x, z, yaw, r, g, b)

  fun render(
    animTime: Float,
    textures: ProceduralTextures.TextureSet,
    boxMesh: GpuMesh,
    sphereMesh: GpuMesh,
    cylinderMesh: GpuMesh,
    coneMesh: GpuMesh,
    torusMesh: GpuMesh,
    streetLampPositions: List<Triple<Float, Float, Float>>,
    drawStaticMesh: (
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
      emissive: Float,
      specStrength: Float,
      outline: Boolean,
      rotX: Float,
      rotY: Float,
      uvScale: Float,
      windSway: Float,
      materialMode: Float,
      alphaCutout: Boolean,
    ) -> Unit,
  ) {
    // A. North-South & East-West Wet Asphalt Street Grid (materialMode = 4f: Specular Rain Puddle Reflections)
    for (segZ in listOf(-25.5f, -39.5f, -53.5f, -67.5f)) {
      drawStaticMesh(
        boxMesh, textures.roadAsphalt, 0f, 0.02f, segZ,
        8.4f, 0.14f, 14.2f, 0.95f, 0.96f, 1.0f, 0f, 0.42f, false, 0f, 0f, 1f, 0f, 4f, false,
      )
    }
    for (segX in listOf(-48.0f, -32.0f, -16.0f, 16.0f, 32.0f, 48.0f)) {
      drawStaticMesh(
        boxMesh, textures.roadAsphalt, segX, 0.01f, -29.0f,
        8.0f, 0.12f, 16.2f, 0.95f, 0.96f, 1.0f, 0f, 0.42f, false, 0f, 90f, 1f, 0f, 4f, false,
      )
    }

    // Crisp Pedestrian Crosswalk Stripes framing Downtown Scramble Crossings
    for (i in -3..3) {
      val off = i * 1.05f
      drawStaticMesh(boxMesh, textures.white, off, 0.105f, -24.2f, 0.48f, 0.03f, 1.85f, 0.90f, 0.92f, 0.96f, 0.08f, 0.22f, false, 0f, 0f, 1f, 0f, 0f, false)
      drawStaticMesh(boxMesh, textures.white, off, 0.105f, -33.8f, 0.48f, 0.03f, 1.85f, 0.90f, 0.92f, 0.96f, 0.08f, 0.22f, false, 0f, 0f, 1f, 0f, 0f, false)
      drawStaticMesh(boxMesh, textures.white, off, 0.105f, -44.2f, 0.48f, 0.03f, 1.85f, 0.90f, 0.92f, 0.96f, 0.08f, 0.22f, false, 0f, 0f, 1f, 0f, 0f, false)
      drawStaticMesh(boxMesh, textures.white, -4.9f, 0.105f, -29.0f + off, 1.85f, 0.03f, 0.48f, 0.90f, 0.92f, 0.96f, 0.08f, 0.22f, false, 0f, 0f, 1f, 0f, 0f, false)
      drawStaticMesh(boxMesh, textures.white, 4.9f, 0.105f, -29.0f + off, 1.85f, 0.03f, 0.48f, 0.90f, 0.92f, 0.96f, 0.08f, 0.22f, false, 0f, 0f, 1f, 0f, 0f, false)
    }

    // Raised Pedestrian Sidewalks flanking the N-S Avenue + Luminous Curb Trim
    for (sideX in listOf(-9.9f, 9.9f)) {
      drawStaticMesh(boxMesh, textures.plazaTile, sideX, 0.11f, -21.8f, 11.2f, 0.22f, 6.0f, 0.92f, 0.94f, 0.98f, 0f, 0.34f, false, 0f, 0f, 2.0f, 0f, 0f, false)
      drawStaticMesh(boxMesh, textures.plazaTile, sideX, 0.11f, -48.0f, 11.2f, 0.22f, 29.6f, 0.92f, 0.94f, 0.98f, 0f, 0.34f, false, 0f, 0f, 3.6f, 0f, 0f, false)
      val curbX = if (sideX < 0f) -4.35f else 4.35f
      drawStaticMesh(boxMesh, textures.white, curbX, 0.225f, -21.8f, 0.14f, 0.04f, 6.0f, 0.24f, 0.92f, 1f, 0.72f, 0.5f, false, 0f, 0f, 1f, 0f, 0f, false)
      drawStaticMesh(boxMesh, textures.white, curbX, 0.225f, -48.0f, 0.14f, 0.04f, 29.6f, 0.96f, 0.42f, 0.76f, 0.72f, 0.5f, false, 0f, 0f, 1f, 0f, 0f, false)
    }

    // B. Elevated NTE Pedestrian Skybridge & Welcome Gantry (z = -20.5f, height = 7.0m)
    val bridgeDeckY = 7.0f
    for (px in skybridgePylonsX) {
      val py = Math3D.walkableWorldHeight(px, SKYBRIDGE_Z)
      drawStaticMesh(cylinderMesh, textures.stone, px, py, SKYBRIDGE_Z, 0.45f, bridgeDeckY - py, 0.45f, 0.22f, 0.26f, 0.38f, 0f, 0.65f, false, 0f, 0f, 1f, 0f, 5f, false)
      drawStaticMesh(torusMesh, textures.white, px, py + 3.4f, SKYBRIDGE_Z, 0.54f, 0.16f, 0.54f, 0.25f, 0.95f, 1f, 1.0f, 0.5f, false, 0f, 0f, 1f, 0f, 0f, false)
    }
    // Bridge structural span, glass walls & glowing neon underside
    drawStaticMesh(boxMesh, textures.stone, 0f, bridgeDeckY, SKYBRIDGE_Z, 17.6f, 0.62f, 2.3f, 0.20f, 0.24f, 0.36f, 0f, 0.68f, false, 0f, 0f, 1f, 0f, 5f, false)
    drawStaticMesh(boxMesh, textures.buildingFacade, 0f, bridgeDeckY + 0.95f, SKYBRIDGE_Z, 16.4f, 1.32f, 1.95f, 0.85f, 0.95f, 1f, 0.12f, 0.92f, false, 0f, 0f, 2f, 0f, 5f, false)
    drawStaticMesh(boxMesh, textures.white, 0f, bridgeDeckY - 0.34f, SKYBRIDGE_Z + 1.02f, 17.2f, 0.14f, 0.14f, 0.25f, 0.95f, 1f, 1.15f, 0.4f, false, 0f, 0f, 1f, 0f, 0f, false)
    drawStaticMesh(boxMesh, textures.white, 0f, bridgeDeckY - 0.34f, SKYBRIDGE_Z - 1.02f, 17.2f, 0.14f, 0.14f, 0.98f, 0.40f, 0.72f, 1.15f, 0.4f, false, 0f, 0f, 1f, 0f, 0f, false)

    // Giant Central Widescreen Jumbotron Mounted on the Skybridge Facing South & North
    drawStaticMesh(boxMesh, textures.stone, 0f, bridgeDeckY + 1.65f, SKYBRIDGE_Z, 10.6f, 3.35f, 0.38f, 0.12f, 0.14f, 0.22f, 0f, 0.70f, false, 0f, 0f, 1f, 0f, 5f, false)
    drawStaticMesh(boxMesh, textures.buildingScreenA, 0f, bridgeDeckY + 1.65f, SKYBRIDGE_Z + 0.24f, 10.0f, 2.90f, 0.10f, 1f, 1f, 1f, 0.92f, 0.85f, false, 0f, 0f, 1f, 0f, 6f, false)
    drawStaticMesh(boxMesh, textures.buildingScreenA, 0f, bridgeDeckY + 1.65f, SKYBRIDGE_Z - 0.24f, 10.0f, 2.90f, 0.10f, 1f, 1f, 1f, 0.92f, 0.85f, false, 0f, 180f, 1f, 0f, 6f, false)

    // 4 Suspended Vertical Festival Banners Hanging Beneath the Skybridge Archway
    for ((idx, bx) in listOf(-5.8f, -3.5f, 3.5f, 5.8f).withIndex()) {
      val bannerTex = if (idx % 2 == 0) textures.streetBannerA else textures.streetBannerB
      drawStaticMesh(boxMesh, textures.stone, bx, bridgeDeckY - 0.55f, SKYBRIDGE_Z, 0.88f, 0.10f, 0.14f, 0.25f, 0.28f, 0.38f, 0f, 0.7f, false, 0f, 0f, 1f, 0f, 5f, false)
      drawStaticMesh(boxMesh, bannerTex, bx, bridgeDeckY - 1.68f, SKYBRIDGE_Z, 0.78f, 2.15f, 0.08f, 1f, 1f, 1f, 0.82f, 0.45f, false, 0f, 0f, 1f, 0f, 6f, false)
    }

    // C. Multi-Part NTE Setback Skyscrapers + Giant Animated Building LED Screens (materialMode = 6f)
    for (bld in urbanBuildings) {
      val by = Math3D.openWorldHeight(bld.x, bld.z)
      val rad = bld.rotY * Math3D.DEG_TO_RAD
      val fwdX = sin(rad)
      val fwdZ = cos(rad)
      val rightX = cos(rad)
      val rightZ = -sin(rad)

      // Core Glass-and-Steel Curtain Wall Facade
      drawStaticMesh(
        boxMesh, textures.buildingFacade, bld.x, by + bld.h * 0.5f - 0.4f, bld.z,
        bld.w, bld.h, bld.d, 0.96f, 0.97f, 1.0f, 0f, 0.84f, false, 0f, bld.rotY, 1f, 0f, 5f, false,
      )
      // Upper Architectural Setback Crown Volume on Taller Towers
      if (bld.h >= 19.0f) {
        drawStaticMesh(
          boxMesh, textures.buildingFacade, bld.x, by + bld.h + 1.8f, bld.z,
          bld.w * 0.72f, 3.8f, bld.d * 0.72f, 0.94f, 0.96f, 1.0f, 0f, 0.88f, false, 0f, bld.rotY, 1f, 0f, 5f, false,
        )
      }
      // Corner Structural Pilasters
      for (side in listOf(-1f, 1f)) {
        val px = bld.x + fwdX * (bld.d * 0.49f) + rightX * (side * bld.w * 0.47f)
        val pz = bld.z + fwdZ * (bld.d * 0.49f) + rightZ * (side * bld.w * 0.47f)
        drawStaticMesh(
          boxMesh, textures.stone, px, by + bld.h * 0.5f - 0.3f, pz,
          0.62f, bld.h + 0.3f, 0.62f, 0.24f, 0.28f, 0.40f, 0f, 0.48f, false, 0f, bld.rotY, 1f, 0f, 0f, false,
        )
      }
      // Mid Cornice + Parapet Crown + Glowing Neon Rooftop Rim
      drawStaticMesh(boxMesh, textures.stone, bld.x, by + 4.3f, bld.z, bld.w + 0.35f, 0.42f, bld.d + 0.35f, 0.32f, 0.36f, 0.48f, 0f, 0.42f, false, 0f, bld.rotY, 1f, 0f, 0f, false)
      drawStaticMesh(boxMesh, textures.stone, bld.x, by + bld.h - 0.15f, bld.z, bld.w + 0.55f, 0.62f, bld.d + 0.55f, 0.72f, 0.76f, 0.88f, 0f, 0.42f, false, 0f, bld.rotY, 1f, 0f, 0f, false)
      drawStaticMesh(
        boxMesh, textures.white, bld.x, by + bld.h + 0.22f, bld.z,
        bld.w * 0.94f, 0.18f, bld.d * 0.94f,
        if (bld.cyanNeon) 0.24f else 1.0f,
        if (bld.cyanNeon) 0.95f else 0.42f,
        if (bld.cyanNeon) 1.0f else 0.72f,
        1.05f, 0.5f, false, 0f, bld.rotY, 1f, 0f, 0f, false,
      )
      // Rooftop Mechanical Volume & Spire
      drawStaticMesh(boxMesh, textures.stone, bld.x - rightX * 1.2f, by + bld.h + 1.0f, bld.z - rightZ * 1.2f, bld.w * 0.42f, 1.5f, bld.d * 0.42f, 0.28f, 0.32f, 0.44f, 0f, 0.4f, false, 0f, bld.rotY, 1f, 0f, 0f, false)
      drawStaticMesh(cylinderMesh, textures.stone, bld.x + rightX * 1.6f, by + bld.h + 0.2f, bld.z + rightZ * 1.6f, 0.10f, 3.8f, 0.10f, 0.50f, 0.55f, 0.68f, 0f, 0.75f, false, 0f, 0f, 1f, 0f, 0f, false)

      // Ground-Floor Luminous Storefront Window & Striped Awning
      drawStaticMesh(
        boxMesh, textures.white,
        bld.x + fwdX * (bld.d * 0.50f + 0.08f), by + 1.65f, bld.z + fwdZ * (bld.d * 0.50f + 0.08f),
        bld.w * 0.80f, 2.3f, 0.12f, 0.82f, 0.95f, 1.0f, 0.78f, 0.6f, false, 0f, bld.rotY, 1f, 0f, 0f, false,
      )
      drawStaticMesh(
        boxMesh, textures.heroCloth,
        bld.x + fwdX * (bld.d * 0.5f + 0.85f), by + 3.25f, bld.z + fwdZ * (bld.d * 0.5f + 0.85f),
        bld.w * 0.88f, 0.35f, 2.0f, bld.awningR, bld.awningG, bld.awningB, 0f, 0.2f, false, 0f, bld.rotY, 1f, 0f, 0f, false,
      )

      // Giant Building Facade Jumbotron LED Screen (materialMode = 6f with dark aluminum bezel)
      if (bld.hasBillboard) {
        val screenTex = if (bld.verticalBillboard) textures.buildingScreenB else textures.buildingScreenA
        val scrW = if (bld.verticalBillboard) 4.2f else 6.8f
        val scrH = if (bld.verticalBillboard) 7.4f else 4.0f
        val scrY = by + (if (bld.verticalBillboard) 9.8f else 7.6f)
        val bezelOff = bld.d * 0.50f + 0.16f
        val screenOff = bld.d * 0.50f + 0.28f
        drawStaticMesh(
          boxMesh, textures.stone,
          bld.x + fwdX * bezelOff, scrY, bld.z + fwdZ * bezelOff,
          scrW + 0.52f, scrH + 0.52f, 0.20f, 0.12f, 0.14f, 0.22f, 0f, 0.65f, false, 0f, bld.rotY, 1f, 0f, 5f, false,
        )
        drawStaticMesh(
          boxMesh, screenTex,
          bld.x + fwdX * screenOff, scrY, bld.z + fwdZ * screenOff,
          scrW, scrH, 0.08f, 1f, 1f, 1f, 0.96f, 0.88f, false, 0f, bld.rotY, 1f, 0f, 6f, false,
        )
      }

      // Projecting Glowing Vertical Neon Signboard
      val neonTex = if (bld.cyanNeon) textures.neonSignA else textures.neonSignB
      val signX = bld.x + fwdX * (bld.d * 0.5f + 0.55f) + rightX * (bld.w * 0.36f)
      val signZ = bld.z + fwdZ * (bld.d * 0.5f + 0.55f) + rightZ * (bld.w * 0.36f)
      drawStaticMesh(
        boxMesh, neonTex, signX, by + 6.8f, signZ,
        1.55f, 4.4f, 0.28f, 1f, 1f, 1f, 1.05f, 0.5f, false, 0f, bld.rotY, 1f, 0f, 6f, false,
      )
    }

    // D. Double-Sided Streetlamp Festival Banners along Downtown Boulevard (z < -17f)
    for ((idx, lamp) in streetLampPositions.withIndex()) {
      val lx = lamp.first; val lz = lamp.second; val rot = lamp.third
      if (lz > -17f) continue
      val ly = Math3D.walkableWorldHeight(lx, lz)
      val rad = rot * Math3D.DEG_TO_RAD
      val armOffX = cos(rad) * 0.55f
      val armOffZ = -sin(rad) * 0.55f
      val bannerTex = if (idx % 2 == 0) textures.streetBannerA else textures.streetBannerB
      // Top & bottom cantilevered banner brackets
      drawStaticMesh(boxMesh, textures.stone, lx + armOffX, ly + 4.02f, lz + armOffZ, 0.85f, 0.08f, 0.10f, 0.28f, 0.32f, 0.44f, 0f, 0.65f, false, 0f, rot, 1f, 0f, 0f, false)
      drawStaticMesh(boxMesh, textures.stone, lx + armOffX, ly + 2.12f, lz + armOffZ, 0.85f, 0.08f, 0.10f, 0.28f, 0.32f, 0.44f, 0f, 0.65f, false, 0f, rot, 1f, 0f, 0f, false)
      // Crisp glowing street festival banner facing the street
      drawStaticMesh(boxMesh, bannerTex, lx + armOffX, ly + 3.07f, lz + armOffZ, 0.72f, 1.82f, 0.08f, 1f, 1f, 1f, 0.80f, 0.45f, false, 0f, rot + 90f, 1f, 0f, 6f, false)
    }

    // E. Elevated Maglev Sky-Rail Bridge + Gliding 3-Car NTE Transit Train (z = -45.5f, y = 14.5f)
    val bridgeZ = -45.5f
    val bridgeY = 14.5f
    drawStaticMesh(boxMesh, textures.stone, 0f, bridgeY, bridgeZ, 96f, 1.35f, 3.2f, 0.28f, 0.32f, 0.44f, 0f, 0.52f, false, 0f, 0f, 1f, 0f, 0f, false)
    drawStaticMesh(boxMesh, textures.white, 0f, bridgeY + 0.76f, bridgeZ + 1.56f, 96f, 0.22f, 0.14f, 0.24f, 0.94f, 1.0f, 1.15f, 0.5f, false, 0f, 0f, 1f, 0f, 0f, false)
    drawStaticMesh(boxMesh, textures.white, 0f, bridgeY + 0.76f, bridgeZ - 1.56f, 96f, 0.22f, 0.14f, 0.24f, 0.94f, 1.0f, 1.15f, 0.5f, false, 0f, 0f, 1f, 0f, 0f, false)
    for (px in monorailPylonsX) {
      drawStaticMesh(cylinderMesh, textures.stone, px, 0f, bridgeZ, 1.15f, bridgeY - 0.5f, 1.15f, 0.32f, 0.36f, 0.48f, 0f, 0.48f, false, 0f, 0f, 1f, 0f, 0f, false)
      drawStaticMesh(torusMesh, textures.white, px, bridgeY - 0.8f, bridgeZ, 1.35f, 0.32f, 1.35f, 0.26f, 0.92f, 1.0f, 0.95f, 0.5f, false, 0f, 0f, 1f, 0f, 0f, false)
    }
    val trainX = ((animTime * 11.5f) % 108f) - 54f
    for (carIdx in -1..1) {
      val cx = trainX + carIdx * 7.6f
      drawStaticMesh(boxMesh, textures.buildingFacade, cx, bridgeY + 1.65f, bridgeZ, 6.8f, 1.95f, 2.05f, 0.92f, 0.96f, 1.0f, 0f, 0.88f, false, 0f, 0f, 1f, 0f, 5f, false)
      drawStaticMesh(boxMesh, textures.white, cx, bridgeY + 1.85f, bridgeZ + 1.04f, 5.8f, 0.62f, 0.08f, 0.42f, 0.95f, 1.0f, 1.10f, 0.5f, false, 0f, 0f, 1f, 0f, 0f, false)
    }
    drawStaticMesh(sphereMesh, textures.white, trainX + 11.1f, bridgeY + 1.35f, bridgeZ, 0.42f, 0.42f, 0.85f, 1.0f, 0.92f, 0.55f, 1.45f, 0.5f, false, 0f, 0f, 1f, 0f, 0f, false)

    // Animated East-West Hover-Cruiser Traffic Gliding along Neon Boulevard (outside player crosswalk lanes)
    val eastCarX = ((animTime * 9.5f) % 88f) - 44f
    val westCarX = 44f - ((animTime * 8.2f + 24f) % 88f)
    drawCruiser(eastCarX, -27.2f, 90f, 0.16f, 0.80f, 0.96f, boxMesh, textures, drawStaticMesh)
    drawCruiser(westCarX, -30.8f, -90f, 0.96f, 0.34f, 0.52f, boxMesh, textures, drawStaticMesh)

    // F. Parked Clearcoat Cruisers, Metro Kiosks & Starlight Sidewalk Cafe Umbrellas
    for (car in parkedCars) {
      drawCruiser(car.x, car.z, car.yaw, car.r, car.g, car.b, boxMesh, textures, drawStaticMesh)
    }

    for ((idx, k) in cityKiosks.withIndex()) {
      val ky = Math3D.walkableWorldHeight(k.first, k.second)
      drawStaticMesh(
        boxMesh, if (idx == 0) textures.neonSignA else textures.neonSignB,
        k.first, ky + 1.15f, k.second, 1.1f, 2.2f, 0.85f, 1f, 1f, 1f, 0.85f, 0.78f, false, 0f, 0f, 1f, 0f, 6f, false,
      )
    }

    for ((idx, cafeX) in listOf(-7.5f, 7.5f).withIndex()) {
      val cafeZ = -21.2f
      val cy = Math3D.walkableWorldHeight(cafeX, cafeZ)
      drawStaticMesh(cylinderMesh, textures.stone, cafeX, cy, cafeZ, 0.08f, 2.3f, 0.08f, 0.35f, 0.38f, 0.45f, 0f, 0.3f, false, 0f, 0f, 1f, 0f, 0f, false)
      drawStaticMesh(cylinderMesh, textures.plazaTile, cafeX, cy + 0.78f, cafeZ, 0.95f, 0.10f, 0.95f, 0.96f, 0.96f, 1f, 0f, 0.45f, false, 0f, 0f, 1f, 0f, 0f, false)
      drawStaticMesh(
        coneMesh, textures.heroCloth, cafeX, cy + 2.55f, cafeZ,
        2.15f, 0.85f, 2.15f,
        if (idx == 0) 0.24f else 0.94f,
        if (idx == 0) 0.78f else 0.44f,
        if (idx == 0) 0.94f else 0.56f,
        0f, 0.14f, false, 0f, 0f, 1f, 0f, 0f, false,
      )
    }
  }

  private fun drawCruiser(
    x: Float,
    z: Float,
    yaw: Float,
    r: Float,
    g: Float,
    b: Float,
    boxMesh: GpuMesh,
    textures: ProceduralTextures.TextureSet,
    drawStaticMesh: (
      mesh: GpuMesh, texId: Int, tx: Float, ty: Float, tz: Float,
      sx: Float, sy: Float, sz: Float, r: Float, g: Float, b: Float,
      emissive: Float, specStrength: Float, outline: Boolean,
      rotX: Float, rotY: Float, uvScale: Float, windSway: Float,
      materialMode: Float, alphaCutout: Boolean,
    ) -> Unit,
  ) {
    val cy = Math3D.walkableWorldHeight(x, z)
    val yawRad = yaw * Math3D.DEG_TO_RAD
    val fwdX = sin(yawRad); val fwdZ = cos(yawRad)
    val rightX = cos(yawRad); val rightZ = -sin(yawRad)
    drawStaticMesh(boxMesh, textures.heroCloth, x, cy + 0.52f, z, 1.78f, 0.56f, 3.65f, r, g, b, 0f, 0.92f, false, 0f, yaw, 1f, 0f, 5f, false)
    drawStaticMesh(boxMesh, textures.stone, x - fwdX * 0.18f, cy + 0.98f, z - fwdZ * 0.18f, 1.42f, 0.44f, 1.85f, 0.12f, 0.16f, 0.28f, 0f, 0.96f, false, 0f, yaw, 1f, 0f, 5f, false)
    for (side in listOf(-0.56f, 0.56f)) {
      drawStaticMesh(
        boxMesh, textures.white,
        x + fwdX * 1.84f + rightX * side, cy + 0.52f, z + fwdZ * 1.84f + rightZ * side,
        0.32f, 0.16f, 0.12f, 0.72f, 0.98f, 1.0f, 1.45f, 0.5f, false, 0f, yaw, 1f, 0f, 0f, false,
      )
    }
    drawStaticMesh(
      boxMesh, textures.white,
      x - fwdX * 1.84f, cy + 0.55f, z - fwdZ * 1.84f,
      1.45f, 0.16f, 0.12f, 1.0f, 0.24f, 0.36f, 1.35f, 0.5f, false, 0f, yaw, 1f, 0f, 0f, false,
    )
  }
}
