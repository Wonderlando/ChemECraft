package com.wonderlando.chemecraft.reaction.reactions;

import com.wonderlando.chemecraft.reaction.Reaction;
import com.wonderlando.chemecraft.reaction.Species;

import java.util.Map;
import java.util.Set;

/**
 * Fermentation as a mass-action reaction (autocatalytic in biomass):
 * <pre>Substrate + Biomass -> 2 Biomass + 2 Ethanol + 2 CO2,  rate = k * [Substrate] * [Biomass]</pre>
 * A tunable caricature (not strictly mass-balanced) — adjust the coefficients or {@code k} freely.
 */
public class Fermentation extends Reaction {
    /** Tuned so a ~150 g/L batch finishes in ~1 in-game day, L/(mol*day). */
    public static final double DEFAULT_RATE_CONSTANT = 10;

    public Fermentation() {
        this(DEFAULT_RATE_CONSTANT);
    }

    public Fermentation(double rateConstant) {
        super(
                Map.of(Species.SUBSTRATE, 1, Species.BIOMASS, 1),
                Map.of(Species.BIOMASS, 2, Species.ETHANOL, 2, Species.CARBON_DIOXIDE, 2),
                rateConstant);
    }

    /** Ethanol leaves the dissolved phase as a real liquid, so it can be separated later (distillation). */
    @Override
    public Set<Species> liquidProducts() {
        return Set.of(Species.ETHANOL);
    }
}
