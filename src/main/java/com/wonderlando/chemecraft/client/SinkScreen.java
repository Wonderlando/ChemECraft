package com.wonderlando.chemecraft.client;

import com.wonderlando.chemecraft.block.entity.SinkBlockEntity;
import com.wonderlando.chemecraft.menu.SinkMenu;
import com.wonderlando.chemecraft.reaction.Species;

import java.util.Arrays;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Live readout for the testing sink: the measured inflow rate and per-species composition of what is being
 * piped in (and discarded), averaged over a ~1 s window. No controls — values come from the synced slots.
 * Species rows are generated from the {@link Species} enum (gases excluded), so new species appear automatically.
 */
public class SinkScreen extends AbstractContainerScreen<SinkMenu> {
    private static final int ROW0_Y = 26;
    private static final int ROW_H = 12;
    /** Non-gas species shown as rows (gases like CO2 vent before reaching the sink, so they're omitted). */
    private static final int SPECIES_SHOWN = (int) Arrays.stream(Species.values()).filter(s -> !s.gas()).count();
    private static final int IMAGE_W = 200;
    // Title + inflow + water + one row per shown species + a footer line, with padding.
    private static final int IMAGE_H = ROW0_Y + ROW_H * (3 + SPECIES_SHOWN) + 10;

    private static final int PANEL = 0xF0101418;
    private static final int BORDER = 0xFF000000;
    private static final int TITLE = 0xFFFFFFFF;
    private static final int HEADER = 0xFFFFC864;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int WATER_COLOR = 0xFF4060C8;

    public SinkScreen(SinkMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, IMAGE_W, IMAGE_H);
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

        graphics.text(f, String.format("Inflow: %.2f L/day", menu.value(SinkBlockEntity.SLOT_RATE) / 100.0),
                x, topPos + ROW0_Y, HEADER, true);
        graphics.text(f, String.format("  Water: %.2f L/day", menu.value(SinkBlockEntity.SLOT_WATER) / 100.0),
                x, topPos + ROW0_Y + ROW_H, WATER_COLOR, true);
        int row = 2;
        for (Species s : Species.values()) {
            if (s.gas()) {
                continue; // gases vent before reaching the sink
            }
            graphics.text(f, String.format("  %s: %.2f mol/day", s.displayName(),
                            menu.value(SinkBlockEntity.SPECIES_BASE + s.ordinal()) / 100.0),
                    x, topPos + ROW0_Y + ROW_H * row, TEXT, true);
            row++;
        }
        graphics.text(f, "(contents discarded)", x, topPos + ROW0_Y + ROW_H * row + 2, 0xFF808080, true);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // All content drawn in extractContents/extractBackground; suppress default labels.
    }
}
