package com.wonderlando.chemecraft.reaction.reactions;

import com.wonderlando.chemecraft.reaction.Reaction;
import com.wonderlando.chemecraft.reaction.Species;

import java.util.Map;

/**
 * Slow, reversible souring of ethanol to acetic acid (wine to vinegar), as a first-order mass-action
 * reaction in each direction: Ethanol ⇌ Acetic Acid. O2 and water are treated as implicit. With the
 * default constants the equilibrium constant Keq = kf/kr = 10, so the batch settles at roughly 91%
 * acetic acid rather than running fully to completion — demonstrating chemical equilibrium.
 */
public class Acetification extends Reaction {
    /** Forward (souring) rate constant, 1/day. Tunable. */
    public static final double DEFAULT_FORWARD_RATE_CONSTANT = 0.05;
    /** Reverse rate constant, 1/day. Tunable; Keq = forward/reverse sets the equilibrium position. */
    public static final double DEFAULT_REVERSE_RATE_CONSTANT = 0.005;

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
}
