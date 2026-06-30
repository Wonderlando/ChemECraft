package com.wonderlando.chemecraft.registry;

import com.wonderlando.chemecraft.ChemECraft;
import com.wonderlando.chemecraft.block.MixerBlock;
import com.wonderlando.chemecraft.block.PipeBlock;
import com.wonderlando.chemecraft.block.ReactorBlock;
import com.wonderlando.chemecraft.block.ReactorCasingBlock;
import com.wonderlando.chemecraft.block.ReservoirBlock;
import com.wonderlando.chemecraft.block.SinkBlock;
import com.wonderlando.chemecraft.block.SplitterBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** All blocks registered by ChemECraft. */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ChemECraft.MODID);

    // Continuously Stirred Tank Reactor: a stirred vessel with an inlet (left) and outlet (right) for through-flow.
    public static final DeferredBlock<ReactorBlock> CSTR = BLOCKS.registerBlock("cstr",
            p -> new ReactorBlock(p, true),
            p -> p.mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL));

    // Batch reactor: the same stirred vessel but with NO inlet — you charge it by hand, react, then release.
    public static final DeferredBlock<ReactorBlock> BATCH_REACTOR = BLOCKS.registerBlock("batch_reactor",
            p -> new ReactorBlock(p, false),
            p -> p.mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL));

    // The shell cells that make up the other 26 blocks of a reactor's 3x3x3 footprint (shared by both reactors).
    // No item: only ever placed/removed as part of the structure by the controller block.
    public static final DeferredBlock<ReactorCasingBlock> REACTOR_CASING = BLOCKS.registerBlock("reactor_casing",
            ReactorCasingBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.5f).sound(SoundType.METAL));

    // A thin fluid pipe (#696865). Not a full cube, connects to pipes/reactors, with a flow-direction arrow.
    public static final DeferredBlock<PipeBlock> PIPE = BLOCKS.registerBlock("pipe",
            PipeBlock::new,
            p -> p.mapColor(MapColor.STONE).strength(1.5f).sound(SoundType.METAL).noOcclusion());

    // Branch/merge hubs: solid machine cubes pipes plug into. A splitter fans one run out; a mixer joins many.
    public static final DeferredBlock<SplitterBlock> SPLITTER = BLOCKS.registerBlock("splitter",
            SplitterBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.5f).sound(SoundType.METAL));

    public static final DeferredBlock<MixerBlock> MIXER = BLOCKS.registerBlock("mixer",
            MixerBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.5f).sound(SoundType.METAL));

    // Testing instruments: a configurable infinite SOURCE (reservoir) and a measuring DRAIN (sink).
    public static final DeferredBlock<ReservoirBlock> RESERVOIR = BLOCKS.registerBlock("reservoir",
            ReservoirBlock::new,
            p -> p.mapColor(MapColor.COLOR_BLUE).strength(2.0f).sound(SoundType.METAL));

    public static final DeferredBlock<SinkBlock> SINK = BLOCKS.registerBlock("sink",
            SinkBlock::new,
            p -> p.mapColor(MapColor.COLOR_RED).strength(2.0f).sound(SoundType.METAL));

    private ModBlocks() {}

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
