package com.wonderlando.chemecraft.datagen;

import java.util.Set;

import com.wonderlando.chemecraft.registry.ModBlocks;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

/** Block loot tables — every ChemECraft block drops itself. */
public class ModBlockLootProvider extends BlockLootSubProvider {
    public ModBlockLootProvider(HolderLookup.Provider lookupProvider) {
        super(Set.of(), FeatureFlags.DEFAULT_FLAGS, lookupProvider);
    }

    @Override
    protected void generate() {
        dropSelf(ModBlocks.BATCH_REACTOR.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ModBlocks.BLOCKS.getEntries().stream().map(holder -> (Block) holder.get()).toList();
    }
}
