package com.mercenarywardrobe.mixin.client;

import com.mercenarywardrobe.data.SkinClassifier;
import com.mercenarywardrobe.data.WardrobeSkinEntry;
import com.mercenarywardrobe.data.WardrobeStorage;
import com.mercenarywardrobe.network.C2SUpdateSkinPacket;
import com.mercenarywardrobe.network.WardrobeNetwork;
import com.mojang.blaze3d.systems.RenderSystem;
import forge.net.mca.client.gui.SkinLibraryScreen;
import forge.net.mca.client.gui.immersive_library.SkinCache;
import forge.net.mca.client.gui.immersive_library.types.LiteContent;
import forge.net.mca.client.resources.SkinPorter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(SkinLibraryScreen.class)
public abstract class SkinLibraryScreenMixin extends Screen {

    private static final ResourceLocation SWORD_ICON = new ResourceLocation("minecraft", "textures/item/iron_sword.png");

    protected SkinLibraryScreenMixin(Component title) {
        super(title);
    }

    private static class MercenaryApplyButton extends Button {
        public MercenaryApplyButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int iconSize = 11;
            int drawX = getX() + (width - iconSize) / 2;
            int drawY = getY() + (height - iconSize) / 2;

            if (isHovered) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.8f);
                graphics.blit(SWORD_ICON, drawX - 1, drawY, iconSize, iconSize, 0.0f, 0.0f, 16, 16, 16, 16);
                graphics.blit(SWORD_ICON, drawX + 1, drawY, iconSize, iconSize, 0.0f, 0.0f, 16, 16, 16, 16);
                graphics.blit(SWORD_ICON, drawX, drawY - 1, iconSize, iconSize, 0.0f, 0.0f, 16, 16, 16, 16);
                graphics.blit(SWORD_ICON, drawX, drawY + 1, iconSize, iconSize, 0.0f, 0.0f, 16, 16, 16, 16);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }

            graphics.blit(SWORD_ICON, drawX, drawY, iconSize, iconSize, 0.0f, 0.0f, 16, 16, 16, 16);
        }
    }

    @Inject(method = "drawControls(Lforge/net/mca/client/gui/immersive_library/types/LiteContent;ZII)V", at = @At("TAIL"), remap = false)
    private void onDrawControls(LiteContent content, boolean isDownloaded, int x, int y, CallbackInfo ci) {
        int btnSize = isDownloaded ? 20 : 16;
        int maxRight = Integer.MIN_VALUE;
        for (var child : this.children()) {
            if (child instanceof AbstractWidget widget) {
                if (widget.getY() == y && Math.abs(widget.getX() - x) < 60) {
                    maxRight = Math.max(maxRight, widget.getX() + widget.getWidth());
                }
            }
        }
        int btnX = (maxRight == Integer.MIN_VALUE) ? (x - btnSize / 2) : (maxRight + 2);

        Button mercButton = new MercenaryApplyButton(btnX, y, btnSize, btnSize, Component.empty(), b -> {
            try {
                SkinCache.sync(content);
                boolean isSlim = content.tags() != null && content.tags().contains("slim");
                String skinType = null;
                try {
                    var opt = SkinCache.getImage(content);
                    if (opt.isPresent()) {
                        isSlim = isSlim || SkinPorter.isSlimFormat(opt.get());
                        skinType = SkinClassifier.classify(opt.get());
                    }
                } catch (Throwable ignored) {
                }

                if (skinType == null || skinType.isEmpty()) {
                    if (content.tags() != null) {
                        if (content.tags().contains("clothing")) skinType = "clothing";
                        else if (content.tags().contains("hair")) skinType = "hair";
                    }
                }
                if (skinType == null) skinType = "skin";

                WardrobeSkinEntry entry = new WardrobeSkinEntry(
                        content.contentid(),
                        content.title(),
                        content.username(),
                        isSlim,
                        System.currentTimeMillis(),
                        skinType
                );
                WardrobeStorage.getInstance().addHistory(entry);

                String targetClass = "fighter";
                try {
                    var live = com.robertx22.mine_and_slash.database.data.mercenary.ClientMercenary.get();
                    if (live != null && live.getClassId() != null) {
                        targetClass = live.getClassId().toLowerCase(Locale.ROOT);
                    }
                } catch (Throwable ignored) {
                }

                if (Minecraft.getInstance().level != null) {
                    int layerType = 0;
                    if (skinType.equals("clothing")) {
                        layerType = 1;
                        WardrobeStorage.getInstance().setActiveClothing(targetClass, entry);
                    } else if (skinType.equals("hair")) {
                        layerType = 2;
                        WardrobeStorage.getInstance().setActiveHair(targetClass, entry);
                    } else {
                        WardrobeStorage.getInstance().setActiveSkin(targetClass, entry);
                    }
                    WardrobeNetwork.sendToServer(new C2SUpdateSkinPacket(targetClass, entry.contentId(), entry.slim(), layerType));
                }
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().gui.getChat().addMessage(Component.literal("§a[Mercenary Wardrobe] Saved " + skinType + " '" + content.title() + "' to Wardrobe!"));
                }
            } catch (Throwable ignored) {
            }
        });
        mercButton.setTooltip(Tooltip.create(Component.literal("Apply to Mercenary")));
        this.addRenderableWidget(mercButton);
    }
}
