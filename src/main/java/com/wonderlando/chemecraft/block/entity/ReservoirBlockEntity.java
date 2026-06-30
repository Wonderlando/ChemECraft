package com.wonderlando.chemecraft.block.entity;

import com.wonderlando.chemecraft.Config;
import com.wonderlando.chemecraft.block.FluidTransport;
import com.wonderlando.chemecraft.menu.ReservoirMenu;
import com.wonderlando.chemecraft.reaction.Species;
import com.wonderlando.chemecraft.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * A testing RESERVOIR: a pure infinite SOURCE with no holdup of its own. It is defined entirely by a feed
 * recipe — a molar concentration (mol/L) per species — and a volumetric flow rate (L/min of solvent). Each
 * tick, while emitting and if a downstream inlet is reachable, it injects that tick's packet directly into
 * the destination ({@code flow × dt} of water plus {@code concentration × flow × dt} of each species) via
 * {@link ReactorBlockEntity#injectFeed}. There is no tank to fill or fraction to draw — the flow rate is the
 * solvent flow, and the concentrations ride on it.
 */
public class ReservoirBlockEntity extends ReactorBlockEntity {
    public static final int FLUID_SLOTS = 6;   // unused holdup; kept for the shared base constructor
    public static final int CAPACITY_MB = 27_000;

    // Display-slot layout: one slot PER SPECIES (concentration, milli-mol/L, indexed by ordinal) first, then
    // the metadata slots. Generic — a new Species automatically gets a slot and a GUI row, no per-species wiring.
    public static final int SPECIES_BASE = 0;
    public static final int SLOT_RATE = SPECIES_BASE + Species.values().length; // flow rate, milli-L/min
    public static final int SLOT_RELEASING = SLOT_RATE + 1;
    public static final int SLOT_HAS_DEST = SLOT_RATE + 2;
    public static final int SLOT_UNIT = SLOT_RATE + 3;
    public static final int DISPLAY_SLOTS = SLOT_RATE + 4;

    // Step sizes for the GUI +/- buttons, per display unit.
    public static final double CONC_STEP = 0.5;   // mol/L
    // Rate step sizes in L/min for each display unit: L/min, L/hr, L/day, L/s
    private static final double[] RATE_STEP_LPM = {1.0, 1.0 / 60.0, 1.0 / 1440.0, 60.0};
    // Conversion factors from L/min to each display unit
    public static final double[] RATE_UNIT_FACTOR = {1.0, 60.0, 1440.0, 1.0 / 60.0};
    public static final String[] RATE_UNIT_NAMES = {"L/min", "L/hr", "L/day", "L/s"};

    // Feed recipe: per-species molar concentration (mol per litre of solvent) + solvent volumetric flow (L/min).
    private final double[] targetConc = new double[Species.values().length];
    private double flowLitersPerMin = 0.0;
    // 0 = L/min, 1 = L/hr, 2 = L/day
    private int rateUnit = 0;

    public ReservoirBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESERVOIR.get(), pos, state, FLUID_SLOTS, CAPACITY_MB);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ReservoirBlockEntity be) {
        be.feedTick(level);
    }

    /** While emitting, inject one tick's worth of the recipe straight into the downstream node. */
    private void feedTick(Level level) {
        ReactorBlockEntity destination = refreshDestination(level);
        if (!isReleasing() || destination == null) {
            return;
        }
        // Solvent flow this tick (L/min → mB/tick), carrying a fractional remainder so the rate stays exact.
        releaseCarryMb += (flowLitersPerMin / 1440.0) * Config.REACTION_MODEL_DAYS_PER_TICK.get() * MB_PER_LITER;
        int waterMb = (int) Math.floor(releaseCarryMb);
        releaseCarryMb -= waterMb;
        if (waterMb <= 0) {
            return;
        }
        double litersThisTick = waterMb / (double) MB_PER_LITER;
        double[] feed = new double[Species.values().length];
        for (Species species : Species.values()) {
            feed[species.ordinal()] = targetConc[species.ordinal()] * litersThisTick;
        }
        destination.injectFeed(waterMb, feed);
    }

    /** Single block, no fixed facing: emit out of whichever face reaches an inlet. */
    @Override
    protected ReactorBlockEntity findOutletDestination(Level level) {
        for (Direction dir : Direction.values()) {
            ReactorBlockEntity dest = FluidTransport.traceToInlet(level, getBlockPos(), dir, this);
            if (dest != null) {
                return dest;
            }
        }
        return null;
    }

    // ---- Configuration (from the GUI) ----

    public void addConcentration(Species species, double deltaMolPerL) {
        int i = species.ordinal();
        targetConc[i] = Math.max(0.0, targetConc[i] + deltaMolPerL);
        changed();
    }

    /** Step the flow rate by one unit in the current display unit. direction = +1 or -1. */
    public void stepRate(int direction) {
        double deltaLPerMin = direction * RATE_STEP_LPM[rateUnit];
        flowLitersPerMin = Math.max(0.0, flowLitersPerMin + deltaLPerMin);
        changed();
    }

    /** Cycle to the next display unit and reset the rate to zero to avoid conversion confusion. */
    public void cycleRateUnit() {
        rateUnit = (rateUnit + 1) % RATE_UNIT_NAMES.length;
        flowLitersPerMin = 0.0;
        changed();
    }

    private void changed() {
        setChanged();
        sync();
    }

    @Override
    protected int displaySlotCount() {
        return DISPLAY_SLOTS;
    }

    @Override
    protected int getDisplaySlot(int index) {
        // SLOT_* depend on Species.values().length, so they aren't constant expressions and can't be switch
        // labels — use an if/else chain. Species concentrations occupy SPECIES_BASE .. SPECIES_BASE+count-1.
        if (index == SLOT_RATE) {
            return (int) Math.round(flowLitersPerMin * 1000.0); // milli-L/min
        }
        if (index == SLOT_RELEASING) {
            return isReleasing() ? 1 : 0;
        }
        if (index == SLOT_HAS_DEST) {
            return hasOutletDestination() ? 1 : 0;
        }
        if (index == SLOT_UNIT) {
            return rateUnit;
        }
        int i = index - SPECIES_BASE; // per-species feed concentration in milli-mol/L
        return (i >= 0 && i < targetConc.length) ? (int) Math.round(targetConc[i] * 1000.0) : 0;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.chemecraft.reservoir");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new ReservoirMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putDouble("flowLitersPerMin", flowLitersPerMin);
        output.putInt("rateUnit", rateUnit);
        for (Species species : Species.values()) {
            output.putDouble("conc_" + species.name(), targetConc[species.ordinal()]);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        flowLitersPerMin = input.getDoubleOr("flowLitersPerMin", 0.0);
        rateUnit = input.getIntOr("rateUnit", 0);
        for (Species species : Species.values()) {
            targetConc[species.ordinal()] = input.getDoubleOr("conc_" + species.name(), 0.0);
        }
    }
}
