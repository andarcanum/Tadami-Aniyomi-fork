package com.davemorrissey.labs.subscaleview.decoder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import com.davemorrissey.labs.subscaleview.provider.InputProvider
import tachiyomi.core.common.util.image.NativeImageDecoder

/** Mihon region-decoder boundary, sharing resource/crop policy with Coil. */
class Decoder(private val cropBorders: Boolean) : ImageRegionDecoder {
    @Volatile private var decoder: NativeImageDecoder? = null

    override fun init(context: Context, provider: InputProvider): Point {
        recycle()
        val opened = NativeImageDecoder.open(checkNotNull(provider.openStream()), cropBorders)
        decoder = opened
        return Point(opened.width, opened.height)
    }

    override fun decodeRegion(sRect: Rect, sampleSize: Int): Bitmap =
        checkNotNull(decoder) { "Decoder not initialized or recycled" }.decode(sRect, sampleSize)

    override fun isReady(): Boolean = decoder != null

    override fun recycle() {
        decoder?.close()
        decoder = null
    }
}
