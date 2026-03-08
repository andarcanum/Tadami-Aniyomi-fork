package eu.kanade.presentation.more.settings.screen.about

internal enum class AboutEasterEggPhase {
    Idle,
    Primed,
    GlyphRain,
    PageMaterializing,
    PrologueVisible,
    Dismissing,
}

internal enum class AboutEasterEggTapFeedback {
    None,
    Light,
    Primed,
}

internal class AboutEasterEggStateMachine(
    private val requiredPrimarySignals: Int,
    private val primedWindowMs: Long,
    private val tapStreakWindowMs: Long,
) {
    var phase: AboutEasterEggPhase = AboutEasterEggPhase.Idle
        private set

    private var primedUntilMs: Long = Long.MIN_VALUE
    private var logoTapCount: Int = 0
    private var lastLogoTapAtMs: Long = Long.MIN_VALUE

    fun onPrimarySignal(nowMs: Long): AboutEasterEggTapFeedback {
        if (phase == AboutEasterEggPhase.Primed) {
            restartTapSequence(nowMs)
            return AboutEasterEggTapFeedback.None
        }
        if (phase != AboutEasterEggPhase.Idle) return AboutEasterEggTapFeedback.None

        logoTapCount = if (nowMs - lastLogoTapAtMs <= tapStreakWindowMs) {
            logoTapCount + 1
        } else {
            1
        }
        lastLogoTapAtMs = nowMs

        if (logoTapCount >= requiredPrimarySignals) {
            phase = AboutEasterEggPhase.Primed
            primedUntilMs = nowMs + primedWindowMs
            return AboutEasterEggTapFeedback.Primed
        }

        return AboutEasterEggTapFeedback.Light
    }

    fun isPrimedAt(nowMs: Long): Boolean {
        return phase == AboutEasterEggPhase.Primed && nowMs <= primedUntilMs
    }

    fun tick(nowMs: Long) {
        if (phase == AboutEasterEggPhase.Primed && nowMs > primedUntilMs) {
            reset()
        }
    }

    fun onSecondarySignal(nowMs: Long): Boolean {
        tick(nowMs)
        if (!isPrimedAt(nowMs)) return false

        phase = AboutEasterEggPhase.GlyphRain
        return true
    }

    fun onGlyphRainFinished() {
        if (phase == AboutEasterEggPhase.GlyphRain) {
            phase = AboutEasterEggPhase.PageMaterializing
        }
    }

    fun onPageMaterialized() {
        if (phase == AboutEasterEggPhase.PageMaterializing) {
            phase = AboutEasterEggPhase.PrologueVisible
        }
    }

    fun dismiss() {
        if (phase in
            setOf(
                AboutEasterEggPhase.GlyphRain,
                AboutEasterEggPhase.PageMaterializing,
                AboutEasterEggPhase.PrologueVisible,
            )
        ) {
            phase = AboutEasterEggPhase.Dismissing
        }
    }

    fun onDismissFinished() {
        if (phase == AboutEasterEggPhase.Dismissing) {
            reset()
        }
    }

    private fun reset() {
        phase = AboutEasterEggPhase.Idle
        primedUntilMs = Long.MIN_VALUE
        logoTapCount = 0
        lastLogoTapAtMs = Long.MIN_VALUE
    }

    private fun restartTapSequence(nowMs: Long) {
        reset()
        logoTapCount = 1
        lastLogoTapAtMs = nowMs
    }
}
