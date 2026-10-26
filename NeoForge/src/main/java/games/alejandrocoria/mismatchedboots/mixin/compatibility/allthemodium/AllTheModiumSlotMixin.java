package games.alejandrocoria.mismatchedboots.mixin.compatibility.allthemodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import games.alejandrocoria.mismatchedboots.compatibility.allthemodium.AllTheModiumBoots;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "net.allthemods.allthemodium.core.event.ATMEventListener", remap = false)
abstract class AllTheModiumSlotMixin {
    @ModifyReturnValue(method = "getStackInSlot(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("RETURN"), require = 0, expect = 0)
    private static ItemStack mismatchedboots$recognizeFeet(ItemStack original, Player player, EquipmentSlot slot) {
        return AllTheModiumBoots.recognize(original, player, slot);
    }
}
