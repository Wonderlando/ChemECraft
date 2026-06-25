package com.wonderlando.chemecraft;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.wonderlando.chemecraft.datagen.ChemECraftDatagen;
import com.wonderlando.chemecraft.registry.ModBlockEntities;
import com.wonderlando.chemecraft.registry.ModBlocks;
import com.wonderlando.chemecraft.registry.ModCreativeTabs;
import com.wonderlando.chemecraft.registry.ModFluids;
import com.wonderlando.chemecraft.registry.ModItems;
import com.wonderlando.chemecraft.registry.ModMenus;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(ChemECraft.MODID)
public class ChemECraft {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "chemecraft";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public ChemECraft(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the mod's deferred registers to the mod event bus.
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModFluids.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenus.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        // Register block-entity capabilities (the batch reactor's fluid tank).
        modEventBus.addListener(ModBlockEntities::registerCapabilities);

        // Data generation — only runs under the data run configuration.
        modEventBus.addListener(ChemECraftDatagen::gatherData);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ChemECraft) to respond directly to events.
        NeoForge.EVENT_BUS.register(this);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.LOG_DIRT_BLOCK.getAsBoolean()) {
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.MAGIC_NUMBER_INTRODUCTION.get(), Config.MAGIC_NUMBER.getAsInt());

        Config.ITEM_STRINGS.get().forEach((item) -> LOGGER.info("ITEM >> {}", item));
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }
}
