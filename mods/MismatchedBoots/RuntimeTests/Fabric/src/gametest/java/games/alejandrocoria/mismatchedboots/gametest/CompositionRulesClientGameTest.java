package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.client.network.FabricCompositionRulesClientNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedboots.component.ComponentNumericKind;
import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentRule;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesConfig;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesConfig;
import games.alejandrocoria.mismatchedboots.network.CompositionRules;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesPayload;
import games.alejandrocoria.mismatchedboots.platform.Services;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.api.composition.DurabilityPolicy;
import games.alejandrocoria.mismatchedthings.api.composition.RepairCostPolicy;
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
        assertDisconnectedState(context);

        writeConfig(configPath, "maximum");
        runConnection(context, AttributePolicy.MAXIMUM, 4.0);
        assertDisconnectedState(context);

        writeConfig(configPath, "combine");
    }

    private static void runConnection(
            ClientGameTestContext context, AttributePolicy expectedPolicy, double expectedAmount) {
        NetworkTrafficProbe.reset();
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitFor(client -> CompositionRulesClientLifecycle.areRulesSynchronized());
            context.waitFor(client -> CompositionRulesClientLifecycle.synchronizedRules().attributes()
                                                        .policyFor(Identifier.withDefaultNamespace("movement_speed")) == expectedPolicy);
            context.waitFor(client -> CompositionRulesClientLifecycle.synchronizedRules().components().durability() == DurabilityPolicy.SEPARATE
                    && CompositionRulesClientLifecycle.synchronizedRules().components().repairCost() == RepairCostPolicy.OWNER);
            context.waitFor(client -> NetworkTrafficProbe.events().size() == 2);
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
                ItemStack preview = MismatchedWorkbenchClientRecipes.assemblePair(new WorkbenchInput(
                                                                                          source(Items.LEATHER_BOOTS, 2.0), source(Items.CHAINMAIL_BOOTS, 4.0)), client.level)
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
                        client, CompositionRulesClientLifecycle.synchronizedRules());
                assertHandshake(expectedPolicy);
            });
            assertServerCompositionDuringClientReset(context, singleplayer, expectedPolicy, expectedAmount);
        }
    }

    private static void assertServerCompositionDuringClientReset(
            ClientGameTestContext context, TestSingleplayerContext singleplayer,
            AttributePolicy expectedPolicy, double expectedAmount) {
        context.runOnClient(client -> {
            CompositionRulesClientLifecycle.onDisconnected();
            var input = new WorkbenchInput(source(Items.LEATHER_BOOTS, 2.0), source(Items.CHAINMAIL_BOOTS, 4.0));
            if (CompositionRulesClientLifecycle.areRulesSynchronized()
                    || new MixedBootsWorkbenchRecipe().assemblePreview(input, client.level).isComplete()) {
                throw new AssertionError("Client preview must be empty while no snapshot is installed");
            }
        });
        singleplayer.getServer().runOnServer(server -> {
            var left = source(Items.LEATHER_BOOTS, 2.0);
            var right = source(Items.CHAINMAIL_BOOTS, 4.0);
            var pair = new MixedBootsWorkbenchRecipe().assemblePair(new WorkbenchInput(left, right));
            if (!pair.isComplete() || attributeAmount(pair.primary()) != expectedAmount) {
                throw new AssertionError("Client reset changed integrated-server Workbench output");
            }
            assertComponentResult(pair.primary(), pair.primary());
            ItemStack recomposed = MixedBootsRecomposer.recompose(pair.primary(), left, right);
            if (attributeAmount(recomposed) != expectedAmount) {
                throw new AssertionError("Client reset changed integrated-server recomposition");
            }
            assertComponentResult(pair.primary(), recomposed);
        });
        // Restore through the native handshake, without installing a test-side snapshot.
        NetworkTrafficProbe.reset();
        context.runOnClient(client -> FabricCompositionRulesClientNetwork.onConnected());
        context.waitFor(client -> CompositionRulesClientLifecycle.areRulesSynchronized()
                && NetworkTrafficProbe.events().size() == 2);
        context.runOnClient(client -> assertHandshake(expectedPolicy));
    }

    private static void assertHandshake(AttributePolicy expectedPolicy) {
        List<NetworkTrafficProbe.Event> events = NetworkTrafficProbe.events();
        if (events.size() != 2
                || !events.get(0).name().equals("client_ready")
                || !events.get(1).name().equals("rules")) {
            throw new AssertionError("Unexpected rule synchronization traffic: " + events);
        }

        CompositionRulesPayload response = (CompositionRulesPayload) events.get(1).payload();
        if (response == null || !response.rules().equals(CompositionRulesClientLifecycle.synchronizedRules())) {
            throw new AssertionError("Client rules differ from the complete server snapshot");
        }
        var attributes = response.rules().attributes();
        if (attributes.policyFor(Identifier.withDefaultNamespace("movement_speed")) != expectedPolicy
                || attributes.policyFor(DORMANT_ATTRIBUTE) != AttributePolicy.MINIMUM) {
            throw new AssertionError("Server attribute snapshot omitted configured rules: " + attributes);
        }

        var components = response.rules().components();
        if (components.durability() != DurabilityPolicy.SEPARATE
                || components.repairCost() != RepairCostPolicy.OWNER
                || !ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.INT).equals(
                components.declarations().get(TestContent.NUMERIC_COMPONENT_ID))
                || !ComponentRule.of(ComponentPolicy.SHARE).equals(
                components.declarations().get(DORMANT_COMPONENT))
                || components.components().rules().containsKey(DORMANT_COMPONENT)) {
            throw new AssertionError("Server component snapshot omitted configured rules: " + components);
        }
    }

    private static void assertDisconnectedState(ClientGameTestContext context) {
        context.waitFor(client -> client.level == null);
        context.runOnClient(client -> {
            if (CompositionRulesClientLifecycle.synchronizedRules() != null) {
                throw new AssertionError("Disconnect retained the previous world's rules");
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
            net.minecraft.client.Minecraft client, CompositionRules before) {
        for (boolean malformedComponents : new boolean[]{false, true}) {
            RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                    Unpooled.buffer(), client.level.registryAccess());
            try {
                // A valid first section followed by an invalid second one must not publish either half.
                buffer.writeUtf("separate", 16);
                buffer.writeVarInt(malformedComponents ? 0 : 2);
                if (malformedComponents) {
                    buffer.writeUtf("separate", 16);
                    buffer.writeUtf("owner", 16);
                    buffer.writeVarInt(2);
                    writeComponentRule(buffer, "minecraft:rarity", "same");
                    writeComponentRule(buffer, "minecraft:rarity", "owner");
                } else {
                    buffer.writeUtf("minecraft:armor");
                    buffer.writeUtf("average", 16);
                    buffer.writeUtf("minecraft:armor");
                    buffer.writeUtf("combine", 16);
                }
                try {
                    CompositionRulesClientLifecycle.install(CompositionRulesPayload.STREAM_CODEC.decode(buffer).rules());
                    throw new AssertionError("Malformed duplicate snapshot was accepted");
                } catch (RuntimeException expected) {
                    if (CompositionRulesClientLifecycle.synchronizedRules() != before) {
                        throw new AssertionError("Malformed snapshot replaced client rules");
                    }
                }
            } finally {
                buffer.release();
            }
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
