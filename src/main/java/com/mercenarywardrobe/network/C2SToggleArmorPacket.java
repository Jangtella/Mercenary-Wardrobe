package com.mercenarywardrobe.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class C2SToggleArmorPacket {
    private final String classId;
    private final boolean hidden;

    public C2SToggleArmorPacket(String classId, boolean hidden) {
        this.classId = classId;
        this.hidden = hidden;
    }

    public C2SToggleArmorPacket(FriendlyByteBuf buf) {
        this.classId = buf.readUtf();
        this.hidden = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(classId);
        buf.writeBoolean(hidden);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                WardrobeNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),
                        new S2CToggleArmorPacket(sender.getUUID(), classId, hidden));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
