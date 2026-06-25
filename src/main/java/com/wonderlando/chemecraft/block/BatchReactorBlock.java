package com.wonderlando.chemecraft.block;

import com.wonderlando.chemecraft.block.entity.BatchReactorBlockEntity;
import com.wonderlando.chemecraft.reaction.Species;
import com.wonderlando.chemecraft.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
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
 * and runs the selected reaction. Right-click with wheat to charge substrate, a bucket to fill/drain
 * water, an empty bottle to cash out a finished product into a potion, or empty-handed to open the GUI.
 */
public class BatchReactorBlock extends Block implements EntityBlock {
    /** Moles of product consumed per bottle when cashing out. Tunable. */
    private static final double MOLES_PER_BOTTLE = 1.0;

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
        // Cash out a finished product into a themed potion with an empty bottle.
        if (stack.is(Items.GLASS_BOTTLE)) {
            if (!level.isClientSide()) {
                cashOut(reactor, player, stack);
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

    /** Bottle a finished product: ethanol -> Swiftness potion, acetic acid (vinegar) -> Strength potion. */
    private static void cashOut(BatchReactorBlockEntity reactor, Player player, ItemStack bottle) {
        ItemStack potion;
        if (reactor.extract(Species.ETHANOL, MOLES_PER_BOTTLE)) {
            potion = PotionContents.createItemStack(Items.POTION, Potions.SWIFTNESS);
        } else if (reactor.extract(Species.ACETIC_ACID, MOLES_PER_BOTTLE)) {
            potion = PotionContents.createItemStack(Items.POTION, Potions.STRENGTH);
        } else {
            return; // nothing finished to cash out yet
        }
        if (!player.getAbilities().instabuild) {
            bottle.shrink(1);
        }
        if (!player.addItem(potion)) {
            player.drop(potion, false);
        }
    }
}
