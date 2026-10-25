package games.alejandrocoria.mismatchedboots.runtimetest.advancednetherite;

import com.autovw.advancednetherite.api.impl.IAdvancedHooks;
import com.autovw.advancednetherite.common.AdvancedUtil;
import com.autovw.advancednetherite.core.ModItems;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.List;

public final class AdvancedNetheriteCompatibilityAssertions {
    public static final String TEST_NAMESPACE = "mismatchedboots_advanced_netherite_test";
    public static final Identifier HOOK_BOOT_ID = id("hook_only_boots");
    public static final ResourceKey<Item> HOOK_BOOT_KEY = ResourceKey.create(Registries.ITEM, HOOK_BOOT_ID);

    private static final TagKey<Item> PACIFY_PIGLINS = advancedNetheriteTag("pacify_piglins_armor");
    private static final TagKey<Item> PACIFY_ENDERMEN = advancedNetheriteTag("pacify_endermen_armor");
    private static final TagKey<Item> PACIFY_PHANTOMS = advancedNetheriteTag("pacify_phantoms_armor");

    private AdvancedNetheriteCompatibilityAssertions() {
    }

    public static Item.Properties hookBootProperties() {
        return new Item.Properties()
                .stacksTo(1)
                .durability(16)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build())
                .setId(HOOK_BOOT_KEY);
    }

    public static Item registerHookBoot() {
        return Registry.register(
                BuiltInRegistries.ITEM,
                HOOK_BOOT_ID,
                new HookOnlyBootItem(hookBootProperties()));
    }

    public static void assertCompleteMatrix(Player player) {
        Item registeredHookBoot = BuiltInRegistries.ITEM.getValue(HOOK_BOOT_ID);
        require(registeredHookBoot instanceof HookOnlyBootItem,
                "The hook-only test boot is not registered");

        List<ArmorCase> cases = List.of(
                new ArmorCase("netherite-iron", ModItems.NETHERITE_IRON_BOOTS, false, false, true),
                new ArmorCase("netherite-gold", ModItems.NETHERITE_GOLD_BOOTS, true, false, false),
                new ArmorCase("netherite-emerald", ModItems.NETHERITE_EMERALD_BOOTS, false, true, false),
                new ArmorCase("netherite-diamond", ModItems.NETHERITE_DIAMOND_BOOTS, true, true, true));

        for (ArmorCase armorCase : cases) {
            ItemStack source = new ItemStack(armorCase.boots());
            require(source.is(PACIFY_PIGLINS) == armorCase.piglins(),
                    armorCase.name() + " has an unexpected upstream piglin tag answer");
            require(source.is(PACIFY_ENDERMEN) == armorCase.endermen(),
                    armorCase.name() + " has an unexpected upstream enderman tag answer");
            require(source.is(PACIFY_PHANTOMS) == armorCase.phantoms(),
                    armorCase.name() + " has an unexpected upstream phantom tag answer");

            MixedBootsPair pair = MixedBootsFactory.createPair(source, new ItemStack(Items.IRON_BOOTS));
            assertOutput(player, pair.primary(), armorCase, "primary");
            assertOutput(player, pair.inverse(), armorCase, "inverse");
        }

        ArmorCase vanillaControl = new ArmorCase("iron-diamond control", Items.IRON_BOOTS,
                                                 false, false, false);
        MixedBootsPair control = MixedBootsFactory.createPair(
                new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
        assertOutput(player, control.primary(), vanillaControl, "primary");
        assertOutput(player, control.inverse(), vanillaControl, "inverse");

        assertHookOnlyBranch(player, registeredHookBoot, 1, true, false);
        assertHookOnlyBranch(player, registeredHookBoot, 2, false, true);
        assertHookOnlyBranch(player, registeredHookBoot, 3, true, true);
        assertHookOnlyBranch(player, registeredHookBoot, 0, false, false);
    }

    private static void assertHookOnlyBranch(
            Player player, Item hookBoot, int damage, boolean endermen, boolean phantoms) {
        ItemStack source = new ItemStack(hookBoot);
        source.setDamageValue(damage);
        MixedBootsPair pair = MixedBootsFactory.createPair(source, new ItemStack(Items.IRON_BOOTS));
        ArmorCase expected = new ArmorCase("hook-only damage " + damage, hookBoot,
                                           false, endermen, phantoms);
        assertOutput(player, pair.primary(), expected, "primary");
        assertOutput(player, pair.inverse(), expected, "inverse");
    }

    private static void assertOutput(Player player, ItemStack output, ArmorCase expected, String orientation) {
        String description = expected.name() + " " + orientation;
        require(output.is(ItemTags.PIGLIN_SAFE_ARMOR) == expected.piglins(),
                description + " has the wrong vanilla piglin-safe answer");
        require(!output.is(PACIFY_PIGLINS),
                description + " globally projected Advanced Netherite's piglin tag");
        require(!output.is(PACIFY_ENDERMEN),
                description + " globally projected Advanced Netherite's enderman tag");
        require(!output.is(PACIFY_PHANTOMS),
                description + " globally projected Advanced Netherite's phantom tag");

        require(output.getItem() instanceof IAdvancedHooks,
                description + " did not receive the optional hook interface");
        IAdvancedHooks hooks = (IAdvancedHooks) output.getItem();
        require(hooks.pacifyEndermen(output) == expected.endermen(),
                description + " has the wrong direct enderman hook answer");
        require(hooks.pacifyPhantoms(output) == expected.phantoms(),
                description + " has the wrong direct phantom hook answer");

        List<ItemStack> previousArmor = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlotGroup.ARMOR) {
            previousArmor.add(player.getItemBySlot(slot).copy());
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        try {
            player.setItemSlot(EquipmentSlot.FEET, output.copy());
            require(AdvancedUtil.isWearingEndermanPassiveArmor(player) == expected.endermen(),
                    description + " has the wrong equipped-player enderman answer");
            require(AdvancedUtil.isWearingPhantomPassiveArmor(player) == expected.phantoms(),
                    description + " has the wrong equipped-player phantom answer");
        } finally {
            int index = 0;
            for (EquipmentSlot slot : EquipmentSlotGroup.ARMOR) {
                player.setItemSlot(slot, previousArmor.get(index++));
            }
        }
    }

    private static TagKey<Item> advancedNetheriteTag(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("advancednetherite", path));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(TEST_NAMESPACE, path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record ArmorCase(
            String name, Item boots, boolean piglins, boolean endermen, boolean phantoms) {
    }
}
