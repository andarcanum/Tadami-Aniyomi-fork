package eu.kanade.tachiyomi.extension.novel.runtime

import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import java.io.File

class NovelPluginScriptOverridesApplierTest {

    @Test
    fun `applies script patches for matching plugin`() {
        val overrides = NovelPluginRuntimeOverrides(
            entries = listOf(
                NovelPluginRuntimeOverride(
                    pluginId = "rulate",
                    scriptPatches = listOf(
                        NovelScriptPatch(
                            pattern = "table > tbody > tr.chapter_row",
                            replacement = "table > tbody > tr.chapter_row, tr.chapter_row",
                        ),
                    ),
                ),
            ),
        )
        val applier = NovelPluginScriptOverridesApplier(overrides)
        val source = "const rows = $('table > tbody > tr.chapter_row');"

        val patched = applier.apply(pluginId = "rulate", script = source)

        patched.shouldContain("table > tbody > tr.chapter_row, tr.chapter_row")
        patched.shouldNotContain("const rows = $('table > tbody > tr.chapter_row');")
    }

    @Test
    fun `returns original script for plugin without patches`() {
        val applier = NovelPluginScriptOverridesApplier(
            NovelPluginRuntimeOverrides(entries = emptyList()),
        )
        val source = "const rows = $('table > tbody > tr.chapter_row');"

        val patched = applier.apply(pluginId = "other", script = source)

        patched shouldContain source
    }

    @Test
    fun `chapter selector replacement preserves valid quoted js selector`() {
        val json = Json { ignoreUnknownKeys = true }
        val payload = readOverridesJson()
        val overrides = NovelPluginRuntimeOverrides.fromJson(json, payload)
        val applier = NovelPluginScriptOverridesApplier(overrides)
        val source = """const rows = i("a.chapter").each(() => {});"""

        val patched = applier.apply(pluginId = "erolate", script = source)

        patched.shouldContain("""i("a.chapter, a[href*=\"/chapter\"], a[href*=\"/read\"]")""")
        patched.shouldNotContain("""i("a.chapter, a[href*="/chapter"], a[href*="/read"]")""")
    }

    @Test
    fun `novelupdates patch makes chapter href extraction robust`() {
        val json = Json { ignoreUnknownKeys = true }
        val payload = readOverridesJson()
        val overrides = NovelPluginRuntimeOverrides.fromJson(json, payload)
        val applier = NovelPluginScriptOverridesApplier(overrides)
        val source = """n="https:"+v(t).find("a").first().next().attr("href");"""

        val patched = applier.apply(pluginId = "novelupdates", script = source)

        patched.shouldNotContain("""first().next().attr("href")""")
        patched.shouldContain("""indexOf("https://")===0""")
        patched.shouldContain("""find("a").eq(1).attr("href")""")
    }

    @Test
    fun `tl patch guards null json payload before reading data`() {
        val json = Json { ignoreUnknownKeys = true }
        val payload = readOverridesJson()
        val overrides = NovelPluginRuntimeOverrides.fromJson(json, payload)
        val applier = NovelPluginScriptOverridesApplier(overrides)
        val source = """t=e.sent().data,n=[];"""

        val patched = applier.apply(pluginId = "TL", script = source)

        patched.shouldContain("""t=(e.sent()||{}).data,n=[];""")
        patched.shouldNotContain("""t=e.sent().data,n=[];""")
    }

    @Test
    fun `tl patch guards null json payload for any transpiled variable name`() {
        val json = Json { ignoreUnknownKeys = true }
        val payload = readOverridesJson()
        val overrides = NovelPluginRuntimeOverrides.fromJson(json, payload)
        val applier = NovelPluginScriptOverridesApplier(overrides)
        val source = """t=a.sent().data,n=[];"""

        val patched = applier.apply(pluginId = "TL", script = source)

        patched.shouldContain("""t=(a.sent()||{}).data,n=[];""")
        patched.shouldNotContain("""t=a.sent().data,n=[];""")
    }

    private fun readOverridesJson(): String {
        val appScoped = File("app/src/main/assets/novel-plugin-overrides.json")
        val moduleScoped = File("src/main/assets/novel-plugin-overrides.json")
        val target = when {
            appScoped.exists() -> appScoped
            moduleScoped.exists() -> moduleScoped
            else -> error("novel-plugin-overrides.json not found")
        }
        return target.readText()
    }
}
