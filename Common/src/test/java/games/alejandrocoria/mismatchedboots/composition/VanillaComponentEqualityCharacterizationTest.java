package games.alejandrocoria.mismatchedboots.composition;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class VanillaComponentEqualityCharacterizationTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void representativeVanillaValuesSurviveCopyAndPersistentRoundTrip() {
        CompoundTag data = new CompoundTag();
        data.putString("marker", "same-value");

        assertStableEquality(DataComponents.LORE, new ItemLore(List.of(Component.literal("source lore"))));
        assertStableEquality(DataComponents.RARITY, Rarity.RARE);
        assertStableEquality(DataComponents.TOOLTIP_DISPLAY,
                             net.minecraft.world.item.component.TooltipDisplay.DEFAULT
                                     .withHidden(DataComponents.ATTRIBUTE_MODIFIERS, true));
        assertStableEquality(DataComponents.DYED_COLOR, new DyedItemColor(0x336699));
        assertStableEquality(DataComponents.CUSTOM_MODEL_DATA,
                             new CustomModelData(List.of(0.25F), List.of(true), List.of("variant"), List.of(0x334455)));
        assertStableEquality(DataComponents.ENCHANTABLE, new Enchantable(13));
        assertStableEquality(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        assertStableEquality(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }

    @Test
    @SuppressWarnings("deprecation")
    void separatelyObtainedNamedDamageSetsDoNotHaveValueEquality() {
        TagKey<DamageType> tag = TagKey.create(Registries.DAMAGE_TYPE,
                                               Identifier.fromNamespaceAndPath("mismatchedboots-test", "resistant"));
        HolderOwner<DamageType> owner = new HolderOwner<>() {
        };
        DamageResistant first = new DamageResistant(HolderSet.emptyNamed(owner, tag));
        DamageResistant second = new DamageResistant(HolderSet.emptyNamed(owner, tag));

        assertNotSame(first.types(), second.types());
        assertEquals(first.types().unwrapKey(), second.types().unwrapKey());
        assertNotEquals(first, second);
    }

    private static <T> void assertStableEquality(DataComponentType<T> type, T value) {
        ItemStack source = new ItemStack(Holder.direct(Items.IRON_BOOTS,
                                                       DataComponentMap.builder().set(type, value).build()), 1);
        ItemStack copy = source.copy();
        JsonElement encoded = type.codecOrThrow().encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        T decoded = type.codecOrThrow().parse(JsonOps.INSTANCE, encoded).getOrThrow();

        assertEquals(value, copy.get(type), "copy: " + type);
        assertEquals(value, decoded, "persistent round-trip: " + type);
        assertEquals(source.get(type), decoded, "source versus decoded: " + type);
    }
}
