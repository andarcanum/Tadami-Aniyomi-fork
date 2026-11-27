package com.davemorrissey.labs.subscaleview.provider

import java.io.IOException
import java.io.InputStream

/**
 * Provider from an already open input stream.
 *
 * Please keep in mind this provider is not reusable.
 */
class OpenStreamProvider(private val stream: InputStream) : InputProvider {
    @Throws(IOException::class)
    override fun openStream(): InputStream {
        return stream
    }
}
