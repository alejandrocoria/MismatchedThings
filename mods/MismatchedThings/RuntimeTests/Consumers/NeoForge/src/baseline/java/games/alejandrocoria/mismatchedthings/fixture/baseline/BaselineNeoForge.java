package games.alejandrocoria.mismatchedthings.fixture.baseline;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod("workbench_baseline")
public final class BaselineNeoForge {
    public BaselineNeoForge(IEventBus bus) {
        BaselineRecipe.install();
        var serializers = DeferredRegister.<RecipeSerializer<?>>create(Registries.RECIPE_SERIALIZER, "workbench_baseline");
        serializers.register("compose", () -> BaselineRecipe.SERIALIZER);
        serializers.register(bus);
    }
}
