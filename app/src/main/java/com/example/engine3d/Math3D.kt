package com.example.engine3d

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

data class CylinderObstacle(
  val x: Float,
  val z: Float,
  val radius: Float,
)

object Math3D {
  const val DEG_TO_RAD = (PI / 180.0).toFloat()
  const val RAD_TO_DEG = (180.0 / PI).toFloat()

  fun clamp(v: Float, min: Float, max: Float): Float = v.coerceIn(min, max)

  fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

  fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
  }

  fun lerpAngleDeg(a: Float, b: Float, t: Float): Float {
    var diff = ((b - a + 540f) % 360f) - 180f
    if (diff < -180f) diff += 360f
    return a + diff * t
  }

  fun distXZ(x1: Float, z1: Float, x2: Float, z2: Float): Float {
    val dx = x2 - x1
    val dz = z2 - z1
    return sqrt(dx * dx + dz * dz)
  }

  fun dist3(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    val dz = z2 - z1
    return sqrt(dx * dx + dy * dy + dz * dz)
  }

  fun yawToward(fromX: Float, fromZ: Float, toX: Float, toZ: Float): Float {
    return atan2(toX - fromX, toZ - fromZ) * RAD_TO_DEG
  }

  fun computeNormalMatrix(modelMat: FloatArray, outNormalMat: FloatArray) {
    val a00 = modelMat[0]; val a01 = modelMat[4]; val a02 = modelMat[8]
    val a10 = modelMat[1]; val a11 = modelMat[5]; val a12 = modelMat[9]
    val a20 = modelMat[2]; val a21 = modelMat[6]; val a22 = modelMat[10]

    val b01 = a22 * a11 - a12 * a21
    val b11 = -a22 * a10 + a12 * a20
    val b21 = a21 * a10 - a11 * a20

    val det = a00 * b01 + a01 * b11 + a02 * b21
    if (kotlin.math.abs(det) < 1e-6f) {
      outNormalMat[0] = 1f; outNormalMat[1] = 0f; outNormalMat[2] = 0f
      outNormalMat[3] = 0f; outNormalMat[4] = 1f; outNormalMat[5] = 0f
      outNormalMat[6] = 0f; outNormalMat[7] = 0f; outNormalMat[8] = 1f
      return
    }
    val invDet = 1f / det
    outNormalMat[0] = b01 * invDet
    outNormalMat[1] = b11 * invDet
    outNormalMat[2] = b21 * invDet
    outNormalMat[3] = (-a22 * a01 + a02 * a21) * invDet
    outNormalMat[4] = (a22 * a00 - a02 * a20) * invDet
    outNormalMat[5] = (-a21 * a00 + a01 * a20) * invDet
    outNormalMat[6] = (a12 * a01 - a02 * a11) * invDet
    outNormalMat[7] = (-a12 * a00 + a02 * a10) * invDet
    outNormalMat[8] = (a11 * a00 - a01 * a10) * invDet
  }

  /**
   * Procedural terrain heightfield for Aetherbound's open world.
   */
  fun openWorldHeight(x: Float, z: Float): Float {
    val distFromCenter = sqrt(x * x + z * z)
    val smoothPlateau = smoothstep(14f, 28f, distFromCenter)

    val hill1 = sin(x * 0.042f) * cos(z * 0.042f) * 5.2f
    val hill2 = sin(x * 0.088f + 1.3f) * sin(z * 0.082f + 0.7f) * 2.1f
    val detail = cos((x + z) * 0.16f) * 0.42f

    // Sculpted Moonlit Lake basin around (32, 24)
    val lakeDist = distXZ(x, z, 32f, 24f)
    val lakeCarve = (1f - smoothstep(8f, 24f, lakeDist)) * 5.4f

    // Gently level NTE Downtown District (x in [-38, 38], z in [-60, -17]) for clean streets & sidewalks
    val inCityX = smoothstep(42f, 26f, kotlin.math.abs(x))
    val inCityZ = smoothstep(-15f, -21f, z) * smoothstep(-66f, -54f, z)
    val cityFlatten = 1f - (inCityX * inCityZ * 0.96f)

    // Outer mountain ridges
    val rimLift = ((distFromCenter - 56f) / 42f).coerceIn(0f, 1.7f)
    val mountain = rimLift * rimLift * 15.5f

    return (hill1 + hill2 + detail + mountain) * smoothPlateau * cityFlatten - lakeCarve
  }

  /**
   * Walkable surface height including the stepped Central Sanctuary Dais and downtown NTE sidewalks.
   */
  fun walkableWorldHeight(x: Float, z: Float): Float {
    var h = openWorldHeight(x, z)
    val distFromCenter = sqrt(x * x + z * z)
    // Stepped circular Sanctuary Dais at origin (radius 9.5m, height 0.55m)
    if (distFromCenter < 9.6f) {
      val daisLift = (1f - smoothstep(7.8f, 9.6f, distFromCenter)) * 0.55f
      h = max(h, daisLift)
    }
    // Raised NTE downtown pedestrian sidewalks flanking the asphalt intersection
    val ax = kotlin.math.abs(x)
    if (z in -56f..-20f && ax in 4.3f..15.5f && z !in -33.2f..-24.8f) {
      h = max(h, 0.22f)
    }
    return h
  }

  fun openWorldNormal(x: Float, z: Float): FloatArray {
    val eps = 0.55f
    val hL = openWorldHeight(x - eps, z)
    val hR = openWorldHeight(x + eps, z)
    val hD = openWorldHeight(x, z - eps)
    val hU = openWorldHeight(x, z + eps)
    val nx = hL - hR
    val ny = 2f * eps
    val nz = hD - hU
    val len = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
    return floatArrayOf(nx / len, ny / len, nz / len)
  }

  /**
   * Resolves horizontal circle-vs-circle collisions against pillars, arches, and bosses.
   */
  fun resolveObstacles(
    x: Float,
    z: Float,
    playerRadius: Float,
    obstacles: List<CylinderObstacle>,
  ): Pair<Float, Float> {
    var px = x
    var pz = z
    for (obs in obstacles) {
      val dx = px - obs.x
      val dz = pz - obs.z
      val minDist = playerRadius + obs.radius
      val d2 = dx * dx + dz * dz
      if (d2 < minDist * minDist) {
        val d = sqrt(d2).coerceAtLeast(0.0001f)
        val push = minDist - d
        px += (dx / d) * push
        pz += (dz / d) * push
      }
    }
    return px to pz
  }
}
