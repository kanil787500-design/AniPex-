package com.example.engine3d

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.opengl.GLES30
import android.opengl.GLUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

object ProceduralTextures {

  data class TextureSet(
    val grass: Int,
    val stone: Int,
    val plazaTile: Int,
    val roadAsphalt: Int,
    val rune: Int,
    val foliageCard: Int,
    val sakuraCard: Int,
    val heroCloth: Int,
    val arenaObsidian: Int,
    val animeFaceAero: Int,
    val animeFacePyro: Int,
    val animeFaceCryo: Int,
    val animeFaceNpcCyan: Int,
    val animeFaceNpcAmber: Int,
    val animeFaceNpcViolet: Int,
    val bark: Int,
    val buildingFacade: Int,
    val neonSignA: Int,
    val neonSignB: Int,
    val buildingScreenA: Int,
    val buildingScreenB: Int,
    val streetBannerA: Int,
    val streetBannerB: Int,
    val white: Int,
  )

  fun createAll(): TextureSet {
    return TextureSet(
      grass = uploadBitmap(buildGrassBitmap(1024)),
      stone = uploadBitmap(buildStoneBitmap(1024)),
      plazaTile = uploadBitmap(buildPlazaTileBitmap(1024)),
      roadAsphalt = uploadBitmap(buildCityRoadBitmap(1024)),
      rune = uploadBitmap(buildRuneBitmap(1024)),
      foliageCard = uploadBitmap(buildLeafCardBitmap(1024, sakura = false), clampEdge = true),
      sakuraCard = uploadBitmap(buildLeafCardBitmap(1024, sakura = true), clampEdge = true),
      heroCloth = uploadBitmap(buildClothBitmap(1024)),
      arenaObsidian = uploadBitmap(buildObsidianArenaBitmap(1024)),
      animeFaceAero =
        uploadBitmap(
          buildAnimeFaceBitmap(
            size = 2048,
            irisLight = Color.rgb(74, 248, 224),
            irisDark = Color.rgb(12, 102, 162),
            isMale = true,
          ),
          clampEdge = true,
        ),
      animeFacePyro =
        uploadBitmap(
          buildAnimeFaceBitmap(
            size = 2048,
            irisLight = Color.rgb(255, 154, 78),
            irisDark = Color.rgb(186, 30, 44),
            isMale = false,
          ),
          clampEdge = true,
        ),
      animeFaceCryo =
        uploadBitmap(
          buildAnimeFaceBitmap(
            size = 2048,
            irisLight = Color.rgb(164, 214, 255),
            irisDark = Color.rgb(52, 86, 224),
            isMale = false,
          ),
          clampEdge = true,
        ),
      animeFaceNpcCyan =
        uploadBitmap(
          buildAnimeFaceBitmap(
            size = 1024,
            irisLight = Color.rgb(88, 236, 244),
            irisDark = Color.rgb(24, 92, 148),
            isMale = true,
          ),
          clampEdge = true,
        ),
      animeFaceNpcAmber =
        uploadBitmap(
          buildAnimeFaceBitmap(
            size = 1024,
            irisLight = Color.rgb(255, 206, 108),
            irisDark = Color.rgb(172, 92, 34),
            isMale = false,
          ),
          clampEdge = true,
        ),
      animeFaceNpcViolet =
        uploadBitmap(
          buildAnimeFaceBitmap(
            size = 1024,
            irisLight = Color.rgb(214, 158, 255),
            irisDark = Color.rgb(92, 48, 178),
            isMale = false,
          ),
          clampEdge = true,
        ),
      bark = uploadBitmap(buildBarkBitmap(512)),
      buildingFacade = uploadBitmap(buildBuildingFacadeBitmap(1024)),
      neonSignA = uploadBitmap(buildNeonSignBitmap(512, 1024, cyanTheme = true), clampEdge = true),
      neonSignB = uploadBitmap(buildNeonSignBitmap(512, 1024, cyanTheme = false), clampEdge = true),
      buildingScreenA = uploadBitmap(buildBuildingScreenBitmap(1024, 512, widescreen = true), clampEdge = true),
      buildingScreenB = uploadBitmap(buildBuildingScreenBitmap(512, 1024, widescreen = false), clampEdge = true),
      streetBannerA = uploadBitmap(buildStreetBannerBitmap(256, 512, cyanTheme = true), clampEdge = true),
      streetBannerB = uploadBitmap(buildStreetBannerBitmap(256, 512, cyanTheme = false), clampEdge = true),
      white = uploadBitmap(buildSolidWhiteBitmap()),
    )
  }

  private fun uploadBitmap(bitmap: Bitmap, clampEdge: Boolean = false): Int {
    val texIds = IntArray(1)
    GLES30.glGenTextures(1, texIds, 0)
    val id = texIds[0]
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
    val wrap = if (clampEdge) GLES30.GL_CLAMP_TO_EDGE else GLES30.GL_REPEAT
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap)
    GLES30.glTexParameteri(
      GLES30.GL_TEXTURE_2D,
      GLES30.GL_TEXTURE_MIN_FILTER,
      GLES30.GL_LINEAR_MIPMAP_LINEAR,
    )
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
    GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
    GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
    bitmap.recycle()
    return id
  }

  private fun buildSolidWhiteBitmap(): Bitmap {
    return Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply {
      eraseColor(Color.WHITE)
    }
  }

  /**
   * 3x Ultra-Crisp (1536px) Studio-Grade Anime Face Texture calibrated to MeshBuilder.createAnimeHead:
   * - Clean luminous porcelain skin with soft anime nose-bridge highlight, cheek gradient & chin shadow
   * - Multi-layered jewel-starlight eyes with winged upper lashes, double eyelid creases, sclera shading,
   *   diamond pupil highlights, and gender-tuned brow/lash silhouettes (NTE / Wuthering / Genshin style)
   */
  private fun buildAnimeFaceBitmap(size: Int, irisLight: Int, irisDark: Int, isMale: Boolean): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val s = size.toFloat()

    // Base warm-porcelain anime skin with delicate forehead-to-chin luminosity gradient
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader =
      LinearGradient(
        0f, s * 0.25f, 0f, s * 0.88f,
        intArrayOf(Color.rgb(255, 238, 227), Color.rgb(255, 234, 222), Color.rgb(246, 212, 202)),
        floatArrayOf(0f, 0.65f, 1f),
        Shader.TileMode.CLAMP,
      )
    canvas.drawRect(0f, 0f, s, s, paint)
    paint.shader = null

    // Soft anime cheek blush + crisp specular ovals
    val blushAlpha = if (isMale) 68 else 118
    for (cx in listOf(s * 0.30f, s * 0.70f)) {
      paint.shader =
        RadialGradient(
          cx, s * 0.592f, s * 0.088f,
          intArrayOf(Color.argb(blushAlpha, 255, 122, 142), Color.argb(0, 255, 122, 142)),
          null,
          Shader.TileMode.CLAMP,
        )
      canvas.drawCircle(cx, s * 0.592f, s * 0.088f, paint)
    }
    paint.shader = null

    paint.color = Color.argb(if (isMale) 52 else 88, 255, 252, 250)
    canvas.drawOval(RectF(s * 0.275f, s * 0.576f, s * 0.312f, s * 0.590f), paint)
    canvas.drawOval(RectF(s * 0.688f, s * 0.576f, s * 0.725f, s * 0.590f), paint)

    // Delicate anime nose-bridge cel highlight
    paint.color = Color.argb(85, 255, 252, 248)
    canvas.drawRoundRect(RectF(s * 0.494f, s * 0.50f, s * 0.506f, s * 0.585f), 10f, 10f, paint)

    fun drawEye(cx: Float, flip: Float) {
      val cy = s * 0.482f
      val ew = s * (if (isMale) 0.094f else 0.098f)
      val eh = s * (if (isMale) 0.080f else 0.096f)

      // Soft upper-socket ambient shadow
      paint.style = Paint.Style.FILL
      paint.color = Color.argb(52, 172, 142, 170)
      canvas.drawOval(RectF(cx - ew * 1.08f, cy - eh * 1.12f, cx + ew * 1.08f, cy - eh * 0.04f), paint)

      // Bright clear anime sclera
      paint.color = Color.rgb(254, 254, 255)
      canvas.drawOval(RectF(cx - ew, cy - eh * 0.72f, cx + ew, cy + eh * 0.74f), paint)

      // Cool lavender-indigo upper sclera cel shadow
      paint.color = Color.argb(92, 114, 132, 198)
      canvas.drawOval(RectF(cx - ew * 0.96f, cy - eh * 0.72f, cx + ew * 0.96f, cy - eh * 0.14f), paint)

      // Tall Luminous Jewel-Starlight Iris
      val irW = ew * (if (isMale) 0.65f else 0.70f)
      val irH = eh * (if (isMale) 0.75f else 0.78f)
      paint.shader =
        LinearGradient(
          cx, cy - irH, cx, cy + irH,
          intArrayOf(Color.rgb(12, 16, 40), irisDark, irisLight, Color.rgb(232, 254, 255)),
          floatArrayOf(0f, 0.36f, 0.78f, 1.0f),
          Shader.TileMode.CLAMP,
        )
      canvas.drawOval(RectF(cx - irW, cy - irH, cx + irW, cy + irH), paint)
      paint.shader = null

      // Crisp dark limbal rim for high readability
      paint.style = Paint.Style.STROKE
      paint.strokeWidth = s * 0.0052f
      paint.color = Color.argb(220, 18, 22, 50)
      canvas.drawOval(RectF(cx - irW, cy - irH, cx + irW, cy + irH), paint)

      // Glowing lower iris starlight crescent
      paint.strokeWidth = s * 0.0078f
      paint.color = Color.argb(228, 255, 255, 255)
      canvas.drawArc(
        RectF(cx - irW * 0.74f, cy - irH * 0.62f, cx + irW * 0.74f, cy + irH * 0.82f),
        22f, 136f, false, paint,
      )

      // Deep pupil + 4-point star sparkle core
      paint.style = Paint.Style.FILL
      paint.color = Color.rgb(12, 14, 34)
      canvas.drawOval(RectF(cx - irW * 0.38f, cy - irH * 0.38f, cx + irW * 0.38f, cy + irH * 0.36f), paint)

      paint.color = irisLight
      val starR = irW * 0.24f
      val starPath = Path().apply {
        moveTo(cx, cy - starR)
        lineTo(cx + starR * 0.28f, cy - starR * 0.28f)
        lineTo(cx + starR, cy)
        lineTo(cx + starR * 0.28f, cy + starR * 0.28f)
        lineTo(cx, cy + starR)
        lineTo(cx - starR * 0.28f, cy + starR * 0.28f)
        lineTo(cx - starR, cy)
        lineTo(cx - starR * 0.28f, cy - starR * 0.28f)
        close()
      }
      canvas.drawPath(starPath, paint)

      // Crisp anime catchlights (large upper diamond-oval + secondary specular + tiny gleam)
      paint.color = Color.WHITE
      canvas.drawOval(
        RectF(
          cx - flip * irW * 0.42f - irW * 0.31f,
          cy - irH * 0.44f - irH * 0.24f,
          cx - flip * irW * 0.42f + irW * 0.31f,
          cy - irH * 0.44f + irH * 0.24f,
        ),
        paint,
      )
      canvas.drawCircle(cx + flip * irW * 0.36f, cy + irH * 0.34f, irW * 0.18f, paint)
      canvas.drawCircle(cx + flip * irW * 0.56f, cy - irH * 0.30f, irW * 0.09f, paint)

      // Razor-Crisp Stylized Curved Upper Eyelash with Flicked Outer Wing
      paint.style = Paint.Style.STROKE
      paint.color = Color.rgb(20, 16, 36)
      paint.strokeWidth = s * (if (isMale) 0.0180f else 0.0205f)
      paint.strokeCap = Paint.Cap.ROUND
      val innerDrop = eh * 0.16f
      val outerWingX = cx - flip * ew * 1.24f
      val innerX = cx + flip * ew * 1.04f
      val lash = Path().apply {
        moveTo(outerWingX, cy - eh * 0.34f)
        quadTo(cx, cy - eh * 1.06f, innerX, cy - innerDrop)
      }
      canvas.drawPath(lash, paint)

      paint.strokeWidth = s * 0.0095f
      canvas.drawLine(outerWingX, cy - eh * 0.34f, outerWingX - flip * ew * 0.20f, cy - eh * 0.54f, paint)

      // Delicate double eyelid crease
      paint.strokeWidth = s * 0.0048f
      paint.color = Color.argb(180, 146, 98, 100)
      val crease = Path().apply {
        moveTo(cx - ew * 0.88f, cy - eh * 0.84f)
        quadTo(cx, cy - eh * 1.28f, cx + ew * 0.86f, cy - eh * 0.74f)
      }
      canvas.drawPath(crease, paint)

      // Crisp broken lower lash line
      paint.strokeWidth = s * 0.0052f
      paint.color = Color.argb(180, 48, 36, 62)
      val lowerLash = Path().apply {
        moveTo(cx - ew * 0.62f, cy + eh * 0.76f)
        quadTo(cx, cy + eh * 0.92f, cx + ew * 0.58f, cy + eh * 0.74f)
      }
      canvas.drawPath(lowerLash, paint)

      // Expressive Clear NTE Eyebrow
      paint.strokeWidth = s * (if (isMale) 0.0095f else 0.0076f)
      paint.color = Color.rgb(46, 38, 68)
      val browInnerY = cy - eh * (if (isMale) 1.18f else 1.26f)
      val browOuterY = cy - eh * (if (isMale) 1.44f else 1.22f)
      val brow = Path().apply {
        moveTo(cx + flip * ew * 0.95f, browInnerY)
        quadTo(cx, cy - eh * 1.50f, cx - flip * ew * 1.02f, browOuterY)
      }
      canvas.drawPath(brow, paint)
    }

    drawEye(s * 0.358f, 1f)
    drawEye(s * 0.642f, -1f)

    // Clear crisp anime nose shadow triangle + tiny specular point
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(148, 192, 118, 112)
    val nose = Path().apply {
      moveTo(s * 0.493f, s * 0.586f)
      lineTo(s * 0.507f, s * 0.586f)
      lineTo(s * 0.499f, s * 0.610f)
      close()
    }
    canvas.drawPath(nose, paint)

    // Refined Clear Anime Mouth
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = s * 0.0064f
    paint.strokeCap = Paint.Cap.ROUND
    paint.color = Color.rgb(156, 76, 82)
    val mouth = Path().apply {
      if (isMale) {
        moveTo(s * 0.456f, s * 0.668f)
        quadTo(s * 0.502f, s * 0.686f, s * 0.546f, s * 0.658f)
      } else {
        moveTo(s * 0.454f, s * 0.666f)
        quadTo(s * 0.50f, s * 0.694f, s * 0.546f, s * 0.666f)
      }
    }
    canvas.drawPath(mouth, paint)
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(78, 210, 122, 126)
    canvas.drawOval(RectF(s * 0.484f, s * 0.688f, s * 0.516f, s * 0.700f), paint)
    return bmp
  }

  /**
   * 3x Crisp (1024px) Alpha-cutout painted anime tree foliage / sakura blossom spray card!
   */
  private fun buildLeafCardBitmap(size: Int, sakura: Boolean): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bmp.eraseColor(Color.TRANSPARENT)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val rng = Random(if (sakura) 901 else 404)
    val cx = size * 0.5f
    val cy = size * 0.52f

    val leafCount = 115
    for (i in 0 until leafCount) {
      val frac = i.toFloat() / leafCount
      val angle = rng.nextFloat() * 2f * PI.toFloat()
      val radius = sqrtApprox(rng.nextFloat()) * size * 0.39f
      val lx = cx + cos(angle) * radius
      val ly = cy + sin(angle) * radius * 0.88f
      val leafW = size * (0.052f + rng.nextFloat() * 0.044f)
      val leafH = leafW * (if (sakura) 0.92f else 0.58f)

      val sunBias = (1f - ly / size).coerceIn(0f, 1f)
      paint.color =
        if (sakura) {
          when {
            frac < 0.28f -> Color.rgb(212, 108, 152)
            sunBias > 0.58f || frac > 0.72f -> Color.rgb(255, 224, 238)
            else -> Color.rgb(255, 166, 202)
          }
        } else {
          when {
            frac < 0.28f -> Color.rgb(52, 118, 86)
            sunBias > 0.58f || frac > 0.72f -> Color.rgb(174, 232, 118)
            else -> Color.rgb(92, 178, 104)
          }
        }

      canvas.save()
      canvas.translate(lx, ly)
      canvas.rotate(rng.nextFloat() * 360f)
      if (sakura && i % 3 == 0) {
        for (p in 0 until 5) {
          canvas.rotate(72f)
          canvas.drawOval(RectF(-leafW * 0.38f, -leafW * 0.95f, leafW * 0.38f, 0f), paint)
        }
        paint.color = Color.rgb(255, 240, 170)
        canvas.drawCircle(0f, 0f, leafW * 0.16f, paint)
      } else {
        val path = Path().apply {
          moveTo(-leafW, 0f)
          quadTo(0f, -leafH, leafW, 0f)
          quadTo(0f, leafH, -leafW, 0f)
          close()
        }
        canvas.drawPath(path, paint)
      }
      canvas.restore()
    }
    return bmp
  }

  private fun sqrtApprox(v: Float): Float = kotlin.math.sqrt(v.coerceAtLeast(0f))

  private fun buildGrassBitmap(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    // Rich natural alpine meadow earth & moss base
    canvas.drawColor(Color.rgb(66, 98, 52))
    val rng = Random(42)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    for (i in 0 until 1100) {
      val x = rng.nextFloat() * size
      val y = rng.nextFloat() * size
      val r = 10f + rng.nextFloat() * 32f
      paint.color =
        when (i % 5) {
          0 -> Color.argb(72, 46, 72, 36)   // deep soil/moss AO pocket
          1 -> Color.argb(76, 98, 138, 66)  // warm olive grass patch
          2 -> Color.argb(64, 54, 102, 56)  // cool forest clover
          3 -> Color.argb(54, 118, 146, 76) // sunlit grass crown
          else -> Color.argb(46, 74, 58, 42) // organic loam undertone
        }
      canvas.drawCircle(x, y, r, paint)
    }
    // Dense layered realistic grass blades with root-to-tip tonal contrast (drives micro-bump!)
    paint.strokeCap = Paint.Cap.ROUND
    for (i in 0 until 2600) {
      val x = rng.nextFloat() * size
      val y = rng.nextFloat() * size
      val h = 11f + rng.nextFloat() * 19f
      paint.strokeWidth = 2.0f + rng.nextFloat() * 2.2f
      paint.color =
        when (i % 4) {
          0 -> Color.argb(175, 138, 174, 88)
          1 -> Color.argb(165, 42, 74, 34)
          2 -> Color.argb(145, 76, 124, 56)
          else -> Color.argb(135, 106, 148, 70)
        }
      canvas.drawLine(x, y, x + (rng.nextFloat() - 0.5f) * 11f, y - h, paint)
    }
    // Delicate natural wildflower specks
    for (i in 0 until 110) {
      val x = rng.nextFloat() * size
      val y = rng.nextFloat() * size
      paint.color =
        when (i % 3) {
          0 -> Color.argb(215, 245, 236, 188)
          1 -> Color.argb(195, 184, 218, 245)
          else -> Color.argb(195, 242, 206, 218)
        }
      canvas.drawCircle(x, y, 2.8f, paint)
    }
    return bmp
  }

  private fun buildStoneBitmap(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    // Photoreal carved granite & slate base
    canvas.drawColor(Color.rgb(122, 128, 138))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val rng = Random(101)
    for (i in 0 until 1800) {
      val x = rng.nextFloat() * size
      val y = rng.nextFloat() * size
      val r = 2f + rng.nextFloat() * 16f
      paint.color =
        when (i % 4) {
          0 -> Color.argb(58, 168, 172, 180)
          1 -> Color.argb(54, 72, 76, 86)
          2 -> Color.argb(38, 198, 192, 178)
          else -> Color.argb(44, 58, 72, 60) // subtle weathered lichen
        }
      canvas.drawCircle(x, y, r, paint)
    }
    // Deep recessed ashlar mortar seams + crisp sunlit bevel edge
    paint.style = Paint.Style.STROKE
    val step = size / 4f
    for (pass in 0..1) {
      paint.strokeWidth = if (pass == 0) 8f else 3f
      paint.color = if (pass == 0) Color.argb(195, 38, 42, 52) else Color.argb(120, 196, 202, 212)
      val shift = if (pass == 0) 0f else 3.5f
      for (row in 0 until 4) {
        val y = row * step + shift
        canvas.drawLine(0f, y, size.toFloat(), y, paint)
        val offset = if (row % 2 == 0) 0f else step * 0.5f
        for (col in 0 until 5) {
          val x = (col * step + offset) % size + shift
          canvas.drawLine(x, y, x, y + step, paint)
        }
      }
    }
    return bmp
  }

  /**
   * 1024px AAA Architectural Travertine & Basalt Urban Sidewalk Pavers with realistic grit & dark grout seams.
   */
  private fun buildPlazaTileBitmap(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.rgb(92, 98, 110))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val step = size / 4f
    val rng = Random(512)
    for (r in 0 until 4) {
      for (c in 0 until 4) {
        val shade = 148 + rng.nextInt(32)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(shade, shade + 2, (shade + 6).coerceAtMost(255))
        canvas.drawRect(c * step + 4f, r * step + 4f, (c + 1) * step - 4f, (r + 1) * step - 4f, paint)
      }
    }
    // Fine stone grain speckles across pavers (feeds PBR derivative bump map)
    for (i in 0 until 1400) {
      val x = rng.nextFloat() * size
      val y = rng.nextFloat() * size
      paint.color = if (i % 2 == 0) Color.argb(38, 224, 228, 236) else Color.argb(42, 76, 82, 94)
      canvas.drawCircle(x, y, 1.5f + rng.nextFloat() * 4.5f, paint)
    }
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 6f
    paint.color = Color.rgb(68, 74, 86)
    for (i in 0..4) {
      val p = i * step
      canvas.drawLine(0f, p, size.toFloat(), p, paint)
      canvas.drawLine(p, 0f, p, size.toFloat(), paint)
    }
    return bmp
  }

  /**
   * 1024px AAA Photorealistic Weathered Urban Asphalt Street Texture with fine aggregate grit,
   * tire-wear wheel tracks, tar crack repairs, and crisp weathered lane markings.
   */
  private fun buildCityRoadBitmap(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val s = size.toFloat()
    canvas.drawColor(Color.rgb(38, 41, 48))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val rng = Random(777)

    // Subtle darker polished tire-wear lanes
    paint.color = Color.argb(55, 22, 24, 30)
    canvas.drawRect(s * 0.16f, 0f, s * 0.42f, s, paint)
    canvas.drawRect(s * 0.58f, 0f, s * 0.84f, s, paint)

    // Fine aggregate gravel grit & tar variation (sharp micro-normal response)
    for (i in 0 until 4200) {
      val x = rng.nextFloat() * s
      val y = rng.nextFloat() * s
      paint.color =
        when (i % 3) {
          0 -> Color.argb(46, 84, 88, 98)
          1 -> Color.argb(42, 20, 22, 28)
          else -> Color.argb(28, 122, 126, 134)
        }
      canvas.drawCircle(x, y, 1.2f + rng.nextFloat() * 2.8f, paint)
    }

    // Fine tar seam lines for weathered urban realism
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f
    paint.color = Color.argb(95, 16, 18, 22)
    canvas.drawLine(s * 0.18f, 0f, s * 0.21f, s, paint)
    canvas.drawLine(s * 0.78f, 0f, s * 0.74f, s, paint)
    paint.style = Paint.Style.FILL

    // Weathered double amber outer lines
    paint.color = Color.argb(225, 222, 174, 72)
    canvas.drawRect(s * 0.055f, 0f, s * 0.078f, s, paint)
    canvas.drawRect(s * 0.922f, 0f, s * 0.945f, s, paint)

    // Crisp white center lane dashes
    paint.color = Color.argb(230, 224, 228, 236)
    for (d in 0 until 4) {
      val y0 = d * (s * 0.25f) + s * 0.04f
      canvas.drawRoundRect(RectF(s * 0.486f, y0, s * 0.514f, y0 + s * 0.15f), 4f, 4f, paint)
    }
    return bmp
  }

  /**
   * 1024px AAA Architectural Concrete + Brushed Anodized Steel + Interior-Depth Curtain-Wall Glass.
   */
  private fun buildBuildingFacadeBitmap(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val s = size.toFloat()
    canvas.drawColor(Color.rgb(142, 148, 162))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val rng = Random(919)

    // Architectural concrete weathering grain
    for (i in 0 until 900) {
      paint.color = if (i % 2 == 0) Color.argb(24, 210, 216, 226) else Color.argb(26, 78, 84, 96)
      canvas.drawCircle(rng.nextFloat() * s, rng.nextFloat() * s, 3f + rng.nextFloat() * 14f, paint)
    }

    // Precision architectural cladding joint lines
    paint.color = Color.argb(115, 58, 64, 76)
    paint.strokeWidth = 3.5f
    for (c in 1..4) {
      canvas.drawLine(c * s * 0.20f, 0f, c * s * 0.20f, s * 0.72f, paint)
    }

    // 3 Upper Curtain-Wall Floors with Interior Parallax Office Ceiling Lights & Blinds
    for (row in 0 until 3) {
      val y = row * (s * 0.23f) + s * 0.04f
      paint.style = Paint.Style.FILL
      paint.color = Color.rgb(92, 98, 112)
      canvas.drawRect(0f, y, s, y + s * 0.028f, paint)

      for (col in 0 until 4) {
        val x = col * (s * 0.23f) + s * 0.055f
        val rect = RectF(x, y + s * 0.036f, x + s * 0.18f, y + s * 0.195f)
        val warmLit = (row + col) % 3 == 0
        paint.shader =
          LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            if (warmLit) intArrayOf(Color.rgb(218, 186, 128), Color.rgb(92, 74, 52))
            else intArrayOf(Color.rgb(54, 92, 134), Color.rgb(18, 28, 46)),
            null, Shader.TileMode.CLAMP,
          )
        canvas.drawRoundRect(rect, 6f, 6f, paint)
        paint.shader = null

        // Subtle interior fluorescent strip / venetian blind slats
        paint.color = if (warmLit) Color.argb(145, 255, 240, 200) else Color.argb(65, 160, 208, 248)
        canvas.drawRect(rect.left + 12f, rect.top + 10f, rect.right - 12f, rect.top + 18f, paint)
        paint.color = Color.argb(40, 12, 16, 26)
        for (slat in 1..3) {
          val sy = rect.top + slat * rect.height() * 0.22f
          canvas.drawRect(rect.left + 4f, sy, rect.right - 4f, sy + 4f, paint)
        }

        // Anodized dark titanium mullions
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 7f
        paint.color = Color.rgb(26, 30, 40)
        canvas.drawRoundRect(rect, 6f, 6f, paint)
        paint.strokeWidth = 3.5f
        canvas.drawLine(rect.centerX(), rect.top, rect.centerX(), rect.bottom, paint)
        paint.style = Paint.Style.FILL
      }
    }

    // Ground-level illuminated urban storefront interior
    val shopRect = RectF(s * 0.05f, s * 0.74f, s * 0.95f, s * 0.97f)
    paint.shader =
      LinearGradient(
        0f, shopRect.top, 0f, shopRect.bottom,
        intArrayOf(Color.rgb(244, 214, 158), Color.rgb(142, 96, 56)),
        null, Shader.TileMode.CLAMP,
      )
    canvas.drawRoundRect(shopRect, 8f, 8f, paint)
    paint.shader = null

    paint.color = Color.argb(110, 72, 46, 32)
    canvas.drawRect(shopRect.left + s * 0.04f, shopRect.top + s * 0.08f, shopRect.right - s * 0.04f, shopRect.top + s * 0.105f, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 10f
    paint.color = Color.rgb(24, 28, 38)
    canvas.drawRoundRect(shopRect, 8f, 8f, paint)
    paint.strokeWidth = 6f
    canvas.drawLine(s * 0.36f, shopRect.top, s * 0.36f, shopRect.bottom, paint)
    canvas.drawLine(s * 0.68f, shopRect.top, s * 0.68f, shopRect.bottom, paint)
    return bmp
  }

  /**
   * 512x1024 Stylized NTE cyber-fantasy glowing neon billboard texture.
   */
  private fun buildNeonSignBitmap(w: Int, h: Int, cyanTheme: Boolean): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.rgb(16, 22, 44))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val primary = if (cyanTheme) Color.rgb(52, 246, 226) else Color.rgb(255, 102, 178)
    val accent = if (cyanTheme) Color.rgb(255, 218, 104) else Color.rgb(92, 228, 255)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 18f
    paint.color = primary
    canvas.drawRoundRect(RectF(20f, 20f, w - 20f, h - 20f), 32f, 32f, paint)

    // Geometric cyber-anime star emblem + katakana-inspired bars
    paint.strokeWidth = 14f
    paint.color = accent
    canvas.drawCircle(w * 0.5f, h * 0.24f, w * 0.26f, paint)
    canvas.drawLine(w * 0.5f, h * 0.14f, w * 0.5f, h * 0.34f, paint)
    canvas.drawLine(w * 0.34f, h * 0.24f, w * 0.66f, h * 0.24f, paint)

    paint.style = Paint.Style.FILL
    paint.color = primary
    for (i in 0 until 4) {
      val y = h * (0.46f + i * 0.115f)
      val indent = if (i % 2 == 0) 0.18f else 0.24f
      canvas.drawRoundRect(RectF(w * indent, y, w * (1f - indent), y + h * 0.056f), 14f, 14f, paint)
    }
    return bmp
  }

  /**
   * Animated NTE Building Jumbotron LED Screen (widescreen or vertical tower display)
   * with cyber-pop artwork, audio/transit telemetry bars, and crisp dark metallic frame.
   */
  private fun buildBuildingScreenBitmap(w: Int, h: Int, widescreen: Boolean): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val wf = w.toFloat()
    val hf = h.toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Deep atmospheric cyber gradient backdrop
    paint.shader =
      LinearGradient(
        0f, 0f, wf, hf,
        if (widescreen) intArrayOf(Color.rgb(12, 24, 58), Color.rgb(18, 66, 108), Color.rgb(72, 26, 94))
        else intArrayOf(Color.rgb(42, 16, 68), Color.rgb(18, 42, 92), Color.rgb(12, 86, 112)),
        null, Shader.TileMode.CLAMP,
      )
    canvas.drawRect(0f, 0f, wf, hf, paint)
    paint.shader = null

    // Glowing holographic horizon grid & anime-style radiant sun/planet
    paint.color = Color.argb(90, 56, 240, 255)
    paint.strokeWidth = 3f
    for (i in 1..6) {
      val gy = hf * (0.52f + i * 0.075f)
      canvas.drawLine(0f, gy, wf, gy, paint)
    }

    val orbX = wf * (if (widescreen) 0.26f else 0.50f)
    val orbY = hf * (if (widescreen) 0.44f else 0.30f)
    val orbR = minOf(wf, hf) * 0.26f
    paint.shader =
      RadialGradient(
        orbX, orbY, orbR * 1.4f,
        intArrayOf(
          if (widescreen) Color.rgb(110, 250, 255) else Color.rgb(255, 130, 210),
          if (widescreen) Color.argb(140, 54, 178, 255) else Color.argb(140, 140, 88, 255),
          Color.TRANSPARENT,
        ),
        null, Shader.TileMode.CLAMP,
      )
    canvas.drawCircle(orbX, orbY, orbR * 1.4f, paint)
    paint.shader = null

    // Crisp geometric anime star crest inside orb
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 8f
    paint.color = Color.rgb(255, 242, 160)
    canvas.drawCircle(orbX, orbY, orbR * 0.68f, paint)
    paint.style = Paint.Style.FILL

    // Graphic typography bars + equalizer visualizer spectrum
    val eqCount = if (widescreen) 14 else 9
    val eqStartX = if (widescreen) wf * 0.48f else wf * 0.14f
    val eqEndX = wf * 0.90f
    val eqBaseY = if (widescreen) hf * 0.78f else hf * 0.84f
    val barW = (eqEndX - eqStartX) / eqCount * 0.65f
    val rng = Random(if (widescreen) 81 else 92)
    for (i in 0 until eqCount) {
      val bh = (0.14f + rng.nextFloat() * 0.34f) * hf
      val bx = eqStartX + i * ((eqEndX - eqStartX) / eqCount)
      paint.color =
        if (i % 3 == 0) Color.rgb(255, 108, 186)
        else if (i % 2 == 0) Color.rgb(72, 248, 232)
        else Color.rgb(255, 224, 118)
      canvas.drawRoundRect(RectF(bx, eqBaseY - bh, bx + barW, eqBaseY), 6f, 6f, paint)
    }

    // Crisp headline UI text rendered onto LED canvas
    paint.color = Color.WHITE
    paint.textSize = minOf(wf, hf) * (if (widescreen) 0.115f else 0.11f)
    paint.isFakeBoldText = true
    if (widescreen) {
      canvas.drawText("AETHER METRO // 07", wf * 0.46f, hf * 0.32f, paint)
      paint.color = Color.rgb(90, 248, 255)
      paint.textSize = hf * 0.072f
      canvas.drawText("SKY-RAIL EXPRESS • LIVE", wf * 0.46f, hf * 0.43f, paint)
    } else {
      canvas.drawText("STARLIGHT", wf * 0.14f, hf * 0.54f, paint)
      paint.color = Color.rgb(255, 146, 214)
      canvas.drawText("STUDIO", wf * 0.22f, hf * 0.62f, paint)
    }

    // Brushed titanium LED monitor bezel
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 18f
    paint.color = Color.rgb(20, 24, 34)
    canvas.drawRect(8f, 8f, wf - 8f, hf - 8f, paint)
    paint.strokeWidth = 4f
    paint.color = Color.argb(180, 120, 245, 255)
    canvas.drawRect(22f, 22f, wf - 22f, hf - 22f, paint)
    return bmp
  }

  /**
   * Vertical Streetlamp & Skybridge NTE Festival Pole Banner texture.
   */
  private fun buildStreetBannerBitmap(w: Int, h: Int, cyanTheme: Boolean): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val wf = w.toFloat()
    val hf = h.toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader =
      LinearGradient(
        0f, 0f, 0f, hf,
        if (cyanTheme) intArrayOf(Color.rgb(16, 46, 98), Color.rgb(22, 118, 162), Color.rgb(16, 36, 76))
        else intArrayOf(Color.rgb(86, 22, 74), Color.rgb(174, 48, 118), Color.rgb(42, 18, 62)),
        null, Shader.TileMode.CLAMP,
      )
    canvas.drawRect(0f, 0f, wf, hf, paint)
    paint.shader = null

    val accent = if (cyanTheme) Color.rgb(86, 250, 236) else Color.rgb(255, 214, 108)
    paint.color = accent
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 10f
    canvas.drawRect(14f, 14f, wf - 14f, hf - 14f, paint)

    // Geometric diamond NTE district emblem + chevron stripes
    paint.style = Paint.Style.FILL
    val emblem = Path().apply {
      moveTo(wf * 0.5f, hf * 0.14f)
      lineTo(wf * 0.78f, hf * 0.30f)
      lineTo(wf * 0.5f, hf * 0.46f)
      lineTo(wf * 0.22f, hf * 0.30f)
      close()
    }
    canvas.drawPath(emblem, paint)
    paint.color = Color.WHITE
    paint.textSize = wf * 0.19f
    paint.isFakeBoldText = true
    canvas.drawText(if (cyanTheme) "NTE" else "AIR", wf * 0.26f, hf * 0.62f, paint)
    paint.color = accent
    for (i in 0..2) {
      val y = hf * (0.72f + i * 0.065f)
      canvas.drawRect(wf * 0.18f, y, wf * 0.82f, y + hf * 0.028f, paint)
    }
    return bmp
  }

  private fun buildRuneBitmap(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.rgb(24, 38, 66))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.style = Paint.Style.STROKE
    val cx = size / 2f
    val cy = size / 2f

    paint.color = Color.rgb(72, 245, 230)
    paint.strokeWidth = 14f
    canvas.drawCircle(cx, cy, size * 0.42f, paint)
    paint.strokeWidth = 8f
    canvas.drawCircle(cx, cy, size * 0.30f, paint)
    paint.color = Color.rgb(255, 220, 118)
    canvas.drawCircle(cx, cy, size * 0.17f, paint)

    paint.color = Color.rgb(135, 250, 242)
    paint.strokeWidth = 8f
    for (i in 0 until 12) {
      val a = (i * PI / 6.0).toFloat()
      val r1 = if (i % 2 == 0) size * 0.14f else size * 0.24f
      val r2 = size * 0.44f
      canvas.drawLine(cx + cos(a) * r1, cy + sin(a) * r1, cx + cos(a) * r2, cy + sin(a) * r2, paint)
    }
    return bmp
  }

  private fun buildBarkBitmap(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.rgb(78, 56, 44))
    val rng = Random(31)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    for (i in 0 until 380) {
      val x = rng.nextFloat() * size
      paint.strokeWidth = 2.5f + rng.nextFloat() * 5f
      paint.color =
        when (i % 3) {
          0 -> Color.argb(170, 38, 24, 18) // deep bark fissure
          1 -> Color.argb(125, 126, 98, 78) // dry ridge highlight
          else -> Color.argb(72, 64, 78, 48) // hint of trunk moss
        }
      canvas.drawLine(x, 0f, x + (rng.nextFloat() - 0.5f) * 22f, size.toFloat(), paint)
    }
    return bmp
  }

  private fun buildClothBitmap(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader =
      LinearGradient(
        0f, 0f, 0f, size.toFloat(),
        intArrayOf(Color.rgb(250, 252, 255), Color.rgb(232, 238, 248), Color.rgb(206, 214, 234)),
        floatArrayOf(0f, 0.72f, 1f),
        Shader.TileMode.CLAMP,
      )
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    paint.shader = null

    paint.strokeWidth = 3.5f
    for (i in 0 until size step 40) {
      paint.color = Color.argb(38, 150, 168, 204)
      canvas.drawLine(i.toFloat(), 0f, i.toFloat(), size.toFloat(), paint)
    }
    paint.color = Color.rgb(252, 214, 114)
    canvas.drawRect(0f, size * 0.86f, size.toFloat(), size * 0.93f, paint)
    paint.color = Color.rgb(185, 140, 56)
    canvas.drawRect(0f, size * 0.84f, size.toFloat(), size * 0.86f, paint)
    return bmp
  }

  private fun buildObsidianArenaBitmap(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.rgb(28, 34, 58))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 5f
    paint.color = Color.argb(140, 88, 152, 238)
    val step = size / 4f
    for (i in 0..4) {
      val p = i * step
      canvas.drawLine(p, 0f, p, size.toFloat(), paint)
      canvas.drawLine(0f, p, size.toFloat(), p, paint)
    }
    paint.color = Color.argb(120, 125, 228, 255)
    canvas.drawCircle(size / 2f, size / 2f, size * 0.34f, paint)
    paint.color = Color.argb(95, 255, 210, 110)
    canvas.drawCircle(size / 2f, size / 2f, size * 0.18f, paint)
    return bmp
  }
}
