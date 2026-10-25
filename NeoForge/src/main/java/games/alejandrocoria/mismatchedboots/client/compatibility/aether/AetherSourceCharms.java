package games.alejandrocoria.mismatchedboots.client.compatibility.aether;

import com.mojang.datafixers.util.Either;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Source provenance only: short-lived detached holders, native rendering, no root holder restoration. */
public final class AetherSourceCharms {
    private static Bindings bindings;
    private static Constructor<?> tooltipConstructor;
    private static boolean sourcesDisabled, graphicsDisabled, warnedData;

    private AetherSourceCharms() {
    }

    public static void registerFactory(RegisterClientTooltipComponentFactoriesEvent event) {
        // Native preparation has already succeeded inside our failure boundary; conversion cannot invoke it again.
        event.register(Prepared.class, Prepared::component);
    }

    public static void source(ItemStack source, Consumer<Component> output) {
        if (sourcesDisabled || !BootHalf.isValidSource(source)) {
            return;
        }
        SourceMarker marker;
        try {
            if (bindings == null) {
                bindings = new Bindings();
            }
            var display = source.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
            if (display.hideTooltip() || !display.shows(bindings.charms)) {
                return;
            }
            Object charms = source.get(bindings.charms);
            if (charms == null) {
                return;
            }
            if (!bindings.charmsClass.isInstance(charms)) {
                invalidData();
                return;
            }
            marker = marker(source, charms);
        } catch (Throwable failure) {
            failure = unwrap(failure);
            sourcesDisabled = true;
            Constants.LOG.warn("Aether II source charms disabled: native provenance unavailable", failure);
            return;
        }
        if (marker != null) {
            output.accept(marker);
        }
    }

    private static SourceMarker marker(ItemStack source, Object charms) throws ReflectiveOperationException {
        var installed = new ArrayList<Object>();
        var names = new ArrayList<Component>();
        Object value = bindings.holders.invoke(charms);
        if (!(value instanceof List<?> holders)) {
            invalidData();
            return null;
        }
        for (Object holder : holders) {
            if (!bindings.holderClass.isInstance(holder)) {
                invalidData();
                return null;
            }
            var stack = (ItemStack) bindings.stack.invoke(holder);
            if (stack == null || bindings.type.invoke(holder) == null || bindings.tier.invoke(holder) == null) {
                invalidData();
                return null;
            }
            if (stack.isEmpty()) {
                continue;
            }
            // Native CharmHolder's copy constructor alone shares its ItemStack. Supply our own copy explicitly.
            var copy = stack.copy();
            installed.add(bindings.copyHolder.newInstance(holder, copy));
            names.add(copy.getHoverName().copy().withStyle(ChatFormatting.GRAY));
        }
        if (installed.isEmpty()) {
            return null;
        }
        // The pinned renderer never reads base metadata. Preserve identity, not shared mutable default holders.
        var base = new ItemStack(source.getItem());
        base.remove(bindings.charms);
        return new SourceMarker(base, List.copyOf(installed), List.copyOf(names));
    }

    public static void gather(RenderTooltipEvent.GatherComponents event) {
        var elements = event.getTooltipElements();
        for (int index = 0; index < elements.size(); index++) {
            if (!(elements.get(index).left().orElse(null) instanceof SourceMarker marker)) {
                continue;
            }
            ClientTooltipComponent prepared = null;
            if (!graphicsDisabled && marker.holders.size() <= 4) {
                try {
                    if (tooltipConstructor == null) {
                        tooltipConstructor = Class.forName("com.aetherteam.aetherii.client.renderer.item.tooltip.ClientCharmTooltip$CharmTooltip")
                                                  .getConstructor(ItemStack.class, List.class);
                    }
                    var nativePayload = (TooltipComponent) tooltipConstructor.newInstance(marker.base, marker.holders);
                    prepared = ClientTooltipComponent.create(nativePayload);
                    if (prepared == null) {
                        throw new IllegalStateException("Native charm tooltip factory returned null");
                    }
                } catch (Throwable failure) {
                    failure = unwrap(failure);
                    graphicsDisabled = true;
                    Constants.LOG.warn("Aether II source charm graphics disabled: native factory unavailable", failure);
                }
            }
            if (prepared != null) {
                elements.set(index, Either.right(new Prepared(prepared)));
            } else {
                // Native >4 layout is incorrect. Names also survive missing factories or foreign text-only copies.
                elements.remove(index);
                for (var name : marker.names) {
                    elements.add(index++, Either.left(name));
                }
                index--;
            }
        }
    }

    private static void invalidData() {
        if (!warnedData) {
            warnedData = true;
            Constants.LOG.warn("Aether II source charms unavailable: invalid native holder data");
        }
    }

    private static Throwable unwrap(Throwable failure) {
        while (failure instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            failure = invocation.getCause();
        }
        if (failure instanceof Error fatal && !(fatal instanceof LinkageError)) {
            throw fatal;
        }
        return failure;
    }

    private record Prepared(ClientTooltipComponent component) implements TooltipComponent {
    }

    /** Ordinary native-name contents keep copying/serialization readable, without a registered chat-content type. */
    private static final class SourceMarker implements Component {
        private final ItemStack base;
        private final List<?> holders;
        private final List<Component> names;
        private final Component fallback;

        SourceMarker(ItemStack base, List<?> holders, List<Component> names) {
            this.base = base;
            this.holders = holders;
            this.names = names;
            var text = Component.empty();
            for (var name : names) {
                if (!text.getSiblings().isEmpty()) {
                    text.append(", ");
                }
                text.append(name);
            }
            fallback = text;
        }

        @Override
        public Style getStyle() {
            return fallback.getStyle();
        }

        @Override
        public ComponentContents getContents() {
            return fallback.getContents();
        }

        @Override
        public List<Component> getSiblings() {
            return fallback.getSiblings();
        }

        @Override
        public FormattedCharSequence getVisualOrderText() {
            return fallback.getVisualOrderText();
        }
    }

    private static final class Bindings {
        @SuppressWarnings("unchecked")
        private final DataComponentType<Object> charms = (DataComponentType<Object>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .getOptional(Identifier.fromNamespaceAndPath("aether_ii", "charms")).orElseThrow();
        private final Class<?> charmsClass = Class.forName("com.aetherteam.aetherii.item.components.Charms");
        private final Class<?> holderClass = Class.forName("com.aetherteam.aetherii.item.components.Charms$CharmHolder");
        private final Method holders = charmsClass.getMethod("charmHolders");
        private final Method stack = holderClass.getMethod("getStack");
        private final Method type = holderClass.getMethod("getType");
        private final Method tier = holderClass.getMethod("getTier");
        private final Constructor<?> copyHolder = holderClass.getConstructor(holderClass, ItemStack.class);

        private Bindings() throws ReflectiveOperationException {
        }
    }
}
