package com.kyro.game.space.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AppIconCache {
    // Cache up to 160 app icon bitmaps & Compose ImageBitmaps in memory to eliminate scroll lag & GC pressure
    private val memoryCache = LruCache<String, Bitmap>(160)
    private val imageBitmapCache = LruCache<String, ImageBitmap>(160)
    private val preloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Non-blocking instant memory cache lookup for Compose UI thread (0ms, never touches disk).
     */
    fun getCachedImageBitmap(packageName: String): ImageBitmap? {
        if (packageName.isBlank()) return null
        val cachedImg = imageBitmapCache.get(packageName)
        if (cachedImg != null) return cachedImg
        val cachedBmp = memoryCache.get(packageName)
        if (cachedBmp != null && !cachedBmp.isRecycled) {
            val img = cachedBmp.asImageBitmap()
            imageBitmapCache.put(packageName, img)
            return img
        }
        return null
    }

    /**
     * Loads and caches the app icon on Dispatchers.IO so scrolling never blocks the Main UI thread.
     */
    suspend fun getOrLoadImageBitmap(context: Context, packageName: String): ImageBitmap? {
        if (packageName.isBlank()) return null
        getCachedImageBitmap(packageName)?.let { return it }
        return withContext(Dispatchers.IO) {
            getCachedImageBitmap(packageName) ?: run {
                val bmp = getIcon(context.applicationContext, packageName)
                bmp?.let {
                    val img = it.asImageBitmap()
                    imageBitmapCache.put(packageName, img)
                    img
                }
            }
        }
    }

    /**
     * Pre-warms icons in the background on Dispatchers.IO for smooth LazyColumn scrolling.
     */
    fun preloadIconsAsync(context: Context, packageNames: List<String>) {
        val appCtx = context.applicationContext
        preloadScope.launch {
            for (pkg in packageNames) {
                if (pkg.isNotBlank() && imageBitmapCache.get(pkg) == null) {
                    try {
                        val bmp = getIcon(appCtx, pkg)
                        if (bmp != null) {
                            imageBitmapCache.put(pkg, bmp.asImageBitmap())
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    fun getIcon(context: Context, packageName: String): Bitmap? {
        if (packageName.isBlank()) return null
        val cached = memoryCache.get(packageName)
        if (cached != null && !cached.isRecycled) {
            return cached
        }
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val bitmap = drawableToBitmap(drawable)
            memoryCache.put(packageName, bitmap)
            imageBitmapCache.put(packageName, bitmap.asImageBitmap())
            bitmap
        } catch (_: Throwable) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            val src = drawable.bitmap
            if (src.width <= 128 && src.height <= 128) {
                return src
            }
            return Bitmap.createScaledBitmap(src, 112, 112, true)
        }
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth.coerceAtMost(112) else 96
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight.coerceAtMost(112) else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
