package com.wonderlando.chemecraft.block;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Merge node: many inlets feeding one outlet. {@link #FACING} marks the single OUTLET face (shown by the
 * ▲ ring); every other connected face is an inlet. Flow is an ideal zero-holdup junction — the transport
 * trace passes straight through (any inlet face → the outlet face), so several runs can converge on one
 * downstream inlet and that destination receives the sum: per species, out = Σ in. Right-click with a
 * wrench to rotate which face is the outlet.
 */
public class MixerBlock extends FluidHubBlock {
    /** The single OUTLET face (any of the six directions). */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public MixerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Default the outlet to the way the player is looking; the wrench can re-aim it afterwards.
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }

    /** Right-click: rotate the outlet face to the next of the six directions. */
    @Override
    public void wrench(Level level, BlockPos pos, BlockState state, Player player, Direction face, boolean sneaking) {
        Direction[] all = Direction.values();
        Direction next = all[(state.getValue(FACING).ordinal() + 1) % all.length];
        level.setBlockAndUpdate(pos, state.setValue(FACING, next));
        if (player != null) {
            player.sendOverlayMessage(Component.literal("Mixer outlet: " + next.getName()));
        }
    }
}
