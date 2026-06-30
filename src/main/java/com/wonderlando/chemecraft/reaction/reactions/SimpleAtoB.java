package com.wonderlando.chemecraft.reaction.reactions;

import com.wonderlando.chemecraft.reaction.Reaction;
import com.wonderlando.chemecraft.reaction.Species;

import java.util.Map;

/**
 * Irreversible isothermal test reaction:
 * <pre>A → B,  rate = kf * [A]</pre>
 * Enthalpy and activation energy are both zero (temperature-independent, no heat release).
 */
public class SimpleAtoB extends Reaction {
    public static final double DEFAULT_RATE_CONSTANT = 1.0; // s⁻¹ (first-order)

    public SimpleAtoB() {
        this(DEFAULT_RATE_CONSTANT);
    }

    public SimpleAtoB(double rateConstant) {
        super(
                Map.of(Species.SPECIES_A, 1),
                Map.of(Species.SPECIES_B, 1),
                rateConstant);
    }
}
