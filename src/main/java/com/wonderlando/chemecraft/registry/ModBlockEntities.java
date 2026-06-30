package com.wonderlando.chemecraft.registry;

import java.util.function.Supplier;

import com.wonderlando.chemecraft.ChemECraft;
import com.wonderlando.chemecraft.block.entity.ReservoirBlockEntity;
import com.wonderlando.chemecraft.block.entity.SinkBlockEntity;
import com.wonderlando.chemecraft.block.entity.TankReactorBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Block entity types registered by ChemECraft, plus their capability wiring. */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ChemECraft.MODID);

    // One stirred-tank block entity type, shared by the CSTR and the batch reactor (they differ only in ports).
    public static final Supplier<BlockEntityType<TankReactorBlockEntity>> TANK_REACTOR =
            BLOCK_ENTITIES.register("tank_reactor",
                    () -> new BlockEntityType<>(TankReactorBlockEntity::new,
                            ModBlocks.CSTR.get(), ModBlocks.BATCH_REACTOR.get()));

    // Testing instruments (no fluid capability needed: they move fluid via transferMixtureTo, not the cap).
    public static final Supplier<BlockEntityType<ReservoirBlockEntity>> RESERVOIR =
            BLOCK_ENTITIES.register("reservoir",
                    () -> new BlockEntityType<>(ReservoirBlockEntity::new, ModBlocks.RESERVOIR.get()));

    public static final Supplier<BlockEntityType<SinkBlockEntity>> SINK =
            BLOCK_ENTITIES.register("sink",
                    () -> new BlockEntityType<>(SinkBlockEntity::new, ModBlocks.SINK.get()));

    private ModBlockEntities() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }

    /** Wire the reactor's fluid tank to the standard fluid capability (both CSTR and batch reactor). */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, TANK_REACTOR.get(), (be, side) -> be.getFluidHandler());
    }
}
