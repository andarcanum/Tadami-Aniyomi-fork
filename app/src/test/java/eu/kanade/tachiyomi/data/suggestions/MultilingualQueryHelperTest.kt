package eu.kanade.tachiyomi.data.suggestions

import eu.kanade.tachiyomi.network.NetworkHelper
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.fullType
import uy.kohesive.injekt.api.get

class MultilingualQueryHelperTest {

    @BeforeEach
    fun setUp() {
        runCatching { Injekt.get<NetworkHelper>() }.onFailure {
            val mockClient = mockk<OkHttpClient>(relaxed = true)
            val mockNetwork = mockk<NetworkHelper>(relaxed = true)
            every { mockNetwork.client } returns mockClient
            Injekt.addSingleton(fullType<NetworkHelper>(), mockNetwork)
        }
        runCatching {
            Injekt.get<eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService>()
        }.onFailure {
            val service = eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService(
                client = Injekt.get<NetworkHelper>().client,
            )
            Injekt.addSingleton(
                fullType<eu.kanade.tachiyomi.ui.reader.novel.translation.GoogleTranslationService>(),
                service,
            )
        }
    }

    @Test
    fun `containsCyrillic should detect Cyrillic characters`() {
        assertTrue(MultilingualQueryHelper.containsCyrillic("Цукаса Фусими"))
        assertTrue(MultilingualQueryHelper.containsCyrillic("Атака Титанов"))
        assertFalse(MultilingualQueryHelper.containsCyrillic("Tsukasa Fushimi"))
    }

    @Test
    fun `containsLatin should detect Latin characters`() {
        assertTrue(MultilingualQueryHelper.containsLatin("Tsukasa Fushimi"))
        assertTrue(MultilingualQueryHelper.containsLatin("Attack on Titan"))
        assertFalse(MultilingualQueryHelper.containsLatin("Цукаса Фусими"))
    }

    @Test
    fun `getGenreTranslations should translate bidirectionally`() {
        // English to Russian
        val translations1 = MultilingualQueryHelper.getGenreTranslations("action")
        assertTrue(translations1.contains("боевик"))
        assertTrue(translations1.contains("экшен"))

        // Russian to English
        val translations2 = MultilingualQueryHelper.getGenreTranslations("боевые искусства")
        assertTrue(translations2.contains("martial arts"))

        val translations3 = MultilingualQueryHelper.getGenreTranslations("Приключения")
        assertTrue(translations3.contains("adventure"))

        val translations4 = MultilingualQueryHelper.getGenreTranslations("milf")
        assertTrue(translations4.contains("милфы"))
    }

    @Test
    fun `getMultilingualVariants returns translated queries when translation succeeds`() = runTest {
        val mockNetwork = Injekt.get<NetworkHelper>()
        val mockClient = mockNetwork.client
        val mockCall = mockk<Call>(relaxed = true)

        // Mock Google translate JSON response: [[["Sword Art Online","Мастер Меча Онлайн",null,null,1]]]
        val responseBodyString = "[[[\"Sword Art Online\",\"Мастер Меча Онлайн\",null,null,1]]]"
        val mockResponse = Response.Builder()
            .request(mockk<okhttp3.Request>(relaxed = true))
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(responseBodyString.toResponseBody("application/json".toMediaType()))
            .build()

        every { mockClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } returns mockResponse

        val variants = MultilingualQueryHelper.getMultilingualVariants("Мастер Меча Онлайн")
        assertTrue(variants.contains("Мастер Меча Онлайн"))
        assertTrue(variants.contains("Sword Art Online"))
    }

    @Test
    fun `getMultilingualVariants gracefully returns original query on translation failure`() = runTest {
        val mockNetwork = Injekt.get<NetworkHelper>()
        val mockClient = mockNetwork.client
        val mockCall = mockk<Call>(relaxed = true)

        every { mockClient.newCall(any()) } returns mockCall
        every { mockCall.execute() } throws RuntimeException("Network Error")

        val variants = MultilingualQueryHelper.getMultilingualVariants("Solo Leveling")
        assertEquals(1, variants.size)
        assertEquals("Solo Leveling", variants.first())
    }
}
