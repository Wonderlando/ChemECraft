package com.wonderlando.chemecraft.block;

import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Split node: one inlet feeding many outlets. For now it is geometry only — a solid cube that pipes plug
 * into so a run can fan out. The inlet/outlet faces and how flow is divided are configured later via GUI.
 */
public class SplitterBlock extends FluidHubBlock {
    public SplitterBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }
}
