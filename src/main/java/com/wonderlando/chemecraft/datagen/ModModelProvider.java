package com.wonderlando.chemecraft.datagen;

import java.util.stream.Stream;

import com.wonderlando.chemecraft.ChemECraft;
import com.wonderlando.chemecraft.registry.ModBlocks;

import com.wonderlando.chemecraft.registry.ModItems;

import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.model.ModelTemplates;
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
        // The reactor controller and casing are HAND-AUTHORED (assets/.../blockstates/batch_reactor*.json): the
        // controller shows an outlet ring on its front face and the front-top-centre casing shows an inlet ring,
        // both orientation-dependent. They are excluded from getKnownBlocks below so no blockstate is generated;
        // the controller's BlockItem model still auto-generates (pointing at the hand-authored block/batch_reactor).
        // Branch/merge hubs are plain cubes (single texture all faces); item models auto-generate.
        blockModels.createTrivialBlock(ModBlocks.SPLITTER.get(), TexturedModel.CUBE);
        blockModels.createTrivialBlock(ModBlocks.MIXER.get(), TexturedModel.CUBE);
        // The pipe's multipart blockstate + models are authored by hand (see assets/.../blockstates/pipe.json);
        // it is excluded from getKnownBlocks below so the generator doesn't expect a generated blockstate. We
        // still register its item model here (pointing at the hand-authored core model) so the auto-generated
        // default — which would point at the non-existent block/pipe — is not emitted.
        blockModels.registerSimpleItemModel(ModBlocks.PIPE.get(), Identifier.parse("chemecraft:block/pipe_core"));

        // The wrench is a standard flat inventory item (textures/item/wrench.png).
        itemModels.generateFlatItem(ModItems.WRENCH.get(), ModelTemplates.FLAT_ITEM);
    }

    @Override
    protected Stream<? extends Holder<Block>> getKnownBlocks() {
        return super.getKnownBlocks().filter(holder ->
                holder.value() != ModBlocks.PIPE.get()
                        && holder.value() != ModBlocks.BATCH_REACTOR.get()
                        && holder.value() != ModBlocks.BATCH_REACTOR_CASING.get());
    }
}
