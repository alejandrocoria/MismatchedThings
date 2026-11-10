package games.alejandrocoria.mismatchedleggings.test.silentgear;

import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearRecognition;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import net.silentchaos512.gear.api.item.GearItem;
import net.silentchaos512.gear.api.property.TraitListPropertyValue;
import net.silentchaos512.gear.api.traits.TraitInstance;
import net.silentchaos512.gear.api.util.DataResource;
import net.silentchaos512.gear.core.component.GearPropertiesData;
import net.silentchaos512.gear.gear.part.PartInstance;
import net.silentchaos512.gear.gear.trait.condition.GearTypeTraitCondition;
import net.silentchaos512.gear.setup.SgDataComponents;
import net.silentchaos512.gear.setup.gear.GearProperties;
import net.silentchaos512.gear.setup.gear.GearTypes;
import net.silentchaos512.gear.util.GearData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real production item and ordinary piglin armor consumer; no research recognition hooks. */
public final class BrilliantChecks {
    private static boolean observing;

    private BrilliantChecks() {}

    public static void recalculation() {
        if (observing) { throw new AssertionError("Brilliant entered native gear recalculation"); }
    }

    static void ordinary(ServerPlayer player, StringBuilder report) {
        clear(player);
        require(!PiglinAi.isWearingSafeArmor(player), "Naked piglin control");
        check(player, new ItemStack(Items.GOLDEN_LEGGINGS), true);
        for (var root : FoundationChecks.paid(player, new ItemStack(Items.GOLDEN_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS))) {
            check(player, root, true);
        }
        require(SilentGearRecognition.brilliant(ItemStack.EMPTY, player, true), "Already-true callback lost");
        var atm = BuiltInRegistries.ITEM.getValue(Identifier.parse("allthemodium:allthemodium_leggings"));
        if (atm != null && atm != Items.AIR) {
            for (var root : FoundationChecks.paid(player, new ItemStack(atm), new ItemStack(Items.DIAMOND_LEGGINGS))) {
                check(player, root, true);
            }
            report.append("Brilliant: actual AllTheModium positives preserved in both paid orientations\n");
        }
        clear(player);
        report.append("Brilliant: vanilla gold/ordinary true preserved\n");
    }

    static void run(ServerPlayer player, StringBuilder report) throws Exception {
        String variant = System.getProperty("leggings.test.sgBrilliantVariant", "");
        boolean ready = !List.of("callback", "proof").contains(variant);
        var gold = FoundationChecks.armor("gold");
        var iron = FoundationChecks.armor("iron");
        boolean witnessed = false;
        observing = true;
        try { GearData.recalculateGearData(iron.copy(), player); }
        catch (AssertionError expected) { witnessed = true; }
        finally { observing = false; }
        require(witnessed, "Brilliant recalculation witness lacks positive control");
        if (List.of("compatible", "callback", "proof").contains(variant)) {
            require(Boolean.TRUE.equals(gold.getItem().getClass().getMethod("sgtest$brilliantModified").invoke(null)),
                    "Final Brilliant transformation witness missing");
        }
        check(player, gold, true);
        check(player, iron, false);
        for (var other : List.of(new ItemStack(Items.DIAMOND_LEGGINGS), iron, gold)) {
            for (var root : FoundationChecks.paid(player, gold, other)) {
                check(player, root, ready);
                root.remove(SilentGearCompatibility.state());
                check(player, root, ready);
                invalid(player, root);
                for (var slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
                    clear(player);
                    player.setItemSlot(slot, root);
                    require(!PiglinAi.isWearingSafeArmor(player), "Held Brilliant manufactured worn armor");
                }
                clear(player);
                player.getInventory().setItem(9, root);
                require(!PiglinAi.isWearingSafeArmor(player), "Idle Brilliant manufactured worn armor");
            }
        }
        for (var root : FoundationChecks.paid(player, iron, new ItemStack(Items.DIAMOND_LEGGINGS))) {
            check(player, root, false);
        }
        for (boolean matching : List.of(true, false)) {
            var source = iron.copy();
            var properties = new HashMap<>(source.get(SgDataComponents.GEAR_PROPERTIES.get()).properties());
            properties.put(GearProperties.TRAITS.get(), new TraitListPropertyValue(List.of(TraitInstance.of(
                    DataResource.trait("silentgear:brilliant"), 1,
                    new GearTypeTraitCondition(matching ? GearTypes.LEGGINGS : GearTypes.BOOTS)))));
            source.set(SgDataComponents.GEAR_PROPERTIES.get(), new GearPropertiesData(properties));
            for (var root : FoundationChecks.paid(player, source, new ItemStack(Items.DIAMOND_LEGGINGS))) {
                check(player, root, ready && matching);
            }
        }
        if (Boolean.getBoolean("leggings.test.boots")) {
            var source = ((GearItem) BuiltInRegistries.ITEM.getValue(Identifier.parse("silentgear:boots")))
                    .construct(List.of(PartInstance.from(FoundationChecks.part("boot_plates", "gold"))));
            var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
            var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class)
                    .invoke(null, source, new ItemStack(Items.DIAMOND_BOOTS));
            var root = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
            clear(player);
            player.setItemSlot(EquipmentSlot.FEET, root);
            require(PiglinAi.isWearingSafeArmor(player), "Leggings refusal changed current Boots Brilliant");
            report.append("Brilliant: current packaged Boots remains independently usable\n");
        }
        ordinary(player, report); // Repeat after refusal and repeated negative calls.
        report.append("Brilliant production/native consumer, both paid orientations, duplicate/plain/conditional sources,")
                .append(" missing state, held/idle and invalid roots; zero recalculations or gear/actor writes; ready=")
                .append(ready).append(" variant=").append(variant).append('\n');
    }

    private static void invalid(ServerPlayer player, ItemStack root) {
        var malformed = root.copy();
        malformed.remove(DataComponents.EQUIPPABLE);
        var wrongSlot = root.copy();
        wrongSlot.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build());
        var foreign = new ItemStack(Items.IRON_LEGGINGS);
        foreign.set(LeggingsContent.mixedLeggingsDataType(), root.get(LeggingsContent.mixedLeggingsDataType()));
        var rejected = new ArrayList<>(List.of(new ItemStack(root.getItem()), root.copyWithCount(2), malformed, wrongSlot, foreign));
        for (boolean quarantine : List.of(false, true)) {
            var suspended = root.copy();
            suspended.set(LeggingsContent.createSuspendedDataType(), quarantine
                    ? SuspendedLeggingsData.quarantined(new CompoundTag(), new CompoundTag())
                    : SuspendedLeggingsData.of(new CompoundTag(), new CompoundTag(), new CompoundTag()));
            rejected.add(suspended);
        }
        for (var stack : rejected) { check(player, stack, false); }
    }

    private static void check(ServerPlayer player, ItemStack stack, boolean expected) {
        clear(player);
        player.setItemSlot(EquipmentSlot.LEGS, stack);
        var before = stack.copy();
        var equipment = java.util.Arrays.stream(EquipmentSlot.values()).map(s -> player.getItemBySlot(s).copy()).toList();
        var effects = player.getActiveEffects().stream().map(net.minecraft.world.effect.MobEffectInstance::new).toList();
        observing = true;
        try {
            require(stack.getItem().makesPiglinsNeutral(stack, player) == expected, "Production Brilliant item callback differs");
            require(PiglinAi.isWearingSafeArmor(player) == expected, "Ordinary piglin armor consumer differs");
            require(ItemStack.matches(before, stack), "Brilliant changed root/source/history");
            int i = 0;
            for (var slot : EquipmentSlot.values()) {
                require(ItemStack.matches(equipment.get(i++), player.getItemBySlot(slot)), "Brilliant changed equipment");
            }
            require(effects.equals(List.copyOf(player.getActiveEffects())), "Brilliant changed actor effects");
        } finally { observing = false; }
    }

    private static void clear(ServerPlayer player) {
        player.getInventory().clearContent();
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
    }
}
