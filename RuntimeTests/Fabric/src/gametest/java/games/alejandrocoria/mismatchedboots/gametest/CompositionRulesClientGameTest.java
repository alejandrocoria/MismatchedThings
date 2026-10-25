package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleDefaults;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import games.alejandrocoria.mismatchedboots.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedboots.component.ComponentNumericKind;
import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentRule;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleDefaults;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleState;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.component.DurabilityPolicy;
import games.alejandrocoria.mismatchedboots.component.RepairCostPolicy;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesConfig;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesConfig;
import games.alejandrocoria.mismatchedboots.network.AttributeRulesPayload;
import games.alejandrocoria.mismatchedboots.network.ComponentRulesPayload;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.platform.Services;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchInput;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
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
public final class CompositionRulesClientGameTest implements FabricClientGameTest {
    private static final Identifier MOVEMENT_MODIFIER =
            Identifier.fromNamespaceAndPath("mismatchedboots-test", "shared_movement");
    private static final Identifier DORMANT_ATTRIBUTE =
            Identifier.fromNamespaceAndPath("futuremod", "dormant");
    private static final Identifier DORMANT_COMPONENT =
            Identifier.fromNamespaceAndPath("futuremod", "dormant_energy");

    @Override
    public void runTest(ClientGameTestContext context) {
        Path configPath = Services.PLATFORM.getConfigDirectory().resolve(AttributeRulesConfig.FILE_NAME);
        Path componentConfigPath = Services.PLATFORM.getConfigDirectory().resolve(ComponentRulesConfig.FILE_NAME);
        writeConfig(configPath, "combine");
        writeComponentConfig(componentConfigPath);

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
            context.waitFor(client -> ComponentRuleState.current().durability() == DurabilityPolicy.SEPARATE
                    && ComponentRuleState.current().repairCost() == RepairCostPolicy.OWNER);
            context.waitFor(client -> NetworkTrafficProbe.events().size() == 3);
            context.waitFor(client -> MismatchedWorkbenchClientRecipes.hasRecipes());
            context.runOnClient(client -> assertHandshake(expectedPolicy));

            singleplayer.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst()
                                                                 .getInventory().add(createPolicyResult()));
            context.waitFor(client -> client.player != null && client.player.getInventory()
                                                                            .contains(stack -> isPolicyResult(stack) && attributeAmount(stack) == expectedAmount));
            context.runOnClient(client -> {
                ItemStack serverResult = client.player.getInventory().getNonEquipmentItems().stream()
                                                      .filter(CompositionRulesClientGameTest::isPolicyResult)
                                                      .findFirst()
                                                      .orElseThrow();
                ItemStack preview = MismatchedWorkbenchClientRecipes.assemblePair(new MismatchedWorkbenchInput(
                                                                                          source(Items.LEATHER_BOOTS, 2.0), source(Items.CHAINMAIL_BOOTS, 4.0)), client.level,
                                                                                  AttributeRuleState.current(), ComponentRuleState.current())
                                                                    .primary();
                if (!serverResult.getOrDefault(
                        DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).equals(
                        preview.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY))) {
                    throw new AssertionError("Client recipe preview differs from the server result");
                }
                if (attributeAmount(preview) != expectedAmount) {
                    throw new AssertionError("Unexpected synchronized policy result: " + attributeAmount(preview));
                }
                assertComponentResult(serverResult, preview);
                assertMalformedSnapshotsAreAtomic(
                        client, AttributeRuleState.current(), ComponentRuleState.current());
                assertHandshake(expectedPolicy);
            });
            assertServerCompositionDuringClientReset(context, singleplayer, expectedPolicy, expectedAmount);
        }
    }

    private static void assertServerCompositionDuringClientReset(
            ClientGameTestContext context, TestSingleplayerContext singleplayer,
            AttributePolicy expectedPolicy, double expectedAmount) {
        var attributes = AttributeRuleState.current();
        var components = ComponentRuleState.current();
        for (int phase = 0; phase < 3; phase++) {
            int partial = phase;
            context.runOnClient(client -> {
                CompositionRulesClientLifecycle.onDisconnected();
                if (partial == 1) {
                    AttributeRuleState.install(attributes);
                }
                if (partial == 2) {
                    ComponentRuleState.install(components);
                }
                if (CompositionRulesClientLifecycle.areRulesSynchronized()) {
                    throw new AssertionError("Reset/partial receipt incorrectly reports full readiness");
                }
                if (partial == 0) {
                    var disconnectedPreview = MixedBootsFactory.createPair(
                            source(Items.LEATHER_BOOTS, 2.0), source(Items.CHAINMAIL_BOOTS, 4.0),
                            AttributeRuleState.current(), ComponentRuleState.current()).primary();
                    if (attributeAmount(disconnectedPreview) != 3.0) {
                        throw new AssertionError("Disconnected client preview did not use built-in average");
                    }
                }
            });
            singleplayer.getServer().runOnServer(server -> {
                var left = source(Items.LEATHER_BOOTS, 2.0);
                var right = source(Items.CHAINMAIL_BOOTS, 4.0);
                var pair = new MixedBootsWorkbenchRecipe().assemblePair(new MismatchedWorkbenchInput(left, right));
                if (!pair.isComplete() || attributeAmount(pair.primary()) != expectedAmount) {
                    throw new AssertionError("Client reset/partial rules changed integrated-server Workbench output");
                }
                assertComponentResult(pair.primary(), pair.primary());
                ItemStack recomposed = MixedBootsRecomposer.recompose(pair.primary(), left, right);
                if (attributeAmount(recomposed) != expectedAmount) {
                    throw new AssertionError("Client reset/partial rules changed integrated-server recomposition");
                }
                assertComponentResult(pair.primary(), recomposed);
            });
        }
        // Restore through a real handshake so subsequent tests keep production readiness semantics.
        NetworkTrafficProbe.reset();
        context.runOnClient(client -> CompositionRulesClientLifecycle.onConnected());
        context.waitFor(client -> CompositionRulesClientLifecycle.areRulesSynchronized()
                && NetworkTrafficProbe.events().size() == 3);
        context.runOnClient(client -> assertHandshake(expectedPolicy));
    }

    private static void assertHandshake(AttributePolicy expectedPolicy) {
        List<NetworkTrafficProbe.Event> events = NetworkTrafficProbe.events();
        if (events.size() != 3
                || !events.get(0).name().equals("client_ready")
                || !events.get(1).name().equals("attribute_rules")
                || !events.get(2).name().equals("component_rules")) {
            throw new AssertionError("Unexpected rule synchronization traffic: " + events);
        }
        if (events.stream().anyMatch(event -> !event.forced())) {
            throw new AssertionError("Rule synchronization did not use forced sends: " + events);
        }
        AttributeRulesPayload response = (AttributeRulesPayload) events.get(1).payload();
        if (response == null || !response.rules().equals(AttributeRuleState.current())) {
            throw new AssertionError("Client attribute rules differ from the complete server snapshot");
        }
        if (response.rules().policyFor(Identifier.withDefaultNamespace("movement_speed")) != expectedPolicy
                || response.rules().policyFor(DORMANT_ATTRIBUTE) != AttributePolicy.MINIMUM) {
            throw new AssertionError("Server attribute snapshot omitted configured rules: " + response.rules());
        }

        ComponentRulesPayload componentResponse = (ComponentRulesPayload) events.get(2).payload();
        var componentRules = componentResponse.rules();
        if (!componentRules.equals(ComponentRuleState.current())) {
            throw new AssertionError("Client component rules differ from the complete server snapshot");
        }
        if (componentRules.durability() != DurabilityPolicy.SEPARATE
                || componentRules.repairCost() != RepairCostPolicy.OWNER
                || !ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.INT).equals(
                componentRules.declarations().get(TestContent.NUMERIC_COMPONENT_ID))
                || !ComponentRule.of(ComponentPolicy.SHARE).equals(
                componentRules.declarations().get(DORMANT_COMPONENT))
                || componentRules.components().rules().containsKey(DORMANT_COMPONENT)) {
            throw new AssertionError("Server component snapshot omitted configured rules: " + componentRules);
        }
    }

    private static void assertDisconnectedDefaults(ClientGameTestContext context) {
        context.waitFor(client -> client.level == null);
        context.runOnClient(client -> {
            if (AttributeRuleState.current() != AttributeRuleDefaults.ruleSet()) {
                throw new AssertionError("Disconnect did not restore built-in attribute rules");
            }
            if (ComponentRuleState.current() != ComponentRuleDefaults.state()) {
                throw new AssertionError("Disconnect did not restore built-in component rules");
            }
            if (MismatchedWorkbenchClientRecipes.hasRecipes()) {
                throw new AssertionError("Disconnect retained the previous world's Workbench recipes");
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
                                                                                 new AttributeModifier(MOVEMENT_MODIFIER,
                                                                                                       amount,
                                                                                                       AttributeModifier.Operation.ADD_VALUE),
                                                                                 EquipmentSlotGroup.FEET)
                                                                            .build());
        if (item == Items.LEATHER_BOOTS) {
            stack.setDamageValue(11);
            stack.set(DataComponents.REPAIR_COST, 2);
            stack.set(TestContent.requireNumericComponent(), 2);
        } else {
            stack.setDamageValue(7);
            stack.set(DataComponents.REPAIR_COST, 5);
            stack.set(TestContent.requireNumericComponent(), 6);
        }
        return stack;
    }

    private static void assertComponentResult(ItemStack serverResult, ItemStack preview) {
        Integer previewNumeric = preview.get(TestContent.requireNumericComponent());
        if (preview.getMaxDamage() != Items.LEATHER_BOOTS.getDefaultInstance().getMaxDamage()
                || preview.getDamageValue() != 11
                || preview.getOrDefault(DataComponents.REPAIR_COST, 0) != 2
                || !Integer.valueOf(4).equals(previewNumeric)) {
            throw new AssertionError("Unexpected synchronized component result: " + preview.getComponents());
        }
        if (serverResult.getMaxDamage() != preview.getMaxDamage()
                || serverResult.getDamageValue() != preview.getDamageValue()
                || serverResult.getOrDefault(DataComponents.REPAIR_COST, 0)
                != preview.getOrDefault(DataComponents.REPAIR_COST, 0)
                || !previewNumeric.equals(serverResult.get(TestContent.requireNumericComponent()))) {
            throw new AssertionError("Client component preview differs from the server result");
        }
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

    private static void assertMalformedSnapshotsAreAtomic(
            net.minecraft.client.Minecraft client,
            AttributeRuleSet beforeAttributes,
            ComponentRules beforeComponents) {
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
                if (AttributeRuleState.current() != beforeAttributes) {
                    throw new AssertionError("Malformed snapshot partially replaced client rules");
                }
            }
        } finally {
            buffer.release();
        }

        buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), client.level.registryAccess());
        try {
            buffer.writeUtf("separate", 16);
            buffer.writeUtf("owner", 16);
            buffer.writeVarInt(2);
            writeComponentRule(buffer, "minecraft:rarity", "same");
            writeComponentRule(buffer, "minecraft:rarity", "owner");
            try {
                ComponentRulesPayload.STREAM_CODEC.decode(buffer);
                throw new AssertionError("Malformed duplicate component snapshot was accepted");
            } catch (RuntimeException expected) {
                if (ComponentRuleState.current() != beforeComponents) {
                    throw new AssertionError("Malformed snapshot partially replaced component rules");
                }
            }
        } finally {
            buffer.release();
        }
    }

    private static void writeComponentRule(
            RegistryFriendlyByteBuf buffer, String id, String policy) {
        buffer.writeUtf(id);
        buffer.writeUtf(policy, 16);
        buffer.writeByte(0);
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

    private static void writeComponentConfig(Path path) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, """
                    [durability]
                    policy = "separate"
                    
                    [repair_cost]
                    policy = "owner"
                    
                    [components."mismatchedboots-test:numeric"]
                    policy = "average"
                    number = "int"
                    
                    [components."futuremod:dormant_energy"]
                    policy = "share"
                    """);
        } catch (IOException exception) {
            throw new AssertionError("Could not prepare client component synchronization config", exception);
        }
    }
}
