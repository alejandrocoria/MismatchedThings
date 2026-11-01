package games.alejandrocoria.mismatchedleggings.test.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Function;

/** Actually hides the native fields, instead of overriding production's installation witness. */
@Mixin(value = ItemStack.class, priority = 1100)
abstract class RecoveryFactoryProbe {
    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE", target =
            "Lcom/mojang/serialization/MapCodec;recursive(Ljava/lang/String;Ljava/util/function/Function;)Lcom/mojang/serialization/MapCodec;"), index = 1)
    private static Function<Codec<ItemStack>, MapCodec<ItemStack>> leggings$alterFactory(Function<Codec<ItemStack>, MapCodec<ItemStack>> factory) {
        return System.getProperty("leggings.test.recoveryStage", "").equals("refused")
                ? self -> factory.apply(self).codec().fieldOf("hidden_native_stack") : factory;
    }
}
