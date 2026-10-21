package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

@Pseudo
@Mixin(targets = "com.aetherteam.aetherii.item.equipment.EquipmentUtil", remap = false)
abstract class AetherSetSpikeMixin {
    @WrapOperation(method = "getArmorCount", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;get(Ljava/util/function/Supplier;)Ljava/lang/Object;"))
    private static Object recognize(ItemStack stack, Supplier<?> type, Operation<Object> original,
                                    @Local(argsOnly = true) TagKey<Item> requested) {
        Object nativeSet = original.call(stack, type);
        return AetherIICompatibilityTest.recognizesSet(stack, requested) ? requested : nativeSet;
    }
}
