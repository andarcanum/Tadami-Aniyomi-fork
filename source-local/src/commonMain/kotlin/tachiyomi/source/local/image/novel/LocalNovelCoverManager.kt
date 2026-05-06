package tachiyomi.source.local.image.novel

import com.hippo.unifile.UniFile
import eu.kanade.tachiyomi.novelsource.model.SNovel
import java.io.InputStream

expect class LocalNovelCoverManager {

    fun find(novelUrl: String): UniFile?

    fun update(novel: SNovel, inputStream: InputStream): UniFile?
}
