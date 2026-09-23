package com.mercenarywardrobe.mixin.client;

import com.mercenarywardrobe.client.gui.MercenarySkinHistoryScreen;
import com.robertx22.mine_and_slash.gui.bases.BaseScreen;
import com.robertx22.mine_and_slash.gui.screens.mercenary.MercenaryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MercenaryScreen.class)
public abstract class MercenaryScreenMixin extends BaseScreen {

    public MercenaryScreenMixin(int sizeX, int sizeY) {
        super(sizeX, sizeY);
    }

    private static class WardrobeShortcutButton extends Button {
        private static final ResourceLocation NORMAL_TEX = new ResourceLocation("mercenary_wardrobe", "textures/gui/shortcut_button.png");
        private static final ResourceLocation HOVERED_TEX = new ResourceLocation("mercenary_wardrobe", "textures/gui/shortcut_button_hovered.png");

        public WardrobeShortcutButton(int x, int y, int width, int height, OnPress onPress) {
            super(x, y, width, height, Component.empty(), onPress, DEFAULT_NARRATION);
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            graphics.blit(isHovered ? HOVERED_TEX : NORMAL_TEX, x, y, 24, 24, 0.0F, 0.0F, 64, 64, 64, 64);
            graphics.renderItem(new ItemStack(Items.GOLDEN_CHESTPLATE), x + 4, y + 4);
        }
    }

    @Inject(method = "rebuild", at = @At("RETURN"), remap = false)
    private void onRebuild(CallbackInfo ci) {
        MercenaryScreen screen = (MercenaryScreen) (Object) this;
        AbstractWidget speechWidget = null;

        for (var child : this.children()) {
            if (child instanceof AbstractWidget widget) {
                if (widget.getY() < this.guiTop && widget.getX() > this.guiLeft + this.sizeX - 50) {
                    speechWidget = widget;
                    break;
                }
            }
        }

        int btnW = 24;
        int btnH = 24;
        int speechX = speechWidget != null ? speechWidget.getX() : (this.guiLeft + this.sizeX - 24);
        int speechY = speechWidget != null ? speechWidget.getY() : (this.guiTop - 25);
        int gap = 1;
        int btnX = speechX - btnW - gap;
        int btnY = speechY;

        WardrobeShortcutButton btn = new WardrobeShortcutButton(btnX, btnY, btnW, btnH, b -> {
            String cls = screen.getActiveClassId();
            Minecraft.getInstance().setScreen(new MercenarySkinHistoryScreen(screen, cls));
        });
        btn.setTooltip(Tooltip.create(Component.translatable("mercenary_wardrobe.button.wardrobe")));
        this.publicAddButton(btn);
    }
}
