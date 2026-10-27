package games.alejandrocoria.mismatchedthings.spike.boots;



import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod("workbench_boots_consumer")
public final class BootsNeoForge {
    public BootsNeoForge(IEventBus bus) {
        BootsRecipe.install();
        DeferredRegister<RecipeSerializer<?>> serializers = DeferredRegister.create(Registries.RECIPE_SERIALIZER, "workbench_boots_consumer");
        serializers.register("compose", () -> BootsRecipe.SERIALIZER);
        serializers.register(bus);
    }
}
