package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.silentchaos512.gear.gear.trait.effect.CancelEffectsTraitEffect;
import net.silentchaos512.gear.util.TraitHelper;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseIncomingChecks.clear;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseIncomingChecks.only;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseIncomingChecks.trait;

/** Disposable native controls and actual dedicated rescue; not maintained production acceptance. */
public final class RemainingSpike {
    private static int ticks;
    private static int phase;
    private static int sample;
    private static int started;
    private static boolean finished;
    private static Player watched;
    private static int pushes;
    private static int poison;
    private static int wither;
    private static int lastPushTick = -1;
    private static double startY;
    private static double maximumY;
    private static ItemStack legs;
    private static ItemStack feet;
    private static ItemStack beforeLegs;
    private static ItemStack beforeFeet;
    private static ItemStack reloadRoot;
    private static Object oldReceiver;
    private static ServerPlayer fake;
    private static CompletableFuture<Void> reload;
    private static final StringBuilder REPORT = new StringBuilder();

    private RemainingSpike() {}

    static int samples() { return Boolean.getBoolean("leggings.test.boots") ? 5 : 3; }

    public static void push(Player player, double x, double y, double z) {
        if (player != watched) { return; }
        require(x == 0 && y == 20 && z == 0, "Native rescue parameters changed");
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.level().getServer().getPlayerList().getPlayers().contains(player)) {
            require(lastPushTick != ticks, "Duplicate native rescue in one real player tick");
            lastPushTick = ticks;
        }
        pushes++;
    }

    public static void removal(LivingEntity actor, Holder<MobEffect> effect) {
        if (actor != watched) { return; }
        if (effect.equals(MobEffects.POISON)) { poison++; }
        if (effect.equals(MobEffects.WITHER)) { wither++; }
    }

    public static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer()) { return; }
        try {
            require(++ticks < 4200, "Remaining spike timeout: phase=" + phase + " sample=" + sample);
            require(!Files.exists(StateChecks.report("client.failure.txt")), "Remaining client failed");
            if (phase == 0) {
                if (ticks < 20) { return; }
                require(proof("net.silentchaos512.gear.event.GearEvents", "mismatchedleggings$remainingReady"), "Candidate final route refused");
                if (Boolean.getBoolean("leggings.test.boots")) {
                    require(proof("net.silentchaos512.gear.event.GearEvents", "mismatchedboots$sgVoidReady")
                            && proof(CancelEffectsTraitEffect.class.getName(), "mismatchedboots$sgCureReady"), "Boots independent final contract refused");
                }
                fake = FoundationChecks.player(server);
                controlled();
                var cureSource = cures();
                reloadRoot = FoundationChecks.paid(fake, cureSource, new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
                oldReceiver = effect();
                reload = server.reloadResources(server.getPackRepository().getSelectedIds());
                phase = 1;
                return;
            }
            if (phase == 1) {
                if (!reload.isDone()) { return; }
                reload.join();
                require(effect() != oldReceiver, "Resource reload reused stale cure receiver");
                cureCheck(fake, reloadRoot, EquipmentSlot.LEGS, 1, 1);
                REPORT.append("Real server resource reload replaced cure definition/receiver; retained paid root removes both targets, no source mutation\n");
                if (Boolean.getBoolean("leggings.test.sgRemainingHeadless")) { finish(server); return; }
                for (int x = 3; x < 14; x++) {
                    for (int z = 3; z < 14; z++) { server.overworld().setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3); }
                }
                Files.writeString(StateChecks.report("ready.txt"), "Native controls/reload passed; dedicated remaining spike ready\n");
                phase = 2;
            }
            if (phase == 9) {
                if (Files.exists(StateChecks.report("client.ok")) && server.getPlayerList().getPlayers().isEmpty()) { finish(server); }
                return;
            }
            if (server.getPlayerList().getPlayers().isEmpty() || !Files.exists(StateChecks.report("remaining-client-ready.ok"))) { return; }
            var player = server.getPlayerList().getPlayers().getFirst();
            if (phase == 2) {
                prepare(player);
                phase = 3;
                started = ticks;
            }
            require(ticks - started < 400, "Connected remaining sample stalled: " + sample + "/" + phase + " y=" + player.getY() + " pushes=" + pushes);
            if (phase == 3 && ack("equipment")) {
                // Release from above the strict threshold; no rescue can happen while equipment packets settle.
                player.setNoGravity(false);
                player.connection.teleport(8.5, startY, 8.5, 0, 0);
                phase = 4;
            } else if (phase == 4) {
                maximumY = Math.max(maximumY, player.getY());
                if (!ack("rescue")) { return; }
                require(pushes > 0 && player.hasEffect(MobEffects.LEVITATION) && player.hasEffect(MobEffects.SLOW_FALLING), "No actual native rescue");
                require(player.getEffect(MobEffects.LEVITATION).getAmplifier() == 9 && player.getEffect(MobEffects.SLOW_FALLING).getAmplifier() == 3
                        && maximumY > startY + 2, "Native potion parameters/server rescue trajectory changed");
                require(ItemStack.matches(beforeLegs, legs) && ItemStack.matches(beforeFeet, feet), "Rescue wrote roots/sources or charged resources");
                publish(player, "used");
                REPORT.append("sample ").append(sample).append(": actual native rescue, server rise=").append(maximumY - startY)
                        .append(", qualifying invocations=").append(pushes).append(", one original push per tick; roots unchanged\n");
                phase = 5;
            } else if (phase == 5 && ack("used")) {
                watched = null;
                sample++;
                if (sample == samples()) {
                    Files.writeString(StateChecks.report("finish.txt"), "All connected native/candidate rescue samples observed\n");
                    phase = 9;
                } else { phase = 2; }
            }
        } catch (Throwable failure) {
            watched = null;
            finished = true;
            SkeletonChecks.failed("server", failure);
            server.halt(false);
        }
    }

    private static void controlled() throws Exception {
        var source = FoundationChecks.armor("tyrian_steel");
        require(TraitHelper.getTraits(source).stream().anyMatch(t -> t.getTraitId().getPath().equals("void_ward") && t.getLevel() == 1), "Native Tyrian Steel LEGS has no Void Ward");
        var roots = FoundationChecks.paid(fake, source, new ItemStack(Items.DIAMOND_LEGGINGS));
        double boundary = fake.level().getMinY() - 32;
        for (var root : List.of(source, roots.getFirst(), roots.get(1))) {
            rescueCheck(root, ItemStack.EMPTY, EquipmentSlot.LEGS, boundary, false);
            rescueCheck(root, ItemStack.EMPTY, EquipmentSlot.LEGS, boundary - .5, true);
            rescueCheck(root, ItemStack.EMPTY, EquipmentSlot.MAINHAND, boundary - .5, false);
            rescueCheck(root, ItemStack.EMPTY, EquipmentSlot.OFFHAND, boundary - .5, false);
            rescueCheck(root, ItemStack.EMPTY, null, boundary - .5, false);
        }
        var malformed = roots.getFirst().copy();
        malformed.setCount(2);
        rescueCheck(malformed, ItemStack.EMPTY, EquipmentSlot.LEGS, boundary - .5, false);
        var bare = new ItemStack(roots.getFirst().getItem());
        rescueCheck(bare, ItemStack.EMPTY, EquipmentSlot.LEGS, boundary - .5, false);
        SilentGearRemainingCandidate.refuseVoid = true;
        rescueCheck(roots.getFirst(), ItemStack.EMPTY, EquipmentSlot.LEGS, boundary - .5, false);
        var nativeFeet = DefenseIncomingChecks.nativePiece("boots", "boot_plates", "tyrian_steel");
        rescueCheck(roots.getFirst(), nativeFeet, EquipmentSlot.LEGS, boundary - .5, true);
        if (Boolean.getBoolean("leggings.test.boots")) {
            rescueCheck(roots.getFirst(), mixedFeet(nativeFeet), EquipmentSlot.LEGS, boundary - .5, true);
        }
        SilentGearRemainingCandidate.refuseVoid = false;
        REPORT.append("Native Tyrian Steel LEGS and both paid inverses: strict threshold, worn/hands/idle/count/bare controls; local candidate refusal preserves native/Boots peer\n");
        var cure = cures();
        var inverses = FoundationChecks.paid(fake, cure, new ItemStack(Items.DIAMOND_LEGGINGS));
        var duplicates = FoundationChecks.paid(fake, cure, cure);
        for (var root : List.of(cure, inverses.getFirst(), inverses.get(1), duplicates.getFirst(), duplicates.get(1))) {
            for (var slot : List.of(EquipmentSlot.LEGS, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) { cureCheck(fake, root, slot, 1, 1); }
            cureCheck(fake, root, null, 0, 0);
        }
        cureCheck(fake, inverses.getFirst(), EquipmentSlot.HEAD, 0, 0);
        var distinct = FoundationChecks.paid(fake, only(FoundationChecks.armor("iron"), trait("cure_poison", 1)),
                only(FoundationChecks.armor("iron"), trait("cure_wither", 1)));
        for (var root : distinct) { cureCheck(fake, root, EquipmentSlot.LEGS, 1, 1); }
        var nonPlayer = new Pig(EntityType.PIG, fake.level());
        require(nonPlayer.addEffect(new MobEffectInstance(MobEffects.POISON, 200))
                && nonPlayer.addEffect(new MobEffectInstance(MobEffects.WITHER, 200)), "Non-player control cannot acquire the target effects");
        var unchanged = inverses.getFirst().copy();
        int callsBefore = SilentGearRemainingCandidate.calls;
        inverses.getFirst().inventoryTick(fake.level(), nonPlayer, EquipmentSlot.LEGS);
        require(nonPlayer.hasEffect(MobEffects.POISON) && nonPlayer.hasEffect(MobEffects.WITHER)
                && SilentGearRemainingCandidate.calls == callsBefore && ItemStack.matches(unchanged, inverses.getFirst()), "Non-player cure control ran");
        REPORT.append("Synthetic cure acquisition on native gear/paid inverses/duplicates/distinct sources: worn and both hands, idle/wrong-slot negatives, one native removal per ID, non-Wielder tick, unrelated Luck/root history retained\n");
    }

    private static void rescueCheck(ItemStack root, ItemStack boots, EquipmentSlot slot, double y, boolean active) {
        clear(fake);
        fake.getInventory().clearContent();
        fake.removeAllEffects();
        fake.setItemSlot(EquipmentSlot.FEET, boots);
        if (slot == null) { fake.getInventory().setItem(8, root); } else { fake.setItemSlot(slot, root); }
        fake.setPos(8.5, y, 8.5);
        watched = fake;
        pushes = 0;
        var before = root.copy();
        var feetBefore = boots.copy();
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(fake));
        require(pushes == (active ? 1 : 0) && fake.hasEffect(MobEffects.LEVITATION) == active && fake.hasEffect(MobEffects.SLOW_FALLING) == active,
                "Native/candidate Void Ward control differs slot=" + slot + " y=" + y);
        if (active) {
            require(fake.getEffect(MobEffects.SLOW_FALLING).getDuration() == 400 && fake.getEffect(MobEffects.SLOW_FALLING).getAmplifier() == 3
                    && fake.getEffect(MobEffects.LEVITATION).getDuration() == 200 && fake.getEffect(MobEffects.LEVITATION).getAmplifier() == 9, "Native exact potion parameters changed");
        }
        require(ItemStack.matches(before, root) && ItemStack.matches(feetBefore, boots), "Controlled rescue wrote roots");
        watched = null;
    }

    private static void cureCheck(ServerPlayer player, ItemStack root, EquipmentSlot slot, int expectedPoison, int expectedWither) {
        clear(player);
        player.getInventory().clearContent();
        player.removeAllEffects();
        if (slot != null) { player.setItemSlot(slot, root); } else { player.getInventory().setItem(8, root); }
        player.tickCount = 11; // The candidate must not inherit Wielder's ten-tick cadence.
        player.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, 200));
        player.addEffect(new MobEffectInstance(MobEffects.LUCK, 200));
        var before = root.copy();
        watched = player;
        poison = wither = 0;
        root.inventoryTick(player.level(), player, slot);
        require(poison == expectedPoison && wither == expectedWither, "Cure callback count differs: " + poison + "/" + wither + " slot=" + slot);
        require(player.hasEffect(MobEffects.POISON) == (expectedPoison == 0) && player.hasEffect(MobEffects.WITHER) == (expectedWither == 0)
                && player.hasEffect(MobEffects.LUCK) && ItemStack.matches(before, root), "Cure actor/root/unrelated-effect contract differs");
        watched = null;
    }

    private static void prepare(ServerPlayer player) throws Exception {
        watched = null;
        clear(player);
        player.getInventory().clearContent();
        player.removeAllEffects();
        player.setInvulnerable(true); // Void Ward is a height trigger, not a damage trigger.
        // A real floor lets prior native rescue momentum settle without test-manufactured motion packets.
        player.setNoGravity(false);
        player.connection.teleport(8.5, 81, 8.5, 0, 0);
        var source = FoundationChecks.armor("tyrian_steel");
        legs = sample == 0 ? source : FoundationChecks.paid(player, source, new ItemStack(Items.DIAMOND_LEGGINGS)).get(sample == 2 ? 1 : 0);
        feet = new ItemStack(Items.DIAMOND_BOOTS);
        if (sample >= 3) {
            feet = mixedFeet(DefenseIncomingChecks.nativePiece("boots", "boot_plates", "tyrian_steel"));
            if (sample == 4) { legs = new ItemStack(Items.DIAMOND_LEGGINGS); }
        }
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        player.setItemSlot(EquipmentSlot.FEET, feet);
        beforeLegs = legs.copy();
        beforeFeet = feet.copy();
        startY = player.level().getMinY() - 33.5;
        maximumY = startY;
        pushes = 0;
        watched = player;
        publish(player, "equipment");
    }

    private static ItemStack mixedFeet(ItemStack source) throws Exception {
        var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, source, new ItemStack(Items.DIAMOND_BOOTS));
        return (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
    }

    private static ItemStack cures() throws Exception { return only(FoundationChecks.armor("iron"), trait("cure_poison", 1), trait("cure_wither", 1)); }
    private static Object effect() { return trait("cure_poison", 1).getTrait().getEffects().getFirst(); }

    private static boolean proof(String owner, String name) throws Exception {
        var method = Class.forName(owner).getDeclaredMethod(name);
        method.setAccessible(true);
        return (boolean) method.invoke(null);
    }

    private static boolean ack(String name) { return Files.exists(StateChecks.report("remaining-" + sample + "." + name + ".ok")); }

    private static void publish(ServerPlayer player, String name) throws Exception {
        player.inventoryMenu.broadcastChanges();
        var ops = player.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var state = new JsonObject();
        state.add("legs", ItemStack.CODEC.encodeStart(ops, legs).getOrThrow());
        state.add("feet", ItemStack.CODEC.encodeStart(ops, feet).getOrThrow());
        state.addProperty("startY", startY);
        Files.writeString(StateChecks.report("remaining-" + sample + "." + name + ".json"), state.toString());
        Files.writeString(StateChecks.report("remaining-" + sample + "." + name + ".ready"), "Complete observation\n");
    }

    private static void finish(MinecraftServer server) throws Exception {
        var source = cures();
        var root = FoundationChecks.paid(fake, source, new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
        SilentGearRemainingCandidate.failPoison = true;
        cureCheck(fake, root, EquipmentSlot.LEGS, 1, 1);
        require(SilentGearRemainingCandidate.poisonDisabled(), "Post-callback failure did not revoke only Poison");
        int before = SilentGearRemainingCandidate.calls;
        cureCheck(fake, root, EquipmentSlot.LEGS, 0, 1);
        require(SilentGearRemainingCandidate.calls == before + 1, "Failed Poison replayed instead of only Wither");
        REPORT.append("Deliberate local post-Poison callback failure: actor mutation retained, no replay/retry, Wither remains active\n")
                .append("Actual Void Ward mixin order: ").append(RemainingSpikePlugin.actualOrder).append('\n');
        Files.writeString(StateChecks.report("remaining-server.txt"), REPORT);
        Files.writeString(StateChecks.report("state.txt"), REPORT);
        SkeletonChecks.passed("server");
        finished = true;
        server.halt(false);
    }
}
