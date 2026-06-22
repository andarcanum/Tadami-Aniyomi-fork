package eu.kanade.presentation.more.settings.screen.player

import io.kotest.matchers.shouldBe
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlayerSettingsTorrentValidationTest {

    @Test
    fun `port validation accepts valid range`() {
        PlayerSettingsTorrentScreen.isValidPort("0") shouldBe true
        PlayerSettingsTorrentScreen.isValidPort("1") shouldBe true
        PlayerSettingsTorrentScreen.isValidPort("8090") shouldBe true
        PlayerSettingsTorrentScreen.isValidPort("65535") shouldBe true
    }

    @Test
    fun `port validation rejects invalid values`() {
        PlayerSettingsTorrentScreen.isValidPort("") shouldBe false
        PlayerSettingsTorrentScreen.isValidPort("-1") shouldBe false
        PlayerSettingsTorrentScreen.isValidPort("65536") shouldBe false
        PlayerSettingsTorrentScreen.isValidPort("abc") shouldBe false
    }

    @Test
    fun `proxy validation accepts blank and supported protocols`() {
        PlayerSettingsTorrentScreen.isValidProxyUrl("") shouldBe true
        PlayerSettingsTorrentScreen.isValidProxyUrl("http://proxy.example:8080") shouldBe true
        PlayerSettingsTorrentScreen.isValidProxyUrl("https://proxy.example:8080") shouldBe true
        PlayerSettingsTorrentScreen.isValidProxyUrl("socks4://proxy.example:1080") shouldBe true
        PlayerSettingsTorrentScreen.isValidProxyUrl("socks4a://proxy.example:1080") shouldBe true
        PlayerSettingsTorrentScreen.isValidProxyUrl("socks5://proxy.example:1080") shouldBe true
        PlayerSettingsTorrentScreen.isValidProxyUrl("socks5h://proxy.example:1080") shouldBe true
    }

    @Test
    fun `proxy validation rejects invalid protocols and missing host`() {
        PlayerSettingsTorrentScreen.isValidProxyUrl("ftp://proxy.example:21") shouldBe false
        PlayerSettingsTorrentScreen.isValidProxyUrl("http://") shouldBe false
        PlayerSettingsTorrentScreen.isValidProxyUrl("not a uri") shouldBe false
    }
}
