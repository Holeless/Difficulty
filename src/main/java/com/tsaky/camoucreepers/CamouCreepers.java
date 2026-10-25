package com.tsaky.camoucreepers;

import com.mojang.logging.LogUtils;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(value = CamouCreepers.MOD_ID, dist = Dist.CLIENT)
public class CamouCreepers {
    public static final String MOD_ID = "camoucreepers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final ModelLayerLocation SPIKE_LAYER =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(MOD_ID, "creeper_spikes"), "main");

    public CamouCreepers(IEventBus modEventBus) {
        modEventBus.addListener(CamouCreepers::registerLayerDefinitions);
        modEventBus.addListener(CamouCreepers::registerRenderers);
        modEventBus.addListener(CamouCreepers::registerReloadListeners);
        NeoForge.EVENT_BUS.addListener(CamouCreepers::onClientTick);
    }

    private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SPIKE_LAYER, () -> CreeperModel.createBodyLayer(new CubeDeformation(1.0F)));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EntityType.CREEPER, CamouCreeperRenderer::new);
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resourceManager -> {
            CamouTextures.clearAll();
            RenderSettings.load(resourceManager);
        });
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        CamouTextures.onClientTick();
    }
}
