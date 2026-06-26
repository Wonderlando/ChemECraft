package com.wonderlando.chemecraft.block.entity;

import com.wonderlando.chemecraft.Config;
import com.wonderlando.chemecraft.reaction.Reaction;
import com.wonderlando.chemecraft.reaction.ReactionRegistry;
import com.wonderlando.chemecraft.reaction.Species;
import com.wonderlando.chemecraft.registry.ModFluids;

import java.util.Arrays;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * Common base for all chemical reactors. Holds one shared, well-mixed vessel of fluids plus the dissolved
 * reaction state (molar amounts per {@link Species}) and a bulk temperature, and runs the shared dynamic
 * model each tick: mass-action (optionally reversible, Arrhenius) kinetics, an exothermic energy balance,
 * and Newton's-law cooling, with offline catch-up after a chunk reload.
 *
 * <p>The integrator computes volume and thermal mass <em>per substep</em> and calls {@link #advect} before
 * each one, so a continuous reactor (e.g. a CSTR) can subclass this and inject convective feed/effluent
 * terms into the FULL transient balances — no steady-state assumption. A {@code BatchReactorBlockEntity}
 * is simply a reactor whose {@link #advect} is a no-op.
 */
public abstract class ReactorBlockEntity extends BlockEntity implements MenuProvider {
    /** Specific heat of water, J/(g*K). Water (1 mB ~= 1 g) is the vessel's thermal mass. */
    protected static final double CP_WATER = 4.184;

    private static final double SEED_BIOMASS_G_PER_L = 0.5; // auto-seeded yeast inoculum
    private static final int MAX_CATCHUP_SUBSTEPS = 2000;
    private static final double MAX_SUBSTEP_DAYS = 0.05;

    /** Reaction state: molar amounts (mol) per species, indexed by {@link Species#ordinal()}. */
    protected final double[] amounts = new double[Species.values().length];
    /** Bulk temperature of the (well-mixed) vessel contents, in kelvin. Starts at ambient (298 K). */
    protected double temperatureK = 298.0;

    private long lastGameTime = Long.MIN_VALUE;
    // The reaction the user selected for this reactor (ASPEN-style); NONE = idle until one is picked.
    private int selectedReaction = ReactionRegistry.NONE;

    private final int capacityMb;
    private final FluidStacksResourceHandler tank;

    protected ReactorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                                 int fluidSlots, int capacityMb) {
        super(type, pos, state);
        this.capacityMb = capacityMb;
        this.tank = new FluidStacksResourceHandler(fluidSlots, capacityMb) {
            @Override
            protected int getCapacity(int index, FluidResource resource) {
                int usedElsewhere = 0;
                for (int i = 0; i < size(); i++) {
                    if (i != index) {
                        usedElsewhere += getAmountAsInt(i);
                    }
                }
                return ReactorBlockEntity.this.capacityMb - usedElsewhere;
            }

            @Override
            public boolean isValid(int index, FluidResource resource) {
                // Water is the solvent fed in; product liquids are placed by the reaction itself.
                if (resource.isEmpty() || resource.getFluid() == Fluids.WATER) {
                    return true;
                }
                for (Species product : ReactionRegistry.LIQUID_SPECIES) {
                    if (resource.getFluid() == fluidFor(product)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            protected void onContentsChanged(int index, FluidStack oldStack) {
                setChanged();
                if (level != null && !level.isClientSide()) {
                    if (totalFluidMb() == 0) {
                        Arrays.fill(amounts, 0.0); // vessel fully drained: clear the dissolved species too
                    }
                    level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
                }
            }
        };
    }

    // ---- Shared accessors ----

    public ResourceHandler<FluidResource> getFluidHandler() {
        return tank;
    }

    public double getMoles(Species species) {
        return amounts[species.ordinal()];
    }

    public double getGrams(Species species) {
        return amounts[species.ordinal()] * species.molarMass();
    }

    public double getTemperatureK() {
        return temperatureK;
    }

    public int getSelectedReaction() {
        return selectedReaction;
    }

    /** Maximum shared working volume of the vessel, in mB. */
    protected int capacityMb() {
        return capacityMb;
    }

    /** A live, int-encoded view of the values shown in the GUI (synced via the menu's data slots). */
    public ContainerData getDisplayData() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return getDisplaySlot(index);
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return displaySlotCount();
            }
        };
    }

    /** Number of synced display values this reactor exposes to its menu. */
    protected abstract int displaySlotCount();

    /** The int-encoded value for display slot {@code index} (decoded by the screen). */
    protected abstract int getDisplaySlot(int index);

    // ---- Charging / selection / teardown ----

    /** Select which reaction this reactor runs (index into {@link ReactionRegistry#AVAILABLE}; -1 = none/idle). */
    public void selectReaction(int index) {
        if (!isEmpty()) {
            return; // ASPEN-style: the reaction can only be changed while the reactor is empty
        }
        Reaction reaction = ReactionRegistry.byIndex(index);
        this.selectedReaction = (reaction == null) ? ReactionRegistry.NONE : index;
        setChanged();
        sync();
    }

    /** Empty the reactor: discard all fluids and dissolved species, returning it to a clean state. */
    public void empty() {
        for (int i = 0; i < tank.size(); i++) {
            tank.set(i, FluidResource.EMPTY, 0);
        }
        Arrays.fill(amounts, 0.0);
        lastGameTime = Long.MIN_VALUE;
        temperatureK = Config.REACTOR_AMBIENT_TEMPERATURE_K.get();
        setChanged();
        sync();
    }

    /** Remove {@code moles} of a product to cash out; returns true if there was enough available. */
    public boolean extract(Species product, double moles) {
        if (amounts[product.ordinal()] < moles) {
            return false;
        }
        amounts[product.ordinal()] -= moles;
        Fluid fluid = fluidFor(product);
        if (fluid != null) {
            double grams = amounts[product.ordinal()] * product.molarMass();
            setFluidAmount(fluid, (int) Math.round(grams / product.density()));
        }
        setChanged();
        sync();
        return true;
    }

    /** Charge fermentable substrate into the vessel (given in grams, e.g. from a wheat item). */
    public void addSubstrate(double grams) {
        amounts[Species.SUBSTRATE.ordinal()] += grams / Species.SUBSTRATE.molarMass();
        setChanged();
    }

    public int getFluidMb(Fluid fluid) {
        int total = 0;
        for (int i = 0; i < tank.size(); i++) {
            if (tank.getResource(i).getFluid() == fluid) {
                total += tank.getAmountAsInt(i);
            }
        }
        return total;
    }

    public int getWaterMb() {
        return getFluidMb(Fluids.WATER);
    }

    /** True when the vessel holds no fluids and no dissolved species — the reaction may be changed. */
    public boolean isEmpty() {
        if (totalFluidMb() > 0) {
            return false;
        }
        for (double amount : amounts) {
            if (amount > 1.0e-9) {
                return false;
            }
        }
        return true;
    }

    // ---- Dynamic simulation ----

    /**
     * Advance the reactor by the game-time elapsed since the last tick (catching up after a reload). Runs
     * the kinetics + energy balance + cooling in substeps, calling {@link #advect} before each substep.
     */
    public void simulate(Level level) {
        long now = level.getGameTime();
        long previous = (lastGameTime == Long.MIN_VALUE) ? now : lastGameTime;
        lastGameTime = now; // always resync, so an idle gap is discarded rather than fast-forwarded later
        long elapsedTicks = Math.max(0L, now - previous);
        if (elapsedTicks == 0L) {
            return;
        }

        double ambient = Config.REACTOR_AMBIENT_TEMPERATURE_K.get();
        double coolingK = Config.REACTOR_COOLING_PER_DAY.get();
        double totalModelDays = elapsedTicks * Config.REACTION_MODEL_DAYS_PER_TICK.get();
        int steps = (int) Math.min(MAX_CATCHUP_SUBSTEPS,
                Math.max(1L, (long) Math.ceil(totalModelDays / MAX_SUBSTEP_DAYS)));
        double dtDays = totalModelDays / steps;

        Reaction reaction = ReactionRegistry.byIndex(selectedReaction);
        double tempBefore = temperatureK;
        boolean reacted = false;

        for (int i = 0; i < steps; i++) {
            // Convective transport (feed in / effluent out). No-op for a batch reactor; a CSTR overrides it.
            advect(dtDays);

            double volumeL = getWaterMb() / 1000.0;
            if (volumeL <= 0.0) {
                temperatureK = ambient; // no solvent => no thermal mass; sit at ambient
                continue;
            }

            // ASPEN-style: react only with a selected reaction and only while there is room for products.
            boolean canReact = reaction != null && totalFluidMb() < capacityMb;
            if (canReact && reaction.reactants().containsKey(Species.BIOMASS)
                    && amounts[Species.BIOMASS.ordinal()] <= 0.0) {
                amounts[Species.BIOMASS.ordinal()] = SEED_BIOMASS_G_PER_L * volumeL / Species.BIOMASS.molarMass();
            }
            if (canReact) {
                double extent = reaction.step(amounts, volumeL, dtDays, temperatureK); // moles this substep
                if (extent != 0.0) {
                    reacted = true;
                    double heatCapacity = getWaterMb() * CP_WATER; // J/K (water mass in grams * specific heat)
                    if (heatCapacity > 0.0) {
                        // Exothermic (enthalpy < 0) releases heat; a reverse step (extent < 0) absorbs it.
                        temperatureK += (-reaction.enthalpy() * extent) / heatCapacity;
                    }
                }
            }
            // Newton's law of cooling toward ambient, integrated exactly (stable for any step size).
            temperatureK = ambient + (temperatureK - ambient) * Math.exp(-coolingK * dtDays);
        }

        if (reacted) {
            updateLiquidProducts();
        }
        if (reacted || temperatureK != tempBefore) {
            setChanged();
        }
    }

    /**
     * Convective in/out of material and enthalpy for one substep of {@code dtDays}. Default: nothing
     * (a batch reactor is closed). A continuous reactor overrides this to add feed/effluent flows directly
     * to {@link #amounts}, the fluid vessel, and {@link #temperatureK}; it should call {@link #setChanged()}
     * when it mutates state.
     */
    protected void advect(double dtDays) {
    }

    /** Materialize each species the reaction network declares as a liquid product into tank fluid. */
    protected void updateLiquidProducts() {
        for (Species product : ReactionRegistry.LIQUID_SPECIES) {
            Fluid fluid = fluidFor(product);
            if (fluid == null) {
                continue; // declared as a liquid product, but no fluid is registered for it yet
            }
            double grams = amounts[product.ordinal()] * product.molarMass();
            setFluidAmount(fluid, (int) Math.round(grams / product.density()));
        }
    }

    /** The fluid a species materializes as when it is a liquid product (null = it stays dissolved). */
    protected static Fluid fluidFor(Species species) {
        return species == Species.ETHANOL ? ModFluids.ETHANOL.get() : null;
    }

    /** Set the tank's amount (mB) of a product fluid, capped so the shared volume never overflows. */
    protected void setFluidAmount(Fluid fluid, int desiredMb) {
        int slot = slotFor(fluid);
        int current = slot >= 0 ? tank.getAmountAsInt(slot) : 0;
        int targetMb = Math.max(0, Math.min(desiredMb, capacityMb - (totalFluidMb() - current)));
        if (targetMb == current) {
            return; // no integer change; avoid redundant sync
        }
        if (slot < 0) {
            slot = firstEmptySlot();
            if (slot < 0) {
                return; // no free slot for this product
            }
        }
        tank.set(slot, targetMb > 0 ? FluidResource.of(fluid) : FluidResource.EMPTY, targetMb);
    }

    protected int slotFor(Fluid fluid) {
        for (int i = 0; i < tank.size(); i++) {
            if (tank.getResource(i).getFluid() == fluid) {
                return i;
            }
        }
        return -1;
    }

    protected int totalFluidMb() {
        int total = 0;
        for (int i = 0; i < tank.size(); i++) {
            total += tank.getAmountAsInt(i);
        }
        return total;
    }

    protected int firstEmptySlot() {
        for (int i = 0; i < tank.size(); i++) {
            if (tank.getResource(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /** Push a full block-entity update to nearby clients (for discrete events; the GUI also polls data slots). */
    protected void sync() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // ---- Persistence ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        for (int i = 0; i < tank.size(); i++) {
            FluidResource resource = tank.getResource(i);
            if (!resource.isEmpty()) {
                output.store("fluid" + i, FluidStack.OPTIONAL_CODEC, resource.toStack(tank.getAmountAsInt(i)));
            }
        }
        for (Species species : Species.values()) {
            output.putDouble("mol_" + species.name(), amounts[species.ordinal()]);
        }
        output.putLong("lastGameTime", lastGameTime);
        output.putInt("selectedReaction", selectedReaction);
        output.putDouble("temperatureK", temperatureK);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        for (int i = 0; i < tank.size(); i++) {
            FluidStack stack = input.read("fluid" + i, FluidStack.OPTIONAL_CODEC).orElse(FluidStack.EMPTY);
            tank.set(i, FluidResource.of(stack), stack.getAmount());
        }
        for (Species species : Species.values()) {
            amounts[species.ordinal()] = input.getDoubleOr("mol_" + species.name(), 0.0);
        }
        lastGameTime = input.getLongOr("lastGameTime", Long.MIN_VALUE);
        selectedReaction = input.getIntOr("selectedReaction", ReactionRegistry.NONE);
        temperatureK = input.getDoubleOr("temperatureK", Config.REACTOR_AMBIENT_TEMPERATURE_K.get());
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
