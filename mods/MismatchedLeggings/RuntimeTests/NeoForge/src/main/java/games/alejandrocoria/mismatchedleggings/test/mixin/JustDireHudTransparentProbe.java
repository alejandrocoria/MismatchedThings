package games.alejandrocoria.mismatchedleggings.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A compatible earlier wrapper must not invalidate either product's local provenance proof. */
@Mixin(targets = "com.direwolf20.justdirethings.client.overlays.AbilityCooldownOverlay", priority = 2000, remap = false)
abstract class JustDireHudTransparentProbe {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack transparent(Player player, EquipmentSlot slot, Operation<ItemStack> original) {
        return original.call(player, slot);
    }
}
