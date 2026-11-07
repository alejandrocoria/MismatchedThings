package games.alejandrocoria.mismatchedleggings.spike.aether;

import com.aetherteam.aetherii.item.components.AetherIIDataComponents;
import com.aetherteam.aetherii.item.components.Charms;
import com.aetherteam.aetherii.item.components.ReinforcementTier;
import com.aetherteam.aetherii.item.equipment.EquipmentUtil;
import com.aetherteam.aetherii.item.equipment.armor.abilities.BurrukaiPlateArmor;
import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedleggings.component.ComponentRuleSet;
import games.alejandrocoria.mismatchedleggings.component.ComponentRules;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentPolicy;
import games.alejandrocoria.mismatchedthings.api.composition.ComponentRule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native controls and deliberately temporary candidates. No production defaults are changed. */
public final class AetherSpike {
    public static final boolean CANDIDATE = !System.getProperty("leggings.test.aetherMode", "baseline").equals("baseline");
    public static final List<String> FAMILIES = List.of("beast_pelt", "burrukai_plate", "zanite", "arkenium", "gravitite", "neptune");
    private static boolean finished;
    private static final StringBuilder REPORT = new StringBuilder();

    private AetherSpike() {}

    public static void register(IEventBus bus) {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
        if (FMLEnvironment.getDist().isClient()) {
            bus.addListener(AetherSpikeClient::setup);
        }
    }

    private static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer() || server.getTickCount() < 20) {
            return;
        }
        finished = true;
        try {
            REPORT.append("mode=").append(CANDIDATE ? "candidate" : "baseline")
                    .append("; Boots=").append(ModList.get().isLoaded("mismatchedboots")).append('\n');
            AetherOrderProof.verify();
            sets(server);
            charms();
            corrosion(server);
            var frozen = pair(item("beast_pelt_leggings"), new ItemStack(Items.IRON_LEGGINGS));
            require(frozen.stream().allMatch(stack -> stack.is(ItemTags.FREEZE_IMMUNE_WEARABLES)), "Existing freeze tag route failed");
            Files.writeString(report("aether-server.txt"), REPORT + "Existing Beast Pelt freeze immunity passed\n");
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

    private static void sets(MinecraftServer server) throws Exception {
        var wearer = player(server.overworld());
        for (String family : FAMILIES) {
            clear(wearer);
            var source = item(family + "_leggings");
            var set = source.get(AetherIIDataComponents.ARMOR_SET);
            wearer.setItemSlot(EquipmentSlot.HEAD, item(family + "_helmet"));
            wearer.setItemSlot(EquipmentSlot.CHEST, item(family + "_chestplate"));
            wearer.setItemSlot(EquipmentSlot.LEGS, source);
            require(EquipmentUtil.getArmorCount(wearer, set) == 3 && EquipmentUtil.hasArmorAbility(wearer, set), "Native set control: " + family);
            var roots = pair(source, new ItemStack(Items.IRON_LEGGINGS));
            for (int side = 0; side < 2; side++) {
                wearer.setItemSlot(EquipmentSlot.LEGS, roots.get(side));
                int expected = CANDIDATE || side == 0 ? 3 : 2;
                require(EquipmentUtil.getArmorCount(wearer, set) == expected, "Set orientation count: " + family + "/" + side);
                require(EquipmentUtil.hasArmorAbility(wearer, set) == (expected >= 3), "Set threshold: " + family);
            }
            wearer.setItemSlot(EquipmentSlot.LEGS, pair(source, source).getFirst());
            require(EquipmentUtil.getArmorCount(wearer, set) == 3, "Duplicate source counted twice: " + family);
            wearer.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            require(!EquipmentUtil.hasArmorAbility(wearer, set), "Set removal stayed active: " + family);

            Class<?> slots = Class.forName("com.aetherteam.aetherii.inventory.container.AccessoryContainer$SlotType");
            int index = ((int[]) slots.getMethod("getIndex").invoke(slots.getField("HANDWEAR").get(null)))[0];
            var attachment = (Supplier<?>) Class.forName("com.aetherteam.aetherii.attachment.AetherIIDataAttachments").getField("ACCESSORIES").get(null);
            // Aether intentionally ignores accessories on FakePlayer; use an ordinary native wearer.
            var gloveWearer = EntityType.ZOMBIE.create(server.overworld(), EntitySpawnReason.COMMAND);
            require(gloveWearer != null, "Missing ordinary accessory wearer");
            gloveWearer.setItemSlot(EquipmentSlot.CHEST, item(family + "_chestplate"));
            gloveWearer.setItemSlot(EquipmentSlot.LEGS, pair(source, source).getFirst());
            var accessories = (Container) gloveWearer.getClass().getMethod("getData", Supplier.class).invoke(gloveWearer, attachment);
            accessories.setItem(index, item(family + "_gloves"));
            require(EquipmentUtil.getArmorCount(gloveWearer, set) == 3, "Native gloves stopped counting: " + family);
            accessories.clearContent();
        }

        clear(wearer);
        var first = item("burrukai_plate_leggings");
        var second = item("zanite_leggings");
        var dual = pair(first, second);
        for (int side = 0; side < 2; side++) {
            wearer.setItemSlot(EquipmentSlot.LEGS, dual.get(side));
            for (int family = 0; family < 2; family++) {
                var set = (family == 0 ? first : second).get(AetherIIDataComponents.ARMOR_SET);
                require(EquipmentUtil.getArmorCount(wearer, set) == (CANDIDATE || side == family ? 1 : 0), "Unequal set recognition");
            }
        }

        wearer.setItemSlot(EquipmentSlot.HEAD, item("burrukai_plate_helmet"));
        wearer.setItemSlot(EquipmentSlot.CHEST, item("burrukai_plate_chestplate"));
        wearer.setItemSlot(EquipmentSlot.LEGS, dual.get(1));
        BurrukaiPlateArmor.updatePlayerAttributes(new PlayerTickEvent.Pre(wearer));
        require(Math.abs(wearer.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - (CANDIDATE ? .2 : 0)) < 1e-6,
                "Native Burrukai transient effect not driven by count");
        wearer.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        BurrukaiPlateArmor.updatePlayerAttributes(new PlayerTickEvent.Pre(wearer));
        require(wearer.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) == 0, "Native set effect did not clear");

        var bare = new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID));
        clear(wearer);
        wearer.setItemSlot(EquipmentSlot.LEGS, bare);
        require(EquipmentUtil.getArmorCount(wearer, first.get(AetherIIDataComponents.ARMOR_SET)) == 0, "Invalid pair gained a set");
        if (ModList.get().isLoaded("mismatchedboots")) {
            var boots = bootsPair(item("burrukai_plate_boots"), new ItemStack(Items.IRON_BOOTS));
            var legs = pair(first, new ItemStack(Items.IRON_LEGGINGS));
            for (int leg = 0; leg < 2; leg++) {
                for (var foot : boots) {
                    wearer.setItemSlot(EquipmentSlot.HEAD, item("burrukai_plate_helmet"));
                    wearer.setItemSlot(EquipmentSlot.LEGS, legs.get(leg));
                    wearer.setItemSlot(EquipmentSlot.FEET, foot);
                    require(EquipmentUtil.getArmorCount(wearer, first.get(AetherIIDataComponents.ARMOR_SET)) == (CANDIDATE || leg == 0 ? 3 : 2),
                            "Mixed LEGS/FEET coexistence count changed");
                }
            }
        }
        REPORT.append("Six native sets: 3/3, 2/3, both orientations, duplicate/unequal sources, glove and invalid controls; native Burrukai activation/removal; optional mixed LEGS+FEET\n");
        clear(wearer);
    }

    private static void charms() {
        var source = charmed();
        var before = source.copy();
        require(Math.abs(health(source) - 2) < 1e-6, "Native Health charm did not add two health");
        var roots = pair(source, new ItemStack(Items.IRON_LEGGINGS));
        require(Math.abs(health(roots.get(0)) - (CANDIDATE ? 1 : 3)) < 1e-6, "Owner charm total changed");
        require(Math.abs(health(roots.get(1)) - 1) < 1e-6, "Inverse charm total changed");
        for (var root : roots) {
            require(root.has(AetherIIDataComponents.CHARMS) == (!CANDIDATE && root == roots.getFirst()), "Unexpected root holders");
            var data = MixedLeggingsStructure.validData(root).orElseThrow();
            require(ItemStack.matches(source, data.left().source()) || ItemStack.matches(source, data.right().source()), "Installed charm history lost");
        }
        for (var root : pair(source, source)) {
            require(Math.abs(health(root) - (CANDIDATE ? 2 : 4)) < 1e-6, "Two-source Health composition changed");
        }
        require(ItemStack.matches(before, source), "Composition mutated native source charm holders");
        REPORT.append("Health charm native +2; mixed owner/inverse=").append(health(roots.get(0))).append('/').append(health(roots.get(1)))
                .append("; duplicate-source, holder presence and immutable history controls\n");
    }

    private static void corrosion(MinecraftServer server) throws Exception {
        for (String family : List.of("arkenium", "beast_pelt")) {
            for (boolean reinforced : List.of(false, true)) {
                var nativeLegs = item(family + "_leggings");
                if (reinforced) { nativeLegs.set(AetherIIDataComponents.REINFORCEMENT_TIER, ReinforcementTier.FIRST); }
                var roots = pair(nativeLegs, new ItemStack(Items.IRON_LEGGINGS));
                contactCases(server, nativeLegs, family.equals("arkenium") || reinforced, reinforced);
                for (int side = 0; side < 2; side++) {
                    boolean tier = reinforced && (CANDIDATE || side == 0);
                    boolean item = tier || CANDIDATE && family.equals("arkenium");
                    contactCases(server, roots.get(side), item, tier);
                }
            }
        }
        if (ModList.get().isLoaded("mismatchedboots")) {
            var reinforced = item("beast_pelt_boots");
            reinforced.set(AetherIIDataComponents.REINFORCEMENT_TIER, ReinforcementTier.FIRST);
            for (var root : bootsPair(reinforced, new ItemStack(Items.IRON_BOOTS))) {
                contactCases(server, root, true, true);
            }
        }
        var tiered = item("beast_pelt_leggings");
        tiered.set(AetherIIDataComponents.REINFORCEMENT_TIER, ReinforcementTier.FIRST);
        var invalid = pair(tiered, new ItemStack(Items.IRON_LEGGINGS)).get(1).copy();
        invalid.remove(LeggingsContent.mixedLeggingsDataType());
        contactCases(server, invalid, false, false);
        REPORT.append("Native actual fluid contact: dropped lifespan, both held hands and worn first/subsequent hit; Arkenium/base, Beast Pelt/base, tier-I controls, both outputs, optional Boots regression\n");
    }

    private static void contactCases(MinecraftServer server, ItemStack input, boolean protectedItem, boolean protectedArmor) throws Exception {
        var level = server.overworld();
        var dropped = new ItemEntity(level, 0, 80, 0, input.copy());
        dropped.lifespan = 6000;
        contact(server, dropped);
        contact(server, dropped);
        require(dropped.lifespan == (protectedItem ? 6000 : 5970), "Dropped protection: " + input + " life=" + dropped.lifespan);
        require(ItemStack.matches(input, dropped.getItem()), "Fluid rewrote dropped history");
        dropped.discard();

        var subject = player(level);
        var main = input.copy();
        var off = input.copy();
        subject.setItemSlot(EquipmentSlot.MAINHAND, main);
        subject.setItemSlot(EquipmentSlot.OFFHAND, off);
        subject.tickCount = 20;
        contact(server, subject);
        require(main.getDamageValue() == (protectedItem ? 0 : 1) && off.getDamageValue() == (protectedItem ? 0 : 1), "Held protection: " + input);

        subject = player(level);
        var worn = input.copy();
        subject.setItemSlot(input.get(DataComponents.EQUIPPABLE).slot(), worn);
        subject.tickCount = 20;
        contact(server, subject);
        int first = worn.getDamageValue();
        subject.invulnerableTime = 0;
        subject.tickCount = 40;
        contact(server, subject);
        require(first == 1 && worn.getDamageValue() - first == (protectedArmor ? 0 : 1), "Native worn protection/timing: " + input);
        require(subject.getHealth() < subject.getMaxHealth(), "Equipment protection granted player immunity");
    }

    private static void contact(MinecraftServer server, Entity entity) throws Exception {
        var state = BuiltInRegistries.BLOCK.getValue(Identifier.parse("aether_ii:alkahest")).defaultBlockState();
        var fluid = state.getFluidState().getType();
        fluid.getClass().getMethod("entityInside", BlockState.class, ServerLevel.class, BlockPos.class, Entity.class)
                .invoke(fluid, state, server.overworld(), new BlockPos(0, 80, 0), entity);
    }

    public static ItemStack charmed() {
        var source = item("neptune_leggings");
        source.set(AetherIIDataComponents.CHARMS, new Charms(new Charms.CharmHolder(Charms.Type.ARMOR, Charms.Tier.TWO,
                item("charm_of_health_1")), new Charms.CharmHolder(Charms.Type.ARMOR, Charms.Tier.TWO)));
        return source;
    }

    private static double health(ItemStack stack) {
        return stack.getAttributeModifiers().modifiers().stream().filter(entry -> entry.attribute().equals(Attributes.MAX_HEALTH))
                .mapToDouble(entry -> entry.modifier().amount()).sum();
    }

    public static List<ItemStack> pair(ItemStack left, ItemStack right) {
        var rules = CompositionRules.defaults();
        if (CANDIDATE) {
            var declarations = new LinkedHashMap<>(rules.components().declarations());
            declarations.put(Identifier.parse("aether_ii:charms"), ComponentRule.of(ComponentPolicy.OMIT));
            rules = new CompositionRules(rules.attributes(), new ComponentRules(rules.components().durability(),
                    rules.components().repairCost(), new ComponentRuleSet(declarations)));
        }
        var pair = MixedLeggingsFactory.createPair(left, right, rules);
        return List.of(pair.primary(), pair.inverse());
    }

    private static List<ItemStack> bootsPair(ItemStack left, ItemStack right) throws Exception {
        var pair = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory")
                .getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, left, right);
        return List.of((ItemStack) pair.getClass().getMethod("primary").invoke(pair), (ItemStack) pair.getClass().getMethod("inverse").invoke(pair));
    }

    public static ItemStack item(String path) {
        var stack = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("aether_ii", path)));
        require(!stack.isEmpty(), "Missing native item: " + path);
        return stack;
    }

    private static FakePlayer player(ServerLevel level) {
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "AetherLegs"));
        player.setGameMode(GameType.SURVIVAL);
        player.setInvulnerable(false);
        player.setPos(0, 80, 0);
        player.connection.markClientLoaded();
        return player;
    }

    private static void clear(FakePlayer player) {
        for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
    }

    public static Path report(String name) {
        return Path.of(System.getProperty("leggings.test.report"), name);
    }
}
