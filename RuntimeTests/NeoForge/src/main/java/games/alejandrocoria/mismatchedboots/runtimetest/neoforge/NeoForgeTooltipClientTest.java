package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.Equippable;
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
    private static final String LEFT_KEY = "tooltip.mismatchedboots.mixed_boots.left";
    private static final String RIGHT_KEY = "tooltip.mismatchedboots.mixed_boots.right";
    private static final int STARTUP_TICKS = 20;
    private static final int TIMEOUT_TICKS = 3_600;

    private static int ticks;
    private static boolean finished;
    private static final List<AppenderObservation> APPENDER_OBSERVATIONS = new ArrayList<>();
    private static FinalObservation finalObservation;
    private static int finalInvocationCount;

    private NeoForgeTooltipClientTest() {
    }

    public static void registerAppenders(RegisterTooltipAppendersEvent event) {
        event.registerAppender(TooltipLocation.POST_CUSTOM, NeoForgeTooltipClientTest::appendTooltip);
    }

    public static void onTooltip(ItemTooltipEvent event) {
        finalInvocationCount++;
        finalObservation = new FinalObservation(
                event.getItemStack(),
                event.getContext(),
                event.getItemStack().getOrDefault(
                        DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT),
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
            acceptProductionTooltip();
            NetworkAcceptanceFiles.write("tooltip-report.txt",
                                         "finalHook.afterBase=true\n"
                                                 + "finalHook.recursesForNestedDetails=false\n"
                                                 + "appender.runsForNestedDetails=true\n"
                                                 + "finalHook.context=true\n"
                                                 + "finalHook.display=true\n"
                                                 + "finalHook.playerNullable=true\n"
                                                 + "production.primaryInverse=true\n"
                                                 + "production.sourceAppenderInRegion=true\n"
                                                 + "production.finalHookAfterRoot=true\n"
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

    private static void acceptProductionTooltip() {
        ItemStack left = source(Items.IRON_BOOTS);
        left.set(DataComponents.CUSTOM_NAME, Component.literal("NeoForge source name"));
        TooltipDisplay sourceDisplay = TooltipDisplay.DEFAULT.withHidden(DataComponents.TRIM, true);
        left.set(DataComponents.TOOLTIP_DISPLAY, sourceDisplay);
        ItemStack leftBefore = left.copy();
        ItemStack right = source(Items.GOLDEN_BOOTS);
        ItemStack rightBefore = right.copy();

        assertProductionOutput(mixed(left, right), left, true, TooltipFlag.NORMAL);
        assertProductionOutput(mixed(right, left), left, false, TooltipFlag.ADVANCED);
        if (!ItemStack.matches(leftBefore, left) || !ItemStack.matches(rightBefore, right)) {
            throw new AssertionError("NeoForge production tooltip fixture mutated its inputs");
        }
    }

    private static ItemStack source(Item item) {
        DataComponentPatch components = DataComponentPatch.builder()
                                                          .set(DataComponents.MAX_STACK_SIZE, 1)
                                                          .set(DataComponents.ITEM_NAME, Component.translatable(item.getDescriptionId()))
                                                          .set(DataComponents.ITEM_MODEL, BuiltInRegistries.ITEM.getKey(item))
                                                          .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build())
                                                          .build();
        return new ItemStack(Holder.direct(item), 1, components);
    }

    private static ItemStack mixed(ItemStack left, ItemStack right) {
        DataComponentType<MixedBootsData> dataType = ModContent.mixedBootsDataType()
                                                               .orElseThrow(() -> new AssertionError(
                                                                       "Mismatched boots data component is not registered"));
        Item mixedItem = BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID);
        if (mixedItem == null) {
            throw new AssertionError("Mismatched boots item is not registered");
        }

        DataComponentPatch components = DataComponentPatch.builder()
                                                          .set(DataComponents.MAX_STACK_SIZE, 1)
                                                          .set(DataComponents.ITEM_NAME, Component.translatable("item.mismatchedboots.mixed_boots"))
                                                          .set(DataComponents.ITEM_MODEL, ModContent.MIXED_BOOTS_ID)
                                                          .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build())
                                                          .set(dataType, new MixedBootsData(new BootHalf(left), new BootHalf(right)))
                                                          .build();
        return new ItemStack(Holder.direct(mixedItem), 1, components);
    }

    private static void assertProductionOutput(
            ItemStack output,
            ItemStack expectedSource,
            boolean sourceOnLeft,
            TooltipFlag rootFlag) {
        output.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        ItemStack before = output.copy();
        MixedBootsData dataBefore = ModContent.getMixedBootsData(output).orElseThrow();
        Item.TooltipContext context = Item.TooltipContext.EMPTY;
        TooltipDisplay rootDisplay = output.getOrDefault(
                DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);

        resetObservations();
        List<Component> full = output.getTooltipLines(context, null, rootFlag);

        AppenderObservation appender = requireAppenderObservation();
        if (!ItemStack.matches(expectedSource, appender.stack())) {
            throw new AssertionError("NeoForge appender received the wrong source snapshot: "
                                             + appender.stack());
        }
        assertSame(context, appender.context(), "source appender context");
        if (appender.player() != null || appender.flag() != rootFlag
                || !appender.display().hiddenComponents().contains(DataComponents.TRIM)
                || !appender.display().hiddenComponents().contains(DataComponents.ENCHANTMENTS)) {
            throw new AssertionError("NeoForge source appender lost display, player, or flag state: "
                                             + appender);
        }

        FinalObservation finalHook = requireFinalObservation();
        if (finalInvocationCount != 1) {
            throw new AssertionError("NeoForge final event recursively ran for a source: "
                                             + finalInvocationCount);
        }
        assertSame(output, finalHook.stack(), "Mismatched final-event stack");
        assertSame(context, finalHook.context(), "Mismatched final-event context");
        assertSame(rootDisplay, finalHook.display(), "Mismatched final-event display");
        if (finalHook.player() != null || finalHook.flag() != rootFlag) {
            throw new AssertionError("NeoForge final event lost nullable player or root flag");
        }
        if (!finalHook.linesBeforeHook().equals(full.subList(0, full.size() - 1))
                || !full.getLast().equals(FINAL_LINE)) {
            throw new AssertionError("NeoForge final event did not receive the completed tooltip: " + full);
        }

        int left = indexOfTranslation(full, LEFT_KEY);
        int right = indexOfTranslation(full, RIGHT_KEY);
        int appenderLine = full.indexOf(APPENDER_LINE);
        int sourceStart = sourceOnLeft ? left : right;
        int sourceEnd = sourceOnLeft ? right : full.size() - 1;
        if (left < 0 || right <= left || appenderLine <= sourceStart || appenderLine >= sourceEnd
                || full.lastIndexOf(APPENDER_LINE) != appenderLine) {
            throw new AssertionError("NeoForge appender escaped its source region: " + full);
        }
        if (!ItemStack.matches(before, output)
                || !dataBefore.equals(ModContent.getMixedBootsData(output).orElseThrow())) {
            throw new AssertionError("Production NeoForge tooltip mutated its root or source snapshots");
        }
    }

    private static void appendTooltip(
            ItemStack stack,
            Item.TooltipContext context,
            TooltipDisplay display,
            Player player,
            TooltipFlag flag,
            java.util.function.Consumer<Component> builder) {
        if (stack.getItem() != Items.IRON_BOOTS) {
            return;
        }
        APPENDER_OBSERVATIONS.add(new AppenderObservation(stack, context, display, player, flag));
        builder.accept(APPENDER_LINE);
    }

    private static void resetObservations() {
        APPENDER_OBSERVATIONS.clear();
        finalObservation = null;
        finalInvocationCount = 0;
    }

    private static AppenderObservation requireAppenderObservation() {
        if (APPENDER_OBSERVATIONS.size() != 1) {
            throw new AssertionError("Expected one NeoForge tooltip appender call, got "
                                             + APPENDER_OBSERVATIONS);
        }
        return APPENDER_OBSERVATIONS.getFirst();
    }

    private static FinalObservation requireFinalObservation() {
        if (finalObservation == null) {
            throw new AssertionError("NeoForge final tooltip event was not invoked");
        }
        return finalObservation;
    }

    private static int indexOfTranslation(List<Component> lines, String key) {
        for (int index = 0; index < lines.size(); index++) {
            if (containsTranslation(lines.get(index), key)) {
                return index;
            }
        }
        return -1;
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
