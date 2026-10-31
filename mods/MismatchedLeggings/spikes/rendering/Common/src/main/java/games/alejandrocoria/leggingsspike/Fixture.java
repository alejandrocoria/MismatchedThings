package games.alejandrocoria.leggingsspike;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.MaterialAssetGroup;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.resources.ResourceKey;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Disposable marker on vanilla stacks: no new product item or persistent schema. */
public final class Fixture {
    public static final String MARKER = "leggings_spike";
    public static List<Sample> gallery = List.of();
    public static final Map<Integer, ItemStack[]> extraSources = new HashMap<>();

    private Fixture() {
    }

    public static ItemStack pair(int scenario) {
        ItemStack stack = source(Items.LEATHER_LEGGINGS, EquipmentAssets.LEATHER);
        CompoundTag tag = new CompoundTag();
        tag.putInt(MARKER, scenario);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    public static int scenario(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? -1 : data.copyTag().getIntOr(MARKER, -1);
    }

    public static ItemStack[] sources(int scenario) {
        ItemStack[] extra = extraSources.get(scenario);
        if (extra != null) {
            return new ItemStack[]{extra[0].copy(), extra[1].copy()};
        }
        ItemStack iron = source(Items.IRON_LEGGINGS, EquipmentAssets.IRON);
        ItemStack gold = source(Items.GOLDEN_LEGGINGS, EquipmentAssets.GOLD);
        if (scenario == 1) {
            return new ItemStack[]{gold, iron};
        }
        if (scenario == 2) {
            ItemStack leather = source(Items.LEATHER_LEGGINGS, EquipmentAssets.LEATHER);
            leather.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3366CC));
            leather.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            leather.set(DataComponents.TRIM, new ArmorTrim(
                    Holder.direct(new TrimMaterial(MaterialAssetGroup.IRON, Component.literal("iron"))),
                    Holder.direct(new TrimPattern(Identifier.withDefaultNamespace("sentry"),
                            Component.literal("sentry"), false))));
            return new ItemStack[]{leather, gold};
        }
        return new ItemStack[]{iron, gold};
    }

    public static ItemStack source(Item item, ResourceKey<EquipmentAsset> asset) {
        return new ItemStack(Holder.direct(item), 1, DataComponentPatch.builder()
                .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS).setAsset(asset).build())
                .build());
    }

    public record Sample(String label, ItemStack stack, float rotation) {
    }
}
