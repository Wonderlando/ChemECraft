package com.wonderlando.chemecraft.reaction;

/** Substances tracked in a reactor. Concentrations are molar (mol/L); molar mass converts to grams. */
public enum Species {
    SUBSTRATE(180.156, 0.0, "Substrate"),    // fermentable sugar (as glucose), dissolved
    BIOMASS(24.6, 0.0, "Biomass"),           // yeast dry cell (approx. CH1.8O0.5N0.2), suspended
    ETHANOL(46.069, 0.789, "Ethanol"),       // product, materialized as ethanol fluid
    CARBON_DIOXIDE(44.01, 0.0, "CO2"),       // product, vented
    ACETIC_ACID(60.052, 0.0, "Acetic Acid"); // souring product, dissolved

    private final double molarMass; // g/mol
    private final double density;   // g/mL when it forms a free liquid (0 = not a standalone fluid)
    private final String displayName;

    Species(double molarMass, double density, String displayName) {
        this.molarMass = molarMass;
        this.density = density;
        this.displayName = displayName;
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
