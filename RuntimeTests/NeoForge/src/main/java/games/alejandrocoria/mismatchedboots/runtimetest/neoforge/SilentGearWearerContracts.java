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
        boolean refused = List.of("wearer-type", "wearer-trait", "wearer-effect").contains(variant);
        ItemStack witness = api.boot("iron");
        SilentGearWearerFixture.active = true;
        try {
            boolean witnessed = false;
            try { SilentGearTraitSpike.nativeClass("util.GearData").getMethod("recalculateGearData", ItemStack.class, net.minecraft.world.entity.player.Player.class).invoke(null, witness, player); }
            catch (InvocationTargetException expected) { witnessed = expected.getCause() instanceof AssertionError; }
            require(witnessed, "Recalculation observer was not calibrated");
        } finally { SilentGearWearerFixture.active = false; }
        report.append("\nWEARER C: production mixed inventory tick + native other-slot callers; research set hooks removed\n");
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
            if (!List.of("wearer", "wearer-definition").contains(variant)) { require((boolean) effect.getClass().getMethod("sgtest$modified").invoke(null), "Competing transformation did not run"); }
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
                    root.inventoryTick(player.level(), player, EquipmentSlot.FEET);
                    check(player, name, count, !refused);
                    unchanged(player, snapshots);
                }
                if (refused) {
                    boolean denied = false;
                    try { invoke(effect, player, entry.nativeInstance(), carrier, true); }
                    catch (IllegalStateException expected) { denied = expected.getMessage().equals("Paired set proof unavailable"); }
                    require(denied && player.getActiveEffects().isEmpty(), "Missing pair allowed optional wearer dispatch");
                    require(ItemStack.matches(before, root) && ItemStack.matches(carrierBefore, carrier), "Refusal changed root/carrier");
                    root.inventoryTick(player.level(), player, EquipmentSlot.FEET);
                    require(player.getActiveEffects().isEmpty(), "Refused production tick published potions");
                    SilentGearWearerFixture.active = false;
                    continue;
                }
                // Native selection includes both hands. A held root is never an extra armor-set member.
                for (EquipmentSlot slot : List.of(EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
                    equip(player, armor, 0); player.setItemSlot(slot, root);
                    root.inventoryTick(player.level(), player, slot);
                    check(player, name, slot == EquipmentSlot.FEET && !refused ? 1 : 0, true);
                }
                equip(player, armor, 4); player.setItemSlot(EquipmentSlot.FEET, root);
                root.inventoryTick(player.level(), player, null); check(player, name, 4, false);
                player.tickCount = 21;
                root.inventoryTick(player.level(), player, EquipmentSlot.FEET); check(player, name, 4, false);
                player.tickCount = 30;
                root.inventoryTick(player.level(), player, EquipmentSlot.FEET); check(player, name, 4, true);
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
        additionalCases(player, api, report, refused);
        // Both B features and existing independent writers survive either refused member of the pair.
        SilentGearEffectsTest.smoke(player, api, report);
        SilentGearLiveStateTest.smoke(player, api, report);
        equip(player, List.of(), 0);
        var moonRoot = NeoForgeSilentGearTest.workbench(player, api.boot("azure_silver"), new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        player.setItemSlot(EquipmentSlot.FEET, moonRoot);
        var fall = new net.neoforged.neoforge.event.entity.living.LivingFallEvent(player, 10, 1);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(fall);
        require(Math.abs(fall.getDistance() - 10 * (1 + 4 * -0.15F)) < 1E-6, "Independent B Moonwalker failed");
        SilentGearWearerFixture.active = false;
        var nativeBrilliant = api.boot("gold");
        for (var root : NeoForgeSilentGearTest.workbench(player, nativeBrilliant, new ItemStack(Items.DIAMOND_BOOTS))) {
            SilentGearWearerFixture.active = true;
            ItemStack before = root.copy(), carrier = SilentGearWearerFixture.carrier(root), carrierBefore = carrier.copy();
            require(carrier.getItem().makesPiglinsNeutral(carrier, player), "Bound native Brilliant receiver lost merged trait view");
            require(root.getItem().makesPiglinsNeutral(root, player), "B production Brilliant regressed");
            require(ItemStack.matches(before, root) && ItemStack.matches(carrierBefore, carrier), "Brilliant carrier mutated gear");
            SilentGearWearerFixture.active = false;
        }
        if (variant.equals("wearer-definition")) { changedDefinition(player, api, report); }
        if (variant.equals("wearer-throw")) { partialActorFailure(player, api, report); }
    }

    private static void partialActorFailure(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Throwable {
        int[] calls = {0}; boolean[] armed = {false};
        var actor = new net.neoforged.neoforge.common.util.FakePlayer(player.level(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "wearer-fault")) {
            @Override public boolean addEffect(MobEffectInstance effect, net.minecraft.world.entity.Entity source) {
                boolean result = super.addEffect(effect, source);
                if (armed[0]) {
                    calls[0]++;
                    if (effect.getEffect().equals(MobEffects.JUMP_BOOST)) { throw new IllegalStateException("Controlled actor failure after native potion publication"); }
                }
                return result;
            }
        };
        var root = NeoForgeSilentGearTest.workbench(player, api.boot("refined_iron"), api.boot("gold")).getFirst();
        actor.setItemSlot(EquipmentSlot.FEET, root);
        actor.setItemSlot(EquipmentSlot.CHEST, api.armor("chestplate", "chestplate_plates", "refined_iron"));
        actor.setItemSlot(EquipmentSlot.HEAD, api.armor("helmet", "helmet_plates", "refined_iron"));
        actor.setItemSlot(EquipmentSlot.LEGS, api.armor("leggings", "legging_plates", "refined_iron"));
        var before = snapshots(actor); actor.tickCount = 20;
        try {
            armed[0] = true;
            for (int attempt = 0; attempt < 3; attempt++) { root.inventoryTick(actor.level(), actor, EquipmentSlot.FEET); }
            require(calls[0] == 2, "Partial actor failure retried native potion work");
            potion(actor, MobEffects.SPEED, 3); potion(actor, MobEffects.JUMP_BOOST, 4);
            unchanged(actor, before);
        } finally { armed[0] = false; }
        // Runtime revocation applies to native other-slot callers, too, not just the optional dispatcher.
        actor.removeAllEffects();
        var chest = actor.getItemBySlot(EquipmentSlot.CHEST); var instance = SilentGearTraitSpike.view(chest).get("silentgear:stellar");
        var effect = SilentGearWearerFixture.effects(instance).stream().filter(SilentGearWearerFixture::isWielder).findFirst().orElseThrow();
        nativeTick(effect, actor, instance, chest.copy(), true); check(actor, "stellar", 3, true);
        require(net.minecraft.world.entity.monster.piglin.PiglinAi.isWearingSafeArmor(actor), "Wielder actor failure disabled Brilliant");
        equip(player, List.of(), 0);
        var moon = NeoForgeSilentGearTest.workbench(player, api.boot("azure_silver"), new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        player.setItemSlot(EquipmentSlot.FEET, moon);
        var fall = new net.neoforged.neoforge.event.entity.living.LivingFallEvent(player, 10, 1);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(fall);
        require(Math.abs(fall.getDistance() - 10 * (1 + 4 * -0.15F)) < 1E-6, "Wielder actor failure disabled Moonwalker");
        SilentGearEffectsTest.smoke(player, api, report); SilentGearLiveStateTest.smoke(player, api, report);
        report.append("Post-publication actor failure: two actual native potion writes remain, no replay across three root ticks, immutable gear; native-chest count loses only mixed FEET; B/repair/state survive\n");
    }

    private static void additionalCases(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report, boolean refused) throws Throwable {
        ItemStack stellar = api.boot("refined_iron"), aquatic = api.boot("lead");
        var aquaticSet = List.of(api.armor("chestplate", "chestplate_plates", "lead"),
                api.armor("helmet", "helmet_plates", "lead"), api.armor("leggings", "legging_plates", "lead"));
        for (ItemStack second : List.of(stellar, aquatic)) {
            for (ItemStack root : NeoForgeSilentGearTest.workbench(player, stellar, second)) {
                equip(player, List.of(), 0); player.setItemSlot(EquipmentSlot.FEET, root);
                player.setItemSlot(EquipmentSlot.MAINHAND, root.copy()); player.setItemSlot(EquipmentSlot.OFFHAND, root.copy());
                var before = snapshots(player); SilentGearWearerFixture.active = true;
                try {
                    for (var slot : List.of(EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
                        player.getItemBySlot(slot).inventoryTick(player.level(), player, slot);
                    }
                    check(player, "stellar", 1, !refused);
                    potion(player, MobEffects.WATER_BREATHING, 0);
                    unchanged(player, before);
                    if (second == aquatic) {
                        player.removeAllEffects();
                        player.setItemSlot(EquipmentSlot.CHEST, aquaticSet.get(0));
                        player.setItemSlot(EquipmentSlot.HEAD, aquaticSet.get(1));
                        player.setItemSlot(EquipmentSlot.LEGS, aquaticSet.get(2));
                        root.inventoryTick(player.level(), player, EquipmentSlot.FEET);
                        check(player, "stellar", 1, !refused);
                        potion(player, MobEffects.WATER_BREATHING, refused ? 0 : 1);
                    }
                } finally { SilentGearWearerFixture.active = false; }
            }
        }
        ItemStack root = NeoForgeSilentGearTest.workbench(player, stellar, new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        var invalidRoots = new ArrayList<ItemStack>();
        ItemStack invalid = root.copy(); invalid.setCount(2); invalidRoots.add(invalid);
        invalid = root.copy(); invalid.remove(DataComponents.EQUIPPABLE); invalidRoots.add(invalid);
        invalid = root.copy(); invalid.remove(games.alejandrocoria.mismatchedboots.content.ModContent.mixedBootsDataType().orElseThrow()); invalidRoots.add(invalid);
        invalidRoots.add(new ItemStack(Items.DIAMOND_BOOTS));
        for (var candidate : invalidRoots) {
            equip(player, List.of(), 0); player.setItemSlot(EquipmentSlot.FEET, candidate);
            candidate.inventoryTick(player.level(), player, EquipmentSlot.FEET);
            require(player.getActiveEffects().isEmpty(), "Invalid/plain root gained Wielder potions");
        }
        // Probability oracle is independent of the adapter. Force both native repair outcomes while
        // the actual production tick also dispatches Stellar potions; a duplicate heal cannot hide.
        var randomField = Class.forName("net.silentchaos512.lib.util.MathUtils").getDeclaredField("RANDOM"); randomField.setAccessible(true);
        var random = (java.util.Random) randomField.get(null);
        Object instance = SilentGearTraitSpike.view(stellar).get("silentgear:stellar");
        for (boolean activate : List.of(true, false)) {
            long seed = SilentGearWearerFixture.stellarRepairSeed(instance, activate);
            equip(player, List.of(), 0); player.setItemSlot(EquipmentSlot.FEET, root);
            root.setDamageValue(12); var before = root.copy(); random.setSeed(seed);
            SilentGearWearerFixture.active = true;
            try { root.inventoryTick(player.level(), player, EquipmentSlot.FEET); }
            finally { SilentGearWearerFixture.active = false; }
            require(root.getDamageValue() == 12 - (activate ? SilentGearTraitSpike.level(instance) : 0), "Stellar repaired twice or lost native repair");
            check(player, "stellar", 1, !refused);
            before.setDamageValue(root.getDamageValue()); require(ItemStack.matches(before, root), "Combined tick changed unrelated root/source state");
        }
        // Actual ServerPlayer ticking, not just direct adapter calls. Only mixed gear is equipped:
        // native item ticks/recalculation are intentionally outside the read-only observer scope.
        var actor = NeoForgeJustDireNegateFallTest.player(player.level().getServer(), net.minecraft.world.level.GameType.SURVIVAL);
        try {
            actor.setPos(0, 80, 0); actor.setNoGravity(true);
            ItemStack healthy = NeoForgeSilentGearTest.workbench(player, stellar, new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
            actor.setItemSlot(EquipmentSlot.FEET, healthy); var before = healthy.copy();
            for (int tick = 0; tick < 65; tick++) { actor.doTick(); }
            require(actor.hasEffect(MobEffects.JUMP_BOOST) != refused, "Continuous production ticks did not refresh potion");
            require(ItemStack.matches(before, healthy), "Continuous healthy tick changed gear");
            actor.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            actor.getInventory().setItem(10, healthy); // Not the selected hotbar slot: native hands are active.
            for (int tick = 0; tick < 45; tick++) { actor.doTick(); }
            require(!actor.hasEffect(MobEffects.JUMP_BOOST), "Idle inventory refreshed worn potion or prevented expiry");
        } finally { actor.getInventory().clearContent(); actor.removeAllEffects(); }
        report.append("C extras: differing/duplicate sources; simultaneous hands count no extra armor; seeded Stellar heal/nonheal once with potions; real ServerPlayer refresh then idle expiry\n");
    }

    private static void changedDefinition(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Throwable {
        ItemStack source = api.boot("refined_iron");
        Object instance = SilentGearTraitSpike.view(source).get("silentgear:stellar");
        Object effect = SilentGearWearerFixture.effects(instance).stream().filter(SilentGearWearerFixture::isWielder).findFirst().orElseThrow();
        Field field = effect.getClass().getDeclaredField("potions"); field.setAccessible(true); Object original = field.get(effect);
        ItemStack root = NeoForgeSilentGearTest.workbench(player, source, new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
        equip(player, List.of(), 0); player.setItemSlot(EquipmentSlot.FEET, root);
        player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 100, 2));
        var before = root.copy();
        try {
            field.set(effect, Map.of()); // A changed admitted definition, not an unavailable material recipe.
            for (int repeat = 0; repeat < 2; repeat++) { root.inventoryTick(player.level(), player, EquipmentSlot.FEET); }
            require(player.getEffect(MobEffects.JUMP_BOOST).getDuration() == 100, "Definition refusal erased or refreshed an existing potion");
            require(ItemStack.matches(before, root), "Definition refusal changed gear");
        } finally { field.set(effect, original); }
        player.removeAllEffects(); root.inventoryTick(player.level(), player, EquipmentSlot.FEET);
        require(player.getActiveEffects().isEmpty(), "Refused definition retried dispatch");
        Object armor = ((Supplier<?>) SilentGearTraitSpike.nativeClass("setup.gear.GearTypes").getField("ARMOR").get(null)).get();
        Method counter = effect.getClass().getDeclaredMethod("getSetPieceCount", SilentGearTraitSpike.nativeClass("api.traits.TraitActionContext"),
                SilentGearTraitSpike.nativeClass("api.item.GearType"), net.minecraft.world.entity.player.Player.class); counter.setAccessible(true);
        require((int) counter.invoke(effect, SilentGearTraitSpike.context(player, instance, source), armor, player) == 0, "Definition refusal left native-caller mixed recognition enabled");
        SilentGearEffectsTest.smoke(player, api, report); SilentGearLiveStateTest.smoke(player, api, report);
        report.append("Changed Wielder definition: refused before potion publication; no retry after restore; existing potion retained, both set predicates revoked, independent writers survive\n");
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
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        invoke(effect, player, instance, armor.get(1).copy(), true); check(player, "stellar", 3, true);
        report.append("Compatible independent HEAD predicate contribution preserved: mixed FEET present=4, removed=3; native counter retained\n");
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
            player.removeAllEffects(); root.inventoryTick(player.level(), player, EquipmentSlot.FEET); check(player, "aquatic", 4, true);
            SilentGearWearerFixture.active = false;
        }
        report.append("Obtainable Aquatic fixture: native iron armor + prismarine coating recipe, Aquatic V on all four pieces; native and both mixed FEET activate water breathing\n");
    }

    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
