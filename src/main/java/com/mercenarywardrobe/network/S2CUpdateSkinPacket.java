package com.mercenarywardrobe.network;

import com.mercenarywardrobe.data.WardrobeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class S2CUpdateSkinPacket {
    private final UUID ownerUUID;
    private final String classId;
    private final int contentId;
    private final int layerType;

    public S2CUpdateSkinPacket(UUID ownerUUID, String classId, int contentId, int layerType) {
        this.ownerUUID = ownerUUID;
        this.classId = classId;
        this.contentId = contentId;
        this.layerType = layerType;
    }

    public S2CUpdateSkinPacket(UUID ownerUUID, String classId, int contentId) {
        this(ownerUUID, classId, contentId, 0);
    }

    public S2CUpdateSkinPacket(FriendlyByteBuf buf) {
        this.ownerUUID = buf.readUUID();
        this.classId = buf.readUtf();
        this.contentId = buf.readInt();
        this.layerType = buf.isReadable() ? buf.readVarInt() : 0;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(ownerUUID);
        buf.writeUtf(classId);
        buf.writeInt(contentId);
        buf.writeVarInt(layerType);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                if (layerType == 1) {
                    WardrobeManager.setRemoteClothing(ownerUUID, classId, contentId);
                } else if (layerType == 2) {
                    WardrobeManager.setRemoteHair(ownerUUID, classId, contentId);
                } else {
                    WardrobeManager.setRemoteSkin(ownerUUID, classId, contentId);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
