package games.alejandrocoria.mismatchedleggings.mixin.compatibility.justdirethings;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedleggings.client.compatibility.justdirethings.JustDireCooldownHud;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.direwolf20.justdirethings.client.overlays.AbilityCooldownOverlay", priority = 1500, remap = false)
abstract class JustDireCooldownHudMixin {
    // Published only after the final wrapper/consumer flow is independently proved for Leggings.
    @Unique
    private static boolean mismatchedleggings$justDireCooldownReady() {
        return false;
    }

    // Keeping the original Operation visible also preserves the current Boots guard's provenance proof.
    @WrapOperation(method = "render", require = 0, expect = 0, allow = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mismatchedleggings$justDireCooldownView(Player player, EquipmentSlot slot, Operation<ItemStack> original) {
        var root = original.call(player, slot);
        return mismatchedleggings$justDireCooldownReady() ? JustDireCooldownHud.view(root, slot) : root;
    }
}
