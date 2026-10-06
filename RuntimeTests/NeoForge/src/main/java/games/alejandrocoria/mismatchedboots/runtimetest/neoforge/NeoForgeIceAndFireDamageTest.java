package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Opt-in, pinned real-mod gate. No synthetic event posting or replacement damage listener. */
public final class NeoForgeIceAndFireDamageTest {
    private static LivingEntity subject;
    private static float damageBefore;
    private static float damageAfter;
    private static int preEvents;
    private static int damageCases;
    private static boolean finished;
    private static CompletableFuture<Void> overlapReload;

    private NeoForgeIceAndFireDamageTest() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, NeoForgeIceAndFireDamageTest::beforeDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, NeoForgeIceAndFireDamageTest::afterDamage);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void beforeDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() == subject) {
            damageBefore = event.getNewDamage();
            preEvents++;
        }
    }

    private static void afterDamage(LivingDamageEvent.Pre event) {
        if (event.getEntity() == subject) {
            damageAfter = event.getNewDamage();
        }
    }

    private static void tick(MinecraftServer server) {
        if (finished) {
            return;
        }
        try {
            if (IceAndFireCoexistenceFixture.CASE.equals("incompatible")) {
                verifyIncompatibleConsumer(server.overworld());
                NetworkAcceptanceFiles.write("ice-and-fire-damage-report.txt",
                        "coexistence=incompatible; " + damageCases + " real hurtServer cases; "
                                + "damage adapter inert; native other slots and independent Lightning adapter preserved\n");
                NetworkAcceptanceFiles.write("ice-and-fire-damage-success.txt", "neoforge\n");
                finished = true;
                return;
            }
            if (Boolean.getBoolean("mismatchedboots.runtimetest.tideDamageSmoke")) {
                verifyTideCoexistence(server.overworld());
                NetworkAcceptanceFiles.write("ice-and-fire-damage-report.txt",
                        damageCases + " real hurtServer cases; Tide coexistence smoke: dragon/Troll/Lightning preserved\n");
                NetworkAcceptanceFiles.write("ice-and-fire-damage-success.txt", "neoforge\n");
                finished = true;
                return;
            }
            if (overlapReload == null) {
                verifyMatrix(server.overworld());
                overlapReload = prepareOverlapPack(server);
                return; // Reload asynchronously; never block the server tick waiting for itself.
            }
            if (!overlapReload.isDone()) {
                return;
            }
            overlapReload.join();
            verifyOverlappingBranches(server.overworld());
            if (!IceAndFireCoexistenceFixture.CASE.isEmpty()) {
                verifyCoexistence(server.overworld());
            }
            NetworkAcceptanceFiles.write("ice-and-fire-damage-report.txt",
                    "15 dragon variants; 3 Troll variants; exact Lightning set; " + damageCases + " real hurtServer cases\n"
                            + "ordinary/mixed/inverse/two-source controls; additive other-slot coefficients; "
                            + "invalid/spoofed roots; complete/missing/wrong Lightning set pieces; "
                            + "native projectile/dragon branch overlap after fixture-only datapack reload; "
                            + "unchanged equipment identity and source snapshots\n"
                            + (IceAndFireCoexistenceFixture.CASE.isEmpty() ? "" : "coexistence="
                                + IceAndFireCoexistenceFixture.CASE + "; high/low FEET wrappers="
                                + IceAndFireCoexistenceFixture.highFeet + "/" + IceAndFireCoexistenceFixture.lowFeet
                                + "; high/low Lightning wrappers=" + IceAndFireCoexistenceFixture.highLightning
                                + "/" + IceAndFireCoexistenceFixture.lowLightning
                                + "; unrelated calls=" + IceAndFireCoexistenceFixture.logCalls + "\n"));
            NetworkAcceptanceFiles.write("ice-and-fire-damage-success.txt", "neoforge\n");
            finished = true;
        } catch (Throwable throwable) {
            NetworkAcceptanceFiles.failure("ice-and-fire-damage", throwable);
            finished = true;
        } finally {
            if (finished) {
                subject = null;
                server.halt(false);
            }
        }
    }

    private static void verifyTideCoexistence(ServerLevel level) {
        for (ItemStack boots : List.of(new ItemStack(item("armor_red_boots")), mixed("armor_red_boots"))) {
            hurt(level, damage(level, "dragon_fire"), boots, 0.9F, "Tide leaves dragon protection active");
        }
        var arrow = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(net.minecraft.world.damagesource.DamageTypes.ARROW));
        for (ItemStack boots : List.of(new ItemStack(item("forest_troll_leather_boots")), mixed("forest_troll_leather_boots"))) {
            hurt(level, arrow, boots, 0.9F, "Tide leaves Troll protection active");
        }
        hurt(level, level.damageSources().lightningBolt(), mixed("dragonsteel_lightning_boots"), 0.0F,
                "Tide leaves complete Lightning set active", lightningArmor(7));
        hurt(level, level.damageSources().lightningBolt(), mixed("dragonsteel_fire_boots"), 1.0F,
                "Tide preserves incomplete Lightning set rejection", lightningArmor(7));
    }

    private static void verifyCoexistence(ServerLevel level) {
        // The fixture helmet is not an Ice and Fire item. Its own slot wrapper must contribute,
        // while our boot wrapper still contributes once, with either wrapper above/below production.
        var head = IceAndFireCoexistenceFixture.helmet(item("armor_red_helmet"));
        var pair = MixedBootsFactory.createPair(new ItemStack(item("armor_red_boots")), new ItemStack(Items.IRON_BOOTS));
        for (ItemStack feet : List.of(new ItemStack(item("armor_red_boots")), pair.primary(), pair.inverse())) {
            hurt(level, damage(level, "dragon_ice"), feet, 0.8F, "independent dragon helmet + boots",
                    List.of(head, ItemStack.EMPTY, ItemStack.EMPTY));
        }
        hurt(level, damage(level, "dragon_ice"), new ItemStack(Items.IRON_BOOTS), 0.9F,
                "fixture helmet does not also qualify FEET", List.of(head, ItemStack.EMPTY, ItemStack.EMPTY));
        hurt(level, damage(level, "dragon_ice"), pair.primary().copy(), 0.9F,
                "missing helmet sources remain negative",
                List.of(new ItemStack(Items.IRON_HELMET), ItemStack.EMPTY, ItemStack.EMPTY));
        var arrow = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(net.minecraft.world.damagesource.DamageTypes.ARROW));
        hurt(level, arrow, mixed("forest_troll_leather_boots"), 0.8F, "independent Troll helmet + boots",
                List.of(IceAndFireCoexistenceFixture.helmet(item("forest_troll_leather_helmet")),
                        ItemStack.EMPTY, ItemStack.EMPTY));
        var lightningOthers = lightningArmor(7);
        lightningOthers.set(0, IceAndFireCoexistenceFixture.helmet(item("dragonsteel_lightning_helmet")));
        var lightningPair = MixedBootsFactory.createPair(new ItemStack(item("dragonsteel_lightning_boots")),
                new ItemStack(Items.IRON_BOOTS));
        for (ItemStack feet : List.of(new ItemStack(item("dragonsteel_lightning_boots")),
                lightningPair.primary(), lightningPair.inverse())) {
            hurt(level, level.damageSources().lightningBolt(), feet, 0.0F,
                    "independent Lightning helmet + boots complete set", lightningOthers);
        }
        hurt(level, level.damageSources().lightningBolt(), new ItemStack(Items.IRON_BOOTS), 1.0F,
                "fixture Lightning helmet cannot substitute missing boots", lightningOthers);
        lightningOthers.set(0, IceAndFireCoexistenceFixture.helmet(item("dragonsteel_fire_helmet")));
        hurt(level, level.damageSources().lightningBolt(), lightningPair.primary().copy(), 1.0F,
                "wrong fixture helmet preserves exact Lightning set requirement", lightningOthers);
        require(IceAndFireCoexistenceFixture.highFeet > 0 && IceAndFireCoexistenceFixture.lowFeet > 0
                && IceAndFireCoexistenceFixture.highLightning > 0 && IceAndFireCoexistenceFixture.lowLightning > 0
                && IceAndFireCoexistenceFixture.logCalls > 0, "Competing mixins did not actually run");
    }

    private static void verifyIncompatibleConsumer(ServerLevel level) {
        hurt(level, damage(level, "dragon_fire"), new ItemStack(item("armor_red_boots")), 0.9F,
                "ordinary dragon recognition still native");
        hurt(level, damage(level, "dragon_fire"), mixed("armor_red_boots"), 1.0F,
                "late incompatible damage consumer disables mixed boot adapter");
        hurt(level, damage(level, "dragon_fire"), mixed("armor_red_boots"), 0.9F,
                "native other-slot contribution survives",
                List.of(new ItemStack(item("armor_red_helmet")), ItemStack.EMPTY, ItemStack.EMPTY));
        var arrow = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(net.minecraft.world.damagesource.DamageTypes.ARROW));
        hurt(level, arrow, mixed("forest_troll_leather_boots"), 1.0F, "changed Troll target stays inert");
        hurt(level, level.damageSources().lightningBolt(), mixed("dragonsteel_lightning_boots"), 0.0F,
                "independent Lightning integration stays active", lightningArmor(7));
        hurt(level, level.damageSources().lightningBolt(), mixed("dragonsteel_fire_boots"), 1.0F,
                "independent Lightning negative control", lightningArmor(7));
        require(IceAndFireCoexistenceFixture.highLightning > 0 && IceAndFireCoexistenceFixture.lowLightning > 0
                && IceAndFireCoexistenceFixture.logCalls > 0, "Incompatible fixture was not applied");
    }

    private static void verifyMatrix(ServerLevel level) {
        List<Item> boots = new ArrayList<>();
        for (String color : List.of("amethyst", "black", "blue", "bronze", "copper", "electric",
                "gray", "green", "red", "sapphire", "silver", "white")) {
            boots.add(item("armor_" + color + "_boots"));
        }
        for (String element : List.of("fire", "ice", "lightning")) {
            boots.add(item("dragonsteel_" + element + "_boots"));
        }
        List<DamageSource> dragonDamage = List.of(damage(level, "dragon_fire"), damage(level, "dragon_ice"),
                damage(level, "dragon_lightning"));
        for (DamageSource source : dragonDamage) {
            for (Item boot : boots) {
                String name = BuiltInRegistries.ITEM.getKey(boot).toString();
                hurt(level, source, new ItemStack(boot), 0.9F, name + " ordinary");
                var pair = MixedBootsFactory.createPair(new ItemStack(boot), new ItemStack(Items.IRON_BOOTS));
                hurt(level, source, pair.primary(), 0.9F, name + " mixed left");
                hurt(level, source, pair.inverse(), 0.9F, name + " mixed right");
                var both = MixedBootsFactory.createPair(new ItemStack(boot), new ItemStack(boot));
                hurt(level, source, both.primary(), 0.9F, name + " two identical sources");
            }
            var scaleSteel = MixedBootsFactory.createPair(new ItemStack(boots.getFirst()),
                    new ItemStack(boots.getLast()));
            hurt(level, source, scaleSteel.primary(), 0.9F, "Scale/Steel, not double-counted");
            hurt(level, source, scaleSteel.inverse(), 0.9F, "Steel/Scale, not double-counted");
            var colors = MixedBootsFactory.createPair(new ItemStack(boots.get(1)), new ItemStack(boots.get(4)));
            hurt(level, source, colors.primary(), 0.9F, "two scale colors");
            hurt(level, source, colors.inverse(), 0.9F, "two scale colors inverse");
            var elements = MixedBootsFactory.createPair(new ItemStack(item("dragonsteel_fire_boots")),
                    new ItemStack(item("dragonsteel_ice_boots")));
            hurt(level, source, elements.primary(), 0.9F, "two steel elements");
            hurt(level, source, elements.inverse(), 0.9F, "two steel elements inverse");

            hurt(level, source, new ItemStack(Items.IRON_BOOTS), 1.0F, "ordinary non-dragon");
            hurt(level, source, mixed("sheep_boots"), 1.0F, "unrelated source");
            hurt(level, source, MixedBootsFactory.createPair(new ItemStack(Items.IRON_BOOTS),
                    new ItemStack(Items.GOLDEN_BOOTS)).primary(), 1.0F, "neither source qualifies");
            hurt(level, source, new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID)),
                    1.0F, "bare root");
            ItemStack badCount = mixed("armor_red_boots");
            badCount.setCount(2);
            hurt(level, source, badCount, 1.0F, "invalid root count");
            ItemStack spoof = new ItemStack(Items.IRON_BOOTS);
            spoof.set(ModContent.mixedBootsDataType().orElseThrow(),
                    ModContent.getMixedBootsData(mixed("armor_red_boots")).orElseThrow());
            hurt(level, source, spoof, 1.0F, "foreign item carrying mixed data");

            for (int mask = 0; mask < 8; mask++) {
                float coefficient = 0.9F;
                List<ItemStack> otherArmor = new ArrayList<>();
                for (int bit = 0; bit < 3; bit++) {
                    if ((mask & (1 << bit)) != 0) {
                        coefficient -= new float[]{0.1F, 0.3F, 0.2F}[bit];
                        otherArmor.add(new ItemStack(item(new String[]{"armor_blue_helmet",
                                "dragonsteel_fire_chestplate", "armor_green_leggings"}[bit])));
                    } else {
                        otherArmor.add(ItemStack.EMPTY);
                    }
                }
                hurt(level, source, mixed("dragonsteel_ice_boots"), coefficient,
                        "native additive armor mask=" + mask, otherArmor);
            }
            // Valid data in another slot must not make its native class predicate recognize a dragon item.
            hurt(level, source, new ItemStack(Items.IRON_BOOTS), 1.0F, "mixed item forced into HEAD",
                    List.of(mixed("armor_red_boots"), ItemStack.EMPTY, ItemStack.EMPTY));
        }
        hurt(level, level.damageSources().generic(), mixed("armor_red_boots"), 1.0F, "non-dragon damage");
        hurt(level, level.damageSources().lightningBolt(), mixed("dragonsteel_lightning_boots"), 1.0F,
                "Lightning source alone grants no set immunity");
        hurt(level, level.damageSources().lightningBolt(), mixed("dragonsteel_lightning_boots"), 0.0F,
                "Lightning mixed boots complete the exact set",
                List.of(new ItemStack(item("dragonsteel_lightning_helmet")),
                        new ItemStack(item("dragonsteel_lightning_chestplate")),
                        new ItemStack(item("dragonsteel_lightning_leggings"))));
        verifyTroll(level);
        verifyLightningSet(level);
    }

    private static void verifyTroll(ServerLevel level) {
        DamageSource arrow = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(net.minecraft.world.damagesource.DamageTypes.ARROW));
        require(arrow.is(DamageTypeTags.IS_PROJECTILE), "Native arrow is not a projectile");
        for (String variant : List.of("forest", "frost", "mountain")) {
            Item boot = item(variant + "_troll_leather_boots");
            var pair = MixedBootsFactory.createPair(new ItemStack(boot), new ItemStack(Items.IRON_BOOTS));
            hurt(level, arrow, new ItemStack(boot), 0.9F, variant + " ordinary Troll");
            hurt(level, arrow, pair.primary(), 0.9F, variant + " mixed Troll left");
            hurt(level, arrow, pair.inverse(), 0.9F, variant + " mixed Troll right");
            hurt(level, arrow, MixedBootsFactory.createPair(new ItemStack(boot), new ItemStack(boot)).primary(),
                    0.9F, variant + " two Troll sources count once");
        }
        var colors = MixedBootsFactory.createPair(new ItemStack(item("forest_troll_leather_boots")),
                new ItemStack(item("frost_troll_leather_boots")));
        hurt(level, arrow, colors.primary(), 0.9F, "two different Troll variants");
        hurt(level, arrow, colors.inverse(), 0.9F, "two different Troll variants inverse");
        for (int mask = 0; mask < 8; mask++) {
            List<ItemStack> others = trollArmor(mask);
            float coefficient = coefficient(mask);
            hurt(level, arrow, mixed("forest_troll_leather_boots"), coefficient,
                    "native additive Troll armor mask=" + mask, others);
            hurt(level, arrow, new ItemStack(item("forest_troll_leather_boots")), coefficient,
                    "ordinary additive Troll control mask=" + mask, others);
        }
        hurt(level, level.damageSources().generic(), mixed("forest_troll_leather_boots"), 1.0F, "Troll non-projectile");
        hurt(level, damage(level, "dragon_fire"), mixed("forest_troll_leather_boots"), 1.0F, "Troll non-projectile breath");
        hurt(level, arrow, mixed("armor_red_boots"), 1.0F, "Dragon source is not Troll");
        hurt(level, arrow, new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID)),
                1.0F, "Troll bare root");
        ItemStack badCount = mixed("forest_troll_leather_boots");
        badCount.setCount(2);
        hurt(level, arrow, badCount, 1.0F, "Troll invalid count");
        hurt(level, arrow, spoof("forest_troll_leather_boots"), 1.0F, "Troll foreign root");
        hurt(level, arrow, new ItemStack(Items.IRON_BOOTS), 1.0F, "Troll mixed forced into HEAD",
                List.of(mixed("forest_troll_leather_boots"), ItemStack.EMPTY, ItemStack.EMPTY));
    }

    private static void verifyLightningSet(ServerLevel level) {
        Item boot = item("dragonsteel_lightning_boots");
        var pair = MixedBootsFactory.createPair(new ItemStack(boot), new ItemStack(Items.IRON_BOOTS));
        var both = MixedBootsFactory.createPair(new ItemStack(boot), new ItemStack(boot));
        for (int mask = 0; mask < 8; mask++) {
            List<ItemStack> others = lightningArmor(mask);
            float expected = mask == 7 ? 0.0F : 1.0F;
            hurt(level, level.damageSources().lightningBolt(), new ItemStack(boot), expected,
                    "ordinary Lightning set mask=" + mask, others);
            hurt(level, level.damageSources().lightningBolt(), pair.primary().copy(), expected,
                    "mixed Lightning set mask=" + mask, others);
            hurt(level, level.damageSources().lightningBolt(), pair.inverse().copy(), expected,
                    "inverse Lightning set mask=" + mask, others);
            hurt(level, level.damageSources().lightningBolt(), both.primary().copy(), expected,
                    "two Lightning sources set mask=" + mask, others);
        }
        for (String wrong : List.of("dragonsteel_fire_boots", "dragonsteel_ice_boots", "armor_red_boots", "sheep_boots")) {
            var wrongPair = MixedBootsFactory.createPair(new ItemStack(item(wrong)), new ItemStack(Items.IRON_BOOTS));
            hurt(level, level.damageSources().lightningBolt(), wrongPair.primary(), 1.0F,
                    "wrong Lightning feet " + wrong, lightningArmor(7));
            hurt(level, level.damageSources().lightningBolt(), wrongPair.inverse(), 1.0F,
                    "wrong Lightning feet inverse " + wrong, lightningArmor(7));
        }
        for (int slot = 0; slot < 3; slot++) {
            List<ItemStack> others = lightningArmor(7);
            others.set(slot, new ItemStack(item(new String[]{"dragonsteel_fire_helmet",
                    "dragonsteel_ice_chestplate", "armor_red_leggings"}[slot])));
            hurt(level, level.damageSources().lightningBolt(), pair.primary().copy(), 1.0F,
                    "wrong non-feet Lightning set piece " + slot, others);
            others.set(slot, pair.inverse().copy());
            hurt(level, level.damageSources().lightningBolt(), pair.primary().copy(), 1.0F,
                    "mixed Lightning forced into non-feet set slot " + slot, others);
        }
        hurt(level, level.damageSources().lightningBolt(), new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID)),
                1.0F, "Lightning bare root", lightningArmor(7));
        ItemStack badCount = mixed("dragonsteel_lightning_boots");
        badCount.setCount(2);
        hurt(level, level.damageSources().lightningBolt(), badCount, 1.0F, "Lightning invalid count", lightningArmor(7));
        hurt(level, level.damageSources().lightningBolt(), spoof("dragonsteel_lightning_boots"), 1.0F,
                "Lightning foreign root", lightningArmor(7));
        hurt(level, damage(level, "dragon_lightning"), pair.primary().copy(), 0.3F,
                "Dragon lightning is breath protection, not ordinary Lightning immunity", lightningArmor(7));
        hurt(level, level.damageSources().generic(), pair.inverse().copy(), 1.0F,
                "Lightning complete set unrelated damage", lightningArmor(7));
    }

    private static List<ItemStack> lightningArmor(int mask) {
        return armor(mask, "dragonsteel_lightning_helmet", "dragonsteel_lightning_chestplate",
                "dragonsteel_lightning_leggings");
    }

    private static List<ItemStack> trollArmor(int mask) {
        return armor(mask, "forest_troll_leather_helmet", "frost_troll_leather_chestplate", "mountain_troll_leather_leggings");
    }

    private static List<ItemStack> armor(int mask, String... names) {
        List<ItemStack> result = new ArrayList<>();
        for (int bit = 0; bit < 3; bit++) {
            result.add((mask & (1 << bit)) != 0 ? new ItemStack(item(names[bit])) : ItemStack.EMPTY);
        }
        return result;
    }

    private static float coefficient(int mask) {
        float result = 0.9F;
        for (int bit = 0; bit < 3; bit++) {
            if ((mask & (1 << bit)) != 0) {
                result -= new float[]{0.1F, 0.3F, 0.2F}[bit];
            }
        }
        return result;
    }

    private static ItemStack spoof(String name) {
        ItemStack foreign = new ItemStack(Items.IRON_BOOTS);
        foreign.set(ModContent.mixedBootsDataType().orElseThrow(),
                ModContent.getMixedBootsData(mixed(name)).orElseThrow());
        return foreign;
    }

    private static CompletableFuture<Void> prepareOverlapPack(MinecraftServer server) throws Exception {
        var pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("mismatchedboots-branch-overlap");
        var version = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.mcmeta"), """
                {"pack":{"description":"Fixture-only native damage branch overlap",
                "min_format":[%d,%d],"max_format":[%d,%d]}}
                """.formatted(version.major(), version.minor(), version.major(), version.minor()), StandardCharsets.UTF_8);
        var tag = pack.resolve("data/minecraft/tags/damage_type/is_projectile.json");
        Files.createDirectories(tag.getParent());
        Files.writeString(tag, "{\"replace\":false,\"values\":[\"iceandfire:dragon_fire\"]}", StandardCharsets.UTF_8);
        server.getPackRepository().reload();
        String id = "file/mismatchedboots-branch-overlap";
        require(server.getPackRepository().isAvailable(id), "Overlap datapack missing");
        var selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
        selected.add(id);
        return server.reloadResources(selected);
    }

    private static void verifyOverlappingBranches(ServerLevel level) {
        DamageSource overlap = damage(level, "dragon_fire");
        require(overlap.is(DamageTypeTags.IS_PROJECTILE), "Overlap datapack did not change the live damage tag");
        var pair = MixedBootsFactory.createPair(new ItemStack(item("forest_troll_leather_boots")),
                new ItemStack(item("armor_red_boots")));
        hurt(level, overlap, pair.primary(), 0.9F * 0.9F, "Troll/Dragon sequential overlap");
        hurt(level, overlap, pair.inverse(), 0.9F * 0.9F, "Dragon/Troll sequential overlap");
        hurt(level, overlap, mixed("forest_troll_leather_boots"), 0.9F, "overlap Troll only");
        hurt(level, overlap, mixed("armor_red_boots"), 0.9F, "overlap Dragon only");
        hurt(level, overlap, mixed("sheep_boots"), 1.0F, "overlap neither branch");
        // Other pieces contribute to their own branch; feet qualifies independently in both branches.
        hurt(level, overlap, pair.primary().copy(), 0.5F * 0.7F, "overlap native other armor contributions",
                List.of(new ItemStack(item("forest_troll_leather_helmet")),
                        new ItemStack(item("frost_troll_leather_chestplate")),
                        new ItemStack(item("armor_red_leggings"))));
    }

    private static void hurt(ServerLevel level, DamageSource source, ItemStack feet, float coefficient,
                             String description) {
        hurt(level, source, feet, coefficient, description,
                List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
    }

    private static void hurt(ServerLevel level, DamageSource source, ItemStack feet, float coefficient,
                             String description, List<ItemStack> otherArmor) {
        var zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        require(zombie != null, "Could not create test entity");
        zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD, otherArmor.get(0));
        zombie.setItemSlot(EquipmentSlot.CHEST, otherArmor.get(1));
        zombie.setItemSlot(EquipmentSlot.LEGS, otherArmor.get(2));
        zombie.setItemSlot(EquipmentSlot.FEET, feet);
        var data = ModContent.getMixedBootsData(feet).orElse(null);
        ItemStack leftBefore = data == null ? ItemStack.EMPTY : data.left().source();
        ItemStack rightBefore = data == null ? ItemStack.EMPTY : data.right().source();
        Item itemBefore = feet.getItem();
        float healthBefore = zombie.getHealth();
        subject = zombie;
        damageBefore = -1;
        damageAfter = -1;
        preEvents = 0;
        boolean hurt = zombie.hurtServer(level, source, 10.0F);
        require(hurt || coefficient == 0.0F, description + ": hurt rejected");
        require(preEvents == 1 && damageBefore > 0 && damageAfter >= 0, description + ": native Pre event missing");
        float expected = damageBefore * coefficient;
        near(expected, damageAfter, description + ": native damage multiplier");
        near(damageAfter, healthBefore - zombie.getHealth(), description + ": actual health loss");
        require(zombie.getItemBySlot(EquipmentSlot.FEET) == feet, description + ": equipped root replaced");
        require(feet.getItem() == itemBefore, description + ": root identity changed");
        if (data != null) {
            var after = ModContent.getMixedBootsData(feet).orElseThrow();
            require(ItemStack.matches(leftBefore, after.left().source())
                    && ItemStack.matches(rightBefore, after.right().source()), description + ": snapshots mutated");
        }
        damageCases++;
        subject = null;
        zombie.discard();
    }

    private static ItemStack mixed(String name) {
        return MixedBootsFactory.createPair(new ItemStack(item(name)), new ItemStack(Items.IRON_BOOTS)).primary();
    }

    private static Item item(String name) {
        Identifier id = Identifier.fromNamespaceAndPath("iceandfire", name);
        return BuiltInRegistries.ITEM.get(id).orElseThrow(() -> new AssertionError("Missing real item " + id)).value();
    }

    private static DamageSource damage(ServerLevel level, String name) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(
                ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath("iceandfire", name))));
    }

    private static void near(float expected, float actual, String description) {
        require(Math.abs(expected - actual) < 0.0001F, description + ": expected " + expected + ", got " + actual);
    }

    private static void require(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }
}
