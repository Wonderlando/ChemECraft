package com.wonderlando.chemecraft.registry;

import java.util.function.Supplier;

import com.wonderlando.chemecraft.ChemECraft;
import com.wonderlando.chemecraft.block.entity.BatchReactorBlockEntity;

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

    public static final Supplier<BlockEntityType<BatchReactorBlockEntity>> BATCH_REACTOR =
            BLOCK_ENTITIES.register("batch_reactor",
                    () -> new BlockEntityType<>(BatchReactorBlockEntity::new, ModBlocks.BATCH_REACTOR.get()));

    private ModBlockEntities() {}

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }

    /** Wire the batch reactor's fluid tank to the standard fluid capability. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, BATCH_REACTOR.get(), (be, side) -> be.getFluidHandler());
    }
}
