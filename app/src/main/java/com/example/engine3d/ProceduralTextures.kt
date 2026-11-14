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
    val rune: Int,
    val foliage: Int,
    val heroCloth: Int,
    val arenaObsidian: Int,
    val animeFaceAero: Int,
    val animeFacePyro: Int,
    val animeFaceCryo: Int,
    val sakuraFoliage: Int,
    val bark: Int,
    val white: Int,
  )

  fun createAll(): TextureSet {
    return TextureSet(
      grass = uploadBitmap(buildGrassBitmap(512)),
      stone = uploadBitmap(buildStoneBitmap(512)),
      rune = uploadBitmap(buildRuneBitmap(512)),
      foliage = uploadBitmap(buildFoliageBitmap(512, sakura = false)),
      heroCloth = uploadBitmap(buildClothBitmap(512)),
      arenaObsidian = uploadBitmap(buildObsidianArenaBitmap(512)),
      animeFaceAero = uploadBitmap(buildAnimeFaceBitmap(512, Color.rgb(42, 222, 200), Color.rgb(16, 108, 130))),
      animeFacePyro = uploadBitmap(buildAnimeFaceBitmap(512, Color.rgb(255, 118, 62), Color.rgb(165, 36, 26))),
      animeFaceCryo = uploadBitmap(buildAnimeFaceBitmap(512, Color.rgb(142, 186, 255), Color.rgb(66, 94, 198))),
      sakuraFoliage = uploadBitmap(buildFoliageBitmap(512, sakura = true)),
      bark = uploadBitmap(buildBarkBitmap(256)),
      white = uploadBitmap(buildSolidWhiteBitmap()),
    )
  }

  private fun uploadBitmap(bitmap: Bitmap): Int {
    val texIds = IntArray(1)
    GLES30.glGenTextures(1, texIds, 0)
    val id = texIds[0]
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)
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

  private fun buildAnimeFaceBitmap(size: Int, irisLight: Int, irisDark: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val s = size.toFloat()
    // Warm luminous porcelain anime skin base
    canvas.drawColor(Color.rgb(255, 232, 218))

    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Subtle cheek blush (anime soft airbrush)
    for (cx in listOf(s * 0.34f, s * 0.66f)) {
      paint.shader =
        RadialGradient(
          cx, s * 0.59f, s * 0.085f,
          intArrayOf(Color.argb(95, 255, 130, 145), Color.argb(0, 255, 130, 145)),
          null,
          Shader.TileMode.CLAMP,
        )
      canvas.drawCircle(cx, s * 0.59f, s * 0.085f, paint)
    }
    paint.shader = null

    // Draw expressive anime eyes centered on front UV (u=0.5, v=0.52)
    fun drawEye(cx: Float, flip: Float) {
      val cy = s * 0.50f
      val ew = s * 0.072f
      val eh = s * 0.068f

      // Sclera (soft shadowed white)
      paint.style = Paint.Style.FILL
      paint.color = Color.rgb(248, 250, 255)
      canvas.drawOval(RectF(cx - ew, cy - eh * 0.78f, cx + ew, cy + eh * 0.78f), paint)

      // Upper sclera cel shadow
      paint.color = Color.argb(55, 130, 145, 195)
      canvas.drawOval(RectF(cx - ew, cy - eh * 0.78f, cx + ew, cy - eh * 0.1f), paint)

      // Iris gradient
      val irisR = eh * 0.68f
      paint.shader =
        LinearGradient(
          cx, cy - irisR, cx, cy + irisR,
          intArrayOf(Color.rgb(24, 28, 56), irisDark, irisLight),
          floatArrayOf(0f, 0.42f, 1f),
          Shader.TileMode.CLAMP,
        )
      canvas.drawOval(RectF(cx - irisR * 0.85f, cy - irisR, cx + irisR * 0.85f, cy + irisR), paint)
      paint.shader = null

      // Dark pupil
      paint.color = Color.rgb(18, 20, 42)
      canvas.drawOval(RectF(cx - irisR * 0.34f, cy - irisR * 0.42f, cx + irisR * 0.34f, cy + irisR * 0.42f), paint)

      // Luminous anime eye catchlights
      paint.color = Color.WHITE
      canvas.drawCircle(cx - flip * irisR * 0.32f, cy - irisR * 0.35f, irisR * 0.26f, paint)
      canvas.drawCircle(cx + flip * irisR * 0.25f, cy + irisR * 0.30f, irisR * 0.13f, paint)

      // Crisp upper eyelash arch
      paint.style = Paint.Style.STROKE
      paint.color = Color.rgb(34, 28, 46)
      paint.strokeWidth = s * 0.014f
      paint.strokeCap = Paint.Cap.ROUND
      val lash = Path().apply {
        moveTo(cx - ew * 1.08f, cy - eh * 0.25f)
        quadTo(cx, cy - eh * 1.05f, cx + ew * 1.08f, cy - eh * 0.22f)
      }
      canvas.drawPath(lash, paint)

      // Eyebrow
      paint.strokeWidth = s * 0.007f
      paint.color = Color.rgb(72, 62, 86)
      val brow = Path().apply {
        moveTo(cx - ew * 0.9f, cy - eh * 1.25f)
        quadTo(cx, cy - eh * 1.42f, cx + ew * 0.9f, cy - eh * 1.18f)
      }
      canvas.drawPath(brow, paint)
    }

    drawEye(s * 0.405f, 1f)
    drawEye(s * 0.595f, -1f)

    // Delicate anime nose dot + confident smile
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(120, 195, 130, 120)
    canvas.drawCircle(s * 0.50f, s * 0.595f, s * 0.005f, paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = s * 0.006f
    paint.color = Color.rgb(175, 95, 95)
    val mouth = Path().apply {
      moveTo(s * 0.465f, s * 0.65f)
      quadTo(s * 0.50f, s * 0.672f, s * 0.535f, s * 0.65f)
    }
    canvas.drawPath(mouth, paint)
    return bmp
  }

  private fun buildGrassBitmap(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.rgb(178, 218, 146))
    val rng = Random(42)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    for (i in 0 until 420) {
      val x = rng.nextFloat() * size
      val y = rng.nextFloat() * size
      val r = 10f + rng.nextFloat() * 28f
      paint.color =
        when (i % 4) {
          0 -> Color.argb(85, 122, 188, 102)
          1 -> Color.argb(72, 214, 238, 158)
          2 -> Color.argb(65, 98, 168, 118)
          else -> Color.argb(55, 155, 212, 128)
        }
      canvas.drawCircle(x, y, r, paint)
    }
    paint.strokeWidth = 2.6f
    paint.strokeCap = Paint.Cap.ROUND
    for (i in 0 until 950) {
      val x = rng.nextFloat() * size
      val y = rng.nextFloat() * size
      val h = 7f + rng.nextFloat() * 11f
      paint.color =
        if (i % 4 == 0) Color.argb(160, 236, 250, 186)
        else if (i % 4 == 1) Color.argb(145, 86, 154, 82)
        else Color.argb(115, 115, 182, 98)
      canvas.drawLine(x, y, x + (rng.nextFloat() - 0.5f) * 7f, y - h, paint)
    }
    for (i in 0 until 85) {
      val x = rng.nextFloat() * size
      val y = rng.nextFloat() * size
      paint.color =
        when (i % 3) {
          0 -> Color.rgb(255, 248, 200)
          1 -> Color.rgb(190, 238, 255)
          else -> Color.rgb(255, 208, 228)
        }
      canvas.drawCircle(x, y, 2.8f, paint)
    }
    return bmp
  }

  private fun buildStoneBitmap(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.rgb(214, 222, 232))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val rng = Random(101)
    // Painterly marble blocks
    for (i in 0 until 320) {
      val x = rng.nextFloat() * size
      val y = rng.nextFloat() * size
      paint.color =
        if (i % 5 == 0) Color.argb(50, 250, 244, 225)
        else Color.argb(42, 148, 164, 188)
      canvas.drawCircle(x, y, 6f + rng.nextFloat() * 20f, paint)
    }
    // Ashlar seams with subtle moss/gold filigree
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3.5f
    paint.color = Color.argb(150, 108, 122, 146)
    val step = size / 4f
    for (row in 0 until 4) {
      val y = row * step
      canvas.drawLine(0f, y, size.toFloat(), y, paint)
      val offset = if (row % 2 == 0) 0f else step * 0.5f
      for (col in 0 until 5) {
        val x = (col * step + offset) % size
        canvas.drawLine(x, y, x, y + step, paint)
      }
    }
    paint.strokeWidth = 2f
    paint.color = Color.argb(115, 226, 198, 118)
    canvas.drawRect(8f, 8f, size - 8f, size - 8f, paint)
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
    paint.strokeWidth = 8f
    canvas.drawCircle(cx, cy, size * 0.42f, paint)
    paint.strokeWidth = 4f
    canvas.drawCircle(cx, cy, size * 0.30f, paint)
    paint.color = Color.rgb(255, 220, 118)
    canvas.drawCircle(cx, cy, size * 0.17f, paint)

    paint.color = Color.rgb(135, 250, 242)
    paint.strokeWidth = 4.5f
    for (i in 0 until 12) {
      val a = (i * PI / 6.0).toFloat()
      val r1 = if (i % 2 == 0) size * 0.14f else size * 0.24f
      val r2 = size * 0.44f
      canvas.drawLine(cx + cos(a) * r1, cy + sin(a) * r1, cx + cos(a) * r2, cy + sin(a) * r2, paint)
    }
    return bmp
  }

  private fun buildFoliageBitmap(size: Int, sakura: Boolean): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(if (sakura) Color.rgb(255, 220, 234) else Color.rgb(206, 232, 198))
    val rng = Random(if (sakura) 88 else 77)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    for (i in 0 until 620) {
      val x = rng.nextFloat() * size
      val y = rng.nextFloat() * size
      val r = 7f + rng.nextFloat() * 19f
      paint.color =
        if (sakura) {
          when (i % 4) {
            0 -> Color.argb(145, 255, 245, 250)
            1 -> Color.argb(135, 255, 178, 206)
            2 -> Color.argb(120, 242, 138, 176)
            else -> Color.argb(110, 255, 206, 224)
          }
        } else {
          when (i % 4) {
            0 -> Color.argb(135, 235, 250, 212)
            1 -> Color.argb(120, 142, 204, 146)
            2 -> Color.argb(110, 92, 162, 112)
            else -> Color.argb(100, 172, 224, 160)
          }
        }
      canvas.drawCircle(x, y, r, paint)
    }
    return bmp
  }

  private fun buildBarkBitmap(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.rgb(122, 88, 72))
    val rng = Random(31)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.strokeWidth = 3f
    for (i in 0 until 140) {
      val x = rng.nextFloat() * size
      paint.color = if (i % 2 == 0) Color.argb(110, 82, 56, 48) else Color.argb(80, 162, 122, 102)
      canvas.drawLine(x, 0f, x + (rng.nextFloat() - 0.5f) * 14f, size.toFloat(), paint)
    }
    return bmp
  }

  private fun buildClothBitmap(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    // Subtle vertical cel-fabric shading gradient
    paint.shader =
      LinearGradient(
        0f, 0f, 0f, size.toFloat(),
        intArrayOf(Color.rgb(248, 250, 255), Color.rgb(232, 238, 248), Color.rgb(208, 216, 234)),
        floatArrayOf(0f, 0.72f, 1f),
        Shader.TileMode.CLAMP,
      )
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
    paint.shader = null

    // Woven silk micro-sheen + cel fold lines
    paint.strokeWidth = 2.2f
    for (i in 0 until size step 24) {
      paint.color = Color.argb(36, 155, 172, 204)
      canvas.drawLine(i.toFloat(), 0f, i.toFloat(), size.toFloat(), paint)
    }
    // Ornamental gold trim band near hem (UV v ~ 0.86..0.94)
    paint.color = Color.rgb(252, 214, 114)
    canvas.drawRect(0f, size * 0.85f, size.toFloat(), size * 0.93f, paint)
    paint.color = Color.rgb(185, 140, 56)
    canvas.drawRect(0f, size * 0.83f, size.toFloat(), size * 0.85f, paint)
    return bmp
  }

  private fun buildObsidianArenaBitmap(size: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(Color.rgb(28, 34, 58))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3f
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
