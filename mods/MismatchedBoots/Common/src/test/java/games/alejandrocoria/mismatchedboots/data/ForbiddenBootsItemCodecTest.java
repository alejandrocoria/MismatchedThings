package games.alejandrocoria.mismatchedboots.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertSame;

class ForbiddenBootsItemCodecTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void unsuccessfulAndPartialDecodesKeepTheirOriginalResultAndMetadata() {
        for (var original : List.of(DataResult.<ItemStack>error(() -> "unreadable native item"),
                DataResult.error(() -> "partial native item", ItemStack.EMPTY))) {
            var delegate = new MapCodec<ItemStack>() {
                @Override
                public <T> Stream<T> keys(DynamicOps<T> ops) {
                    return Stream.empty();
                }

                @Override
                public <T> RecordBuilder<T> encode(ItemStack value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                    return prefix;
                }

                @Override
                public <T> DataResult<ItemStack> decode(DynamicOps<T> ops, MapLike<T> input) {
                    return original;
                }
            };
            var wrapper = new ForbiddenBootsItemCodec(delegate);
            var input = JsonOps.INSTANCE.getMap(JsonParser.parseString("{}")).getOrThrow();
            assertSame(original, wrapper.decode(JsonOps.INSTANCE, input));
        }
    }
}
