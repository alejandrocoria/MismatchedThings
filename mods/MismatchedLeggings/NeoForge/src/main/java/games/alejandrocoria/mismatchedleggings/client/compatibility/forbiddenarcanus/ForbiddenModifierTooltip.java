package games.alejandrocoria.mismatchedleggings.client.compatibility.forbiddenarcanus;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.ForbiddenModifiers;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

/** Presentation only: the stored structural name and source provenance are never rewritten. */
public final class ForbiddenModifierTooltip {
    private static final ResourceKey<Registry<Object>> REGISTRY = ResourceKey.createRegistryKey(
            Identifier.parse("forbidden_arcanus:item_modifier"));
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private ForbiddenModifierTooltip() {}

    public static void title(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        var state = stack.getOrDefault(LeggingsContent.forbiddenModifiersType(), ForbiddenModifiers.EMPTY);
        var level = event.getContext().level();
        if (state.isEmpty() || stack.has(DataComponents.CUSTOM_NAME) || level == null
                || event.getToolTip().isEmpty() || MixedLeggingsStructure.validData(stack).isEmpty()) {
            return;
        }
        try {
            var names = Names.INSTANCE;
            if (names == null) {
                return;
            }
            var registry = level.registryAccess().lookupOrThrow(REGISTRY);
            var original = event.getToolTip().getFirst();
            var title = Component.empty().withStyle(original.getStyle());
            for (String kind : new String[]{"eternal", "soulbound"}) {
                if (kind.equals("eternal") ? state.eternal() : state.soulbound()) {
                    var modifier = registry.getOrThrow(ResourceKey.create(REGISTRY,
                            Identifier.fromNamespaceAndPath("forbidden_arcanus", kind))).value();
                    title.append(((Component) names.name().invoke(names.display().invoke(modifier))).copy()).append(" ");
                }
            }
            event.getToolTip().set(0, title.append(original.copy()));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            warn(failure);
        }
    }

    private static void warn(Throwable failure) {
        if (WARNED.compareAndSet(false, true)) {
            LeggingsContent.LOG.warn("Forbidden Arcanus modifier tooltip title unavailable: {}", failure.toString());
        }
    }

    /** Resolve methods lazily, never retain world registries or native modifier instances. */
    private record Names(Method display, Method name) {
        private static final Names INSTANCE = resolve();

        private static Names resolve() {
            try {
                var display = Class.forName("com.stal111.forbidden_arcanus.common.item.modifier.ItemModifier")
                        .getMethod("displaySettings");
                var name = display.getReturnType().getMethod("name");
                if (name.getReturnType() != Component.class) {
                    throw new NoSuchMethodException("Unexpected native modifier display name");
                }
                return new Names(display, name);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                warn(failure);
                return null;
            }
        }
    }
}
