package com.mercenarywardrobe.client;

import com.mercenarywardrobe.MercenaryWardrobe;
import com.mercenarywardrobe.client.gui.MercenarySkinHistoryScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = MercenaryWardrobe.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MercenaryWardrobeClient {
    public static final KeyMapping OPEN_WARDROBE_KEY = new KeyMapping(
            "key.mercenary_wardrobe.open_wardrobe",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_KP_SUBTRACT,
            "key.categories.mercenary_wardrobe"
    );

    public static void init() {
        MinecraftForge.EVENT_BUS.register(ClientForgeEvents.class);
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_WARDROBE_KEY);
    }

    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                while (OPEN_WARDROBE_KEY.consumeClick()) {
                    Minecraft.getInstance().setScreen(new MercenarySkinHistoryScreen(Minecraft.getInstance().screen));
                }
            }
        }

        @SubscribeEvent
        public static void onLoggingIn(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
            com.mercenarywardrobe.data.WardrobeManager.ensureMissingCaches();
        }
    }
}
