package com.tsaky.camoucreepers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import com.mojang.blaze3d.platform.NativeImage;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CamouTextures {
    public record Entry(ResourceLocation location, boolean translucent, DynamicTexture texture,
                        @Nullable ResourceLocation spikes, @Nullable DynamicTexture spikeTexture) {
    }

    private record Uploaded(ResourceLocation location, DynamicTexture texture) {
    }

    private record Key(BlockState state, int tint) {
    }

    private static final class Memo {
        BlockPos feet;
        long resolvedAt;
        long lastUsed;
        Entry entry;

        Entry lastGood;
    }

    private static final Entry UNUSABLE = new Entry(null, false, null, null, null);

    private static final int MAX_CACHED = 512;

    private static final int REFRESH_TICKS = 10;
    private static final int MEMO_TTL_TICKS = 200;
    private static final int MAX_SCALE = 4;

    private static final int MAX_SEARCH_DOWN = 12;

    private static final Map<Key, Entry> CACHE = new HashMap<>();

    private static final List<Entry> RETIRED = new ArrayList<>();
    private static final Map<UUID, Memo> MEMOS = new HashMap<>();

    private static int[] overlay;
    private static int overlayW;
    private static int overlayH;
    private static boolean overlayLoaded;
    private static int counter;

    private CamouTextures() {
    }

    @Nullable
    public static Entry forCreeper(Creeper creeper) {
        Level level = creeper.level();
        long now = level.getGameTime();
        BlockPos feet = creeper.blockPosition();
        UUID id = creeper.getUUID();

        Memo memo = MEMOS.get(id);
        if (memo != null && memo.feet.equals(feet) && now >= memo.resolvedAt && now - memo.resolvedAt < REFRESH_TICKS) {
            memo.lastUsed = now;
            return memo.entry;
        }

        Entry entry = findSkin(level, creeper);
        if (memo != null && MEMOS.get(id) != memo) {
            memo = null;
        }
        if (entry == null && memo != null) {
            entry = memo.lastGood;
        }
        if (entry == null) {
            entry = searchDown(level, feet);
        }

        memo = MEMOS.computeIfAbsent(id, k -> new Memo());
        memo.feet = feet;
        memo.resolvedAt = now;
        memo.lastUsed = now;
        memo.entry = entry;
        if (entry != null) {
            memo.lastGood = entry;
        }
        return entry;
    }

    @Nullable
    private static Entry findSkin(Level level, Creeper creeper) {
        Entry entry = lookup(level, creeper.blockPosition());
        if (entry != null) {
            return entry;
        }

        AABB box = creeper.getBoundingBox();
        int y = Mth.floor(box.minY - 0.01D);
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX - 1.0E-4D);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ - 1.0E-4D);

        List<BlockPos> cells = new ArrayList<>(4);
        List<Double> overlaps = new ArrayList<>(4);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double ox = Math.min(box.maxX, x + 1) - Math.max(box.minX, x);
                double oz = Math.min(box.maxZ, z + 1) - Math.max(box.minZ, z);
                double area = Math.max(0D, ox) * Math.max(0D, oz);
                int at = 0;
                while (at < overlaps.size() && overlaps.get(at) >= area) {
                    at++;
                }
                overlaps.add(at, area);
                cells.add(at, new BlockPos(x, y, z));
            }
        }
        for (BlockPos cell : cells) {
            entry = lookup(level, cell);
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    @Nullable
    private static Entry searchDown(Level level, BlockPos feet) {
        for (int i = 2; i <= MAX_SEARCH_DOWN; i++) {
            Entry entry = lookup(level, feet.below(i));
            if (entry != null) {
                return entry;
            }
        }
        return null;
    }

    public static void clearAll() {
        MEMOS.clear();
        for (Entry entry : CACHE.values()) {
            if (entry != UNUSABLE) {
                release(entry);
            }
        }
        CACHE.clear();
        for (Entry entry : RETIRED) {
            release(entry);
        }
        RETIRED.clear();
        overlay = null;
        overlayLoaded = false;
    }

    public static void onClientTick() {
        if (!RETIRED.isEmpty()) {
            for (Entry entry : RETIRED) {
                release(entry);
            }
            RETIRED.clear();
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            MEMOS.clear();
        } else if (level.getGameTime() % 100 == 0) {
            long now = level.getGameTime();
            MEMOS.values().removeIf(m -> now - m.lastUsed > MEMO_TTL_TICKS);
        }
    }

    @Nullable
    private static Entry lookup(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || RenderSettings.isIgnored(state)) {
            return null;
        }

        BlockState source = state;
        boolean overridden = false;
        Block replacement = RenderSettings.getOverride(state.getBlock());
        if (replacement != null) {
            source = replacement.defaultBlockState();
            overridden = true;
        }

        int tint = isSnowy(source) ? -1 : TextureMath.quantizeTint(tintFor(source, level, pos));
        Key key = new Key(source, tint);
        Entry entry = CACHE.get(key);
        if (entry == null) {
            entry = create(source, level, pos, tint, overridden);
            if (CACHE.size() >= MAX_CACHED) {
                flush();
            }
            CACHE.put(key, entry);
        }
        return entry == UNUSABLE ? null : entry;
    }

    private static boolean isSnowy(BlockState state) {
        return state.hasProperty(BlockStateProperties.SNOWY) && state.getValue(BlockStateProperties.SNOWY);
    }

    private static int tintFor(BlockState state, Level level, BlockPos pos) {
        try {
            int color = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, 0);
            return color == -1 ? -1 : color & 0xFFFFFF;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private static Entry create(BlockState state, Level level, BlockPos pos, int tint, boolean overridden) {
        try {
            Minecraft mc = Minecraft.getInstance();
            ResourceManager resources = mc.getResourceManager();

            int[] pixels;
            int width;
            int height;
            try (NativeImage block = loadBlockImage(mc, resources, state, level, pos, overridden)) {
                if (block == null) {
                    return UNUSABLE;
                }
                width = block.getWidth();
                height = block.getHeight();
                pixels = readPixels(block);
            }

            int scale = Mth.clamp(Math.min(width, height) / 16, 1, MAX_SCALE);
            int tileSize = 16 * scale;
            int[] tile = TextureMath.toTile(pixels, width, height, tileSize);
            if (TextureMath.coverage(tile) < TextureMath.MIN_COVERAGE) {
                return UNUSABLE;
            }

            TextureMath.Cleaned cleaned = RenderSettings.isSpiky(state)
                    ? TextureMath.cleanSpiky(tile, tileSize)
                    : TextureMath.clean(tile);
            tile = cleaned.pixels();

            loadOverlay(resources);
            int[] composed = TextureMath.compose(tile, tileSize, scale, overlay, overlayW, overlayH, tint);

            int skinW = TextureMath.SKIN_WIDTH * scale;
            int skinH = TextureMath.SKIN_HEIGHT * scale;
            Uploaded main = upload(mc, composed, skinW, skinH);
            Uploaded spikes = null;
            if (cleaned.spikes() != null) {
                try {
                    int[] spikePixels = TextureMath.spikeSkin(cleaned.spikes(), tileSize, scale, tint);
                    spikes = upload(mc, spikePixels, skinW, skinH);
                } catch (RuntimeException e) {
                    release(new Entry(main.location(), false, main.texture(), null, null));
                    throw e;
                }
            }
            return new Entry(main.location(), cleaned.translucent(), main.texture(),
                    spikes == null ? null : spikes.location(), spikes == null ? null : spikes.texture());
        } catch (Exception e) {
            CamouCreepers.LOGGER.warn("Could not build a camouflage texture for {}", state, e);
            return UNUSABLE;
        }
    }

    private static Uploaded upload(Minecraft mc, int[] pixels, int width, int height) {
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, width, height, false);
        try {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setPixelRGBA(x, y, pixels[y * width + x]);
                }
            }
        } catch (RuntimeException e) {
            image.close();
            throw e;
        }
        DynamicTexture texture = new DynamicTexture(image);
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                CamouCreepers.MOD_ID, "dynamic/skin_" + (counter++));
        mc.getTextureManager().register(location, texture);
        return new Uploaded(location, texture);
    }

    @Nullable
    private static NativeImage loadBlockImage(Minecraft mc, ResourceManager resources, BlockState state,
                                              Level level, BlockPos pos, boolean overridden) throws IOException {
        ResourceLocation name;
        if (isSnowy(state)) {
            name = ResourceLocation.withDefaultNamespace("block/snow");
        } else if (state.is(Blocks.GRASS_BLOCK)) {
            name = ResourceLocation.withDefaultNamespace("block/grass_block_top");
        } else {
            name = spriteName(mc, state, level, pos, overridden);
        }
        if (name == null || name.equals(MissingTextureAtlasSprite.getLocation())) {
            return null;
        }

        String path = name.getPath();
        if (!RenderSettings.isSpiky(state) && RenderSettings.shouldReplaceSideWithTop(state)) {
            String top = topVariant(path);
            if (top != null) {
                NativeImage image = readTexture(resources, name.getNamespace(), top);
                if (image != null) {
                    return image;
                }
            }
        }
        return readTexture(resources, name.getNamespace(), path);
    }

    @Nullable
    @SuppressWarnings("deprecation")
    private static ResourceLocation spriteName(Minecraft mc, BlockState state, Level level, BlockPos pos, boolean generic) {
        var shaper = mc.getBlockRenderer().getBlockModelShaper();
        TextureAtlasSprite sprite = generic ? shaper.getParticleIcon(state) : shaper.getTexture(state, level, pos);
        return sprite == null ? null : sprite.contents().name();
    }

    @Nullable
    static String topVariant(String path) {
        for (String suffix : new String[]{"_front_on", "_front", "_side"}) {
            if (path.endsWith(suffix)) {
                return path.substring(0, path.length() - suffix.length()) + "_top";
            }
        }
        return null;
    }

    @Nullable
    private static NativeImage readTexture(ResourceManager resources, String namespace, String path) throws IOException {
        Optional<Resource> resource = resources.getResource(
                ResourceLocation.fromNamespaceAndPath(namespace, "textures/" + path + ".png"));
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream in = resource.get().open()) {
            return NativeImage.read(in);
        }
    }

    private static int[] readPixels(NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out[y * w + x] = image.getPixelRGBA(x, y);
            }
        }
        return out;
    }

    private static void loadOverlay(ResourceManager resources) {
        if (overlayLoaded) {
            return;
        }
        overlayLoaded = true;
        overlay = null;
        try (NativeImage image = readTexture(resources, CamouCreepers.MOD_ID, "creeper_overlay")) {
            if (image != null) {
                overlayW = image.getWidth();
                overlayH = image.getHeight();
                overlay = readPixels(image);
            }
        } catch (IOException e) {
            CamouCreepers.LOGGER.warn("Could not read the creeper overlay texture", e);
        }
    }

    private static void flush() {
        MEMOS.clear();
        for (Entry entry : CACHE.values()) {
            if (entry != UNUSABLE) {
                RETIRED.add(entry);
            }
        }
        CACHE.clear();
    }

    private static void release(Entry entry) {
        release(entry.location(), entry.texture());
        if (entry.spikes() != null) {
            release(entry.spikes(), entry.spikeTexture());
        }
    }

    private static void release(ResourceLocation location, DynamicTexture texture) {
        TextureManager textures = Minecraft.getInstance().getTextureManager();
        try {
            textures.release(location);
        } catch (RuntimeException ignored) {
        }
        try {
            if (texture.getPixels() != null) {
                texture.close();
            }
        } catch (RuntimeException ignored) {
        }
    }
}
