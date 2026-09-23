package com.mercenarywardrobe.client.render.layer;

import com.mercenarywardrobe.data.WardrobeManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.robertx22.mine_and_slash.database.data.mercenary.entity.MercenaryEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class MercenaryHairLayer extends RenderLayer<MercenaryEntity, PlayerModel<MercenaryEntity>> {
    private final PlayerModel<MercenaryEntity> model;

    public MercenaryHairLayer(RenderLayerParent<MercenaryEntity, PlayerModel<MercenaryEntity>> parent) {
        super(parent);
        ModelPart part = LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(0.125F), false), 64, 64).bakeRoot();
        this.model = new PlayerModel<>(part, false);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, MercenaryEntity entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;

        ResourceLocation texture = WardrobeManager.getMercenaryHairTexture(entity);
        if (texture == null) return;

        this.getParentModel().copyPropertiesTo(this.model);
        this.model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        this.model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
    }
}
