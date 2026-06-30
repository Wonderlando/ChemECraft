package com.wonderlando.chemecraft.reaction;

import java.util.Comparator;
import java.util.EnumSet;
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
    /** Universal gas constant, J/(mol*K). */
    public static final double GAS_CONSTANT = 8.314;
    /** Temperature at which the rate constants are quoted; the Arrhenius factor is 1 here. */
    public static final double REFERENCE_TEMPERATURE_K = 298.0;

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

    /** Forward (left-to-right) mass-action rate at temperature {@code temperatureK} (kelvin). */
    public double forwardRate(double[] concentration, double temperatureK) {
        double k = forwardRateConstant * arrheniusFactor(activationEnergyForward(), temperatureK);
        return massAction(k, reactants, concentration);
    }

    /** Reverse (right-to-left) mass-action rate at temperature {@code temperatureK}; zero if irreversible. */
    public double reverseRate(double[] concentration, double temperatureK) {
        if (reverseRateConstant == 0.0) {
            return 0.0;
        }
        double k = reverseRateConstant * arrheniusFactor(activationEnergyReverse(), temperatureK);
        return massAction(k, products, concentration);
    }

    /** Net reaction rate (forward - reverse) at {@code temperatureK}. Positive runs forward, negative backward. */
    public double rate(double[] concentration, double temperatureK) {
        return forwardRate(concentration, temperatureK) - reverseRate(concentration, temperatureK);
    }

    /**
     * The Arrhenius temperature factor relative to {@link #REFERENCE_TEMPERATURE_K}:
     * {@code exp((Ea/R) * (1/T_ref - 1/T))}. Equals 1 at the reference temperature, &gt;1 hotter, &lt;1
     * colder. With {@code Ea == 0} the rate is temperature-independent (factor 1).
     */
    private static double arrheniusFactor(double activationEnergy, double temperatureK) {
        if (activationEnergy == 0.0 || temperatureK <= 0.0) {
            return 1.0;
        }
        return Math.exp(activationEnergy / GAS_CONSTANT * (1.0 / REFERENCE_TEMPERATURE_K - 1.0 / temperatureK));
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
     * (amount / volume) and applied through the net stoichiometry. A negative net rate runs the reaction in
     * reverse (consuming products, regenerating reactants). (For several simultaneous reactions in one vessel,
     * evaluate all rates first, then apply.)
     *
     * <p>Rate constants are in SI units: s⁻¹ for first-order reactions, L/(mol·s) for second-order.
     * {@code dtDays} is converted to seconds internally (×86400) so the units work out.
     *
     * <p><b>Mass balance.</b> A single scalar reaction {@code extent} (moles) is applied to every species
     * through its stoichiometric coefficient. If an explicit step would drive any consumed species negative,
     * the extent is reduced to exactly empty that species — so the coupled species change by the matching
     * amount and atoms are never created or destroyed, even when the step badly overshoots. (Independently
     * clamping each species, as a naive {@code max(0, …)} would, breaks the stoichiometric coupling and can
     * manufacture product from a depleted reactant; we deliberately clamp the shared extent instead.)
     *
     * @return the reaction extent actually applied this step in moles; positive for a forward step, negative
     *         for a reverse one. Multiply by {@code -enthalpy()} to get heat released.
     */
    public double step(double[] amounts, double volumeL, double dtDays, double temperatureK) {
        if (volumeL <= 0.0 || dtDays <= 0.0) {
            return 0.0;
        }
        double[] concentration = new double[amounts.length];
        for (int i = 0; i < amounts.length; i++) {
            concentration[i] = amounts[i] / volumeL;
        }
        double r = rate(concentration, temperatureK);
        if (r == 0.0) {
            return 0.0;
        }
        double dtSeconds = dtDays * 86400.0;
        double extent = r * volumeL * dtSeconds; // raw moles of reaction this step (signed)

        // Limit the shared extent so no species it consumes goes negative (species s changes by net(s)*extent;
        // it is consumed when net(s)*extent < 0). Reducing the one extent keeps the whole step stoichiometric.
        for (Species species : Species.values()) {
            double nu = net(species);
            if (nu == 0.0 || nu * extent >= 0.0) {
                continue; // unchanged or being produced in this direction — no lower bound from it
            }
            double maxMagnitude = amounts[species.ordinal()] / Math.abs(nu);
            extent = (extent > 0.0) ? Math.min(extent, maxMagnitude) : Math.max(extent, -maxMagnitude);
        }
        if (extent == 0.0) {
            return 0.0;
        }
        for (Species species : Species.values()) {
            double nu = net(species);
            if (nu != 0.0) {
                int i = species.ordinal();
                amounts[i] = Math.max(0.0, amounts[i] + nu * extent); // max() only mops up float round-off
            }
        }
        return extent;
    }

    /**
     * Largest fractional consumption rate (units of 1/s) among the species this reaction consumes at the given
     * state — i.e. the inverse of the shortest depletion timescale. The integrator uses this to size its
     * substeps so an explicit forward-Euler step never overshoots (it keeps {@code rate·dt} per consumed
     * species small). Returns 0 when nothing is being consumed (no reaction, or every reactant is exhausted).
     */
    public double fastestRelativeRate(double[] concentration, double temperatureK) {
        double r = rate(concentration, temperatureK); // net rate, mol/(L·s)
        if (r == 0.0) {
            return 0.0;
        }
        double fastest = 0.0;
        for (Species species : Species.values()) {
            double nu = net(species);
            double consumptionRate = -nu * r; // mol/(L·s), positive when this species is being consumed
            double conc = concentration[species.ordinal()];
            if (consumptionRate > 0.0 && conc > 0.0) {
                fastest = Math.max(fastest, consumptionRate / conc);
            }
        }
        return fastest;
    }

    /**
     * Species this reaction materializes as a FREE LIQUID in the vessel, changing its fluid composition.
     * Default: none — products stay dissolved and the solvent/fluid composition is left unchanged, which
     * is the usual case. Override to opt a product into becoming a separable liquid (e.g. for distillation).
     */
    public Set<Species> liquidProducts() {
        return Set.of();
    }

    /**
     * Standard enthalpy of reaction per mole of reaction extent, in joules. NEGATIVE is exothermic
     * (releases heat). Default 0 = thermally neutral; override to give a reaction a heat of reaction.
     * Heat released over a step is {@code -enthalpy() * step(...)} (the reverse direction absorbs it).
     */
    public double enthalpy() {
        return 0.0;
    }

    /**
     * Activation energy of the FORWARD reaction in J/mol, used by the Arrhenius temperature dependence.
     * Default 0 = temperature-independent. Higher Ea = more sensitive to temperature.
     */
    public double activationEnergyForward() {
        return 0.0;
    }

    /**
     * Activation energy of the REVERSE reaction in J/mol (only relevant when reversible). By DEFAULT this is
     * derived for thermodynamic consistency (the van't Hoff relation): {@code Ea_r = Ea_f - ΔH_rxn}.
     *
     * <p>Why: with both rate constants Arrhenius, the equilibrium constant K = kf/kr varies with temperature
     * as {@code exp[(Ea_f - Ea_r)/R · (1/T_ref - 1/T)]}, while van't Hoff requires
     * {@code K(T) = K_ref · exp[ΔH/R · (1/T_ref - 1/T)]}. Matching them forces {@code Ea_f - Ea_r = ΔH}. So the
     * equilibrium position shifts with temperature using the SAME {@link #enthalpy() ΔH} as the energy balance:
     * an exothermic reaction (ΔH &lt; 0 ⇒ Ea_r &gt; Ea_f) loses equilibrium conversion as it heats up
     * (Le Chatelier), and gains it when cooled. Override only for a deliberately non-thermodynamic reverse.
     */
    public double activationEnergyReverse() {
        return activationEnergyForward() - enthalpy();
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

    /**
     * The species this reaction "holds data for": the ones shown in the reactor GUI and treated as
     * meaningful vessel state. Defaults to the union of its reactants and products.
     *
     * <p><b>DESIGN RULE (reaction chaining).</b> When this reaction consumes the PRODUCT of an upstream
     * reaction — i.e. reactors are chained by piping the first reaction's output into a reactor running this
     * one — this set MUST include ALL of the upstream reaction's species. The upstream broth's leftover
     * impurities ride along in the transferred well-mixed mixture; declaring them here keeps them tracked and
     * visible instead of looking deleted. Override and union in the upstream reaction's {@code trackedSpecies()}.
     */
    public Set<Species> trackedSpecies() {
        EnumSet<Species> set = EnumSet.noneOf(Species.class);
        set.addAll(reactants.keySet());
        set.addAll(products.keySet());
        return set;
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
