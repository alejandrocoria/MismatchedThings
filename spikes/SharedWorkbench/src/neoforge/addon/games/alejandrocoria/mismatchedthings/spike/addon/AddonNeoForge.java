package games.alejandrocoria.mismatchedthings.spike.addon;



import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod("workbench_addon_consumer")
public final class AddonNeoForge {
    public AddonNeoForge(IEventBus bus) {
        AddonRecipe.install();
        DeferredRegister<RecipeSerializer<?>> serializers = DeferredRegister.create(Registries.RECIPE_SERIALIZER, "workbench_addon_consumer");
        serializers.register("compose", () -> AddonRecipe.SERIALIZER);
        serializers.register(bus);
    }
}
