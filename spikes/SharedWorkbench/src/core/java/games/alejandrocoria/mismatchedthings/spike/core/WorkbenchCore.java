package games.alejandrocoria.mismatchedthings.spike.core;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Research API v1: no Boots types, policy objects, global config or sibling enumeration. */
public final class WorkbenchCore {
    public static final String NAMESPACE = "mismatchedthings";
    public static final Identifier MISMATCHED_WORKBENCH_ID = Identifier.fromNamespaceAndPath(NAMESPACE, "mismatched_workbench");
    // NeoForge may construct independent consumer mods concurrently; registration must not lose an owner.
    private static final Map<Identifier, Predicate<ItemStack>> INPUTS = new ConcurrentHashMap<>();
    public static int initializations;

    private WorkbenchCore() {
    }

    public static void register(Identifier owner, Predicate<ItemStack> accepts) {
        if (INPUTS.putIfAbsent(owner, accepts) != null) {
            throw new IllegalStateException("Duplicate contributor: " + owner);
        }
    }

    public static boolean accepts(ItemStack stack) {
        return INPUTS.values().stream().anyMatch(predicate -> predicate.test(stack));
    }

    public static int contributors() {
        return INPUTS.size();
    }

    public static WorkbenchBlock createBlock() {
        return new WorkbenchBlock(BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD)
                .setId(ResourceKey.create(Registries.BLOCK, MISMATCHED_WORKBENCH_ID)));
    }

    public static Item.Properties itemProperties() {
        return new Item.Properties().useBlockDescriptionPrefix()
                .setId(ResourceKey.create(Registries.ITEM, MISMATCHED_WORKBENCH_ID));
    }

    public static MenuType<WorkbenchMenu> createMenu() {
        return new MenuType<>(WorkbenchMenu::new, FeatureFlags.VANILLA_SET);
    }

    public static Optional<Block> mismatchedWorkbenchBlock() {
        return BuiltInRegistries.BLOCK.get(MISMATCHED_WORKBENCH_ID).map(holder -> holder.value());
    }

    @SuppressWarnings("unchecked")
    public static Optional<MenuType<WorkbenchMenu>> mismatchedWorkbenchMenuType() {
        return BuiltInRegistries.MENU.get(MISMATCHED_WORKBENCH_ID)
                .map(holder -> (MenuType<WorkbenchMenu>) holder.value());
    }

    public static RecipeType<WorkbenchRecipe> mismatchedWorkbenchRecipeType() {
        return WorkbenchRecipe.TYPE;
    }
}
