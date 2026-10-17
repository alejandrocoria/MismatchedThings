package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.JustDireInventoryActivationSpike;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility", remap = false)
abstract class JustDireInventoryBindingMixin {
    @Inject(method = "matchesBinding(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;IZLnet/minecraft/core/component/DataComponentType;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void binding(ItemStack root, String name, int key, boolean mouse, DataComponentType<Integer> mode,
                                CallbackInfoReturnable<Boolean> cir) {
        if (JustDireInventoryActivationSpike.scoped(root)) { cir.setReturnValue(JustDireInventoryActivationSpike.matches(root, name, key, mouse, mode)); }
    }
}
