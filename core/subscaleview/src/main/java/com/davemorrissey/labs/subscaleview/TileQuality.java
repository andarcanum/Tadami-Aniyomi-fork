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

    /**
     * Skips a separate preview only when full resolution also fits the permanent pixel budget.
     */
    static int previewSample(int width, int height, int maxTileWidth, int maxTileHeight) {
        if (width <= 0 || height <= 0 || maxTileWidth <= 0 || maxTileHeight <= 0) {
            return previewSample(width, height);
        }
        int effectiveMaxW = maxTileWidth > 2 ? maxTileWidth - 2 : maxTileWidth;
        int effectiveMaxH = maxTileHeight > 2 ? maxTileHeight - 2 : maxTileHeight;
        long xTiles = ((long) width + effectiveMaxW - 1) / effectiveMaxW;
        long yTiles = ((long) height + effectiveMaxH - 1) / effectiveMaxH;
        if (xTiles * yTiles <= 2L && (long) width * height <= PREVIEW_PIXELS) {
            return 1;
        }
        return previewSample(width, height);
    }

    static int sampleForScale(float scale) {
        if (!Float.isFinite(scale) || scale <= 0f) return 1;
        int sample = 1;
        while (sample < (1 << 30) && sample * 2.0 * scale <= 1.0) sample *= 2;
        return sample;
    }
}
