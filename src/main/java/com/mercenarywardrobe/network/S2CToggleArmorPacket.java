package com.mercenarywardrobe.network;

import com.mercenarywardrobe.data.WardrobeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class S2CToggleArmorPacket {
    private final UUID ownerUUID;
    private final String classId;
    private final boolean hidden;

    public S2CToggleArmorPacket(UUID ownerUUID, String classId, boolean hidden) {
        this.ownerUUID = ownerUUID;
        this.classId = classId;
        this.hidden = hidden;
    }

    public S2CToggleArmorPacket(FriendlyByteBuf buf) {
        this.ownerUUID = buf.readUUID();
        this.classId = buf.readUtf();
        this.hidden = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(ownerUUID);
        buf.writeUtf(classId);
        buf.writeBoolean(hidden);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                WardrobeManager.setRemoteArmor(ownerUUID, classId, hidden);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
