package eu.kanade.presentation.reader.novel

import com.tadami.aurora.R
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderColorTheme

data class NovelReaderFontOption(
    val id: String,
    val label: String,
    val assetFileName: String = "",
    val assetPath: String = "",
    val fontResId: Int? = null,
    val source: NovelReaderFontSource = NovelReaderFontSource.BUILT_IN,
    val filePath: String? = null,
)

enum class NovelReaderFontSource {
    BUILT_IN,
    LOCAL_PRIVATE,
    USER_IMPORTED,
}

data class NovelReaderBackgroundPreset(
    val id: String,
    val imageResId: Int,
    val isDarkPreferred: Boolean,
    val backdropColorHex: String,
)

data class NovelReaderBackgroundCard(
    val id: String,
    val preset: NovelReaderBackgroundPreset? = null,
    val customItem: NovelReaderCustomBackgroundItem? = null,
) {
    val isBuiltIn: Boolean
        get() = preset != null
}

const val NOVEL_READER_BACKGROUND_PRESET_LINEN_PAPER_ID = "linen_paper"
const val NOVEL_READER_BACKGROUND_PRESET_AGED_PAGE_ID = "aged_page"
const val NOVEL_READER_BACKGROUND_PRESET_AGED_PARCHMENT_ID = "aged_parchment"
const val NOVEL_READER_BACKGROUND_PRESET_CRUMPLED_SHEET_ID = "crumpled_sheet"
const val NOVEL_READER_BACKGROUND_PRESET_NIGHT_VELVET_ID = "night_velvet"
const val NOVEL_READER_BACKGROUND_PRESET_DARK_WOOD_ID = "dark_wood"

val novelReaderBackgroundPresets: List<NovelReaderBackgroundPreset> = listOf(
    NovelReaderBackgroundPreset(
        id = NOVEL_READER_BACKGROUND_PRESET_LINEN_PAPER_ID,
        imageResId = R.drawable.novel_bg_linen_paper,
        isDarkPreferred = false,
        backdropColorHex = "#E8DDBD",
    ),
    NovelReaderBackgroundPreset(
        id = NOVEL_READER_BACKGROUND_PRESET_AGED_PAGE_ID,
        imageResId = R.drawable.novel_bg_aged_page,
        isDarkPreferred = false,
        backdropColorHex = "#ECCF98",
    ),
    NovelReaderBackgroundPreset(
        id = NOVEL_READER_BACKGROUND_PRESET_AGED_PARCHMENT_ID,
        imageResId = R.drawable.novel_bg_aged_parchment,
        isDarkPreferred = false,
        backdropColorHex = "#EFCA9C",
    ),
    NovelReaderBackgroundPreset(
        id = NOVEL_READER_BACKGROUND_PRESET_CRUMPLED_SHEET_ID,
        imageResId = R.drawable.novel_bg_crumpled_sheet,
        isDarkPreferred = false,
        backdropColorHex = "#D1BC96",
    ),
    NovelReaderBackgroundPreset(
        id = NOVEL_READER_BACKGROUND_PRESET_NIGHT_VELVET_ID,
        imageResId = R.drawable.novel_bg_night_velvet,
        isDarkPreferred = true,
        backdropColorHex = "#0F121C",
    ),
    NovelReaderBackgroundPreset(
        id = NOVEL_READER_BACKGROUND_PRESET_DARK_WOOD_ID,
        imageResId = R.drawable.novel_bg_dark_wood,
        isDarkPreferred = true,
        backdropColorHex = "#1B1209",
    ),
)

fun buildNovelReaderBackgroundCards(
    customItems: List<ReaderBackgroundCatalogItem>,
): List<NovelReaderBackgroundCard> {
    val resolvedCustomItems = customItems.map { item ->
        NovelReaderCustomBackgroundItem(
            id = item.id,
            displayName = item.displayName,
            fileName = item.fileName,
            absolutePath = item.fileName,
            isDarkHint = item.isDarkHint,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt,
        )
    }
    return buildNovelReaderBackgroundCardsFromCustomItems(resolvedCustomItems)
}

fun buildNovelReaderBackgroundCardsFromCustomItems(
    customItems: List<NovelReaderCustomBackgroundItem>,
): List<NovelReaderBackgroundCard> {
    val presetCards = novelReaderBackgroundPresets.map { preset ->
        NovelReaderBackgroundCard(
            id = preset.id,
            preset = preset,
        )
    }
    val customCards = customItems.map { item ->
        NovelReaderBackgroundCard(
            id = item.id,
            customItem = item,
        )
    }
    return presetCards + customCards
}

val novelReaderPresetThemes: List<NovelReaderColorTheme> = listOf(
    NovelReaderColorTheme(backgroundColor = "#f5f5fa", textColor = "#111111"),
    NovelReaderColorTheme(backgroundColor = "#F7DFC6", textColor = "#593100"),
    NovelReaderColorTheme(backgroundColor = "#dce5e2", textColor = "#000000"),
    NovelReaderColorTheme(backgroundColor = "#292832", textColor = "#CCCCCC"),
    NovelReaderColorTheme(backgroundColor = "#000000", textColor = "#FFFFFFB3"),
)

val novelReaderBuiltInFonts: List<NovelReaderFontOption> = listOf(
    NovelReaderFontOption(
        id = "",
        label = "Original",
        assetFileName = "",
        assetPath = "",
        fontResId = null,
    ),
    NovelReaderFontOption(
        id = "lora",
        label = "Lora",
        assetFileName = "lora.ttf",
        assetPath = "fonts/lora.ttf",
        fontResId = R.font.lora,
    ),
    NovelReaderFontOption(
        id = "nunito",
        label = "Nunito",
        assetFileName = "nunito.ttf",
        assetPath = "fonts/nunito.ttf",
        fontResId = R.font.nunito,
    ),
    NovelReaderFontOption(
        id = "noto-sans",
        label = "Noto Sans",
        assetFileName = "noto-sans.ttf",
        assetPath = "fonts/noto-sans.ttf",
        fontResId = R.font.noto_sans,
    ),
    NovelReaderFontOption(
        id = "open-sans",
        label = "Open Sans",
        assetFileName = "open-sans.ttf",
        assetPath = "fonts/open-sans.ttf",
        fontResId = R.font.open_sans,
    ),
    NovelReaderFontOption(
        id = "arbutus-slab",
        label = "Arbutus Slab",
        assetFileName = "arbutus-slab.ttf",
        assetPath = "fonts/arbutus-slab.ttf",
        fontResId = R.font.arbutus_slab,
    ),
    NovelReaderFontOption(
        id = "domine",
        label = "Domine",
        assetFileName = "domine.ttf",
        assetPath = "fonts/domine.ttf",
        fontResId = R.font.domine,
    ),
    NovelReaderFontOption(
        id = "lato",
        label = "Lato",
        assetFileName = "lato.ttf",
        assetPath = "fonts/lato.ttf",
        fontResId = R.font.lato,
    ),
    NovelReaderFontOption(
        id = "pt-serif",
        label = "PT Serif",
        assetFileName = "pt-serif.ttf",
        assetPath = "fonts/pt-serif.ttf",
        fontResId = R.font.pt_serif,
    ),
    NovelReaderFontOption(
        id = "OpenDyslexic3-Regular",
        label = "OpenDyslexic",
        assetFileName = "OpenDyslexic3-Regular.ttf",
        assetPath = "fonts/OpenDyslexic3-Regular.ttf",
        fontResId = R.font.open_dyslexic3_regular,
    ),
)

val novelReaderFonts: List<NovelReaderFontOption>
    get() = novelReaderBuiltInFonts
