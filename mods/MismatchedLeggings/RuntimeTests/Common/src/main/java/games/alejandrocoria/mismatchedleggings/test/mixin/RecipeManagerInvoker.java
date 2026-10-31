package games.alejandrocoria.mismatchedleggings.test.mixin;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RecipeManager.class)
public interface RecipeManagerInvoker {
    @Invoker("apply")
    void leggings$apply(RecipeMap recipes, ResourceManager resources, ProfilerFiller profiler);
}
