package com.wonderlando.chemecraft.registry;

import com.wonderlando.chemecraft.ChemECraft;
import com.wonderlando.chemecraft.block.BatchReactorBlock;
import com.wonderlando.chemecraft.block.BatchReactorCasingBlock;
import com.wonderlando.chemecraft.block.PipeBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** All blocks registered by ChemECraft. */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ChemECraft.MODID);

    // The batch reactor: a sturdy metal vessel backed by a BlockEntity with a fluid tank.
    public static final DeferredBlock<BatchReactorBlock> BATCH_REACTOR = BLOCKS.registerBlock("batch_reactor",
            BatchReactorBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL));

    // The shell cells that make up the other 26 blocks of the reactor's 3x3x3 footprint. No item: it is
    // only ever placed (and removed) as part of the reactor structure by the controller block.
    public static final DeferredBlock<BatchReactorCasingBlock> BATCH_REACTOR_CASING = BLOCKS.registerBlock("batch_reactor_casing",
            BatchReactorCasingBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL));

    // A thin fluid pipe (#696865). Not a full cube, connects to pipes/reactors, with a flow-direction arrow.
    public static final DeferredBlock<PipeBlock> PIPE = BLOCKS.registerBlock("pipe",
            PipeBlock::new,
            p -> p.mapColor(MapColor.STONE).strength(1.5f).sound(SoundType.METAL).noOcclusion());

    private ModBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
