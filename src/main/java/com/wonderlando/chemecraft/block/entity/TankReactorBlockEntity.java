package com.wonderlando.chemecraft.block.entity;

import com.wonderlando.chemecraft.block.ReactorBlock;
import com.wonderlando.chemecraft.menu.ReactorMenu;
import com.wonderlando.chemecraft.reaction.Reaction;
import com.wonderlando.chemecraft.reaction.Species;
import com.wonderlando.chemecraft.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * The concrete stirred-tank reactor block entity, shared by both the CSTR and the batch reactor blocks
 * (they differ only in whether their {@link ReactorBlock} exposes an inlet). It supplies the vessel size,
 * the GUI display values, and the menu; all of the dynamic model lives in {@link ReactorBlockEntity}.
 */
public class TankReactorBlockEntity extends ReactorBlockEntity {
    /** Maximum number of distinct fluids the vessel can hold at once (one shared, well-mixed pool). */
    public static final int FLUID_SLOTS = 6;
    /** Shared working volume across all fluids, in millibuckets (27 buckets). Provisional mapping: 1000 mB = 1 L. */
    public static final int CAPACITY_MB = 27_000;
    /** Fermentable substrate added per wheat item, in grams. */
    public static final double SUBSTRATE_PER_WHEAT_G = 50.0;

    // Display-slot layout: fixed metadata slots first, then one slot PER SPECIES (indexed by ordinal). This is
    // generic — adding a Species automatically gets a slot, so no per-species wiring is needed here or downstream.
    public static final int SLOT_WATER = 0;
    public static final int SLOT_SELECTED = 1;
    public static final int SLOT_TEMP_DK = 2;     // temperature in deci-kelvin (0.1 K precision)
    public static final int SLOT_RELEASING = 3;
    public static final int SLOT_HAS_DEST = 4;
    public static final int SPECIES_BASE = 5;     // species[ordinal] amount in milli-moles starts here
    /** Number of synced display values exposed to the GUI: the metadata slots plus one per species. */
    public static final int DISPLAY_SLOTS = SPECIES_BASE + Species.values().length;

    public TankReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TANK_REACTOR.get(), pos, state, FLUID_SLOTS, CAPACITY_MB);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TankReactorBlockEntity be) {
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
            case SLOT_WATER -> getFluidMb(Fluids.WATER);
            case SLOT_SELECTED -> getSelectedReaction();
            case SLOT_TEMP_DK -> (int) Math.round(temperatureK * 10.0);
            case SLOT_RELEASING -> isReleasing() ? 1 : 0;
            case SLOT_HAS_DEST -> hasOutletDestination() ? 1 : 0;
            default -> {
                int i = index - SPECIES_BASE; // per-species amount in milli-moles, indexed by Species ordinal
                yield (i >= 0 && i < amounts.length) ? (int) Math.round(amounts[i] * 1000.0) : 0;
            }
        };
    }

    @Override
    public Component getDisplayName() {
        boolean cstr = getBlockState().getBlock() instanceof ReactorBlock reactor && reactor.hasInlet();
        return Component.translatable(cstr ? "block.chemecraft.cstr" : "block.chemecraft.batch_reactor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ReactorMenu(containerId, playerInventory, this);
    }
}
