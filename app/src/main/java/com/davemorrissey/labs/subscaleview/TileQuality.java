package com.davemorrissey.labs.subscaleview;

/** Pure pixel-budget policy; parent transforms never alter source or layout coordinates. */
final class TileQuality {
    static final long PREVIEW_PIXELS = 262_144L;

    private TileQuality() {}

    static int previewSample(int width, int height) {
        int sample = 1;
        while (((width + (long) sample - 1) / sample) * ((height + (long) sample - 1) / sample) > PREVIEW_PIXELS) {
            sample *= 2;
        }
        return sample;
    }

    static int sampleForScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0f) return 1;
        int sample = 1;
        while (sample < (1 << 30) && sample * 2.0 * scale <= 1.0) sample *= 2;
        return sample;
    }
}
