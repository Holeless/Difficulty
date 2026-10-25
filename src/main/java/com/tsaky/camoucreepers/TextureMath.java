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

    /** Face colour = block's average colour times this (a dark version of the camouflage colours). */
    static final float FACE_SHADE = 0.2F;
    /** The original overlay is faint (max ~57% black); make the face a bit stronger now that it is coloured. */
    static final float FACE_ALPHA_BOOST = 1.35F;
    /** If at least this fraction of pixels is semi-transparent the texture is truly translucent (glass, ice). */
    static final float TRANSLUCENT_FRACTION = 0.25F;

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

    /**
     * A tile after alpha clean-up.
     *
     * @param spikes spikes that should stick out of the creeper (same size as {@code pixels}, transparent
     *               everywhere except the spike pixels), or null when the block has none
     */
    record Cleaned(int[] pixels, boolean translucent, int[] spikes) {
    }

    /**
     * Makes a normal block tile safe to wrap around a creeper:
     * <ul>
     *   <li>If only a few pixels are semi-transparent (anti-aliased edges, stray pixels) they are snapped to
     *       fully opaque or to a hole. Otherwise a couple of odd pixels would switch the whole creeper to
     *       translucent rendering, which sorts its faces wrongly and looks glitchy.</li>
     *   <li>Holes (leaf gaps...) are kept: the creeper is see-through there, like the block itself.</li>
     *   <li>Genuinely translucent textures (stained glass, ice, water) are left as they are.</li>
     * </ul>
     */
    static Cleaned clean(int[] tile) {
        int partial = 0;
        for (int p : tile) {
            int a = p >>> 24;
            if (a >= 32 && a < 247) {
                partial++;
            }
        }
        if (partial / (float) tile.length >= TRANSLUCENT_FRACTION) {
            return new Cleaned(tile.clone(), true, null);
        }

        int[] out = tile.clone();
        for (int i = 0; i < out.length; i++) {
            int a = out[i] >>> 24;
            if (a >= 247) {
                continue;
            }
            out[i] = a >= 128 ? (0xFF000000 | (out[i] & 0xFFFFFF)) : 0;
        }
        return new Cleaned(out, false, null);
    }

    /**
     * Cleans the tile of a spiky block (cactus). Its texture is a solid body with a see-through margin
     * where single spine pixels poke out. Here the spines are split off into their own tile (they are drawn
     * as a slightly larger second layer so they stick out of the creeper), and the gaps they leave are filled
     * with the colour of the nearest body pixel, so the creeper itself is completely solid.
     *
     * <p>A spine is any opaque pixel in a column or row that is mostly empty; this works at any resolution.
     *
     * @param size the tile is {@code size x size}
     */
    static Cleaned cleanSpiky(int[] tile, int size) {
        int[] colSolid = new int[size];
        int[] rowSolid = new int[size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if ((tile[y * size + x] >>> 24) >= 128) {
                    colSolid[x]++;
                    rowSolid[y]++;
                }
            }
        }

        int[] body = new int[tile.length];
        int[] spikes = new int[tile.length];
        boolean anyBody = false;
        boolean anySpike = false;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int i = y * size + x;
                if ((tile[i] >>> 24) < 128) {
                    continue;
                }
                int opaque = 0xFF000000 | (tile[i] & 0xFFFFFF);
                if (colSolid[x] * 2 < size || rowSolid[y] * 2 < size) {
                    spikes[i] = opaque;
                    anySpike = true;
                } else {
                    body[i] = opaque;
                    anyBody = true;
                }
            }
        }
        if (!anyBody) {
            return clean(tile); // nothing solid to build a body from
        }
        fillHoles(body, size);
        return new Cleaned(body, false, anySpike ? spikes : null);
    }

    /** Fills every transparent pixel with the colour of the nearest opaque one (the tile wraps around). */
    private static void fillHoles(int[] px, int size) {
        int[] queue = new int[px.length];
        int head = 0, tail = 0;
        for (int i = 0; i < px.length; i++) {
            if ((px[i] >>> 24) != 0) {
                queue[tail++] = i;
            }
        }
        while (head < tail) {
            int i = queue[head++];
            int x = i % size, y = i / size;
            int[] next = {
                    y * size + (x + 1) % size,
                    y * size + (x + size - 1) % size,
                    ((y + 1) % size) * size + x,
                    ((y + size - 1) % size) * size + x
            };
            for (int j : next) {
                if ((px[j] >>> 24) == 0) {
                    px[j] = px[i];
                    queue[tail++] = j;
                }
            }
        }
    }

    /** Alpha-weighted average colour of a tile, as opaque ABGR. */
    static int averageColor(int[] tile) {
        long sumA = 0, sumR = 0, sumG = 0, sumB = 0;
        for (int p : tile) {
            int a = p >>> 24;
            sumA += a;
            sumR += (long) (p & 0xFF) * a;
            sumG += (long) ((p >> 8) & 0xFF) * a;
            sumB += (long) ((p >> 16) & 0xFF) * a;
        }
        if (sumA == 0) {
            return 0xFF000000;
        }
        int r = (int) (sumR / sumA), g = (int) (sumG / sumA), b = (int) (sumB / sumA);
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }

    /** Multiplies the colour channels of an opaque ABGR colour. */
    static int shade(int abgr, float factor) {
        int r = clamp((abgr & 0xFF) * factor);
        int g = clamp(((abgr >> 8) & 0xFF) * factor);
        int b = clamp(((abgr >> 16) & 0xFF) * factor);
        return 0xFF000000 | (b << 16) | (g << 8) | r;
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
        // The overlay only provides the *shape* of the face/shading (its alpha). Its colour (black) is replaced
        // by a dark version of the block's own colours so the creeper blends in instead of having a black face.
        int faceColor = shade(averageColor(tile), FACE_SHADE) & 0xFFFFFF;
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int base = tile[(y % tileSize) * tileSize + (x % tileSize)];
                int top = 0;
                if (overlay != null) {
                    int o = overlay[(y * overlayH / h) * overlayW + (x * overlayW / w)];
                    int oa = Math.min(255, Math.round((o >>> 24) * FACE_ALPHA_BOOST));
                    top = (oa << 24) | faceColor;
                }
                out[y * w + x] = over(top, base, tr, tg, tb);
            }
        }
        return out;
    }

    /**
     * Builds the skin of the spike layer: transparent except for the spine pixels. The tile is not just
     * repeated; its pixels are scattered with a shear (a bijection on 16x16 blocks, so the spine density is
     * exactly the texture's) so every face of the creeper gets spines instead of only the faces that happen to
     * line up with the tile's margin columns.
     *
     * @return skin pixels, {@code (64*scale) * (32*scale)} ABGR ints
     */
    static int[] spikeSkin(int[] spikeTile, int tileSize, int scale, int tint) {
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
                int bx = x / scale, by = y / scale;
                int tx = Math.floorMod(bx + 5 * by, 16) * scale + x % scale;
                int ty = Math.floorMod(3 * bx + 2 * by, 16) * scale + y % scale;
                int p = spikeTile[ty * tileSize + tx];
                if ((p >>> 24) == 0) {
                    continue;
                }
                out[y * w + x] = (p & 0xFF000000)
                        | (clamp(((p >> 16) & 0xFF) * tb) << 16)
                        | (clamp(((p >> 8) & 0xFF) * tg) << 8)
                        | clamp((p & 0xFF) * tr);
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
