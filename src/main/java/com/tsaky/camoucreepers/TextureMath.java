package com.tsaky.camoucreepers;

/**
 * Pure pixel maths for building camouflaged creeper skins. Everything here works on
 * packed ABGR ints (the layout used by NativeImage's RGBA format: a&lt;&lt;24 | b&lt;&lt;16 | g&lt;&lt;8 | r)
 * and has no Minecraft dependencies, so it is trivially testable.
 */
final class TextureMath {
    /** Size of the vanilla creeper skin, in pixels. */
    static final int SKIN_WIDTH = 64;
    static final int SKIN_HEIGHT = 32;

    /** Block textures with less visible area than this (plants, panes, rails...) are not used as camouflage. */
    static final float MIN_COVERAGE = 0.4F;

    private TextureMath() {
    }

    /**
     * Resamples the first square frame of an image (animated textures are tall strips of frames)
     * to a {@code tile x tile} square. Box-filters when shrinking (so HD packs use the whole
     * texture, not just the top-left corner) and uses nearest-neighbour when enlarging.
     * Colour is averaged weighted by alpha so transparent pixels do not darken the edges.
     */
    static int[] toTile(int[] pixels, int width, int height, int tile) {
        int size = Math.min(width, height);
        int[] out = new int[tile * tile];
        for (int ty = 0; ty < tile; ty++) {
            int y0 = ty * size / tile;
            int y1 = Math.max(y0 + 1, (ty + 1) * size / tile);
            for (int tx = 0; tx < tile; tx++) {
                int x0 = tx * size / tile;
                int x1 = Math.max(x0 + 1, (tx + 1) * size / tile);
                long sumA = 0, sumR = 0, sumG = 0, sumB = 0;
                int count = 0;
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        int p = pixels[y * width + x];
                        int a = p >>> 24;
                        sumA += a;
                        sumR += (long) (p & 0xFF) * a;
                        sumG += (long) ((p >> 8) & 0xFF) * a;
                        sumB += (long) ((p >> 16) & 0xFF) * a;
                        count++;
                    }
                }
                int a = (int) Math.round(sumA / (double) count);
                int r = 0, g = 0, b = 0;
                if (sumA > 0) {
                    r = (int) Math.round(sumR / (double) sumA);
                    g = (int) Math.round(sumG / (double) sumA);
                    b = (int) Math.round(sumB / (double) sumA);
                }
                out[ty * tile + tx] = (a << 24) | (b << 16) | (g << 8) | r;
            }
        }
        return out;
    }

    /** Fraction of pixels that are reasonably visible. */
    static float coverage(int[] tile) {
        int visible = 0;
        for (int p : tile) {
            if ((p >>> 24) >= 32) {
                visible++;
            }
        }
        return visible / (float) tile.length;
    }

    /** True if any pixel is genuinely semi-transparent (glass, ice, water...), i.e. needs alpha blending. */
    static boolean hasPartialAlpha(int[] tile) {
        for (int p : tile) {
            int a = p >>> 24;
            if (a > 8 && a < 247) {
                return true;
            }
        }
        return false;
    }

    /**
     * Reduces the number of distinct tints (biome colours blend smoothly, which would otherwise
     * create a new texture for almost every block position). {@code -1} means "no tint".
     */
    static int quantizeTint(int rgb) {
        if (rgb == -1) {
            return -1;
        }
        return (rgb & 0xF8F8F8) | 0x040404;
    }

    /**
     * Builds the creeper skin: the block tile repeated across the whole skin, the creeper
     * overlay (face and shading) composited on top, then multiplied by the tint.
     *
     * @param tile    block texture resampled to {@code tileSize x tileSize}
     * @param scale   skin resolution multiplier; skin is {@code 64*scale x 32*scale}
     * @param overlay creeper overlay pixels, may be null
     * @param tint    0xRRGGBB tint or -1 for none
     * @return skin pixels, {@code (64*scale) * (32*scale)} ABGR ints
     */
    static int[] compose(int[] tile, int tileSize, int scale, int[] overlay, int overlayW, int overlayH, int tint) {
        int w = SKIN_WIDTH * scale;
        int h = SKIN_HEIGHT * scale;
        float tr = 1F, tg = 1F, tb = 1F;
        if (tint != -1) {
            tr = ((tint >> 16) & 0xFF) / 255F;
            tg = ((tint >> 8) & 0xFF) / 255F;
            tb = (tint & 0xFF) / 255F;
        }
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int base = tile[(y % tileSize) * tileSize + (x % tileSize)];
                int top = 0;
                if (overlay != null) {
                    top = overlay[(y * overlayH / h) * overlayW + (x * overlayW / w)];
                }
                out[y * w + x] = over(top, base, tr, tg, tb);
            }
        }
        return out;
    }

    /** Standard "source over" compositing of {@code top} on {@code base}, then tinting. */
    private static int over(int top, int base, float tr, float tg, float tb) {
        float ta = (top >>> 24) / 255F;
        float ba = (base >>> 24) / 255F;
        float a = ta + ba * (1F - ta);
        if (a <= 0F) {
            return 0;
        }
        float bw = ba * (1F - ta);
        float r = (((top & 0xFF) * ta) + ((base & 0xFF) * bw)) / a * tr;
        float g = ((((top >> 8) & 0xFF) * ta) + (((base >> 8) & 0xFF) * bw)) / a * tg;
        float b = ((((top >> 16) & 0xFF) * ta) + (((base >> 16) & 0xFF) * bw)) / a * tb;
        return (clamp(a * 255F) << 24) | (clamp(b) << 16) | (clamp(g) << 8) | clamp(r);
    }

    private static int clamp(float v) {
        int i = Math.round(v);
        return i < 0 ? 0 : Math.min(i, 255);
    }
}
