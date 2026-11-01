package com.kyro.game.space.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.collection.LruCache

object AppIconCache {
    // Cache up to 128 app icon bitmaps in memory to eliminate scroll lag & GC pressure
    private val memoryCache = LruCache<String, Bitmap>(128)

    fun getIcon(context: Context, packageName: String): Bitmap? {
        val cached = memoryCache.get(packageName)
        if (cached != null && !cached.isRecycled) {
            return cached
        }
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = drawableToBitmap(drawable)
            memoryCache.put(packageName, bitmap)
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            return drawable.bitmap
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceAtMost(192) else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceAtMost(192) else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
