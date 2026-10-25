package com.tsaky.camoucreepers;

import com.mojang.logging.LogUtils;
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

/**
 * Camouflaged Creepers: creepers take on the texture of the block they stand on.
 * Client-only; it is never loaded on a dedicated server and works on servers without the mod.
 */
@Mod(value = CamouCreepers.MOD_ID, dist = Dist.CLIENT)
public class CamouCreepers {
    public static final String MOD_ID = "camoucreepers";
    public static final Logger LOGGER = LogUtils.getLogger();

    public CamouCreepers(IEventBus modEventBus) {
        modEventBus.addListener(CamouCreepers::registerRenderers);
        modEventBus.addListener(CamouCreepers::registerReloadListeners);
        NeoForge.EVENT_BUS.addListener(CamouCreepers::onClientTick);
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
