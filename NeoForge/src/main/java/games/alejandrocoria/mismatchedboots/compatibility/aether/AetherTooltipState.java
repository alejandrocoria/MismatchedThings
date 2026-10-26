package games.alejandrocoria.mismatchedboots.compatibility.aether;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** Presentation readiness only. Gameplay recognition has its own independent guards/state. */
public final class AetherTooltipState {
    private static final ThreadLocal<ItemStack> SOURCE = new ThreadLocal<>();
    private static volatile boolean attributes;
    private static volatile boolean tiers;
    private static volatile boolean scope;
    private static volatile boolean refusedAttributes;
    private static volatile boolean refusedTiers;

    private AetherTooltipState() {
    }

    public static boolean managed(ItemStack stack) {
        return MixedBootsSources.anySource(stack, source -> true);
    }

    public static boolean attributesReady() {
        return attributes && scope && !refusedAttributes;
    }

    public static boolean tiersReady() {
        return tiers && !refusedTiers;
    }

    public static boolean suppressAttributes(ItemStack stack) {
        return attributesReady() && (SOURCE.get() == stack || managed(stack));
    }

    public static boolean suppressTier(ItemStack stack) {
        return tiersReady() && managed(stack);
    }

    static void ready(boolean attributeDomain) {
        if (attributeDomain) {
            attributes = true;
        } else {
            tiers = true;
        }
    }

    static void scopeReady() {
        scope = true;
    }

    static synchronized void refuse(boolean attributeDomain, Throwable failure) {
        if (attributeDomain ? refusedAttributes : refusedTiers) {
            return;
        }
        if (attributeDomain) {
            refusedAttributes = true;
        } else {
            refusedTiers = true;
        }

        String message = "Aether II " + (attributeDomain ? "set" : "tier") + " tooltip containment unavailable: local contract changed";
        if (failure == null) {
            Constants.LOG.warn(message);
        } else {
            Constants.LOG.warn(message, failure);
        }
    }

    /** Only our exact source detail call is scoped. Nested foreign tooltips retain their own identity. */
    @SuppressWarnings("deprecation")
    public static void details(ItemStack source, Item.TooltipContext context, TooltipDisplay display,
                               Player player, TooltipFlag flag, Consumer<Component> output) {
        ItemStack previous = SOURCE.get();
        SOURCE.set(source);
        try {
            source.addDetailsToTooltip(context, display, player, flag, output);
        } finally {
            if (previous == null) {
                SOURCE.remove();
            } else {
                SOURCE.set(previous);
            }
        }
    }
}
