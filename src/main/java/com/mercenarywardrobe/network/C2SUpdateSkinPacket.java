package com.mercenarywardrobe.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class C2SUpdateSkinPacket {
    private final String classId;
    private final int contentId;
    private final boolean slim;
    private final int layerType;

    public C2SUpdateSkinPacket(String classId, int contentId, boolean slim, int layerType) {
        this.classId = classId;
        this.contentId = contentId;
        this.slim = slim;
        this.layerType = layerType;
    }

    public C2SUpdateSkinPacket(String classId, int contentId, boolean slim) {
        this(classId, contentId, slim, 0);
    }

    public C2SUpdateSkinPacket(FriendlyByteBuf buf) {
        this.classId = buf.readUtf();
        this.contentId = buf.readInt();
        this.slim = buf.readBoolean();
        this.layerType = buf.isReadable() ? buf.readVarInt() : 0;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(classId);
        buf.writeInt(contentId);
        buf.writeBoolean(slim);
        buf.writeVarInt(layerType);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                WardrobeNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(),
                        new S2CUpdateSkinPacket(sender.getUUID(), classId, contentId, layerType));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
