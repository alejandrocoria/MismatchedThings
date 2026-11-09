package games.alejandrocoria.mismatchedleggings.test.iceandfire;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.compatibility.iceandfire.IceAndFireTideCompatibility;
import games.alejandrocoria.mismatchedleggings.compatibility.iceandfire.IceAndFireTideReadiness;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.mixin.compatibility.iceandfire.IceAndFireTideArmorAccess;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Production native-tick comparisons; no replacement event posting or potion engine. */
public final class TideChecks {
    private static final String VARIANT = System.getProperty("leggings.test.iafTideVariant", "normal");
    private static final boolean BOOTS = Boolean.getBoolean("leggings.test.boots");
    private static final StringBuilder REPORT = new StringBuilder();
    private static final List<String> TRACE = new ArrayList<>();
    private static LivingEntity observed;
    private static float before, after;
    private static int events, damageCases;
    private static int tideSteps;
    private static int rootTicks;
    private static boolean cancelEffects;
    private static boolean finished;

    private TideChecks() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((MobEffectEvent.Applicable event) -> {
            if (cancelEffects && event.getEntity() == observed) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (LivingDamageEvent.Pre event) -> {
            if (event.getEntity() == observed) { before = event.getNewDamage(); events++; }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDamageEvent.Pre event) -> {
            if (event.getEntity() == observed) { after = event.getNewDamage(); }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer()));
    }

    public static void effect(LivingEntity entity, MobEffectInstance effect) {
        if (entity == observed && (effect.is(MobEffects.WATER_BREATHING) || effect.is(MobEffects.STRENGTH))) {
            TRACE.add(effect.getEffect() + ":" + effect.getDuration() + ":" + effect.getAmplifier()
                    + ":" + effect.isAmbient() + ":" + effect.isVisible() + ":" + effect.showIcon());
        }
    }

    private static void run(MinecraftServer server) {
        if (finished || server.getTickCount() < 20) { return; }
        finished = true;
        try {
            require(ModList.get().isLoaded("mismatchedboots") == BOOTS, "Unexpected product graph");
            REPORT.append("Production Tide; graph=").append(BOOTS ? "boots" : "alone").append("; variant=").append(VARIANT)
                    .append("; order=").append(System.getProperty("leggings.test.iafTideOrder")).append('\n');
            var ordinary = pair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS));
            if (VARIANT.equals("absent")) {
                require(!ModList.get().isLoaded("iceandfire"), "Absence graph loaded native mod");
                require(MixedLeggingsStructure.validData(ordinary.getFirst()).isPresent(), "Ordinary composition failed");
                var player = player(server);
                player.setItemSlot(EquipmentSlot.LEGS, ordinary.getFirst());
                player.doTick();
                require(!player.hasEffect(MobEffects.WATER_BREATHING) && !player.hasEffect(MobEffects.STRENGTH), "Absent adapter activated");
                require(java.util.Arrays.stream(net.minecraft.world.item.Item.class.getDeclaredMethods()).noneMatch(m -> m.getName().equals("mismatchedleggings$tideBaseReady")), "Absent API installed base checkpoint");
                REPORT.append("Quiet absence, ordinary composition/equipment tick, no base checkpoint\n");
            } else {
                require(TideControlPlugin.actualOrder != null, "Missing actual sorted mixin order");
                REPORT.append(TideControlPlugin.actualOrder).append("\n");
                receiverControl();
                tide(server);
                damageRegression(server.overworld());
            }
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "tide.txt"), REPORT);
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

    public static void rootTick(Entity entity, ItemStack stack, EquipmentSlot slot) {
        if (entity == observed && slot == EquipmentSlot.LEGS && ((ServerPlayer) entity).getItemBySlot(slot) == stack) { rootTicks++; }
    }

    private static boolean refused() { return !VARIANT.equals("normal") && !VARIANT.equals("compatible"); }
    private static boolean sharedRefusal() { return VARIANT.equals("entry") || VARIANT.equals("base"); }

    private static void receiverControl() throws Exception {
        if (VARIANT.equals("receiver")) {
            var field = IceAndFireTideCompatibility.class.getDeclaredField("receiver");
            field.setAccessible(true);
            field.set(null, nativeItem("tide_blue_boots").getItem());
        }
    }

    private static void tide(MinecraftServer server) throws Exception {
        var level = server.overworld();
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                for (int y = 0; y < 4; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, level.getMinY() + 3 + y, z), Blocks.WATER.defaultBlockState());
                }
            }
        }
        var subject = player(server);
        var control = player(server);
        for (var legs : pair(nativeItem("tide_red_leggings"), new ItemStack(Items.DIAMOND_LEGGINGS))) {
            scenario(subject, control, legs, false, false);
            scenario(subject, control, legs, false, true);
            scenario(subject, control, legs, true, false);
            scenario(subject, control, legs, true, true);
        }
        for (String color : List.of("blue", "bronze", "deepblue", "green", "purple", "red", "teal")) {
            for (var legs : pair(nativeItem("tide_" + color + "_leggings"), nativeItem("tide_blue_leggings"))) {
                scenario(subject, control, legs, true, false);
            }
        }
        clear(subject);
        clear(control);
        subject.getInventory().setItem(0, pair(nativeItem("tide_red_leggings"), new ItemStack(Items.IRON_LEGGINGS)).getFirst());
        compare(subject, control, true, 0);
        require(!subject.hasEffect(MobEffects.WATER_BREATHING), "Inventory-only root activated Tide");
        subject.getInventory().setItem(0, ItemStack.EMPTY);
        subject.setItemSlot(EquipmentSlot.FEET, pair(nativeItem("tide_red_leggings"), new ItemStack(Items.IRON_LEGGINGS)).getFirst());
        compare(subject, control, true, 0);
        require(!subject.hasEffect(MobEffects.WATER_BREATHING), "Wrong-slot root activated Tide");
        clear(subject); clear(control);
        var root = pair(nativeItem("tide_blue_leggings"), new ItemStack(Items.IRON_LEGGINGS)).getFirst();
        var foreign = new ItemStack(Items.IRON_LEGGINGS);
        foreign.set(LeggingsContent.mixedLeggingsDataType(), root.get(LeggingsContent.mixedLeggingsDataType()));
        var malformed = root.copy(); malformed.remove(DataComponents.EQUIPPABLE);
        var suspended = root.copy();
        suspended.set(LeggingsContent.createSuspendedDataType(), games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData.quarantined(new net.minecraft.nbt.CompoundTag(), new net.minecraft.nbt.CompoundTag()));
        for (var invalid : List.of(foreign, malformed, suspended, root.copyWithCount(2),
                new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID)),
                pair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS)).getFirst())) {
            clear(subject); clear(control);
            subject.setItemSlot(EquipmentSlot.LEGS, invalid);
            compare(subject, control, true, invalid.getItem() instanceof games.alejandrocoria.mismatchedleggings.content.MixedLeggingsItem ? 1 : 0);
            require(!subject.hasEffect(MobEffects.WATER_BREATHING), "Invalid/non-Tide root activated");
        }
        // Native wrong-slot class recognition remains upstream-owned.
        clear(subject); clear(control);
        subject.setItemSlot(EquipmentSlot.LEGS, nativeItem("tide_red_leggings"));
        control.setItemSlot(EquipmentSlot.LEGS, nativeItem("tide_red_leggings"));
        subject.setItemSlot(EquipmentSlot.HEAD, nativeItem("tide_blue_boots"));
        control.setItemSlot(EquipmentSlot.HEAD, nativeItem("tide_blue_boots"));
        compare(subject, control, true, 0);
        require(subject.getEffect(MobEffects.STRENGTH).getAmplifier() == 1, "Native wrong-slot semantics rewritten");
        REPORT.append("Real player doTick comparisons=").append(tideSteps)
                .append("; exact ordered effect trace/full hidden-state codec; root ticks and per-piece effect counts; immersion/dry/rain-only; cancelled native hooks; stronger effects/removal/expiry; immutable equipment/history; independent final readiness\n");
    }

    private static void scenario(ServerPlayer subject, ServerPlayer control, ItemStack legs, boolean wet, boolean neighbors) throws Exception {
        clear(subject);
        clear(control);
        subject.setItemSlot(EquipmentSlot.LEGS, legs.copy());
        control.setItemSlot(EquipmentSlot.LEGS, refused() ? ItemStack.EMPTY : nativeItem("tide_red_leggings"));
        readiness(subject);
        if (neighbors) {
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST)) {
                String path = slot == EquipmentSlot.HEAD ? "tide_teal_helmet" : "tide_green_chestplate";
                subject.setItemSlot(slot, nativeItem(path));
                control.setItemSlot(slot, nativeItem(path));
            }
            subject.setItemSlot(EquipmentSlot.FEET, BOOTS ? bootsPair(nativeItem("tide_blue_boots"), new ItemStack(Items.IRON_BOOTS)).getLast() : nativeItem("tide_blue_boots"));
            control.setItemSlot(EquipmentSlot.FEET, BOOTS && sharedRefusal() ? ItemStack.EMPTY : nativeItem("tide_blue_boots"));
        } else if (BOOTS) {
            subject.setItemSlot(EquipmentSlot.FEET, bootsPair(nativeItem("tide_blue_boots"), new ItemStack(Items.IRON_BOOTS)).getFirst());
            control.setItemSlot(EquipmentSlot.FEET, BOOTS && sharedRefusal() ? ItemStack.EMPTY : nativeItem("tide_blue_boots"));
        }
        for (int tick = 0; tick < 3; tick++) { compare(subject, control, wet, 1); }
        int pieces = (refused() ? 0 : 1) + (neighbors ? 2 : 0) + (neighbors || BOOTS ? (BOOTS && sharedRefusal() ? 0 : 1) : 0);
        require(subject.hasEffect(MobEffects.WATER_BREATHING) == (pieces > 0), "Wrong Water Breathing readiness");
        if (wet && pieces > 0) {
            int expected = pieces - 1;
            require(subject.getEffect(MobEffects.STRENGTH).getAmplifier() == expected, "Wrong logical Tide count");
        }
        for (var player : List.of(subject, control)) {
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 15, 5, true, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 15, 2, true, true, true));
        }
        for (int tick = 0; tick < 20; tick++) { compare(subject, control, true, 1); }
        // Real rain-only predicate, followed by a native event veto of all refreshes.
        subject.level().getWeatherData().setRaining(true);
        subject.level().setRainLevel(1);
        rainOnly = true;
        compare(subject, control, true, 1);
        rainOnly = false;
        subject.level().getWeatherData().setRaining(false);
        subject.level().setRainLevel(0);
        subject.removeAllEffects(); control.removeAllEffects();
        cancelEffects = true;
        compare(subject, control, true, 1);
        require(!subject.hasEffect(MobEffects.WATER_BREATHING) && !subject.hasEffect(MobEffects.STRENGTH), "Native effect veto ignored");
        cancelEffects = false;
        subject.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        control.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        compare(subject, control, false, 0);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            subject.setItemSlot(slot, ItemStack.EMPTY);
            control.setItemSlot(slot, ItemStack.EMPTY);
        }
        for (int tick = 0; tick < 55; tick++) { compare(subject, control, false, 0); }
        require(!subject.hasEffect(MobEffects.WATER_BREATHING) && !subject.hasEffect(MobEffects.STRENGTH), "Native effects failed to expire");
    }

    private static void compare(ServerPlayer subject, ServerPlayer control, boolean wet, int roots) {
        var items = new ArrayList<ItemStack>();
        var copies = new ArrayList<ItemStack>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            var stack = subject.getItemBySlot(slot);
            items.add(stack);
            copies.add(stack.copy());
        }
        var expected = step(control, wet);
        int activations = rootTicks;
        var actual = step(subject, wet);
        require(rootTicks - activations == roots, "Expected one actual worn-root LEGS tick");
        require(expected.equals(actual), "Native addEffect trace differs: expected=" + expected + " actual=" + actual);
        for (var effect : List.of(MobEffects.WATER_BREATHING, MobEffects.STRENGTH)) {
            require(state(subject.getEffect(effect), subject.level()).equals(state(control.getEffect(effect), control.level())), "Native hidden effect state differs");
        }
        int index = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            require(subject.getItemBySlot(slot) == items.get(index) && ItemStack.matches(items.get(index), copies.get(index)), "Equipment mutated by Tide");
            index++;
        }
        long pieces = 0;
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            if (control.getItemBySlot(slot).getItem() instanceof IceAndFireTideArmorAccess access
                    && access.mismatchedleggings$tideArmorType().getSlot() == slot) { pieces++; }
        }
        require(actual.size() == pieces * (wet ? 2 : 1), "Expected one complete native effect sequence per logical worn piece");
        tideSteps++;
    }

    private static void damageRegression(ServerLevel level) throws Exception {
        for (String path : List.of("armor_red_leggings", "forest_troll_leather_leggings")) {
            for (var legs : pair(nativeItem(path), new ItemStack(Items.IRON_LEGGINGS))) {
                var source = path.startsWith("armor") ? new DamageSource(level.registryAccess()
                        .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ResourceKey.create(
                                Registries.DAMAGE_TYPE, Identifier.parse("iceandfire:dragon_fire")))) : new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.ARROW));
                hit(level, source, legs, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, .8F);
            }
        }
        for (var legs : pair(nativeItem("dragonsteel_lightning_leggings"), new ItemStack(Items.IRON_LEGGINGS))) {
            var feet = BOOTS ? bootsPair(nativeItem("dragonsteel_lightning_boots"), new ItemStack(Items.IRON_BOOTS)).getFirst() : nativeItem("dragonsteel_lightning_boots");
            hit(level, level.damageSources().lightningBolt(), legs, feet, nativeItem("dragonsteel_lightning_helmet"), nativeItem("dragonsteel_lightning_chestplate"), 0F);
        }
        REPORT.append("Independent damage/set native hurtServer regressions=").append(damageCases).append("\n");
    }

    private static void hit(ServerLevel level, DamageSource source, ItemStack legs, ItemStack feet, ItemStack head, ItemStack chest, float coefficient) {
        var entity = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        require(entity != null, "Missing native damage subject");
        entity.setNoAi(true);
        entity.setItemSlot(EquipmentSlot.HEAD, head);
        entity.setItemSlot(EquipmentSlot.CHEST, chest);
        entity.setItemSlot(EquipmentSlot.LEGS, legs);
        entity.setItemSlot(EquipmentSlot.FEET, feet);
        var saved = MixedLeggingsStructure.validData(legs).orElse(null);
        var feetData = feet.getComponents().stream().map(c -> c.value())
                .filter(v -> v.getClass().getSimpleName().equals("MixedBootsData")).findFirst().orElse(null);
        observed = entity;
        events = 0;
        before = after = -1;
        float health = entity.getHealth();
        entity.hurtServer(level, source, 10F);
        observed = null;
        require(events == 1 && before > 0 && after >= 0, "Missing real native damage event");
        require(Math.abs(after - before * coefficient) < .0001F,
                "Damage coefficient mismatch case=" + damageCases + " source=" + source + " legs=" + legs + " expected=" + coefficient + " ratio=" + after / before);
        require(Math.abs((health - entity.getHealth()) - after) < .0001F, "Actual damage differs from event");
        require(entity.getItemBySlot(EquipmentSlot.LEGS) == legs, "Root replaced during damage");
        if (saved != null) { require(saved.equals(MixedLeggingsStructure.validData(legs).orElseThrow()), "Source history mutated during damage"); }
        require(entity.getItemBySlot(EquipmentSlot.FEET) == feet, "Feet replaced during damage");
        if (feetData != null) { require(feet.getComponents().stream().anyMatch(c -> feetData.equals(c.value())), "Boots source history mutated"); }
        damageCases++;
    }

    private static boolean rainOnly;
    private static void readiness(ServerPlayer player) throws Exception {
        require(IceAndFireTideCompatibility.recognizes(player, EquipmentSlot.LEGS) == !refused(), "Unexpected LEGS readiness");
        var receiver = nativeItem("tide_blue_leggings").getItem();
        var entry = (IceAndFireTideReadiness) receiver;
        var base = (IceAndFireTideReadiness.Base) receiver;
        require(entry.mismatchedleggings$tideEntryReady() == (!VARIANT.equals("count") && !VARIANT.equals("entry") && !VARIANT.equals("publication")), "LEGS final entry publication");
        require(base.mismatchedleggings$tideBaseReady() == !VARIANT.equals("base"), "LEGS final base publication");
        if (BOOTS) {
            var boots = nativeItem("tide_blue_boots").getItem();
            var contract = Class.forName("games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireTideReadiness");
            require((boolean) contract.getMethod("mismatchedboots$tideEntryReady").invoke(boots) == !VARIANT.equals("entry"), "Boots final entry proof");
            contract = Class.forName("games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireTideReadiness$Base");
            require((boolean) contract.getMethod("mismatchedboots$tideBaseReady").invoke(boots) == !VARIANT.equals("base"), "Boots final base proof");
        }
    }

    private static List<String> step(ServerPlayer player, boolean wet) {
        player.setPos(wet && !rainOnly ? 1.5 : 12.5, player.level().getMinY() + 3.5, wet && !rainOnly ? 1.5 : 12.5);
        player.setDeltaMovement(Vec3.ZERO);
        player.setAirSupply(300);
        observed = player;
        TRACE.clear();
        player.doTick();
        observed = null;
        require(player.isInWaterOrRain() == wet && (!rainOnly || !player.isInWater()), "Real immersion/rain-only predicate mismatch");
        return List.copyOf(TRACE);
    }

    private static String state(MobEffectInstance effect, ServerLevel level) {
        return effect == null ? "none" : MobEffectInstance.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), effect).getOrThrow().toString();
    }

    private static ServerPlayer player(MinecraftServer server) throws Exception {
        var profile = new GameProfile(UUID.randomUUID(), "IafLegs");
        var player = new ServerPlayer(server, server.overworld(), profile, ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(profile, false));
        var loaded = ServerGamePacketListenerImpl.class.getDeclaredMethod("markClientLoaded");
        loaded.setAccessible(true);
        loaded.invoke(player.connection);
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        server.overworld().getChunkAt(new BlockPos(0, server.overworld().getMinY() + 3, 0));
        return player;
    }

    private static void clear(ServerPlayer player) {
        player.removeAllEffects();
        player.getInventory().clearContent();
        for (EquipmentSlot slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
    }

    public static ItemStack nativeItem(String path) {
        var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("iceandfire:" + path)));
        require(!stack.isEmpty(), "Missing native item " + path);
        return stack;
    }

    private static List<ItemStack> pair(ItemStack left, ItemStack right) {
        var pair = MixedLeggingsFactory.createPair(left, right);
        return List.of(pair.primary(), pair.inverse());
    }

    private static List<ItemStack> bootsPair(ItemStack left, ItemStack right) throws Exception {
        var type = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
        var pair = type.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, left, right);
        return List.of((ItemStack) pair.getClass().getMethod("primary").invoke(pair), (ItemStack) pair.getClass().getMethod("inverse").invoke(pair));
    }
}
