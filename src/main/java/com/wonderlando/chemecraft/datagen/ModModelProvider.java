package com.wonderlando.chemecraft.datagen;

import java.util.stream.Stream;

import com.wonderlando.chemecraft.ChemECraft;
import com.wonderlando.chemecraft.registry.ModBlocks;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.TexturedModel;
import net.minecraft.core.Holder;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

/** Generates blockstates, block models, and (block) item models for ChemECraft. */
public class ModModelProvider extends ModelProvider {
    public ModModelProvider(PackOutput output) {
        super(output, ChemECraft.MODID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // cube_column: top/bottom use the *_top texture, sides use *_side. The BlockItem's model is auto-generated.
        blockModels.createTrivialBlock(ModBlocks.BATCH_REACTOR.get(), TexturedModel.COLUMN);
        // Casing cells share the same look so the 3x3x3 structure reads as one vessel.
        blockModels.createTrivialBlock(ModBlocks.BATCH_REACTOR_CASING.get(), TexturedModel.COLUMN);
        // The pipe's multipart blockstate + models are authored by hand (see assets/.../blockstates/pipe.json);
        // it is excluded from getKnownBlocks below so the generator doesn't expect a generated blockstate. We
        // still register its item model here (pointing at the hand-authored core model) so the auto-generated
        // default — which would point at the non-existent block/pipe — is not emitted.
        blockModels.registerSimpleItemModel(ModBlocks.PIPE.get(), Identifier.parse("chemecraft:block/pipe_core"));
    }

    @Override
    protected Stream<? extends Holder<Block>> getKnownBlocks() {
        return super.getKnownBlocks().filter(holder -> holder.value() != ModBlocks.PIPE.get());
    }
}
