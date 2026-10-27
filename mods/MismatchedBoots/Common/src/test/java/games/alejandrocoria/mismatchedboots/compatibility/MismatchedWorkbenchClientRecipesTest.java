package games.alejandrocoria.mismatchedboots.compatibility;

import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.api.WorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.WorkbenchResult;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MismatchedWorkbenchClientRecipesTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void resetRecipes() {
        MismatchedWorkbenchClientRecipes.reset();
    }

    @Test
    void removalReplacementAndDisconnectUseLoadedAuthority() {
        assertFalse(preview().isComplete());
        MismatchedWorkbenchClientRecipes.install(List.of(holder("original", Items.IRON_BOOTS, Items.DIAMOND_BOOTS)));
        assertTrue(preview().primary().is(Items.DIAMOND_BOOTS));
        MismatchedWorkbenchClientRecipes.install(List.of());
        assertFalse(preview().isComplete());
        MismatchedWorkbenchClientRecipes.install(List.of(holder("replacement", Items.IRON_BOOTS, Items.LEATHER_BOOTS)));
        assertTrue(preview().primary().is(Items.LEATHER_BOOTS));
        MismatchedWorkbenchClientRecipes.reset();
        assertFalse(preview().isComplete());
    }

    @Test
    void selectsFirstMatchingLoadedRecipeRatherThanInventingComposition() {
        MismatchedWorkbenchClientRecipes.install(List.of(
                holder("unmatched", Items.GOLDEN_BOOTS, Items.NETHERITE_BOOTS),
                holder("matching", Items.IRON_BOOTS, Items.LEATHER_BOOTS),
                holder("later", Items.IRON_BOOTS, Items.DIAMOND_BOOTS)));
        assertTrue(preview().primary().is(Items.LEATHER_BOOTS));
        var empty = new WorkbenchInput(ItemStack.EMPTY, ItemStack.EMPTY);
        assertFalse(MismatchedWorkbenchClientRecipes.assemblePair(
                empty, null).isComplete());
    }

    @Test
    void notifiesReloadAndResetButReleasesClosedViewerListener() {
        AtomicInteger changes = new AtomicInteger();
        Runnable remove = MismatchedWorkbenchClientRecipes.addListener(changes::incrementAndGet);
        try {
            MismatchedWorkbenchClientRecipes.install(List.of(holder("first", Items.IRON_BOOTS, Items.DIAMOND_BOOTS)));
            MismatchedWorkbenchClientRecipes.install(List.of());
            MismatchedWorkbenchClientRecipes.reset();
            assertEquals(3, changes.get());
            remove.run();
            MismatchedWorkbenchClientRecipes.install(List.of(holder("next", Items.IRON_BOOTS, Items.LEATHER_BOOTS)));
            assertEquals(3, changes.get());
        } finally {
            remove.run();
        }
    }

    private static WorkbenchResult preview() {
        return MismatchedWorkbenchClientRecipes.assemblePair(new WorkbenchInput(
                                                                     stack(Items.IRON_BOOTS), stack(Items.GOLDEN_BOOTS)),
                                                             null);
    }

    private static ItemStack stack(Item item) {
        // Bootstrap-only JUnit does not bind data-driven item prototypes.
        return new ItemStack(Holder.direct(item, DataComponentMap.builder()
                                                                 .set(DataComponents.MAX_STACK_SIZE, 1).build()));
    }

    private static RecipeHolder<WorkbenchRecipe> holder(String id, Item expected, Item output) {
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Identifier.parse("test:" + id)),
                                  new LoadedRecipe(expected, output));
    }

    private record LoadedRecipe(Item expected, Item output) implements WorkbenchRecipe {
        @Override
        public boolean matches(WorkbenchInput input, Level level) {
            return input.left().is(expected);
        }

        @Override
        public WorkbenchResult assemblePair(WorkbenchInput input) {
            return new WorkbenchResult(stack(output), stack(Items.GOLDEN_BOOTS));
        }

        @Override
        public RecipeSerializer<MixedBootsWorkbenchRecipe> getSerializer() {
            return MixedBootsWorkbenchRecipe.SERIALIZER;
        }
    }
}
