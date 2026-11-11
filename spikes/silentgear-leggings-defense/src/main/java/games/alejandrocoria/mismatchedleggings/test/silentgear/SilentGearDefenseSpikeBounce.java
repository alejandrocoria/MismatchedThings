package games.alejandrocoria.mismatchedleggings.test.silentgear;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.silentchaos512.gear.util.TraitHelper;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.SilentGearDefenseSpikeIncomingCases.clear;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.SilentGearDefenseSpikeIncomingCases.nativePiece;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.SilentGearDefenseSpikeIncomingCases.only;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.SilentGearDefenseSpikeIncomingCases.trait;

/** Query-only candidate. Native events own cancellation, sound, scheduling, push, retaliation and FEET costs. */
public final class SilentGearDefenseSpikeBounce {
    private static int phase;
    private static int scheduledTick;
    private static int sounds;
    private static int ordinaryCalls;
    private static int nativeCalls;
    private static int requested;
    private static ItemStack legs = ItemStack.EMPTY;
    private static ItemStack feet = ItemStack.EMPTY;
    private static ItemStack blockedWriter;
    private static boolean mixedFeet;
    private static boolean ordinaryObserved;
    private static boolean observing;
    private static final List<Pending> PENDING = new ArrayList<>();

    public static int level(int original, LivingEntity actor, boolean fall) {
        String refused = fall ? "fall-query" : "retaliation-query";
        var root = actor.getItemBySlot(EquipmentSlot.LEGS);
        if (!SilentGearDefenseSpike.mode().equals("full") || SilentGearDefenseSpike.mutation().equals(refused)
                || !(actor instanceof ServerPlayer) || actor.level().isClientSide() || MixedLeggingsStructure.validData(root).isEmpty()) { return original; }
        try {
            int extra = SilentGearDefenseSpike.selected(root).stream().filter(t -> t.getTraitId().getPath().equals("bounce"))
                    .mapToInt(net.silentchaos512.gear.api.traits.TraitInstance::getLevel).max().orElse(0);
            return Math.max(original, extra);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    public static void wear(ItemStack stack, int amount, EquipmentSlot slot) {
        if (!ordinaryObserved) { return; }
        if (!legs.isEmpty() && stack == legs) { throw new AssertionError("Native Bounce charged LEGS"); }
        if (!feet.isEmpty() && stack == feet) {
            require(slot == EquipmentSlot.FEET, "Wrong ordinary cost slot");
            ordinaryCalls++;
            requested += amount;
        }
    }

    public static void nativeWear(ItemStack stack, int amount, EquipmentSlot slot) {
        if (!observing) { return; }
        if (!legs.isEmpty() && stack == legs) { throw new AssertionError("Native wear received LEGS"); }
        if (stack == feet) {
            require(!mixedFeet && slot == EquipmentSlot.FEET, "Native attemptDamage received mixed FEET");
            nativeCalls++;
        }
    }

    public static void recalculation(ItemStack stack) {
        if (stack == blockedWriter || !legs.isEmpty() && stack == legs || mixedFeet && stack == feet) {
            throw new AssertionError("Native recalculation received an observed protected root");
        }
    }

    static boolean step(MinecraftServer server, StringBuilder report) throws Exception {
        if (phase > 0) {
            require(server.getTickCount() > scheduledTick && queued() == 0, "Real native pre-tick did not drain rebound actions");
            for (var sample : PENDING) {
                close(sample.actor().getDeltaMovement().distanceToSqr(new Vec3(.125, .25, .25)), 0, "Native additive scheduled push");
                require(ItemStack.matches(sample.legs(), sample.actor().getItemBySlot(EquipmentSlot.LEGS))
                        && ItemStack.matches(sample.feet(), sample.actor().getItemBySlot(EquipmentSlot.FEET)), "Deferred rebound changed roots");
            }
            if (phase++ == 1) { return false; }
            report.append("Bounce: actual next server-pre-tick additive push and following-tick stability for ").append(PENDING.size())
                    .append(" native/mixed samples; no manual queue drain, duplicate push or deferred bill\n");
            return true;
        }
        server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        NeoForge.EVENT_BUS.addListener((PlayLevelSoundEvent.AtPosition event) -> {
            if (event.getSound() != null && event.getSound().value() == SoundEvents.SLIME_BLOCK_FALL) {
                require(event.getSource() == SoundSource.PLAYERS && event.getOriginalVolume() == 1 && event.getOriginalPitch() == 1, "Changed native sound parameters");
                sounds++;
            }
        });
        var owner = FoundationChecks.player(server);
        var nativeLegs = FoundationChecks.armor("iron", "lining", "slime");
        require(TraitHelper.getTraits(nativeLegs).stream().anyMatch(t -> t.getTraitId().getPath().equals("bounce") && t.getLevel() == 1), "Native lined LEGS Bounce control missing");
        calibrate(owner);
        var roots = new ArrayList<>(FoundationChecks.paid(owner, nativeLegs, new ItemStack(Items.DIAMOND_LEGGINGS)));
        roots.addAll(FoundationChecks.paid(owner, nativeLegs, nativeLegs));
        boolean fallEnabled = SilentGearDefenseSpike.mode().equals("full") && !SilentGearDefenseSpike.mutation().equals("fall-query");
        boolean retaliationEnabled = SilentGearDefenseSpike.mode().equals("full") && !SilentGearDefenseSpike.mutation().equals("retaliation-query");
        boolean boots = Boolean.getBoolean("leggings.test.boots");
        boolean safe = !SilentGearDefenseSpike.mutation().equals("safety");
        var nativeFeet = only(nativePiece("boots", "boot_plates", "iron"), trait("bounce", 1));
        landing(owner, nativeLegs.copy(), new ItemStack(Items.DIAMOND_BOOTS), ItemStack.EMPTY, 12, true, false);
        retaliation(server, nativeLegs, ItemStack.EMPTY, ItemStack.EMPTY, 1);
        landing(owner, ItemStack.EMPTY, nativeFeet.copy(), ItemStack.EMPTY, 12, true, false);
        var nativeBroken = nativeFeet.copy();
        nativeBroken.setDamageValue(nativeBroken.getMaxDamage() - 1);
        clear(owner);
        owner.setItemSlot(EquipmentSlot.LEGS, nativeLegs.copy());
        owner.setItemSlot(EquipmentSlot.FEET, nativeBroken);
        watch(owner.getItemBySlot(EquipmentSlot.LEGS), nativeBroken, false);
        var nativeFall = new LivingFallEvent(owner, 12, 1);
        try { NeoForge.EVENT_BUS.post(nativeFall); } finally { unwatch(); }
        require(nativeFall.isCanceled() && !nativeBroken.isEmpty() && nativeCalls == 1, "Native repairable broken control changed");
        for (var root : roots) {
            var before = root.copy();
            landing(owner, root, new ItemStack(Items.DIAMOND_BOOTS), ItemStack.EMPTY, 12, fallEnabled, false);
            landing(owner, root, ItemStack.EMPTY, ItemStack.EMPTY, 12, fallEnabled, false);
            retaliation(server, root, ItemStack.EMPTY, ItemStack.EMPTY, retaliationEnabled ? 1 : 0);
            for (String negative : List.of("short", "crouch", "flying", "canceled", "count", "slot", "data", "held")) {
                clear(owner);
                var candidate = root.copy();
                if (negative.equals("count")) { candidate.setCount(2); }
                if (negative.equals("slot")) { candidate.remove(DataComponents.EQUIPPABLE); }
                if (negative.equals("data")) { candidate.remove(games.alejandrocoria.mismatchedleggings.content.LeggingsContent.mixedLeggingsDataType()); }
                owner.setItemSlot(negative.equals("held") ? EquipmentSlot.MAINHAND : EquipmentSlot.LEGS, candidate);
                owner.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
                owner.setShiftKeyDown(negative.equals("crouch"));
                if (negative.equals("flying")) { owner.startFallFlying(); }
                sounds = 0;
                int beforeQueue = queued();
                var event = new LivingFallEvent(owner, negative.equals("short") ? 3 : 12, 1);
                event.setCanceled(negative.equals("canceled"));
                NeoForge.EVENT_BUS.post(event);
                require(event.isCanceled() == negative.equals("canceled") && sounds == 0 && queued() == beforeQueue, "Fall negative acquired work: " + negative);
            }
            require(ItemStack.matches(before, root), "Fall/retaliation edited LEGS or source history");
        }
        var stronger = only(nativePiece("helmet", "helmet_plates", "iron"), trait("bounce", 2));
        clear(owner);
        owner.setItemSlot(EquipmentSlot.LEGS, roots.getFirst());
        owner.setDeltaMovement(0, .25, 0);
        int beforePositive = queued();
        NeoForge.EVENT_BUS.post(new LivingFallEvent(owner, 12, 1));
        require(queued() == beforePositive && owner.getDeltaMovement().y == .25, "Nonnegative initial velocity gained rebound");
        var nonPlayer = new Zombie(EntityType.ZOMBIE, owner.level());
        nonPlayer.setItemSlot(EquipmentSlot.LEGS, roots.getFirst().copy());
        sounds = 0;
        var nonPlayerFall = new LivingFallEvent(nonPlayer, 12, 1);
        NeoForge.EVENT_BUS.post(nonPlayerFall);
        require(!nonPlayerFall.isCanceled() && sounds == 0 && queued() == beforePositive, "Non-player gained Bounce fall");
        retaliation(server, roots.getFirst(), ItemStack.EMPTY, stronger, 2);
        var moon = only(nativePiece("helmet", "helmet_plates", "iron"), trait("moonwalker", 2));
        landing(owner, roots.getFirst(), new ItemStack(Items.DIAMOND_BOOTS), moon, 30, fallEnabled, false);

        if (boots) {
            var plain = mixedFeet(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
            var withBounce = mixedFeet(nativeFeet, new ItemStack(Items.DIAMOND_BOOTS));
            for (var foot : List.of(plain, withBounce)) {
                boolean ownBounce = foot == withBounce;
                landing(owner, roots.getFirst(), foot.copy(), ItemStack.EMPTY, 12, safe && (fallEnabled || ownBounce), true);
                landing(owner, nativeLegs.copy(), foot.copy(), ItemStack.EMPTY, 12, safe, true);
                retaliation(server, roots.getFirst(), foot.copy(), ItemStack.EMPTY, retaliationEnabled || ownBounce ? 1 : 0);
                var breaking = foot.copy();
                breaking.setDamageValue(breaking.getMaxDamage() - 1);
                landing(owner, roots.getFirst(), breaking, stronger, 12, safe, true);
            }
            // Cross an actual native damage-factor band, not an arbitrarily chosen durability value.
            var band = Class.forName("net.silentchaos512.gear.util.GearHelper").getDeclaredMethod("getDamageFactor", ItemStack.class, int.class);
            band.setAccessible(true);
            int crossing = -1;
            for (int damage = 0; damage < withBounce.getMaxDamage() - 4; damage++) {
                withBounce.setDamageValue(damage);
                int a = (int) band.invoke(null, withBounce, withBounce.getMaxDamage());
                withBounce.setDamageValue(damage + 3);
                if (a != (int) band.invoke(null, withBounce, withBounce.getMaxDamage())) { crossing = damage; break; }
            }
            require(crossing >= 0, "No real native damage band");
            withBounce.setDamageValue(crossing);
            landing(owner, roots.getFirst(), withBounce, ItemStack.EMPTY, 12, safe, true);
            for (String malformed : List.of("count", "slot", "data")) {
                var bad = plain.copy();
                if (malformed.equals("count")) { bad.setCount(2); }
                else if (malformed.equals("slot")) { bad.remove(DataComponents.EQUIPPABLE); }
                else {
                    var content = Class.forName("games.alejandrocoria.mismatchedboots.content.ModContent");
                    var type = (java.util.Optional<?>) content.getMethod("mixedBootsDataType").invoke(null);
                    bad.remove((net.minecraft.core.component.DataComponentType<?>) type.orElseThrow());
                }
                landing(owner, roots.getFirst(), bad, stronger, 12, false, true);
            }
            landing(owner, roots.getFirst(), plain.copy(), moon, 30, safe && fallEnabled, true);
            for (boolean creative : List.of(false, true)) {
                var exempt = plain.copy();
                if (!creative) { exempt.set(DataComponents.UNBREAKABLE, Unit.INSTANCE); }
                owner.setGameMode(creative ? GameType.CREATIVE : GameType.SURVIVAL);
                landing(owner, roots.getFirst(), exempt, stronger, 12, safe, true);
            }
            owner.setGameMode(GameType.SURVIVAL);
        }
        report.append("Bounce query candidate: paid inverse/duplicate LEGS; native/vanilla/empty/current mixed FEET; max versus a higher native piece; actual melee retaliation, projectile/environment/non-player negatives; native sound/cancel, short/crouch/flying/canceled/held/malformed negatives; Moonwalker-before-cost; mixed band crossing, destruction, creative/Unbreakable; calibrated native writer tripwire passed\n");
        report.append("Measured LEGS admission: fall=").append(fallEnabled).append(", retaliation=").append(retaliationEnabled)
                .append(", Boots safe wear=").append(safe).append("; native repairable survival remains native-only\n");

        clear(owner);
        require(queued() == 0, "Unexpected preexisting rebound actions");
        schedule(server, nativeLegs.copy(), new ItemStack(Items.DIAMOND_BOOTS), true, false);
        for (var root : roots.subList(0, 3)) {
            schedule(server, root.copy(), new ItemStack(Items.DIAMOND_BOOTS), fallEnabled, false);
        }
        if (boots) {
            schedule(server, roots.getFirst().copy(), mixedFeet(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)), safe && fallEnabled, true);
        }
        scheduledTick = server.getTickCount();
        phase = 1;
        return false;
    }

    private static void landing(ServerPlayer actor, ItemStack leg, ItemStack foot, ItemStack helmet,
                                double distance, boolean active, boolean protectFeet) throws Exception {
        clear(actor);
        actor.setItemSlot(EquipmentSlot.HEAD, helmet);
        actor.setItemSlot(EquipmentSlot.LEGS, leg);
        actor.setItemSlot(EquipmentSlot.FEET, foot);
        var beforeLegs = leg.copy();
        var beforeFeet = foot.copy();
        double actualDistance = helmet.isEmpty() || TraitHelper.getTraitLevel(helmet, net.silentchaos512.gear.api.util.DataResource.trait("silentgear:moonwalker")) == 0
                ? distance : distance * .7F;
        int bill = (int) (actualDistance / 3) - 1;
        watch(leg, foot, protectFeet);
        sounds = 0;
        int queue = queued();
        var event = new LivingFallEvent(actor, distance, 1);
        try { NeoForge.EVENT_BUS.post(event); } finally { unwatch(); }
        require(event.isCanceled() == active && sounds == (active ? 1 : 0) && queued() == queue, "Fall cancellation/sound/synchronous queue differs");
        close(event.getDistance(), actualDistance, "Moonwalker preceding Bounce");
        require(ItemStack.matches(beforeLegs, leg), "Native fall edited mixed LEGS/history");
        if (protectFeet) {
            require(nativeCalls == 0 && ordinaryCalls == (active && bill > 0 ? 1 : 0), "Mixed FEET native/duplicate payment");
            if (active && bill > 0) { require(requested == bill, "Mixed FEET bill did not use native adjusted distance"); }
        } else {
            require(nativeCalls == (active && bill > 0 ? 1 : 0), "Native FEET bill count differs");
        }
        if (active && bill > 0 && !beforeFeet.isEmpty() && !beforeFeet.has(DataComponents.UNBREAKABLE) && !actor.getAbilities().instabuild) {
            if (beforeFeet.getDamageValue() + bill >= beforeFeet.getMaxDamage() && protectFeet) { require(foot.isEmpty(), "Mixed FEET borrowed native repairable survival"); }
            else { beforeFeet.setDamageValue(beforeFeet.getDamageValue() + bill); require(ItemStack.matches(beforeFeet, foot), "Ordinary FEET delta/history differs"); }
        } else { require(ItemStack.matches(beforeFeet, foot), "Inactive/exempt fall changed FEET"); }
    }

    private static void schedule(MinecraftServer server, ItemStack leg, ItemStack foot, boolean active, boolean protectFeet) throws Exception {
        var actor = FoundationChecks.player(server);
        actor.setNoGravity(true);
        actor.setItemSlot(EquipmentSlot.LEGS, leg);
        actor.setItemSlot(EquipmentSlot.FEET, foot);
        actor.setDeltaMovement(0, -1, 0);
        int beforeQueue = queued();
        watch(leg, foot, protectFeet);
        sounds = 0;
        var event = new LivingFallEvent(actor, 12, 1);
        try { NeoForge.EVENT_BUS.post(event); } finally { unwatch(); }
        require(event.isCanceled() == active && sounds == (active ? 1 : 0) && queued() == beforeQueue + (active ? 1 : 0), "Native deferred queue/sound count differs");
        close(actor.getDeltaMovement().y, -1, "Rebound ran synchronously");
        if (active) {
            actor.setDeltaMovement(.125, -.5, .25);
            PENDING.add(new Pending(actor, leg.copy(), foot.copy()));
        }
    }

    private static void retaliation(MinecraftServer server, ItemStack leg, ItemStack foot, ItemStack helmet, int expected) throws Exception {
        var victim = FoundationChecks.player(server);
        var root = leg.copy();
        root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        var shoe = foot.copy();
        if (!shoe.isEmpty()) { shoe.set(DataComponents.UNBREAKABLE, Unit.INSTANCE); }
        victim.setItemSlot(EquipmentSlot.LEGS, root);
        victim.setItemSlot(EquipmentSlot.FEET, shoe);
        victim.setItemSlot(EquipmentSlot.HEAD, helmet.copy());
        var before = root.copy();
        var beforeFeet = shoe.copy();
        var attacker = new Zombie(EntityType.ZOMBIE, victim.level());
        attacker.setYRot(90);
        attacker.setDeltaMovement(Vec3.ZERO);
        watch(root, shoe, false);
        ordinaryObserved = false; // Ordinary armor-hit wear is not the separate native Bounce landing bill.
        try {
            require(victim.hurtServer(victim.level(), victim.damageSources().mobAttack(attacker), 2), "Native melee control rejected");
            close(attacker.getDeltaMovement().x, 2 * expected, "Native retaliation max strength/direction");
            close(attacker.getDeltaMovement().z, 0, "Native retaliation direction");
            attacker.setDeltaMovement(Vec3.ZERO);
            victim.invulnerableTime = 0;
            var arrow = new Arrow(EntityType.ARROW, victim.level());
            require(victim.hurtServer(victim.level(), new DamageSource(victim.damageSources().generic().typeHolder(), arrow, attacker), 2)
                    && attacker.getDeltaMovement().equals(Vec3.ZERO), "Projectile owner gained retaliation");
            victim.invulnerableTime = 0;
            require(victim.hurtServer(victim.level(), victim.damageSources().generic(), 2) && attacker.getDeltaMovement().equals(Vec3.ZERO), "Environmental hit gained retaliation");
            var mob = new Zombie(EntityType.ZOMBIE, victim.level());
            mob.setItemSlot(EquipmentSlot.LEGS, root.copy());
            require(mob.hurtServer(victim.level(), mob.damageSources().mobAttack(attacker), 2) && attacker.getDeltaMovement().equals(Vec3.ZERO), "Non-player gained retaliation");
            require(nativeCalls == 0 && ItemStack.matches(before, root) && ItemStack.matches(beforeFeet, shoe), "Retaliation charged native fall wear or changed roots");
        } finally { unwatch(); }
    }

    private static ItemStack mixedFeet(ItemStack first, ItemStack second) throws Exception {
        var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, first, second);
        return (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
    }

    private static void calibrate(ServerPlayer owner) throws Exception {
        blockedWriter = FoundationChecks.armor("iron");
        boolean caught = false;
        try {
            Class.forName("net.silentchaos512.gear.util.GearData").getMethod("recalculateGearData", ItemStack.class, net.minecraft.world.entity.player.Player.class)
                    .invoke(null, blockedWriter, owner);
        } catch (InvocationTargetException failure) { caught = failure.getCause() instanceof AssertionError; }
        finally { blockedWriter = null; }
        require(caught, "Native writer witness was not calibrated");
    }

    private static void watch(ItemStack leg, ItemStack foot, boolean protectFeet) {
        legs = leg;
        feet = foot;
        mixedFeet = protectFeet;
        ordinaryObserved = true;
        observing = true;
        ordinaryCalls = 0;
        nativeCalls = 0;
        requested = 0;
    }

    private static void unwatch() { legs = ItemStack.EMPTY; feet = ItemStack.EMPTY; mixedFeet = false; ordinaryObserved = false; observing = false; }

    private static int queued() throws Exception {
        var type = Class.forName("net.silentchaos512.lib.event.ServerTicks");
        var instance = type.getDeclaredField("INSTANCE");
        var queue = type.getDeclaredField("scheduledActions");
        instance.setAccessible(true);
        queue.setAccessible(true);
        return ((java.util.Queue<?>) queue.get(instance.get(null))).size();
    }

    private static void close(double actual, double expected, String message) {
        require(Math.abs(actual - expected) < .0001, message + ": " + actual + " != " + expected);
    }

    private record Pending(ServerPlayer actor, ItemStack legs, ItemStack feet) {}
}
