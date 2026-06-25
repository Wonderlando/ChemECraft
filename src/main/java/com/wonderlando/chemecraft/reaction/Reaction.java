package com.wonderlando.chemecraft.reaction;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A chemical reaction: reactant/product stoichiometry plus a rate constant. The default {@link #rate}
 * is mass-action ({@code k * product([reactant]^coeff)}); subclasses define a specific reaction by
 * supplying their stoichiometry, and may override {@link #rate} for non-mass-action kinetics
 * (e.g. saturation or temperature-dependent rate laws).
 *
 * <p>To add a new reaction, subclass this and pass the stoichiometry to {@code super(...)}.
 */
public abstract class Reaction {
    private final Map<Species, Integer> reactants;
    private final Map<Species, Integer> products;
    private final double rateConstant;

    protected Reaction(Map<Species, Integer> reactants, Map<Species, Integer> products, double rateConstant) {
        this.reactants = Map.copyOf(reactants);
        this.products = Map.copyOf(products);
        this.rateConstant = rateConstant;
    }

    /** Reaction rate from molar concentrations (indexed by {@link Species#ordinal()}). Mass-action by default. */
    public double rate(double[] concentration) {
        double r = rateConstant;
        for (Map.Entry<Species, Integer> term : reactants.entrySet()) {
            r *= Math.pow(concentration[term.getKey().ordinal()], term.getValue());
        }
        return r;
    }

    /** Net stoichiometric coefficient for a species (negative = consumed, positive = produced). */
    public double net(Species species) {
        return products.getOrDefault(species, 0) - reactants.getOrDefault(species, 0);
    }

    /**
     * Species this reaction materializes as a FREE LIQUID in the vessel, changing its fluid composition.
     * Default: none — products stay dissolved and the solvent/fluid composition is left unchanged, which
     * is the usual case. Override to opt a product into becoming a separable liquid (e.g. for distillation).
     */
    public Set<Species> liquidProducts() {
        return Set.of();
    }

    public double rateConstant() {
        return rateConstant;
    }

    public Map<Species, Integer> reactants() {
        return reactants;
    }

    public Map<Species, Integer> products() {
        return products;
    }

    /** Human-readable name for this reaction (shown in the reactor GUI). */
    public String displayName() {
        return getClass().getSimpleName();
    }

    /** The stoichiometric equation as text, e.g. "Substrate + Biomass -> 2 Biomass + 2 Ethanol + 2 CO2". */
    public String equation() {
        return formatSide(reactants) + " -> " + formatSide(products);
    }

    private static String formatSide(Map<Species, Integer> terms) {
        return terms.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> entry.getKey().ordinal()))
                .map(entry -> (entry.getValue() == 1 ? "" : entry.getValue() + " ") + entry.getKey().displayName())
                .collect(Collectors.joining(" + "));
    }
}
