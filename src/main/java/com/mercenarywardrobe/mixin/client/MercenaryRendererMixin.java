package com.mercenarywardrobe.mixin.client;

import com.mercenarywardrobe.client.render.layer.MercenaryClothingLayer;
import com.mercenarywardrobe.client.render.layer.MercenaryHairLayer;
import com.mercenarywardrobe.data.WardrobeManager;
import com.robertx22.mine_and_slash.database.data.mercenary.entity.MercenaryEntity;
import com.robertx22.mine_and_slash.database.data.mercenary.render.MercenaryRenderer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MercenaryRenderer.class)
public abstract class MercenaryRendererMixin extends HumanoidMobRenderer<MercenaryEntity, PlayerModel<MercenaryEntity>> {

    public MercenaryRendererMixin(EntityRendererProvider.Context context, PlayerModel<MercenaryEntity> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)V", at = @At("RETURN"), remap = false)
    private void onInit(EntityRendererProvider.Context context, CallbackInfo ci) {
        this.addLayer(new MercenaryClothingLayer(this));
        this.addLayer(new MercenaryHairLayer(this));
    }

    @Inject(method = "getTextureLocation(Lcom/robertx22/mine_and_slash/database/data/mercenary/entity/MercenaryEntity;)Lnet/minecraft/resources/ResourceLocation;", at = @At("HEAD"), cancellable = true, remap = false)
    private void onGetTextureLocation(MercenaryEntity entity, CallbackInfoReturnable<ResourceLocation> cir) {
        ResourceLocation customTexture = WardrobeManager.getMercenaryTexture(entity);
        if (customTexture != null) {
            cir.setReturnValue(customTexture);
        }
    }
}
