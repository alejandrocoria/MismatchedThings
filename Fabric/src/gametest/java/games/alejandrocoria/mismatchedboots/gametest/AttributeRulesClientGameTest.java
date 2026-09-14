package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleDefaults;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesConfig;
import games.alejandrocoria.mismatchedboots.network.AttributeRulesPayload;
import games.alejandrocoria.mismatchedboots.platform.Services;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class AttributeRulesClientGameTest implements FabricClientGameTest {
    private static final Identifier MOVEMENT_MODIFIER =
            Identifier.fromNamespaceAndPath("mismatchedboots-test", "shared_movement");
    private static final Identifier DORMANT_ATTRIBUTE =
            Identifier.fromNamespaceAndPath("futuremod", "dormant");

    @Override
    public void runTest(ClientGameTestContext context) {
        Path configPath = Services.PLATFORM.getConfigDirectory().resolve(AttributeRulesConfig.FILE_NAME);
        writeConfig(configPath, "combine");

        runConnection(context, AttributePolicy.COMBINE, 6.0);
        assertDisconnectedDefaults(context);

        writeConfig(configPath, "maximum");
        runConnection(context, AttributePolicy.MAXIMUM, 4.0);
        assertDisconnectedDefaults(context);

        writeConfig(configPath, "combine");
    }

    private static void runConnection(
            ClientGameTestContext context, AttributePolicy expectedPolicy, double expectedAmount) {
        NetworkTrafficProbe.reset();
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitFor(client -> AttributeRuleState.current()
                    .policyFor(Identifier.withDefaultNamespace("movement_speed")) == expectedPolicy);
            context.waitFor(client -> NetworkTrafficProbe.events().size() == 2);
            context.runOnClient(client -> assertHandshake(expectedPolicy));

            singleplayer.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
                    .getInventory().add(createPolicyResult()));
            context.waitFor(client -> client.player != null && client.player.getInventory()
                    .contains(stack -> isPolicyResult(stack) && attributeAmount(stack) == expectedAmount));
            context.runOnClient(client -> {
                ItemStack serverResult = client.player.getInventory().getNonEquipmentItems().stream()
                        .filter(AttributeRulesClientGameTest::isPolicyResult)
                        .findFirst()
                        .orElseThrow();
                ItemStack preview = createPolicyResult();
                if (!serverResult.getOrDefault(
                        DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).equals(
                        preview.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY))) {
                    throw new AssertionError("Client recipe preview differs from the server result");
                }
                if (attributeAmount(preview) != expectedAmount) {
                    throw new AssertionError("Unexpected synchronized policy result: " + attributeAmount(preview));
                }
                assertMalformedSnapshotIsAtomic(client, AttributeRuleState.current());
                assertHandshake(expectedPolicy);
            });
        }
    }

    private static void assertHandshake(AttributePolicy expectedPolicy) {
        List<NetworkTrafficProbe.Event> events = NetworkTrafficProbe.events();
        if (events.size() != 2
                || !events.get(0).name().equals("client_ready")
                || !events.get(1).name().equals("attribute_rules")) {
            throw new AssertionError("Unexpected rule synchronization traffic: " + events);
        }
        if (events.stream().anyMatch(event -> !event.forced())) {
            throw new AssertionError("Rule synchronization did not use forced sends: " + events);
        }
        AttributeRulesPayload response = events.get(1).payload();
        if (response == null || !response.rules().equals(AttributeRuleState.current())) {
            throw new AssertionError("Client rules differ from the complete server snapshot");
        }
        if (response.rules().policyFor(Identifier.withDefaultNamespace("movement_speed")) != expectedPolicy
                || response.rules().policyFor(DORMANT_ATTRIBUTE) != AttributePolicy.MINIMUM) {
            throw new AssertionError("Server snapshot omitted configured rules: " + response.rules());
        }
    }

    private static void assertDisconnectedDefaults(ClientGameTestContext context) {
        context.waitFor(client -> client.level == null);
        context.runOnClient(client -> {
            if (AttributeRuleState.current() != AttributeRuleDefaults.ruleSet()) {
                throw new AssertionError("Disconnect did not restore built-in attribute rules");
            }
        });
    }

    private static ItemStack createPolicyResult() {
        return MixedBootsFactory.createPair(
                source(Items.LEATHER_BOOTS, 2.0), source(Items.CHAINMAIL_BOOTS, 4.0)).primary();
    }

    private static ItemStack source(Item item, double amount) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(Attributes.MOVEMENT_SPEED,
                        new AttributeModifier(MOVEMENT_MODIFIER, amount, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.FEET)
                .build());
        return stack;
    }

    private static boolean isPolicyResult(ItemStack stack) {
        return games.alejandrocoria.mismatchedboots.content.ModContent.getMixedBootsData(stack)
                .filter(data -> data.left().source().is(Items.LEATHER_BOOTS)
                        && data.right().source().is(Items.CHAINMAIL_BOOTS))
                .isPresent();
    }

    private static double attributeAmount(ItemStack stack) {
        ItemAttributeModifiers modifiers = stack.getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return modifiers.modifiers().stream()
                .filter(entry -> entry.modifier().id().equals(MOVEMENT_MODIFIER))
                .mapToDouble(entry -> entry.modifier().amount())
                .findFirst()
                .orElse(Double.NaN);
    }

    private static void assertMalformedSnapshotIsAtomic(
            net.minecraft.client.Minecraft client, AttributeRuleSet before) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), client.level.registryAccess());
        try {
            buffer.writeUtf("separate", 16);
            buffer.writeVarInt(2);
            buffer.writeUtf("minecraft:armor");
            buffer.writeUtf("average", 16);
            buffer.writeUtf("minecraft:armor");
            buffer.writeUtf("combine", 16);
            try {
                AttributeRulesPayload.STREAM_CODEC.decode(buffer);
                throw new AssertionError("Malformed duplicate snapshot was accepted");
            } catch (RuntimeException expected) {
                if (AttributeRuleState.current() != before) {
                    throw new AssertionError("Malformed snapshot partially replaced client rules");
                }
            }
        } finally {
            buffer.release();
        }
    }

    private static void writeConfig(Path path, String movementPolicy) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, """
                    [attributes]
                    fallback = "separate"

                    [attributes."minecraft:movement_speed"]
                    policy = "%s"

                    [attributes."futuremod:dormant"]
                    policy = "minimum"
                    """.formatted(movementPolicy));
        } catch (IOException exception) {
            throw new AssertionError("Could not prepare client synchronization config", exception);
        }
    }
}
