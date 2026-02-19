package eu.kanade.presentation.reader.novel

import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.reader.novel.setting.NovelReaderColorTheme

data class NovelReaderFontOption(
    val id: String,
    val label: String,
    val assetFileName: String,
    val fontResId: Int? = null,
)

val novelReaderPresetThemes: List<NovelReaderColorTheme> = listOf(
    NovelReaderColorTheme(backgroundColor = "#f5f5fa", textColor = "#111111"),
    NovelReaderColorTheme(backgroundColor = "#F7DFC6", textColor = "#593100"),
    NovelReaderColorTheme(backgroundColor = "#dce5e2", textColor = "#000000"),
    NovelReaderColorTheme(backgroundColor = "#292832", textColor = "#CCCCCC"),
    NovelReaderColorTheme(backgroundColor = "#000000", textColor = "#FFFFFFB3"),
)

val novelReaderFonts: List<NovelReaderFontOption> = listOf(
    NovelReaderFontOption(
        id = "",
        label = "Original",
        assetFileName = "",
        fontResId = null,
    ),
    NovelReaderFontOption(
        id = "lora",
        label = "Lora",
        assetFileName = "lora.ttf",
        fontResId = R.font.lora,
    ),
    NovelReaderFontOption(
        id = "nunito",
        label = "Nunito",
        assetFileName = "nunito.ttf",
        fontResId = R.font.nunito,
    ),
    NovelReaderFontOption(
        id = "noto-sans",
        label = "Noto Sans",
        assetFileName = "noto-sans.ttf",
        fontResId = R.font.noto_sans,
    ),
    NovelReaderFontOption(
        id = "open-sans",
        label = "Open Sans",
        assetFileName = "open-sans.ttf",
        fontResId = R.font.open_sans,
    ),
    NovelReaderFontOption(
        id = "arbutus-slab",
        label = "Arbutus Slab",
        assetFileName = "arbutus-slab.ttf",
        fontResId = R.font.arbutus_slab,
    ),
    NovelReaderFontOption(
        id = "domine",
        label = "Domine",
        assetFileName = "domine.ttf",
        fontResId = R.font.domine,
    ),
    NovelReaderFontOption(
        id = "lato",
        label = "Lato",
        assetFileName = "lato.ttf",
        fontResId = R.font.lato,
    ),
    NovelReaderFontOption(
        id = "pt-serif",
        label = "PT Serif",
        assetFileName = "pt-serif.ttf",
        fontResId = R.font.pt_serif,
    ),
    NovelReaderFontOption(
        id = "OpenDyslexic3-Regular",
        label = "OpenDyslexic",
        assetFileName = "OpenDyslexic3-Regular.ttf",
        fontResId = R.font.open_dyslexic3_regular,
    ),
)
