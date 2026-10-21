package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.item.equipment.armor.abilities.SentryArmor", remap = false)
interface AetherSentrySpikeMixin {
    @WrapOperation(method = "playerFall", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/core/Holder;)Z"))
    private static boolean recognize(ItemStack stack, Holder<Item> item, Operation<Boolean> original) {
        return original.call(stack, item) || AetherIICompatibilityTest.recognizesItem(stack, item);
    }
}
