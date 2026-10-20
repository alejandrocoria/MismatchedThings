package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
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

/** Opt-in executable candidate, deliberately not production admission, failure recovery or client acceptance. */
public final class SilentGearDefenseSpike {
    public static boolean candidate;
    public static int wearCalls, requestedWear;
    private static final List<ServerPlayer> pending = new ArrayList<>();
    private static String evidence;
    private static Random nativeRandom;
    private static int bounceSounds;

    private SilentGearDefenseSpike() {}

    public static boolean eligible(ItemStack stack) {
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        return stack.getCount() == 1 && stack.getItem() instanceof MixedBootsItem
                && ModContent.getMixedBootsData(stack).isPresent() && equippable != null && equippable.slot() == EquipmentSlot.FEET;
    }

    public static int bounce(ItemStack stack) {
        return !candidate || !eligible(stack) ? 0 : SilentGearCompatibility.effectiveTraits(stack).stream()
                .filter(trait -> trait.id().toString().equals("silentgear:bounce")).mapToInt(SilentGearCompatibility.Trait::level).max().orElse(0);
    }

    public static float incoming(ItemStack stack, float current, LivingIncomingDamageEvent event) {
        try {
            // Only the selected concrete effect runs; native Fireproof and unrelated trait callbacks do not.
            var before = stack.copy(); var disposable = stack.copy();
            for (var trait : SilentGearCompatibility.effectiveTraits(stack)) {
                if (!List.of("silentgear:heat_resistant", "silentgear:bounce").contains(trait.id().toString())) { continue; }
                Object definition = traitOf(trait.nativeInstance());
                var effects = ((List<?>) definition.getClass().getMethod("getEffects").invoke(definition)).stream()
                        .filter(effect -> effect.getClass() == nativeClass("gear.trait.effect.NegateDamageTraitEffect")).toList();
                require(effects.size() == 1, "Selected native negate effect changed");
                Object effect = effects.getFirst();
                current = (float) effect.getClass().getMethod("onEntityIncomingDamage", ItemStack.class, int.class,
                                LivingEntity.class, DamageSource.class, float.class, float.class)
                        .invoke(effect, disposable, trait.level(), event.getEntity(), event.getSource(), current, event.getAmount());
            }
            require(ItemStack.matches(before, stack) && ItemStack.matches(before, disposable), "Read-only negate effect mutated gear");
            return current;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    static boolean step(ServerPlayer owner, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Exception {
        SilentGearTraitSpike.initialize(api);
        var randomField = Class.forName("net.silentchaos512.lib.util.MathUtils").getDeclaredField("RANDOM");
        randomField.setAccessible(true); nativeRandom = (Random) randomField.get(null);
        if (evidence != null) {
            for (var actor : pending) {
                close(actor.getDeltaMovement().y, -0.25, "Next real pre-tick additive native push");
                clear(actor);
            }
            candidate = false; pending.clear(); report.append(evidence)
                    .append("Native scheduled rebound ran at the next real server pre-tick for native, both mixed orientations and duplicate Bounce; no manual scheduler drain.\n");
            return true;
        }
        NeoForge.EVENT_BUS.addListener((PlayLevelSoundEvent.AtPosition event) -> {
            if (event.getSound() != null && event.getSound().value() == SoundEvents.SLIME_BLOCK_FALL) {
                require(event.getSource() == SoundSource.PLAYERS && event.getOriginalVolume() == 1 && event.getOriginalPitch() == 1, "Native Bounce sound parameters differ");
                bounceSounds++;
            }
        });
        var details = new StringBuilder();
        ItemStack heat = api.boot("crimson_iron"), bounce = lined(api.boot("crimson_iron"), api, owner), vanilla = new ItemStack(Items.DIAMOND_BOOTS);
        int heatLevel = api.traitLevel(heat, "heat_resistant");
        require(heatLevel > 0 && api.traitLevel(bounce, "bounce") == 1, "Stock material/lining traits missing");
        details.append("Native crimson iron Heat Resistant=").append(heatLevel).append("; native slime lining Bounce=1; real part swap inputs immutable.\n");
        calibrate(owner, api);
        candidate = false;
        damage(owner, heat, owner.damageSources().lava(), 20 * (1 - 0.04F * heatLevel));
        damage(owner, bounce, owner.damageSources().flyIntoWall(), 16);
        for (var root : NeoForgeSilentGearTest.workbench(owner, bounce, vanilla)) {
            damage(owner, root, owner.damageSources().lava(), 20);
            damage(owner, root, owner.damageSources().flyIntoWall(), 20);
            clear(owner); owner.setItemSlot(EquipmentSlot.FEET, root);
            require(!fall(owner, 12).isCanceled(), "Current production unexpectedly has Bounce fall support");
            retaliation(owner, root, false);
        }
        details.append("Candidate OFF: native damage/fall/retaliation controls positive; current mixed heat/collision/rebound/retaliation absent in both orientations.\n");
        clear(owner); owner.setItemSlot(EquipmentSlot.FEET, bounce.copy());
        require(fall(owner, 12).isCanceled(), "Native Bounce fall control missing");
        retaliation(owner, bounce, true);
        var peer = SilentGearRecognitionTest.trait(api.armor("helmet", "helmet_plates", "crimson_iron"), api, "bounce", 1);
        var band = nativeClass("util.GearHelper").getDeclaredMethod("getDamageFactor", ItemStack.class, int.class); band.setAccessible(true);
        for (var root : NeoForgeSilentGearTest.workbench(owner, heat, vanilla)) {
            int crossing = -1;
            for (int damage = 0; damage < root.getMaxDamage() - 4; damage++) {
                root.setDamageValue(damage); int a = (int) band.invoke(null, root, root.getMaxDamage());
                root.setDamageValue(damage + 3); int b = (int) band.invoke(null, root, root.getMaxDamage());
                if (a != b) { crossing = damage; break; }
            }
            require(crossing >= 0, "No native damage-band boundary found"); root.setDamageValue(crossing);
            clear(owner); owner.setItemSlot(EquipmentSlot.HEAD, peer.copy()); owner.setItemSlot(EquipmentSlot.FEET, root);
            boolean intercepted = false;
            try { observe(() -> fall(owner, 12)); }
            catch (AssertionError expected) { intercepted = expected.getMessage().startsWith("Wearer read-only scope entered native recalculation:"); }
            require(intercepted, "Current native armor-peer Bounce did not reach the expected unsafe root recalculation boundary");
            // Stop the baseline at writer entry; do not intentionally let native recalculation corrupt a root.
            root.setDamageValue(crossing); candidate = true; wearCalls = 0;
            observe(() -> require(fall(owner, 12).isCanceled(), "Candidate peer Bounce missing at damage band"));
            require(wearCalls == 1 && root.getDamageValue() == crossing + 3, "Candidate did not bypass unsafe native recalculation at a real damage band");
            candidate = false;
        }
        details.append("Current production + native Bounce helmet reaches native recalculation on mixed FEET at a damage-band crossing (intercepted at writer entry); candidate bypasses it in both orientations.\n");
        candidate = true;
        var samples = new ArrayList<ItemStack>();
        samples.addAll(NeoForgeSilentGearTest.workbench(owner, bounce, vanilla));
        samples.addAll(NeoForgeSilentGearTest.workbench(owner, bounce, bounce));
        for (var root : samples) {
            require(root.has(DataComponents.DAMAGE_RESISTANT) == (samples.indexOf(root) >= 2), "Existing BOTH-source fireproof policy changed");
            damage(owner, root, owner.damageSources().lava(), 20 * (1 - 0.04F * heatLevel));
            damage(owner, root, owner.damageSources().flyIntoWall(), 16);
            damage(owner, root, owner.damageSources().fall(), 20);
            damage(owner, root, owner.damageSources().generic(), 20);
            clear(owner); owner.setItemSlot(EquipmentSlot.HEAD, api.armor("helmet", "helmet_plates", "crimson_iron"));
            owner.setItemSlot(EquipmentSlot.FEET, root); incomingEvent(owner, owner.damageSources().lava(), 20 * (1 - 0.08F * heatLevel));
            var commonBase = new LivingIncomingDamageEvent(owner, new DamageContainer(owner.damageSources().lava(), 20)); commonBase.setAmount(8);
            observe(() -> NeoForge.EVENT_BUS.post(commonBase));
            close(commonBase.getAmount(), 8 * (1 - 0.08F * heatLevel), "Native current-base anchor, not original amount or multiplicative remainder");
            clear(owner); var before = root.copy(); root.setDamageValue(10); before.setDamageValue(13);
            owner.setItemSlot(EquipmentSlot.FEET, root); wearCalls = 0;
            observe(() -> require(fall(owner, 12).isCanceled(), "Candidate Bounce fall missing"));
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
            retaliation(owner, root, true);
            clear(owner); owner.setItemSlot(EquipmentSlot.MAINHAND, root); require(!fall(owner, 12).isCanceled(), "Held Bounce counted as worn");
            var invalid = root.copy(); invalid.setCount(2); owner.setItemSlot(EquipmentSlot.FEET, invalid);
            require(!fall(owner, 12).isCanceled(), "Stacked mixed gained Bounce");
        }
        details.append("Candidate ON: SG/vanilla + duplicate SG, both paid orientations; native negate math, fire/wall tags vs fall/generic negatives; additive native helmet; one root fall debit; short/crouch/flying/held/stacked controls; creative/Unbreakable.\n");
        // Explicit computed-property source fixtures, not stock recipes for these unequal levels.
        var low = SilentGearRecognitionTest.trait(heat, api, "heat_resistant", 1);
        var high = SilentGearRecognitionTest.trait(heat, api, "heat_resistant", 5);
        for (var root : NeoForgeSilentGearTest.workbench(owner, low, high)) { damage(owner, root, owner.damageSources().lava(), 16); }
        var moon = lined(api.boot("azure_silver"), api, owner);
        for (var root : NeoForgeSilentGearTest.workbench(owner, moon, vanilla)) {
            clear(owner); owner.setItemSlot(EquipmentSlot.FEET, root); root.setDamageValue(10); wearCalls = 0;
            var event = new LivingFallEvent(owner, 30, 1); observe(() -> NeoForge.EVENT_BUS.post(event));
            double distance = 30 * (1 + api.traitLevel(moon, "moonwalker") * -0.15F);
            close(event.getDistance(), distance, "Completed production Moonwalker with candidate Bounce");
            require(event.isCanceled() && wearCalls == 1 && requestedWear == (int) (distance / 3) - 1, "Bounce did not use post-Moonwalker distance");
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
        details.append("Unequal Heat I/V uses max once; current-base damage anchor and additive armor; unchanged BOTH-source fireproof policy; Moonwalker precedes native Bounce cost; native armor peer charges non-Bounce mixed FEET safely; ordinary mixed destruction vs native repairable survival.\n");
        // Only these actors have negative initial Y, avoiding unrelated pending native rebounds.
        var moving = new ArrayList<ItemStack>(); moving.add(bounce.copy()); moving.addAll(samples.subList(0, 3));
        for (var stack : moving) {
            var actor = NeoForgeJustDireNegateFallTest.player(owner.level().getServer(), GameType.SURVIVAL);
            actor.setNoGravity(true); actor.setDeltaMovement(0, -1, 0); actor.setItemSlot(EquipmentSlot.FEET, stack.copy());
            require(fall(actor, 12).isCanceled(), "Deferred Bounce control missing");
            close(actor.getDeltaMovement().y, -1, "Bounce executed synchronously"); pending.add(actor);
        }
        clear(owner); evidence = details.toString(); return false;
    }

    private static ItemStack lined(ItemStack stack, NeoForgeSilentGearTest.NativeApi api, ServerPlayer owner) throws Exception {
        var lining = api.part("lining", "slime"); var before = stack.copy(); var liningBefore = lining.copy();
        Object swap = api.swapClass.getField("INSTANCE").get(null); var input = CraftingInput.of(2, 1, List.of(stack, lining));
        require((boolean) api.matches.invoke(swap, input, owner.level()), "Stock slime lining recipe rejected");
        var result = (ItemStack) api.assemble.invoke(swap, input);
        require(!result.isEmpty() && ItemStack.matches(before, stack) && ItemStack.matches(liningBefore, lining), "Part swap changed inputs"); return result;
    }

    private static void damage(ServerPlayer owner, ItemStack feet, DamageSource source, float expected) {
        clear(owner); owner.setItemSlot(EquipmentSlot.FEET, feet); observe(() -> incomingEvent(owner, source, expected));
    }
    private static void incomingEvent(ServerPlayer owner, DamageSource source, float expected) {
        var before = owner.getItemBySlot(EquipmentSlot.FEET).copy();
        var event = new LivingIncomingDamageEvent(owner, new DamageContainer(source, 20)); NeoForge.EVENT_BUS.post(event);
        close(event.getAmount(), expected, "Registered incoming native event math");
        require(!event.isCanceled() && ItemStack.matches(before, owner.getItemBySlot(EquipmentSlot.FEET)), "Incoming event unexpectedly changed/canceled gear");
    }
    private static LivingFallEvent fall(ServerPlayer player, double distance) {
        // Crimson Iron also has Malleable: make the existing probabilistic wear hook deterministic.
        nativeRandom.setSeed(0);
        bounceSounds = 0;
        var event = new LivingFallEvent(player, distance, 1); NeoForge.EVENT_BUS.post(event);
        require(bounceSounds == (event.isCanceled() ? 1 : 0), "Registered native Bounce sound count differs"); return event;
    }
    private static void retaliation(ServerPlayer owner, ItemStack stack, boolean expected) {
        var server = owner.level().getServer(); server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        var victim = NeoForgeJustDireNegateFallTest.player(server, GameType.SURVIVAL);
        victim.setItemSlot(EquipmentSlot.FEET, stack.copy());
        var attacker = new Zombie(EntityType.ZOMBIE, owner.level()); attacker.setYRot(90); attacker.setDeltaMovement(Vec3.ZERO);
        require(victim.hurtServer(victim.level(), victim.damageSources().mobAttack(attacker), 2), "Real incoming hit rejected");
        require((attacker.getDeltaMovement().lengthSqr() > 0) == expected, "Native post-damage attacker knockback differs");
        if (expected) { require(attacker.getDeltaMovement().x > 1.9 && Math.abs(attacker.getDeltaMovement().z) < 0.001, "Native attacker-yaw direction differs"); }
        attacker.setDeltaMovement(Vec3.ZERO); victim.invulnerableTime = 0; victim.setHealth(victim.getMaxHealth());
        var arrow = new Arrow(EntityType.ARROW, owner.level());
        var indirect = new DamageSource(victim.damageSources().generic().typeHolder(), arrow, attacker);
        require(victim.hurtServer(victim.level(), indirect, 2) && attacker.getDeltaMovement().equals(Vec3.ZERO), "Indirect living owner incorrectly gained Bounce retaliation");
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
