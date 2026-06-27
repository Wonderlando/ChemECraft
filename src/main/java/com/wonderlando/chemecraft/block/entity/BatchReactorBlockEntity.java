package com.wonderlando.chemecraft.block.entity;

import com.wonderlando.chemecraft.menu.BatchReactorMenu;
import com.wonderlando.chemecraft.reaction.Reaction;
import com.wonderlando.chemecraft.reaction.Species;
import com.wonderlando.chemecraft.registry.ModBlockEntities;
import com.wonderlando.chemecraft.registry.ModFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * The batch reactor: a closed, well-mixed vessel (water in, ethanol out) that advances the selected
 * mass-action {@link Reaction}. It is the simplest {@link ReactorBlockEntity} — a reactor with no
 * convective transport (closed vessel), so it inherits the shared dynamic model unchanged and only
 * supplies its vessel size and the values its GUI displays.
 */
public class BatchReactorBlockEntity extends ReactorBlockEntity {
    /** Maximum number of distinct fluids the vessel can hold at once (one shared, well-mixed pool). */
    public static final int FLUID_SLOTS = 6;
    /** Shared working volume across all fluids, in millibuckets (27 buckets). Provisional mapping: 1000 mB = 1 L. */
    public static final int CAPACITY_MB = 27_000;
    /** Fermentable substrate added per wheat item, in grams. */
    public static final double SUBSTRATE_PER_WHEAT_G = 50.0;
    /** Number of synced display values exposed to the GUI. */
    public static final int DISPLAY_SLOTS = 10;

    public BatchReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BATCH_REACTOR.get(), pos, state, FLUID_SLOTS, CAPACITY_MB);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BatchReactorBlockEntity be) {
        be.simulate(level);
        be.tickTransport(level);
    }

    @Override
    protected int displaySlotCount() {
        return DISPLAY_SLOTS;
    }

    @Override
    protected int getDisplaySlot(int index) {
        return switch (index) {
            case 0 -> getFluidMb(Fluids.WATER);
            case 1 -> getFluidMb(ModFluids.ETHANOL.get());
            case 2 -> (int) Math.round(amounts[Species.SUBSTRATE.ordinal()] * 1000.0);
            case 3 -> (int) Math.round(amounts[Species.BIOMASS.ordinal()] * 1000.0);
            case 4 -> (int) Math.round(amounts[Species.CARBON_DIOXIDE.ordinal()] * 1000.0);
            case 5 -> (int) Math.round(amounts[Species.ACETIC_ACID.ordinal()] * 1000.0);
            case 6 -> getSelectedReaction();
            case 7 -> (int) Math.round(temperatureK * 10.0); // deci-kelvin (0.1 K precision)
            case 8 -> isReleasing() ? 1 : 0;
            case 9 -> hasOutletDestination() ? 1 : 0;
            default -> 0;
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.chemecraft.batch_reactor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BatchReactorMenu(containerId, playerInventory, this);
    }
}
