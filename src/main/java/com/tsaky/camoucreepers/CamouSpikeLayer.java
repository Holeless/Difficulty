package com.tsaky.camoucreepers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Creeper;

final class CamouSpikeLayer extends RenderLayer<Creeper, CreeperModel<Creeper>> {
    private final CreeperModel<Creeper> model;

    CamouSpikeLayer(RenderLayerParent<Creeper, CreeperModel<Creeper>> parent, CreeperModel<Creeper> model) {
        super(parent);
        this.model = model;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, Creeper creeper,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (creeper.isInvisible()) {
            return;
        }
        CamouTextures.Entry skin = CamouTextures.forCreeper(creeper);
        if (skin == null || skin.spikes() == null) {
            return;
        }

        model.prepareMobModel(creeper, limbSwing, limbSwingAmount, partialTick);
        this.getParentModel().copyPropertiesTo(model);
        model.setupAnim(creeper, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        float swelling = creeper.getSwelling(partialTick);
        float flash = (int) (swelling * 10.0F) % 2 == 0 ? 0.0F : Mth.clamp(swelling, 0.5F, 1.0F);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(skin.spikes()));
        model.renderToBuffer(poseStack, consumer, packedLight, LivingEntityRenderer.getOverlayCoords(creeper, flash), -1);
    }
}
