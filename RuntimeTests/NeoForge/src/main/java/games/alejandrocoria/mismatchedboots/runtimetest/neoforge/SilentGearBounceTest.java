package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.SilentGearTraitSpike.nativeClass;

/** Production Bounce only; native motion runs through the ordinary next-server-pre-tick scheduler. */
public final class SilentGearBounceTest {
    public static int wearCalls, requestedWear;
    private static final List<ServerPlayer> pending = new ArrayList<>();
    private static String evidence;
    private static Random nativeRandom;
    private static int bounceSounds;
    private static boolean listening;

    private SilentGearBounceTest() {}

    static boolean step(ServerPlayer owner, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Exception {
        SilentGearTraitSpike.initialize(api);
        var randomField = Class.forName("net.silentchaos512.lib.util.MathUtils").getDeclaredField("RANDOM");
        randomField.setAccessible(true); nativeRandom = (Random) randomField.get(null);
        if (evidence != null) {
            for (var actor : pending) {
                close(actor.getDeltaMovement().y, -0.25, "Next real pre-tick additive native push");
                clear(actor);
            }
            pending.clear(); report.append(evidence)
                    .append("Native scheduled rebound ran at the next real server pre-tick for native, both mixed orientations and duplicate Bounce; no manual scheduler drain.\n");
            return true;
        }
        listen();
        var details = new StringBuilder();
        ItemStack heat = api.boot("crimson_iron"), bounce = lined(api.boot("crimson_iron"), api, owner), vanilla = new ItemStack(Items.DIAMOND_BOOTS);
        int heatLevel = api.traitLevel(heat, "heat_resistant");
        require(heatLevel > 0 && api.traitLevel(bounce, "bounce") == 1, "Stock material/lining traits missing");
        details.append("Native crimson iron Heat Resistant=").append(heatLevel).append("; native slime lining Bounce=1; real part swap inputs immutable.\n");
        calibrate(owner, api);
        clear(owner); owner.setItemSlot(EquipmentSlot.FEET, bounce.copy());
        require(fall(owner, 12).isCanceled(), "Native Bounce fall control missing");
        retaliation(owner, bounce, 1);
        var peer = SilentGearRecognitionTest.trait(api.armor("helmet", "helmet_plates", "crimson_iron"), api, "bounce", 1);
        var band = nativeClass("util.GearHelper").getDeclaredMethod("getDamageFactor", ItemStack.class, int.class); band.setAccessible(true);
        for (var root : NeoForgeSilentGearTest.workbench(owner, bounce, vanilla)) {
            int crossing = -1;
            for (int damage = 0; damage < root.getMaxDamage() - 4; damage++) {
                root.setDamageValue(damage); int a = (int) band.invoke(null, root, root.getMaxDamage());
                root.setDamageValue(damage + 3); int b = (int) band.invoke(null, root, root.getMaxDamage());
                if (a != b) { crossing = damage; break; }
            }
            require(crossing >= 0, "No native damage-band boundary found"); root.setDamageValue(crossing);
            clear(owner); owner.setItemSlot(EquipmentSlot.HEAD, peer.copy()); owner.setItemSlot(EquipmentSlot.FEET, root);
            wearCalls = 0;
            observe(() -> require(fall(owner, 12).isCanceled(), "Production peer Bounce missing at damage band"));
            require(wearCalls == 1 && root.getDamageValue() == crossing + 3, "Production protection failed at a real damage band");
        }
        details.append("Production mixed Bounce + native helmet crosses a real damage band with one ordinary root payment in both orientations.\n");
        var samples = new ArrayList<ItemStack>();
        samples.addAll(NeoForgeSilentGearTest.workbench(owner, bounce, vanilla));
        samples.addAll(NeoForgeSilentGearTest.workbench(owner, bounce, bounce));
        for (var root : samples) {
            clear(owner); var before = root.copy(); root.setDamageValue(10); before.setDamageValue(13);
            owner.setItemSlot(EquipmentSlot.FEET, root); wearCalls = 0;
            observe(() -> require(fall(owner, 12).isCanceled(), "Production Bounce fall missing"));
            require(wearCalls == 1 && requestedWear == 3 && ItemStack.matches(before, root), "Bounce root debit/history differs: calls=" + wearCalls + ", requested=" + requestedWear + ", damage=" + root.getDamageValue());
            for (boolean crouch : List.of(false, true)) {
                owner.setShiftKeyDown(crouch); var snapshot = root.copy(); wearCalls = 0;
                require(!fall(owner, crouch ? 12 : 3).isCanceled() && wearCalls == 0 && ItemStack.matches(snapshot, root), "Short/crouch fall changed gear");
            }
            owner.setShiftKeyDown(false); owner.startFallFlying();
            require(!fall(owner, 12).isCanceled(), "Fall-flying triggered rebound"); owner.stopFallFlying();
            root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE); int wear = root.getDamageValue();
            require(fall(owner, 12).isCanceled() && root.getDamageValue() == wear, "Unbreakable exemption lost"); root.remove(DataComponents.UNBREAKABLE);
            owner.setGameMode(GameType.CREATIVE); require(fall(owner, 12).isCanceled() && root.getDamageValue() == wear, "Creative exemption lost"); owner.setGameMode(GameType.SURVIVAL);
            retaliation(owner, root, 1);
            clear(owner); owner.setItemSlot(EquipmentSlot.MAINHAND, root); require(!fall(owner, 12).isCanceled(), "Held Bounce counted as worn");
            var invalid = root.copy(); invalid.setCount(2); owner.setItemSlot(EquipmentSlot.FEET, invalid);
            require(!fall(owner, 12).isCanceled(), "Stacked mixed gained Bounce");
        }
        details.append("Production: SG/vanilla + duplicate SG, both paid orientations; one root fall debit; short/crouch/flying/held/stacked controls; creative/Unbreakable.\n");
        controls(owner, api, bounce, samples.getFirst(), details);
        var stronger = SilentGearRecognitionTest.trait(api.armor("helmet", "helmet_plates", "crimson_iron"), api, "bounce", 2);
        retaliation(owner, samples.getFirst(), 2, stronger);
        var moon = lined(api.boot("azure_silver"), api, owner);
        for (var root : NeoForgeSilentGearTest.workbench(owner, moon, vanilla)) {
            clear(owner); owner.setItemSlot(EquipmentSlot.FEET, root); root.setDamageValue(10); wearCalls = 0;
            var event = new LivingFallEvent(owner, 30, 1); observe(() -> NeoForge.EVENT_BUS.post(event));
            double distance = 30 * (1 + api.traitLevel(moon, "moonwalker") * -0.15F);
            close(event.getDistance(), distance, "Completed production Moonwalker with Bounce");
            require(event.isCanceled() && wearCalls == 1 && requestedWear == (int) (distance / 3) - 1, "Bounce did not use post-Moonwalker distance");
            // Initial native distance gate passes, but Moonwalker leaves a zero-cost Bounce.
            root.setDamageValue(10); var before = root.copy(); wearCalls = 0;
            var zeroBill = fall(owner, 3.5);
            require(zeroBill.isCanceled() && zeroBill.getDistance() < 3 && wearCalls == 0 && ItemStack.matches(before, root), "Moonwalker zero-bill boundary changed");
        }
        // Native armor Bounce still charges mixed FEET with no Bounce of their own.
        var root = NeoForgeSilentGearTest.workbench(owner, heat, vanilla).getFirst();
        clear(owner); owner.setItemSlot(EquipmentSlot.HEAD, SilentGearRecognitionTest.trait(api.armor("helmet", "helmet_plates", "crimson_iron"), api, "bounce", 1));
        owner.setItemSlot(EquipmentSlot.FEET, root); root.setDamageValue(10); wearCalls = 0;
        observe(() -> require(fall(owner, 12).isCanceled(), "Native peer Bounce was lost"));
        require(wearCalls == 1 && root.getDamageValue() == 13, "Native peer Bounce bypassed root debit ownership");
        clear(owner); root = samples.getFirst().copy(); root.setDamageValue(root.getMaxDamage() - 1); owner.setItemSlot(EquipmentSlot.FEET, root);
        observe(() -> require(fall(owner, 12).isCanceled(), "Near-break Bounce event missing"));
        require(root.isEmpty(), "Mixed inherited SG repairable broken survival");
        var nativeNearBreak = bounce.copy(); nativeNearBreak.setDamageValue(nativeNearBreak.getMaxDamage() - 1);
        clear(owner); owner.setItemSlot(EquipmentSlot.FEET, nativeNearBreak); require(fall(owner, 12).isCanceled() && !nativeNearBreak.isEmpty(), "Native broken-state control changed");
        details.append("Moonwalker precedes native Bounce cost; native armor peer charges non-Bounce mixed FEET safely; ordinary mixed destruction vs native repairable survival.\n");
        if (System.getProperty("mismatchedboots.runtimetest.silentGearEffectVariant", "").equals("defense-bounce-compatible")) {
            SilentGearDefensePeer.queryContribution = 3;
            try {
                clear(owner); owner.setItemSlot(EquipmentSlot.FEET, samples.getFirst().copy()); wearCalls = 0;
                require(fall(owner, 12).isCanceled() && wearCalls == 1, "Compatible higher fall contribution lost");
                retaliation(owner, samples.getFirst(), 3);
            } finally { SilentGearDefensePeer.queryContribution = 0; }
            details.append("Compatible independent optional-query level 3 preserved for fall and armor-only retaliation; no Curios installation claim.\n");
        }
        // Only these actors have negative initial Y, avoiding unrelated pending native rebounds.
        var moving = new ArrayList<ItemStack>(); moving.add(bounce.copy()); moving.addAll(samples.subList(0, 3));
        for (var stack : moving) {
            var actor = NeoForgeJustDireNegateFallTest.player(owner.level().getServer(), GameType.SURVIVAL);
            actor.setNoGravity(true); actor.setDeltaMovement(0, -1, 0); actor.setItemSlot(EquipmentSlot.FEET, stack.copy());
            var equipped = actor.getItemBySlot(EquipmentSlot.FEET); var expected = equipped.copy(); expected.setDamageValue(expected.getDamageValue() + 3); wearCalls = 0;
            require(fall(actor, 12).isCanceled(), "Deferred Bounce control missing");
            if (ModContent.getMixedBootsData(equipped).isPresent()) { require(wearCalls == 1 && requestedWear == 3 && ItemStack.matches(expected, equipped), "Negative-Y rebound charged more than one ordinary root bill"); }
            close(actor.getDeltaMovement().y, -1, "Bounce executed synchronously"); pending.add(actor);
        }
        clear(owner); evidence = details.toString(); return false;
    }

    static ItemStack lined(ItemStack stack, NeoForgeSilentGearTest.NativeApi api, ServerPlayer owner) throws Exception {
        var lining = api.part("lining", "slime"); var before = stack.copy(); var liningBefore = lining.copy();
        Object swap = api.swapClass.getField("INSTANCE").get(null); var input = CraftingInput.of(2, 1, List.of(stack, lining));
        require((boolean) api.matches.invoke(swap, input, owner.level()), "Stock slime lining recipe rejected");
        var result = (ItemStack) api.assemble.invoke(swap, input);
        require(!result.isEmpty() && ItemStack.matches(before, stack) && ItemStack.matches(liningBefore, lining), "Part swap changed inputs"); return result;
    }

    private static void controls(ServerPlayer owner, NeoForgeSilentGearTest.NativeApi api, ItemStack source, ItemStack root, StringBuilder details) throws Exception {
        clear(owner); owner.setItemSlot(EquipmentSlot.FEET, root); var before = root.copy(); wearCalls = 0; bounceSounds = 0;
        var canceled = new LivingFallEvent(owner, 12, 1); canceled.setCanceled(true); NeoForge.EVENT_BUS.post(canceled);
        require(wearCalls == 0 && bounceSounds == 0 && ItemStack.matches(before, root), "Pre-canceled fall gained work");
        owner.setDeltaMovement(0, .25, 0); require(fall(owner, 12).isCanceled(), "Nonnegative native Bounce control missing");
        close(owner.getDeltaMovement().y, .25, "Positive initial Y gained a rebound");
        for (String invalid : List.of("data", "slot")) {
            var bad = root.copy(); if (invalid.equals("data")) { bad.remove(ModContent.mixedBootsDataType().orElseThrow()); } else { bad.remove(DataComponents.EQUIPPABLE); }
            clear(owner); owner.setItemSlot(EquipmentSlot.FEET, bad); before = bad.copy(); wearCalls = 0;
            require(!fall(owner, 12).isCanceled() && wearCalls == 0 && ItemStack.matches(before, bad), "Invalid mixed Bounce admitted");
        }
        var mob = new Zombie(EntityType.ZOMBIE, owner.level()); mob.setItemSlot(EquipmentSlot.FEET, root.copy());
        var mobBefore = mob.getItemBySlot(EquipmentSlot.FEET).copy(); var mobFall = new LivingFallEvent(mob, 12, 1); wearCalls = 0; NeoForge.EVENT_BUS.post(mobFall);
        require(!mobFall.isCanceled() && wearCalls == 0 && ItemStack.matches(mobBefore, mob.getItemBySlot(EquipmentSlot.FEET)), "Non-player gained Bounce fall");
        var spoof = new ItemStack(Items.DIAMOND_BOOTS); spoof.set(api.properties, source.get(api.properties));
        for (var plain : NeoForgeSilentGearTest.workbench(owner, spoof, new ItemStack(Items.IRON_BOOTS))) {
            clear(owner); owner.setItemSlot(EquipmentSlot.FEET, plain); before = plain.copy(); wearCalls = 0;
            require(!fall(owner, 12).isCanceled() && ItemStack.matches(before, plain) && wearCalls == 0, "Foreign native components granted Bounce without an SG source");
        }
        var instance = SilentGearTraitSpike.view(source).get("silentgear:malleable");
        Object trait = SilentGearTraitSpike.traitOf(instance);
        var effect = ((List<?>) trait.getClass().getMethod("getEffects").invoke(trait)).stream().filter(e -> e.getClass().getSimpleName().equals("DurabilityTraitEffect")).findFirst().orElseThrow();
        var chance = effect.getClass().getDeclaredField("activationChance"); chance.setAccessible(true);
        float threshold = chance.getFloat(effect) * api.traitLevel(source, "malleable");
        for (boolean active : List.of(false, true)) {
            long seed = 0; while (seed < 100_000 && (new Random(seed).nextDouble() < threshold) != active) { seed++; }
            require(seed < 100_000, "No seeded Malleable control");
            var sample = root.copy(); sample.setDamageValue(10); clear(owner); owner.setItemSlot(EquipmentSlot.FEET, sample); nativeRandom.setSeed(seed); wearCalls = 0;
            var event = new LivingFallEvent(owner, 12, 1); observe(() -> NeoForge.EVENT_BUS.post(event));
            var reference = beforeWear(root, owner, seed, false);
            require(event.isCanceled() && wearCalls == 1 && requestedWear == 3 && sample.getDamageValue() == 10 + (active ? 2 : 3)
                    && ItemStack.matches(sample, reference), "Bounce bypassed existing seeded Malleable/ordinary wear");
        }
        var enchanted = root.copy(); enchanted.setDamageValue(10);
        enchanted.enchant(owner.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 3);
        var reference = beforeWear(enchanted, owner, 0, true);
        clear(owner); owner.setItemSlot(EquipmentSlot.FEET, enchanted); owner.level().getRandom().setSeed(184); nativeRandom.setSeed(0); wearCalls = 0;
        var event = new LivingFallEvent(owner, 12, 1); observe(() -> NeoForge.EVENT_BUS.post(event));
        require(event.isCanceled() && wearCalls == 1 && ItemStack.matches(reference, enchanted), "Bounce bypassed ordinary Unbreaking pipeline");
        details.append("Pre-canceled, positive initial Y, malformed/held/non-player negatives; Moonwalker bill boundaries; seeded Malleable activation/nonactivation and Unbreaking match one ordinary root hurtAndBreak\n");
    }

    private static ItemStack beforeWear(ItemStack source, ServerPlayer owner, long seed, boolean unbreaking) {
        var reference = source.copy(); reference.setDamageValue(10); clear(owner); owner.setItemSlot(EquipmentSlot.FEET, reference);
        if (unbreaking) { owner.level().getRandom().setSeed(184); } nativeRandom.setSeed(seed);
        observe(() -> reference.hurtAndBreak(3, owner, EquipmentSlot.FEET)); return reference;
    }

    static void refusal(ServerPlayer owner, NeoForgeSilentGearTest.NativeApi api, StringBuilder report, String variant) throws Exception {
        SilentGearTraitSpike.initialize(api); listen();
        var field = Class.forName("net.silentchaos512.lib.util.MathUtils").getDeclaredField("RANDOM"); field.setAccessible(true); nativeRandom = (Random) field.get(null);
        var source = lined(api.boot("crimson_iron"), api, owner);
        boolean fall = !List.of("defense-fall", "defense-bounce-query").contains(variant), retaliate = !variant.equals("defense-retaliation");
        for (var root : NeoForgeSilentGearTest.workbench(owner, source, new ItemStack(Items.DIAMOND_BOOTS))) {
            clear(owner); owner.setItemSlot(EquipmentSlot.FEET, root); var before = root.copy(); wearCalls = 0;
            int queued = queued(); if (!fall) { owner.setDeltaMovement(0, -1, 0); }
            require(fall(owner, 12).isCanceled() == fall && wearCalls == (fall ? 1 : 0), "Independent mixed fall recognition/refusal differs");
            if (!fall) { require(ItemStack.matches(before, root) && queued() == queued && owner.getDeltaMovement().y == -1, "Refused mixed Bounce changed gear or queued motion"); }
            retaliation(owner, root, retaliate ? 1 : 0);
        }
        clear(owner); owner.setItemSlot(EquipmentSlot.FEET, source.copy()); require(fall(owner, 12).isCanceled(), "Refusal disabled native Bounce");
        retaliation(owner, source, 1); clear(owner);
        report.append("C refusal ").append(variant).append(": mixed fall=").append(fall).append(" retaliation=").append(retaliate).append("; native controls survive, no retaliation bill\n");
    }

    private static int queued() throws ReflectiveOperationException {
        var scheduler = Class.forName("net.silentchaos512.lib.event.ServerTicks");
        var instance = scheduler.getDeclaredField("INSTANCE"); instance.setAccessible(true);
        var field = scheduler.getDeclaredField("scheduledActions"); field.setAccessible(true);
        return ((java.util.Queue<?>) field.get(instance.get(null))).size();
    }

    private static void listen() {
        if (listening) { return; } listening = true;
        NeoForge.EVENT_BUS.addListener((PlayLevelSoundEvent.AtPosition event) -> {
            if (event.getSound() != null && event.getSound().value() == SoundEvents.SLIME_BLOCK_FALL) {
                require(event.getSource() == SoundSource.PLAYERS && event.getOriginalVolume() == 1 && event.getOriginalPitch() == 1, "Native Bounce sound parameters differ");
                bounceSounds++;
            }
        });
    }

    private static LivingFallEvent fall(ServerPlayer player, double distance) {
        // Crimson Iron also has Malleable: make the existing probabilistic wear hook deterministic.
        nativeRandom.setSeed(0);
        bounceSounds = 0;
        var event = new LivingFallEvent(player, distance, 1); NeoForge.EVENT_BUS.post(event);
        require(bounceSounds == (event.isCanceled() ? 1 : 0), "Registered native Bounce sound count differs"); return event;
    }
    private static void retaliation(ServerPlayer owner, ItemStack stack, int expected) {
        retaliation(owner, stack, expected, ItemStack.EMPTY);
    }
    private static void retaliation(ServerPlayer owner, ItemStack stack, int expected, ItemStack helmet) {
        var server = owner.level().getServer(); server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        var victim = NeoForgeJustDireNegateFallTest.player(server, GameType.SURVIVAL);
        var root = stack.copy(); root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE); var before = root.copy();
        victim.setItemSlot(EquipmentSlot.FEET, root); victim.setItemSlot(EquipmentSlot.HEAD, helmet.copy()); wearCalls = 0;
        var attacker = new Zombie(EntityType.ZOMBIE, owner.level()); attacker.setYRot(90); attacker.setDeltaMovement(Vec3.ZERO);
        require(victim.hurtServer(victim.level(), victim.damageSources().mobAttack(attacker), 2), "Real incoming hit rejected");
        close(attacker.getDeltaMovement().x, 2 * expected, "Native post-damage strength/max once");
        require(Math.abs(attacker.getDeltaMovement().z) < 0.001 && wearCalls == 0 && ItemStack.matches(before, root), "Retaliation changed direction/root or charged fall wear");
        attacker.setDeltaMovement(Vec3.ZERO); victim.invulnerableTime = 0; victim.setHealth(victim.getMaxHealth());
        var arrow = new Arrow(EntityType.ARROW, owner.level());
        var indirect = new DamageSource(victim.damageSources().generic().typeHolder(), arrow, attacker);
        require(victim.hurtServer(victim.level(), indirect, 2) && attacker.getDeltaMovement().equals(Vec3.ZERO), "Indirect living owner incorrectly gained Bounce retaliation");
        victim.invulnerableTime = 0; victim.setHealth(victim.getMaxHealth());
        require(victim.hurtServer(victim.level(), victim.damageSources().generic(), 2) && attacker.getDeltaMovement().equals(Vec3.ZERO)
                && wearCalls == 0 && ItemStack.matches(before, root), "Environmental retaliation/payment negative failed");
        var mobVictim = new Zombie(EntityType.ZOMBIE, owner.level()); mobVictim.setItemSlot(EquipmentSlot.FEET, root.copy());
        require(mobVictim.hurtServer(owner.level(), mobVictim.damageSources().mobAttack(attacker), 2)
                && attacker.getDeltaMovement().equals(Vec3.ZERO) && wearCalls == 0, "Non-player victim gained retaliation");
        clear(victim);
    }
    private static void calibrate(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api) throws Exception {
        var witness = api.boot("iron"); boolean caught = false; SilentGearWearerFixture.active = true;
        try { nativeClass("util.GearData").getMethod("recalculateGearData", ItemStack.class, net.minecraft.world.entity.player.Player.class).invoke(null, witness, player); }
        catch (InvocationTargetException expected) { caught = expected.getCause() instanceof AssertionError; }
        finally { SilentGearWearerFixture.active = false; }
        require(caught, "Recalculation witness did not observe native writer");
    }
    private static void observe(Runnable action) {
        SilentGearWearerFixture.active = true;
        try { action.run(); } finally { SilentGearWearerFixture.active = false; }
    }
    private static void clear(ServerPlayer player) {
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
        player.removeAllEffects(); player.setShiftKeyDown(false); player.stopFallFlying(); player.setDeltaMovement(Vec3.ZERO); player.setGameMode(GameType.SURVIVAL);
    }
    private static void close(double actual, double expected, String message) { require(Math.abs(actual - expected) < 0.00001, message + ": " + actual + " vs " + expected); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
