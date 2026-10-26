package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.allthemodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AllTheModiumTest;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.allthemods.allthemodium.core.event.ATMEventListener", remap = false, priority = 1500)
abstract class AllTheModiumTestMixin {
    @ModifyReturnValue(method = "getStackInSlot(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"))
    private static ItemStack mismatchedboots$observeReturn(ItemStack original) {
        return AllTheModiumTest.observe(original);
    }
}
