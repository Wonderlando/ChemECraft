package com.wonderlando.chemecraft;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    // ChemECraft — batch reactor
    public static final ModConfigSpec.DoubleValue REACTION_MODEL_DAYS_PER_TICK = BUILDER
            .comment("Model-days of fermentation simulated per game tick.",
                    "Default ~0.00125 makes a typical batch finish in roughly one in-game day (24000 ticks).")
            .defineInRange("reactionModelDaysPerTick", 0.00125, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue REACTOR_AMBIENT_TEMPERATURE_K = BUILDER
            .comment("Ambient temperature the reactor starts at and cools toward, in kelvin (298 K ~= 25 C).")
            .defineInRange("reactorAmbientTemperatureK", 298.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue REACTOR_COOLING_PER_DAY = BUILDER
            .comment("Newton's-law-of-cooling constant K in dT/dt = -K*(T - T_ambient), per model-day.",
                    "Higher = the reactor loses heat to the environment faster.")
            .defineInRange("reactorCoolingPerDay", 0.2, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue REACTOR_PIPE_TRANSFER_L_PER_DAY = BUILDER
            .comment("Fixed fluid-transfer rate through pipes, in litres per MODEL-day.",
                    "Uses the SAME model clock as the reaction rate constants (see reactionModelDaysPerTick),",
                    "so residence time (tank volume / flow rate) and reaction timescales are comparable.",
                    "At the defaults this drains a full 27 L vessel in roughly two real minutes.")
            .defineInRange("pipeTransferLitersPerDay", 10.0, 0.0, 1.0e9);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(Identifier.parse(itemName));
    }
}
