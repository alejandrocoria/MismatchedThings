package games.alejandrocoria.mismatchedboots.gametest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.item.v1.ItemComponentTooltipProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public final class FabricTooltipHookProbe implements ClientModInitializer {
    static final Component FINAL_LINE = Component.translatable("tooltip.mismatchedboots_test.fabric_final_hook");

    private static Observation observation;

    @Override
    public void onInitializeClient() {
        ItemComponentTooltipProviderRegistry.addLast(TestContent.requireTooltipProbeComponent());
        ItemTooltipCallback.EVENT.register(FabricTooltipHookProbe::onTooltip);
    }

    static void reset() {
        observation = null;
    }

    static Observation observation() {
        if (observation == null) {
            throw new AssertionError("Fabric tooltip callback was not invoked");
        }
        return observation;
    }

    static boolean wasInvoked() {
        return observation != null;
    }

    private static void onTooltip(
            ItemStack stack,
            Item.TooltipContext context,
            TooltipFlag flag,
            List<Component> lines) {
        if (!stack.has(TestContent.requireTooltipProbeComponent())) {
            return;
        }
        observation = new Observation(
                stack,
                context,
                flag,
                List.copyOf(lines),
                Minecraft.getInstance().options.keyShift.isDown());
        lines.add(FINAL_LINE);
    }

    record Observation(
            ItemStack stack,
            Item.TooltipContext context,
            TooltipFlag flag,
            List<Component> linesBeforeHook,
            boolean shiftDown) {
    }
}
