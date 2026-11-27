package com.davemorrissey.labs.subscaleview.provider

import java.io.IOException
import java.io.InputStream

/**
 * Reusable [InputProvider] backed by a caller-supplied stream factory. Each decode pass
 * (tiles init, smart-fit bounds, retry) opens a fresh [InputStream] without buffering the
 * entire compressed image in memory.
 */
class StreamInputProvider(
    private val streamOpener: () -> InputStream?,
) : InputProvider {
    @Throws(IOException::class)
    override fun openStream(): InputStream? = streamOpener()
}
