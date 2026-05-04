package eu.kanade.tachiyomi.animesource.model

import eu.kanade.tachiyomi.animesource.model.SerializableVideo.Companion.serialize
import eu.kanade.tachiyomi.animesource.model.SerializableVideo.Companion.toVideoList
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode

@Execution(ExecutionMode.CONCURRENT)
class VideoTest {

    @Test
    fun `serialize preserves legacy page url for unresolved videos`() {
        val original = listOf(
            Video(
                url = "https://example.com/watch",
                quality = "1080p",
                videoUrl = "null",
            ),
        )

        val restored = original.serialize().toVideoList()

        restored.single().url shouldBe "https://example.com/watch"
        restored.single().pageUrl shouldBe "https://example.com/watch"
        restored.single().videoUrl shouldBe "null"
        restored.single().videoTitle shouldBe "1080p"
    }

    @Test
    fun `legacy default constructor signature stays available`() {
        val expectedParameterTypes = listOf(
            "java.lang.String",
            "java.lang.String",
            "java.lang.Integer",
            "java.lang.Integer",
            "okhttp3.Headers",
            "boolean",
            "java.util.List",
            "java.util.List",
            "java.util.List",
            "java.util.List",
            "java.util.List",
            "java.util.List",
            "java.lang.String",
            "boolean",
            "int",
            "kotlin.jvm.internal.DefaultConstructorMarker",
        )

        Video::class.java.declaredConstructors.any { constructor ->
            constructor.parameterTypes.map { it.name } == expectedParameterTypes
        } shouldBe true
    }
}
