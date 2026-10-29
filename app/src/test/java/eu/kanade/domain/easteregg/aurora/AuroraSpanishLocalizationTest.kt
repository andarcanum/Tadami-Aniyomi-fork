package eu.kanade.domain.easteregg.aurora

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AuroraSpanishLocalizationTest {

    @Test
    fun knownUiTextsTranslateFromRussianAndEnglish() {
        val expected = mapOf(
            "Echo I — Insomnia" to "Eco I — Insomnio",
            "Echo II — Landmark" to "Eco II — Punto de referencia",
            "Aurora Echo" to "Eco de Aurora",
            "Call of Aurora" to "Llamado de Aurora",
            "AURORA CODEX" to "CÓDICE DE AURORA",
            "The Call" to "El llamado",
            "Echo" to "Eco",
            "The Letter" to "La carta",
            "Relive the Manifestation" to "Revive la manifestación",
            "Save to Heart" to "Guardar en el corazón",
            "if words are powerless — hold the riddle" to
                "si las palabras no bastan, mantén presionado el acertijo",
            "Replay the echo" to "Volver a reproducir el eco",
            "Aurora noticed you at this hour..." to "Aurora se fijó en ti a esta hora...",
            "Some boundaries are thinner than others." to "Algunas fronteras son más delgadas que otras.",
            "Hidden by the aurora" to "Oculto por la aurora boreal",
        )

        AuroraLocalization.UI_KEYS.values.toSet() shouldBe expected.keys
        AuroraLocalization.UI_KEYS.forEach { (ru, en) ->
            AuroraLocalization.translate(ru, "es") shouldBe expected.getValue(en)
            AuroraLocalization.translate(en, "es") shouldBe expected.getValue(en)
        }
    }

    @Test
    fun additionalKnownTextsTranslateToSpanish() {
        AuroraLocalization.translate("Heart of Aurora", "es") shouldBe "Corazón de Aurora"
        AuroraLocalization.translate("Сердце Авроры", "es") shouldBe "Corazón de Aurora"
        AuroraLocalization.translate("Северное сияние помнит тех, кто не спит.", "es") shouldBe
            "La aurora boreal recuerda a quienes no duermen."
    }

    @Test
    fun unknownTextsKeepTheirOriginalValue() {
        AuroraLocalization.translate("  texto desconocido  ", "es") shouldBe "  texto desconocido  "
        AuroraLocalization.translate("  неизвестная строка  ", "es") shouldBe "  неизвестная строка  "
        AuroraLocalization.translate("Aurora Prime", "es") shouldBe "Aurora Prime"
        AuroraLocalization.translate("", "es") shouldBe ""
        AuroraLocalization.translate("   ", "es") shouldBe "   "
        AuroraLocalization.translate(null, "es") shouldBe null
    }

    @Test
    fun knownTextLookupIgnoresSurroundingWhitespace() {
        AuroraLocalization.translate("  Зов  ", "es") shouldBe "El llamado"
        AuroraLocalization.translate("\nAurora Echo\t", "es") shouldBe "Eco de Aurora"
    }

    @Test
    fun themeUnlockUsesSpanishWithoutChangingTheThemeName() {
        AuroraLocalization.translateThemeUnlock("Aurora Prime", "es") shouldBe
            "Tema desbloqueado: Aurora Prime"
        AuroraLocalization.translateThemeUnlock(null, "es") shouldBe null
        AuroraLocalization.translateThemeUnlock("", "es") shouldBe null
        AuroraLocalization.translateThemeUnlock("   ", "es") shouldBe null
    }

    @Test
    fun displayFallbackCanBeTranslatedToSpanish() {
        val englishFallback = AuroraLocalization.auroraDisplayTitle(null, "???", isEnglish = true)
        val russianFallback = AuroraLocalization.auroraDisplayTitle(null, "???", isEnglish = false)

        AuroraLocalization.translate(englishFallback, "es") shouldBe "Corazón de Aurora"
        AuroraLocalization.translate(russianFallback, "es") shouldBe "Corazón de Aurora"
    }

    @Test
    fun additionalAuroraUiTextsTranslateToSpanish() {
        val expected = mapOf(
            "Aurora Keeper" to "Guardián de Aurora",
            "Хранитель Авроры" to "Guardián de Aurora",
            "CONSTELLATION" to "CONSTELACIÓN",
            "the thread is thin — slower" to "el hilo es frágil: más despacio",
            "touch the stars, one by one" to "toca las estrellas, una por una",
            "join the stars with one thread" to "une las estrellas con un solo hilo",
            "Anew" to "Empezar de nuevo",
            "Seal" to "Sellar",
            "weave with a thread" to "tejer con un hilo",
            "touch one by one" to "tocar una por una",
            "AURORA ECHO" to "ECO DE AURORA",
        )
        expected.forEach { (source, spanish) ->
            AuroraLocalization.translate(source, "es") shouldBe spanish
        }
    }

    @Test
    fun newAuroraEnglishUiTextsStayUnchangedForEnglish() {
        val texts = listOf(
            "Aurora Keeper",
            "CONSTELLATION",
            "the thread is thin — slower",
            "touch the stars, one by one",
            "join the stars with one thread",
            "Anew",
            "Seal",
            "weave with a thread",
            "touch one by one",
            "AURORA ECHO",
        )
        texts.forEach { source ->
            AuroraLocalization.translate(source, "en") shouldBe source
            AuroraLocalization.translate(source, "ru") shouldBe source
        }
        AuroraLocalization.translate("Хранитель Авроры", "ru") shouldBe "Хранитель Авроры"
    }
}
