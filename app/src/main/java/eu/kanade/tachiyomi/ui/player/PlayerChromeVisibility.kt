package eu.kanade.tachiyomi.ui.player

import tachiyomi.domain.custombuttons.model.CustomButton

internal fun resolveVisibleCustomButton(
    showCustomButtons: Boolean,
    customButton: CustomButton?,
): CustomButton? {
    return if (showCustomButtons) customButton else null
}
