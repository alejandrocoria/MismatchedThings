package games.alejandrocoria.mismatchedboots.gametest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Repairable;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

public final class TestContent implements ModInitializer {
    private static final String NAMESPACE = "mismatchedboots-test";
    private static final Identifier OPAQUE_COMPONENT_ID = id("opaque");
    private static final Identifier ADDED_COMPONENT_ID = id("added");
    private static final Identifier REMOVED_COMPONENT_ID = id("removed");
    private static final Identifier COMPONENT_BOOTS_ID = id("component_boots");
    private static final Identifier UPGRADED_COMPONENT_BOOTS_ID = id("upgraded_component_boots");
    private static final ResourceKey<Item> COMPONENT_BOOTS_KEY =
            ResourceKey.create(Registries.ITEM, COMPONENT_BOOTS_ID);
    private static final ResourceKey<Item> UPGRADED_COMPONENT_BOOTS_KEY =
            ResourceKey.create(Registries.ITEM, UPGRADED_COMPONENT_BOOTS_ID);

    static DataComponentType<String> OPAQUE_COMPONENT;
    static DataComponentType<String> ADDED_COMPONENT;
    static DataComponentType<String> REMOVED_COMPONENT;
    public static final Identifier NUMERIC_COMPONENT_ID = id("numeric");
    public static DataComponentType<Integer> NUMERIC_COMPONENT;
    static Item COMPONENT_BOOTS;
    static Item UPGRADED_COMPONENT_BOOTS;

    @Override
    public void onInitialize() {
        NUMERIC_COMPONENT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, NUMERIC_COMPONENT_ID,
                DataComponentType.<Integer>builder()
                        .persistent(Codec.intRange(0, 10).validate(value -> value % 2 == 0
                                ? DataResult.success(value) : DataResult.error(() -> "Expected even value")))
                        .networkSynchronized(ByteBufCodecs.VAR_INT).build());
        OPAQUE_COMPONENT = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                OPAQUE_COMPONENT_ID,
                DataComponentType.<String>builder()
                        .persistent(Codec.STRING)
                        .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                        .build());
        ADDED_COMPONENT = stringComponent(ADDED_COMPONENT_ID);
        REMOVED_COMPONENT = stringComponent(REMOVED_COMPONENT_ID);
        COMPONENT_BOOTS = Registry.register(
                BuiltInRegistries.ITEM,
                COMPONENT_BOOTS_ID,
                new Item(new Item.Properties()
                        .stacksTo(1)
                        .durability(321)
                        .component(
                                DataComponents.EQUIPPABLE,
                                Equippable.builder(EquipmentSlot.FEET)
                                        .setAsset(EquipmentAssets.IRON)
                                        .build())
                        .setId(COMPONENT_BOOTS_KEY)));
        UPGRADED_COMPONENT_BOOTS = Registry.register(
                BuiltInRegistries.ITEM,
                UPGRADED_COMPONENT_BOOTS_ID,
                new Item(new Item.Properties()
                        .stacksTo(1)
                        .durability(500)
                        .component(DataComponents.REPAIRABLE,
                                new Repairable(HolderSet.direct(BuiltInRegistries.ITEM.wrapAsHolder(Items.DIAMOND))))
                        .component(DataComponents.EQUIPPABLE,
                                Equippable.builder(EquipmentSlot.FEET)
                                        .setAsset(EquipmentAssets.DIAMOND)
                                        .build())
                        .setId(UPGRADED_COMPONENT_BOOTS_KEY)));
    }

    static Item requireComponentBoots() {
        if (COMPONENT_BOOTS == null) {
            throw new IllegalStateException("Test content has not been initialized");
        }
        return COMPONENT_BOOTS;
    }

    static DataComponentType<String> requireOpaqueComponent() {
        if (OPAQUE_COMPONENT == null) {
            throw new IllegalStateException("Test content has not been initialized");
        }
        return OPAQUE_COMPONENT;
    }

    private static DataComponentType<String> stringComponent(Identifier id) {
        return Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                id,
                DataComponentType.<String>builder()
                        .persistent(Codec.STRING)
                        .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                        .build());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(NAMESPACE, path);
    }
}
