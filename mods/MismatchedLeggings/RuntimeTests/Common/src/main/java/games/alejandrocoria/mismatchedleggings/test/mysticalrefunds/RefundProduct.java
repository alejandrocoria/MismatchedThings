package games.alejandrocoria.mismatchedleggings.test.mysticalrefunds;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.data.LeggingHalf;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import games.alejandrocoria.mismatchedleggings.test.mysticaleffects.NativeAugments;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Arrays;
import java.util.List;

/** Test-only typed public product access; the same actual-break cases run for either installed product. */
enum RefundProduct {
    BOOTS,
    LEGGINGS;

    boolean installed() {
        return BuiltInRegistries.ITEM.containsKey(Identifier.parse("mismatched" + name().toLowerCase(java.util.Locale.ROOT) + ":mixed_"
                + name().toLowerCase(java.util.Locale.ROOT)));
    }

    EquipmentSlot slot() {
        return this == BOOTS ? EquipmentSlot.FEET : EquipmentSlot.LEGS;
    }

    Item iron() {
        return this == BOOTS ? Items.IRON_BOOTS : Items.IRON_LEGGINGS;
    }

    Item diamond() {
        return this == BOOTS ? Items.DIAMOND_BOOTS : Items.DIAMOND_LEGGINGS;
    }

    ItemStack nativeItem(String path, String... augments) {
        if (this == BOOTS) {
            path = path.replace("_leggings", "_boots");
            augments = Arrays.stream(augments).map(augment -> augment.equals("SPEED_III") ? "JUMP_BOOST_III" : augment).toArray(String[]::new);
        }
        return NativeAugments.item(path, augments);
    }

    List<ItemStack> pair(ItemStack left, ItemStack right) {
        if (this == BOOTS) {
            var result = MixedBootsFactory.createPair(left, right);
            return List.of(result.primary(), result.inverse());
        }
        var result = MixedLeggingsFactory.createPair(left, right);
        return List.of(result.primary(), result.inverse());
    }

    DataComponentType<?> dataType() {
        return this == BOOTS ? ModContent.mixedBootsDataType().orElseThrow() : LeggingsContent.mixedLeggingsDataType();
    }

    boolean selected(ItemStack root) {
        return this == BOOTS
                ? !games.alejandrocoria.mismatchedboots.compatibility.mysticalagriculture.MysticalAugmentsCompatibility.selectedIds(root).isEmpty()
                : !games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture.MysticalAugmentsCompatibility.selectedIds(root).isEmpty();
    }

    Runnable sourceMutation(ItemStack root) {
        if (this == BOOTS) {
            var type = ModContent.mixedBootsDataType().orElseThrow();
            var history = root.get(type);
            return () -> root.set(type, new MixedBootsData(new BootHalf(nativeItem("awakened_supremium_leggings", "LUCK_I")), history.right()));
        }
        var type = LeggingsContent.mixedLeggingsDataType();
        var history = root.get(type);
        return () -> root.set(type, new MixedLeggingsData(new LeggingHalf(nativeItem("awakened_supremium_leggings", "LUCK_I")), history.right()));
    }
}
