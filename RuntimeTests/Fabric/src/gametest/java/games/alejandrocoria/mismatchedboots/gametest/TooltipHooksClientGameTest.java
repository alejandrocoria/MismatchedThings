package games.alejandrocoria.mismatchedboots.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class TooltipHooksClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        context.runOnClient(TooltipHooksClientGameTest::characterizeHooks);
    }

    private static void characterizeHooks(Minecraft client) {
        Item.TooltipContext tooltipContext = Item.TooltipContext.EMPTY;
        ItemStack stack = new ItemStack(TestContent.requireTooltipProbeItem());
        stack.set(TestContent.requireTooltipProbeComponent(), new TestContent.TooltipProbe("fabric-component"));
        ItemStack before = stack.copy();

        FabricTooltipHookProbe.reset();
        List<Component> full;
        client.options.keyShift.setDown(true);
        try {
            full = stack.getTooltipLines(tooltipContext, client.player, TooltipFlag.NORMAL);
        } finally {
            client.options.keyShift.setDown(false);
        }

        FabricTooltipHookProbe.Observation observation = FabricTooltipHookProbe.observation();
        assertSame(stack, observation.stack(), "callback stack");
        assertSame(tooltipContext, observation.context(), "callback context");
        if (observation.flag() != TooltipFlag.NORMAL || !observation.shiftDown()) {
            throw new AssertionError("Fabric callback lost the tooltip flag or client Shift state");
        }
        assertContains(observation.linesBeforeHook(), "tooltip.mismatchedboots_test.item_probe");
        assertContains(observation.linesBeforeHook(), "tooltip.mismatchedboots_test.component_probe");
        if (!full.getLast().equals(FabricTooltipHookProbe.FINAL_LINE)) {
            throw new AssertionError("Fabric final callback did not append after base details: " + full);
        }

        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        List<Component> nested = new ArrayList<>();
        FabricTooltipHookProbe.reset();
        stack.addDetailsToTooltip(
                tooltipContext, display, client.player, TooltipFlag.NORMAL, nested::add);
        if (FabricTooltipHookProbe.wasInvoked()) {
            throw new AssertionError("Nested source details recursively invoked the Fabric final callback");
        }
        assertContains(nested, "tooltip.mismatchedboots_test.item_probe");
        assertContains(nested, "tooltip.mismatchedboots_test.component_probe");
        if (!ItemStack.matches(before, stack)) {
            throw new AssertionError("Fabric tooltip characterization mutated its stack");
        }
        if (client.options.keyShift.isDown()) {
            throw new AssertionError("Fabric tooltip characterization left Shift pressed");
        }
    }

    private static void assertContains(List<Component> lines, String key) {
        if (lines.stream().noneMatch(line -> line.getContents()
                instanceof TranslatableContents contents
                && contents.getKey().equals(key))) {
            throw new AssertionError("Missing " + key + " in " + lines);
        }
    }

    private static void assertSame(Object expected, Object actual, String description) {
        if (actual != expected) {
            throw new AssertionError(description + " was not passed through unchanged");
        }
    }
}
