package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.compatibility.aether;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherAlkahestTestControl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Supplier;

@Pseudo
@Mixin(targets = "games.alejandrocoria.mismatchedboots.compatibility.aether.AetherAlkahestProtection", remap = false)
abstract class AetherAlkahestBindingTestMixin {
    @WrapOperation(method = "matches", at = @At(value = "INVOKE", target = "Ljava/util/function/Supplier;get()Ljava/lang/Object;"))
    private static Object unavailable(Supplier<?> receiver,
                                      Operation<Object> operation,
                                      boolean original,
                                      ItemStack stack,
                                      Supplier<?> supplier,
                                      boolean armor) {
        if (AetherAlkahestTestControl.bindingFault(armor)) {
            AetherAlkahestTestControl.bindingFailures++;
            throw new NoClassDefFoundError("Controlled optional Alkahest tier binding failure");
        }
        return operation.call(receiver);
    }
}
