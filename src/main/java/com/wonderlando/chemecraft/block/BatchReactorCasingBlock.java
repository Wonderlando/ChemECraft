package com.wonderlando.chemecraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A passive shell block making up the other 26 cells of the batch reactor's 3x3x3 footprint. Interactions
 * are forwarded to the controller {@link BatchReactorBlock} (found by a short neighbour scan), and breaking
 * any casing collapses the whole structure. Two casing cells are tagged with a {@link #PORT} by the
 * controller so they show the inlet/outlet rings; {@code HORIZONTAL_FACING} orients those markers to the front.
 */
public class BatchReactorCasingBlock extends Block {
    /** Which port (if any) this casing cell represents. */
    public enum PortType implements StringRepresentable {
        NONE("none"), INLET("inlet"), OUTLET("outlet");

        private final String name;

        PortType(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** NONE for plain shell cells; INLET/OUTLET on the two cells the controller designates as ports. */
    public static final EnumProperty<PortType> PORT = EnumProperty.create("port", PortType.class);

    public BatchReactorCasingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(PORT, PortType.NONE)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PORT, BlockStateProperties.HORIZONTAL_FACING);
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
