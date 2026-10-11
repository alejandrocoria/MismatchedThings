package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "com.direwolf20.justdirethings.client.screens.ToolSettingScreen", priority = 1500, remap = false)
abstract class JustDireSettingsClientHighMixin {
    @WrapOperation(method = "refreshButtons", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getItem()Lnet/minecraft/world/item/Item;"))
    private Item fixtureHigh(ItemStack stack, Operation<Item> original) { return original.call(stack); }
}
