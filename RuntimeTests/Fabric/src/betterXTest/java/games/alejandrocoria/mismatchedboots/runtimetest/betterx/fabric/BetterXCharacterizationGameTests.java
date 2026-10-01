package games.alejandrocoria.mismatchedboots.runtimetest.betterx.fabric;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public final class BetterXCharacterizationGameTests {
    private static final String EMPTY_STRUCTURE = "mismatchedboots_betterx_test:empty";
    private static final String PIGLIN_IGNORE_PROPERTY = "mismatchedboots.test.betterX.piglinIgnore";
    private static final TagKey<Item> CRYSTALITE_SET = TagKey.create(
            Registries.ITEM, id("betterend", "armor_set/crystalite"));
    private static final ResourceKey<Enchantment> RUBY_FIRE = ResourceKey.create(
            Registries.ENCHANTMENT, id("betternether", "ruby_fire"));

    @GameTest(structure = EMPTY_STRUCTURE)
    public void crystaliteSwiftnessUsesConcreteItemInterface(GameTestHelper helper) {
        ServerPlayer player = connectedMockPlayer(helper);
        Item crystaliteBoots = item("betterend", "crystalite_boots");
        Class<?> effectApplier = externalClass("org.betterx.betterend.interfaces.MobEffectApplier");

        require(effectApplier.isInstance(crystaliteBoots),
                "Ordinary Crystalite Boots do not implement MobEffectApplier");
        require(!effectApplier.isInstance(Items.IRON_BOOTS),
                "Unrelated iron boots unexpectedly implement MobEffectApplier");

        player.tickCount = 63;
        invoke(effectApplier, crystaliteBoots, "applyEffect", player);
        require(player.getEffect(MobEffects.SPEED) == null,
                "Crystalite Boots applied Speed before their 64-tick boundary");

        player.tickCount = 64;
        invoke(effectApplier, crystaliteBoots, "applyEffect", player);

        MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
        require(speed != null, "Ordinary Crystalite Boots did not apply Speed");
        require(speed.getAmplifier() == 0, "Crystalite Boots applied an unexpected Speed amplifier");
        require(speed.getDuration() == 80, "Crystalite Boots applied an unexpected Speed duration");

        player.removeEffect(MobEffects.SPEED);
        player.addEffect(new MobEffectInstance(MobEffects.SPEED, 5, 0));
        player.tickCount = 65;
        invoke(effectApplier, crystaliteBoots, "applyEffect", player);
        require(player.getEffect(MobEffects.SPEED).getDuration() == 5,
                "Crystalite Boots refreshed Speed away from their 64-tick boundary");

        player.tickCount = 128;
        invoke(effectApplier, crystaliteBoots, "applyEffect", player);
        speed = player.getEffect(MobEffects.SPEED);
        require(speed != null && speed.getDuration() == 80,
                "Crystalite Boots did not refresh Speed at their next 64-tick boundary");

        MixedBootsPair pair = MixedBootsFactory.createPair(
                crystaliteBoots.getDefaultInstance(), Items.IRON_BOOTS.getDefaultInstance());
        require(!effectApplier.isInstance(pair.primary().getItem()),
                "Current primary Mismatched root unexpectedly implements MobEffectApplier");
        require(!effectApplier.isInstance(pair.inverse().getItem()),
                "Current inverse Mismatched root unexpectedly implements MobEffectApplier");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void crystaliteFullSetRejectsCurrentMixedRoot(GameTestHelper helper) {
        Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
        Item crystaliteBoots = item("betterend", "crystalite_boots");
        equipCrystaliteArmor(player, crystaliteBoots.getDefaultInstance());

        require(crystaliteBoots.getDefaultInstance().is(CRYSTALITE_SET),
                "Ordinary Crystalite Boots are missing the Crystalite set tag");
        require(hasFullCrystaliteSet(player), "Better End rejected an ordinary full Crystalite set");

        MixedBootsPair pair = MixedBootsFactory.createPair(
                crystaliteBoots.getDefaultInstance(), Items.IRON_BOOTS.getDefaultInstance());
        assertMixedCrystaliteSetBaseline(player, pair.primary(), "primary");
        assertMixedCrystaliteSetBaseline(player, pair.inverse(), "inverse");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void betterNetherPiglinRuleUsesConcreteArmorAndConfig(GameTestHelper helper) {
        boolean piglinIgnore = Boolean.parseBoolean(System.getProperty(PIGLIN_IGNORE_PROPERTY, ""));
        require(System.getProperty(PIGLIN_IGNORE_PROPERTY) != null,
                "Missing expected Better Nether piglin config property");
        Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);

        for (String path : List.of("cincinnasite_boots", "nether_ruby_boots", "flaming_ruby_boots")) {
            Item boots = item("betternether", path);
            player.setItemSlot(EquipmentSlot.FEET, boots.getDefaultInstance());
            require(PiglinAi.isWearingSafeArmor(player) == piglinIgnore,
                    path + " did not follow Better Nether's piglin config");

            MixedBootsPair pair = MixedBootsFactory.createPair(
                    boots.getDefaultInstance(), Items.IRON_BOOTS.getDefaultInstance());
            assertPiglinUnsafe(player, pair.primary(), path + " primary");
            assertPiglinUnsafe(player, pair.inverse(), path + " inverse");
        }

        player.setItemSlot(EquipmentSlot.FEET, Items.GOLDEN_BOOTS.getDefaultInstance());
        require(PiglinAi.isWearingSafeArmor(player),
                "Better Nether changed vanilla golden-boots safety");
        player.setItemSlot(EquipmentSlot.FEET, Items.IRON_BOOTS.getDefaultInstance());
        require(!PiglinAi.isWearingSafeArmor(player),
                "Better Nether made unrelated iron boots piglin-safe");
        player.setItemSlot(EquipmentSlot.FEET, item("betterend", "aeternium_boots").getDefaultInstance());
        require(!PiglinAi.isWearingSafeArmor(player),
                "Better Nether made unrelated Better End boots piglin-safe");
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

    private static void assertMixedCrystaliteSetBaseline(Player player, ItemStack mixed, String orientation) {
        require(!mixed.is(CRYSTALITE_SET),
                "Current " + orientation + " Mismatched root unexpectedly projects the Crystalite set tag");
        player.setItemSlot(EquipmentSlot.FEET, mixed);
        require(!hasFullCrystaliteSet(player),
                "Current " + orientation + " Mismatched root unexpectedly completes the Crystalite set");
    }

    private static void equipCrystaliteArmor(Player player, ItemStack boots) {
        player.setItemSlot(EquipmentSlot.HEAD, item("betterend", "crystalite_helmet").getDefaultInstance());
        player.setItemSlot(EquipmentSlot.CHEST, item("betterend", "crystalite_chestplate").getDefaultInstance());
        player.setItemSlot(EquipmentSlot.LEGS, item("betterend", "crystalite_leggings").getDefaultInstance());
        player.setItemSlot(EquipmentSlot.FEET, boots);
    }

    private static boolean hasFullCrystaliteSet(Player player) {
        Class<?> crystaliteArmor = externalClass("org.betterx.betterend.item.CrystaliteArmor");
        return (boolean) invoke(crystaliteArmor, null, "hasFullSet", player);
    }

    private static void assertPiglinUnsafe(Player player, ItemStack mixed, String description) {
        player.setItemSlot(EquipmentSlot.FEET, mixed);
        require(!PiglinAi.isWearingSafeArmor(player),
                "Current " + description + " Mismatched root unexpectedly preserves piglin-ignore behavior");
    }

    private static void assertRubyFireRetaliation(
            GameTestHelper helper, ItemStack boots, String description) {
        ServerPlayer wearer = connectedMockPlayer(helper);
        wearer.setItemSlot(EquipmentSlot.FEET, boots);
        ItemStack equipped = wearer.getItemBySlot(EquipmentSlot.FEET);
        MixedBootsData before = ModContent.getMixedBootsData(equipped).orElse(null);
        int initialDamage = equipped.getDamageValue();

        Zombie attacker = helper.spawn(EntityTypes.ZOMBIE, BlockPos.ZERO);
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

    private static ServerPlayer connectedMockPlayer(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        new ServerGamePacketListenerImpl(
                helper.getLevel().getServer(),
                new Connection(PacketFlow.SERVERBOUND),
                player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        return player;
    }

    private static Item item(String namespace, String path) {
        Identifier identifier = id(namespace, path);
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        require(identifier.equals(BuiltInRegistries.ITEM.getKey(item)),
                "Missing exact BetterX item " + identifier);
        return item;
    }

    private static Class<?> externalClass(String name) {
        try {
            return Class.forName(name, false, BetterXCharacterizationGameTests.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            throw new AssertionError("Missing exact BetterX class " + name, exception);
        }
    }

    private static Object invoke(Class<?> owner, Object receiver, String name, Player player) {
        try {
            Method method = owner.getMethod(name, net.minecraft.world.entity.LivingEntity.class);
            return method.invoke(receiver, player);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError("Could not invoke exact BetterX method " + owner.getName() + "." + name,
                    exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Exact BetterX method failed " + owner.getName() + "." + name,
                    exception.getCause());
        }
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
