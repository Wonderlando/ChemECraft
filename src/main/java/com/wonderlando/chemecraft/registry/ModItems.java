package com.wonderlando.chemecraft.registry;

import com.wonderlando.chemecraft.ChemECraft;

import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** All items registered by ChemECraft. */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ChemECraft.MODID);

    public static final DeferredItem<BlockItem> BATCH_REACTOR_ITEM =
            ITEMS.registerSimpleBlockItem("batch_reactor", ModBlocks.BATCH_REACTOR);

    private ModItems() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
