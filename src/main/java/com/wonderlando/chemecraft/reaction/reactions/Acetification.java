package com.wonderlando.chemecraft.reaction.reactions;

import com.wonderlando.chemecraft.reaction.ReactionRegistry;
import com.wonderlando.chemecraft.reaction.Species;

import java.util.Map;

/**
 * Slow aerobic souring of ethanol to acetic acid (wine to vinegar), as a first-order mass-action
 * reaction: Ethanol -> Acetic Acid, rate = k * [Ethanol]. O2 and water are treated as implicit.
 * Demonstrates a second reaction that consumes the first reaction's product.
 */
public class Acetification extends ReactionRegistry {
    /** First-order souring rate constant, 1/day. Tunable. */
    public static final double DEFAULT_RATE_CONSTANT = 0.05;

    public Acetification() {
        this(DEFAULT_RATE_CONSTANT);
    }

    public Acetification(double rateConstant) {
        super(
                Map.of(Species.ETHANOL, 1),
                Map.of(Species.ACETIC_ACID, 1),
                rateConstant);
    }
}
