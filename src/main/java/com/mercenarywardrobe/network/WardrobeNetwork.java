package com.mercenarywardrobe.network;

import com.mercenarywardrobe.MercenaryWardrobe;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class WardrobeNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MercenaryWardrobe.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION)
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, C2SUpdateSkinPacket.class,
                C2SUpdateSkinPacket::encode, C2SUpdateSkinPacket::new, C2SUpdateSkinPacket::handle);
        CHANNEL.registerMessage(id++, S2CUpdateSkinPacket.class,
                S2CUpdateSkinPacket::encode, S2CUpdateSkinPacket::new, S2CUpdateSkinPacket::handle);
        CHANNEL.registerMessage(id++, C2SToggleArmorPacket.class,
                C2SToggleArmorPacket::encode, C2SToggleArmorPacket::new, C2SToggleArmorPacket::handle);
        CHANNEL.registerMessage(id++, S2CToggleArmorPacket.class,
                S2CToggleArmorPacket::encode, S2CToggleArmorPacket::new, S2CToggleArmorPacket::handle);
    }

    public static void sendToServer(Object msg) {
        try {
            if (Minecraft.getInstance().getConnection() != null) {
                CHANNEL.sendToServer(msg);
            }
        } catch (Throwable ignored) {
        }
    }
}
