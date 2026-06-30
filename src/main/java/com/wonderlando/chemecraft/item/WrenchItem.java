package com.wonderlando.chemecraft.item;

import com.wonderlando.chemecraft.block.FluidHubBlock;
import com.wonderlando.chemecraft.block.PipeBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A wrench for adjusting fluid blocks in place. Right-click a pipe to rotate its flow direction (the arrow)
 * or sneak-right-click a pipe face to toggle a connection; right-click a mixer/splitter hub to re-aim its
 * single (outlet/inlet) side. Other blocks are left alone.
 */
public class WrenchItem extends Item {
    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof PipeBlock pipe) {
            if (!level.isClientSide()) {
                pipe.wrench(level, pos, state, context.getPlayer(), context.getClickedFace(), context.isSecondaryUseActive());
            }
            return InteractionResult.SUCCESS;
        }
        if (state.getBlock() instanceof FluidHubBlock hub) {
            if (!level.isClientSide()) {
                hub.wrench(level, pos, state, context.getPlayer(), context.getClickedFace(), context.isSecondaryUseActive());
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
