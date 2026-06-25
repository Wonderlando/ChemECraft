package com.wonderlando.chemecraft.reaction;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Runs a set of {@link Reaction}s on a reactor's molar state. Construct it with the reactions a reactor
 * should run, then call {@link #step} each tick. Reaction-agnostic: it only knows the {@link Reaction}
 * interface, so adding chemistry is just passing more reactions in.
 */
public final class ReactionNetwork {
    private final List<Reaction> reactions;

    public ReactionNetwork(List<Reaction> reactions) {
        this.reactions = List.copyOf(reactions);
    }

    public ReactionNetwork(Reaction... reactions) {
        this(List.of(reactions));
    }

    public List<Reaction> reactions() {
        return reactions;
    }

    /** Union of every species the contained reactions materialize as a free liquid. */
    public Set<Species> liquidProducts() {
        EnumSet<Species> liquids = EnumSet.noneOf(Species.class);
        for (Reaction reaction : reactions) {
            liquids.addAll(reaction.liquidProducts());
        }
        return liquids;
    }

    /**
     * Advance {@code amounts} (mol, indexed by {@link Species#ordinal()}) by one forward-Euler step of
     * {@code dtDays}. Concentrations are amounts / volume; amounts are clamped non-negative.
     */
    public void step(double[] amounts, double volumeL, double dtDays) {
        if (volumeL <= 0.0 || dtDays <= 0.0) {
            return;
        }
        int n = amounts.length;
        double[] concentration = new double[n];
        for (int i = 0; i < n; i++) {
            concentration[i] = amounts[i] / volumeL;
        }
        double[] dConcentration = new double[n]; // summed d[]/dt over reactions, mol/(L*day)
        for (Reaction reaction : reactions) {
            double r = reaction.rate(concentration);
            if (r <= 0.0) {
                continue;
            }
            for (Species species : Species.values()) {
                double nu = reaction.net(species);
                if (nu != 0.0) {
                    dConcentration[species.ordinal()] += nu * r;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            amounts[i] = Math.max(0.0, amounts[i] + dConcentration[i] * volumeL * dtDays);
        }
    }
}
