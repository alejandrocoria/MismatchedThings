package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.silentchaos512.gear.gear.trait.effect.CancelEffectsTraitEffect;
import net.silentchaos512.gear.util.TraitHelper;

import java.nio.file.Files;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseIncomingChecks.clear;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseIncomingChecks.nativePiece;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseIncomingChecks.only;
import static games.alejandrocoria.mismatchedleggings.test.silentgear.DefenseIncomingChecks.trait;

/** Bounded production TCP acceptance. Native callbacks alone supply rescue and cancellation packets. */
public final class RemainingLifecycle {
    static final int CURE_SAMPLES = 9;
    private static final List<EquipmentSlot> CURE_SLOTS = List.of(EquipmentSlot.LEGS, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);
    private enum Step { START, VOID_PREPARE, VOID_EQUIPMENT, VOID_RESCUE, VOID_USED, CURE_PREPARE, CURE_IDLE, CURE_ACTIVE, CURE_RETURNED, CURE_USED, FINISH }
    private static Step step = Step.START;
    private static int ticks;
    private static int started;
    private static int sample;
    private static int pushes;
    private static int lastPush = -1;
    private static int poison;
    private static int wither;
    private static int lastPoison = -1;
    private static int lastWither = -1;
    private static int tickPoison;
    private static int tickWither;
    private static int maximumPoison;
    private static int maximumWither;
    private static final int[] NATIVE_CADENCE = new int[3];
    private static Player watched;
    private static boolean watchingCures;
    private static boolean finished;
    private static ItemStack root = ItemStack.EMPTY;
    private static ItemStack beforeRoot;
    private static ItemStack legs = ItemStack.EMPTY;
    private static ItemStack feet = ItemStack.EMPTY;
    private static ItemStack beforeLegs;
    private static ItemStack beforeFeet;
    private static double startY;
    private static double maximumY;
    private static final StringBuilder REPORT = new StringBuilder();

    private RemainingLifecycle() {}

    static int voidSamples() { return Boolean.getBoolean("leggings.test.boots") ? 5 : 3; }

    public static void push(Player player, double x, double y, double z) {
        if (player != watched || watchingCures) { return; }
        require(x == 0 && y == 20 && z == 0, "Native rescue parameters changed");
        int tick = player.level().getServer().getTickCount();
        require(lastPush != tick, "Duplicate original rescue push in one real player tick");
        lastPush = tick;
        pushes++;
    }

    public static void removal(LivingEntity actor, Holder<MobEffect> effect) {
        if (actor != watched || !watchingCures) { return; }
        int tick = actor.level().getServer().getTickCount();
        if (effect.equals(MobEffects.POISON)) {
            tickPoison = lastPoison == tick ? tickPoison + 1 : 1;
            lastPoison = tick;
            maximumPoison = Math.max(maximumPoison, tickPoison);
            poison++;
        }
        if (effect.equals(MobEffects.WITHER)) {
            tickWither = lastWither == tick ? tickWither + 1 : 1;
            lastWither = tick;
            maximumWither = Math.max(maximumWither, tickWither);
            wither++;
        }
    }

    public static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer()) { return; }
        try {
            require(++ticks < 4200, "Remaining acceptance timeout: " + step + "/" + sample);
            require(!Files.exists(StateChecks.report("client.failure.txt")), "Connected remaining client failed");
            if (step == Step.START) {
                if (ticks < 20) { return; }
                initialize(server);
                step = Step.VOID_PREPARE;
            }
            if (step == Step.FINISH) {
                if (Files.exists(StateChecks.report("client.ok")) && server.getPlayerList().getPlayers().isEmpty()) {
                    Files.writeString(StateChecks.report("remaining-server.txt"), REPORT);
                    Files.writeString(StateChecks.report("state.txt"), REPORT);
                    server.saveEverything(true, true, true);
                    SkeletonChecks.passed("server");
                    finished = true;
                    server.halt(false);
                }
                return;
            }
            if (server.getPlayerList().getPlayers().isEmpty() || !Files.exists(StateChecks.report("remaining-client-ready.ok"))) { return; }
            var player = server.getPlayerList().getPlayers().getFirst();
            if (step == Step.VOID_PREPARE) {
                prepareVoid(player);
                step = Step.VOID_EQUIPMENT;
                started = ticks;
            } else if (step == Step.CURE_PREPARE) {
                prepareCure(player);
                step = Step.CURE_IDLE;
                started = ticks;
            }
            require(ticks - started < 400, "Connected sample stalled: " + step + "/" + sample + " y=" + player.getY());
            switch (step) {
                case VOID_EQUIPMENT -> {
                    if (!ack("void", "equipment")) { return; }
                    player.connection.teleport(8.5, startY, 8.5, 0, 0);
                    step = Step.VOID_RESCUE;
                }
                case VOID_RESCUE -> {
                    maximumY = Math.max(maximumY, player.getY());
                    if (!ack("void", "rescue")) { return; }
                    require(pushes > 0 && maximumY > startY + 2 && player.hasEffect(MobEffects.LEVITATION)
                            && player.hasEffect(MobEffects.SLOW_FALLING), "No actual native server rescue");
                    require(player.getEffect(MobEffects.LEVITATION).getAmplifier() == 9
                            && player.getEffect(MobEffects.SLOW_FALLING).getAmplifier() == 3, "Native rescue potion parameters changed");
                    unchanged();
                    publish(player, "void", "used", EquipmentSlot.LEGS);
                    REPORT.append("Void sample ").append(sample).append(": server rise=").append(maximumY - startY)
                            .append("; original pushes=").append(pushes).append(", once per qualifying real tick; unchanged complete roots\n");
                    step = Step.VOID_USED;
                }
                case VOID_USED -> {
                    if (!ack("void", "used")) { return; }
                    watched = null;
                    if (++sample == voidSamples()) { sample = 0; step = Step.CURE_PREPARE; }
                    else { step = Step.VOID_PREPARE; }
                }
                case CURE_IDLE -> {
                    require(poison == 0 && wither == 0 && player.hasEffect(MobEffects.POISON) && player.hasEffect(MobEffects.WITHER),
                            "Idle source cured a target before selection");
                    if (!ack("cure", "idle") || ticks - started < 10) { return; }
                    player.getInventory().setItem(8, ItemStack.EMPTY);
                    player.setItemSlot(cureSlot(), root);
                    observeCures(player);
                    publish(player, "cure", "active", cureSlot());
                    started = ticks;
                    step = Step.CURE_ACTIVE;
                }
                case CURE_ACTIVE -> {
                    if (!ack("cure", "active") || ticks - started < 6) { return; }
                    require(poison > 0 && poison == wither && maximumPoison == maximumWither
                            && !player.hasEffect(MobEffects.POISON) && !player.hasEffect(MobEffects.WITHER)
                            && player.hasEffect(MobEffects.LUCK), "Selected real item ticks did not cure exactly their targets");
                    if (sample < 3) { NATIVE_CADENCE[sample] = maximumPoison; }
                    require(maximumPoison == NATIVE_CADENCE[sample % 3], "Mixed source duplicated/changed native per-tick cancellation cadence");
                    REPORT.append("Cure sample ").append(sample).append('/').append(cureSlot()).append(": removals=")
                            .append(poison).append('/').append(wither).append("; per-tick maximum=").append(maximumPoison)
                            .append(" matches native; selected real item ticks, no source duplication\n");
                    watched = null;
                    player.setItemSlot(cureSlot(), cureSlot() == EquipmentSlot.LEGS ? legs : ItemStack.EMPTY);
                    player.getInventory().setItem(8, root);
                    addTargets(player);
                    observeCures(player);
                    publish(player, "cure", "returned", null);
                    started = ticks;
                    step = Step.CURE_RETURNED;
                }
                case CURE_RETURNED -> {
                    require(poison == 0 && wither == 0 && player.hasEffect(MobEffects.POISON) && player.hasEffect(MobEffects.WITHER)
                            && player.hasEffect(MobEffects.LUCK), "Returning to idle did not stop cancellation");
                    if (!ack("cure", "returned") || ticks - started < 10) { return; }
                    unchanged();
                    publish(player, "cure", "used", null);
                    step = Step.CURE_USED;
                }
                case CURE_USED -> {
                    if (!ack("cure", "used")) { return; }
                    watched = null;
                    watchingCures = false;
                    if (++sample == CURE_SAMPLES) {
                        Files.writeString(StateChecks.report("finish.txt"), "All connected production rescue/cure samples passed\n");
                        step = Step.FINISH;
                    } else { step = Step.CURE_PREPARE; }
                }
                default -> {}
            }
        } catch (Throwable failure) {
            watched = null;
            finished = true;
            SkeletonChecks.failed("server", failure);
            server.halt(false);
        }
    }

    private static void initialize(MinecraftServer server) throws Exception {
        require(ModList.get().isLoaded("silentgear") && ModList.get().isLoaded("mismatchedboots") == Boolean.getBoolean("leggings.test.boots"),
                "Wrong connected remaining graph");
        require(proof("net.silentchaos512.gear.event.GearEvents", "mismatchedleggings$sgVoidReady")
                && proof(CancelEffectsTraitEffect.class.getName(), "mismatchedleggings$sgCureReady"), "Production remaining guard declined");
        if (Boolean.getBoolean("leggings.test.boots")) {
            require(proof("net.silentchaos512.gear.event.GearEvents", "mismatchedboots$sgVoidReady")
                    && proof(CancelEffectsTraitEffect.class.getName(), "mismatchedboots$sgCureReady"), "Independent Boots guard declined");
        }
        for (int x = 3; x < 14; x++) {
            for (int z = 3; z < 14; z++) { server.overworld().setBlock(new BlockPos(x, 80, z), Blocks.STONE.defaultBlockState(), 3); }
        }
        RemainingManualFixtures.write(FoundationChecks.player(server));
        Files.writeString(StateChecks.report("ready.txt"), "Dedicated production remaining effects ready\n");
    }

    private static void settle(ServerPlayer player) {
        watched = null;
        watchingCures = false;
        clear(player);
        player.getInventory().clearContent();
        player.removeAllEffects();
        player.setInvulnerable(true); // Height-triggered rescue, not a damage test; cure negatives must survive Wither.
        player.setNoGravity(false);
        // Real floor preparation settles prior native rescue momentum; do not manufacture a stopping packet.
        player.connection.teleport(8.5, 81, 8.5, 0, 0);
    }

    private static void prepareVoid(ServerPlayer player) throws Exception {
        settle(player);
        var source = FoundationChecks.armor("tyrian_steel");
        require(TraitHelper.getTraits(source).stream().anyMatch(t -> t.getTraitId().getPath().equals("void_ward") && t.getLevel() == 1),
                "Native Tyrian Steel LEGS lacks computed Void Ward I");
        root = sample == 0 ? source : FoundationChecks.paid(player, source, new ItemStack(Items.DIAMOND_LEGGINGS)).get(sample == 2 ? 1 : 0);
        legs = root;
        feet = new ItemStack(Items.DIAMOND_BOOTS);
        if (sample >= 3) {
            feet = mixedFeet(nativePiece("boots", "boot_plates", "tyrian_steel"));
            if (sample == 4) { legs = root = new ItemStack(Items.DIAMOND_LEGGINGS); }
        }
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        player.setItemSlot(EquipmentSlot.FEET, feet);
        snapshot();
        startY = player.level().getMinY() - 33.5;
        maximumY = startY;
        pushes = 0;
        lastPush = -1;
        watched = player;
        publish(player, "void", "equipment", EquipmentSlot.LEGS);
    }

    private static void prepareCure(ServerPlayer player) throws Exception {
        settle(player);
        var source = cures(FoundationChecks.armor("iron"));
        root = sample < 3 ? source : FoundationChecks.paid(player, source, new ItemStack(Items.DIAMOND_LEGGINGS)).get(sample < 6 ? 0 : 1);
        legs = new ItemStack(Items.DIAMOND_LEGGINGS);
        feet = new ItemStack(Items.DIAMOND_BOOTS);
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        player.setItemSlot(EquipmentSlot.FEET, feet);
        player.getInventory().setSelectedSlot(0);
        player.getInventory().setItem(8, root);
        snapshot();
        addTargets(player);
        observeCures(player);
        publish(player, "cure", "idle", null);
    }

    private static void addTargets(ServerPlayer player) {
        require(player.addEffect(new MobEffectInstance(MobEffects.POISON, 600))
                && player.addEffect(new MobEffectInstance(MobEffects.WITHER, 600)), "Connected cure fixture could not acquire target effects");
        if (!player.hasEffect(MobEffects.LUCK)) { player.addEffect(new MobEffectInstance(MobEffects.LUCK, 1200)); }
    }

    private static void observeCures(Player player) {
        poison = wither = maximumPoison = maximumWither = 0;
        lastPoison = lastWither = -1;
        watchingCures = true;
        watched = player;
    }

    private static void snapshot() { beforeRoot = root.copy(); beforeLegs = legs.copy(); beforeFeet = feet.copy(); }

    private static void unchanged() {
        require(ItemStack.matches(beforeRoot, root) && ItemStack.matches(beforeLegs, legs) && ItemStack.matches(beforeFeet, feet),
                "Connected effects wrote roots/source histories or charged resources");
    }

    static ItemStack cures(ItemStack source) { return only(source, trait("cure_poison", 1), trait("cure_wither", 1)); }

    static ItemStack mixedFeet(ItemStack source) throws Exception {
        var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, source, new ItemStack(Items.DIAMOND_BOOTS));
        return (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
    }

    private static EquipmentSlot cureSlot() { return CURE_SLOTS.get(sample % 3); }

    private static boolean proof(String owner, String name) throws Exception {
        var method = Class.forName(owner).getDeclaredMethod(name);
        method.setAccessible(true);
        return (boolean) method.invoke(null);
    }

    private static boolean ack(String family, String name) { return Files.exists(StateChecks.report("remaining-" + family + "-" + sample + "." + name + ".ok")); }

    private static void publish(ServerPlayer player, String family, String name, EquipmentSlot slot) throws Exception {
        player.inventoryMenu.broadcastChanges();
        var ops = player.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var state = new JsonObject();
        state.add("root", ItemStack.CODEC.encodeStart(ops, root).getOrThrow());
        state.add("legs", ItemStack.CODEC.encodeStart(ops, player.getItemBySlot(EquipmentSlot.LEGS)).getOrThrow());
        state.add("feet", ItemStack.CODEC.encodeStart(ops, feet).getOrThrow());
        state.addProperty("slot", slot == null ? "INVENTORY" : slot.name());
        state.addProperty("startY", startY);
        String path = "remaining-" + family + "-" + sample + "." + name;
        Files.writeString(StateChecks.report(path + ".json"), state.toString());
        Files.writeString(StateChecks.report(path + ".ready"), "Complete expected root/equipment observation\n");
    }
}
