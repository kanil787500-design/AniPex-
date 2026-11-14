package com.example

import com.example.engine3d.CylinderObstacle
import com.example.engine3d.Math3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun walkableHeight_elevatesSanctuaryDaisAboveTerrain() {
    val centerY = Math3D.walkableWorldHeight(0f, 0f)
    val rimY = Math3D.walkableWorldHeight(12f, 0f)
    assertTrue("Center Sanctuary Dais should be elevated", centerY >= 0.50f)
    assertTrue("Outer terrain around dais step should drop down", centerY - rimY > 0.30f)
  }

  @Test
  fun resolveObstacles_pushesPlayerOutOfCylinder() {
    val obs = listOf(CylinderObstacle(x = 5f, z = 5f, radius = 1.0f))
    val (rx, rz) = Math3D.resolveObstacles(5.2f, 5.0f, 0.45f, obs)
    val dist = Math3D.distXZ(rx, rz, 5f, 5f)
    assertEquals(1.45f, dist, 0.01f)
  }

  @Test
  fun computeNormalMatrix_preservesOrthonormalIdentity() {
    val identity = FloatArray(16).apply {
      this[0] = 1f; this[5] = 1f; this[10] = 1f; this[15] = 1f
    }
    val out = FloatArray(9)
    Math3D.computeNormalMatrix(identity, out)
    assertEquals(1f, out[0], 1e-4f)
    assertEquals(1f, out[4], 1e-4f)
    assertEquals(1f, out[8], 1e-4f)
  }

  @Test
  fun celLightingConfig_producesUnitLengthLightVector() {
    val cfg = com.example.ui.shader.CelLightingConfig(lightAzimuthDeg = 135f, lightElevationDeg = 60f)
    val (lx, ly, lz) = cfg.normalizedLightVector()
    val len = kotlin.math.sqrt(lx * lx + ly * ly + lz * lz)
    assertEquals(1.0f, len, 1e-3f)
    assertTrue("Elevated key light points downward in screen coordinates", ly < 0f)
  }

  @Test
  fun animeCelAgsl_quantizesAndDeclaresRequiredUniforms() {
    val dark = com.example.ui.shader.AnimeCelAgsl.quantizeCelLuminance(0.12f, 3f)
    val bright = com.example.ui.shader.AnimeCelAgsl.quantizeCelLuminance(0.90f, 3f)
    assertTrue(bright > dark)
    val src = com.example.ui.shader.AnimeCelAgsl.SHADER_SOURCE
    listOf("uniform shader content;", "uniform float2 iResolution;", "uniform float3 iLightDir;", "uniform float iMode;")
      .forEach { uniform -> assertTrue("Missing AGSL uniform: $uniform", src.contains(uniform)) }
  }

  @Test
  fun graphicsQualityMod_defaultsToUltra3x() {
    val worldState = com.example.engine3d.GameWorldState()
    assertEquals(com.example.engine3d.GraphicsQualityTier.ULTRA_3X, worldState.graphicsQuality)
    assertEquals(3, com.example.engine3d.GraphicsQualityTier.ULTRA_3X.scale)
    assertEquals("3X ULTRA", com.example.engine3d.GraphicsQualityTier.ULTRA_3X.label)
  }

  @Test
  fun downtownCityDistrict_flattensStreetAndElevatesSidewalk() {
    val streetY = Math3D.openWorldHeight(0f, -29f)
    val sidewalkY = Math3D.walkableWorldHeight(6.0f, -22.0f)
    assertTrue("Downtown intersection street should be gently flattened", kotlin.math.abs(streetY) < 0.6f)
    assertTrue("Downtown sidewalk should be elevated for clean curb footing", sidewalkY >= 0.20f)
  }

  @Test
  fun aaaShaders_includePbrMicroBumpIblAndGodRays() {
    val cel = com.example.engine3d.Shaders.CEL_FRAGMENT
    assertTrue(cel.contains("applyMicroBump"))
    assertTrue(cel.contains("sampleEnvReflection"))
    assertTrue(cel.contains("puddleMask"))
    val comp = com.example.engine3d.Shaders.COMPOSITE_FRAGMENT
    assertTrue(comp.contains("uSunScreenPos"))
    assertTrue(comp.contains("uSunVisible"))
    assertTrue(comp.contains("uCinemaMode"))
  }

  @Test
  fun nteCityAndNpcs_populateDistinctNpcsAndCinematicDefaults() {
    val worldState = com.example.engine3d.GameWorldState()
    assertEquals(7, worldState.npcs.size)
    assertEquals(7, worldState.npcs.map { it.id }.toSet().size)
    assertTrue("Should have patrolling NPCs", worldState.npcs.any { it.moveSpeed > 0.5f })
    assertTrue("Should have stationary shop/guide NPCs", worldState.npcs.any { it.moveSpeed == 0f })
    assertTrue("Shoulder-close camera distance configured", worldState.camDistance <= 4.5f)
    assertTrue("Cinema shader mode present", com.example.engine3d.Shaders.CEL_FRAGMENT.contains("uCinemaMode"))
    assertTrue("LED building screen scanline mode present", com.example.engine3d.Shaders.CEL_FRAGMENT.contains("scanline"))
    assertTrue("Skybridge & billboard city specs present", com.example.engine3d.CityDistrictRenderer.urbanBuildings.any { it.hasBillboard })
  }
}
