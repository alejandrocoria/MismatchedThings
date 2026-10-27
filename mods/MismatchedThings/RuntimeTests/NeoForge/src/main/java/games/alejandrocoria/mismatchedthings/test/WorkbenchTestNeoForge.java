package games.alejandrocoria.mismatchedthings.test;

import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod("workbench_test")
public final class WorkbenchTestNeoForge {
    public WorkbenchTestNeoForge(IEventBus bus) {
        WorkbenchChecks.require(!ModList.get().isLoaded("mismatchedboots"), "Core tests must not load Boots");
        if (WorkbenchChecks.RECIPES) {
            MismatchedWorkbench.registerContributor(FixtureRecipe.ID, FixtureRecipe::accepts);
            var serializers = DeferredRegister.<RecipeSerializer<?>>create(Registries.RECIPE_SERIALIZER, "workbench_test");
            serializers.register("compose", () -> FixtureRecipe.SERIALIZER);
            serializers.register(bus);
        }
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> WorkbenchChecks.serverTick(event.getServer()));
        if (FMLEnvironment.getDist().isClient()) {
            bus.addListener(WorkbenchTestClientNeoForge::setup);
        }
    }
}
