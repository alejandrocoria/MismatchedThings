package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearDefenseApi;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.nativeClass;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.traitOf;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.view;

/** Production A: no research hooks, guarded direct incoming controls and native-peer root wear. */
final class SilentGearDefenseTest {
    private static int sounds;

    private SilentGearDefenseTest() {
    }

    static void run(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report, String variant) throws Throwable {
        SilentGearTraitSpike.initialize(api);
        clear(player);
        boolean safe = !variant.equals("defense-fall");
        boolean negate = !variant.equals("defense-negate");
        boolean incoming = !variant.equals("defense-incoming");
        boolean retaliation = !variant.equals(
                "defense-retaliation");
        var proof = new StringBuilder();
        for (String[] pair : List.of(new String[]{"event.GearEvents", "mismatchedboots$sgSafeFallReady"},
                                     new String[]{"event.GearEvents", "mismatchedboots$sgIncomingReady"},
                                     new String[]{"event.GearEvents", "mismatchedboots$sgBounceFallReady"},
                                     new String[]{"event.GearEvents", "mismatchedboots$sgRetaliationReady"},
                                     new String[]{"gear.trait.effect.NegateDamageTraitEffect", "mismatchedboots$sgNegateReady"})) {
            var method = nativeClass(pair[0]).getDeclaredMethod(pair[1]);
            method.setAccessible(true);
            proof.append(pair[1]).append('=').append(method.invoke(null)).append(' ');
        }
        require(ready("event.GearEvents", "mismatchedboots$sgSafeFallReady") == safe, proof.toString());
        require(ready("event.GearEvents", "mismatchedboots$sgBounceFallReady") == !variant.equals("defense-bounce-query"), proof.toString());
        require(ready("event.GearEvents", "mismatchedboots$sgIncomingReady") == incoming, proof.toString());
        require(ready("event.GearEvents", "mismatchedboots$sgRetaliationReady") == retaliation, proof.toString());
        require(ready("gear.trait.effect.NegateDamageTraitEffect", "mismatchedboots$sgNegateReady") == negate, proof.toString());
        require(ready("event.GearEvents", "mismatchedboots$sgMoonReady"), "Independent Moonwalker proof regressed");
        var binding = new SilentGearDefenseApi();
        var heat = api.boot("crimson_iron");
        var instance = view(heat).get("silentgear:heat_resistant");
        Object effect = binding.admittedEffect(instance);
        var before = heat.copy();
        boolean refused = false;
        try {
            float value = binding.incoming(instance, effect, heat, player, player.damageSources().lava(), 20, 20);
            require(Math.abs(value - 16.8F) < 0.0001, "Native Heat IV direct math differs");
        } catch (IllegalStateException expected) {
            refused = true;
        }
        require(refused == (!negate || !incoming) && ItemStack.matches(before, heat), "Independent guarded direct call/read-only behavior differs");
        definitions(binding, instance, effect);
        calibrate(player, api);
        NeoForge.EVENT_BUS.addListener((PlayLevelSoundEvent.AtPosition event) -> {
            if (event.getSound() != null && event.getSound().value() == SoundEvents.SLIME_BLOCK_FALL) {
                sounds++;
            }
        });
        var randomField = Class.forName("net.silentchaos512.lib.util.MathUtils").getDeclaredField("RANDOM");
        randomField.setAccessible(true);
        var random = (Random) randomField.get(null);
        var peer = SilentGearRecognitionTest.trait(api.armor("helmet", "helmet_plates", "crimson_iron"), api, "bounce", 1);
        var roots = new ArrayList<ItemStack>();
        roots.addAll(NeoForgeSilentGearTest.workbench(player, heat, new ItemStack(Items.DIAMOND_BOOTS)));
        roots.addAll(NeoForgeSilentGearTest.workbench(player, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)));
        var factor = nativeClass("util.GearHelper").getDeclaredMethod("getDamageFactor", ItemStack.class, int.class);
        factor.setAccessible(true);
        for (var root : roots) {
            int crossing = -1;
            for (int damage = 0; damage < root.getMaxDamage() - 4; damage++) {
                root.setDamageValue(damage);
                int a = (int) factor.invoke(null, root, root.getMaxDamage());
                root.setDamageValue(damage + 3);
                if (a != (int) factor.invoke(null, root, root.getMaxDamage())) {
                    crossing = damage;
                    break;
                }
            }
            require(crossing >= 0, "No actual native damage band available");
            root.setDamageValue(crossing);
            clear(player);
            player.setItemSlot(EquipmentSlot.HEAD, peer.copy());
            player.setItemSlot(EquipmentSlot.FEET, root);
            var expected = root.copy();
            expected.setDamageValue(crossing + (safe ? 3 : 0));
            sounds = 0;
            random.setSeed(0);
            var event = new LivingFallEvent(player, 12, 1);
            observe(() -> NeoForge.EVENT_BUS.post(event));
            require(event.isCanceled() == safe && sounds == (safe ? 1 : 0) && ItemStack.matches(root, expected),
                    "Native peer safe root wear/refusal differs");
            require(player.getDeltaMovement().lengthSqr() == 0, "Nonnegative native rebound created motion");
            clear(player);
            player.setItemSlot(EquipmentSlot.FEET, root);
            var noPeer = new LivingFallEvent(player, 12, 1);
            NeoForge.EVENT_BUS.post(noPeer);
            require(!noPeer.isCanceled(), "A enabled mixed-source Bounce");
            clear(player);
            player.setItemSlot(EquipmentSlot.HEAD, peer.copy());
            var protectedRoot = root.copy();
            protectedRoot.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            player.setItemSlot(EquipmentSlot.FEET, protectedRoot);
            var protectedBefore = protectedRoot.copy();
            var exempt = new LivingFallEvent(player, 12, 1);
            NeoForge.EVENT_BUS.post(exempt);
            require(exempt.isCanceled() == safe && ItemStack.matches(protectedRoot, protectedBefore), "Native peer bypassed ordinary Unbreakable");
            protectedRoot.remove(DataComponents.UNBREAKABLE);
            player.setGameMode(GameType.CREATIVE);
            protectedBefore = protectedRoot.copy();
            var creative = new LivingFallEvent(player, 12, 1);
            NeoForge.EVENT_BUS.post(creative);
            player.setGameMode(GameType.SURVIVAL);
            require(creative.isCanceled() == safe && ItemStack.matches(protectedRoot, protectedBefore), "Native peer bypassed creative exemption");
            var nearBreak = root.copy();
            nearBreak.setDamageValue(nearBreak.getMaxDamage() - 1);
            player.setItemSlot(EquipmentSlot.FEET, nearBreak);
            random.setSeed(0);
            var breaking = new LivingFallEvent(player, 12, 1);
            observe(() -> NeoForge.EVENT_BUS.post(breaking));
            require(breaking.isCanceled() == safe && nearBreak.isEmpty() == safe, "Native peer changed ordinary mixed break/refusal");
        }

        var sourceBounce = SilentGearBounceTest.lined(heat, api, player);
        require(api.traitLevel(sourceBounce, "bounce") == 1, "Real native slime lining control missing");
        for (var root : NeoForgeSilentGearTest.workbench(player, sourceBounce, new ItemStack(Items.DIAMOND_BOOTS))) {
            clear(player);
            player.setItemSlot(EquipmentSlot.FEET, root);
            var fall = new LivingFallEvent(player, 12, 1);
            NeoForge.EVENT_BUS.post(fall);
            var fire = new LivingIncomingDamageEvent(player, new DamageContainer(player.damageSources().lava(), 20));
            NeoForge.EVENT_BUS.post(fire);
            require(fall.isCanceled() == (safe && !variant.equals("defense-bounce-query")) && Math.abs(fire.getAmount() - (incoming && negate
                                                                                                                           ? 16.8F
                                                                                                                           : 20)) < .0001,
                    "Production incoming/refused fall recognition differs");
        }
        for (String invalid : List.of("count", "data", "slot")) {
            var root = roots.getLast().copy();
            if (invalid.equals("count")) {
                root.setCount(2);
            } else if (invalid.equals("data")) {
                root.remove(ModContent.mixedBootsDataType().orElseThrow());
            } else {
                root.remove(DataComponents.EQUIPPABLE);
            }
            clear(player);
            player.setItemSlot(EquipmentSlot.HEAD, peer.copy());
            player.setItemSlot(EquipmentSlot.FEET, root);
            var snapshot = root.copy();
            sounds = 0;
            var denied = new LivingFallEvent(player, 12, 1);
            observe(() -> NeoForge.EVENT_BUS.post(denied));
            require(!denied.isCanceled() && sounds == 0 && ItemStack.matches(root, snapshot), "Malformed mixed target reached native wear");
        }

        var nativeBoot = SilentGearRecognitionTest.trait(api.boot("crimson_iron"), api, "bounce", 1);
        clear(player);
        player.setItemSlot(EquipmentSlot.FEET, nativeBoot);
        var nativeEvent = new LivingFallEvent(player, 12, 1);
        NeoForge.EVENT_BUS.post(nativeEvent);
        require(nativeEvent.isCanceled(), "Native non-mixed Bounce was disabled");
        if (variant.equals("defense-compatible")) {
            SilentGearDefensePeer.queryContribution = 2;
            try {
                clear(player);
                var root = roots.getLast();
                root.setDamageValue(10);
                player.setItemSlot(EquipmentSlot.FEET, root);
                random.setSeed(0);
                var contributed = new LivingFallEvent(player, 12, 1);
                observe(() -> NeoForge.EVENT_BUS.post(contributed));
                require(contributed.isCanceled() && root.getDamageValue() == 13, "Compatible optional-query contribution was lost");
            } finally {
                SilentGearDefensePeer.queryContribution = 0;
            }
        }
        if (!safe) {
            clear(player);
            player.setItemSlot(EquipmentSlot.HEAD, peer);
            player.setItemSlot(EquipmentSlot.FEET, roots.getFirst());
            player.setDeltaMovement(0, -1, 0);
            var scheduler = Class.forName("net.silentchaos512.lib.event.ServerTicks");
            var singleton = scheduler.getDeclaredField("INSTANCE");
            singleton.setAccessible(true);
            var queue = scheduler.getDeclaredField("scheduledActions");
            queue.setAccessible(true);
            var pending = (java.util.Queue<?>) queue.get(singleton.get(null));
            int size = pending.size();
            var snapshot = roots.getFirst().copy();
            sounds = 0;
            var denied = new LivingFallEvent(player, 12, 1);
            NeoForge.EVENT_BUS.post(denied);
            require(!denied.isCanceled() && sounds == 0 && pending.size() == size && ItemStack.matches(snapshot, roots.getFirst()),
                    "Refused fall published side effects");
        }
        clear(player);
        report.append("A ")
              .append(variant)
              .append(':')
              .append(proof)
              .append("\nGuarded native Heat IV/read-only direct call; live definition admission; calibrated writer witness; four paid SG/non-SG root orientations cross real native damage bands through production wear; independent mixed incoming/fall permissions and native positives; Moonwalker retained.\n");
    }

    private static boolean ready(String owner, String name) throws Throwable {
        try {
            SilentGearDefenseApi.require(owner, name);
            return true;
        } catch (IllegalStateException refused) {
            return false;
        }
    }

    private static void definitions(SilentGearDefenseApi binding, Object instance, Object effect) throws Throwable {
        var field = effect.getClass().getDeclaredField("negatedDamageScale");
        field.setAccessible(true);
        float original = field.getFloat(effect);
        for (float invalid : new float[]{-1, Float.NaN, Float.POSITIVE_INFINITY}) {
            field.setFloat(effect, invalid);
            boolean refused = false;
            try {
                binding.admittedEffect(instance);
            } catch (IllegalStateException expected) {
                refused = true;
            } finally {
                field.setFloat(effect, original);
            }
            require(refused, "Unsupported native scale admitted");
        }

        var definition = traitOf(instance);
        var effects = definition.getClass().getDeclaredField("effects");
        effects.setAccessible(true);
        Object saved = effects.get(definition);
        for (var replacement : List.of(List.of(effect, effect), List.of(effect, new Object()), List.of(effect))) {
            effects.set(definition, replacement);
            boolean refused = false;
            try {
                binding.admittedEffect(instance);
            } catch (IllegalStateException expected) {
                refused = true;
            } finally {
                effects.set(definition, saved);
            }
            require(refused, "Unsupported companion/count admitted");
        }
        require(binding.admittedEffect(instance) == effect, "Definition was not reread after restoration");
        var tag = effect.getClass().getDeclaredField("damageType");
        tag.setAccessible(true);
        Object originalTag = tag.get(effect);
        try {
            tag.set(effect, TagKey.create(Registries.ITEM, Identifier.parse("minecraft:logs")));
            boolean refused = false;
            try {
                binding.admittedEffect(instance);
            } catch (IllegalStateException expected) {
                refused = true;
            }
            require(refused, "Foreign registry damage tag admitted");
        } finally {
            tag.set(effect, originalTag);
        }
    }

    private static void calibrate(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api) throws Exception {
        var witness = api.boot("iron");
        boolean intercepted = false;
        SilentGearWearerFixture.active = true;
        try {
            nativeClass("util.GearData").getMethod("recalculateGearData", ItemStack.class, net.minecraft.world.entity.player.Player.class)
                                        .invoke(null, witness, player);
        } catch (InvocationTargetException failure) {
            intercepted = failure.getCause() instanceof AssertionError;
        } finally {
            SilentGearWearerFixture.active = false;
        }
        require(intercepted, "Writer witness did not intercept a genuine native recalculation");
    }

    private static void observe(Runnable action) {
        SilentGearWearerFixture.active = true;
        try {
            action.run();
        } finally {
            SilentGearWearerFixture.active = false;
        }
    }

    private static void clear(ServerPlayer player) {
        for (var slot : EquipmentSlot.values()) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        player.setDeltaMovement(0, 0, 0);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
