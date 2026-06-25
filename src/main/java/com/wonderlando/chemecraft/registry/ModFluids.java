package com.wonderlando.chemecraft.registry;

import com.wonderlando.chemecraft.ChemECraft;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Fluids registered by ChemECraft. Ethanol is registered "tank-only" for now: a FluidType plus
 * source/flowing fluids so it can accumulate in the reactor and show in the readout. No bucket or
 * world block yet — those arrive with the distillation/output work.
 */
public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, ChemECraft.MODID);
    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, ChemECraft.MODID);

    public static final DeferredHolder<FluidType, FluidType> ETHANOL_TYPE = FLUID_TYPES.register("ethanol",
            () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid.chemecraft.ethanol")
                    .density(789)
                    .viscosity(1200)
                    .canConvertToSource(false)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> ETHANOL = FLUIDS.register("ethanol",
            () -> new BaseFlowingFluid.Source(ethanolProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> ETHANOL_FLOWING = FLUIDS.register("ethanol_flowing",
            () -> new BaseFlowingFluid.Flowing(ethanolProperties()));

    private static BaseFlowingFluid.Properties ethanolProperties() {
        return new BaseFlowingFluid.Properties(ETHANOL_TYPE, ETHANOL, ETHANOL_FLOWING);
    }

    private ModFluids() {}

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }
}
