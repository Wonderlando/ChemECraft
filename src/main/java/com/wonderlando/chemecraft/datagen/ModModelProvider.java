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
        // The reactor controllers (CSTR + batch) and the shared casing are HAND-AUTHORED
        // (assets/.../blockstates/cstr.json, batch_reactor.json, reactor_casing.json): the casing shows the
        // inlet/outlet rings on the marked cells. They are excluded from getKnownBlocks below so no blockstate
        // is generated; both controllers' BlockItem models are pointed at the shared hand-authored block/reactor.
        blockModels.registerSimpleItemModel(ModBlocks.CSTR.get(), Identifier.parse("chemecraft:block/reactor"));
        blockModels.registerSimpleItemModel(ModBlocks.BATCH_REACTOR.get(), Identifier.parse("chemecraft:block/reactor"));
        // The splitter and the testing reservoir/sink are plain cubes (single texture); item models auto-generate.
        blockModels.createTrivialBlock(ModBlocks.SPLITTER.get(), TexturedModel.CUBE);
        blockModels.createTrivialBlock(ModBlocks.RESERVOIR.get(), TexturedModel.CUBE);
        blockModels.createTrivialBlock(ModBlocks.SINK.get(), TexturedModel.CUBE);
        // The mixer is HAND-AUTHORED (directional: an outlet ring on its FACING face) — see
        // assets/.../blockstates/mixer.json. Excluded from getKnownBlocks below; its item model auto-generates
        // pointing at the hand-authored block/mixer.
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
                        && holder.value() != ModBlocks.MIXER.get()
                        && holder.value() != ModBlocks.CSTR.get()
                        && holder.value() != ModBlocks.BATCH_REACTOR.get()
                        && holder.value() != ModBlocks.REACTOR_CASING.get());
    }
}
