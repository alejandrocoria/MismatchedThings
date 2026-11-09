package games.alejandrocoria.mismatchedleggings.test.iafspike;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.core.BlockPos;
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
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Research over real native events/ticks; no replacement event posting or potion engine. */
public final class SpikeChecks {
    private static final String VARIANT = System.getProperty("leggings.test.iafVariant", "normal");
    private static final boolean BOOTS = Boolean.getBoolean("leggings.test.boots");
    private static final StringBuilder REPORT = new StringBuilder();
    private static final List<String> TRACE = new ArrayList<>();
    private static LivingEntity observed;
    private static float before;
    private static float after;
    private static int events;
    private static int damageCases;
    private static int tideSteps;
    private static boolean finished;

    private SpikeChecks() {}

    public static void register() {
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
            REPORT.append("Research only; graph=").append(BOOTS ? "boots" : "alone").append("; variant=").append(VARIANT)
                    .append("; order=").append(System.getProperty("leggings.test.iafOrder")).append('\n');
            var ordinary = pair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS));
            if (VARIANT.equals("absent")) {
                require(!ModList.get().isLoaded("iceandfire"), "Absence graph loaded native mod");
                require(MixedLeggingsStructure.validData(ordinary.getFirst()).isPresent(), "Ordinary composition failed");
                REPORT.append("Quiet absence and ordinary composition\n");
            } else {
                priorities();
                damage(server.overworld());
                tide(server);
            }
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "spike.txt"), REPORT);
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

    private static void priorities() throws Exception {
        var type = Class.forName("com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem");
        int expected = System.getProperty("leggings.test.iafOrder").equals("before") ? 1100 : 900;
        boolean found = false;
        for (var method : type.getDeclaredMethods()) {
            if (!method.getName().contains("iafSpike$tide")) { continue; }
            for (var annotation : method.getDeclaredAnnotations()) {
                if (annotation.annotationType().getName().endsWith(".MixinMerged")) {
                    require((int) annotation.annotationType().getMethod("priority").invoke(annotation) == expected, "Unexpected actual candidate priority");
                    found = true;
                }
            }
        }
        require(found, "Loaded candidate result hook missing");
        REPORT.append("Actual merged Tide priority=").append(expected).append('\n');
    }

    private static void damage(ServerLevel level) throws Exception {
        var registry = level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        var arrow = new DamageSource(registry.getOrThrow(DamageTypes.ARROW));
        var dragons = List.of("dragon_fire", "dragon_ice", "dragon_lightning").stream().map(path -> new DamageSource(registry.getOrThrow(
                ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.parse("iceandfire:" + path))))).toList();
        var originals = BuiltInRegistries.ITEM.stream().filter(item -> {
            var id = BuiltInRegistries.ITEM.getKey(item);
            return id.getNamespace().equals("iceandfire") && id.getPath().endsWith("_leggings");
        }).toList();
        require(originals.size() == 31, "Native leggings inventory changed");
        for (var item : originals) {
            var nativeLegs = new ItemStack(item);
            String name = item.getClass().getSimpleName();
            boolean dragon = name.equals("DragonScaleArmorItem") || name.equals("DragonSteelArmorItem");
            boolean troll = name.equals("TrollArmorItem");
            var alternatives = new ArrayList<ItemStack>();
            alternatives.add(nativeLegs);
            alternatives.addAll(pair(nativeLegs, new ItemStack(Items.DIAMOND_LEGGINGS)));
            alternatives.add(pair(nativeLegs, nativeLegs).getFirst());
            for (int index = 0; index < alternatives.size(); index++) {
                boolean mixed = index > 0;
                for (var source : dragons) {
                    hit(level, source, alternatives.get(index).copy(), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                            dragon && (!mixed || !VARIANT.equals("damage")) ? .8F : 1F);
                }
                hit(level, arrow, alternatives.get(index).copy(), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                        troll && !VARIANT.equals("damage") ? .8F : 1F);
            }
        }
        for (var legs : pair(nativeItem("forest_troll_leather_leggings"), nativeItem("armor_red_leggings"))) {
            hit(level, arrow, legs.copy(), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, VARIANT.equals("damage") ? 1F : .8F);
            hit(level, dragons.getFirst(), legs.copy(), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, VARIANT.equals("damage") ? 1F : .8F);
        }
        var empty = new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID));
        hit(level, dragons.getFirst(), empty, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, 1F);
        for (var legs : pair(nativeItem("dragonsteel_lightning_leggings"), new ItemStack(Items.IRON_LEGGINGS))) {
            var feet = BOOTS ? bootsPair(nativeItem("dragonsteel_lightning_boots"), new ItemStack(Items.IRON_BOOTS))
                    : List.of(nativeItem("dragonsteel_lightning_boots"));
            for (var foot : feet) {
                for (int mask = 0; mask < 4; mask++) {
                    hit(level, level.damageSources().lightningBolt(), legs.copy(), foot.copy(),
                            (mask & 1) != 0 ? nativeItem("dragonsteel_lightning_helmet") : ItemStack.EMPTY,
                            (mask & 2) != 0 ? nativeItem("dragonsteel_lightning_chestplate") : ItemStack.EMPTY,
                            mask == 3 && !VARIANT.equals("lightning") ? 0F : 1F);
                }
                hit(level, dragons.getFirst(), legs.copy(), foot.copy(), ItemStack.EMPTY, ItemStack.EMPTY,
                        VARIANT.equals("damage") ? .9F : .7F);
            }
        }
        // Native positive for the deliberately changed Lightning operand remains upstream-owned.
        hit(level, level.damageSources().lightningBolt(), nativeItem(VARIANT.equals("lightning")
                ? "dragonsteel_fire_leggings" : "dragonsteel_lightning_leggings"), nativeItem("dragonsteel_lightning_boots"),
                nativeItem("dragonsteel_lightning_helmet"), nativeItem("dragonsteel_lightning_chestplate"), 0F);
        REPORT.append("Real hurtServer damage cases=").append(damageCases)
                .append("; 31 variants; native/both orientations/duplicates; three breaths/projectiles; Lightning sets; local operand refusal; no synthetic event posting\n");
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
        damageCases++;
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
        scenario(subject, control, pair(nativeItem("tide_red_leggings"), nativeItem("tide_blue_leggings")).getFirst(), true, false);
        clear(subject);
        clear(control);
        subject.getInventory().setItem(0, pair(nativeItem("tide_red_leggings"), new ItemStack(Items.IRON_LEGGINGS)).getFirst());
        compare(subject, control, true, 0);
        require(!subject.hasEffect(MobEffects.WATER_BREATHING), "Inventory-only root activated Tide");
        subject.getInventory().setItem(0, ItemStack.EMPTY);
        subject.setItemSlot(EquipmentSlot.FEET, pair(nativeItem("tide_red_leggings"), new ItemStack(Items.IRON_LEGGINGS)).getFirst());
        compare(subject, control, true, 0);
        require(!subject.hasEffect(MobEffects.WATER_BREATHING), "Wrong-slot root activated Tide");
        if (BOOTS) {
            subject.setItemSlot(EquipmentSlot.FEET, bootsPair(nativeItem("tide_blue_boots"), new ItemStack(Items.IRON_BOOTS)).getFirst());
            var api = Class.forName("games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireTideCompatibility");
            require(Boolean.TRUE.equals(api.getMethod("recognizes", net.minecraft.world.entity.Entity.class, EquipmentSlot.class)
                    .invoke(null, subject, EquipmentSlot.FEET)), "Boots final Tide readiness declined the LEGS peer");
            var receiver = nativeItem("tide_blue_boots").getItem();
            for (String contract : List.of("IceAndFireTideReadiness", "IceAndFireTideReadiness$Base")) {
                var type = Class.forName("games.alejandrocoria.mismatchedboots.compatibility.iceandfire." + contract);
                String method = contract.endsWith("$Base") ? "mismatchedboots$tideBaseReady" : "mismatchedboots$tideEntryReady";
                require(Boolean.TRUE.equals(type.getMethod(method).invoke(receiver)), "Boots final entry/base proof false");
            }
        }
        REPORT.append("Tide real player doTick steps=").append(tideSteps)
                .append("; actual immersion/dry transitions; exact native addEffect trace and full hidden-effect codec state; one LEGS activation per tick; original/mixed neighbors; stronger effects; removal/expiry; unchanged roots; Boots final entry/base readiness retained\n");
    }

    private static void scenario(ServerPlayer subject, ServerPlayer control, ItemStack legs, boolean wet, boolean neighbors) throws Exception {
        clear(subject);
        clear(control);
        subject.setItemSlot(EquipmentSlot.LEGS, legs.copy());
        control.setItemSlot(EquipmentSlot.LEGS, nativeItem("tide_red_leggings"));
        if (neighbors) {
            for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST)) {
                String path = slot == EquipmentSlot.HEAD ? "tide_teal_helmet" : "tide_green_chestplate";
                subject.setItemSlot(slot, nativeItem(path));
                control.setItemSlot(slot, nativeItem(path));
            }
            subject.setItemSlot(EquipmentSlot.FEET, BOOTS ? bootsPair(nativeItem("tide_blue_boots"), new ItemStack(Items.IRON_BOOTS)).getLast() : nativeItem("tide_blue_boots"));
            control.setItemSlot(EquipmentSlot.FEET, nativeItem("tide_blue_boots"));
        } else if (BOOTS) {
            subject.setItemSlot(EquipmentSlot.FEET, bootsPair(nativeItem("tide_blue_boots"), new ItemStack(Items.IRON_BOOTS)).getFirst());
            control.setItemSlot(EquipmentSlot.FEET, nativeItem("tide_blue_boots"));
        }
        for (int tick = 0; tick < 3; tick++) { compare(subject, control, wet, 1); }
        require(subject.getEffect(MobEffects.WATER_BREATHING) != null, "No native Water Breathing positive");
        if (wet) {
            int expected = neighbors ? 3 : BOOTS ? 1 : 0;
            require(subject.getEffect(MobEffects.STRENGTH).getAmplifier() == expected, "Wrong logical Tide count");
        }
        for (var player : List.of(subject, control)) {
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 15, 5, true, true, true));
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 15, 2, true, true, true));
        }
        for (int tick = 0; tick < 20; tick++) { compare(subject, control, true, 1); }
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
        int activations = Candidate.activations;
        var actual = step(subject, wet);
        require(Candidate.activations - activations == roots, "Wrong number of own LEGS activations");
        require(expected.equals(actual), "Native addEffect trace differs: expected=" + expected + " actual=" + actual);
        for (var effect : List.of(MobEffects.WATER_BREATHING, MobEffects.STRENGTH)) {
            require(state(subject.getEffect(effect), subject.level()).equals(state(control.getEffect(effect), control.level())), "Native hidden effect state differs");
        }
        int index = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            require(subject.getItemBySlot(slot) == items.get(index) && ItemStack.matches(items.get(index), copies.get(index)), "Equipment mutated by Tide");
            index++;
        }
        tideSteps++;
    }

    private static List<String> step(ServerPlayer player, boolean wet) {
        player.setPos(wet ? 1.5 : 12.5, player.level().getMinY() + 3.5, wet ? 1.5 : 12.5);
        player.setDeltaMovement(Vec3.ZERO);
        player.setAirSupply(300);
        observed = player;
        TRACE.clear();
        player.doTick();
        observed = null;
        require(player.isInWaterOrRain() == wet, "Real immersion predicate mismatch");
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
