package com.tsaky.camoucreepers;

import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Creeper;

/**
 * Vanilla creeper renderer (so the charged-creeper swirl, swelling and white flash all keep
 * working) with the skin swapped for the one matching the block it is standing on.
 */
public class CamouCreeperRenderer extends CreeperRenderer {
    public CamouCreeperRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.addLayer(new CamouSpikeLayer(this, new CreeperModel<>(context.bakeLayer(CamouCreepers.SPIKE_LAYER))));
    }

    @Override
    public ResourceLocation getTextureLocation(Creeper creeper) {
        CamouTextures.Entry skin = CamouTextures.forCreeper(creeper);
        return skin == null ? super.getTextureLocation(creeper) : skin.location();
    }

    @Override
    protected RenderType getRenderType(Creeper creeper, boolean bodyVisible, boolean translucent, boolean glowing) {
        CamouTextures.Entry skin = CamouTextures.forCreeper(creeper);
        if (skin == null) {
            return super.getRenderType(creeper, bodyVisible, translucent, glowing);
        }
        ResourceLocation texture = skin.location();
        if (translucent) {
            return RenderType.itemEntityTranslucentCull(texture);
        }
        if (bodyVisible) {
            return skin.translucent() ? RenderType.entityTranslucent(texture) : RenderType.entityCutoutNoCull(texture);
        }
        return glowing ? RenderType.outline(texture) : null;
    }
}
