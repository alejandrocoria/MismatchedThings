package games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.missingmods.ArchiveItemCodec;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Function;

/** Test-only codec entry point; no production codec, record or component is changed. */
@Mixin(ItemStack.class)
abstract class MissingModsItemCodecMixin {
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target =
            "Lcom/mojang/serialization/MapCodec;recursive(Ljava/lang/String;Ljava/util/function/Function;)Lcom/mojang/serialization/MapCodec;"))
    private static MapCodec<ItemStack> mismatchedboots$researchArchive(String name,
            Function<Codec<ItemStack>, MapCodec<ItemStack>> factory) {
        return MapCodec.recursive(name, self -> {
            var nativeCodec = factory.apply(self);
            return System.getProperty("mismatchedboots.test.missingModsCase", "").equals("candidate")
                    ? new ArchiveItemCodec(nativeCodec) : nativeCodec;
        });
    }
}
