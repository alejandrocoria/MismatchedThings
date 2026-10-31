package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;

public final class CompositionFixture {
    public static final DataComponentType<Long> CHARGE = DataComponentType.<Long>builder()
            .persistent(Codec.LONG.validate(value -> value >= 0 && value <= 1000
                    ? DataResult.success(value) : DataResult.error(() -> "Charge outside capacity")))
            .networkSynchronized(ByteBufCodecs.VAR_LONG).build();

    private CompositionFixture() {
    }
}
