package com.zain.screenlens

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtils {

    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
    }

    /** Downscale so the longest side is <= maxSide, then JPEG + base64 (small = fast upload). */
    fun toJpegBase64(bitmap: Bitmap, maxSide: Int = 1024, quality: Int = 80): String {
        val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** 64-bit average hash, used to skip frames that haven't changed in Auto mode. */
    fun averageHash(bitmap: Bitmap): Long {
        val small = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        val px = IntArray(64)
        small.getPixels(px, 0, 8, 0, 0, 8, 8)
        val gray = px.map { (Color.red(it) * 299 + Color.green(it) * 587 + Color.blue(it) * 114) / 1000 }
        val avg = gray.average()
        var hash = 0L
        gray.forEachIndexed { i, g -> if (g > avg) hash = hash or (1L shl i) }
        return hash
    }
}
