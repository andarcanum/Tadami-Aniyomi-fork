package eu.kanade.tachiyomi.ui.player

/**
 * Resolves the mpv `hwdec` option from the "try hardware decoding" preference.
 *
 * `hwdec=auto` only enables whitelisted "actively supported" hwaccels (vaapi,
 * videotoolbox, d3d11va, …); MediaCodec is not part of that whitelist on Android,
 * so `auto` silently leaves playback on software decoding and low-end SoCs stutter
 * on HEVC-10bit/AV1 content (see the mpv manual and
 * https://github.com/mpv-android/mpv-android/issues/1088). `mediacodec-copy` is the
 * safe Android hardware path: decoded frames are copied back to system RAM, which
 * keeps the software filter paths (`vf`, deband, subtitles) working. Plain
 * `mediacodec` is intentionally not used here because it forces RGB conversion and
 * downgrades 10-bit output to 8-bit.
 */
fun buildHwdecOption(tryHWDecoding: Boolean): String {
    return if (tryHWDecoding) Decoder.HW.value else Decoder.SW.value
}
