package com.tsaky.camoucreepers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class RenderSettings {
    private static final ResourceLocation FILE =
            ResourceLocation.fromNamespaceAndPath(CamouCreepers.MOD_ID, "json/render_settings.json");

    private static volatile Settings current = Settings.EMPTY;

    private RenderSettings() {
    }

    private record Settings(
            Set<Block> ignored, List<TagKey<Block>> ignoredTags,
            Map<Block, Block> overrides,
            Set<Block> keepSide, List<TagKey<Block>> keepSideTags,
            Set<Block> spiky, List<TagKey<Block>> spikyTags) {
        static final Settings EMPTY = new Settings(Set.of(), List.of(), Map.of(), Set.of(), List.of(), Set.of(), List.of());
    }

    static void load(ResourceManager resourceManager) {
        Set<Block> ignored = new HashSet<>();
        List<TagKey<Block>> ignoredTags = new ArrayList<>();
        Map<Block, Block> overrides = new HashMap<>();
        Set<Block> keepSide = new HashSet<>();
        List<TagKey<Block>> keepSideTags = new ArrayList<>();
        Set<Block> spiky = new HashSet<>();
        List<TagKey<Block>> spikyTags = new ArrayList<>();

        try (Reader reader = resourceManager.openAsReader(FILE)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            readBlockList(root, "ignore_list", ignored, ignoredTags);
            readBlockList(root, "do_not_replace_side_with_top_list", keepSide, keepSideTags);
            readBlockList(root, "spiky_list", spiky, spikyTags);

            if (root.has("override_list")) {
                for (JsonElement element : root.getAsJsonArray("override_list")) {
                    JsonArray pair = element.getAsJsonArray();
                    Block from = lookupBlock(pair.get(0).getAsString());
                    Block to = lookupBlock(pair.get(1).getAsString());
                    if (from != null && to != null) {
                        overrides.put(from, to);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            CamouCreepers.LOGGER.error("Could not read {}; using empty settings", FILE, e);
        }

        current = new Settings(ignored, ignoredTags, overrides, keepSide, keepSideTags, spiky, spikyTags);
        CamouCreepers.LOGGER.info("Camouflaged Creepers: ignoring {} blocks + {} tags, overriding {} blocks",
                ignored.size(), ignoredTags.size(), overrides.size());
    }

    private static void readBlockList(JsonObject root, String key, Set<Block> blocks, List<TagKey<Block>> tags) {
        if (!root.has(key)) {
            return;
        }
        for (JsonElement element : root.getAsJsonArray(key)) {
            String id = element.getAsString();
            if (id.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(id.substring(1));
                if (tag != null) {
                    tags.add(TagKey.create(Registries.BLOCK, tag));
                }
            } else {
                Block block = lookupBlock(id);
                if (block != null) {
                    blocks.add(block);
                }
            }
        }
    }

    private static Block lookupBlock(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            return null;
        }
        return BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
    }

    static boolean isIgnored(BlockState state) {
        Settings s = current;
        if (s.ignored.contains(state.getBlock())) {
            return true;
        }
        for (TagKey<Block> tag : s.ignoredTags) {
            if (state.is(tag)) {
                return true;
            }
        }
        return false;
    }

    static Block getOverride(Block block) {
        return current.overrides.get(block);
    }

    static boolean isSpiky(BlockState state) {
        Settings s = current;
        if (s.spiky.contains(state.getBlock())) {
            return true;
        }
        for (TagKey<Block> tag : s.spikyTags) {
            if (state.is(tag)) {
                return true;
            }
        }
        return false;
    }

    static boolean shouldReplaceSideWithTop(BlockState state) {
        Settings s = current;
        if (s.keepSide.contains(state.getBlock())) {
            return false;
        }
        for (TagKey<Block> tag : s.keepSideTags) {
            if (state.is(tag)) {
                return false;
            }
        }
        return true;
    }
}
