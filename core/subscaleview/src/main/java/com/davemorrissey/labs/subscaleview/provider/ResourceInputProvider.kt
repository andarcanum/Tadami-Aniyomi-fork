package com.davemorrissey.labs.subscaleview.provider

import android.content.Context
import android.content.res.Resources
import java.io.InputStream

class ResourceInputProvider(
    context: Context,
    private val resource: Int,
) : InputProvider {

    private val resources: Resources = context.resources

    override fun openStream(): InputStream {
        return resources.openRawResource(resource)
    }
}
