package games.alejandrocoria.mismatchedboots.runtimetest.viewer;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedboots.compatibility.jei.JeiRecipeReloadProbe;
import games.alejandrocoria.mismatchedboots.compatibility.rei.ReiRecipeReloadProbe;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesConfig;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.platform.Services;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings("UnstableApiUsage")
public final class RecipeViewerReloadClientGameTest implements FabricClientGameTest {
    public interface Probe {
        boolean ready();

        List<Cycle> cycles();

        void open();
    }

    public record Cycle(List<ItemStack> left, List<ItemStack> right,
                        List<ItemStack> primary, List<ItemStack> inverse) {
    }

    @Override
    public void runTest(ClientGameTestContext context) {
        String viewer = System.getProperty("mismatchedboots.test.recipeViewer");
        Probe probe = "jei".equals(viewer) ? new JeiRecipeReloadProbe() : new ReiRecipeReloadProbe();
        for (boolean recipesFirst : new boolean[]{true, false}) {
            String policy = recipesFirst ? "combine" : "minimum";
            try {
                Files.writeString(Services.PLATFORM.getConfigDirectory().resolve(AttributeRulesConfig.FILE_NAME),
                        "[attributes]\nfallback = 'separate'\n[attributes.\"minecraft:armor\"]\npolicy = '" + policy + "'\n");
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
            context.runOnClient(client -> RulesDeliveryGate.begin());
            AtomicReference<List<String>> originalPacks = new AtomicReference<>();
            try (TestSingleplayerContext world = context.worldBuilder().create()) {
                world.getServer().runOnServer(server -> originalPacks.set(List.copyOf(
                        server.getPackRepository().getSelectedIds())));
                context.waitFor(client -> RulesDeliveryGate.captured() && probe.ready());
                context.runOnClient(client -> {
                    assertNoExample(probe);
                    if (recipesFirst) {
                        RulesDeliveryGate.releaseRecipes();
                    } else {
                        RulesDeliveryGate.releaseRules();
                    }
                    assertNoExample(probe);
                });
                context.waitTicks(5);
                context.runOnClient(client -> {
                    assertNoExample(probe);
                    if (recipesFirst) {
                        RulesDeliveryGate.releaseRules();
                    } else {
                        RulesDeliveryGate.releaseRecipes();
                    }
                });
                verifyPhase(context, probe, 0);
                context.runOnClient(client -> assertServerRulesInExamples(probe,
                        recipesFirst ? AttributePolicy.COMBINE : AttributePolicy.MINIMUM));
                context.runOnClient(client -> RulesDeliveryGate.finish());
                reload(world, originalPacks.get(), 1);
                verifyPhase(context, probe, 1);
                reload(world, originalPacks.get(), 2);
                verifyPhase(context, probe, 2);
                reload(world, originalPacks.get(), 0);
                verifyPhase(context, probe, 0);
            } finally {
                context.runOnClient(client -> RulesDeliveryGate.finish());
            }
            context.waitFor(client -> !MismatchedWorkbenchClientRecipes.hasRecipes()
                    && !CompositionRulesClientLifecycle.areRulesSynchronized());
            context.runOnClient(client -> {
                if (probe.ready()) {
                    assertNoExample(probe);
                }
            });
        }
        try {
            Files.writeString(Path.of("viewer-reload.ok"), viewer + "-reload-passed\n");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void assertNoExample(Probe probe) {
        if (!probe.cycles().isEmpty()) {
            throw new AssertionError("Viewer exposed Boots before both synchronized inputs were available");
        }
    }

    private static void assertServerRulesInExamples(Probe probe, AttributePolicy expectedPolicy) {
        var rules = CompositionRulesClientLifecycle.synchronizedRules();
        if (rules == null || rules.attributes().policyFor(
                net.minecraft.resources.Identifier.withDefaultNamespace("armor")) != expectedPolicy) {
            throw new AssertionError("Viewer did not receive the current server's nondefault armor policy");
        }
        for (Cycle cycle : probe.cycles()) {
            for (int index = 0; index < cycle.left().size(); index++) {
                var expected = MixedBootsFactory.createPair(cycle.left().get(index), cycle.right().get(index),
                        rules.attributes(), rules.components());
                if (!ItemStack.isSameItemSameComponents(expected.primary(), cycle.primary().get(index))
                        || !ItemStack.isSameItemSameComponents(expected.inverse(), cycle.inverse().get(index))) {
                    throw new AssertionError("Actual viewer outputs did not use the complete server snapshot");
                }
            }
        }
    }

    private static void verifyPhase(ClientGameTestContext context, Probe probe, int mode) {
        // REI also queues asynchronous registry reloads after the loader packet.
        context.waitTicks(10);
        try {
            context.waitFor(client -> CompositionRulesClientLifecycle.areRulesSynchronized() && probe.ready()
                    && (mode == 1 ? !MismatchedWorkbenchClientRecipes.hasRecipes() && probe.cycles().isEmpty()
                                  : MismatchedWorkbenchClientRecipes.hasRecipes() && matches(probe.cycles(), mode == 2)));
        } catch (AssertionError failure) {
            context.runOnClient(client -> {
                throw new AssertionError("Viewer phase " + mode + ": rules="
                                                 + CompositionRulesClientLifecycle.areRulesSynchronized() + ", ready=" + probe.ready()
                                                 + ", recipes=" + MismatchedWorkbenchClientRecipes.hasRecipes()
                                                 + ", cycles=" + probe.cycles().size(), failure);
            });
            throw failure;
        }
        context.runOnClient(client -> {
            client.setScreen(null);
            if (mode == 1) {
                probe.open();
                if (!probe.cycles().isEmpty()) {
                    throw new AssertionError("Disabled recipe is still advertised");
                }
            } else {
                // Repeat while already open, then after closing: no duplicate page or stale outputs.
                for (int attempt = 0; attempt < 3; attempt++) {
                    probe.open();
                    if (client.screen == null || !matches(probe.cycles(), mode == 2)) {
                        throw new AssertionError("Repeated viewer reopening duplicated or changed the loaded example");
                    }
                }
                client.setScreen(null);
                probe.open();
                if (client.screen == null || !matches(probe.cycles(), mode == 2)) {
                    throw new AssertionError("Fresh reopening lost the loaded example");
                }
            }
            // Do not render REI's upstream mutable entry list during its next reload.
            // This test covers truthful fresh queries and repeated reopening, not
            // concurrent upstream registry mutation while a viewer screen renders.
            client.setScreen(null);
        });
        context.waitTicks(10);
        context.waitFor(client -> probe.ready());
    }

    private static boolean matches(List<Cycle> cycles, boolean reversed) {
        if (cycles.size() != 1) {
            return false;
        }

        Cycle cycle = cycles.getFirst();
        if (cycle.left().isEmpty() || cycle.left().size() != cycle.right().size()
                || cycle.left().size() != cycle.primary().size() || cycle.left().size() != cycle.inverse().size()) {
            return false;
        }
        for (int index = 0; index < cycle.left().size(); index++) {
            var primary = ModContent.getMixedBootsData(cycle.primary().get(index)).orElseThrow();
            var inverse = ModContent.getMixedBootsData(cycle.inverse().get(index)).orElseThrow();
            ItemStack left = (reversed ? cycle.right() : cycle.left()).get(index);
            ItemStack right = (reversed ? cycle.left() : cycle.right()).get(index);
            if (!ItemStack.matches(primary.left().source(), left) || !ItemStack.matches(primary.right().source(), right)
                    || !ItemStack.matches(inverse.left().source(), right) || !ItemStack.matches(inverse.right().source(), left)) {
                return false;
            }
        }

        return true;
    }

    private static void reload(TestSingleplayerContext world, List<String> original, int mode) {
        world.getServer().runOnServer(server -> {
            var selected = new ArrayList<>(original);
            if (mode != 0) {
                Path pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("viewer-recipe-reload");
                var format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
                try {
                    Files.createDirectories(pack.resolve("data/mismatchedboots/recipe"));
                    Files.writeString(pack.resolve("pack.mcmeta"), """
                            {"pack":{"description":"Viewer recipe reload fixture",
                            "min_format":[%d,%d],"max_format":[%d,%d]}}
                            """.formatted(format.major(), format.minor(), format.major(), format.minor()));
                    Files.writeString(pack.resolve("data/mismatchedboots/recipe/mixed_boots_workbench.json"),
                                      mode == 1 ? """
                                              {"fabric:load_conditions":[{"condition":"fabric:false"}],
                                              "type":"mismatchedboots:mixed_boots_workbench"}
                                              """ : "{\"type\":\"mismatchedboots-test:reversed_workbench\"}");
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
                server.getPackRepository().reload();
                selected.add("file/viewer-recipe-reload");
            }
            server.reloadResources(selected).join();
        });
    }
}
