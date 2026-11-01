package games.alejandrocoria.mismatchedleggings.test;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

@JeiPlugin
public final class JeiViewerProbe implements IModPlugin, ViewerProbe {
    private static IJeiRuntime runtime;

    @Override
    public Identifier getPluginUid() {
        return Identifier.parse("leggings_skeleton_test:viewer_probe");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime current) {
        runtime = current;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    @Override
    public boolean ready() {
        return runtime != null;
    }

    @Override
    public List<Cycle> cycles(String namespace) {
        return runtime.getRecipeManager().createRecipeLookup(type(namespace)).get()
                .map(example -> new Cycle(stacks(example, "leftInputs"), stacks(example, "rightInputs"),
                        stacks(example, "primaryOutputs"), stacks(example, "inverseOutputs")))
                .filter(cycle -> namespace.equals("mismatchedthings") || BuiltInRegistries.ITEM.getKey(cycle.primary().getFirst().getItem()).getNamespace().equals(namespace))
                .toList();
    }

    @Override
    public void open(String namespace) {
        runtime.getRecipesGui().showTypes(List.of(type(namespace)));
    }

    @Override
    public void verifyCategories(boolean legacyBoots) {
        var manager = runtime.getRecipeManager();
        if (manager.getRecipeType(Identifier.parse("mismatchedthings:mismatched_workbench")).isEmpty()
                || manager.getRecipeType(Identifier.parse("mismatchedleggings:mismatched_workbench")).isPresent()
                || manager.getRecipeType(Identifier.parse("mismatchedboots:mismatched_workbench")).isPresent() != legacyBoots) {
            throw new AssertionError("Unexpected shared/legacy JEI categories");
        }
    }

    private static IRecipeType<?> type(String namespace) {
        String owner = namespace.equals("mismatchedboots") && Boolean.getBoolean("leggings.test.previousBoots")
                ? namespace : "mismatchedthings";
        return runtime.getRecipeManager().getRecipeType(Identifier.fromNamespaceAndPath(owner, "mismatched_workbench")).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> stacks(Object example, String name) {
        try {
            var method = example.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return ((List<ItemStack>) method.invoke(example)).stream().map(ItemStack::copy).toList();
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Cannot inspect actual JEI example", failure);
        }
    }
}
