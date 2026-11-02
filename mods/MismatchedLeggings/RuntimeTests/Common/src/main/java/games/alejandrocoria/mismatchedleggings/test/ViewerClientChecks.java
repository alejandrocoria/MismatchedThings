package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedleggings.compatibility.MismatchedWorkbenchExampleCycle;
import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.api.composition.AttributePolicy;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.storage.LevelResource;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Actual viewer queries/screens, native datapack reload, disconnect and changed-rule world reopen. */
final class ViewerClientChecks {
    private static final String LEGGINGS = "mismatchedleggings";
    private static final String BOOTS = "mismatchedboots";
    private static final boolean NONE = System.getProperty("leggings.test.viewer", "none").equals("none");
    private static int ticks;
    private static int mode;
    private static int nextCheck;
    private static int state;
    private static boolean secondSession;
    private static boolean finished;
    private static ViewerProbe probe;
    private static CompletableFuture<Void> job;
    private static List<String> originalPacks;

    private ViewerClientChecks() {
    }

    static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(++ticks < 5000, "Viewer timed out at state/mode " + state + "/" + mode + ": " + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated viewer client became visible");
            if (job != null) {
                if (!job.isDone()) {
                    return;
                }
                job.join();
                job = null;
                nextCheck = ticks + 30;
            }
            if (state == 1) {
                state = 2;
                client.disconnectFromWorld(net.minecraft.network.chat.Component.literal("Viewer session fixture"));
                client.setScreen(new TitleScreen());
                return;
            }
            if (state == 2) {
                if (client.level != null || client.player != null || client.getSingleplayerServer() != null) {
                    return;
                }
                require(CompositionRuleState.synchronizedPreview() == null && !MismatchedWorkbenchClientRecipes.hasRecipes(),
                        "Disconnect retained recipe/rule authority");
                if (probe.ready()) {
                    require(probe.cycles(LEGGINGS).isEmpty(), "Disconnect retained a visible Leggings example");
                }
                if (secondSession) {
                    SkeletonChecks.passed("client");
                    finished = true;
                    client.stop();
                    return;
                }
                secondSession = true;
                mode = 0;
                originalPacks = null;
                state = 0;
                client.createWorldOpenFlows().openWorld("skeleton", () -> client.setScreen(new TitleScreen()));
                return;
            }
            if (client.level == null || client.player == null || client.getSingleplayerServer() == null
                    || client.getOverlay() != null || ticks < nextCheck) {
                return;
            }
            if (probe == null) {
                probe = NONE ? new NoViewerProbe() : System.getProperty("leggings.test.viewer").equals("jei")
                        ? new JeiViewerProbe() : new ReiViewerProbe();
            }
            var rules = CompositionRuleState.synchronizedPreview();
            if (rules == null || rules.attributes().fallback() != (secondSession ? AttributePolicy.MINIMUM : AttributePolicy.MAXIMUM)
                    || !probe.ready()) {
                return;
            }
            var cycles = probe.cycles(LEGGINGS);
            if (!matches(cycles, mode)) {
                return;
            }
            if (!NONE) {
                boolean legacyBoots = Boolean.getBoolean("leggings.test.previousBoots");
                probe.verifyCategories(legacyBoots);
                int expected = (mode == 1 ? 0 : 1)
                        + (Boolean.getBoolean("leggings.test.boots") && !legacyBoots ? 1 : 0);
                require(probe.cycles("mismatchedthings").size() == expected,
                        "Shared category contains missing, duplicated or uninstalled contributions");
            }
            if (Boolean.getBoolean("leggings.test.boots")) {
                var boots = probe.cycles(BOOTS);
                require(NONE || boots.size() == 1, "Leggings removed/duplicated the Boots viewer example");
            }
            if (!NONE && mode == 0) {
                boolean currentBoots = Boolean.getBoolean("leggings.test.boots")
                        && !Boolean.getBoolean("leggings.test.previousBoots");
                probe.verifyUsages(ViewerUsageChecks.samples(currentBoots));
            }
            if (!NONE && mode != 1) {
                for (int attempt = 0; attempt < 3; attempt++) {
                    probe.open(LEGGINGS);
                    require(client.screen != null && matches(probe.cycles(LEGGINGS), mode), "Repeated open duplicated/staled the Leggings display");
                }
                client.setScreen(null);
                probe.open(LEGGINGS);
                require(client.screen != null && matches(probe.cycles(LEGGINGS), mode), "Fresh open lost the Leggings display");
                probe.verifyOutputTooltips(LEGGINGS);
                if (Boolean.getBoolean("leggings.test.boots")) {
                    probe.open(BOOTS);
                    require(probe.cycles(BOOTS).size() == 1 && matches(probe.cycles(LEGGINGS), mode), "Opening Boots changed Leggings examples");
                    probe.verifyOutputTooltips(BOOTS);
                }
            }
            // Do not render an upstream mutable REI entry list while the next native reload runs.
            client.setScreen(null);
            System.out.println("Viewer phase verified: secondSession=" + secondSession + ", mode=" + mode);
            if (secondSession || mode == 3) {
                job = client.getSingleplayerServer().submit(() -> {
                    try {
                        WorkbenchChecks.writeRules(Path.of("config"), "minimum");
                    } catch (Exception failure) {
                        throw new IllegalStateException(failure);
                    }
                });
                state = 1;
                return;
            }
            int requested = ++mode;
            job = client.getSingleplayerServer().submit(() -> reload(client, requested));
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static boolean matches(List<ViewerProbe.Cycle> cycles, int mode) {
        if (mode == 1) {
            return cycles.isEmpty();
        }
        if (cycles.size() != 1 || cycles.getFirst().left().isEmpty()) {
            return false;
        }
        var cycle = cycles.getFirst();
        require(cycle.left().size() == cycle.right().size() && cycle.left().size() == cycle.primary().size()
                        && cycle.left().size() == cycle.inverse().size(), "Viewer cycle slots lost their shared index");
        for (int index = 0; index < cycle.left().size(); index++) {
            var primary = MixedLeggingsStructure.validData(cycle.primary().get(index)).orElseThrow();
            var inverse = MixedLeggingsStructure.validData(cycle.inverse().get(index)).orElseThrow();
            var left = (mode == 2 ? cycle.right() : cycle.left()).get(index);
            var right = (mode == 2 ? cycle.left() : cycle.right()).get(index);
            if (!ItemStack.matches(primary.left().source(), left) || !ItemStack.matches(primary.right().source(), right)
                    || !ItemStack.matches(inverse.left().source(), right) || !ItemStack.matches(inverse.right().source(), left)) {
                return false;
            }
            double expected = secondSession ? Math.min(armor(left), armor(right)) : Math.max(armor(left), armor(right));
            require(armor(cycle.primary().get(index)) == expected, "Viewer output ignored synchronized session rules");
        }
        return true;
    }

    private static double armor(ItemStack stack) {
        return stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().stream()
                .filter(entry -> entry.attribute().equals(Attributes.ARMOR)).mapToDouble(entry -> entry.modifier().amount()).sum();
    }

    private static void reload(Minecraft client, int mode) {
        var server = client.getSingleplayerServer();
        if (originalPacks == null) {
            originalPacks = List.copyOf(server.getPackRepository().getSelectedIds());
        }
        var selected = new ArrayList<>(originalPacks);
        if (mode != 3) {
            var pack = server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("leggings-viewer-reload");
            var format = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
            try {
                Files.createDirectories(pack.resolve("data/mismatchedleggings/recipe"));
                Files.writeString(pack.resolve("pack.mcmeta"), """
                        {"pack":{"description":"Leggings viewer fixture","min_format":[%d,%d],"max_format":[%d,%d]}}
                        """.formatted(format.major(), format.minor(), format.major(), format.minor()));
                Files.writeString(pack.resolve("data/mismatchedleggings/recipe/mixed_leggings_workbench.json"), mode == 1 ? """
                        {"type":"minecraft:crafting_shapeless","ingredients":["minecraft:dirt"],"result":{"id":"minecraft:dirt"}}
                        """ : "{\"type\":\"leggings_skeleton_test:reversed_workbench\"}");
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
            server.getPackRepository().reload();
            selected.add("file/leggings-viewer-reload");
        }
        server.reloadResources(selected).join();
    }

    private static final class NoViewerProbe implements ViewerProbe {
        @Override
        public boolean ready() {
            return true;
        }

        @Override
        public List<Cycle> cycles(String namespace) {
            if (!namespace.equals(LEGGINGS) || Minecraft.getInstance().level == null) {
                return List.of();
            }
            return MismatchedWorkbenchExampleCycle.create(List.of(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.GOLDEN_LEGGINGS)),
                    (left, right) -> MismatchedWorkbenchClientRecipes.assemblePair(new WorkbenchInput(left, right), Minecraft.getInstance().level))
                    .map(example -> List.of(new Cycle(example.leftInputs(), example.rightInputs(), example.primaryOutputs(), example.inverseOutputs())))
                    .orElse(List.of());
        }

        @Override
        public void open(String namespace) {
            throw new AssertionError("No viewer should be invoked");
        }
    }
}
