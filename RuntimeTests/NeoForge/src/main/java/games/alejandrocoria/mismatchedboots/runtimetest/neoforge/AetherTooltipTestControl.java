package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.event.AddAttributeTooltipsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

/** Native observation/peer/input only. Never emits a replacement presentation. */
public final class AetherTooltipTestControl {
    static final List<String> FAMILIES = List.of("beast_pelt", "burrukai_plate", "zanite", "arkenium", "gravitite", "neptune", "sentry");
    static final String VARIANT = System.getProperty("mismatchedboots.runtimetest.aetherTooltipVariant", "live");
    public static final Component PEER = Component.literal("Compatible native listener peer");
    public static final Component TIER_SCRATCH = Component.literal("Controlled source tier scratch witness");
    private static List<Component> rejectedSetScratch;
    static boolean setScratchChecked;
    public static int tierScratchCalls;
    static boolean capturing, nested, throwing, inProbe;
    static int calls, added, start, peers;
    static ItemStack failedSource;
    static List<Component> nestedLines;

    record Capture(List<Component> lines, int calls, int nativeAttributes) {
    }

    private AetherTooltipTestControl() {
    }

    public static void beforeAttributes(List<Component> lines) {
        if (capturing) {
            calls++;
            start = lines.size();
        }
    }

    public static void afterAttributes(List<Component> lines) {
        if (capturing) {
            added += lines.size() - start;
        }
    }

    public static ItemStack identity(ItemStack stack) {
        peers++;
        return stack;
    }

    public static ItemStack opaqueStack(AddAttributeTooltipsEvent event) {
        return event.getStack();
    }

    public static Item.TooltipContext opaqueContext(ItemTooltipEvent event) {
        return event.getContext();
    }

    public static void rejectedSet(List<?> lines) {
        rejectedSetScratch = lines.stream().map(Component.class::cast).toList();
    }

    public static void sourceProbe(ItemStack source, TooltipDisplay display, TooltipFlag flag) {
        if (inProbe || !nested && !throwing || display.shows(DataComponents.ATTRIBUTE_MODIFIERS)) {
            return;
        }
        inProbe = true;
        try {
            if (nested) {
                var client = Minecraft.getInstance();
                nestedLines = AetherIICompatibilityTest.item("burrukai_plate_boots")
                                                       .getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
            }
            if (throwing) {
                failedSource = source;
                throw new DetailFailure();
            }
        } finally {
            inProbe = false;
        }
    }

    static final class DetailFailure extends RuntimeException {
    }

    static Capture capture(Minecraft client, ItemStack stack, TooltipFlag flag, boolean shift) {
        capturing = true;
        calls = 0;
        added = 0;
        String before = encoded(client, stack);
        try {
            var lines = JustDireTooltipTestSupport.capture(client, stack, flag, shift);
            if (rejectedSetScratch != null && !setScratchChecked) {
                AetherIICompatibilityTest.require(rejectedSetScratch.stream().filter(line -> !line.getString().isEmpty()).noneMatch(lines::contains),
                                                  "The first failing populated set formatter published part of its scratch block");
                setScratchChecked = true;
                rejectedSetScratch = null;
            }
            AetherIICompatibilityTest.require(!lines.contains(TIER_SCRATCH), "The first failing tier provider published its populated scratch group");
            AetherIICompatibilityTest.require(before.equals(encoded(client, stack)), "Production tooltip changed full root/history");
            return new Capture(lines, calls, added);
        } finally {
            capturing = false;
        }
    }

    static TooltipDisplay display(ItemStack stack) {
        return stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
    }

    static String encoded(Minecraft client, ItemStack stack) {
        return ItemStack.CODEC.encodeStart(client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE), stack).getOrThrow().toString();
    }
}
