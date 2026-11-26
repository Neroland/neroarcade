package za.co.neroland.neroarcade.registry;

//? if <1.20.5 {
/*import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.Codec;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import za.co.neroland.neroarcade.NeroArcadeCommon;
import za.co.neroland.neroarcade.item.ModuleChips;
import za.co.neroland.neroarcade.module.ModuleRegistry;
import za.co.neroland.nerolandcore.registry.RegistrationProvider;

public final class LegacyChipRecipe extends ShapelessRecipe {
    private static final RecipeSerializer<ShapelessRecipe> VANILLA = RecipeSerializer.SHAPELESS_RECIPE;
    private static final RegistrationProvider.RegistryEntry<RecipeSerializer<LegacyChipRecipe>> SERIALIZER =
            RegistrationProvider.get(Registries.RECIPE_SERIALIZER, NeroArcadeCommon.MOD_ID)
                    .register("chip_shapeless", key -> new Serializer());

    private final ShapelessRecipe recipe;
    private final Identifier module;

    private LegacyChipRecipe(ShapelessRecipe recipe, Identifier module) {
        //? if >=1.20.2 {
        super(recipe.getGroup(), recipe.category(), chip(recipe, module), recipe.getIngredients());
        //?} else {
        super(recipe.getId(), recipe.getGroup(), recipe.category(), chip(recipe, module), recipe.getIngredients());
        //?}
        this.recipe = recipe;
        this.module = module;
    }

    private static ItemStack chip(ShapelessRecipe recipe, Identifier id) {
        ItemStack result = recipe.getResultItem(RegistryAccess.EMPTY);
        if (!result.is(ModItems.MODULE_CHIP.get())) {
            throw new JsonSyntaxException("A module recipe must output a module chip");
        }
        ItemStack chip = ModuleChips.stackFor(ModuleRegistry.get(id)
                .orElseThrow(() -> new JsonSyntaxException("Unknown arcade module: " + id)));
        chip.setCount(result.getCount());
        return chip;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER.get();
    }

    static void init() {
    }

    private static final class Serializer implements RecipeSerializer<LegacyChipRecipe> {
        //? if >=1.20.2 {
        @Override
        public Codec<LegacyChipRecipe> codec() {
            return RecordCodecBuilder.create(instance -> instance.group(
                    VANILLA.codec().fieldOf("recipe").forGetter(recipe -> recipe.recipe),
                    Identifier.CODEC.fieldOf("module").forGetter(recipe -> recipe.module)
            ).apply(instance, LegacyChipRecipe::new));
        }

        @Override
        public LegacyChipRecipe fromNetwork(FriendlyByteBuf buf) {
            return new LegacyChipRecipe(VANILLA.fromNetwork(buf), buf.readResourceLocation());
        }
        //?} else {
        @Override
        public LegacyChipRecipe fromJson(Identifier id, JsonObject json) {
            return new LegacyChipRecipe(VANILLA.fromJson(id, GsonHelper.getAsJsonObject(json, "recipe")),
                    Identifier.tryParse(GsonHelper.getAsString(json, "module")));
        }

        @Override
        public LegacyChipRecipe fromNetwork(Identifier id, FriendlyByteBuf buf) {
            return new LegacyChipRecipe(VANILLA.fromNetwork(id, buf), buf.readResourceLocation());
        }
        //?}

        @Override
        public void toNetwork(FriendlyByteBuf buf, LegacyChipRecipe recipe) {
            VANILLA.toNetwork(buf, recipe.recipe);
            buf.writeResourceLocation(recipe.module);
        }
    }
}
*///?}
