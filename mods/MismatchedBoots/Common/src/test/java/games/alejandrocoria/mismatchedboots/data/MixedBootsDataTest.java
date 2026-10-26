package games.alejandrocoria.mismatchedboots.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixedBootsDataTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        bindEquippable(Items.IRON_BOOTS, EquipmentSlot.FEET);
        bindEquippable(Items.GOLDEN_BOOTS, EquipmentSlot.FEET);
        bindEquippable(Items.DIAMOND_BOOTS, EquipmentSlot.FEET);
        bindEquippable(Items.IRON_HELMET, EquipmentSlot.HEAD);
    }

    @Test
    void persistentCodecRoundTripsBothOrderedHalves() {
        MixedBootsData original = data();

        JsonElement encoded = MixedBootsData.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        MixedBootsData decoded = MixedBootsData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(original, decoded);
        assertEquals(Items.IRON_BOOTS, decoded.left().source().getItem());
        assertEquals(Items.GOLDEN_BOOTS, decoded.right().source().getItem());
        assertEquals(Component.literal("Left source"), decoded.left().source().get(DataComponents.CUSTOM_NAME));
        assertEquals(1, decoded.left().source().getCount());
        assertEquals(1, decoded.right().source().getCount());
    }

    @Test
    void networkCodecRoundTripsBothOrderedHalves() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), builtInRegistryAccess());

        try {
            MixedBootsData.STREAM_CODEC.encode(buffer, data());
            MixedBootsData decoded = MixedBootsData.STREAM_CODEC.decode(buffer);

            assertEquals(Items.IRON_BOOTS, decoded.left().source().getItem());
            assertEquals(Items.GOLDEN_BOOTS, decoded.right().source().getItem());
        } finally {
            buffer.release();
        }
    }

    @Test
    void bootHalfDefensivelyCopiesItsStack() {
        ItemStack input = new ItemStack(Items.IRON_BOOTS);
        BootHalf half = new BootHalf(input);

        input.setCount(2);
        ItemStack exposed = half.source();
        exposed.setCount(2);

        assertEquals(1, half.source().getCount());
    }

    @Test
    void rejectsEmptyNonBootAndWrongCountSources() {
        assertThrows(IllegalArgumentException.class, () -> new BootHalf(ItemStack.EMPTY));
        assertThrows(IllegalArgumentException.class, () -> new BootHalf(new ItemStack(Items.IRON_HELMET)));

        ItemStack twoBoots = new ItemStack(Items.IRON_BOOTS, 2);
        assertThrows(IllegalArgumentException.class, () -> new BootHalf(twoBoots));
    }

    @Test
    void rejectsNestedMixedBootData() {
        ItemStack nested = new ItemStack(Items.DIAMOND_BOOTS);
        nested.set(ModContent.createMixedBootsDataType(), data());

        assertThrows(IllegalArgumentException.class, () -> new BootHalf(nested));
    }

    @Test
    void persistentCodecRejectsWrongCount() {
        ItemStack twoBoots = new ItemStack(Items.IRON_BOOTS, 2);
        JsonElement encoded = ItemStack.CODEC.encodeStart(JsonOps.INSTANCE, twoBoots).getOrThrow();

        assertTrue(BootHalf.CODEC.parse(JsonOps.INSTANCE, encoded).isError());
    }

    @Test
    void persistentCodecRejectsMissingRegistryItemsWithoutSubstitution() {
        JsonObject missingBoot = new JsonObject();
        missingBoot.addProperty("id", "missing_mod:lost_boots");
        missingBoot.addProperty("count", 1);

        var result = BootHalf.CODEC.parse(JsonOps.INSTANCE, missingBoot);

        assertTrue(result.isError());
        assertTrue(result.result().isEmpty());
    }

    @Test
    void constructorRejectsNullHalves() {
        assertThrows(NullPointerException.class, () -> new MixedBootsData(null, new BootHalf(new ItemStack(Items.IRON_BOOTS))));
        assertThrows(NullPointerException.class, () -> new MixedBootsData(new BootHalf(new ItemStack(Items.IRON_BOOTS)), null));
    }

    private static MixedBootsData data() {
        ItemStack iron = new ItemStack(Items.IRON_BOOTS);
        iron.set(DataComponents.CUSTOM_NAME, Component.literal("Left source"));
        return new MixedBootsData(new BootHalf(iron), new BootHalf(new ItemStack(Items.GOLDEN_BOOTS)));
    }

    private static void bindEquippable(Item item, EquipmentSlot slot) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                                                                    .set(DataComponents.EQUIPPABLE, Equippable.builder(slot).build())
                                                                    .build());
    }

    private static RegistryAccess builtInRegistryAccess() {
        return new RegistryAccess.ImmutableRegistryAccess(
                BuiltInRegistries.REGISTRY.stream().map(registry -> (Registry<?>) registry).toList());
    }
}
