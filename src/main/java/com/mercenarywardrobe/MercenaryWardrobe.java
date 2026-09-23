package com.mercenarywardrobe;

import com.mercenarywardrobe.client.MercenaryWardrobeClient;
import com.mercenarywardrobe.client.gui.MercenarySkinHistoryScreen;
import com.mercenarywardrobe.network.WardrobeNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MercenaryWardrobe.MOD_ID)
public class MercenaryWardrobe {
    public static final String MOD_ID = "mercenary_wardrobe";

    public MercenaryWardrobe() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);

        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY, (remoteVersion, isServer) -> true));

        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, screen) -> new MercenarySkinHistoryScreen(screen)));

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> MercenaryWardrobeClient::init);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(WardrobeNetwork::register);
    }
}
