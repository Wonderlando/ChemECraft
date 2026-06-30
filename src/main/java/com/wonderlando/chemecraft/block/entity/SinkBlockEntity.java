package com.wonderlando.chemecraft.block.entity;

import com.wonderlando.chemecraft.Config;
import com.wonderlando.chemecraft.menu.SinkMenu;
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
 * A testing SINK: a destination that accepts flow on any face, measures the rate and composition of what
 * arrives over a short window, and then discards it. It reuses the reactor's tank/transfer machinery (so a
 * source can {@code transferMixtureTo} it) but runs no reaction — each window it reads the accumulated
 * inflow, exposes it as display values for the GUI, and empties itself.
 */
public class SinkBlockEntity extends ReactorBlockEntity {
    public static final int FLUID_SLOTS = 6;
    public static final int CAPACITY_MB = 27_000;
    // Synced GUI values: rate + water (both L/day x100), then one slot PER SPECIES (mol/day x100) indexed by
    // ordinal. Generic — a new Species gets a slot automatically, no per-species wiring here or in the screen.
    public static final int SLOT_RATE = 0;
    public static final int SLOT_WATER = 1;
    public static final int SPECIES_BASE = 2;
    public static final int DISPLAY_SLOTS = SPECIES_BASE + Species.values().length;
    private static final int WINDOW_TICKS = 20; // average over ~1 second for a stable reading

    private int windowTicks = 0;
    private double rateLPerDay = 0.0;
    private double waterLPerDay = 0.0;
    private final double[] speciesMolPerDay = new double[Species.values().length];

    public SinkBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SINK.get(), pos, state, FLUID_SLOTS, CAPACITY_MB);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SinkBlockEntity be) {
        be.measure();
    }

    /** Accumulate inflow for a window, then convert it to per-model-day rates and discard the contents. */
    private void measure() {
        if (++windowTicks < WINDOW_TICKS) {
            return;
        }
        double days = Config.REACTION_MODEL_DAYS_PER_TICK.get() * windowTicks;
        if (days > 0.0) {
            rateLPerDay = (totalFluidMb() / (double) MB_PER_LITER) / days;
            waterLPerDay = (getFluidMb(Fluids.WATER) / (double) MB_PER_LITER) / days;
            for (Species species : Species.values()) {
                speciesMolPerDay[species.ordinal()] = getMoles(species) / days;
            }
        }
        empty(); // discard everything received this window
        windowTicks = 0;
        setChanged();
    }

    @Override
    protected int displaySlotCount() {
        return DISPLAY_SLOTS;
    }

    @Override
    protected int getDisplaySlot(int index) {
        return switch (index) {
            case SLOT_RATE -> (int) Math.round(rateLPerDay * 100.0);
            case SLOT_WATER -> (int) Math.round(waterLPerDay * 100.0);
            default -> {
                int i = index - SPECIES_BASE; // per-species rate in mol/day, indexed by Species ordinal
                yield (i >= 0 && i < speciesMolPerDay.length) ? (int) Math.round(speciesMolPerDay[i] * 100.0) : 0;
            }
        };
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.chemecraft.sink");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SinkMenu(containerId, playerInventory, this);
    }
}
