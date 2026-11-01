package games.alejandrocoria.mismatchedleggings.test.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
public interface GrindstoneXpInvoker {
    @Invoker("getExperienceFromItem")
    int leggings$potential(ItemStack input);
}
