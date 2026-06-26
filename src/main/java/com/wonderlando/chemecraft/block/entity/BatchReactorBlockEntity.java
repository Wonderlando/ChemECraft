package com.wonderlando.chemecraft.block.entity;

import com.wonderlando.chemecraft.Config;
import com.wonderlando.chemecraft.menu.BatchReactorMenu;
import com.wonderlando.chemecraft.reaction.Reaction;
import com.wonderlando.chemecraft.reaction.ReactionRegistry;
import com.wonderlando.chemecraft.reaction.Species;
import com.wonderlando.chemecraft.registry.ModBlockEntities;
import com.wonderlando.chemecraft.registry.ModFluids;

import java.util.Arrays;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
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
 * Block entity for the batch reactor: one shared, well-mixed vessel of fluids (water in, ethanol out)
 * plus the dissolved reaction state, tracked as molar amounts per {@link Species}. Each tick it advances
 * the selected mass-action {@link Reaction}, catching up elapsed time after a chunk reload.
 */
public class BatchReactorBlockEntity extends BlockEntity implements MenuProvider {
    /** Maximum number of distinct fluids the vessel can hold at once (one shared, well-mixed pool). */
    public static final int FLUID_SLOTS = 6;
    /** Shared working volume across all fluids, in millibuckets (27 buckets). Provisional mapping: 1000 mB = 1 L. */
    public static final int CAPACITY_MB = 27_000;
    /** Fermentable substrate added per wheat item, in grams. */
    public static final double SUBSTRATE_PER_WHEAT_G = 50.0;
    /** Number of synced display values exposed to the GUI. */
    public static final int DISPLAY_SLOTS = 8;

    private static final double SEED_BIOMASS_G_PER_L = 0.5; // auto-seeded yeast inoculum
    private static final int MAX_CATCHUP_SUBSTEPS = 2000;
    private static final double MAX_SUBSTEP_DAYS = 0.05;
    private static final double CP_WATER = 4.184; // specific heat of water, J/(g*K)

    // Reaction state: molar amounts (mol) per species, indexed by Species.ordinal().
    private final double[] amounts = new double[Species.values().length];
    private long lastGameTime = Long.MIN_VALUE;
    // Bulk temperature of the (well-mixed) vessel contents, in kelvin. Starts at ambient (298 K).
    private double temperatureK = 298.0;

    // The reaction the user selected for this reactor (ASPEN-style); NONE = idle until one is picked.
    private int selectedReaction = ReactionRegistry.NONE;

    private final FluidStacksResourceHandler tank = new FluidStacksResourceHandler(FLUID_SLOTS, CAPACITY_MB) {
        @Override
        protected int getCapacity(int index, FluidResource resource) {
            int usedElsewhere = 0;
            for (int i = 0; i < size(); i++) {
                if (i != index) {
                    usedElsewhere += getAmountAsInt(i);
                }
            }
            return CAPACITY_MB - usedElsewhere;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            // Only water is charged in; product liquids are placed by the reaction itself.
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

    public BatchReactorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BATCH_REACTOR.get(), pos, state);
    }

    public ResourceHandler<FluidResource> getFluidHandler() {
        return tank;
    }

    public double getMoles(Species species) {
        return amounts[species.ordinal()];
    }

    public double getGrams(Species species) {
        return amounts[species.ordinal()] * species.molarMass();
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
                return DISPLAY_SLOTS;
            }
        };
    }

    private int getDisplaySlot(int index) {
        return switch (index) {
            case 0 -> getFluidMb(Fluids.WATER);
            case 1 -> getFluidMb(ModFluids.ETHANOL.get());
            case 2 -> (int) Math.round(amounts[Species.SUBSTRATE.ordinal()] * 1000.0);
            case 3 -> (int) Math.round(amounts[Species.BIOMASS.ordinal()] * 1000.0);
            case 4 -> (int) Math.round(amounts[Species.CARBON_DIOXIDE.ordinal()] * 1000.0);
            case 5 -> (int) Math.round(amounts[Species.ACETIC_ACID.ordinal()] * 1000.0);
            case 6 -> selectedReaction;
            case 7 -> (int) Math.round(temperatureK * 10.0); // deci-kelvin (0.1 K precision)
            default -> 0;
        };
    }

    public double getTemperatureK() {
        return temperatureK;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.chemecraft.batch_reactor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new BatchReactorMenu(containerId, playerInventory, this);
    }

    /** Select which reaction this reactor runs (index into {@link ReactionRegistry#AVAILABLE}; -1 = none/idle). */
    public void selectReaction(int index) {
        if (!isEmpty()) {
            return; // ASPEN-style: the reaction can only be changed while the reactor is empty
        }
        Reaction reaction = ReactionRegistry.byIndex(index);
        this.selectedReaction = (reaction == null) ? ReactionRegistry.NONE : index;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    public int getSelectedReaction() {
        return selectedReaction;
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
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
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
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
        return true;
    }

    /** Charge fermentable substrate into the vessel (given in grams, e.g. from a wheat item). */
    public void addSubstrate(double grams) {
        amounts[Species.SUBSTRATE.ordinal()] += grams / Species.SUBSTRATE.molarMass();
        setChanged();
    }

    /** Total amount of a given fluid in the vessel, in mB. */
    public int getFluidMb(Fluid fluid) {
        int total = 0;
        for (int i = 0; i < tank.size(); i++) {
            if (tank.getResource(i).getFluid() == fluid) {
                total += tank.getAmountAsInt(i);
            }
        }
        return total;
    }

    /** Total water currently in the vessel, in mB. */
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, BatchReactorBlockEntity be) {
        be.tickReaction(level);
    }

    private void tickReaction(Level level) {
        long now = level.getGameTime();
        long previous = (lastGameTime == Long.MIN_VALUE) ? now : lastGameTime;
        lastGameTime = now; // always resync, so an idle gap is discarded rather than fast-forwarded later
        long elapsedTicks = Math.max(0L, now - previous);
        if (elapsedTicks == 0L) {
            return;
        }

        double ambient = Config.REACTOR_AMBIENT_TEMPERATURE_K.get();
        double volumeL = getWaterMb() / 1000.0;
        if (volumeL <= 0.0) {
            // No solvent to hold heat: there is nothing to react in and no thermal mass, so sit at ambient.
            if (temperatureK != ambient) {
                temperatureK = ambient;
                setChanged();
            }
            return;
        }

        double totalModelDays = elapsedTicks * Config.REACTION_MODEL_DAYS_PER_TICK.get();
        int steps = (int) Math.min(MAX_CATCHUP_SUBSTEPS,
                Math.max(1L, (long) Math.ceil(totalModelDays / MAX_SUBSTEP_DAYS)));
        double dtDays = totalModelDays / steps;

        Reaction reaction = ReactionRegistry.byIndex(selectedReaction);
        // ASPEN-style: react only with a selected reaction and only while there is room for liquid products.
        boolean canReact = reaction != null && totalFluidMb() < CAPACITY_MB;

        // Seed the inoculum/catalyst the selected reaction needs (e.g. yeast for fermentation).
        if (canReact && reaction.reactants().containsKey(Species.BIOMASS)
                && amounts[Species.BIOMASS.ordinal()] <= 0.0) {
            amounts[Species.BIOMASS.ordinal()] = SEED_BIOMASS_G_PER_L * volumeL / Species.BIOMASS.molarMass();
        }

        // Thermal mass = mass of solvent (water, 1 mB ~= 1 g) times its specific heat. Larger batches
        // heat up more slowly for the same heat of reaction.
        double heatCapacity = getWaterMb() * CP_WATER; // J/K
        double coolingK = Config.REACTOR_COOLING_PER_DAY.get();
        for (int i = 0; i < steps; i++) {
            if (canReact) {
                double extent = reaction.step(amounts, volumeL, dtDays, temperatureK); // moles this substep
                if (extent != 0.0 && heatCapacity > 0.0) {
                    // Exothermic (enthalpy < 0) releases heat; a reverse step (extent < 0) absorbs it.
                    temperatureK += (-reaction.enthalpy() * extent) / heatCapacity;
                }
            }
            // Newton's law of cooling toward ambient, integrated exactly (stable for any step size).
            temperatureK = ambient + (temperatureK - ambient) * Math.exp(-coolingK * dtDays);
        }

        if (canReact) {
            updateLiquidProducts();
        }
        setChanged();
    }

    /** Materialize each species the reaction network declares as a liquid product into tank fluid. */
    private void updateLiquidProducts() {
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
    private static Fluid fluidFor(Species species) {
        return species == Species.ETHANOL ? ModFluids.ETHANOL.get() : null;
    }

    /** Set the tank's amount (mB) of a product fluid, capped so the shared volume never overflows. */
    private void setFluidAmount(Fluid fluid, int desiredMb) {
        int slot = slotFor(fluid);
        int current = slot >= 0 ? tank.getAmountAsInt(slot) : 0;
        int targetMb = Math.max(0, Math.min(desiredMb, CAPACITY_MB - (totalFluidMb() - current)));
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

    private int slotFor(Fluid fluid) {
        for (int i = 0; i < tank.size(); i++) {
            if (tank.getResource(i).getFluid() == fluid) {
                return i;
            }
        }
        return -1;
    }

    private int totalFluidMb() {
        int total = 0;
        for (int i = 0; i < tank.size(); i++) {
            total += tank.getAmountAsInt(i);
        }
        return total;
    }

    private int firstEmptySlot() {
        for (int i = 0; i < tank.size(); i++) {
            if (tank.getResource(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

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
