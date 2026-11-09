package com.davemorrissey.labs.subscaleview.provider

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.IOException
import java.io.InputStream

class UriInputProvider(
    context: Context,
    private val uri: Uri,
) : InputProvider {

    private val resolver: ContentResolver = context.contentResolver

    @Throws(IOException::class)
    override fun openStream(): InputStream? {
        return resolver.openInputStream(uri)
    }
}
