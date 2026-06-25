package com.wonderlando.chemecraft.block;

import com.wonderlando.chemecraft.block.entity.BatchReactorBlockEntity;
import com.wonderlando.chemecraft.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The batch reactor block. Hosts a {@link BatchReactorBlockEntity} that holds a multi-fluid vessel
 * and runs the fermentation. Right-click with wheat to charge substrate, with a bucket to fill/drain
 * water, or empty-handed for a contents readout.
 */
public class BatchReactorBlock extends Block implements EntityBlock {
    public BatchReactorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BatchReactorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null; // the reaction is simulated server-side only
        }
        return createTickerHelper(type, ModBlockEntities.BATCH_REACTOR.get(), BatchReactorBlockEntity::serverTick);
    }

    @SuppressWarnings("unchecked")
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> given, BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker) {
        return expected == given ? (BlockEntityTicker<A>) ticker : null;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND; // empty hand -> contents readout
        }
        if (!(level.getBlockEntity(pos) instanceof BatchReactorBlockEntity reactor)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        // Charge fermentable substrate from wheat.
        if (stack.is(Items.WHEAT)) {
            if (!level.isClientSide()) {
                reactor.addSubstrate(BatchReactorBlockEntity.SUBSTRATE_PER_WHEAT_G);
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }
        // Otherwise try a bucket fill/drain against the fluid capability, inside a transaction.
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hit.getDirection(), transaction)) {
                transaction.commit();
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof BatchReactorBlockEntity reactor) {
            player.openMenu(reactor, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.SUCCESS;
    }
}
