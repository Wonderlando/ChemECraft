package com.wonderlando.chemecraft.block;

import com.wonderlando.chemecraft.block.entity.BatchReactorBlockEntity;
import com.wonderlando.chemecraft.reaction.Species;
import com.wonderlando.chemecraft.registry.ModBlockEntities;
import com.wonderlando.chemecraft.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * The batch reactor controller block: the bottom-centre cell of a 3x3x3 footprint, the other 26 cells of
 * which are {@link BatchReactorCasingBlock}. It hosts the {@link BatchReactorBlockEntity} (fluid vessel +
 * reaction sim). Right-click any cell with wheat to charge substrate, a bucket to fill/drain water, an
 * empty bottle to cash out a finished product into a potion, or empty-handed to open the GUI.
 */
public class BatchReactorBlock extends Block implements EntityBlock {
    /** Moles of product consumed per bottle when cashing out. Tunable. */
    private static final double MOLES_PER_BOTTLE = 1.0;

    // 3x3x3 footprint. The controller is the bottom-FRONT-centre cell (the one you click to place); the
    // structure extends BEHIND it (the player's facing direction) and upward, one cell to each side.
    // HORIZONTAL_FACING stores that "back" direction so the casing cells can always be recomputed.
    private static final int DEPTH = 3;  // cells along the back direction: controller + 2 behind
    private static final int HEIGHT = 3; // cells upward: controller layer + 2 above

    public BatchReactorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
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

    // ---- Multiblock placement / teardown ----

    /** Cancel placement (return null) unless the full 3x3x3 region behind the click position is clear. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction back = context.getHorizontalDirection();
        if (!regionClear(context.getLevel(), context.getClickedPos(), back)) {
            return null;
        }
        return this.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, back);
    }

    /** After the controller is placed, materialise the 26 casing cells behind and above it. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            Direction back = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            BlockState casing = ModBlocks.BATCH_REACTOR_CASING.get().defaultBlockState();
            forEachCasingCell(pos, back, cell -> {
                if (level.getBlockState(cell).canBeReplaced()) {
                    level.setBlock(cell, casing, Block.UPDATE_ALL);
                }
            });
        }
    }

    /** Breaking the controller tears down all of its casing cells (without dropping anything for them). */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Direction back = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        forEachCasingCell(pos, back, cell -> {
            if (level.getBlockState(cell).getBlock() instanceof BatchReactorCasingBlock) {
                level.removeBlock(cell, false);
            }
        });
    }

    /** True when every casing cell of the region anchored at {@code controller} (extending {@code back}) is replaceable. */
    static boolean regionClear(LevelReader level, BlockPos controller, Direction back) {
        boolean[] clear = {true};
        forEachCasingCell(controller, back, cell -> {
            if (!level.getBlockState(cell).canBeReplaced()) {
                clear[0] = false;
            }
        });
        return clear[0];
    }

    /**
     * Locate the controller that owns the casing at {@code casingPos} by scanning the box of cells in which a
     * controller could sit and checking which one's region actually contains this casing; null if none.
     */
    static BlockPos findController(BlockGetter level, BlockPos casingPos) {
        for (int dy = -(HEIGHT - 1); dy <= 0; dy++) {
            for (int dx = -(DEPTH - 1); dx <= DEPTH - 1; dx++) {
                for (int dz = -(DEPTH - 1); dz <= DEPTH - 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos candidate = casingPos.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(candidate);
                    if (state.getBlock() instanceof BatchReactorBlock
                            && regionContains(candidate, state.getValue(BlockStateProperties.HORIZONTAL_FACING), casingPos)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /** Whether {@code target} is one of the casing cells of the controller at {@code controller} facing {@code back}. */
    private static boolean regionContains(BlockPos controller, Direction back, BlockPos target) {
        boolean[] found = {false};
        forEachCasingCell(controller, back, cell -> {
            if (cell.equals(target)) {
                found[0] = true;
            }
        });
        return found[0];
    }

    /**
     * Run {@code action} on each of the 26 casing cells: the 3x3x3 block whose front-bottom-centre is the
     * controller, extending {@code back} (depth) and upward, with width spanning one cell to each side.
     */
    private static void forEachCasingCell(BlockPos controller, Direction back, java.util.function.Consumer<BlockPos> action) {
        Direction side = back.getClockWise();
        for (int h = 0; h < HEIGHT; h++) {
            for (int d = 0; d < DEPTH; d++) {
                for (int w = -1; w <= 1; w++) {
                    if (d == 0 && w == 0 && h == 0) {
                        continue; // the controller cell itself
                    }
                    action.accept(controller.relative(back, d).relative(side, w).above(h));
                }
            }
        }
    }

    // ---- Interaction (shared by the controller and its casing cells) ----

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND; // empty hand -> open the GUI
        }
        return interact(stack, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            openGui(level, pos, player);
        }
        return InteractionResult.SUCCESS;
    }

    /** Apply a held-item interaction to the reactor whose controller is at {@code controllerPos}. */
    static InteractionResult interact(ItemStack stack, Level level, BlockPos controllerPos,
                                      Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(controllerPos) instanceof BatchReactorBlockEntity reactor)) {
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
            if (FluidUtil.interactWithFluidHandler(player, hand, level, controllerPos, hit.getDirection(), transaction)) {
                transaction.commit();
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    /** Open the reactor GUI for the controller at {@code controllerPos} (server-side). */
    static void openGui(Level level, BlockPos controllerPos, Player player) {
        if (level.getBlockEntity(controllerPos) instanceof BatchReactorBlockEntity reactor) {
            player.openMenu(reactor, buf -> buf.writeBlockPos(controllerPos));
        }
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
