package com.wonderlando.chemecraft.datagen;

import java.util.List;
import java.util.Set;

import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/** Entry point for ChemECraft data generation (registered on the mod event bus). */
public final class ChemECraftDatagen {
    private ChemECraftDatagen() {}

    public static void gatherData(GatherDataEvent.Client event) {
        // Blockstates, block models, and block-item models.
        event.createProvider(ModModelProvider::new);

        // Block loot tables (each block drops itself).
        event.createProvider((output, lookup) -> new LootTableProvider(
                output,
                Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(ModBlockLootProvider::new, LootContextParamSets.BLOCK)),
                lookup));
    }
}
