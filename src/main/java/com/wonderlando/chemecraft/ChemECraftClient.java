package com.wonderlando.chemecraft;

import com.wonderlando.chemecraft.client.ReactorScreen;
import com.wonderlando.chemecraft.client.ReservoirScreen;
import com.wonderlando.chemecraft.client.SinkScreen;
import com.wonderlando.chemecraft.registry.ModMenus;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

// This class will not load on dedicated servers. Accessing client side code from here is safe.
@Mod(value = ChemECraft.MODID, dist = Dist.CLIENT)
// You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
@EventBusSubscriber(modid = ChemECraft.MODID, value = Dist.CLIENT)
public class ChemECraftClient {
    public ChemECraftClient(ModContainer container) {
        // Allows NeoForge to create a config screen for this mod's configs.
        // The config screen is accessed by going to the Mods screen > clicking on your mod > clicking on config.
        // Do not forget to add translations for your config options to the en_us.json file.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        // Some client setup code
        ChemECraft.LOGGER.info("HELLO FROM CLIENT SETUP");
        ChemECraft.LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
    }

    @SubscribeEvent
    static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.REACTOR.get(), ReactorScreen::new);
        event.register(ModMenus.RESERVOIR.get(), ReservoirScreen::new);
        event.register(ModMenus.SINK.get(), SinkScreen::new);
    }
}
