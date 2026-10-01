package games.alejandrocoria.mismatchedboots.runtimetest.betterend.fabric;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class BetterEndAdapterSpikeGameTests {
    private static final String EMPTY_STRUCTURE = "mismatchedboots_betterend_spike_test:empty";
    private static final String PRESENT_PROPERTY = "mismatchedboots.test.betterEnd.present";
    private static final String EFFECT_APPLIER = "org.betterx.betterend.interfaces.MobEffectApplier";
    private static final String EFFECT_APPLIER_INTERNAL =
            "org/betterx/betterend/interfaces/MobEffectApplier";
    private static final String APPLY_EFFECT = "(Lnet/minecraft/world/entity/LivingEntity;)V";

    @GameTest(structure = EMPTY_STRUCTURE)
    public void guardAcceptsOnlyTheExactExpectedApi(GameTestHelper helper) {
        ClassNode effectApplier = effectApplierNode();
        ClassNode crystaliteBoots = crystaliteBootsNode();
        require(BetterEndSpikeMixinPlugin.hasCompatibleApi(effectApplier, crystaliteBoots),
                "Exact Better End API shape was rejected");
        require(!BetterEndSpikeMixinPlugin.hasCompatibleApi(null, crystaliteBoots),
                "Missing effect interface was accepted");
        require(!BetterEndSpikeMixinPlugin.hasCompatibleApi(effectApplier, null),
                "Missing Crystalite Boots class was accepted");

        ClassNode wrongDescriptor = effectApplierNode();
        wrongDescriptor.methods.getFirst().desc = "()V";
        require(!BetterEndSpikeMixinPlugin.hasCompatibleApi(wrongDescriptor, crystaliteBootsNode()),
                "Wrong applyEffect descriptor was accepted");

        ClassNode wrongBootInterface = crystaliteBootsNode();
        wrongBootInterface.interfaces.clear();
        require(!BetterEndSpikeMixinPlugin.hasCompatibleApi(effectApplierNode(), wrongBootInterface),
                "Crystalite Boots without MobEffectApplier was accepted");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void adapterPresenceMatchesTheOptionalMod(GameTestHelper helper) {
        boolean expectedPresent = expectedPresent();
        Class<?> effectApplier = optionalClass(EFFECT_APPLIER);
        require((effectApplier != null) == expectedPresent,
                "Better End classpath presence does not match the test scenario");

        MixedBootsPair control = MixedBootsFactory.createPair(
                Items.IRON_BOOTS.getDefaultInstance(), Items.GOLDEN_BOOTS.getDefaultInstance());
        if (expectedPresent) {
            require(effectApplier.isInstance(control.primary().getItem()),
                    "Guarded mixin was not applied with the exact Better End API present");
        }
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void adapterDelegatesCrystaliteSwiftnessExactlyOnce(GameTestHelper helper) {
        if (!expectedPresent()) {
            helper.succeed();
            return;
        }

        ServerPlayer player = connectedMockPlayer(helper);
        Class<?> effectApplier = requiredClass(EFFECT_APPLIER);
        ItemStack crystalite = item("betterend", "crystalite_boots").getDefaultInstance();
        MixedBootsPair pair = MixedBootsFactory.createPair(
                crystalite, Items.IRON_BOOTS.getDefaultInstance());

        assertSwiftness(effectApplier, player, pair.primary(), "primary", 1);
        assertSwiftness(effectApplier, player, pair.inverse(), "inverse", 1);

        MixedBootsPair twoCrystalite = MixedBootsFactory.createPair(crystalite, crystalite);
        assertSwiftness(effectApplier, player, twoCrystalite.primary(), "two-source", 1);

        MixedBootsPair control = MixedBootsFactory.createPair(
                Items.IRON_BOOTS.getDefaultInstance(), Items.GOLDEN_BOOTS.getDefaultInstance());
        assertNoSwiftness(effectApplier, player, control.primary());
        helper.succeed();
    }

    private static void assertSwiftness(
            Class<?> effectApplier,
            ServerPlayer player,
            ItemStack mixed,
            String description,
            int expectedCalls) {
        require(effectApplier.isInstance(mixed.getItem()),
                description + " Mismatched root does not implement MobEffectApplier");
        player.removeEffect(MobEffects.SPEED);
        player.setItemSlot(EquipmentSlot.FEET, mixed);
        player.tickCount = 64;
        MixedBootsData before = ModContent.getMixedBootsData(mixed).orElseThrow();
        BetterEndSpikeProbe.reset();

        invokeApplyEffect(effectApplier, mixed.getItem(), player);

        MobEffectInstance speed = player.getEffect(MobEffects.SPEED);
        require(speed != null, description + " Mismatched root did not apply Swiftness");
        require(speed.getAmplifier() == 0, description + " applied an unexpected amplifier");
        require(speed.getDuration() == 80, description + " applied an unexpected duration");
        require(BetterEndSpikeProbe.delegatedCalls() == expectedCalls,
                description + " delegated Swiftness an unexpected number of times");
        require(before.equals(ModContent.getMixedBootsData(mixed).orElse(null)),
                description + " mutated stored source snapshots");
    }

    private static void assertNoSwiftness(Class<?> effectApplier, ServerPlayer player, ItemStack mixed) {
        player.removeEffect(MobEffects.SPEED);
        player.setItemSlot(EquipmentSlot.FEET, mixed);
        player.tickCount = 64;
        BetterEndSpikeProbe.reset();

        invokeApplyEffect(effectApplier, mixed.getItem(), player);

        require(player.getEffect(MobEffects.SPEED) == null,
                "Unrelated Mismatched sources received Crystalite Swiftness");
        require(BetterEndSpikeProbe.delegatedCalls() == 0,
                "Unrelated Mismatched sources delegated to Crystalite Boots");
    }

    private static void invokeApplyEffect(Class<?> effectApplier, Object receiver, LivingEntity entity) {
        try {
            Method method = effectApplier.getMethod("applyEffect", LivingEntity.class);
            method.invoke(receiver, entity);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError("Could not invoke exact Better End applyEffect contract", exception);
        } catch (InvocationTargetException exception) {
            throw new AssertionError("Better End adapter failed", exception.getCause());
        }
    }

    private static boolean expectedPresent() {
        String property = System.getProperty(PRESENT_PROPERTY);
        require(property != null, "Missing Better End presence scenario property");
        return Boolean.parseBoolean(property);
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
        Identifier identifier = Identifier.fromNamespaceAndPath(namespace, path);
        Item item = BuiltInRegistries.ITEM.getValue(identifier);
        require(identifier.equals(BuiltInRegistries.ITEM.getKey(item)),
                "Missing exact Better End item " + identifier);
        return item;
    }

    private static Class<?> requiredClass(String name) {
        Class<?> type = optionalClass(name);
        if (type == null) {
            throw new AssertionError("Missing exact Better End class " + name);
        }
        return type;
    }

    private static Class<?> optionalClass(String name) {
        try {
            return Class.forName(name, false, BetterEndAdapterSpikeGameTests.class.getClassLoader());
        } catch (ClassNotFoundException exception) {
            return null;
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

    private static ClassNode type(int access) {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V25;
        node.access = access;
        node.name = "fixture/Type";
        node.superName = "java/lang/Object";
        return node;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
