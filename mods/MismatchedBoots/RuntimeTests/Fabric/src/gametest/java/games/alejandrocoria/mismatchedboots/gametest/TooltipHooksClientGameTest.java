package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchInput;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchResult;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class TooltipHooksClientGameTest implements FabricClientGameTest {
    private static final String LEFT_KEY = "tooltip.mismatchedboots.mixed_boots.left";
    private static final String RIGHT_KEY = "tooltip.mismatchedboots.mixed_boots.right";
    private static final String NAME_WITH_NATURAL_KEY =
            "tooltip.mismatchedboots.mixed_boots.name_with_natural";
    private static final String ITEM_PROBE_KEY = "tooltip.mismatchedboots_test.item_probe";
    private static final String COMPONENT_PROBE_KEY = "tooltip.mismatchedboots_test.component_probe";

    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(client -> {
            assertUnattended(client);
            characterizeHooks(client);
            acceptProductionTooltip(client);
        });
    }

    private static void characterizeHooks(Minecraft client) {
        Item.TooltipContext tooltipContext = Item.TooltipContext.EMPTY;
        ItemStack stack = new ItemStack(TestContent.requireTooltipProbeItem());
        stack.set(TestContent.requireTooltipProbeComponent(), new TestContent.TooltipProbe("fabric-component"));
        ItemStack before = stack.copy();

        resetObservations();
        List<Component> full;
        client.options.keyShift.setDown(true);
        try {
            full = stack.getTooltipLines(tooltipContext, client.player, TooltipFlag.NORMAL);
        } finally {
            client.options.keyShift.setDown(false);
        }

        FabricTooltipHookProbe.Observation finalHook = FabricTooltipHookProbe.observation();
        assertSame(stack, finalHook.stack(), "callback stack");
        assertSame(tooltipContext, finalHook.context(), "callback context");
        if (finalHook.flag() != TooltipFlag.NORMAL || !finalHook.shiftDown()) {
            throw new AssertionError("Fabric callback lost the tooltip flag or client Shift state");
        }
        if (FabricTooltipHookProbe.invocationCount() != 1) {
            throw new AssertionError("Fabric final callback ran more than once for a complete tooltip");
        }

        TestContent.ItemTooltipObservation item = onlyItemObservation();
        assertSame(stack, item.stack(), "item callback stack");
        assertSame(tooltipContext, item.context(), "item callback context");
        assertSame(stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT),
                   item.display(), "item callback display");
        if (item.flag() != TooltipFlag.NORMAL) {
            throw new AssertionError("Fabric item callback lost the tooltip flag");
        }

        TestContent.ComponentTooltipObservation component = onlyComponentObservation();
        assertSame(tooltipContext, component.context(), "component provider context");
        if (component.flag() != TooltipFlag.NORMAL || !component.value().equals("fabric-component")) {
            throw new AssertionError("Fabric component provider lost its value or flag: " + component);
        }
        assertContains(finalHook.linesBeforeHook(), ITEM_PROBE_KEY);
        assertContains(finalHook.linesBeforeHook(), COMPONENT_PROBE_KEY);
        if (!full.getLast().equals(FabricTooltipHookProbe.FINAL_LINE)) {
            throw new AssertionError("Fabric final callback did not append after base details: " + full);
        }

        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        List<Component> nested = new ArrayList<>();
        resetObservations();
        stack.addDetailsToTooltip(
                tooltipContext, display, client.player, TooltipFlag.NORMAL, nested::add);
        if (FabricTooltipHookProbe.wasInvoked()) {
            throw new AssertionError("Nested source details recursively invoked the Fabric final callback");
        }
        assertContains(nested, ITEM_PROBE_KEY);
        assertContains(nested, COMPONENT_PROBE_KEY);
        onlyItemObservation();
        onlyComponentObservation();
        if (!ItemStack.matches(before, stack)) {
            throw new AssertionError("Fabric tooltip characterization mutated its stack");
        }
        if (client.options.keyShift.isDown()) {
            throw new AssertionError("Fabric tooltip characterization left Shift pressed");
        }
    }

    private static void acceptProductionTooltip(Minecraft client) {
        ItemStack source = new ItemStack(TestContent.requireTooltipProbeItem());
        source.set(DataComponents.CUSTOM_NAME, Component.literal("Fabric source name"));
        source.set(TestContent.requireTooltipProbeComponent(), new TestContent.TooltipProbe("source-visible"));
        ItemStack sourceBefore = source.copy();
        MismatchedWorkbenchResult pair = pair(source, new ItemStack(Items.GOLDEN_BOOTS));

        assertProductionOutput(client, pair.primary(), source, true,
                               TooltipFlag.NORMAL, false, "root-primary");
        assertProductionOutput(client, pair.inverse(), source, false,
                               TooltipFlag.ADVANCED, true, "root-inverse");
        if (!ItemStack.matches(sourceBefore, source)) {
            throw new AssertionError("Workbench tooltip fixture mutated its source input");
        }

        ItemStack hiddenComponent = new ItemStack(TestContent.requireTooltipProbeItem());
        hiddenComponent.set(TestContent.requireTooltipProbeComponent(),
                            new TestContent.TooltipProbe("source-hidden-component"));
        hiddenComponent.set(DataComponents.TOOLTIP_DISPLAY,
                            TooltipDisplay.DEFAULT.withHidden(TestContent.requireTooltipProbeComponent(), true));
        assertHiddenSourceCase(client, pair(hiddenComponent, new ItemStack(Items.GOLDEN_BOOTS)).primary(),
                               "source-hidden-component", "root-hidden-component", false);

        ItemStack hiddenTooltip = new ItemStack(TestContent.requireTooltipProbeItem());
        hiddenTooltip.set(TestContent.requireTooltipProbeComponent(),
                          new TestContent.TooltipProbe("source-hidden-tooltip"));
        hiddenTooltip.set(DataComponents.TOOLTIP_DISPLAY,
                          new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));
        assertHiddenSourceCase(client, pair(hiddenTooltip, new ItemStack(Items.GOLDEN_BOOTS)).primary(),
                               "source-hidden-tooltip", "root-hidden-tooltip", true);
    }

    private static void assertProductionOutput(
            Minecraft client,
            ItemStack output,
            ItemStack expectedSource,
            boolean sourceOnLeft,
            TooltipFlag rootFlag,
            boolean shiftDown,
            String rootValue) {
        output.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        output.set(TestContent.requireTooltipProbeComponent(), new TestContent.TooltipProbe(rootValue));
        ItemStack before = output.copy();
        MixedBootsData dataBefore = ModContent.getMixedBootsData(output).orElseThrow();
        Item.TooltipContext tooltipContext = Item.TooltipContext.EMPTY;

        resetObservations();
        List<Component> lines;
        client.options.keyShift.setDown(shiftDown);
        try {
            lines = output.getTooltipLines(tooltipContext, client.player, rootFlag);
        } finally {
            client.options.keyShift.setDown(false);
        }

        FabricTooltipHookProbe.Observation finalHook = FabricTooltipHookProbe.observation();
        if (FabricTooltipHookProbe.invocationCount() != 1) {
            throw new AssertionError("Fabric final callback did not run exactly once for Mismatched Boots");
        }
        assertSame(output, finalHook.stack(), "Mismatched final-hook stack");
        assertSame(tooltipContext, finalHook.context(), "Mismatched final-hook context");
        if (finalHook.flag() != rootFlag || finalHook.shiftDown() != shiftDown) {
            throw new AssertionError("Mismatched final hook lost root flag or Shift state");
        }
        if (!finalHook.linesBeforeHook().equals(lines.subList(0, lines.size() - 1))
                || !lines.getLast().equals(FabricTooltipHookProbe.FINAL_LINE)) {
            throw new AssertionError("Fabric final hook did not receive the completed root tooltip: " + lines);
        }

        TestContent.ItemTooltipObservation item = onlyItemObservation();
        if (!ItemStack.matches(expectedSource, item.stack())) {
            throw new AssertionError("Source item callback received the wrong snapshot: " + item.stack());
        }
        assertSame(tooltipContext, item.context(), "source item callback context");
        if (item.flag() != rootFlag
                || !item.display().hiddenComponents().contains(DataComponents.ENCHANTMENTS)) {
            throw new AssertionError("Source item callback did not receive the root flag and filtered details: "
                                             + item);
        }

        List<TestContent.ComponentTooltipObservation> componentObservations =
                TestContent.componentTooltipObservations();
        if (componentObservations.size() != 2
                || !componentObservations.get(0).value().equals("source-visible")
                || componentObservations.get(0).flag() != rootFlag
                || componentObservations.get(0).context() != tooltipContext
                || !componentObservations.get(1).value().equals(rootValue)
                || componentObservations.get(1).flag() != rootFlag
                || componentObservations.get(1).context() != tooltipContext) {
            throw new AssertionError("Unexpected source/root component provider calls: " + componentObservations);
        }

        int left = indexOfTranslation(lines, LEFT_KEY);
        int right = indexOfTranslation(lines, RIGHT_KEY);
        int itemLine = indexOfTranslation(lines, ITEM_PROBE_KEY);
        int sourceComponent = indexOfTranslation(lines, COMPONENT_PROBE_KEY, "source-visible");
        int rootComponent = indexOfTranslation(lines, COMPONENT_PROBE_KEY, rootValue);
        int sourceStart = sourceOnLeft ? left : right;
        int sourceEnd = sourceOnLeft ? right : rootComponent;
        if (left < 0 || right <= left || itemLine <= sourceStart || itemLine >= sourceEnd
                || sourceComponent <= itemLine || sourceComponent >= sourceEnd
                || rootComponent <= right || countTranslation(lines, ITEM_PROBE_KEY) != 1
                || countTranslation(lines, COMPONENT_PROBE_KEY) != 2) {
            throw new AssertionError("Fabric contributions escaped their source/root regions: " + lines);
        }

        Component sourceIdentity = lines.get(sourceStart);
        if (!containsTranslation(sourceIdentity, NAME_WITH_NATURAL_KEY)) {
            throw new AssertionError("Custom source identity omitted its natural name: " + sourceIdentity);
        }
        if (!ItemStack.matches(before, output)
                || !dataBefore.equals(ModContent.getMixedBootsData(output).orElseThrow())) {
            throw new AssertionError("Production Fabric tooltip mutated its root or source snapshots");
        }
    }

    private static void assertHiddenSourceCase(
            Minecraft client,
            ItemStack output,
            String hiddenSourceValue,
            String rootValue,
            boolean hideWholeTooltip) {
        output.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        output.set(TestContent.requireTooltipProbeComponent(), new TestContent.TooltipProbe(rootValue));
        ItemStack before = output.copy();
        MixedBootsData dataBefore = ModContent.getMixedBootsData(output).orElseThrow();

        resetObservations();
        List<Component> lines = output.getTooltipLines(
                Item.TooltipContext.EMPTY, client.player, TooltipFlag.NORMAL);

        if (FabricTooltipHookProbe.invocationCount() != 1
                || indexOfTranslation(lines, LEFT_KEY) < 0
                || indexOfTranslation(lines, COMPONENT_PROBE_KEY, hiddenSourceValue) >= 0
                || indexOfTranslation(lines, COMPONENT_PROBE_KEY, rootValue) < 0) {
            throw new AssertionError("Hidden Fabric source leaked or hid live root details: " + lines);
        }
        if (TestContent.componentTooltipObservations().size() != 1
                || !TestContent.componentTooltipObservations().getFirst().value().equals(rootValue)) {
            throw new AssertionError("Hidden source component provider was invoked: "
                                             + TestContent.componentTooltipObservations());
        }
        if (hideWholeTooltip) {
            if (!TestContent.itemTooltipObservations().isEmpty()
                    || indexOfTranslation(lines, ITEM_PROBE_KEY) >= 0) {
                throw new AssertionError("Fully hidden source emitted nested item details: " + lines);
            }
        } else {
            TestContent.ItemTooltipObservation item = onlyItemObservation();
            if (!item.display().hiddenComponents().contains(TestContent.requireTooltipProbeComponent())
                    || !item.display().hiddenComponents().contains(DataComponents.ENCHANTMENTS)
                    || indexOfTranslation(lines, ITEM_PROBE_KEY) < 0) {
                throw new AssertionError("Source display hiding was not composed with gameplay hiding: " + item);
            }
        }
        if (!ItemStack.matches(before, output)
                || !dataBefore.equals(ModContent.getMixedBootsData(output).orElseThrow())) {
            throw new AssertionError("Hidden-source tooltip mutated its root or snapshots");
        }
    }

    private static MismatchedWorkbenchResult pair(ItemStack left, ItemStack right) {
        MismatchedWorkbenchResult result = new MixedBootsWorkbenchRecipe()
                .assemblePair(new MismatchedWorkbenchInput(left, right));
        if (!result.isComplete()) {
            throw new AssertionError("Production Workbench recipe rejected tooltip fixtures");
        }
        return result;
    }

    private static void resetObservations() {
        FabricTooltipHookProbe.reset();
        TestContent.resetTooltipObservations();
    }

    private static TestContent.ItemTooltipObservation onlyItemObservation() {
        List<TestContent.ItemTooltipObservation> observations = TestContent.itemTooltipObservations();
        if (observations.size() != 1) {
            throw new AssertionError("Expected one item callback observation, got " + observations);
        }
        return observations.getFirst();
    }

    private static TestContent.ComponentTooltipObservation onlyComponentObservation() {
        List<TestContent.ComponentTooltipObservation> observations =
                TestContent.componentTooltipObservations();
        if (observations.size() != 1) {
            throw new AssertionError("Expected one component provider observation, got " + observations);
        }
        return observations.getFirst();
    }

    private static void assertContains(List<Component> lines, String key) {
        if (indexOfTranslation(lines, key) < 0) {
            throw new AssertionError("Missing " + key + " in " + lines);
        }
    }

    private static int indexOfTranslation(List<Component> lines, String key) {
        for (int index = 0; index < lines.size(); index++) {
            if (containsTranslation(lines.get(index), key)) {
                return index;
            }
        }
        return -1;
    }

    private static int indexOfTranslation(List<Component> lines, String key, String argument) {
        for (int index = 0; index < lines.size(); index++) {
            if (containsTranslation(lines.get(index), key, argument)) {
                return index;
            }
        }
        return -1;
    }

    private static long countTranslation(List<Component> lines, String key) {
        return lines.stream().filter(line -> containsTranslation(line, key)).count();
    }

    private static boolean containsTranslation(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents contents) {
            if (contents.getKey().equals(key)) {
                return true;
            }
            for (Object argument : contents.getArgs()) {
                if (argument instanceof Component nested && containsTranslation(nested, key)) {
                    return true;
                }
            }
        }
        return component.getSiblings().stream().anyMatch(sibling -> containsTranslation(sibling, key));
    }

    private static boolean containsTranslation(Component component, String key, String argument) {
        if (component.getContents() instanceof TranslatableContents contents) {
            if (contents.getKey().equals(key)
                    && List.of(contents.getArgs()).contains(argument)) {
                return true;
            }
            for (Object nestedArgument : contents.getArgs()) {
                if (nestedArgument instanceof Component nested
                        && containsTranslation(nested, key, argument)) {
                    return true;
                }
            }
        }

        return component.getSiblings().stream()
                        .anyMatch(sibling -> containsTranslation(sibling, key, argument));
    }

    private static void assertSame(Object expected, Object actual, String description) {
        if (actual != expected) {
            throw new AssertionError(description + " was not passed through unchanged");
        }
    }

    private static void assertUnattended(Minecraft client) {
        if (client.options.getSoundSourceVolume(SoundSource.MASTER) != 0.0F) {
            throw new AssertionError("Tooltip client master volume is not muted");
        }
        if (Boolean.getBoolean("mismatchedboots.test.hideClientWindow")
                && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE)
                != GLFW.GLFW_FALSE) {
            throw new AssertionError("Tooltip client window is visible");
        }
    }
}
