package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.common.tooltip.TooltipLocation;
import net.neoforged.neoforge.event.RegisterTooltipAppendersEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class NeoForgeTooltipClientTest {
    private static final Component APPENDER_LINE =
            Component.translatable("tooltip.mismatchedboots_runtime_test.neoforge_appender");
    private static final Component FINAL_LINE =
            Component.translatable("tooltip.mismatchedboots_runtime_test.neoforge_final_hook");
    private static final int STARTUP_TICKS = 20;
    private static final int TIMEOUT_TICKS = 3_600;

    private static int ticks;
    private static boolean finished;
    private static AppenderObservation appenderObservation;
    private static FinalObservation finalObservation;

    private NeoForgeTooltipClientTest() {
    }

    public static void registerAppenders(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, NeoForgeTooltipClientTest::appendTooltip);
    }

    public static void onTooltip(ItemTooltipEvent event) {
        finalObservation = new FinalObservation(
                event.getItemStack(),
                event.getContext(),
                event.getDisplay(),
                event.getEntity(),
                event.getFlags(),
                List.copyOf(event.getToolTip()));
        event.getToolTip().add(FINAL_LINE);
    }

    public static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            ticks++;
            assertUnattended(client);
            if (ticks > TIMEOUT_TICKS) {
                throw new AssertionError("Tooltip client timed out during startup");
            }
            if (ticks < STARTUP_TICKS) {
                return;
            }

            characterize(client);
            NetworkAcceptanceFiles.write("tooltip-report.txt",
                    "finalHook.afterBase=true\n"
                            + "finalHook.recursesForNestedDetails=false\n"
                            + "appender.runsForNestedDetails=true\n"
                            + "finalHook.context=true\n"
                            + "finalHook.display=true\n"
                            + "finalHook.playerNullable=true\n"
                            + "shift.requiresClientState=true\n");
            NetworkAcceptanceFiles.write("tooltip-success.txt", "neoforge\n");
            finished = true;
            client.stop();
        } catch (Throwable throwable) {
            NetworkAcceptanceFiles.failure("tooltip-client", throwable);
            finished = true;
            client.stop();
        }
    }

    private static void characterize(Minecraft client) {
        DataComponentMap components = DataComponentMap.builder()
                .set(DataComponents.MAX_STACK_SIZE, 1)
                .set(DataComponents.ITEM_NAME, Component.translatable(Items.IRON_BOOTS.getDescriptionId()))
                .set(DataComponents.ITEM_MODEL, BuiltInRegistries.ITEM.getKey(Items.IRON_BOOTS))
                .build();
        ItemStack stack = new ItemStack(Holder.direct(Items.IRON_BOOTS, components), 1);
        ItemStack before = stack.copy();
        Item.TooltipContext context = Item.TooltipContext.EMPTY;
        TooltipDisplay display = stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);

        resetObservations();
        List<Component> full = stack.getTooltipLines(context, null, TooltipFlag.NORMAL);

        AppenderObservation appender = requireAppenderObservation();
        FinalObservation finalHook = requireFinalObservation();
        assertSame(stack, appender.stack(), "appender stack");
        assertSame(context, appender.context(), "appender context");
        assertSame(display, appender.display(), "appender display");
        assertSame(stack, finalHook.stack(), "final-hook stack");
        assertSame(context, finalHook.context(), "final-hook context");
        assertSame(display, finalHook.display(), "final-hook display");
        if (appender.player() != null || finalHook.player() != null) {
            throw new AssertionError("NeoForge hooks changed a null player");
        }
        if (appender.flag() != TooltipFlag.NORMAL || finalHook.flag() != TooltipFlag.NORMAL) {
            throw new AssertionError("NeoForge hooks changed the tooltip flag");
        }
        if (!finalHook.linesBeforeHook().contains(APPENDER_LINE)
                || !full.getLast().equals(FINAL_LINE)) {
            throw new AssertionError("NeoForge final event did not run after base appenders: " + full);
        }

        resetObservations();
        List<Component> nested = new ArrayList<>();
        stack.addDetailsToTooltip(context, display, null, TooltipFlag.NORMAL, nested::add);
        appender = requireAppenderObservation();
        assertSame(stack, appender.stack(), "nested appender stack");
        assertSame(context, appender.context(), "nested appender context");
        assertSame(display, appender.display(), "nested appender display");
        if (finalObservation != null) {
            throw new AssertionError("Nested source details recursively invoked the NeoForge final event");
        }
        if (!nested.contains(APPENDER_LINE)) {
            throw new AssertionError("Nested details omitted the NeoForge tooltip appender: " + nested);
        }
        if (!ItemStack.matches(before, stack)) {
            throw new AssertionError("NeoForge tooltip characterization mutated its stack");
        }
    }

    private static void appendTooltip(
            ItemStack stack,
            Item.TooltipContext context,
            TooltipDisplay display,
            Player player,
            TooltipFlag flag,
            java.util.function.Consumer<Component> builder) {
        appenderObservation = new AppenderObservation(stack, context, display, player, flag);
        builder.accept(APPENDER_LINE);
    }

    private static void resetObservations() {
        appenderObservation = null;
        finalObservation = null;
    }

    private static AppenderObservation requireAppenderObservation() {
        if (appenderObservation == null) {
            throw new AssertionError("NeoForge tooltip appender was not invoked");
        }
        return appenderObservation;
    }

    private static FinalObservation requireFinalObservation() {
        if (finalObservation == null) {
            throw new AssertionError("NeoForge final tooltip event was not invoked");
        }
        return finalObservation;
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
                && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) != GLFW.GLFW_FALSE) {
            throw new AssertionError("Tooltip client window is visible");
        }
    }

    private record AppenderObservation(
            ItemStack stack,
            Item.TooltipContext context,
            TooltipDisplay display,
            Player player,
            TooltipFlag flag) {
    }

    private record FinalObservation(
            ItemStack stack,
            Item.TooltipContext context,
            TooltipDisplay display,
            Player player,
            TooltipFlag flag,
            List<Component> linesBeforeHook) {
    }
}
