/* Copyright (C) 2026 Rusty Shackleford and nfx. SPDX-License-Identifier: AGPL-3.0-or-later */
package com.chunkworks.magicalmap.integration.emi;

import com.chunkworks.magicalmap.MagicalMap;
import com.chunkworks.magicalmap.domain.AtlasRecipes;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import java.util.ArrayList;
import java.util.List;

/** Shows the atlas's cartography-table operations in EMI under a "Cartography Table" category.
 * Loaded by EMI's entrypoint scan only when EMI is installed; nothing else references it. */
@EmiEntrypoint
public final class MagicalMapEmiPlugin implements EmiPlugin {
    public static final EmiRecipeCategory CARTOGRAPHY = new EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath(MagicalMap.ID, "cartography"), EmiStack.of(Items.CARTOGRAPHY_TABLE));

    /** effects: registers the category, the cartography table as its workstation, and one recipe
     * per {@link AtlasRecipes#ALL} operation. */
    @Override public void register(EmiRegistry registry) {
        registry.addCategory(CARTOGRAPHY);
        registry.addWorkstation(CARTOGRAPHY, EmiStack.of(Items.CARTOGRAPHY_TABLE));
        for (var op : AtlasRecipes.ALL) registry.addRecipe(new TableRecipe(op));
    }
    private static EmiStack stack(String id) { return EmiStack.of(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id))); }

    /** One table operation drawn as first slot, second slot, arrow, one or two results. */
    static final class TableRecipe implements EmiRecipe {
        private final AtlasRecipes.Operation op;
        private final List<EmiIngredient> inputs;
        private final List<EmiStack> outputs;
        TableRecipe(AtlasRecipes.Operation op) {
            this.op = op;
            inputs = List.of(stack(op.first()), stack(op.second()));
            var out = new ArrayList<EmiStack>();
            for (var id : op.outputs()) out.add(stack(id));
            outputs = List.copyOf(out);
        }
        @Override public EmiRecipeCategory getCategory() { return CARTOGRAPHY; }
        /** effects: the operation's id with a leading slash on the path, EMI's marker for a recipe
         * that has no counterpart in the recipe manager. */
        @Override public ResourceLocation getId() {
            var id = ResourceLocation.parse(op.id());
            return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "/" + id.getPath());
        }
        @Override public List<EmiIngredient> getInputs() { return inputs; }
        @Override public List<EmiStack> getOutputs() { return outputs; }
        @Override public int getDisplayWidth() { return 76 + 22 * outputs.size(); }
        @Override public int getDisplayHeight() { return 18; }
        @Override public boolean supportsRecipeTree() { return false; }
        @Override public void addWidgets(WidgetHolder widgets) {
            widgets.addSlot(inputs.get(0), 0, 0);
            widgets.addSlot(inputs.get(1), 22, 0);
            widgets.addTexture(EmiTexture.EMPTY_ARROW, 46, 1);
            for (int i = 0; i < outputs.size(); i++) widgets.addSlot(outputs.get(i), 76 + 22 * i, 0).recipeContext(this);
        }
    }
}
