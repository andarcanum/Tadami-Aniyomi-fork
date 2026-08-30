package eu.kanade.tachiyomi.data.discord

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.InMemoryPreferenceStore
import tachiyomi.core.common.preference.Preference

class DiscordPresenceManagerTest {

    private class FakeGateway : DiscordGatewayClient {
        override val status = MutableStateFlow(ConnectionStatus.Disconnected)
        val frames = mutableListOf<String>()
        var connectCalls = 0
        var disconnectCalls = 0
        var lastToken: String? = null

        override fun connect(token: String) {
            connectCalls++
            lastToken = token
        }

        override fun sendFrame(frame: String) {
            frames += frame
        }

        override fun disconnect() {
            disconnectCalls++
        }
    }

    private fun manager(
        store: InMemoryPreferenceStore,
        gateway: FakeGateway,
    ): DiscordPresenceManager = DiscordPresenceManager(
        preferences = DiscordPreferences(store),
        gateway = gateway,
        scope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO.limitedParallelism(1) + CoroutineName("test"),
        ),
        debounceMillis = 50,
    )

    @Test
    fun `enabled session publishes watching frame after debounce`() = runBlocking {
        val gateway = FakeGateway()
        val mgr = manager(enabledStore(), gateway)
        mgr.setSession(
            "h1",
            DiscordPresenceInfo(DiscordPresenceInfo.MediaKind.ANIME, "One Piece", 5.0, "Ep", 1),
        )
        delay(300)
        gateway.frames.size shouldBe 1
        gateway.frames[0] shouldStartWith """{"op":3,"d":{"since":0,"activities":[{"name":"One Piece""""
        gateway.connectCalls shouldBe 1
    }

    @Test
    fun `identical session info is deduplicated`() = runBlocking {
        val gateway = FakeGateway()
        val mgr = manager(enabledStore(), gateway)
        repeat(3) {
            mgr.setSession(
                "h1",
                DiscordPresenceInfo(DiscordPresenceInfo.MediaKind.ANIME, "One Piece", 5.0, "Ep", 1),
            )
        }
        delay(300)
        gateway.frames.size shouldBe 1
    }

    @Test
    fun `disabled or tokenless never connects`() = runBlocking {
        val disabled = FakeGateway()
        manager(InMemoryPreferenceStore(), disabled).setSession(
            "h1",
            DiscordPresenceInfo(DiscordPresenceInfo.MediaKind.MANGA, "X", 1.0, null, 1),
        )
        delay(150)
        disabled.connectCalls shouldBe 0
        disabled.frames shouldContainExactly emptyList()
    }

    @Test
    fun `last session wins and only owner clears`() = runBlocking {
        val gateway = FakeGateway()
        val mgr = manager(enabledStore(), gateway)
        mgr.setSession("h1", DiscordPresenceInfo(DiscordPresenceInfo.MediaKind.MANGA, "A", 1.0, null, 1))
        mgr.setSession("h2", DiscordPresenceInfo(DiscordPresenceInfo.MediaKind.ANIME, "B", 2.0, null, 1))
        delay(300)
        mgr.clearSession("h1")
        gateway.frames.filter { it.contains(""""activities":[]""") } shouldContainExactly emptyList()
        mgr.clearSession("h2")
        delay(100)
        gateway.frames.last() shouldBe """{"op":3,"d":{"since":0,"activities":[],"status":"online","afk":false}}"""
        gateway.disconnectCalls shouldBe 1
    }

    @Test
    fun `clear sends empty presence and disconnects`() = runBlocking {
        val gateway = FakeGateway()
        val mgr = manager(enabledStore(), gateway)
        mgr.setSession("h1", DiscordPresenceInfo(DiscordPresenceInfo.MediaKind.NOVEL, "N", 3.0, null, 1))
        delay(300)
        mgr.clearSession("h1")
        delay(100)
        gateway.frames.last() shouldBe """{"op":3,"d":{"since":0,"activities":[],"status":"online","afk":false}}"""
        gateway.disconnectCalls shouldBe 1
    }

    @Test
    fun `reconnect resends current presence`() = runBlocking {
        val gateway = FakeGateway()
        val mgr = manager(enabledStore(), gateway)
        mgr.setSession("h1", DiscordPresenceInfo(DiscordPresenceInfo.MediaKind.ANIME, "One Piece", 5.0, "Ep", 1))
        delay(300)
        val sentBefore = gateway.frames.size
        gateway.status.value = ConnectionStatus.Connected // эмулируем переподключение транспорта
        delay(300)
        gateway.frames.size shouldBe sentBefore + 1
    }

    private fun enabledStore() = InMemoryPreferenceStore(
        sequenceOf(
            pref(Preference.privateKey("pref_discord_presence_enabled"), true, false),
            pref(Preference.privateKey("pref_discord_presence_token"), "tok", ""),
        ),
    )

    private fun <T> pref(key: String, data: T, default: T) =
        InMemoryPreferenceStore.InMemoryPreference(key, data, default)
}
