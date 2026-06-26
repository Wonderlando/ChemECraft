package com.wonderlando.chemecraft.reaction;

import com.wonderlando.chemecraft.reaction.reactions.Acetification;
import com.wonderlando.chemecraft.reaction.reactions.Fermentation;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The reactions a batch reactor can be set to run. The user picks one (ASPEN-style) before the reactor
 * goes; until then it sits idle. Add a new reaction by adding it to {@link #AVAILABLE}.
 */
public final class Reactions {
    /** Sentinel index meaning "no reaction selected". */
    public static final int NONE = -1;

    public static final List<ReactionRegistry> AVAILABLE = List.of(
            new Fermentation(),
            new Acetification());

    /**
     * Species that any available reaction turns into a free liquid. This is the union over ALL reactions
     * (not just the selected one) so the tank keeps tracking, say, ethanol as a liquid even while
     * Acetification — which consumes it — is the active reaction.
     */
    public static final Set<Species> LIQUID_SPECIES = liquidSpecies();

    private Reactions() {}

    /** The reaction at an index, or null for {@link #NONE} / out of range. */
    public static ReactionRegistry byIndex(int index) {
        return (index >= 0 && index < AVAILABLE.size()) ? AVAILABLE.get(index) : null;
    }

    private static Set<Species> liquidSpecies() {
        EnumSet<Species> set = EnumSet.noneOf(Species.class);
        for (ReactionRegistry reaction : AVAILABLE) {
            set.addAll(reaction.liquidProducts());
        }
        return set;
    }
}
