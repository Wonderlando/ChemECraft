package com.wonderlando.chemecraft.block;

import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Merge node: many inlets feeding one outlet. For now it is geometry only — a solid cube that pipes plug
 * into so several runs can join. The inlet/outlet faces and mixing behaviour are configured later via GUI.
 */
public class MixerBlock extends FluidHubBlock {
    public MixerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
