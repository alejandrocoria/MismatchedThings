package games.alejandrocoria.mismatchedboots.runtimetest.missingmods.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;

/** Actually hides the native component entry point, rather than toggling the guard's result. */
@Mixin(value = ItemStack.class, priority = 1100)
abstract class CaptureIncompatibleMixin {
    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE", target =
            "Lcom/mojang/serialization/MapCodec;recursive(Ljava/lang/String;Ljava/util/function/Function;)Lcom/mojang/serialization/MapCodec;"), index = 1)
    private static Function<Codec<ItemStack>, MapCodec<ItemStack>> alter(Function<Codec<ItemStack>, MapCodec<ItemStack>> factory) {
        return Boolean.getBoolean("mismatchedboots.test.missingModsRefuse")
                ? self -> factory.apply(self).codec().fieldOf("hidden_native_stack") : factory;
    }
}
