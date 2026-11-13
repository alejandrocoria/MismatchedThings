package games.alejandrocoria.mismatchedleggings.test.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import games.alejandrocoria.mismatchedleggings.test.DecoyCandidate;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Existing Boots guards understand the original equipment read carried by an Operation.
// An expression-only stack modifier does not preserve that incoming provenance proof.
@Mixin(targets = "com.direwolf20.justdirethings.client.overlays.AbilityCooldownOverlay", priority = 1500, remap = false)
abstract class DecoySpikeHudMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack view(Player player, EquipmentSlot slot, Operation<ItemStack> original) {
        return DecoyCandidate.hud(original.call(player, slot), slot);
    }
}
