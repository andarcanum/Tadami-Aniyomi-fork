package eu.kanade.presentation.easteregg.crystal

import eu.kanade.domain.easteregg.crystal.CrystalDayPhase
import eu.kanade.domain.easteregg.crystal.CrystalWeather
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

class CrystalLivingArtworkContractTest {
    @Test
    fun shippedAssetMatchesFinalPostMutationHashAndEverySupportedElementIsRetained() {
        val bytes = asset().readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(hash == "4d9f3d6b810fabd4f6991528b7c3021be4157ec7bcd6df2686ad629288fcbf7c")
        val art = CrystalLivingArtwork.load(ByteArrayInputStream(bytes))
        // V8 provenance counts actual element nodes, including the parsed root.
        check(art.nodes.size == 711)
        check(art.nodes.map { it.index }.toSet().size == art.nodes.size)
        check(
            art.nodes.groupingBy { it.tag }.eachCount() == mapOf(
                "svg" to 2, "title" to 2, "desc" to 2, "defs" to 2, "linearGradient" to 20,
                "radialGradient" to 17, "stop" to 107, "clipPath" to 13, "path" to 392,
                "g" to 84, "ellipse" to 41, "circle" to 24, "polygon" to 3, "rect" to 2,
            ),
        )
        check(art.nodes.none { it.tag == "style" || it.tag == "image" || it.tag == "script" })
        check(art.root.attributes["viewBox"] == "0 -110 640 530")
        check(art.viewBox == CrystalLivingViewBox(0f, -110f, 640f, 530f))
        check(art.root.attributes["preserveAspectRatio"] == "xMidYMid meet")
    }

    @Test
    fun nestedViewboxOrderedLayersAndAuthoredOriginsRemainInTheirOwnCoordinateSystems() {
        val art = loadAsset()
        val cat = art.nodes.single { "crystal-illustrated-tabby" in it.classes }
        check(cat.attributes["viewBox"] == "0 0 360 380")
        check(cat.number("x") == 262f && cat.number("y") == 74f)
        check(cat.number("width") == 300f && cat.number("height") == 317f)
        check(cat.attributes["overflow"] == "visible")
        val rootOrder = art.root.children.map { it.classes }
        check(
            rootOrder.indexOfFirst { "bed-front" in it } <
                rootOrder.indexOfFirst { "crystal-illustrated-tabby" in it },
        )
        check(rootOrder.takeLast(3).map { it.single() } == listOf("yarn-thread", "physics-yarn", "physics-ball"))
        val expected = mapOf(
            "cat-head" to "169 143", "cat-body" to "165 295", "pro-chest-bib" to "167 174",
            "cat-tail" to "223 280", "pro-ear-left" to "132 72", "pro-ear-right" to "206 69",
            "pro-whiskers-left" to "149 128", "pro-whiskers-right" to "191 124",
            "pro-muzzle-overlay" to "169 124", "forepaw-left" to "150 186", "forepaw-right" to "184 186",
            "bed-cushion" to "402 334", "hanging-crystal" to "354 24", "toy-feather" to "208 366",
            "post-dangler" to "606 255",
        )
        for ((role, origin) in expected) {
            check(art.nodes.single { role in it.classes }.attributes["data-origin"] == origin)
        }
        check(art.nodes.count { "pro-iris-group" in it.classes } == 2)
        val placement = art.nodes.single { "crystal-placement" in it.classes }
        check(placement.attributes["transform"] == "translate(0 -184)")
        check(placement.children.single().classes.contains("hanging-crystal"))
    }

    @Test
    fun negativeViewboxCameraFitsTheCeilingOnceWithoutMovingBodyOrToyWorldCoordinates() {
        val box = loadAsset().viewBox
        for (width in listOf(297f, 320f, 390f, 640f, 720f)) {
            val height = width * box.height / box.width
            val viewport = box.fit(width, height)
            val scale = viewport.scale
            val x = viewport.offsetX
            val y = viewport.offsetY
            checkClose(box.x * scale + x, 0f)
            checkClose(box.y * scale + y, 0f)
            checkClose((box.x + box.width) * scale + x, width)
            checkClose((box.y + box.height) * scale + y, height)
            // Camera translation is not baked into the original cat/toy coordinate system.
            checkClose(0f * scale + y, 110f * scale)
            checkClose((542f * scale + x - x) / scale, 542f)
            checkClose((336f * scale + y - y) / scale, 336f)
        }
        val fitted = box.fit(640f, 640f)
        check(fitted == CrystalLivingViewport(1f, 0f, 165f))
        checkClose(box.y * fitted.scale + fitted.offsetY, 55f)
    }

    @Test
    fun v8RetainsSoftSidePlaneCeilingAndMatchingTopAndBottomSeamGradients() {
        val art = loadAsset()
        val ceiling = art.nodes.single { "v7-ceiling" in it.classes }
        check(ceiling.properties["d"] == "M0-110H640V0H0Z")
        check(ceiling.properties["fill"] == "var(--room-wall,#172c3b)")
        val side = art.nodes.single { it.properties["d"] == "M615 0H640V290L615 278Z" }
        check(side.number("opacity") == 0.12f)
        check(art.ids.getValue("hero-wall-vignette").children.last().number("stop-opacity") == 0.25f)
        val top = art.ids.getValue("hero-v6-top-match")
        val bottom = art.ids.getValue("hero-v6-bottom-match")
        check(top.children.map { it.number("stop-opacity", 1f) } == listOf(1f, 0f))
        check(bottom.children.map { it.number("stop-opacity", 1f) } == listOf(0f, 1f))
        check(top.children.all { it.properties["stop-color"] == "var(--room-wall,#172c3b)" })
        check(bottom.children.all { it.properties["stop-color"] == "var(--room-floor,#223b45)" })
        val seam = art.nodes.single { "v6-backdrop-seam" in it.classes }
        check(seam.children.map { it.properties["d"] } == listOf("M0 0H640V58H0Z", "M0 338H640V420H0Z"))
        check(
            seam.children.map { it.properties["fill"] } ==
                listOf("url(#hero-v6-top-match)", "url(#hero-v6-bottom-match)"),
        )
    }

    @Test
    fun intrinsicViewboxRejectsInvalidDimensionsAndStillLoadsUnchangedHistoricalV4() {
        for (viewBox in listOf("0 0 640", "0 0 640 420 1", "0 0 0 530", "0 0 640 -1", "0 NaN 640 530")) {
            assertThrows(IllegalArgumentException::class.java) {
                CrystalLivingArtwork.load(
                    ByteArrayInputStream(
                        ("<svg viewBox='" + viewBox + "'/> ").toByteArray(),
                    ),
                )
            }
        }
        val bytes = asset("easteregg/crystal/living_corner_v4.svg").readBytes()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(hash == "a700ccdc587bae3c79e7803ab4e7fea01b4e0dc1794f468821ee82dfbec53c8e")
        val old = CrystalLivingArtwork.load(ByteArrayInputStream(bytes))
        check(old.nodes.size == 701 && old.viewBox == CrystalLivingViewBox(0f, 0f, 640f, 420f))
        check(old.viewBox.fit(320f, 210f) == CrystalLivingViewport(0.5f, 0f, 0f))
    }

    @Test
    fun gradientsClipsStrokeDetailsAndAmbientMetadataAreNotFlattenedOrDiscarded() {
        val art = loadAsset()
        val eye = art.ids.getValue("hero-pro-eye-amber")
        check(eye.tag == "radialGradient")
        check(eye.attributes["cx"] == "46%" && eye.attributes["cy"] == "62%")
        check(eye.attributes["fx"] == "42%" && eye.attributes["fy"] == "72%")
        check(eye.children.size == 5)
        check(art.ids.getValue("hero-window-clip").children.single().tag == "path")
        check(art.nodes.any { it.attributes["stroke-dasharray"] == "5 6" })
        check(art.nodes.any { it.attributes["stroke-linecap"] == "round" })
        check(art.nodes.any { it.attributes["stroke-linejoin"] == "round" })
        check(art.nodes.count { "atmo-rain-sheet" in it.classes } == 2)
        check(art.nodes.count { "ambient-mote" in it.classes } == 6)
        val drop = art.nodes.first { "atmo-glass-drop" in it.classes }
        check(drop.properties["--drop-duration"] == "6.4s")
        check(drop.properties["--drop-distance"] == "42px")
        check(art.nodes.single { "physics-yarn" in it.classes }.number("data-radius") == 25f)
        check(art.nodes.single { "physics-ball" in it.classes }.number("data-radius") == 17f)
        val yarn = art.nodes.single { "physics-yarn" in it.classes }
        val ball = art.nodes.single { "physics-ball" in it.classes }
        check(yarn.number("data-base-x") == 542f && yarn.number("data-base-y") == 336f)
        check(ball.number("data-base-x") == 251f && ball.number("data-base-y") == 353f)
    }

    @Test
    fun parserIsPureAndRejectsUnsupportedTagsDuplicateIdsMissingAndExternalReferences() {
        for (body in listOf(
            "<image href='a.png'/>",
            "<script/>",
            "<style/>",
            "<path id='same'/><path id='same'/>",
            "<path fill='url(#absent)'/>",
            "<path fill='url(https://example.invalid/a)'/>",
        )) {
            assertThrows(IllegalArgumentException::class.java) { load(body) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            CrystalLivingArtwork.load(
                ByteArrayInputStream(
                    "<!DOCTYPE svg [<!ENTITY remote SYSTEM 'file:///not-allowed'>]><svg viewBox='0 0 640 420'/>"
                        .toByteArray(),
                ),
            )
        }
        val parsed = load(
            "<g fill='#abcdef' fill-opacity='.4' stroke='#112233'>" +
                "<path style='stroke-width:2;stroke-linecap:round'/></g>",
        )
        val path = parsed.nodes.single { it.tag == "path" }
        check(path.properties["stroke-width"] == "2")
        check(path.properties["stroke-linecap"] == "round")
    }

    @Test
    fun yarnThreadAncestryRemainsDistinctFromAnUnrelatedIdenticallyColoredStroke() {
        val art = load(
            "<g class='yarn-thread'><path class='palette-stroke-scene-mint' d='M0 0L1 1'/></g>" +
                "<g class='unrelated'><path class='palette-stroke-scene-mint' d='M2 2L3 3'/></g>",
        )
        val thread = art.root.children.first().children.single()
        val unrelated = art.root.children.last().children.single()
        check(thread.classes == unrelated.classes)
        check(thread.index != unrelated.index)
        check("yarn-thread" in art.root.children.first().classes)
        check("yarn-thread" !in art.root.children.last().classes)
        check(thread.properties["d"] == "M0 0L1 1")
        check(unrelated.properties["d"] == "M2 2L3 3")
    }

    @Test
    fun phaseAndWetPaintGoldensMatchCurrentInlineV4RatherThanOlderPaletteDraft() {
        val day = colors(CrystalDayPhase.DAY, CrystalWeather.CLEAR)
        check(day.wall == hex("2f4b57") && day.floor == hex("3d5c63"))
        check(day.skyHigh == hex("5fa4ba") && day.skyLow == hex("dcebd8"))
        check(day.sunStrength == 0.34f && day.shelterHigh == hex("fff4e3"))
        val rain = colors(CrystalDayPhase.DAY, CrystalWeather.RAIN)
        check(rain.wall == hex("243c48") && rain.floor == hex("2f4b54"))
        check(rain.trim == hex("7b9699") && rain.skyHigh == hex("364f63") && rain.skyLow == hex("6f8b91"))
        check(rain.heading == hex("f2e5d2") && rain.muted == hex("c5d6d2"))
        check(rain.sunStrength == 0.08f && rain.shelterHigh == hex("ece2d5") && rain.shelterLow == hex("a89c90"))
        val nightRain = colors(CrystalDayPhase.NIGHT, CrystalWeather.RAIN)
        check(nightRain.wall == hex("182d3a") && nightRain.floor == hex("223b45"))
        val storm = colors(CrystalDayPhase.NIGHT, CrystalWeather.STORM)
        check(storm.wall == hex("1b2b36") && storm.floor == hex("233640") && storm.trim == hex("6f8791"))
        check(storm.skyHigh == hex("1d3142") && storm.skyLow == hex("4f6875"))
        check(storm.heading == hex("f2e8d5") && storm.muted == hex("c2d0cc") && storm.sunStrength == 0.05f)
        check(storm.shelterHigh == hex("e2d8ce") && storm.shelterLow == hex("9c8f88"))
        check(
            resolveCrystalLivingColors(
                CrystalDayPhase.NIGHT,
                CrystalWeather.STORM,
                CrystalPalette.Moon,
                false,
                true,
                true,
            ).wall == hex("0e202b"),
        )
    }

    @Test
    fun inkHasNeutralStaticLightAndDarkRoomsAndOnlyColorInkRetainsArtworkChromas() {
        for (phase in CrystalDayPhase.entries) {
            for (weather in CrystalWeather.entries) {
                for (dark in listOf(false, true)) {
                    val mono = resolveCrystalLivingColors(phase, weather, CrystalPalette.Dusk, true, dark, false)
                    val color = resolveCrystalLivingColors(phase, weather, CrystalPalette.Dusk, true, dark, false, true)
                    check(mono.eInk && mono.monochrome && color.eInk && !color.monochrome)
                    check(mono.sunStrength == 0f && color.sunStrength == 0f)
                    check(mono.wall == hex(if (dark) "171717" else "f3f3f3"))
                    check(mono.heading == hex(if (dark) "eeeeee" else "111111"))
                    check(mono.shelterHigh == hex(if (dark) "cccccc" else "f6f6f6"))
                    check(mono.shelterLow == hex(if (dark) "777777" else "bbbbbb"))
                    val gray = mono.token("scene-gold", 0)
                    check((gray ushr 16 and 255) == (gray ushr 8 and 255) && (gray ushr 8 and 255) == (gray and 255))
                    check(color.token("scene-gold", 0) == CrystalPalette.Dusk.gold)
                }
            }
        }
        val colors = colors(CrystalDayPhase.DAY, CrystalWeather.CLEAR)
        check(colors.token("--scene-mint", 0) == CrystalPalette.Moon.mint)
        check(colors.token("unknown", 0x12345678) == 0x12345678)
    }

    private fun colors(phase: CrystalDayPhase, weather: CrystalWeather) =
        resolveCrystalLivingColors(phase, weather, CrystalPalette.Moon, false, true, false)

    private fun hex(value: String): Int = (0xff000000L or value.toLong(16)).toInt()

    private fun load(body: String): CrystalLivingArtwork = CrystalLivingArtwork.load(
        ByteArrayInputStream(
            ("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 640 420'>" + body + "</svg>").toByteArray(),
        ),
    )

    private fun loadAsset() = asset().inputStream().use(CrystalLivingArtwork::load)

    private fun asset(name: String = CrystalLivingArtwork.ASSET_NAME): File = listOf(
        File("src/main/assets/" + name),
        File("app/src/main/assets/" + name),
    ).first { it.isFile }

    private fun checkClose(actual: Float, expected: Float) {
        check(kotlin.math.abs(actual - expected) < 0.0001f) { "Expected $expected, got $actual" }
    }
}
