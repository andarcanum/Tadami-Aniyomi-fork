package tachiyomi.source.local.io.novel

import com.hippo.unifile.UniFile

expect class LocalNovelSourceFileSystem {

    fun getBaseDirectory(): UniFile?

    fun getFilesInBaseDirectory(): List<UniFile>

    fun getNovelDirectory(name: String): UniFile?

    fun getFilesInNovelDirectory(name: String): List<UniFile>
}
