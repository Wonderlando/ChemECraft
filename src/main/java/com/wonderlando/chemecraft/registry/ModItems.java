package com.wonderlando.chemecraft.registry;

import com.wonderlando.chemecraft.ChemECraft;
import com.wonderlando.chemecraft.item.WrenchItem;

import net.minecraft.world.item.BlockItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** All items registered by ChemECraft. */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ChemECraft.MODID);

    public static final DeferredItem<BlockItem> CSTR_ITEM =
            ITEMS.registerSimpleBlockItem("cstr", ModBlocks.CSTR);

    public static final DeferredItem<BlockItem> BATCH_REACTOR_ITEM =
            ITEMS.registerSimpleBlockItem("batch_reactor", ModBlocks.BATCH_REACTOR);

    public static final DeferredItem<BlockItem> PIPE_ITEM =
            ITEMS.registerSimpleBlockItem("pipe", ModBlocks.PIPE);

    public static final DeferredItem<BlockItem> SPLITTER_ITEM =
            ITEMS.registerSimpleBlockItem("splitter", ModBlocks.SPLITTER);

    public static final DeferredItem<BlockItem> MIXER_ITEM =
            ITEMS.registerSimpleBlockItem("mixer", ModBlocks.MIXER);

    public static final DeferredItem<BlockItem> RESERVOIR_ITEM =
            ITEMS.registerSimpleBlockItem("reservoir", ModBlocks.RESERVOIR);

    public static final DeferredItem<BlockItem> SINK_ITEM =
            ITEMS.registerSimpleBlockItem("sink", ModBlocks.SINK);

    // A wrench: right-click a pipe to rotate its flow direction; sneak-right-click a face to toggle a connection.
    public static final DeferredItem<WrenchItem> WRENCH =
            ITEMS.registerItem("wrench", WrenchItem::new, props -> props.stacksTo(1));

    private ModItems() {}

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
