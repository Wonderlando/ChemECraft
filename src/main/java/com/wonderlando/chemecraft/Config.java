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
    public static final ModConfigSpec.DoubleValue SIMULATION_SPEED = BUILDER
            .comment("Simulation speed multiplier. 1.0 = REAL TIME: in-game seconds equal real seconds.",
                    "(Minecraft runs 20 ticks/s, so one tick advances the chemistry by 0.05 s.) Rate constants",
                    "are quoted per real second, residence time tau = V/Q is in real seconds, etc. Raise this to",
                    "fast-forward long runs — e.g. 60 makes one real second cover one model minute.")
            .defineInRange("simulationSpeed", 1.0, 0.0, 1.0e6);

    public static final ModConfigSpec.DoubleValue REACTOR_AMBIENT_TEMPERATURE_K = BUILDER
            .comment("Ambient temperature the reactor starts at and cools toward, in kelvin (298 K ~= 25 C).")
            .defineInRange("reactorAmbientTemperatureK", 298.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue REACTOR_COOLING_PER_SECOND = BUILDER
            .comment("Newton's-law-of-cooling constant K in dT/dt = -K*(T - T_ambient), per model-SECOND.",
                    "In real time (simulationSpeed=1) the default 0.005/s is a ~200 s (~3 min) cooling time",
                    "constant. Higher = the reactor loses heat to the environment faster.")
            .defineInRange("reactorCoolingPerSecond", 0.005, 0.0, 1.0e6);

    public static final ModConfigSpec.DoubleValue REACTOR_OUTLET_FLOW_L_PER_MIN = BUILDER
            .comment("Rate fluid leaves a reactor's outlet, in litres per real minute.",
                    "Pipes are passive: whatever rate flows in at the outlet is delivered unchanged to the",
                    "destination (flow in = flow out). Converted internally to the model clock (L/day) using",
                    "1440 min/day. At the default 0.1 L/min (~6 L/hr) a full 27 L vessel drains in ~4.5 hours.")
            .defineInRange("reactorOutletFlowLitersPerMin", 0.1, 0.0, 1.0e9);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(Identifier.parse(itemName));
    }
}
