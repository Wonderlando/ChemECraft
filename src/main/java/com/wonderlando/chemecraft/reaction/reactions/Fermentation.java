package com.wonderlando.chemecraft.reaction.reactions;

import com.wonderlando.chemecraft.reaction.Reaction;
import com.wonderlando.chemecraft.reaction.Species;

import java.util.Map;

/**
 * Fermentation as a mass-action reaction (autocatalytic in biomass):
 * <pre>Substrate + Biomass -> 2 Biomass + 2 Ethanol + 2 CO2,  rate = k * [Substrate] * [Biomass]</pre>
 * A tunable caricature (not strictly mass-balanced) — adjust the coefficients or {@code k} freely.
 */
public class Fermentation extends Reaction {
    /** L/(mol·s). Tuned so a ~150 g/L batch finishes in ~20 real minutes at simulationSpeed=1. */
    public static final double DEFAULT_RATE_CONSTANT = 0.05;
    /** Exothermic heat of reaction per mole of extent (one substrate consumed), in joules. */
    public static final double DEFAULT_ENTHALPY = -100_000.0;
    /** Activation energy, J/mol — ~Q10 of 1.9 (rate nearly doubles per +10 K), typical of yeast kinetics. */
    public static final double DEFAULT_ACTIVATION_ENERGY = 50_000.0;

    public Fermentation() {
        this(DEFAULT_RATE_CONSTANT);
    }

    public Fermentation(double rateConstant) {
        super(
                Map.of(Species.SUBSTRATE, 1, Species.BIOMASS, 1),
                Map.of(Species.BIOMASS, 2, Species.ETHANOL, 2, Species.CARBON_DIOXIDE, 2),
                rateConstant);
    }

    // Ethanol stays a DISSOLVED species (no separate liquid phase / volume) so the vessel's volume balance is
    // just water in/out — otherwise the produced ethanol's volume would accumulate in a CSTR. Liquid-liquid
    // separation (materializing ethanol as its own fluid) will be handled later by a distillation step.

    @Override
    public double enthalpy() {
        return DEFAULT_ENTHALPY;
    }

    @Override
    public double activationEnergyForward() {
        return DEFAULT_ACTIVATION_ENERGY;
    }
}
