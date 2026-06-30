package com.wonderlando.chemecraft.client;

import com.wonderlando.chemecraft.block.entity.ReservoirBlockEntity;
import com.wonderlando.chemecraft.menu.ReservoirMenu;
import com.wonderlando.chemecraft.reaction.Species;

import java.util.Arrays;
import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Config screen for the testing reservoir: a feed recipe — each species as a molar concentration with its
 * mass concentration (mol/L and g/L) — plus a flow-rate stepper with a unit selector (L/min / L/hr / L/day /
 * L/s) and an Emit toggle. Species rows (and their +/- steppers) are generated from the {@link Species} enum
 * (gases excluded), so a new species appears here automatically with no per-species wiring.
 */
public class ReservoirScreen extends AbstractContainerScreen<ReservoirMenu> {
    /** Species offered as feed components (gases like CO2 vent immediately, so they're not feedable). */
    private static final List<Species> FEED_SPECIES =
            Arrays.stream(Species.values()).filter(s -> !s.gas()).toList();
    private static final int SPECIES_ROWS = FEED_SPECIES.size();
    private static final int RATE_ROW = SPECIES_ROWS; // the flow-rate stepper sits just below the species rows

    private static final int ROW0_Y = 26;
    private static final int ROW_H = 20;
    private static final int IMAGE_W = 272;
    // Title + one row per species + the flow row + a status line + the Emit button, with padding.
    private static final int IMAGE_H = ROW0_Y + (RATE_ROW + 1) * ROW_H + 44;
    private static final int BTN_W = 20;
    private static final int BTN_H = 16;
    private static final int DEC_X = 212;
    private static final int INC_X = 236;
    private static final int UNIT_BTN_X = 156;
    private static final int UNIT_BTN_W = 52;

    private static final int PANEL = 0xF0101418;
    private static final int BORDER = 0xFF000000;
    private static final int TITLE = 0xFFFFFFFF;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int WATER_COLOR = 0xFF4060C8;
    private static final int DEST_OK = 0xFF60C878;
    private static final int DEST_NO = 0xFFC86060;

    private Button emitButton;
    private Button unitButton;

    public ReservoirScreen(ReservoirMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, IMAGE_W, IMAGE_H);
    }

    @Override
    protected void init() {
        super.init();
        // One -/+ stepper pair per feed species, addressed generically by ordinal.
        for (int i = 0; i < SPECIES_ROWS; i++) {
            Species s = FEED_SPECIES.get(i);
            int y = topPos + ROW0_Y + i * ROW_H;
            int dec = ReservoirMenu.speciesButton(s, false);
            int inc = ReservoirMenu.speciesButton(s, true);
            addRenderableWidget(Button.builder(Component.literal("-"), b -> send(dec))
                    .bounds(leftPos + DEC_X, y, BTN_W, BTN_H).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> send(inc))
                    .bounds(leftPos + INC_X, y, BTN_W, BTN_H).build());
        }
        // Flow-rate stepper + unit selector on the rate row.
        int rateRowY = topPos + ROW0_Y + RATE_ROW * ROW_H;
        addRenderableWidget(Button.builder(Component.literal("-"), b -> send(ReservoirMenu.BTN_RATE_DEC))
                .bounds(leftPos + DEC_X, rateRowY, BTN_W, BTN_H).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> send(ReservoirMenu.BTN_RATE_INC))
                .bounds(leftPos + INC_X, rateRowY, BTN_W, BTN_H).build());
        unitButton = Button.builder(Component.literal("L/min"), b -> send(ReservoirMenu.BTN_UNIT_CYCLE))
                .bounds(leftPos + UNIT_BTN_X, rateRowY, UNIT_BTN_W, BTN_H).build();
        addRenderableWidget(unitButton);

        emitButton = Button.builder(Component.literal("Emit"), b -> send(ReservoirMenu.BTN_EMIT_TOGGLE))
                .bounds(leftPos + 10, topPos + IMAGE_H - 24, IMAGE_W - 20, 18).build();
        addRenderableWidget(emitButton);
    }

    private void send(int id) {
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
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
        super.extractContents(graphics, mouseX, mouseY, partialTick);

        Font f = this.font;
        int x = leftPos + 10;
        graphics.text(f, this.title, x, topPos + 8, TITLE, true);

        for (int i = 0; i < SPECIES_ROWS; i++) {
            Species s = FEED_SPECIES.get(i);
            row(graphics, f, x, i, speciesLabel(s), TEXT);
        }

        // Flow row: convert from internal milli-L/min to the selected display unit.
        int unitOrdinal = menu.value(ReservoirBlockEntity.SLOT_UNIT);
        double lpm = menu.value(ReservoirBlockEntity.SLOT_RATE) / 1000.0;
        double displayRate = lpm * ReservoirBlockEntity.RATE_UNIT_FACTOR[unitOrdinal];
        String unitName = ReservoirBlockEntity.RATE_UNIT_NAMES[unitOrdinal];
        unitButton.setMessage(Component.literal(unitName));
        row(graphics, f, x, RATE_ROW, String.format("Flow: %.2f %s  (aqueous)", displayRate, unitName), WATER_COLOR);

        boolean emitting = menu.value(ReservoirBlockEntity.SLOT_RELEASING) == 1;
        boolean hasDest = menu.value(ReservoirBlockEntity.SLOT_HAS_DEST) == 1;
        emitButton.active = emitting || hasDest;
        emitButton.setMessage(Component.literal(emitting ? "Stop" : (hasDest ? "Emit" : "Emit (no outlet)")));

        int statusY = topPos + ROW0_Y + (RATE_ROW + 1) * ROW_H + 2;
        graphics.text(f, hasDest ? "Outlet connected" : "No outlet detected", x, statusY,
                hasDest ? DEST_OK : DEST_NO, true);
    }

    private void row(GuiGraphicsExtractor graphics, Font f, int x, int i, String label, int color) {
        graphics.text(f, label, x, topPos + ROW0_Y + i * ROW_H + 4, color, true);
    }

    /** "<name>: <conc> mol/L (<mass> g/L)" from the species' synced milli-mol/L slot and its molar mass. */
    private String speciesLabel(Species s) {
        double conc = menu.value(ReservoirBlockEntity.SPECIES_BASE + s.ordinal()) / 1000.0;
        return String.format("%s: %.2f mol/L (%.1f g/L)", s.displayName(), conc, conc * s.molarMass());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // All content drawn in extractContents/extractBackground; suppress default labels.
    }
}
