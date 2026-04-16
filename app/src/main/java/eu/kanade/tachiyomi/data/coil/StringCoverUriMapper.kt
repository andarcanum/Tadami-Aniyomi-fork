package eu.kanade.tachiyomi.data.coil

import coil3.Uri
import coil3.map.Mapper
import coil3.request.Options
import coil3.toUri

class StringCoverUriMapper : Mapper<String, Uri> {
    override fun map(data: String, options: Options): Uri {
        return data.trim().toUri()
    }
}
