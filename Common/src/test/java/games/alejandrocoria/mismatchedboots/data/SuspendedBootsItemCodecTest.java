package games.alejandrocoria.mismatchedboots.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertSame;

class SuspendedBootsItemCodecTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Items.IRON_BOOTS.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build()).build());
    }

    @Test
    void activeJsonBooleanComponentsReachTheNativeDecoderWithoutTranscoding() {
        var source = new ItemStack(Items.IRON_BOOTS);
        source.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build());
        var root = source.copy();
        root.set(ModContent.createMixedBootsDataType(), new MixedBootsData(new BootHalf(source), new BootHalf(source)));

        for (boolean enabled : new boolean[]{false, true}) {
            // A strict foreign boolean codec exposes the wrapper's format conversion independently of loader mixins.
            var nativeDecoder = new MapCodec<ItemStack>() {
                @Override
                public <T> DataResult<ItemStack> decode(DynamicOps<T> ops, MapLike<T> input) {
                    return ops.getMap(input.get("components")).flatMap(patch -> {
                        T flag = patch.get("fixture:enabled");
                        return flag == null ? DataResult.error(() -> "Missing fixture boolean")
                                : Codec.BOOL.parse(ops, flag).flatMap(value -> value == enabled
                                        ? DataResult.success(root) : DataResult.error(() -> "Changed fixture boolean"));
                    });
                }

                @Override
                public <T> RecordBuilder<T> encode(ItemStack input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                    throw new UnsupportedOperationException("Decode-only witness");
                }

                @Override
                public <T> Stream<T> keys(DynamicOps<T> ops) {
                    return Stream.of("id", "count", "components").map(ops::createString);
                }
            };
            var input = JsonParser.parseString("""
                    {
                      "id": "mismatchedboots:mixed_boots",
                      "components": {
                        "fixture:enabled": %s,
                        "mismatchedboots:mixed_boots_data": {
                          "left": {"id": "minecraft:iron_boots"},
                          "right": {"id": "minecraft:iron_boots"}
                        }
                      }
                    }
                    """.formatted(enabled));

            var decoded = new SuspendedBootsItemCodec(nativeDecoder).codec().parse(JsonOps.INSTANCE, input).getOrThrow();
            assertSame(root, decoded);
        }
    }
}
