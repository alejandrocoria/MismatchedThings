package games.alejandrocoria.mismatchedboots.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings.JustDireCooldownHud;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.direwolf20.justdirethings.client.overlays.AbilityCooldownOverlay", remap = false)
abstract class JustDireCooldownHudMixin {
    // Published only after the final overlay proves that our view reaches its clock/parameter reads.
    @Unique private static boolean mismatchedboots$justDireCooldownReady() { return false; }

    @ModifyExpressionValue(method = "render", require = 0, expect = 0, allow = 1,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getItemBySlot(Lnet/minecraft/world/entity/EquipmentSlot;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack mismatchedboots$justDireCooldownView(ItemStack root, @Local EquipmentSlot slot) {
        return mismatchedboots$justDireCooldownReady() ? JustDireCooldownHud.view(root, slot) : root;
    }
}
