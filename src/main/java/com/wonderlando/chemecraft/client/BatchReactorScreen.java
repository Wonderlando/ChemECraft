package com.wonderlando.chemecraft.client;

import com.wonderlando.chemecraft.block.entity.BatchReactorBlockEntity;
import com.wonderlando.chemecraft.menu.BatchReactorMenu;
import com.wonderlando.chemecraft.reaction.Reaction;
import com.wonderlando.chemecraft.reaction.Species;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Display screen for the batch reactor: liquids, solutes (amount + concentration), the current reaction
 * rate, and which reaction is occurring. Rate and reaction are computed client-side from the synced state
 * using the same {@link Reaction} classes the server uses.
 *
 * <p>Everything is drawn in {@link #extractContents} using absolute screen coordinates offset by
 * {@code leftPos}/{@code topPos} (the centered GUI origin); {@link #extractLabels} is left empty to
 * suppress the default title/inventory labels.
 */
public class BatchReactorScreen extends AbstractContainerScreen<BatchReactorMenu> {
    private static final int PANEL = 0xF0101418;
    private static final int BORDER = 0xFF000000;
    private static final int HEADER = 0xFFFFC864;
    private static final int TEXT = 0xFFE0E0E0;
    private static final int TITLE = 0xFFFFFFFF;

    public BatchReactorScreen(BatchReactorMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 210, 182);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = this.leftPos;
        int top = this.topPos;

        // Panel + border.
        graphics.fill(left, top, left + imageWidth, top + imageHeight, PANEL);
        graphics.fill(left, top, left + imageWidth, top + 1, BORDER);
        graphics.fill(left, top + imageHeight - 1, left + imageWidth, top + imageHeight, BORDER);
        graphics.fill(left, top, left + 1, top + imageHeight, BORDER);
        graphics.fill(left + imageWidth - 1, top, left + imageWidth, top + imageHeight, BORDER);

        Font f = this.font;
        int x = left + 8;
        int y = top + 8;
        graphics.text(f, this.title, x, y, TITLE, true);
        y += 14;

        int waterMb = menu.value(0);
        int ethanolMb = menu.value(1);
        double substrateMol = menu.value(2) / 1000.0;
        double biomassMol = menu.value(3) / 1000.0;
        double co2Mol = menu.value(4) / 1000.0;
        double volumeL = waterMb / 1000.0;

        graphics.text(f, "Liquids", x, y, HEADER, true);
        y += 10;
        graphics.text(f, "  Water: " + waterMb + " mB", x, y, TEXT, true);
        y += 9;
        graphics.text(f, "  Ethanol: " + ethanolMb + " mB", x, y, TEXT, true);
        y += 13;

        graphics.text(f, "Solutes (amount / concentration)", x, y, HEADER, true);
        y += 10;
        y = solute(graphics, f, x, y, "Substrate", substrateMol, Species.SUBSTRATE, volumeL);
        y = solute(graphics, f, x, y, "Biomass", biomassMol, Species.BIOMASS, volumeL);
        y = solute(graphics, f, x, y, "CO2", co2Mol, Species.CARBON_DIOXIDE, volumeL);
        y += 5;

        double ethanolMol = ethanolMb * Species.ETHANOL.density() / Species.ETHANOL.molarMass();
        double[] concentration = new double[Species.values().length];
        if (volumeL > 0.0) {
            concentration[Species.SUBSTRATE.ordinal()] = substrateMol / volumeL;
            concentration[Species.BIOMASS.ordinal()] = biomassMol / volumeL;
            concentration[Species.CARBON_DIOXIDE.ordinal()] = co2Mol / volumeL;
            concentration[Species.ETHANOL.ordinal()] = ethanolMol / volumeL;
        }
        double totalRate = 0.0;
        String reactionName = "Idle";
        for (Reaction reaction : BatchReactorBlockEntity.NETWORK.reactions()) {
            double rate = volumeL > 0.0 ? reaction.rate(concentration) : 0.0;
            totalRate += rate;
            if (rate > 1.0e-6) {
                reactionName = reaction.displayName();
            }
        }
        graphics.text(f, String.format("Rate: %.4f mol/L/day", totalRate), x, y, TEXT, true);
        y += 10;
        graphics.text(f, "Reaction: " + reactionName, x, y, TEXT, true);
        y += 10;
        for (Reaction reaction : BatchReactorBlockEntity.NETWORK.reactions()) {
            graphics.textWithWordWrap(f, Component.literal("  " + reaction.equation()), x, y, imageWidth - 16, TEXT);
            y += 20;
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // All content is drawn in extractContents; suppress the default title/inventory labels.
    }

    private int solute(GuiGraphicsExtractor graphics, Font f, int x, int y, String name,
                       double mol, Species species, double volumeL) {
        double grams = mol * species.molarMass();
        double concentration = volumeL > 0.0 ? mol / volumeL : 0.0;
        graphics.text(f, String.format("  %s: %.1f g / %.3f M", name, grams, concentration), x, y, TEXT, true);
        return y + 9;
    }
}
