package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedleggings.component.ComponentRuleSet;
import games.alejandrocoria.mismatchedleggings.component.ComponentRules;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.mixin.ArmorWearInvoker;
import games.alejandrocoria.mismatchedthings.api.composition.AttributeMerger;
import games.alejandrocoria.mismatchedthings.api.composition.AttributePolicy;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentNumericKind;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentPolicy;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentRule;
import games.alejandrocoria.mismatchedthings.api.composition.DurabilityPolicy;
import games.alejandrocoria.mismatchedthings.api.composition.RepairCostPolicy;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

public final class CompositionChecks {
    public static boolean effectiveAttributeExpected;
    public static Consumer<ItemStack> nativeQueryCheck = ignored -> {};

    private CompositionChecks() {
    }

    static void verify(MinecraftServer server) throws Exception {
        var a = new ItemStack(Items.IRON_LEGGINGS);
        var b = new ItemStack(Items.GOLDEN_LEGGINGS);
        a.setDamageValue(7);
        b.setDamageValue(12);
        a.set(DataComponents.REPAIR_COST, 3);
        b.set(DataComponents.REPAIR_COST, 8);
        a.set(CompositionFixture.CHARGE, 7L);
        b.set(CompositionFixture.CHARGE, 4L);
        a.set(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        a.set(DataComponents.LORE, new ItemLore(List.of(net.minecraft.network.chat.Component.literal("Source lore"))));
        b.remove(DataComponents.CUSTOM_DATA);
        var enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        a.enchant(enchantments.getOrThrow(Enchantments.PROTECTION), 2);
        b.enchant(enchantments.getOrThrow(Enchantments.PROTECTION), 2);
        b.enchant(enchantments.getOrThrow(Enchantments.SWIFT_SNEAK), 1);
        var beforeA = a.copy();
        var beforeB = b.copy();
        nativeQueryCheck.accept(a);
        var pair = MixedLeggingsFactory.createPair(a, b);
        var primaryData = MixedLeggingsStructure.validData(pair.primary()).orElseThrow();
        var inverseData = MixedLeggingsStructure.validData(pair.inverse()).orElseThrow();
        require(primaryData.left().source().is(Items.IRON_LEGGINGS) && inverseData.left().source().is(Items.GOLDEN_LEGGINGS),
                "Factory lost ordered provenance");
        require(pair.primary().get(CompositionFixture.CHARGE) == 7L && pair.inverse().get(CompositionFixture.CHARGE) == 4L
                        && pair.primary().has(DataComponents.CUSTOM_DATA) && !pair.inverse().has(DataComponents.CUSTOM_DATA),
                "Unknown components/removals did not retain their orientation owner");
        for (var root : List.of(pair.primary(), pair.inverse())) {
            require(root.getMaxDamage() == (a.getMaxDamage() + b.getMaxDamage()) / 2
                            && root.getMaxDamage() - root.getDamageValue() == (a.getMaxDamage() - 7 + b.getMaxDamage() - 12) / 2,
                    "Durability did not conserve remaining resource with floor rounding");
            require(root.get(DataComponents.REPAIR_COST) == 8 && !root.has(DataComponents.LORE), "Prior work/prohibited component projection changed");
            var repairable = root.get(DataComponents.REPAIRABLE);
            require(repairable.items().contains(Items.IRON_INGOT.builtInRegistryHolder())
                    && repairable.items().contains(Items.GOLD_INGOT.builtInRegistryHolder()), "Repair material union is incomplete");
            require(root.getEnchantments().getLevel(enchantments.getOrThrow(Enchantments.PROTECTION)) == 3
                    && root.getEnchantments().getLevel(enchantments.getOrThrow(Enchantments.SWIFT_SNEAK)) == 1, "Enchantment union/upgrade failed");
            require(amount(root, "minecraft:armor.leggings") == 4, "Native armor modifiers were not averaged under their original ID");
            require(amount(root, "leggings_skeleton_test:effective") == (effectiveAttributeExpected ? 3 : 0),
                    "Native effective-attribute query was skipped or applied twice");
            var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
            require(ItemStack.matches(root, ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, root).getOrThrow()).getOrThrow()),
                    "Composed root does not persist with canonical attribute holders");
        }
        require(ItemStack.matches(a, beforeA) && ItemStack.matches(b, beforeB), "Factory or native attribute hook mutated an input");
        pair.primary().setDamageValue(1);
        require(pair.inverse().getDamageValue() != 1 && primaryData.left().source().getDamageValue() == 7, "Outputs or provenance alias live root state");
        require(MixedLeggingsFactory.createPair(a, a).primary().getEnchantments()
                .getLevel(enchantments.getOrThrow(Enchantments.PROTECTION)) == 3, "Same-item sources were rejected or collapsed");

        policies(a, b);
        attributes();
        config();
        wear(server);
    }

    private static void policies(ItemStack a, ItemStack b) {
        var components = new ComponentRules(DurabilityPolicy.SEPARATE, RepairCostPolicy.OWNER,
                new ComponentRuleSet(Map.of(Identifier.parse("leggings_skeleton_test:charge"),
                        ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.LONG))));
        var rules = new CompositionRules(CompositionRules.defaults().attributes(), components);
        var pair = MixedLeggingsFactory.createPair(a, b, rules);
        require(pair.primary().getMaxDamage() == a.getMaxDamage() && pair.primary().getDamageValue() == a.getDamageValue()
                && pair.inverse().getMaxDamage() == b.getMaxDamage() && pair.inverse().get(DataComponents.REPAIR_COST) == 8
                && pair.primary().get(DataComponents.REPAIR_COST) == 3 && pair.primary().get(CompositionFixture.CHARGE) == 5L
                && pair.inverse().get(CompositionFixture.CHARGE) == 5L, "Explicit owner/resource policies were ignored");
        CompositionRuleState.installPreview(rules);
        require(MixedLeggingsFactory.createPair(a, b).primary().getMaxDamage() != a.getMaxDamage(), "Preview state changed server composition");
        CompositionRuleState.resetPreview();
        var rejected = new CompositionRules(new AttributeRuleSet(AttributePolicy.REJECT, Map.of()), components);
        fails(() -> MixedLeggingsFactory.createPair(a, b, rejected), "Rejected attributes were admitted");
    }

    private static void attributes() {
        var id = Identifier.parse("fixture:identity");
        var first = ItemAttributeModifiers.builder().add(Attributes.ARMOR,
                new AttributeModifier(id, 8, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.LEGS).build();
        var second = ItemAttributeModifiers.builder().add(Attributes.ARMOR,
                new AttributeModifier(id, 4, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.LEGS).build();
        var result = AttributeMerger.merge(first, second, ignored -> AttributePolicy.AVERAGE);
        require(result.primary().equals(first) && result.inverse().equals(second), "Divergent operations were normalized incorrectly");
        second = ItemAttributeModifiers.builder().add(Attributes.ARMOR,
                new AttributeModifier(id, 4, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.FEET).build();
        result = AttributeMerger.merge(first, second, ignored -> AttributePolicy.COMBINE);
        require(result.primary().equals(first) && result.inverse().equals(second), "Divergent slots were normalized incorrectly");
        var total = ItemAttributeModifiers.builder().add(Attributes.LUCK,
                new AttributeModifier(id, .5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.LEGS).build();
        require(AttributeMerger.merge(total, ItemAttributeModifiers.EMPTY, ignored -> AttributePolicy.AVERAGE)
                .primary().modifiers().getFirst().modifier().amount() == .25, "Absent total multiplier did not contribute neutral factor one");
        var bad = ItemAttributeModifiers.builder().add(Attributes.ARMOR,
                new AttributeModifier(id, Double.NaN, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.LEGS).build();
        fails(() -> AttributeMerger.merge(bad, first, ignored -> AttributePolicy.SEPARATE), "Nonfinite modifier admitted");
        var direct = ItemAttributeModifiers.builder().add(Holder.direct(Attributes.ARMOR.value()),
                new AttributeModifier(id, 1, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.LEGS).build();
        fails(() -> AttributeMerger.merge(direct, first, ignored -> AttributePolicy.AVERAGE), "Unregistered holder admitted");
    }

    private static void config() throws Exception {
        var directory = Path.of(System.getProperty("leggings.test.report"), "composition-config");
        Files.createDirectories(directory);
        Files.deleteIfExists(directory.resolve("mismatchedleggings-attributes.toml"));
        Files.deleteIfExists(directory.resolve("mismatchedleggings-components.toml"));
        var defaults = CompositionRules.load(directory);
        require(defaults.equals(CompositionRules.defaults()), "Config defaults differ from factory defaults");
        Files.writeString(directory.resolve("mismatchedleggings-attributes.toml"), "[attributes]\nfallback='separate'\n[attributes.'minecraft:armor']\npolicy='maximum'\n");
        var loaded = CompositionRules.load(directory);
        require(loaded.attributes().policyFor(Identifier.parse("minecraft:armor")) == AttributePolicy.MAXIMUM,
                "Attribute schema ignored explicit policy");
        Files.writeString(directory.resolve("mismatchedleggings-components.toml"), "[durability]\npolicy='separate'\n[repair_cost]\npolicy='owner'\n[components.'leggings_skeleton_test:charge']\npolicy='average'\nnumber='long'\n");
        loaded = CompositionRules.load(directory);
        require(loaded.components().durability() == DurabilityPolicy.SEPARATE
                && loaded.components().components().ruleFor(Identifier.parse("leggings_skeleton_test:charge")).number() == ComponentNumericKind.LONG,
                "Component schema ignored numeric/dedicated policies");
        // Reset generated fixture input so repeated suite runs test fresh defaults, not the previous edit.
        Files.delete(directory.resolve("mismatchedleggings-attributes.toml"));
        Files.delete(directory.resolve("mismatchedleggings-components.toml"));
    }

    private static void wear(MinecraftServer server) {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "leggings-wear"), ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        player.setGameMode(GameType.SURVIVAL);
        var enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var pair = MixedLeggingsFactory.createPair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.GOLDEN_LEGGINGS));
        for (var root : List.of(pair.primary(), pair.inverse())) {
            var provenance = MixedLeggingsStructure.validData(root).orElseThrow();
            player.setItemSlot(EquipmentSlot.LEGS, root);
            ((ArmorWearInvoker) player).leggings$hurtArmor(player.damageSources().generic(), 8);
            require(root.getDamageValue() == 2 && root.getItem().isBarVisible(root), "Native combat wear/bar did not use the live root");
            root.enchant(enchantments.getOrThrow(Enchantments.UNBREAKING), 3);
            root.setDamageValue(0);
            server.overworld().getRandom().setSeed(123456);
            root.hurtAndBreak(25, player, EquipmentSlot.LEGS);
            require(root.is(ItemTags.ARMOR_ENCHANTABLE) && root.getDamageValue() == 17, "Root uses non-armor Unbreaking");
            root.enchant(enchantments.getOrThrow(Enchantments.MENDING), 1);
            root.setDamageValue(20);
            player.takeXpDelay = 0;
            new ExperienceOrb(server.overworld(), 0, 65, 0, 5).playerTouch(player);
            require(root.getDamageValue() == 10 && MixedLeggingsStructure.validData(root).orElseThrow().equals(provenance),
                    "Native Mending failed or live durability leaked into provenance");
        }
    }

    private static double amount(ItemStack root, String id) {
        return root.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                .filter(entry -> entry.modifier().id().equals(Identifier.parse(id))).mapToDouble(entry -> entry.modifier().amount()).sum();
    }

    private static void fails(Runnable action, String message) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }
}
