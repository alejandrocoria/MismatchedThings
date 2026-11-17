package games.alejandrocoria.mismatchedthings.mixin.compatibility.neovitae;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import games.alejandrocoria.mismatchedthings.internal.compatibility.neovitae.NeoForgeNeoVitaeHellforgedService;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.breakinblocks.neovitae.common.item.soul.HellforgedHelper", remap = false)
abstract class HellforgedMixin {
    @ModifyReturnValue(method = "isHellforged(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"), require = 0)
    private static boolean mismatchedthings$hellforged(boolean ordinary, ItemStack stack) {
        return NeoForgeNeoVitaeHellforgedService.read(ordinary, stack);
    }
}
