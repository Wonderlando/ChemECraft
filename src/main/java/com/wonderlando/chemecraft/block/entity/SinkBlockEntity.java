package com.wonderlando.chemecraft.block.entity;

import com.wonderlando.chemecraft.Config;
import com.wonderlando.chemecraft.menu.SinkMenu;
import com.wonderlando.chemecraft.reaction.Species;
import com.wonderlando.chemecraft.registry.ModBlockEntities;

import java.util.Arrays;

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
    // Synced GUI values: rate + water (both L/s x100), then one slot PER SPECIES (mol/s x100) indexed by
    // ordinal. Generic — a new Species gets a slot automatically, no per-species wiring here or in the screen.
    public static final int SLOT_RATE = 0;
    public static final int SLOT_WATER = 1;
    public static final int SPECIES_BASE = 2;
    public static final int DISPLAY_SLOTS = SPECIES_BASE + Species.values().length;
    private static final int WINDOW_TICKS = 20; // average over ~1 second for a stable reading

    private int windowTicks = 0;
    private double rateLPerSec = 0.0;
    private double waterLPerSec = 0.0;
    private final double[] speciesMolPerSec = new double[Species.values().length];
    // Running totals over the current window. The sink drains EVERY tick (not just at window end) so it can
    // never fill to capacity and throttle its feed; these accumulate what passed through for the rate average.
    private double windowFluidMb = 0.0;
    private double windowWaterMb = 0.0;
    private final double[] windowSpeciesMol = new double[Species.values().length];

    public SinkBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SINK.get(), pos, state, FLUID_SLOTS, CAPACITY_MB);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SinkBlockEntity be) {
        be.measure();
    }

    /** Each tick: tally what arrived and drain it (so the sink never throttles its feed); average per window. */
    private void measure() {
        // Record this tick's arrivals, then discard EVERY tick — a sink that fills to capacity would cap
        // transferMixtureTo and back flow up into whatever feeds it (that was the "volume keeps growing" bug).
        windowFluidMb += totalFluidMb();
        windowWaterMb += getFluidMb(Fluids.WATER);
        for (Species species : Species.values()) {
            windowSpeciesMol[species.ordinal()] += getMoles(species);
        }
        discardContents();

        if (++windowTicks < WINDOW_TICKS) {
            return;
        }
        double seconds = MODEL_SECONDS_PER_TICK * Config.SIMULATION_SPEED.get() * windowTicks;
        if (seconds > 0.0) {
            rateLPerSec = (windowFluidMb / (double) MB_PER_LITER) / seconds;
            waterLPerSec = (windowWaterMb / (double) MB_PER_LITER) / seconds;
            for (Species species : Species.values()) {
                speciesMolPerSec[species.ordinal()] = windowSpeciesMol[species.ordinal()] / seconds;
            }
        }
        windowFluidMb = 0.0;
        windowWaterMb = 0.0;
        Arrays.fill(windowSpeciesMol, 0.0);
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
            case SLOT_RATE -> (int) Math.round(rateLPerSec * 100.0);
            case SLOT_WATER -> (int) Math.round(waterLPerSec * 100.0);
            default -> {
                int i = index - SPECIES_BASE; // per-species rate in mol/s, indexed by Species ordinal
                yield (i >= 0 && i < speciesMolPerSec.length) ? (int) Math.round(speciesMolPerSec[i] * 100.0) : 0;
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
