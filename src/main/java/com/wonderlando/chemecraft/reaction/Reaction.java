package com.wonderlando.chemecraft.reaction;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A (optionally reversible) chemical reaction: reactant/product stoichiometry plus a forward and a reverse
 * rate constant. The mass-action forward and reverse rates are
 * {@code kf * product([reactant]^coeff)} and {@code kr * product([product]^coeff)}; the {@link #rate} that
 * drives the integrator is the NET rate {@code forward - reverse}, so a reaction with a non-zero reverse
 * constant relaxes toward chemical equilibrium (net rate -> 0) instead of running to completion, and can
 * even run backwards when products dominate.
 *
 * <p>A reaction with {@code kr == 0} is irreversible and behaves exactly as a one-way mass-action reaction.
 *
 * <p>To add a new reaction, subclass this and pass the stoichiometry (and rate constants) to {@code super}.
 * Override {@link #rate}/{@link #forwardRate}/{@link #reverseRate} for non-mass-action or
 * temperature-dependent kinetics.
 */
public abstract class Reaction {
    private final Map<Species, Integer> reactants;
    private final Map<Species, Integer> products;
    private final double forwardRateConstant;
    private final double reverseRateConstant;

    /** Irreversible reaction (reverse rate constant = 0). */
    protected Reaction(Map<Species, Integer> reactants, Map<Species, Integer> products, double forwardRateConstant) {
        this(reactants, products, forwardRateConstant, 0.0);
    }

    /** Reversible reaction with distinct forward and reverse rate constants. */
    protected Reaction(Map<Species, Integer> reactants, Map<Species, Integer> products,
                       double forwardRateConstant, double reverseRateConstant) {
        this.reactants = Map.copyOf(reactants);
        this.products = Map.copyOf(products);
        this.forwardRateConstant = forwardRateConstant;
        this.reverseRateConstant = reverseRateConstant;
    }

    /** Forward (left-to-right) mass-action rate from molar concentrations (indexed by {@link Species#ordinal()}). */
    public double forwardRate(double[] concentration) {
        return massAction(forwardRateConstant, reactants, concentration);
    }

    /** Reverse (right-to-left) mass-action rate; zero for an irreversible reaction. */
    public double reverseRate(double[] concentration) {
        if (reverseRateConstant == 0.0) {
            return 0.0;
        }
        return massAction(reverseRateConstant, products, concentration);
    }

    /** Net reaction rate (forward - reverse). Positive runs the reaction forward, negative runs it backward. */
    public double rate(double[] concentration) {
        return forwardRate(concentration) - reverseRate(concentration);
    }

    private static double massAction(double rateConstant, Map<Species, Integer> terms, double[] concentration) {
        double r = rateConstant;
        for (Map.Entry<Species, Integer> term : terms.entrySet()) {
            r *= Math.pow(concentration[term.getKey().ordinal()], term.getValue());
        }
        return r;
    }

    /** Net stoichiometric coefficient for a species (negative = consumed, positive = produced) in the forward direction. */
    public double net(Species species) {
        return products.getOrDefault(species, 0) - reactants.getOrDefault(species, 0);
    }

    /**
     * Advance molar {@code amounts} (indexed by {@link Species#ordinal()}) by one forward-Euler step of
     * {@code dtDays} under this reaction: the NET rate is evaluated at the current concentrations
     * (amount / volume) and applied through the net stoichiometry, with amounts clamped non-negative. A
     * negative net rate runs the reaction in reverse (consuming products, regenerating reactants).
     * (For several simultaneous reactions in one vessel, evaluate all rates first, then apply.)
     */
    public void step(double[] amounts, double volumeL, double dtDays) {
        if (volumeL <= 0.0 || dtDays <= 0.0) {
            return;
        }
        double[] concentration = new double[amounts.length];
        for (int i = 0; i < amounts.length; i++) {
            concentration[i] = amounts[i] / volumeL;
        }
        double r = rate(concentration);
        if (r == 0.0) {
            return;
        }
        for (Species species : Species.values()) {
            double nu = net(species);
            if (nu != 0.0) {
                int i = species.ordinal();
                amounts[i] = Math.max(0.0, amounts[i] + nu * r * volumeL * dtDays);
            }
        }
    }

    /**
     * Species this reaction materializes as a FREE LIQUID in the vessel, changing its fluid composition.
     * Default: none — products stay dissolved and the solvent/fluid composition is left unchanged, which
     * is the usual case. Override to opt a product into becoming a separable liquid (e.g. for distillation).
     */
    public Set<Species> liquidProducts() {
        return Set.of();
    }

    public double forwardRateConstant() {
        return forwardRateConstant;
    }

    public double reverseRateConstant() {
        return reverseRateConstant;
    }

    /** True when this reaction has a non-zero reverse rate constant (it relaxes toward equilibrium). */
    public boolean isReversible() {
        return reverseRateConstant != 0.0;
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

    /** The arrow used in the equation: a double harpoon for reversible reactions, a single arrow otherwise. */
    public String arrow() {
        return isReversible() ? "⇌" : "→";
    }

    /** The stoichiometric equation as text, e.g. "Substrate + Biomass -> 2 Biomass + 2 Ethanol + 2 CO2". */
    public String equation() {
        return formatSide(reactants) + " " + arrow() + " " + formatSide(products);
    }

    private static String formatSide(Map<Species, Integer> terms) {
        return terms.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> entry.getKey().ordinal()))
                .map(entry -> (entry.getValue() == 1 ? "" : entry.getValue() + " ") + entry.getKey().displayName())
                .collect(Collectors.joining(" + "));
    }
}
