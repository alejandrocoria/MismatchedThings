package games.alejandrocoria.mismatchedthings.fixture.sibling;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod("workbench_sibling")
public final class SiblingNeoForge {
    public SiblingNeoForge(IEventBus bus) {
        SiblingRecipe.install();
        var serializers = DeferredRegister.<RecipeSerializer<?>>create(Registries.RECIPE_SERIALIZER, "workbench_sibling");
        serializers.register("compose", () -> SiblingRecipe.SERIALIZER);
        serializers.register(bus);
    }
}
