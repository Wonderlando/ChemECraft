package com.wonderlando.chemecraft.reaction;

/** Substances tracked in a reactor. Concentrations are molar (mol/L); molar mass converts to grams. */
public enum Species {
    SUBSTRATE(180.156, 0.0, false, "Substrate"),    // fermentable sugar (as glucose), dissolved
    BIOMASS(24.6, 0.0, false, "Biomass"),           // yeast dry cell (approx. CH1.8O0.5N0.2), suspended
    ETHANOL(46.069, 0.789, false, "Ethanol"),       // product, materialized as ethanol fluid
    CARBON_DIOXIDE(44.01, 0.0, true, "CO2"),         // gas product — vents out of the reactor
    ACETIC_ACID(60.052, 0.0, false, "Acetic Acid"); // souring product, dissolved

    private final double molarMass; // g/mol
    private final double density;   // g/mL when it forms a free liquid (0 = not a standalone fluid)
    private final boolean gas;      // true = escapes the vessel (vented) rather than staying in solution
    private final String displayName;

    Species(double molarMass, double density, boolean gas, String displayName) {
        this.molarMass = molarMass;
        this.density = density;
        this.gas = gas;
        this.displayName = displayName;
    }

    /** True if this species is a gas that vents out of the reactor instead of accumulating. */
    public boolean gas() {
        return gas;
    }

    public double molarMass() {
        return molarMass;
    }

    public double density() {
        return density;
    }

    public String displayName() {
        return displayName;
    }
}
