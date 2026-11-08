package games.alejandrocoria.mismatchedleggings.test.mysticaleffects;

import games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture.MysticalAugments;
import games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture.MysticalAugmentsCompatibility;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

public final class EffectsChecks {
    private static boolean finished;
    public static final StringBuilder REPORT = new StringBuilder();

    private EffectsChecks() {}

    public static boolean enabled() { return Boolean.getBoolean("leggings.test.mysticalEffects"); }
    public static boolean absent() { return Boolean.getBoolean("leggings.test.maAbsent"); }

    public static void seed(MinecraftServer server) {
        if (!server.isDedicatedServer() || finished || server.getTickCount() < 20) { return; }
        finished = true;
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "setworldspawn 0 " + (server.overworld().getMinY() + 1) + " 0");
            server.saveEverything(true, true, true);
            SkeletonChecks.passed("server");
        } catch (Throwable failure) { SkeletonChecks.failed("server", failure); }
        finally { server.halt(false); }
    }

    public static void initialize(ServerPlayer player) throws Exception {
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.fallDistance = 0;
        player.connection.teleport(.5, player.level().getMinY() + 1, .5, 0, 0);
        player.getInventory().clearContent();
        player.getFoodData().setFoodLevel(20);
        if (absent()) {
            var pair = games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory.createPair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS));
            require(!pair.primary().isEmpty(), "Optional absence broke ordinary composition");
            require(MysticalAugmentsCompatibility.selectedIds(pair.primary()).isEmpty(), "Optional absence authorized augments");
            require(EffectsChecks.class.getClassLoader().getResource("com/blakebr0/mysticalagriculture/api/util/AugmentUtils.class") == null, "Absence staged native MA classes");
            player.setItemSlot(EquipmentSlot.LEGS, pair.primary()); player.aiStep();
            return;
        }
        var source = NativeAugments.item("awakened_supremium_leggings", "SPEED_I", "SLOWNESS_RESISTANCE");
        var other = NativeAugments.item("awakened_supremium_leggings", "SPEED_III", "HEALTH_BOOST_III");
        var first = source.copy();
        var second = other.copy();
        var pair = games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory.createPair(source, other);
        for (var root : List.of(pair.primary(), pair.inverse())) {
            var ids = games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture.MysticalAugmentsCompatibility.selectedIds(root).stream().map(Object::toString).toList();
            require(ids.contains("mysticalagriculture:speed_iii") == !variant("equipment") && !ids.contains("mysticalagriculture:speed_i"), "Strongest selection failed");
        }
        require(ItemStack.matches(source, first) && ItemStack.matches(other, second), "Mixed changed input sources");
        selection(player);
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        write("server-results.txt", REPORT.toString());
    }

    static boolean variant(String name) { return System.getProperty("leggings.test.maVariant", "normal").equals(name); }

    private static List<Identifier> ids(ItemStack source) {
        var before = source.copy();
        var roots = MixedLeggingsFactory.createPair(source, new ItemStack(Items.IRON_LEGGINGS));
        var primary = MysticalAugmentsCompatibility.selectedIds(roots.primary());
        require(primary.equals(MysticalAugmentsCompatibility.selectedIds(roots.inverse())), "Orientation changed selection");
        require(ItemStack.matches(before, source), "Reader mutated original history");
        for (var root : List.of(roots.primary(), roots.inverse())) {
            var data = MixedLeggingsStructure.validData(root).orElseThrow();
            require(ItemStack.matches(before, data.left().source().is(Items.IRON_LEGGINGS) ? data.right().source() : data.left().source()), "Reader changed stored source");
        }
        return primary;
    }

    @SuppressWarnings("unchecked")
    private static void selection(ServerPlayer player) throws Exception {
        var component = (DataComponentType<List<Object>>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(Identifier.parse("mysticalagriculture:equipped_augments")).orElseThrow().value();
        var constructor = NativeAugments.type("api.components.AugmentComponent").getConstructor(Identifier.class, int.class);
        for (var spec : MysticalAugments.CATALOG.values()) {
            var source = NativeAugments.item("awakened_supremium_leggings", spec.id().getPath().toUpperCase(java.util.Locale.ROOT));
            boolean refused = variant("equipment") && spec.family().route == MysticalAugments.Route.PLAYER
                    || variant("health") && spec.family() == MysticalAugments.Family.HEALTH_BOOST
                    || variant("armor") && spec.family() == MysticalAugments.Family.LUCK;
            require(ids(source).equals(refused ? List.of() : List.of(spec.id())), "Catalog/native binding mismatch " + spec);
            for (String path : MysticalAugments.LEGGINGS) {
                var constrained = NativeAugments.item(path);
                constrained.set(component, List.of(constructor.newInstance(spec.id(), 0)));
                boolean nativeAllowed = Boolean.TRUE.equals(NativeAugments.invoke(constrained.getItem(), "canApplyAugment",
                        new Class<?>[]{NativeAugments.type("api.tinkering.Augment")}, NativeAugments.augment(spec.id().getPath().toUpperCase(java.util.Locale.ROOT))));
                require(!ids(constrained).isEmpty() == (nativeAllowed && !refused), "Source tier mismatch " + path + "/" + spec.id());
            }
        }
        Identifier speed = Identifier.parse("mysticalagriculture:speed_iii");
        var source = NativeAugments.item("awakened_supremium_leggings");
        for (List<Object> entries : List.of(List.of(constructor.newInstance(speed, -1)), List.of(constructor.newInstance(speed, 2)),
                List.of(constructor.newInstance(speed, 0), constructor.newInstance(speed, 0)),
                List.of(constructor.newInstance(speed, 0), constructor.newInstance(speed, 1), constructor.newInstance(speed, 1)))) {
            source.set(component, entries);
            require(ids(source).isEmpty(), "Malformed slots authorized effects");
        }
        source.set(component, List.of(constructor.newInstance(speed, 0), constructor.newInstance(speed, 1)));
        require(ids(source).size() == (variant("equipment") ? 0 : 1), "Physical duplicates dispatched twice");
        source.set(component, List.of(constructor.newInstance(Identifier.parse("addon:unknown"), 0), constructor.newInstance(speed, 1)));
        require(ids(source).size() == (variant("equipment") ? 0 : 1), "Unknown addon authorized effects or hid a valid slot");
        for (String unsupported : List.of("jump_boost_iii", "no_fall_damage", "slow_falling", "flight", "unknown")) {
            source.set(component, List.of(constructor.newInstance(Identifier.parse("mysticalagriculture:" + unsupported), 0)));
            require(ids(source).isEmpty(), "Unsupported ID authorized effects");
        }
        var root = MixedLeggingsFactory.createPair(NativeAugments.item("awakened_supremium_leggings", "SPEED_III"), new ItemStack(Items.IRON_LEGGINGS)).primary();
        var badCount = root.copy(); badCount.setCount(2);
        var bare = root.copy(); bare.remove(games.alejandrocoria.mismatchedleggings.content.LeggingsContent.mixedLeggingsDataType());
        var spoof = new ItemStack(Items.STICK); spoof.applyComponents(root.getComponents());
        for (var bad : List.of(badCount, bare, spoof)) { require(MysticalAugmentsCompatibility.selectedIds(bad).isEmpty(), "Invalid root authorized effects"); }
        var ordinary = MixedLeggingsFactory.createPair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS)).primary();
        ordinary.set(component, List.of(constructor.newInstance(speed, 0)));
        require(MysticalAugmentsCompatibility.selectedIds(ordinary).isEmpty(), "Copied root component authorized effects");
        player.getInventory().clearContent();
        REPORT.append("All 22 IDs/six source constraints, both orientations, malformed slots/tier/root, duplicates/addons and immutable history passed; selection coverage only\n");
        armor(player);
        absorption(player);
    }

    private static void armor(ServerPlayer player) {
        for (String field : List.of("FIRE_RESISTANCE", "LUCK_III", "MINING_FATIGUE_RESISTANCE", "POISON_RESISTANCE", "SLOWNESS_RESISTANCE", "WITHER_RESISTANCE")) {
            var nativeItem = NativeAugments.item("awakened_supremium_leggings", field);
            var roots = MixedLeggingsFactory.createPair(nativeItem, new ItemStack(Items.IRON_LEGGINGS));
            for (ItemStack stack : List.of(nativeItem, roots.primary(), roots.inverse())) {
                player.removeAllEffects();
                player.setItemSlot(EquipmentSlot.LEGS, stack);
                for (var effect : List.of(MobEffects.MINING_FATIGUE, MobEffects.POISON, MobEffects.SLOWNESS, MobEffects.WITHER)) {
                    player.addEffect(new MobEffectInstance(effect, 200));
                }
                player.setRemainingFireTicks(100);
                player.aiStep();
                boolean works = switch (field) {
                    case "FIRE_RESISTANCE" -> player.hasEffect(MobEffects.FIRE_RESISTANCE) && !player.isOnFire();
                    case "LUCK_III" -> player.hasEffect(MobEffects.LUCK) && player.getEffect(MobEffects.LUCK).getAmplifier() == 2;
                    case "MINING_FATIGUE_RESISTANCE" -> !player.hasEffect(MobEffects.MINING_FATIGUE);
                    case "POISON_RESISTANCE" -> !player.hasEffect(MobEffects.POISON);
                    case "SLOWNESS_RESISTANCE" -> !player.hasEffect(MobEffects.SLOWNESS);
                    default -> !player.hasEffect(MobEffects.WITHER);
                };
                boolean refused = stack != nativeItem && field.equals("LUCK_III") && variant("armor");
                require(works != refused, "Worn armor native effect mismatch " + field);
            }
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            for (var slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.FEET)) {
                player.removeAllEffects();
                player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
                player.setItemSlot(slot, roots.primary()); player.aiStep();
                require(!player.hasEffect(MobEffects.LUCK) && !player.hasEffect(MobEffects.FIRE_RESISTANCE) && player.hasEffect(MobEffects.POISON), "Non-LEGS stack activated armor callback");
                var ordinary = new java.util.ArrayList<Object>();
                require(MysticalAugmentsCompatibility.equipment(player, roots.primary(), ordinary) == ordinary, "Non-LEGS equipment authorized player augments");
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
            player.getInventory().setItem(0, roots.primary()); player.removeAllEffects(); player.aiStep();
            require(!player.hasEffect(MobEffects.LUCK) && !player.hasEffect(MobEffects.FIRE_RESISTANCE), "Carried stack activated armor callback");
            player.getInventory().clearContent();
        }
        REPORT.append("Six armor families: native and both worn orientations; carried/hand/wrong-slot gates passed\n");
    }

    private static void absorption(ServerPlayer player) throws Exception {
        long before = player.level().getGameTime();
        var data = player.level().getServer().getWorldData().overworldData();
        try {
            long time = 30_000;
            var nativeItem = NativeAugments.item("awakened_supremium_leggings", "ABSORPTION_III");
            var roots = MixedLeggingsFactory.createPair(nativeItem, new ItemStack(Items.IRON_LEGGINGS));
            List<String> order = null;
            for (var stack : List.of(nativeItem, roots.primary(), roots.inverse())) {
                data.setGameTime(time);
                player.setItemSlot(EquipmentSlot.LEGS, stack); player.removeAllEffects(); player.aiStep();
                player.setItemSlot(EquipmentSlot.HEAD, NativeAugments.item("awakened_supremium_helmet", "ABSORPTION_I"));
                var list = (List<?>) NativeAugments.invoke(NativeAugments.type("api.util.AugmentUtils"), "getArmorAugments", new Class<?>[]{net.minecraft.world.entity.player.Player.class}, player);
                var actual = list.stream().map(NativeAugments::id).toList();
                if (order == null) { order = actual; }
                else if (variant("equipment")) { require(actual.size() == order.size() - 1, "Refused equipment view still exposes mixed augments"); }
                else { require(order.equals(actual), "Native equipment iteration order changed"); }
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);

                require(player.getEffect(MobEffects.ABSORPTION).getAmplifier() == 2, "Absorption native rank differs");
                player.removeEffect(MobEffects.ABSORPTION); player.aiStep();
                require(!player.hasEffect(MobEffects.ABSORPTION), "Absorption immediately refilled");
                // Swapping native/mixed equipment must retain the player's original timer.
                player.setItemSlot(EquipmentSlot.LEGS, roots.inverse().copy()); player.aiStep();
                require(!player.hasEffect(MobEffects.ABSORPTION), "Equipping mixed leggings reset absorption timer");
                data.setGameTime(time + 9600); player.aiStep();
                require(!player.hasEffect(MobEffects.ABSORPTION), "Absorption exclusive boundary changed");
                data.setGameTime(time + 9601); player.aiStep();
                require(player.hasEffect(MobEffects.ABSORPTION), "Absorption did not refill after interval");
                time += 20_000;
            }
        } finally { data.setGameTime(before); player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY); player.removeAllEffects(); }
        REPORT.append("Absorption III native/both orientations: shared timing, exclusive 9600 boundary, swap retention and equipment order passed\n");
    }

    public static void health(ServerPlayer player, int scene) throws Exception {
        // -1: real worn armor callbacks; 0..3: native/primary/inverse/removal;
        // 4..7: original/mixed Boots coexistence, then removing each contribution.
        player.setHealth(player.getMaxHealth());
        if (scene == 9 || scene == 18 || scene == 21) {
            var root = player.getItemBySlot(EquipmentSlot.LEGS);
            root.remove(net.minecraft.core.component.DataComponents.UNBREAKABLE);
            root.setDamageValue(root.getMaxDamage() - 1);
            root.hurtAndBreak(2, player, EquipmentSlot.LEGS);
            require(root.isEmpty(), "Health cleanup break did not consume the root");
            return;
        }
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        player.removeAllEffects();
        player.setShiftKeyDown(scene == 14);
        if (scene == -1) {
            var root = games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory.createPair(
                    NativeAugments.item("awakened_supremium_leggings", "SLOWNESS_RESISTANCE", "FIRE_RESISTANCE"),
                    NativeAugments.item("inferium_leggings", "LUCK_I")).primary();
            player.setItemSlot(EquipmentSlot.LEGS, root);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 200));
            player.setRemainingFireTicks(100);
        }
        if (scene >= 0 && scene < 3) {
            player.setItemSlot(EquipmentSlot.LEGS, scene == 0
                    ? NativeAugments.item("awakened_supremium_leggings", "HEALTH_BOOST_III", "STEP_ASSIST")
                    : healthRoot(scene == 2));
        }
        if (scene == 4 || scene == 5) { player.setItemSlot(EquipmentSlot.LEGS, healthRoot(scene == 5)); }
        if (scene == 4) { player.setItemSlot(EquipmentSlot.FEET, NativeAugments.item("inferium_boots", "HEALTH_BOOST_I")); }
        if (scene == 5 || scene == 6) {
            var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
            Object roots = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null,
                    NativeAugments.item("inferium_boots", "HEALTH_BOOST_I"), new ItemStack(Items.IRON_BOOTS));
            player.setItemSlot(EquipmentSlot.FEET, (ItemStack) roots.getClass().getMethod("primary").invoke(roots));
        }
        if (scene == 17 || scene == 20) {
            player.setItemSlot(EquipmentSlot.LEGS, MixedLeggingsFactory.createPair(
                    NativeAugments.item("awakened_supremium_leggings", "HEALTH_BOOST_V", "STEP_ASSIST"), new ItemStack(Items.IRON_LEGGINGS)).primary());
            ItemStack feet = NativeAugments.item("inferium_boots", "HEALTH_BOOST_I");
            if (scene == 20) {
                var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
                Object roots = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, feet, new ItemStack(Items.IRON_BOOTS));
                feet = (ItemStack) roots.getClass().getMethod("primary").invoke(roots);
            }
            player.setItemSlot(EquipmentSlot.FEET, feet);
        }
        if (scene == 8) { player.setItemSlot(EquipmentSlot.LEGS, MixedLeggingsFactory.createPair(
                NativeAugments.item("awakened_supremium_leggings", "HEALTH_BOOST_V", "STEP_ASSIST"), new ItemStack(Items.IRON_LEGGINGS)).inverse()); }
        if (scene == 10) { player.setItemSlot(EquipmentSlot.LEGS, NativeAugments.item("awakened_supremium_leggings", "HEALTH_BOOST_III", "STEP_ASSIST")); }
        if (scene == 11 || scene == 14 || scene == 15) { player.setItemSlot(EquipmentSlot.LEGS, healthRoot(false)); }
        if (scene == 12) { player.setItemSlot(EquipmentSlot.LEGS, MixedLeggingsFactory.createPair(
                NativeAugments.item("inferium_leggings", "HEALTH_BOOST_I"), new ItemStack(Items.IRON_LEGGINGS)).primary()); }
    }

    private static ItemStack healthRoot(boolean inverse) {
        var pair = games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory.createPair(
                NativeAugments.item("awakened_supremium_leggings", "HEALTH_BOOST_III", "STEP_ASSIST"),
                NativeAugments.item("inferium_leggings", "HEALTH_BOOST_I"));
        return inverse ? pair.inverse() : pair.primary();
    }

    public static void verifyHealth(ServerPlayer player, int scene) {
        if (scene == -1) {
            require(!player.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS)
                    && player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)
                    && player.hasEffect(net.minecraft.world.effect.MobEffects.LUCK) == !variant("armor") && !player.isOnFire(),
                    "Actual worn mixed inventory tick did not dispatch native armor effects");
            REPORT.append("Actual worn LEGS inventory tick: Slowness removal, Fire Resistance/extinguishing and Luck passed\n");
            return;
        }
        require(Math.abs(player.getMaxHealth() - expectedHealth(scene)) < .001, "Native/mixed health/cache mismatch " + scene + ": " + player.getMaxHealth());
        require(Math.abs(player.getAttributeValue(Attributes.STEP_HEIGHT) - (expectedStep(scene))) < .001, "Native/mixed step/cache mismatch");
        require(player.isAlive() && player.getHealth() <= player.getMaxHealth(), "Health cleanup left an unsafe server health value");
    }

    public static int expectedHealth(int scene) {
        if (scene == 8 || scene == 17 || scene == 20) { return 40; }
        if (scene == 18 || scene == 21) { return 24; }
        if (scene == 10 || scene == 11 || scene == 14 || scene == 15) { return 32; }
        if (scene == 12) { return 24; }
        return scene == 0 || !(variant("equipment") || variant("health")) && (scene >= 1 && scene < 3 || scene == 4 || scene == 5) ? 32 : scene == 6 ? 24 : 20;
    }

    public static double expectedStep(int scene) {
        return scene >= 0 && (scene == 0 || !variant("equipment")) && (scene < 3 || scene == 4 || scene == 5 || scene == 8 || scene == 10 || scene == 11 || scene == 15 || scene == 17 || scene == 20) ? 1.6 : .6;
    }

    public static void write(String name, String value) throws Exception {
        Files.writeString(Path.of(System.getProperty("leggings.test.report"), name), value);
    }
}
