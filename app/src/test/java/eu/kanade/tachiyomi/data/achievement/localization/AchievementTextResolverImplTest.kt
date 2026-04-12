package eu.kanade.tachiyomi.data.achievement.localization

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import tachiyomi.i18n.aniyomi.AYMR
import kotlin.test.assertEquals

class AchievementTextResolverImplTest {

    @Test
    fun `maps first chapter achievement to localized resources`() {
        val refs = achievementTextResourceRefs("first_chapter")

        assertSame(AYMR.strings.achievement_first_chapter_title, refs.title)
        assertSame(AYMR.strings.achievement_first_chapter_desc, refs.description)
    }

    @Test
    fun `maps feature achievement to localized resources`() {
        val refs = achievementTextResourceRefs("settings_explorer")

        assertSame(AYMR.strings.achievement_settings_explorer_title, refs.title)
        assertSame(AYMR.strings.achievement_settings_explorer_desc, refs.description)
    }

    @Test
    fun `maps novel completion achievement to localized resources`() {
        val refs = achievementTextResourceRefs("complete_10_novel")

        assertSame(AYMR.strings.achievement_complete_10_novel_title, refs.title)
        assertSame(AYMR.strings.achievement_complete_10_novel_desc, refs.description)
    }

    @Test
    fun `returns null refs for unknown achievement`() {
        val refs = achievementTextResourceRefs("unknown_achievement")

        assertNull(refs.title)
        assertNull(refs.description)
        assertEquals(AchievementTextResourceRefs(null, null), refs)
    }
}
