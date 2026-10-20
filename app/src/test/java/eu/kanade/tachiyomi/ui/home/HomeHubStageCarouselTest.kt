package eu.kanade.tachiyomi.ui.home

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import tachiyomi.domain.discovery.model.DiscoveryMediaType
import tachiyomi.domain.discovery.model.DiscoveryRowType

class HomeHubStageCarouselTest {

    @Test
    fun `focus slot keeps full scale, no dim and no offset`() {
        val focus = resolveStageSlotPose(0)
        focus.scale shouldBe 1f
        focus.alpha shouldBe 1f
        focus.dimAlpha shouldBe 0f
        focus.translationXPercent shouldBe 0f
        focus.rotationYDeg shouldBe 0f
    }

    @Test
    fun `near neighbour is smaller, dimmed and shifted to its side`() {
        val left = resolveStageSlotPose(-1)
        val right = resolveStageSlotPose(1)
        (left.scale < 1f) shouldBe true
        left.alpha shouldBe 1f
        (left.dimAlpha > 0f) shouldBe true
        (left.translationXPercent < 0f) shouldBe true
        (right.translationXPercent > 0f) shouldBe true
        right.scale shouldBe left.scale
    }

    @Test
    fun `far slots fade out and buffer slots stay invisible`() {
        val far = resolveStageSlotPose(2)
        (far.alpha > 0f) shouldBe true
        (far.alpha < 1f) shouldBe true
        (far.dimAlpha > resolveStageSlotPose(1).dimAlpha) shouldBe true

        resolveStageSlotPose(3).alpha shouldBe 0f
        resolveStageSlotPose(-3).alpha shouldBe 0f
    }

    @Test
    fun `pose interpolation stays strictly between key poses`() {
        val from = resolveStageSlotPose(0)
        val to = resolveStageSlotPose(1)
        val middle = lerpStageSlotPose(from, to, 0.5f)
        (middle.scale < from.scale) shouldBe true
        (middle.scale > to.scale) shouldBe true
        (middle.translationXPercent > from.translationXPercent) shouldBe true
        (middle.translationXPercent < to.translationXPercent) shouldBe true
        (middle.dimAlpha > from.dimAlpha) shouldBe true
        (middle.dimAlpha < to.dimAlpha) shouldBe true
    }

    @Test
    fun `item index wraps around the feed in both directions`() {
        stageItemIndex(center = 0, slot = 0, size = 12) shouldBe 0
        stageItemIndex(center = 0, slot = -1, size = 12) shouldBe 11
        stageItemIndex(center = 11, slot = 2, size = 12) shouldBe 1
        stageItemIndex(center = 13, slot = 0, size = 12) shouldBe 1
        stageItemIndex(center = -1, slot = 0, size = 12) shouldBe 11
        stageItemIndex(center = 5, slot = 2, size = 12) shouldBe 7
    }

    @Test
    fun `window slots never repeat while the feed is long enough`() {
        val slots = (-3..3).map { stageItemIndex(center = 4, slot = it, size = 12) }
        slots.distinct().size shouldBe slots.size
    }

    @Test
    fun `short feed repeats titles across the window instead of crashing`() {
        val slots = (-3..3).map { stageItemIndex(center = 0, slot = it, size = 3) }
        slots.all { it in 0..2 } shouldBe true
        slots.distinct().size shouldBe 3
    }

    @Test
    fun `motion spec honours speed, e-ink and disabled animations`() {
        resolveStageMotionSpec("fast", isEInk = false, animationsEnabled = true).settleMillis shouldBe 250
        resolveStageMotionSpec("smooth", isEInk = false, animationsEnabled = true).settleMillis shouldBe 700
        resolveStageMotionSpec("normal", isEInk = false, animationsEnabled = true).settleMillis shouldBe 420
        resolveStageMotionSpec("normal", isEInk = true, animationsEnabled = true).settleMillis shouldBe 0
        resolveStageMotionSpec("normal", isEInk = false, animationsEnabled = false).settleMillis shouldBe 0
        resolveStageMotionSpec("fast", isEInk = false, animationsEnabled = true).fadeMillis shouldBe 200
    }

    @Test
    fun `micro drag returns to the slot at touch regardless of velocity`() {
        // Величины в страницах: одна страница = 100 единиц (как px при travel 100).
        resolveStageDragTarget(4f, 4.02f, -0.03f, 0.10f, 0.16f) shouldBe 4
        resolveStageDragTarget(4f, 3.98f, 0.03f, -0.10f, 0.16f) shouldBe 4
    }

    @Test
    fun `slow swipe settles to the nearest slot without flick projection`() {
        // Торможённый релиз: скорость ниже порога «страница за 300 мс» — важна только позиция пальца.
        // Конвенция знаков: драг влево = отрицательное смещение, intent впереди базы.
        resolveStageDragTarget(4f, 5.0f, -0.0005f, -0.25f, 0.16f) shouldBe 5
        resolveStageDragTarget(4f, 4.6f, 0.0029f, 0.60f, 0.16f) shouldBe 5
        resolveStageDragTarget(4f, 3.4f, -0.001f, 0.60f, 0.16f) shouldBe 3
    }

    @Test
    fun `small swipe always advances a single title regardless of flick velocity`() {
        // Небольшой свайп = следующий тайтл: кап одного слота для драга < 1.25 страницы,
        // скорость может дотолкнуть только до ближайшей границы — не дальше.
        resolveStageDragTarget(4f, 4.2f, -0.03f, -0.20f, 0.16f) shouldBe 5
        resolveStageDragTarget(4f, 3.6f, 0.03f, 0.20f, 0.16f) shouldBe 3
        resolveStageDragTarget(4f, 4.4f, -0.008f, -0.44f, 0.16f) shouldBe 5
    }

    @Test
    fun `long swipe or strong flick still reaches two pages`() {
        // Драг ≥ 1.25 страницы — кап поднимается до двух: «больше порога = скролл».
        resolveStageDragTarget(4f, 5.4f, -0.008f, -1.40f, 0.16f) shouldBe 6
        // Длинный медленный драг: округление само даёт два слота, скорость не нужна.
        resolveStageDragTarget(4f, 5.6f, 0f, -1.60f, 0.16f) shouldBe 6
        resolveStageDragTarget(4f, 2.4f, 0f, 1.60f, 0.16f) shouldBe 2
    }

    @Test
    fun `lift-off bounce against the drag direction is not a flick`() {
        // Регрессия «проматывает назад 3–4 тайтла»: при подъёме пальца цифрайзер выдаёт
        // спайк скорости ПРОТИВ смещения — проекция не применяется, ближайший слот от пальца.
        resolveStageDragTarget(4f, 4.4f, 0.008f, -0.44f, 0.16f) shouldBe 4
        resolveStageDragTarget(4f, 3.6f, -0.02f, 0.60f, 0.16f) shouldBe 4
    }

    @Test
    fun `genuine reverse fling still projects backward`() {
        // Пользователь протащил вправо и рванул вправо: скорость и смещение в одну сторону — флик.
        // Небольшой обратный рывок — один слот назад (кап небольшого драга).
        resolveStageDragTarget(4f, 3.4f, 0.02f, 0.60f, 0.16f) shouldBe 3
        // Длинный обратный рывок — два слота назад.
        resolveStageDragTarget(4f, 2.6f, 0.02f, 1.40f, 0.16f) shouldBe 2
    }

    @Test
    fun `flick projection is resolution independent in page units`() {
        // Регрессия старого бага единиц: скорость в dp/мс делилась на travel в px — проекция
        // занижалась в density раз, флики «не долетали». Реальное устройство: travel 248px,
        // бросок 2.2px/мс влево, порог 16dp ≈ 44px; конвертация в страницы — на месте жеста.
        val travelPx = 248f
        val flickMinPages = 44f / travelPx
        val displacementPages = -80f / travelPx
        resolveStageDragTarget(
            dragBase = 4f,
            intent = 4f - displacementPages,
            velocityPagesPerMs = -2.2f / travelPx,
            displacementPages = displacementPages,
            flickMinPages = flickMinPages,
        ) shouldBe 5
    }

    @Test
    fun `auto rotation is off in e-ink, without interval, when paused or without animations`() {
        shouldAutoRotateStage(
            isEInk = true,
            intervalHours = 2,
            isLifecycleResumed = true,
            systemAnimationsEnabled = true,
        ) shouldBe false
        shouldAutoRotateStage(
            isEInk = false,
            intervalHours = 0,
            isLifecycleResumed = true,
            systemAnimationsEnabled = true,
        ) shouldBe false
        shouldAutoRotateStage(
            isEInk = false,
            intervalHours = 2,
            isLifecycleResumed = false,
            systemAnimationsEnabled = true,
        ) shouldBe false
        shouldAutoRotateStage(
            isEInk = false,
            intervalHours = 2,
            isLifecycleResumed = true,
            systemAnimationsEnabled = false,
        ) shouldBe false
        shouldAutoRotateStage(
            isEInk = false,
            intervalHours = 2,
            isLifecycleResumed = true,
            systemAnimationsEnabled = true,
        ) shouldBe true
        shouldAutoRotateStage(
            isEInk = false,
            intervalHours = 2,
            isLifecycleResumed = true,
            systemAnimationsEnabled = true,
            isScrollInProgress = true,
        ) shouldBe false
    }

    @Test
    fun `stage prefers the full discovery pool and falls back to the teaser window`() {
        val pool = listOf(stageItem("Pool 1"), stageItem("Pool 2"))
        val teaser = listOf(stageItem("Teaser 1"))
        resolveStageItems(discoveryPool = pool, discovery = teaser) shouldBe pool
        resolveStageItems(discoveryPool = emptyList(), discovery = teaser) shouldBe teaser
        resolveStageItems(discoveryPool = emptyList(), discovery = emptyList()) shouldBe emptyList()
    }

    private fun stageItem(title: String) = HomeHubDiscoveryItem(
        title = title,
        cleanTitle = title.lowercase(),
        coverUrl = null,
        seedTitle = null,
        reasonPayload = null,
        provider = "test",
        rowType = DiscoveryRowType.TREND,
        mediaType = DiscoveryMediaType.NOVEL,
    )
}
