package games.alejandrocoria.mismatchedleggings.test.silentgear;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.silentchaos512.gear.api.traits.TraitInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseIncomingChecks.clear;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseIncomingChecks.nativePiece;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseIncomingChecks.only;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseIncomingChecks.trait;

/** Real selected item ticks, calibrated native removals and retained-root server reload acceptance. */
public final class CureChecks {
    private static LivingEntity watched;
    private static ItemStack liveRoot;
    private static int poison;
    private static int wither;
    private static int dispatches;
    private static Object poisonReceiver;
    private static Object witherReceiver;
    private static boolean failAfterPoison;
    private static boolean poisonActive;
    private static boolean witherActive;
    private static ServerPlayer player;
    private static ItemStack retained;
    private static ItemStack retainedBefore;
    private static Object oldPoison;
    private static Object oldWither;
    private static CompletableFuture<Void> reload;
    private static int started;
    private static boolean finished;

    private CureChecks() {}

    public static void removal(LivingEntity actor, Holder<MobEffect> effect) {
        if (actor != watched) { return; }
        if (effect.equals(MobEffects.POISON)) { poison++; }
        if (effect.equals(MobEffects.WITHER)) { wither++; }
    }

    public static void afterNative(Object effect, Object instance, ItemStack copy, Player actor) {
        if (actor != watched) { return; }
        require(copy != liveRoot && ItemStack.matches(copy, liveRoot), "Native cure did not receive an unchanged disposable root copy");
        dispatches++;
        String id = ((TraitInstance) instance).getTraitId().getPath();
        if (id.equals("cure_poison")) { poisonReceiver = effect; }
        if (id.equals("cure_wither")) { witherReceiver = effect; }
        if (failAfterPoison && id.equals("cure_poison")) {
            failAfterPoison = false;
            throw new IllegalStateException("Deliberate local failure after native Poison removal");
        }
    }

    static boolean step(MinecraftServer server, StringBuilder report) throws Exception {
        String variant = System.getProperty("leggings.test.sgCureVariant", "");
        if (variant.isEmpty() || finished) { return true; }
        require(!Boolean.getBoolean("leggings.test.sgRemainingSpike"), "Research candidate must not supply cure acceptance");
        if (Boolean.getBoolean("leggings.test.sgFoundationAbsent")) {
            report.append("Cures: quiet optional absence; ordinary item ticks unchanged\n");
            finished = true;
            return true;
        }
        if (reload == null) {
            player = FoundationChecks.player(server);
            boolean ready = !variant.equals("proof");
            poisonActive = witherActive = ready;
            require(proof("net.silentchaos512.gear.gear.trait.effect.CancelEffectsTraitEffect", "mismatchedleggings$sgCureReady") == ready,
                    "Final native cure proof differs");
            require(CureTestPlugin.mutations == (List.of("proof", "compatible").contains(variant) ? 1 : 0), "Missing cure transformation witness");
            if (Boolean.getBoolean("leggings.test.boots")) {
                require(proof("net.silentchaos512.gear.gear.trait.effect.CancelEffectsTraitEffect", "mismatchedboots$sgCureReady"),
                        "Local cure proof disabled Boots");
            }
            basic(ready);
            if (ready) { CureDefinitionChecks.run(); }
            fault(variant);
            independentVoidWard();
            oldPoison = effect("poison");
            oldWither = effect("wither");
            retainedBefore = retained.copy();
            reload = server.reloadResources(server.getPackRepository().getSelectedIds());
            started = server.getTickCount();
            return false;
        }
        require(server.getTickCount() - started < 400, "Cure resource reload stalled");
        if (!reload.isDone()) { return false; }
        reload.join();
        require(effect("poison") != oldPoison && effect("wither") != oldWither, "Reload retained old cancellation receivers");
        check(retained, EquipmentSlot.LEGS, poisonActive ? 1 : 0, witherActive ? 1 : 0);
        require((poisonReceiver == effect("poison")) == poisonActive && (witherReceiver == effect("wither")) == witherActive,
                "Retained root used stale receivers or revived a disabled cure after reload");
        require(ItemStack.matches(retainedBefore, retained), "Reload/dispatch changed retained root or sources");
        var nativeSource = cures(FoundationChecks.armor("iron"));
        check(nativeSource, EquipmentSlot.LEGS, 1, 1);
        if (Boolean.getBoolean("leggings.test.boots")) { sibling(poisonActive, witherActive); }
        independentVoidWard();
        report.append("Cures production: synthetic native/paid inverses/duplicates/distinct sources; selected LEGS/hands every tick, idle/wrong-slot/non-player/invalid negatives; exact leaf/removal counts and disposable root preservation; variant=")
                .append(variant).append('\n');
        report.append("Cures reload: actual server resource reload replaced both receivers; retained root reads healthy current data, declined IDs stay declined, native/Boots and Void Ward remain independent; no rollback/replay\n");
        clear(player);
        player.getInventory().clearContent();
        player.removeAllEffects();
        player.setPos(0, 65, 0);
        finished = true;
        return true;
    }

    private static void basic(boolean ready) throws Exception {
        var source = cures(FoundationChecks.armor("iron"));
        var before = source.copy();
        var roots = FoundationChecks.paid(player, source, new ItemStack(Items.DIAMOND_LEGGINGS));
        retained = roots.getFirst();
        var duplicates = FoundationChecks.paid(player, source, source);
        var distinct = FoundationChecks.paid(player, only(FoundationChecks.armor("iron"), trait("cure_poison", 1)),
                only(FoundationChecks.armor("iron"), trait("cure_wither", 1)));
        for (var root : List.of(source, roots.getFirst(), roots.get(1), duplicates.getFirst(), duplicates.get(1), distinct.getFirst(), distinct.get(1))) {
            int expected = root == source || ready ? 1 : 0;
            for (var slot : List.of(EquipmentSlot.LEGS, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
                player.tickCount = 11;
                check(root, slot, expected, expected);
                player.tickCount = 12;
                check(root, slot, expected, expected);
            }
            check(root, null, 0, 0);
            check(root, EquipmentSlot.HEAD, root == source ? 1 : 0, root == source ? 1 : 0);
        }
        var malformed = retained.copy();
        malformed.remove(DataComponents.EQUIPPABLE);
        var foreign = new ItemStack(Items.IRON_LEGGINGS);
        foreign.set(LeggingsContent.mixedLeggingsDataType(), retained.get(LeggingsContent.mixedLeggingsDataType()));
        var negatives = new ArrayList<>(List.of(new ItemStack(retained.getItem()), retained.copyWithCount(2), malformed, foreign));
        for (boolean quarantine : List.of(false, true)) {
            var suspended = retained.copy();
            suspended.set(LeggingsContent.createSuspendedDataType(), quarantine
                    ? SuspendedLeggingsData.quarantined(new CompoundTag(), new CompoundTag())
                    : SuspendedLeggingsData.of(new CompoundTag(), new CompoundTag(), new CompoundTag()));
            negatives.add(suspended);
        }
        negatives.addAll(FoundationChecks.paid(player, new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS)));
        for (var root : negatives) { check(root, EquipmentSlot.LEGS, 0, 0); }
        var pig = new Pig(EntityType.PIG, player.level());
        require(pig.addEffect(new MobEffectInstance(MobEffects.POISON, 100)) && pig.addEffect(new MobEffectInstance(MobEffects.WITHER, 100)),
                "Non-player negative cannot acquire target effects");
        var rootBefore = retained.copy();
        watched = pig;
        poison = wither = dispatches = 0;
        try {
            retained.inventoryTick(player.level(), pig, EquipmentSlot.LEGS);
            require(poison == 0 && wither == 0 && dispatches == 0 && pig.hasEffect(MobEffects.POISON) && pig.hasEffect(MobEffects.WITHER),
                    "Non-player activated mixed cures");
        } finally { watched = null; }
        require(ItemStack.matches(rootBefore, retained) && ItemStack.matches(before, source), "Cure checks changed root/source history");
        prepare();
        player.setItemSlot(EquipmentSlot.LEGS, retained);
        watched = player;
        liveRoot = retained;
        poison = wither = dispatches = 0;
        try {
            var otherLevel = player.level().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            require(otherLevel != null && otherLevel != player.level(), "Missing mismatched-level negative");
            retained.inventoryTick(otherLevel, player, EquipmentSlot.LEGS);
            require(poison == 0 && wither == 0 && dispatches == 0 && player.hasEffect(MobEffects.POISON) && player.hasEffect(MobEffects.WITHER)
                    && ItemStack.matches(rootBefore, retained), "Mismatched server level activated cures or changed the root");
        } finally { watched = null; liveRoot = null; }
        check(retained, EquipmentSlot.LEGS, ready ? 1 : 0, ready ? 1 : 0);
        if (Boolean.getBoolean("leggings.test.boots")) { sibling(ready, ready); }
    }

    private static void fault(String variant) throws Exception {
        if (variant.equals("callback")) {
            failAfterPoison = true;
            check(retained, EquipmentSlot.LEGS, 1, 1);
            require(!failAfterPoison, "Post-Poison tripwire never executed");
            poisonActive = false;
        } else if (variant.equals("target") || variant.equals("definition")) {
            boolean target = variant.equals("target");
            Object owner = target ? effect("poison") : trait("cure_wither", 1).getTrait();
            var field = CureDefinitionChecks.field(owner, "effects");
            Object original = field.get(owner);
            try {
                field.set(owner, target ? List.of(MobEffects.LUCK) : List.of());
                check(retained, EquipmentSlot.LEGS, target ? 0 : 1, target ? 1 : 0);
            } finally { field.set(owner, original); }
            if (target) { poisonActive = false; } else { witherActive = false; }
        }
        check(retained, EquipmentSlot.LEGS, poisonActive ? 1 : 0, witherActive ? 1 : 0);
    }

    private static void check(ItemStack root, EquipmentSlot slot, int expectedPoison, int expectedWither) {
        prepare();
        if (slot == null) { player.getInventory().setItem(8, root); } else { player.setItemSlot(slot, root); }
        var before = root.copy();
        liveRoot = root;
        watched = player;
        poison = wither = dispatches = 0;
        poisonReceiver = witherReceiver = null;
        try {
            root.inventoryTick(player.level(), player, slot);
            int expectedCalls = MixedLeggingsStructure.isMixedItem(root) ? expectedPoison + expectedWither : 0;
            require(poison == expectedPoison && wither == expectedWither && dispatches == expectedCalls,
                    "Cure count differs slot=" + slot + " removals=" + poison + "/" + wither + " leaves=" + dispatches);
            require(player.hasEffect(MobEffects.POISON) == (expectedPoison == 0) && player.hasEffect(MobEffects.WITHER) == (expectedWither == 0)
                    && player.hasEffect(MobEffects.LUCK) && ItemStack.matches(before, root), "Cure actor/unrelated effect/root changed");
        } finally { watched = null; liveRoot = null; }
    }

    private static void sibling(boolean poisonReady, boolean witherReady) throws Exception {
        var nativeFeet = cures(nativePiece("boots", "boot_plates", "iron"));
        var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, nativeFeet, new ItemStack(Items.DIAMOND_BOOTS));
        var feet = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
        prepare();
        player.setItemSlot(EquipmentSlot.LEGS, retained);
        player.setItemSlot(EquipmentSlot.FEET, feet);
        var beforeFeet = feet.copy();
        var beforeLegs = retained.copy();
        liveRoot = retained;
        watched = player;
        poison = wither = dispatches = 0;
        try {
            retained.inventoryTick(player.level(), player, EquipmentSlot.LEGS);
            feet.inventoryTick(player.level(), player, EquipmentSlot.FEET);
            require(poison == (poisonReady ? 2 : 1) && wither == (witherReady ? 2 : 1)
                    && dispatches == (poisonReady ? 1 : 0) + (witherReady ? 1 : 0), "Separate items were actor-globally deduplicated or Boots declined");
            require(!player.hasEffect(MobEffects.POISON) && !player.hasEffect(MobEffects.WITHER) && player.hasEffect(MobEffects.LUCK)
                    && ItemStack.matches(beforeFeet, feet) && ItemStack.matches(beforeLegs, retained), "Joint cure ticks changed unrelated state");
        } finally { watched = null; liveRoot = null; }
    }

    private static void independentVoidWard() throws Exception {
        clear(player);
        player.getInventory().clearContent();
        player.removeAllEffects();
        var root = FoundationChecks.paid(player, FoundationChecks.armor("tyrian_steel"), new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst();
        player.setItemSlot(EquipmentSlot.LEGS, root);
        player.setPos(0, player.level().getMinY() - 33, 0);
        var before = root.copy();
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
        require(player.getDeltaMovement().y == 20 && player.hasEffect(MobEffects.LEVITATION) && player.hasEffect(MobEffects.SLOW_FALLING)
                && ItemStack.matches(before, root), "Cure refusal disabled independent production Void Ward");
    }

    private static void prepare() {
        clear(player);
        player.getInventory().clearContent();
        player.removeAllEffects();
        require(player.addEffect(new MobEffectInstance(MobEffects.POISON, 100)) && player.addEffect(new MobEffectInstance(MobEffects.WITHER, 100))
                && player.addEffect(new MobEffectInstance(MobEffects.LUCK, 100)), "Cure fixture did not acquire effects");
    }

    private static ItemStack cures(ItemStack source) {
        return only(source, trait("cure_poison", 1), trait("cure_wither", 1));
    }

    private static Object effect(String target) {
        return trait("cure_" + target, 1).getTrait().getEffects().getFirst();
    }

    private static boolean proof(String owner, String name) throws Exception {
        var method = Class.forName(owner).getDeclaredMethod(name);
        method.setAccessible(true);
        return (boolean) method.invoke(null);
    }
}
