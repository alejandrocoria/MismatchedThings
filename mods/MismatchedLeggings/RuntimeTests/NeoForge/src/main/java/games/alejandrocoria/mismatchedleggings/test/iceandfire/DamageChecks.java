package games.alejandrocoria.mismatchedleggings.test.iceandfire;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Maintained production damage checks through real hurtServer and the native event bus. */
public final class DamageChecks {
    private static final String VARIANT = System.getProperty("leggings.test.iafDamageVariant", "normal");
    private static final boolean BOOTS = Boolean.getBoolean("leggings.test.boots");
    private static final StringBuilder REPORT = new StringBuilder();
    private static LivingEntity observed;
    private static float before;
    private static float after;
    private static int events;
    private static int damageCases;
    private static boolean finished;

    private DamageChecks() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, (LivingDamageEvent.Pre event) -> {
            if (event.getEntity() == observed) { before = event.getNewDamage(); events++; }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (LivingDamageEvent.Pre event) -> {
            if (event.getEntity() == observed) { after = event.getNewDamage(); }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer()));
    }

    private static void run(MinecraftServer server) {
        if (finished || server.getTickCount() < 20) { return; }
        finished = true;
        try {
            require(ModList.get().isLoaded("mismatchedboots") == BOOTS, "Unexpected product graph");
            REPORT.append("Production damage; graph=").append(BOOTS ? "boots" : "alone").append("; variant=").append(VARIANT)
                    .append("; order=").append(System.getProperty("leggings.test.iafDamageOrder")).append('\n');
            var ordinary = pair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS));
            if (VARIANT.equals("absent")) {
                require(!ModList.get().isLoaded("iceandfire"), "Absence graph loaded native mod");
                require(MixedLeggingsStructure.validData(ordinary.getFirst()).isPresent(), "Ordinary composition failed");
                REPORT.append("Quiet absence and ordinary composition\n");
            } else {
                damage(server.overworld());
                require(DamageControlPlugin.actualOrder != null, "Missing actual transformation order witness");
                REPORT.append(DamageControlPlugin.actualOrder).append('\n');
            }
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "damage.txt"), REPORT);
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

    private static boolean damageRefused() { return VARIANT.startsWith("damage-"); }
    private static boolean lightningRefused() { return VARIANT.startsWith("lightning-"); }

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
                            dragon && (!mixed || !damageRefused()) ? .8F : 1F);
                }
                hit(level, arrow, alternatives.get(index).copy(), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                        troll && (!mixed ? !VARIANT.equals("damage-class") : !damageRefused()) ? .8F : 1F);
            }
        }
        for (var legs : pair(nativeItem("forest_troll_leather_leggings"), nativeItem("armor_red_leggings"))) {
            hit(level, arrow, legs.copy(), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, damageRefused() ? 1F : .8F);
            hit(level, dragons.getFirst(), legs.copy(), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, damageRefused() ? 1F : .8F);
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
                            mask == 3 && !lightningRefused() ? 0F : 1F);
                }
                hit(level, dragons.getFirst(), legs.copy(), foot.copy(), ItemStack.EMPTY, ItemStack.EMPTY,
                        damageRefused() ? .9F : .7F);
            }
        }
        var root = pair(nativeItem("dragonsteel_lightning_leggings"), nativeItem("forest_troll_leather_leggings")).getFirst();
        var foreign = new ItemStack(Items.IRON_LEGGINGS);
        foreign.set(LeggingsContent.mixedLeggingsDataType(), root.get(LeggingsContent.mixedLeggingsDataType()));
        var malformed = root.copy();
        malformed.remove(net.minecraft.core.component.DataComponents.EQUIPPABLE);
        var stacked = root.copyWithCount(2);
        var suspended = root.copy();
        suspended.set(LeggingsContent.createSuspendedDataType(),
                games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData.quarantined(
                        new net.minecraft.nbt.CompoundTag(), new net.minecraft.nbt.CompoundTag()));
        for (var invalid : List.of(foreign, malformed, stacked, suspended)) {
            require(MixedLeggingsStructure.validData(invalid).isEmpty(), "Invalid control unexpectedly valid");
            for (var source : dragons) { hit(level, source, invalid.copy(), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, 1F); }
            hit(level, arrow, invalid.copy(), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, 1F);
            hit(level, level.damageSources().lightningBolt(), invalid.copy(), nativeItem("dragonsteel_lightning_boots"),
                    nativeItem("dragonsteel_lightning_helmet"), nativeItem("dragonsteel_lightning_chestplate"), 1F);
        }
        for (String path : List.of("dragonsteel_fire_leggings", "dragonsteel_ice_leggings", "armor_red_leggings")) {
            for (var legs : pair(nativeItem(path), new ItemStack(Items.IRON_LEGGINGS))) {
                hit(level, level.damageSources().lightningBolt(), legs, nativeItem("dragonsteel_lightning_boots"),
                        nativeItem("dragonsteel_lightning_helmet"), nativeItem("dragonsteel_lightning_chestplate"), 1F);
                hit(level, dragons.get(2), legs, nativeItem("dragonsteel_lightning_boots"),
                        nativeItem("dragonsteel_lightning_helmet"), nativeItem("dragonsteel_lightning_chestplate"), damageRefused() ? .5F : .3F);
            }
        }
        // Native positive for the deliberately changed Lightning operand remains upstream-owned.
        hit(level, level.damageSources().lightningBolt(), nativeItem(VARIANT.equals("lightning-item")
                ? "dragonsteel_fire_leggings" : "dragonsteel_lightning_leggings"), nativeItem("dragonsteel_lightning_boots"),
                nativeItem("dragonsteel_lightning_helmet"), nativeItem("dragonsteel_lightning_chestplate"), 0F);
        if (BOOTS) {
            for (var feet : bootsPair(nativeItem("dragonsteel_lightning_boots"), new ItemStack(Items.IRON_BOOTS))) {
                hit(level, level.damageSources().lightningBolt(), nativeItem(VARIANT.equals("lightning-item")
                        ? "dragonsteel_fire_leggings" : "dragonsteel_lightning_leggings"), feet,
                        nativeItem("dragonsteel_lightning_helmet"), nativeItem("dragonsteel_lightning_chestplate"), 0F);
            }
            for (var feet : bootsPair(nativeItem("forest_troll_leather_boots"), new ItemStack(Items.IRON_BOOTS))) {
                hit(level, arrow, nativeItem("forest_troll_leather_leggings"), feet, ItemStack.EMPTY, ItemStack.EMPTY,
                        VARIANT.equals("damage-class") ? .9F : .7F);
                for (var legs : pair(nativeItem("forest_troll_leather_leggings"), new ItemStack(Items.IRON_LEGGINGS))) {
                    hit(level, arrow, legs, feet.copy(), ItemStack.EMPTY, ItemStack.EMPTY, damageRefused() ? .9F : .7F);
                }
            }
            var receiver = nativeItem("tide_blue_boots").getItem();
            for (String contract : List.of("IceAndFireTideReadiness", "IceAndFireTideReadiness$Base")) {
                var api = Class.forName("games.alejandrocoria.mismatchedboots.compatibility.iceandfire." + contract);
                require(Boolean.TRUE.equals(api.getMethod(contract.endsWith("$Base")
                        ? "mismatchedboots$tideBaseReady" : "mismatchedboots$tideEntryReady").invoke(receiver)), "Boots final Tide readiness lost");
            }
        }
        REPORT.append("Real hurtServer damage cases=").append(damageCases)
                .append("; 31 variants; native/both orientations/duplicates; three breaths/projectiles; Lightning sets; independent local refusal; no synthetic event posting\n");
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
