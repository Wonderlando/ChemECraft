package com.wonderlando.chemecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A passive shell block making up the other 26 cells of the batch reactor's 3x3x3 footprint. It holds no
 * state of its own: interactions are forwarded to the controller {@link BatchReactorBlock} (found by a
 * short neighbour scan), and breaking any casing collapses the whole structure.
 */
public class BatchReactorCasingBlock extends Block {
    public BatchReactorCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos controller = BatchReactorBlock.findController(level, pos);
        if (controller == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        return BatchReactorBlock.interact(stack, level, controller, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockPos controller = BatchReactorBlock.findController(level, pos);
            if (controller != null) {
                BatchReactorBlock.openGui(level, controller, player);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Breaking a casing collapses the whole reactor: destroy the controller (which drops the item + clears the rest). */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        BlockPos controller = BatchReactorBlock.findController(level, pos);
        if (controller != null) {
            level.destroyBlock(controller, true, null, 512);
        }
    }
}
