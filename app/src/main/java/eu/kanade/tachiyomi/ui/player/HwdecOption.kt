package eu.kanade.tachiyomi.ui.player

/**
 * Resolves the mpv `hwdec` option from the hardware decoding preferences.
 *
 * `hwdec=auto` only enables whitelisted "actively supported" hwaccels (vaapi,
 * videotoolbox, d3d11va, …); MediaCodec is not part of that whitelist on Android,
 * so `auto` silently leaves playback on software decoding and low-end SoCs stutter
 * on HEVC-10bit/AV1 content (see the mpv manual and
 * https://github.com/mpv-android/mpv-android/issues/1088). `mediacodec-copy` (`HW`) is the
 * default Android hardware path: decoded frames are copied back to system RAM, which
 * keeps the software filter paths (`vf`, deband, subtitles) and 10-bit output working.
 * When the user explicitly selects `HW+` ([preferZeroCopy] = true), `mediacodec` zero-copy
 * decoding is used instead.
 */
@JvmOverloads
fun buildHwdecOption(
    tryHWDecoding: Boolean,
    preferZeroCopy: Boolean = false,
): String {
    return when {
        !tryHWDecoding -> Decoder.SW.value
        preferZeroCopy -> Decoder.HWPlus.value
        else -> Decoder.HW.value
    }
}
