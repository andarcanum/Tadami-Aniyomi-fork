package aniyomi.core.common.torrent

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore

class TorrentPreferencesTest {

    @Test
    fun `defaults keep TorrServer disabled and use expected connection values`() {
        val preferences = TorrentPreferences(InMemoryPreferenceStore())

        preferences.torrServerEnable().get() shouldBe false
        preferences.torrServerShownNotice().get() shouldBe false
        preferences.torrServerPort().get() shouldBe "8090"
        preferences.torrServerProxyMode().get() shouldBe ProxyMode.None
        preferences.torrServerProxyUrl().get() shouldBe ""
        preferences.torrServerTrackers().get().isNotBlank() shouldBe true
    }

    @Test
    fun `proxy mode values match TorrServer expected strings`() {
        ProxyMode.None.value shouldBe ""
        ProxyMode.Tracker.value shouldBe "tracker"
        ProxyMode.Peers.value shouldBe "peers"
        ProxyMode.Full.value shouldBe "full"
    }

    @Test
    fun `trackers preference uses corrected key`() {
        val store = InMemoryPreferenceStore(
            sequenceOf(
                InMemoryPreferenceStore.InMemoryPreference(
                    key = "pref_torrserver_trackers",
                    data = "udp://tracker.example:6969/announce",
                    defaultValue = TorrentPreferences.DEFAULT_TRACKERS,
                ),
            ),
        )

        TorrentPreferences(store).torrServerTrackers().get() shouldBe "udp://tracker.example:6969/announce"
    }
}
