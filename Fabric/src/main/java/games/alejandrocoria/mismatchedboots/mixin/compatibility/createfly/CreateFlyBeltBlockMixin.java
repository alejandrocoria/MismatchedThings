package games.alejandrocoria.mismatchedboots.mixin.compatibility.createfly;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.zurrtum.create.AllItems;
import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "com.zurrtum.create.content.kinetics.belt.BeltBlock", remap = false)
abstract class CreateFlyBeltBlockMixin {
    @ModifyExpressionValue(
            method = "entityInside(Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/entity/Entity;"
                    + "Lnet/minecraft/world/entity/InsideBlockEffectApplier;Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;is(Ljava/lang/Object;)Z"),
            require = 1)
    private boolean mismatchedboots$recognizeCardboardSourceOnBelt(
            boolean ordinaryCardboard, @Local Player player) {
        if (ordinaryCardboard) {
            return true;
        }
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        return MixedBootsSources.anySource(
                boots, source -> source.getItem() == AllItems.CARDBOARD_BOOTS);
    }
}
