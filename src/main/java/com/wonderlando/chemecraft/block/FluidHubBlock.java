package com.wonderlando.chemecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base for the branch/merge nodes (mixer, splitter): a solid machine cube that pipes connect to on any of
 * its six faces. Pipes themselves stay limited to one inlet and one outlet, so a hub is the only place a
 * line can branch or merge.
 *
 * <p>Geometry only for now: a hub is purely a connection point that pulls in the arms of the pipes around
 * it. Flow semantics — which faces are inlets vs outlets, and how throughput is divided — arrive later with
 * the configuration GUI.
 */
public abstract class FluidHubBlock extends Block implements PipeConnectable {
    protected FluidHubBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** When placed, pull every adjacent pipe's arm toward us so existing runs hook up without a wrench. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) {
            return;
        }
        for (Direction dir : Direction.values()) {
            // The neighbour pipe's face pointing back at us is the opposite of the way we look toward it.
            PipeBlock.connect(level, pos.relative(dir), dir.getOpposite());
        }
    }

    /** When removed, release the arms of the pipes that were pointing at us. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        for (Direction dir : Direction.values()) {
            PipeBlock.disconnect(level, pos.relative(dir), dir.getOpposite());
        }
    }
}
