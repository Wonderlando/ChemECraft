package com.wonderlando.chemecraft.client;

import com.wonderlando.chemecraft.block.entity.TankReactorBlockEntity;
import com.wonderlando.chemecraft.menu.ReactorMenu;
import com.wonderlando.chemecraft.reaction.Reaction;
import com.wonderlando.chemecraft.reaction.ReactionRegistry;
import com.wonderlando.chemecraft.reaction.Species;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Inventory;

/**
 * Display + control screen for the batch reactor. A vertical level gauge on the far left shows how full the
 * vessel is (out of its litre capacity); the left column shows the selected reaction, liquids, solutes
 * (amount + concentration), and the current rate; the right side shows a live rolling plot of concentrations
 * (or the reaction rate) over time. The plot history is sampled client-side while the GUI is open, so it
 * resets when the screen is closed.
 */
public class ReactorScreen extends AbstractContainerScreen<ReactorMenu> {
    // Synced display-slot indices (see TankReactorBlockEntity#getDisplaySlot). Species amounts live in a
    // contiguous range starting at SPECIES_BASE, addressed by Species#ordinal() — no per-species constants.
    private static final int SLOT_WATER_MB = TankReactorBlockEntity.SLOT_WATER;
    private static final int SLOT_SELECTED = TankReactorBlockEntity.SLOT_SELECTED;
    private static final int SLOT_TEMP_DK = TankReactorBlockEntity.SLOT_TEMP_DK;
    private static final int SLOT_RELEASING = TankReactorBlockEntity.SLOT_RELEASING;
    private static final int SLOT_HAS_DEST = TankReactorBlockEntity.SLOT_HAS_DEST;
    private static final int SPECIES_BASE = TankReactorBlockEntity.SPECIES_BASE;

    private static final int IMAGE_W = 380;
    private static final int IMAGE_H = 220;
    private static final int LEFT_W = 206; // width of the left (readout + controls) column

    // Layout of the left column: a thin level gauge, then the text/controls content.
    private static final int GAUGE_X = 8;   // gauge left edge, relative to panel origin
    private static final int GAUGE_W = 8;
    private static final int GAUGE_TOP = 22;
    private static final int GAUGE_BOTTOM = IMAGE_H - 30;
    private static final int CONTENT_X = GAUGE_X + GAUGE_W + 6; // text/buttons start here
    private static final int CONTENT_W = LEFT_W - CONTENT_X - 8;

    private static final int CAPACITY_MB = TankReactorBlockEntity.CAPACITY_MB;
    private static final int MB_PER_LITER = TankReactorBlockEntity.MB_PER_LITER; // 1 L = 1000 mB (1 bucket = 1 L)

    private static final int HISTORY = 240;     // samples retained (~2 min at SAMPLE_TICKS = 10)
    private static final int SAMPLE_TICKS = 10;  // take a sample every N client ticks

    private static final int PANEL = 0xF0101418;
    private static final int PLOT_BG = 0xFF0A0D10;
    private static final int BORDER = 0xFF000000;
    private static final int HEADER = 0xFFFFC864;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int NEUTRAL = TEXT & 0xFFFFFF; // RGB form for styled Components
    private static final int AXIS = 0xFF808080;
    private static final int TITLE = 0xFFFFFFFF;
    private static final int RATE_COLOR = 0xFFFFFFFF;
    private static final int WATER_COLOR = 0xFF4060C8;
    private static final int TEMP_COLOR = 0xFFFF9050; // warm orange for the temperature readout

    private Button selector;
    private Button plotToggle;
    private Button releaseButton;
    private boolean showRate = false;

    // Rolling history (newest sample at index HISTORY-1).
    private final double[][] concHistory = new double[Species.values().length][HISTORY];
    private final double[] rateHistory = new double[HISTORY];
    private int samples = 0;
    private int tickCounter = 0;

    public ReactorScreen(ReactorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, IMAGE_W, IMAGE_H);
    }

    @Override
    protected void init() {
        super.init();
        // Cycle button: click advances to the next reaction in the registry (wrapping).
        selector = Button.builder(Component.literal("Reaction: none"), b -> {
            int current = menu.value(SLOT_SELECTED);
            int next = (current < 0) ? 0 : (current + 1) % ReactionRegistry.AVAILABLE.size();
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, next);
        }).bounds(leftPos + CONTENT_X, topPos + 22, CONTENT_W, 18).build();
        addRenderableWidget(selector);

        // Bottom row, split in two: Release (push contents to a piped reactor) on the left, Empty on the right.
        int rowY = topPos + IMAGE_H - 26;
        int halfW = (CONTENT_W - 4) / 2;
        releaseButton = Button.builder(Component.literal("Release"), b ->
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, ReactorMenu.BUTTON_RELEASE))
                .bounds(leftPos + CONTENT_X, rowY, halfW, 18).build();
        addRenderableWidget(releaseButton);

        Button emptyButton = Button.builder(Component.literal("Empty"), b ->
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, ReactorMenu.BUTTON_EMPTY))
                .bounds(leftPos + CONTENT_X + halfW + 4, rowY, CONTENT_W - halfW - 4, 18).build();
        addRenderableWidget(emptyButton);

        // Plot mode toggle (client-only display state).
        plotToggle = Button.builder(Component.literal("Show: Concentrations"), b -> showRate = !showRate)
                .bounds(leftPos + LEFT_W + 4, topPos + 8, IMAGE_W - LEFT_W - 12, 14).build();
        addRenderableWidget(plotToggle);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (++tickCounter % SAMPLE_TICKS != 0) {
            return;
        }
        double[] conc = currentConcentrations();
        double rate = currentRate(conc);
        for (double[] series : concHistory) {
            System.arraycopy(series, 1, series, 0, HISTORY - 1);
        }
        System.arraycopy(rateHistory, 1, rateHistory, 0, HISTORY - 1);
        for (Species s : Species.values()) {
            concHistory[s.ordinal()][HISTORY - 1] = conc[s.ordinal()];
        }
        rateHistory[HISTORY - 1] = rate;
        if (samples < HISTORY) {
            samples++;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int left = this.leftPos;
        int top = this.topPos;
        graphics.fill(left, top, left + imageWidth, top + imageHeight, PANEL);
        graphics.fill(left, top, left + imageWidth, top + 1, BORDER);
        graphics.fill(left, top + imageHeight - 1, left + imageWidth, top + imageHeight, BORDER);
        graphics.fill(left, top, left + 1, top + imageHeight, BORDER);
        graphics.fill(left + imageWidth - 1, top, left + imageWidth, top + imageHeight, BORDER);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractContents(graphics, mouseX, mouseY, partialTick); // submits the button widgets

        int left = this.leftPos;
        int top = this.topPos;
        Font f = this.font;
        int x = left + CONTENT_X;

        Reaction reaction = ReactionRegistry.byIndex(menu.value(SLOT_SELECTED));
        int waterMb = menu.value(SLOT_WATER_MB);
        double[] conc = currentConcentrations();

        boolean empty = waterMb == 0;
        for (Species s : Species.values()) {
            if (menu.value(SPECIES_BASE + s.ordinal()) != 0) {
                empty = false;
                break;
            }
        }
        selector.active = empty;
        String reactionName = (reaction == null) ? "none" : reaction.displayName();
        String hint = empty
                ? (reaction == null ? " (click to choose)" : " (click to change)")
                : " (locked - empty to change)";
        selector.setMessage(Component.literal("Reaction: " + reactionName + hint));
        plotToggle.setMessage(Component.literal(showRate ? "Show: Rate" : "Show: Concentrations"));

        boolean releasing = menu.value(SLOT_RELEASING) == 1;
        boolean hasDest = menu.value(SLOT_HAS_DEST) == 1;
        releaseButton.active = releasing || hasDest; // can always stop; can only start with a destination
        releaseButton.setMessage(Component.literal(
                releasing ? "Stop Release" : (hasDest ? "Release" : "No Outlet")));

        graphics.text(f, this.title, x, top + 8, TITLE, true);

        drawGauge(graphics, f, waterMb);

        int y = top + 44;
        if (reaction != null) {
            Component eq = equationComponent(reaction);
            graphics.textWithWordWrap(f, eq, x, y, CONTENT_W, TEXT);
            y += f.split(eq, CONTENT_W).size() * 9 + 6;
        } else {
            graphics.text(f, "Pick a reaction above to start.", x, y, TEXT, true);
            y += 14;
        }

        double tempK = menu.value(SLOT_TEMP_DK) / 10.0;
        graphics.text(f, String.format("Temp: %.1f K  (%.1f °C)", tempK, tempK - 273.15), x, y, TEMP_COLOR, true);
        y += 13;

        // Show only the species the selected reaction involves (plus water, the solvent).
        Set<Species> involved = involvedSpecies(reaction);

        graphics.text(f, "Liquids", x, y, HEADER, true);
        y += 10;
        graphics.text(f, String.format("  Water: %.1f L", waterMb / (double) MB_PER_LITER), x, y, WATER_COLOR, true);
        y += 9;
        for (Species s : Species.values()) {
            if (involved.contains(s) && ReactionRegistry.LIQUID_SPECIES.contains(s)) {
                graphics.text(f, String.format("  %s: %.1f L", s.displayName(), liquidMb(s) / (double) MB_PER_LITER),
                        x, y, seriesColor(s), true);
                y += 9;
            }
        }
        graphics.text(f, String.format("  Fill: %.1f / %d L", waterMb / (double) MB_PER_LITER, CAPACITY_MB / MB_PER_LITER),
                x, y, TEXT, true);
        y += 13;

        boolean anySolute = false;
        for (Species s : Species.values()) {
            if (!involved.contains(s) || ReactionRegistry.LIQUID_SPECIES.contains(s)) {
                continue;
            }
            if (!anySolute) {
                graphics.text(f, "Solutes (amount / conc)", x, y, HEADER, true);
                y += 10;
                anySolute = true;
            }
            y = solute(graphics, f, x, y, s, molOf(s), conc);
        }
        y += 5;

        double rate = currentRate(conc);
        graphics.text(f, String.format("Rate: %.4f mol/L/s", rate), x, y, TEXT, true);

        drawPlot(graphics, f, involved);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // All content is drawn in extractContents / extractBackground; suppress default labels.
    }

    /** A vertical level gauge: liquid fill (water, the only fluid phase) out of the vessel capacity. */
    private void drawGauge(GuiGraphicsExtractor graphics, Font f, int waterMb) {
        int gx0 = leftPos + GAUGE_X;
        int gx1 = gx0 + GAUGE_W;
        int gy0 = topPos + GAUGE_TOP;
        int gy1 = topPos + GAUGE_BOTTOM;

        graphics.fill(gx0, gy0, gx1, gy1, PLOT_BG);

        int innerTop = gy0 + 1;
        int innerBottom = gy1 - 1;
        int innerH = innerBottom - innerTop;

        int waterH = Math.min(innerH, (int) Math.round(Math.min(waterMb, CAPACITY_MB) / (double) CAPACITY_MB * innerH));
        if (waterH > 0) {
            graphics.fill(gx0 + 1, innerBottom - waterH, gx1 - 1, innerBottom, WATER_COLOR);
        }

        // Tick marks at each third of capacity (9 L intervals), then the border on top.
        graphics.fill(gx0 + 1, innerTop + innerH / 3, gx1 - 1, innerTop + innerH / 3 + 1, AXIS);
        graphics.fill(gx0 + 1, innerTop + 2 * innerH / 3, gx1 - 1, innerTop + 2 * innerH / 3 + 1, AXIS);
        graphics.fill(gx0, gy0, gx1, gy0 + 1, BORDER);
        graphics.fill(gx0, gy1 - 1, gx1, gy1, BORDER);
        graphics.fill(gx0, gy0, gx0 + 1, gy1, BORDER);
        graphics.fill(gx1 - 1, gy0, gx1, gy1, BORDER);
    }

    /** The reaction equation as a styled component, with each species name in its plot/readout colour. */
    private static Component equationComponent(Reaction reaction) {
        MutableComponent c = Component.empty();
        appendSide(c, reaction.reactants());
        c.append(Component.literal("  " + reaction.arrow() + "  ").withColor(NEUTRAL));
        appendSide(c, reaction.products());
        return c;
    }

    private static void appendSide(MutableComponent out, Map<Species, Integer> terms) {
        boolean[] first = {true};
        terms.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getKey().ordinal()))
                .forEach(e -> {
                    if (!first[0]) {
                        out.append(Component.literal(" + ").withColor(NEUTRAL));
                    }
                    first[0] = false;
                    if (e.getValue() != 1) {
                        out.append(Component.literal(e.getValue() + " ").withColor(NEUTRAL));
                    }
                    out.append(Component.literal(e.getKey().displayName()).withColor(seriesColor(e.getKey()) & 0xFFFFFF));
                });
    }

    private void drawPlot(GuiGraphicsExtractor graphics, Font f, Set<Species> involved) {
        int bx0 = leftPos + LEFT_W + 4;
        int bx1 = leftPos + IMAGE_W - 8;
        int by0 = topPos + 28;
        int by1 = topPos + IMAGE_H - 10;

        graphics.fill(bx0, by0, bx1, by1, PLOT_BG);
        graphics.fill(bx0, by0, bx1, by0 + 1, BORDER);
        graphics.fill(bx0, by1 - 1, bx1, by1, BORDER);
        graphics.fill(bx0, by0, bx0 + 1, by1, BORDER);
        graphics.fill(bx1 - 1, by0, bx1, by1, BORDER);

        int px0 = bx0 + 3;
        int px1 = bx1 - 3;
        int py0 = by0 + 10;
        int py1 = by1 - 9;

        double max = 1.0e-9;
        int first = HISTORY - samples;
        if (showRate) {
            for (int k = first; k < HISTORY; k++) {
                max = Math.max(max, rateHistory[k]);
            }
        } else {
            for (Species s : involved) {
                for (int k = first; k < HISTORY; k++) {
                    max = Math.max(max, concHistory[s.ordinal()][k]);
                }
            }
        }
        if (max <= 0.0) {
            max = 1.0;
        }

        // Y-axis: max value at top (with units), 0 at the bottom.
        String yUnit = showRate ? "mol/L/s" : "mol/L";
        graphics.text(f, String.format("%.3g %s", max, yUnit), px0, by0 + 1, AXIS, false);
        graphics.text(f, "0", px0, by1 - 9, AXIS, false);

        if (showRate) {
            drawSeries(graphics, rateHistory, px0, px1, py0, py1, max, RATE_COLOR);
        } else {
            for (Species s : involved) {
                drawSeries(graphics, concHistory[s.ordinal()], px0, px1, py0, py1, max, seriesColor(s));
            }
        }
    }

    private void drawSeries(GuiGraphicsExtractor graphics, double[] data, int px0, int px1, int py0, int py1,
                            double max, int color) {
        if (samples < 2) {
            return;
        }
        int plotW = px1 - px0;
        int plotH = py1 - py0;
        int first = HISTORY - samples;
        int prevX = 0;
        int prevY = 0;
        for (int k = first; k < HISTORY; k++) {
            int idx = k - first;
            int xPix = px0 + idx * plotW / (samples - 1);
            double frac = Math.min(1.0, Math.max(0.0, data[k] / max));
            int yPix = py1 - (int) Math.round(frac * plotH);
            if (idx > 0) {
                graphics.fill(prevX, Math.min(prevY, yPix), xPix + 1, Math.max(prevY, yPix) + 1, color);
            }
            prevX = xPix;
            prevY = yPix;
        }
    }

    /** Current molar concentrations (mol/L) indexed by {@link Species#ordinal()}, from the synced values. */
    private double[] currentConcentrations() {
        double[] c = new double[Species.values().length];
        double v = menu.value(SLOT_WATER_MB) / 1000.0;
        if (v > 0.0) {
            for (Species s : Species.values()) {
                c[s.ordinal()] = molOf(s) / v;
            }
        }
        return c;
    }

    private double currentRate(double[] conc) {
        Reaction reaction = ReactionRegistry.byIndex(menu.value(SLOT_SELECTED));
        double tempK = menu.value(SLOT_TEMP_DK) / 10.0;
        return (reaction != null && menu.value(SLOT_WATER_MB) > 0) ? reaction.rate(conc, tempK) : 0.0;
    }

    /**
     * Species the reactor shows for this reaction: the reaction's {@link Reaction#trackedSpecies()} (its own
     * reactants/products plus any upstream species it holds data for), minus vented gases. Empty when none.
     */
    private static Set<Species> involvedSpecies(Reaction reaction) {
        if (reaction == null) {
            return Set.of();
        }
        EnumSet<Species> set = EnumSet.noneOf(Species.class);
        set.addAll(reaction.trackedSpecies());
        set.removeIf(Species::gas); // vented gases (CO2) escape rather than accumulate — don't display them
        return set;
    }

    /** Moles of a species from the synced display values (the per-species slot at {@code SPECIES_BASE + ordinal}). */
    private double molOf(Species species) {
        return menu.value(SPECIES_BASE + species.ordinal()) / 1000.0;
    }

    /** Volume (mB) of a species that is held as a liquid in the tank (none currently — all stay dissolved). */
    private int liquidMb(Species species) {
        return (int) Math.round(molOf(species) * species.molarMass() / species.density());
    }

    private int solute(GuiGraphicsExtractor graphics, Font f, int x, int y, Species species, double mol, double[] conc) {
        double grams = mol * species.molarMass();
        graphics.text(f, String.format("  %s: %.1f g / %.3f M", species.displayName(), grams, conc[species.ordinal()]),
                x, y, seriesColor(species), true);
        return y + 9;
    }

    private static int seriesColor(Species species) {
        return switch (species) {
            case SUBSTRATE -> 0xFFFFC864;
            case BIOMASS -> 0xFF7FE07F;
            case ETHANOL -> 0xFF7FB8FF;
            case CARBON_DIOXIDE -> 0xFFB0B0B0;
            case ACETIC_ACID -> 0xFFFF8080;
            case SPECIES_A -> 0xFFC890FF;
            case SPECIES_B -> 0xFF90FFD0;
            default -> 0xFFB0B0B0; // any future species: neutral gray until given a colour (no crash, no headache)
        };
    }
}
