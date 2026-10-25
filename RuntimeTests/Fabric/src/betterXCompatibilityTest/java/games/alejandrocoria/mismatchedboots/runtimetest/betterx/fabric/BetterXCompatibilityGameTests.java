package games.alejandrocoria.mismatchedboots.runtimetest.betterx.fabric;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelResource;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class BetterXCompatibilityGameTests {
    private static final String EMPTY_STRUCTURE = "mismatchedboots_betterx_compatibility_test:empty";
    private static final String PACK_NAME = "mismatchedboots-betterx-compatibility";
    private static final String PACK_ID = "file/" + PACK_NAME;
    private static final String EFFECT_APPLIER = "org.betterx.betterend.interfaces.MobEffectApplier";
    private static final String EFFECT_APPLIER_INTERNAL =
            "org/betterx/betterend/interfaces/MobEffectApplier";
    private static final String APPLY_EFFECT = "(Lnet/minecraft/world/entity/LivingEntity;)V";
    private static final String PIGLIN_RULE_PROPERTY =
            "mismatchedboots.test.betterNether.enabled";
    private static final String DEFAULT_CONFIG_DESCRIPTOR =
            "Lorg/betterx/betternether/config/DefaultWorldConfig;";
    private static final String BOOLEAN_VALUE_DESCRIPTOR =
            "Lde/ambertation/wunderlib/configs/AbstractConfig$BooleanValue;";
    private static final String NETHER_SET_DESCRIPTOR =
            "Lorg/betterx/betternether/items/complex/NetherSet;";
    private static final String EQUIPMENT_SET_INTERNAL =
            "de/ambertation/wover/complex/api/equipment/EquipmentSet";
    private static final String ARMOR_PIECES_DESCRIPTOR =
            "()[Lnet/minecraft/world/item/Item;";
    private static final String UPSTREAM_HANDLER_DESCRIPTOR =
            "(Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;)V";
    private static final TagKey<Item> CRYSTALITE_SET = tag("betterend", "armor_set/crystalite");
    private static final TagKey<Item> UNAUDITED_BETTER_END = tag("betterend", "unaudited_control");
    private static final ResourceKey<Enchantment> RUBY_FIRE = ResourceKey.create(
            Registries.ENCHANTMENT, Identifier.fromNamespaceAndPath("betternether", "ruby_fire"));

    @GameTest(structure = EMPTY_STRUCTURE)
    public void betterEndGuardAcceptsOnlyTheExactExpectedApi(GameTestHelper helper) {
        ClassNode effectApplier = effectApplierNode();
        ClassNode crystaliteBoots = crystaliteBootsNode();
        require(guardAccepts(effectApplier, crystaliteBoots),
                "Exact Better End API shape was rejected");
        require(!guardAccepts(null, crystaliteBoots),
                "Missing effect interface was accepted");
        require(!guardAccepts(effectApplier, null),
                "Missing Crystalite Boots class was accepted");

        ClassNode wrongDescriptor = effectApplierNode();
        wrongDescriptor.methods.getFirst().desc = "()V";
        require(!guardAccepts(wrongDescriptor, crystaliteBootsNode()),
                "Wrong applyEffect descriptor was accepted");

        ClassNode wrongBootInterface = crystaliteBootsNode();
        wrongBootInterface.interfaces.clear();
        require(!guardAccepts(effectApplierNode(), wrongBootInterface),
                "Crystalite Boots without MobEffectApplier was accepted");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void betterNetherGuardAcceptsOnlyTheExactExpectedApi(GameTestHelper helper) {
        ClassNode[] exact = betterNetherApiNodes();
        require(betterNetherGuardAccepts(exact),
                "Exact Better Nether API shape was rejected");

        ClassNode[] missingConfig = betterNetherApiNodes();
        missingConfig[0] = null;
        require(!betterNetherGuardAccepts(missingConfig),
                "Missing Better Nether config class was accepted");

        ClassNode[] missingSet = betterNetherApiNodes();
        missingSet[2].fields.removeIf(field -> "FLAMING_RUBY_SET".equals(field.name));
        require(!betterNetherGuardAccepts(missingSet),
                "Missing Better Nether equipment set was accepted");

        ClassNode[] staticAccessor = betterNetherApiNodes();
        staticAccessor[4].methods.getFirst().access |= Opcodes.ACC_STATIC;
        require(!betterNetherGuardAccepts(staticAccessor),
                "Static armor-piece accessor was accepted");

        ClassNode[] incompatibleMixin = betterNetherApiNodes();
        incompatibleMixin[5].methods.getFirst().visibleAnnotations = null;
        require(!betterNetherGuardAccepts(incompatibleMixin),
                "Better Nether mixin without its cancellable HEAD injection was accepted");

        require(guardAccepts(effectApplierNode(), crystaliteBootsNode()),
                "A rejected Better Nether shape affected the independent Better End guard");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void crystaliteSwiftnessUsesTheGuardedProductionAdapter(GameTestHelper helper) {
        ServerPlayer player = connectedMockPlayer(helper);
        Class<?> effectApplier = requiredClass(EFFECT_APPLIER);
        ItemStack crystalite = item("betterend", "crystalite_boots").getDefaultInstance();
        require(effectApplier.isInstance(crystalite.getItem()),
                "Ordinary Crystalite Boots do not implement MobEffectApplier");
        require(!effectApplier.isInstance(Items.IRON_BOOTS),
                "Unrelated iron boots unexpectedly implement MobEffectApplier");

        assertSwiftnessLifecycle(effectApplier, player, crystalite, "ordinary Crystalite");

        MixedBootsPair pair = MixedBootsFactory.createPair(
                crystalite, Items.IRON_BOOTS.getDefaultInstance());
        assertSwiftnessLifecycle(effectApplier, player, pair.primary(), "primary");
        assertSwiftnessLifecycle(effectApplier, player, pair.inverse(), "inverse");

        MixedBootsPair twoCrystalite = MixedBootsFactory.createPair(crystalite, crystalite);
        assertSwiftnessLifecycle(
                effectApplier, player, twoCrystalite.primary(), "two-source Crystalite");

        MixedBootsPair unrelated = MixedBootsFactory.createPair(
                Items.IRON_BOOTS.getDefaultInstance(), Items.GOLDEN_BOOTS.getDefaultInstance());
        assertNoSwiftness(effectApplier, player, unrelated.primary());
        assertRequiresEquippedMixedBoots(effectApplier, player, pair.primary());
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void betterNetherPiglinRuleUsesTheGuardedProductionAdapter(GameTestHelper helper) {
        boolean configured = requiredBooleanProperty(PIGLIN_RULE_PROPERTY);
        require(piglinRuleEnabled() == configured,
                "Better Nether did not load the expected piglin configuration");

        Player player = GameTestPlayers.makeMockServerPlayer(helper, GameType.SURVIVAL);
        for (String path : List.of(
                "cincinnasite_boots", "nether_ruby_boots", "flaming_ruby_boots")) {
            ItemStack ordinary = item("betternether", path).getDefaultInstance();
            player.setItemSlot(EquipmentSlot.FEET, ordinary);
            require(PiglinAi.isWearingSafeArmor(player) == configured,
                    path + " did not retain Better Nether's configured behavior");

            MixedBootsPair pair = MixedBootsFactory.createPair(
                    ordinary, Items.IRON_BOOTS.getDefaultInstance());
            assertPiglinResult(player, pair.primary(), configured, path + " primary");
            assertPiglinResult(player, pair.inverse(), configured, path + " inverse");
        }

        MixedBootsPair twoQualifying = MixedBootsFactory.createPair(
                item("betternether", "cincinnasite_boots").getDefaultInstance(),
                item("betternether", "flaming_ruby_boots").getDefaultInstance());
        assertPiglinResult(
                player, twoQualifying.primary(), configured, "two qualifying sources");

        MixedBootsPair unrelated = MixedBootsFactory.createPair(
                Items.IRON_BOOTS.getDefaultInstance(), Items.LEATHER_BOOTS.getDefaultInstance());
        assertPiglinResult(player, unrelated.primary(), false, "unrelated mixed sources");

        player.setItemSlot(EquipmentSlot.FEET, Items.GOLDEN_BOOTS.getDefaultInstance());
        require(PiglinAi.isWearingSafeArmor(player),
                "Adapter changed vanilla golden-boots safety");
        player.setItemSlot(EquipmentSlot.FEET, Items.IRON_BOOTS.getDefaultInstance());
        require(!PiglinAi.isWearingSafeArmor(player),
                "Adapter made vanilla iron boots piglin-safe");
        player.setItemSlot(
                EquipmentSlot.FEET, item("betterend", "aeternium_boots").getDefaultInstance());
        require(!PiglinAi.isWearingSafeArmor(player),
                "Adapter made unrelated Better End boots piglin-safe");

        ItemStack existing = twoQualifying.primary();
        try {
            setPiglinRule(!configured);
            require(piglinRuleEnabled() != configured,
                    "Better Nether piglin option did not change live");
            assertPiglinResult(
                    player, existing, !configured, "existing stack after live option change");
        } finally {
            setPiglinRule(configured);
        }
        require(piglinRuleEnabled() == configured,
                "Better Nether piglin option was not restored");
        assertPiglinResult(player, existing, configured, "existing stack after option restore");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void rubyFireRemainsAWorkingRootEnchantment(GameTestHelper helper) {
        Holder.Reference<Enchantment> rubyFire = helper.getLevel().registryAccess()
                                                       .lookupOrThrow(Registries.ENCHANTMENT)
                                                       .getOrThrow(RUBY_FIRE);
        ItemStack flamingRuby = betterXCustomStack(
                helper, item("betternether", "flaming_ruby_boots"));
        require(flamingRuby.getEnchantments().getLevel(rubyFire) == 1,
                "Ordinary Flaming Ruby Boots do not carry Ruby Fire I");

        MixedBootsPair pair = MixedBootsFactory.createPair(
                flamingRuby, Items.IRON_BOOTS.getDefaultInstance());
        require(pair.primary().getEnchantments().getLevel(rubyFire) == 1,
                "Primary Mismatched root did not receive Ruby Fire I");
        require(pair.inverse().getEnchantments().getLevel(rubyFire) == 1,
                "Inverse Mismatched root did not receive Ruby Fire I");

        assertRubyFireRetaliation(helper, flamingRuby.copy(), "ordinary Flaming Ruby Boots");
        assertRubyFireRetaliation(helper, pair.primary(), "primary Mismatched root");
        assertRubyFireRetaliation(helper, pair.inverse(), "inverse Mismatched root");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE, maxTicks = 1200)
    public void crystaliteProjectionIsExactAndReloadable(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Player player = GameTestPlayers.makeMockServerPlayer(helper, GameType.SURVIVAL);
        ItemStack crystalite = item("betterend", "crystalite_boots").getDefaultInstance();
        MixedBootsPair pair = MixedBootsFactory.createPair(
                crystalite, Items.IRON_BOOTS.getDefaultInstance());
        ItemStack existing = pair.primary();
        ItemStack unchangedRoot = existing.copy();
        MixedBootsData unchangedSources = mixedData(existing);

        equipCrystaliteArmor(player, crystalite);
        require(crystalite.is(CRYSTALITE_SET),
                "Ordinary Crystalite Boots are missing the exact set tag");
        require(hasFullCrystaliteSet(player),
                "Better End rejected the ordinary full Crystalite set");
        assertProjectedSet(player, pair.primary(), "primary");
        assertProjectedSet(player, pair.inverse(), "inverse");

        MixedBootsPair unrelated = MixedBootsFactory.createPair(
                Items.IRON_BOOTS.getDefaultInstance(), Items.GOLDEN_BOOTS.getDefaultInstance());
        require(!unrelated.primary().is(CRYSTALITE_SET),
                "Unrelated Mismatched sources received Crystalite set identity");
        require(crystalite.is(UNAUDITED_BETTER_END),
                "Crystalite source is missing the unaudited Better End control tag");
        require(!existing.is(UNAUDITED_BETTER_END),
                "Mismatched root projected an unaudited Better End tag");
        require(crystalite.is(ItemTags.FOOT_ARMOR),
                "Crystalite source is missing its vanilla foot-armor tag");
        require(!existing.is(ItemTags.FOOT_ARMOR),
                "Mismatched root projected an unaudited vanilla tag");

        Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve(PACK_NAME);
        try {
            writePackMetadata(pack);
            writeCrystaliteSet(pack);
            ArrayList<String> selected = selectPack(server);
            server.reloadResources(selected).join();

            require(!existing.is(CRYSTALITE_SET),
                    "Existing stack retained Crystalite identity after its source left the tag");
            player.setItemSlot(EquipmentSlot.FEET, existing);
            require(!hasFullCrystaliteSet(player),
                    "Mixed boots completed the set without a qualifying source");
            assertUnchanged(existing, unchangedRoot, unchangedSources, "Crystalite removal");

            writeCrystaliteSet(pack, "minecraft:iron_boots");
            server.reloadResources(selected).join();

            require(existing.is(CRYSTALITE_SET),
                    "Existing stack did not follow its iron source into the reloaded tag");
            require(hasFullCrystaliteSet(player),
                    "Better End rejected the reloaded qualifying mixed boots");
            MixedBootsData liveSources = mixedData(existing);
            require(!liveSources.left().source().is(CRYSTALITE_SET),
                    "Crystalite source unexpectedly remained in the replacement tag");
            require(liveSources.right().source().is(CRYSTALITE_SET),
                    "Iron source did not enter the replacement tag");
            require(!existing.is(UNAUDITED_BETTER_END),
                    "Reload caused an unaudited Better End tag to leak from a source");
            require(!existing.is(ItemTags.FOOT_ARMOR),
                    "Reload caused an unaudited vanilla tag to leak from a source");
            assertUnchanged(existing, unchangedRoot, unchangedSources, "iron replacement");
            helper.succeed();
        } catch (IOException exception) {
            throw new AssertionError("Could not prepare the Crystalite tag data pack", exception);
        }
    }

    private static void assertPiglinResult(
            Player player,
            ItemStack mixed,
            boolean expected,
            String description) {
        ItemStack unchangedRoot = mixed.copy();
        MixedBootsData unchangedSources = mixedData(mixed);
        player.setItemSlot(EquipmentSlot.FEET, mixed);
        require(PiglinAi.isWearingSafeArmor(player) == expected,
                description + " produced the wrong piglin-safe result");
        assertUnchanged(mixed, unchangedRoot, unchangedSources, description);
    }

    private static void assertRubyFireRetaliation(
            GameTestHelper helper, ItemStack boots, String description) {
        ServerPlayer wearer = connectedMockPlayer(helper);
        wearer.setItemSlot(EquipmentSlot.FEET, boots);
        ItemStack equipped = wearer.getItemBySlot(EquipmentSlot.FEET);
        MixedBootsData before = ModContent.getMixedBootsData(equipped).orElse(null);
        int initialDamage = equipped.getDamageValue();

        Zombie attacker = helper.spawn(EntityType.ZOMBIE, BlockPos.ZERO);
        float initialHealth = attacker.getHealth();
        for (int attempt = 0; attempt < 64 && !attacker.isOnFire(); attempt++) {
            EnchantmentHelper.doPostAttackEffects(
                    helper.getLevel(), wearer, helper.getLevel().damageSources().mobAttack(attacker));
        }

        require(attacker.isOnFire(), description + " never triggered Ruby Fire retaliation");
        require(attacker.getHealth() < initialHealth,
                description + " ignited without applying Ruby Fire damage");
        require(equipped.getDamageValue() > initialDamage,
                description + " triggered without damaging the enchanted boots");
        if (before != null) {
            require(before.equals(ModContent.getMixedBootsData(equipped).orElse(null)),
                    description + " mutated its stored source snapshots");
        }
        attacker.discard();
    }

    private static ItemStack betterXCustomStack(GameTestHelper helper, Item item) {
        ItemStack stack = item.getDefaultInstance();
        Method setup = null;
        for (Method method : item.getClass().getMethods()) {
            if (method.getName().equals("setupItemStack") && method.getParameterCount() == 2) {
                setup = method;
                break;
            }
        }
        require(setup != null, "BetterX item does not expose its custom-stack setup hook");
        try {
            setup.invoke(item, stack, helper.getLevel().registryAccess());
        } catch (IllegalAccessException exception) {
            throw new AssertionError("Could not access BetterX custom-stack setup", exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("BetterX custom-stack setup failed", exception.getCause());
        }
        return stack;
    }

    private static boolean piglinRuleEnabled() {
        Object value = piglinRuleValue();
        try {
            return Boolean.TRUE.equals(value.getClass().getMethod("get").invoke(value));
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError("Could not read Better Nether's piglin option", exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError(
                    "Better Nether's piglin option getter failed", exception.getCause());
        }
    }

    private static void setPiglinRule(boolean enabled) {
        Object value = piglinRuleValue();
        try {
            value.getClass().getMethod("set", Object.class).invoke(value, enabled);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError("Could not change Better Nether's piglin option", exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError(
                    "Better Nether's piglin option setter failed", exception.getCause());
        }
    }

    private static Object piglinRuleValue() {
        try {
            Class<?> configs = requiredClass("org.betterx.betternether.config.Configs");
            Object gameRules = configs.getField("GAME_RULES").get(null);
            return gameRules.getClass().getField("piglinIgnoreNetherArmor").get(gameRules);
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            throw new AssertionError("Could not resolve Better Nether's piglin option", exception);
        }
    }

    private static boolean requiredBooleanProperty(String name) {
        String value = System.getProperty(name);
        require("true".equals(value) || "false".equals(value),
                "Missing expected boolean property " + name);
        return Boolean.parseBoolean(value);
    }

    private static void assertProjectedSet(Player player, ItemStack mixed, String orientation) {
        require(mixed.is(CRYSTALITE_SET),
                orientation + " Mismatched root did not project Crystalite set identity");
        player.setItemSlot(EquipmentSlot.FEET, mixed);
        require(hasFullCrystaliteSet(player),
                orientation + " Mismatched root did not complete the Crystalite set");
    }

    private static void assertSwiftnessLifecycle(
            Class<?> effectApplier,
            ServerPlayer player,
            ItemStack stack,
            String description) {
        require(effectApplier.isInstance(stack.getItem()),
                description + " item does not implement MobEffectApplier");
        ItemStack unchangedRoot = stack.copy();
        MixedBootsData unchangedSources = ModContent.getMixedBootsData(stack).orElse(null);
        player.removeEffect(MobEffects.SPEED);
        player.setItemSlot(EquipmentSlot.FEET, stack);

        player.tickCount = 63;
        invokeApplyEffect(effectApplier, stack.getItem(), player);
        require(player.getEffect(MobEffects.SPEED) == null,
                description + " applied Speed before the 64-tick boundary");

        player.tickCount = 64;
        invokeApplyEffect(effectApplier, stack.getItem(), player);
        MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
        require(speed != null, description + " did not apply Swiftness");
        require(speed.getAmplifier() == 0, description + " applied an unexpected amplifier");
        require(speed.getDuration() == 80, description + " applied an unexpected duration");

        player.removeEffect(MobEffects.SPEED);
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 5, 0));
        player.tickCount = 65;
        invokeApplyEffect(effectApplier, stack.getItem(), player);
        require(player.getEffect(MobEffects.SPEED).getDuration() == 5,
                description + " refreshed Speed away from the 64-tick boundary");

        player.tickCount = 128;
        invokeApplyEffect(effectApplier, stack.getItem(), player);
        speed = player.getEffect(MobEffects.SPEED);
        require(speed != null && speed.getDuration() == 80,
                description + " did not refresh Speed at the next 64-tick boundary");
        require(ItemStack.matches(unchangedRoot, stack), description + " changed root item data");
        if (unchangedSources != null) {
            require(unchangedSources.equals(mixedData(stack)),
                    description + " mutated stored source snapshots");
        }
    }

    private static void assertNoSwiftness(
            Class<?> effectApplier,
            ServerPlayer player,
            ItemStack mixed) {
        ItemStack unchangedRoot = mixed.copy();
        MixedBootsData unchangedSources = mixedData(mixed);
        player.removeEffect(MobEffects.SPEED);
        player.setItemSlot(EquipmentSlot.FEET, mixed);
        player.tickCount = 64;

        invokeApplyEffect(effectApplier, mixed.getItem(), player);

        require(player.getEffect(MobEffects.SPEED) == null,
                "Unrelated Mismatched sources received Crystalite Swiftness");
        assertUnchanged(mixed, unchangedRoot, unchangedSources, "unrelated effect control");
    }

    private static void assertRequiresEquippedMixedBoots(
            Class<?> effectApplier,
            ServerPlayer player,
            ItemStack qualifyingMixed) {
        ItemStack unchangedRoot = qualifyingMixed.copy();
        MixedBootsData unchangedSources = mixedData(qualifyingMixed);
        player.removeEffect(MobEffects.SPEED);
        player.setItemSlot(EquipmentSlot.FEET, Items.IRON_BOOTS.getDefaultInstance());
        player.tickCount = 64;

        invokeApplyEffect(effectApplier, qualifyingMixed.getItem(), player);

        require(player.getEffect(MobEffects.SPEED) == null,
                "Unequipped Mismatched sources applied Crystalite Swiftness");
        assertUnchanged(
                qualifyingMixed, unchangedRoot, unchangedSources, "unequipped effect control");
    }

    private static void equipCrystaliteArmor(Player player, ItemStack boots) {
        player.setItemSlot(EquipmentSlot.HEAD, item("betterend", "crystalite_helmet").getDefaultInstance());
        player.setItemSlot(EquipmentSlot.CHEST, item("betterend", "crystalite_chestplate").getDefaultInstance());
        player.setItemSlot(EquipmentSlot.LEGS, item("betterend", "crystalite_leggings").getDefaultInstance());
        player.setItemSlot(EquipmentSlot.FEET, boots);
    }

    private static boolean hasFullCrystaliteSet(Player player) {
        try {
            Class<?> armor = Class.forName(
                    "org.betterx.betterend.item.CrystaliteArmor",
                    false,
                    BetterXCompatibilityGameTests.class.getClassLoader());
            Method method = armor.getMethod("hasFullSet", LivingEntity.class);
            return (boolean) method.invoke(null, player);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError("Could not invoke Better End's full-set predicate", exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Better End's full-set predicate failed", exception.getCause());
        }
    }

    private static void invokeApplyEffect(
            Class<?> effectApplier,
            Object receiver,
            LivingEntity entity) {
        try {
            Method method = effectApplier.getMethod("applyEffect", LivingEntity.class);
            method.invoke(receiver, entity);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError("Could not invoke exact Better End applyEffect contract", exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Better End adapter failed", exception.getCause());
        }
    }

    private static boolean guardAccepts(ClassNode effectApplier, ClassNode crystaliteBoots) {
        try {
            Class<?> plugin = Class.forName(
                    "games.alejandrocoria.mismatchedboots.compatibility.betterx.BetterXMixinPlugin",
                    false,
                    BetterXCompatibilityGameTests.class.getClassLoader());
            Method method = plugin.getDeclaredMethod(
                    "hasCompatibleBetterEndApi", ClassNode.class, ClassNode.class);
            require(method.trySetAccessible(), "Could not access Better End structural guard");
            return (boolean) method.invoke(null, effectApplier, crystaliteBoots);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError("Could not invoke Better End structural guard", exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Better End structural guard failed", exception.getCause());
        }
    }

    private static boolean betterNetherGuardAccepts(ClassNode[] nodes) {
        try {
            Class<?> plugin = Class.forName(
                    "games.alejandrocoria.mismatchedboots.compatibility.betterx.BetterXMixinPlugin",
                    false,
                    BetterXCompatibilityGameTests.class.getClassLoader());
            Method method = plugin.getDeclaredMethod(
                    "hasCompatibleBetterNetherApi",
                    ClassNode.class,
                    ClassNode.class,
                    ClassNode.class,
                    ClassNode.class,
                    ClassNode.class,
                    ClassNode.class);
            require(method.trySetAccessible(), "Could not access Better Nether structural guard");
            return (boolean) method.invoke(null, (Object[]) nodes);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError("Could not invoke Better Nether structural guard", exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError(
                    "Better Nether structural guard failed", exception.getCause());
        }
    }

    private static ServerPlayer connectedMockPlayer(GameTestHelper helper) {
        ServerPlayer player = GameTestPlayers.makeMockServerPlayer(helper, GameType.SURVIVAL);
        new ServerGamePacketListenerImpl(
                helper.getLevel().getServer(),
                new Connection(PacketFlow.SERVERBOUND),
                player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        return player;
    }

    private static ArrayList<String> selectPack(MinecraftServer server) {
        server.getPackRepository().reload();
        require(server.getPackRepository().isAvailable(PACK_ID),
                "Crystalite tag test data pack was not discovered");
        ArrayList<String> selected = new ArrayList<>(server.getPackRepository().getSelectedIds());
        selected.remove(PACK_ID);
        selected.add(PACK_ID);
        return selected;
    }

    private static void writePackMetadata(Path pack) throws IOException {
        PackFormat version = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.mcmeta"), """
                                  {"pack":{"description":"MismatchedBoots BetterX compatibility tests",
                                  "min_format":[%d,%d],"max_format":[%d,%d]}}
                                  """.formatted(version.major(), version.minor(), version.major(), version.minor()),
                          StandardCharsets.UTF_8);
    }

    private static void writeCrystaliteSet(Path pack, String... boots) throws IOException {
        Path tag = pack.resolve("data/betterend/tags/item/armor_set/crystalite.json");
        Files.createDirectories(tag.getParent());
        ArrayList<String> values = new ArrayList<>(Arrays.asList(
                "betterend:crystalite_helmet",
                "betterend:crystalite_chestplate",
                "betterend:crystalite_leggings"));
        values.addAll(Arrays.asList(boots));
        String encoded = values.stream()
                               .map(item -> "\"" + item + "\"")
                               .collect(Collectors.joining(","));
        Files.writeString(tag, "{\"replace\":true,\"values\":[" + encoded + "]}",
                          StandardCharsets.UTF_8);
    }

    private static void assertUnchanged(
            ItemStack existing,
            ItemStack expectedRoot,
            MixedBootsData expectedSources,
            String stage) {
        require(ItemStack.matches(expectedRoot, existing), stage + " changed root item data");
        require(expectedSources.equals(mixedData(existing)), stage + " changed source snapshots");
    }

    private static MixedBootsData mixedData(ItemStack stack) {
        return ModContent.getMixedBootsData(stack)
                         .orElseThrow(() -> new AssertionError("Missing mixed-boots source data"));
    }

    private static Item item(String namespace, String path) {
        Identifier identifier = Identifier.fromNamespaceAndPath(namespace, path);
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        require(identifier.equals(BuiltInRegistries.ITEM.getKey(item)),
                "Missing exact Better End item " + identifier);
        return item;
    }

    private static Class<?> requiredClass(String name) {
        try {
            return Class.forName(name, false, BetterXCompatibilityGameTests.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Missing exact Better End class " + name, exception);
        }
    }

    private static ClassNode effectApplierNode() {
        ClassNode node = type(Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT);
        node.methods.add(new MethodNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                "applyEffect",
                APPLY_EFFECT,
                null,
                null));
        return node;
    }

    private static ClassNode crystaliteBootsNode() {
        ClassNode node = type(Opcodes.ACC_PUBLIC);
        node.interfaces.add(EFFECT_APPLIER_INTERNAL);
        node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "applyEffect", APPLY_EFFECT, null, null));
        return node;
    }

    private static ClassNode[] betterNetherApiNodes() {
        ClassNode configs = type(Opcodes.ACC_PUBLIC);
        configs.fields.add(field(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                "GAME_RULES",
                DEFAULT_CONFIG_DESCRIPTOR));

        ClassNode defaultConfig = type(Opcodes.ACC_PUBLIC);
        defaultConfig.fields.add(field(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                "piglinIgnoreNetherArmor",
                BOOLEAN_VALUE_DESCRIPTOR));

        ClassNode equipmentItems = type(Opcodes.ACC_PUBLIC);
        for (String name : List.of(
                "CINCINNASITE_SET", "NETHER_RUBY_SET", "FLAMING_RUBY_SET")) {
            equipmentItems.fields.add(field(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                    name,
                    NETHER_SET_DESCRIPTOR));
        }

        ClassNode netherSet = type(Opcodes.ACC_PUBLIC);
        netherSet.superName = EQUIPMENT_SET_INTERNAL;

        ClassNode equipmentSet = type(Opcodes.ACC_PUBLIC);
        equipmentSet.methods.add(new MethodNode(
                Opcodes.ACC_PUBLIC,
                "getArmorPieces",
                ARMOR_PIECES_DESCRIPTOR,
                null,
                null));

        ClassNode upstreamMixin = type(Opcodes.ACC_PUBLIC);
        MethodNode handler = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "bn_isWearingSafeArmor",
                UPSTREAM_HANDLER_DESCRIPTOR,
                null,
                null);
        AnnotationNode at = new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/At;");
        at.values = new ArrayList<>(List.of("value", "HEAD"));
        AnnotationNode inject =
                new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;");
        inject.values = new ArrayList<>(List.of(
                "method", List.of("isWearingSafeArmor"),
                "at", List.of(at),
                "cancellable", true));
        handler.visibleAnnotations = new ArrayList<>(List.of(inject));
        upstreamMixin.methods.add(handler);

        return new ClassNode[]{
                configs, defaultConfig, equipmentItems, netherSet, equipmentSet, upstreamMixin
        };
    }

    private static FieldNode field(int access, String name, String descriptor) {
        return new FieldNode(access, name, descriptor, null, null);
    }

    private static ClassNode type(int access) {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V25;
        node.access = access;
        node.name = "fixture/Type";
        node.superName = "java/lang/Object";
        return node;
    }

    private static TagKey<Item> tag(String namespace, String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(namespace, path));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
