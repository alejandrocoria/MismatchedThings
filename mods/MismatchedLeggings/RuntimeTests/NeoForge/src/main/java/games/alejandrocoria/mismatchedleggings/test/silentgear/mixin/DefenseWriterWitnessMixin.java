package games.alejandrocoria.mismatchedleggings.test.silentgear.mixin;

import games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseProbe;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.silentchaos512.gear.util.GearData", remap = false)
abstract class DefenseWriterWitnessMixin {
    @Inject(method = "recalculateGearData(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;)V", at = @At("HEAD"))
    private static void before(ItemStack stack, Player actor, CallbackInfo callback) {
        DefenseProbe.recalculation(stack);
    }
}
