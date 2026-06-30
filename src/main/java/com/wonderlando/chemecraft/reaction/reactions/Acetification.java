package com.wonderlando.chemecraft.reaction.reactions;

import com.wonderlando.chemecraft.reaction.Reaction;
import com.wonderlando.chemecraft.reaction.Species;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Slow, reversible souring of ethanol to acetic acid (wine to vinegar), as a first-order mass-action
 * reaction in each direction: Ethanol ⇌ Acetic Acid. O2 and water are treated as implicit. At the reference
 * temperature (298 K) Keq = kf/kr = 10, so the batch settles at ~91% acetic acid — chemical equilibrium.
 *
 * <p>Being exothermic, its equilibrium is temperature-dependent (van't Hoff): the reverse activation energy
 * is derived as {@code Ea_r = Ea_f - ΔH} (see {@link #activationEnergyReverse()}), so heating the vessel
 * pushes the equilibrium BACK toward ethanol (≈79% conversion by ~313 K) while cooling favors more vinegar.
 *
 * <p>Its ethanol comes from upstream {@link Fermentation}, so per the reaction-chaining design rule it
 * {@link #trackedSpecies() holds data for} all of fermentation's species too — the broth's leftover
 * substrate/biomass ride along in the piped mixture and must stay tracked, not silently dropped.
 */
public class Acetification extends Reaction {
    // Design rule: a downstream reaction holds data for ALL of its upstream reaction's species. Acetification's
    // ethanol is Fermentation's product, so track Fermentation's species (impurities) plus our own acetic acid.
    private static final Set<Species> TRACKED;
    static {
        EnumSet<Species> tracked = EnumSet.of(Species.ETHANOL, Species.ACETIC_ACID);
        tracked.addAll(new Fermentation().trackedSpecies());
        TRACKED = Set.copyOf(tracked);
    }

    /** Forward (souring) rate constant, s⁻¹. Tunable. */
    public static final double DEFAULT_FORWARD_RATE_CONSTANT = 0.05 / 86400.0;
    /** Reverse rate constant, s⁻¹. Tunable; Keq = forward/reverse sets the equilibrium position. */
    public static final double DEFAULT_REVERSE_RATE_CONSTANT = 0.005 / 86400.0;
    /**
     * Exothermic heat of reaction per mole of ethanol soured, in joules. Tuned caricature (O2/water are
     * implicit, so this is an effective ΔH): negative enough to give a clear van't Hoff equilibrium shift
     * (~91% → ~79% conversion over a +15 K rise) without the violent shift a textbook −480 kJ/mol would give.
     * Drives BOTH the energy balance (self-heating) and, via {@code Ea_r = Ea_f - ΔH}, the equilibrium.
     */
    public static final double DEFAULT_ENTHALPY = -50_000.0;
    /**
     * FORWARD activation energy, J/mol — sets how fast the souring speeds up with temperature. The reverse
     * activation energy is NOT set here; it is derived from van't Hoff consistency (Ea_r = Ea_f - ΔH), which
     * here is 60000 - (-50000) = 110000 J/mol, so the reverse is more temperature-sensitive than the forward.
     */
    public static final double DEFAULT_ACTIVATION_ENERGY = 60_000.0;

    public Acetification() {
        this(DEFAULT_FORWARD_RATE_CONSTANT, DEFAULT_REVERSE_RATE_CONSTANT);
    }

    public Acetification(double forwardRateConstant, double reverseRateConstant) {
        super(
                Map.of(Species.ETHANOL, 1),
                Map.of(Species.ACETIC_ACID, 1),
                forwardRateConstant,
                reverseRateConstant);
    }

    @Override
    public double enthalpy() {
        return DEFAULT_ENTHALPY;
    }

    @Override
    public double activationEnergyForward() {
        return DEFAULT_ACTIVATION_ENERGY;
    }

    // activationEnergyReverse() is intentionally NOT overridden: the base derives it as Ea_f - ΔH (van't Hoff).

    @Override
    public Set<Species> trackedSpecies() {
        return TRACKED;
    }
}
