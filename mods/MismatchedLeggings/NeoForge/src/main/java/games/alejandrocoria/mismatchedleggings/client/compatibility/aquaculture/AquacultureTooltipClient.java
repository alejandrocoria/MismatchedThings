package games.alejandrocoria.mismatchedleggings.client.compatibility.aquaculture;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Reuses only the native leggings description, never the source's complete tooltip or event bus. */
public final class AquacultureTooltipClient {
    private static final Identifier LEGGINGS = Identifier.parse("aquaculture:neptunium_leggings");
    private static final TagKey<Item> TOOLTIP = TagKey.create(Registries.ITEM, Identifier.parse("aquaculture:tooltip"));
    private static Method nativeTooltip;
    private static boolean disabled;

    private AquacultureTooltipClient() {
    }

    public static void appendSource(ItemStack source, TooltipFlag flag, Consumer<Component> output) {
        if (disabled || !LEGGINGS.equals(BuiltInRegistries.ITEM.getKey(source.getItem())) || !source.is(TOOLTIP)) {
            return;
        }

        List<Component> lines = new ArrayList<>();
        try {
            if (nativeTooltip == null) {
                nativeTooltip = Class.forName("com.teammetallurgy.aquaculture.misc.AquaTooltip")
                                     .getMethod("onTooltip", ItemTooltipEvent.class);
            }

            // The audited handler ignores player/context, but other item branches can clear its list.
            var event = new ItemTooltipEvent(source.copy(), null, lines, flag, Item.TooltipContext.EMPTY);
            nativeTooltip.invoke(null, event);
        } catch (Throwable failure) {
            while (failure instanceof InvocationTargetException invocation && invocation.getCause() != null) {
                failure = invocation.getCause();
            }
            if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) {
                throw fatal;
            }

            disabled = true;
            LeggingsContent.LOG.warn("Aquaculture leggings source tooltips disabled: native presentation unavailable", failure);
            return;
        }

        lines.forEach(output);
    }
}
