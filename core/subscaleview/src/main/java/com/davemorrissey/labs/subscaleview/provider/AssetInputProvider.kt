package com.davemorrissey.labs.subscaleview.provider

import android.content.Context
import android.content.res.AssetManager
import java.io.IOException
import java.io.InputStream

class AssetInputProvider(
    context: Context,
    private val assetName: String,
) : InputProvider {

    private val assets: AssetManager = context.assets

    @Throws(IOException::class)
    override fun openStream(): InputStream {
        return assets.open(assetName)
    }
}
