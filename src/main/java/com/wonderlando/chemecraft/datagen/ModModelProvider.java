package com.wonderlando.chemecraft.datagen;

import com.wonderlando.chemecraft.ChemECraft;
import com.wonderlando.chemecraft.registry.ModBlocks;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.data.PackOutput;

/** Generates blockstates, block models, and (block) item models for ChemECraft. */
public class ModModelProvider extends ModelProvider {
    public ModModelProvider(PackOutput output) {
        super(output, ChemECraft.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // cube_column: top/bottom use the *_top texture, sides use *_side. The BlockItem's model is auto-generated.
        blockModels.createTrivialBlock(ModBlocks.BATCH_REACTOR.get(), TexturedModel.COLUMN);
    }
}
