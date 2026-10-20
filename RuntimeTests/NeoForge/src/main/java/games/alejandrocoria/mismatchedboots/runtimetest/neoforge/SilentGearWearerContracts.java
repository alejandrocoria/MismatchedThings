package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearWearerReadiness;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Maintained native fixture extension: no duplicated potion dispatcher or replacement set counter. */
final class SilentGearWearerContracts {
    private static final List<EquipmentSlot> SLOTS = List.of(EquipmentSlot.FEET, EquipmentSlot.CHEST, EquipmentSlot.HEAD, EquipmentSlot.LEGS);
    private SilentGearWearerContracts() {}

    static void run(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report, String variant) throws Exception {
        try { contracts(player, api, report, variant); }
        catch (Exception | AssertionError failure) { throw failure; }
        catch (Throwable failure) { throw new AssertionError("Native wearer contract failed", failure); }
        finally { SilentGearWearerFixture.active = false; player.removeAllEffects(); for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); } }
    }

    private static void contracts(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report, String variant) throws Throwable {
        SilentGearTraitSpike.initialize(api);
        SilentGearWearerFixture.bind();
        boolean refused = variant.equals("wearer-type") || variant.equals("wearer-trait");
        if (variant.equals("wearer")) { SilentGearTraitSpike.run(player, api, report); }
        ItemStack witness = api.boot("iron");
        SilentGearWearerFixture.active = true;
        try {
            boolean witnessed = false;
            try { SilentGearTraitSpike.nativeClass("util.GearData").getMethod("recalculateGearData", ItemStack.class, net.minecraft.world.entity.player.Player.class).invoke(null, witness, player); }
            catch (InvocationTargetException expected) { witnessed = expected.getCause() instanceof AssertionError; }
            require(witnessed, "Recalculation observer was not calibrated");
        } finally { SilentGearWearerFixture.active = false; }
        report.append("\nWEARER A: paired local predicates, optional binding; production routing remains OFF\n");
        for (String ingredient : List.of("ingots/nickel", "ingots/lead", "ingots/refined_iron", "ingots/crimson_steel", "gems/prismarine")) {
            var items = new ArrayList<String>();
            for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", ingredient)))) {
                items.add(BuiltInRegistries.ITEM.getKey(holder.value()).toString());
            }
            report.append("NATIVE INGREDIENT c:").append(ingredient).append('=').append(items).append('\n');
        }
        report.append("Native creative example materials=").append(SilentGearTraitSpike.nativeClass("core.BuiltinMaterials").getField("EXAMPLE_SUB_ITEM_MATERIALS").get(null)).append('\n');
        for (String[] family : List.of(new String[]{"adamant", "nickel"}, new String[]{"aquatic", "lead"},
                new String[]{"flame_ward", "crimson_steel"}, new String[]{"stellar", "refined_iron"})) {
            String name = family[0], material = family[1];
            var armor = List.of(api.boot(material), api.armor("chestplate", "chestplate_plates", material),
                    api.armor("helmet", "helmet_plates", material), api.armor("leggings", "legging_plates", material));
            Object instance = SilentGearTraitSpike.view(armor.getFirst()).get("silentgear:" + name);
            require(instance != null, "Native constructed material lacks " + name);
            Object effect = SilentGearWearerFixture.effects(instance).stream().filter(SilentGearWearerFixture::isWielder).findFirst().orElseThrow();
            require(((SilentGearWearerReadiness) effect).mismatchedboots$sgSetReady() != refused, "Paired final proof differs: " + variant);
            if (!variant.equals("wearer")) { require((boolean) effect.getClass().getMethod("sgtest$modified").invoke(null), "Competing transformation did not run"); }
            inspectDefinition(effect, name, report);
            Object armorType = ((Supplier<?>) SilentGearTraitSpike.nativeClass("setup.gear.GearTypes").getField("ARMOR").get(null)).get();
            Method counter = effect.getClass().getDeclaredMethod("getSetPieceCount", SilentGearTraitSpike.nativeClass("api.traits.TraitActionContext"),
                    SilentGearTraitSpike.nativeClass("api.item.GearType"), net.minecraft.world.entity.player.Player.class);
            counter.setAccessible(true);
            Object weaponType = ((Supplier<?>) SilentGearTraitSpike.nativeClass("setup.gear.GearTypes").getField("SWORD").get(null)).get();
            require((int) counter.invoke(effect, SilentGearTraitSpike.context(player, instance, armor.getFirst()), weaponType, player) == 1, "Non-armor branch was changed");
            ItemStack sword = api.armor("sword", "sword_blade", material), swordBefore = sword.copy();
            equip(player, armor, 4);
            effect.getClass().getMethod("inventoryTick", SilentGearTraitSpike.nativeClass("api.traits.TraitActionContext"), net.minecraft.world.level.Level.class,
                    net.minecraft.world.entity.Entity.class, boolean.class).invoke(effect, SilentGearTraitSpike.context(player, instance, sword), player.level(), player, true);
            require(player.getActiveEffects().isEmpty() && ItemStack.matches(swordBefore, sword), "Armor definition reached an unrelated weapon gear branch");
            for (int count = 0; count <= 4; count++) {
                equip(player, armor, count);
                SilentGearWearerFixture.active = false;
                require((int) counter.invoke(effect, SilentGearTraitSpike.context(player, instance, armor.getFirst()), armorType, player) == count, "Native piece counter differs");
                nativeTick(effect, player, instance, armor.getFirst().copy(), true);
                check(player, name, count, true);
            }
            for (ItemStack root : NeoForgeSilentGearTest.workbench(player, armor.getFirst(), new ItemStack(Items.DIAMOND_BOOTS))) {
                var entry = SilentGearCompatibility.effectiveTraits(root).stream().filter(value -> value.id().getPath().equals(name)).findFirst().orElseThrow();
                SilentGearWearerFixture.active = true;
                ItemStack carrier = SilentGearWearerFixture.carrier(root), before = root.copy(), carrierBefore = carrier.copy();
                if (!refused) {
                    boolean deniedRoot = false;
                    try { SilentGearWearerFixture.tick(effect, player, entry.nativeInstance(), root, true); }
                    catch (IllegalArgumentException expected) { deniedRoot = true; }
                    require(deniedRoot, "Optional binding accepted mixed root as native receiver");
                    for (Object unrelated : SilentGearWearerFixture.effects(entry.nativeInstance())) {
                        if (SilentGearWearerFixture.isWielder(unrelated)) { continue; }
                        boolean deniedEffect = false;
                        try { SilentGearWearerFixture.tick(unrelated, player, entry.nativeInstance(), carrier, true); }
                        catch (IllegalArgumentException expected) { deniedEffect = true; }
                        require(deniedEffect, "Optional binding entered a non-Wielder effect");
                    }
                }
                for (int count = 1; count <= 4; count++) {
                    equip(player, armor, count); player.setItemSlot(EquipmentSlot.FEET, root);
                    var snapshots = snapshots(player);
                    SilentGearWearerFixture.active = true;
                    require((int) counter.invoke(effect, SilentGearTraitSpike.context(player, instance, carrier), armorType, player) == count - (refused ? 1 : 0), "Paired native piece count differs");
                    // Native chest caller sees our feet, not just a special carrier context.
                    nativeTick(effect, player, instance, armor.get(1).copy(), true);
                    check(player, name, count - (refused ? 1 : 0), true);
                    unchanged(player, snapshots);
                    player.removeAllEffects();
                    if (!refused) { invoke(effect, player, entry.nativeInstance(), carrier, true); check(player, name, count, true); }
                    unchanged(player, snapshots);
                }
                if (refused) {
                    boolean denied = false;
                    try { invoke(effect, player, entry.nativeInstance(), carrier, true); }
                    catch (IllegalStateException expected) { denied = expected.getMessage().equals("Paired set proof unavailable"); }
                    require(denied && player.getActiveEffects().isEmpty(), "Missing pair allowed optional wearer dispatch");
                    require(ItemStack.matches(before, root) && ItemStack.matches(carrierBefore, carrier), "Refusal changed root/carrier");
                    SilentGearWearerFixture.active = false;
                    continue;
                }
                // Native selection includes both hands. A held root is never an extra armor-set member.
                for (EquipmentSlot slot : List.of(EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
                    equip(player, armor, 0); player.setItemSlot(slot, root);
                    invoke(effect, player, entry.nativeInstance(), carrier, slot != null);
                    check(player, name, slot == EquipmentSlot.FEET && !refused ? 1 : 0, true);
                }
                equip(player, armor, 4); player.setItemSlot(EquipmentSlot.FEET, root);
                invoke(effect, player, entry.nativeInstance(), carrier, false); check(player, name, 4, false);
                player.tickCount = 21;
                invoke(effect, player, entry.nativeInstance(), carrier, true); check(player, name, 4, false);
                player.tickCount = 30;
                invoke(effect, player, entry.nativeInstance(), carrier, true); check(player, name, refused ? 3 : 4, true);
                for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
                // No further callbacks: exercise actual native potion ticking, not manual removal.
                Method tickEffects = net.minecraft.world.entity.LivingEntity.class.getDeclaredMethod("tickEffects"); tickEffects.setAccessible(true);
                for (int tick = 0; tick < 40; tick++) { tickEffects.invoke(player); }
                require(player.getActiveEffects().isEmpty(), "Native effects did not expire naturally");
                require(ItemStack.matches(before, root) && ItemStack.matches(carrierBefore, carrier), "Wearer callback changed root/source/carrier");
                if (name.equals("flame_ward")) { require(!root.has(DataComponents.DAMAGE_RESISTANT), "Potion broadened dropped-item immunity"); }
                SilentGearWearerFixture.active = false;
            }
            report.append(name).append(": native 0..4; both paid outputs; native-chest recognition; read-only gear; ")
                    .append(refused ? "both predicate extensions + optional dispatch refused before actor writes" : "carrier FEET/hands/idle, cadence10, expiry38").append('\n');
        }
        if (!refused) { obtainableAquatic(player, api, report); }
        if (variant.equals("wearer-compatible")) { compatibleSlot(player, api, report); }
        // Root remains ordinary outside the explicit research scope.
        SilentGearWearerFixture.active = false;
        var nativeBrilliant = api.boot("gold");
        for (var root : NeoForgeSilentGearTest.workbench(player, nativeBrilliant, new ItemStack(Items.DIAMOND_BOOTS))) {
            SilentGearWearerFixture.active = true;
            ItemStack before = root.copy(), carrier = SilentGearWearerFixture.carrier(root), carrierBefore = carrier.copy();
            require(carrier.getItem().makesPiglinsNeutral(carrier, player), "Bound native Brilliant receiver lost merged trait view");
            require(!root.getItem().makesPiglinsNeutral(root, player), "A accidentally enabled production Brilliant");
            require(ItemStack.matches(before, root) && ItemStack.matches(carrierBefore, carrier), "Brilliant carrier mutated gear");
            SilentGearWearerFixture.active = false;
        }
    }

    private static void invoke(Object effect, ServerPlayer player, Object instance, ItemStack carrier, boolean selected) throws Throwable {
        ItemStack before = carrier.copy();
        SilentGearWearerFixture.tick(effect, player, instance, carrier, selected);
        require(ItemStack.matches(before, carrier), "Native effect mutated its gear context");
    }

    private static void nativeTick(Object effect, ServerPlayer player, Object instance, ItemStack carrier, boolean selected) throws Exception {
        ItemStack before = carrier.copy();
        effect.getClass().getMethod("inventoryTick", SilentGearTraitSpike.nativeClass("api.traits.TraitActionContext"), net.minecraft.world.level.Level.class,
                net.minecraft.world.entity.Entity.class, boolean.class).invoke(effect, SilentGearTraitSpike.context(player, instance, carrier), player.level(), player, selected);
        require(ItemStack.matches(before, carrier), "Independent native callback mutated gear");
    }

    private static void equip(ServerPlayer player, List<ItemStack> armor, int count) {
        player.removeAllEffects(); player.tickCount = 20;
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
        for (int i = 0; i < count; i++) { player.setItemSlot(SLOTS.get(i), armor.get(i).copy()); }
    }

    private static void check(ServerPlayer player, String name, int count, boolean selected) {
        switch (name) {
            case "adamant" -> potion(player, MobEffects.RESISTANCE, selected ? count == 4 ? 2 : 1 : 0);
            case "aquatic" -> potion(player, MobEffects.WATER_BREATHING, selected && count == 4 ? 1 : 0);
            case "flame_ward" -> potion(player, MobEffects.FIRE_RESISTANCE, selected && count == 4 ? 1 : 0);
            case "stellar" -> {
                potion(player, MobEffects.SPEED, selected ? Math.max(0, count - 1) : 0);
                // Native clamping at count=0 selects the first entry, too.
                potion(player, MobEffects.JUMP_BOOST, selected ? Math.max(1, count) : 0);
            }
            default -> throw new AssertionError(name);
        }
    }

    private static void potion(ServerPlayer player, Holder<MobEffect> type, int level) {
        MobEffectInstance actual = player.getEffect(type);
        require((actual == null ? 0 : actual.getAmplifier() + 1) == level, "Native potion level differs: " + type + " expected=" + level + " actual=" + actual);
        if (actual != null) { require(actual.getDuration() == 38 && actual.isAmbient() && !actual.isVisible(), "Native duration/presentation differs"); }
    }

    private static List<ItemStack> snapshots(ServerPlayer player) {
        var result = new ArrayList<ItemStack>();
        for (var slot : EquipmentSlot.values()) { result.add(player.getItemBySlot(slot).copy()); }
        return result;
    }
    private static void unchanged(ServerPlayer player, List<ItemStack> before) {
        int i = 0;
        for (var slot : EquipmentSlot.values()) { require(ItemStack.matches(before.get(i++), player.getItemBySlot(slot)), "Wearer changed actual equipment/source"); }
    }

    private static void inspectDefinition(Object effect, String name, StringBuilder report) throws Exception {
        Field potions = effect.getClass().getDeclaredField("potions"); potions.setAccessible(true);
        var map = (Map<?, ?>) potions.get(effect);
        Object armor = ((Supplier<?>) SilentGearTraitSpike.nativeClass("setup.gear.GearTypes").getField("ARMOR").get(null)).get();
        require(map.size() == 1 && map.containsKey(armor), "Admitted definition changed gear branch");
        var entries = (List<?>) map.get(armor);
        require(entries.size() == (name.equals("stellar") ? 2 : 1), "Admitted definition changed potion count");
        report.append("DEFINITION ").append(name).append(' ');
        for (Object entry : entries) {
            for (String field : List.of("type", "effect", "duration", "levels")) {
                Field member = entry.getClass().getDeclaredField(field); member.setAccessible(true);
                report.append(field).append('=').append(member.get(entry)).append(' ');
            }
        }
        report.append('\n');
    }

    private static void compatibleSlot(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Throwable {
        var stellar = api.boot("refined_iron");
        var root = NeoForgeSilentGearTest.workbench(player, stellar, new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        Object instance = SilentGearTraitSpike.view(stellar).get("silentgear:stellar");
        Object effect = SilentGearWearerFixture.effects(instance).stream().filter(SilentGearWearerFixture::isWielder).findFirst().orElseThrow();
        var armor = List.of(stellar, api.armor("chestplate", "chestplate_plates", "refined_iron"), ItemStack.EMPTY,
                api.armor("leggings", "legging_plates", "refined_iron"));
        equip(player, armor, 4); player.setItemSlot(EquipmentSlot.FEET, root); player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        SilentGearWearerFixture.active = true;
        invoke(effect, player, instance, armor.get(1).copy(), true); check(player, "stellar", 4, true);
        SilentGearWearerFixture.active = false; player.removeAllEffects();
        invoke(effect, player, instance, armor.get(1).copy(), true); check(player, "stellar", 3, true);
        report.append("Compatible independent HEAD predicate contribution preserved: mixed FEET ON=4, OFF=3; native counter retained\n");
    }

    private static void obtainableAquatic(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Throwable {
        var armor = new ArrayList<ItemStack>();
        for (String[] piece : List.of(new String[]{"boots", "boot_plates"}, new String[]{"chestplate", "chestplate_plates"},
                new String[]{"helmet", "helmet_plates"}, new String[]{"leggings", "legging_plates"})) {
            var input = CraftingInput.of(2, 1, List.of(api.armor(piece[0], piece[1], "iron"), api.part("coating", "prismarine")));
            Object recipe = api.swapClass.getField("INSTANCE").get(null);
            require((boolean) api.matches.invoke(recipe, input, player.level()), "Native prismarine coating recipe refused");
            ItemStack coated = (ItemStack) api.assemble.invoke(recipe, input);
            require(api.traitLevel(coated, "aquatic") == 5, "Prismarine coating did not produce Aquatic V"); armor.add(coated);
        }
        Object instance = SilentGearTraitSpike.view(armor.getFirst()).get("silentgear:aquatic");
        Object effect = SilentGearWearerFixture.effects(instance).stream().filter(SilentGearWearerFixture::isWielder).findFirst().orElseThrow();
        equip(player, armor, 4); nativeTick(effect, player, instance, armor.get(1).copy(), true); check(player, "aquatic", 4, true);
        for (var root : NeoForgeSilentGearTest.workbench(player, armor.getFirst(), new ItemStack(Items.DIAMOND_BOOTS))) {
            equip(player, armor, 4); player.setItemSlot(EquipmentSlot.FEET, root); SilentGearWearerFixture.active = true;
            nativeTick(effect, player, instance, armor.get(1).copy(), true); check(player, "aquatic", 4, true);
            SilentGearWearerFixture.active = false;
        }
        report.append("Obtainable Aquatic fixture: native iron armor + prismarine coating recipe, Aquatic V on all four pieces; native and both mixed FEET activate water breathing\n");
    }

    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
