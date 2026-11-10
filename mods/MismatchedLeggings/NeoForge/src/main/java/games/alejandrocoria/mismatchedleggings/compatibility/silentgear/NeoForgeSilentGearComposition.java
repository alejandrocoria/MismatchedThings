package games.alejandrocoria.mismatchedleggings.compatibility.silentgear;

import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Initializes/preserves authority and prepares owned armor against final root wear. */
public final class NeoForgeSilentGearComposition implements SilentGearComposition {
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    private static SilentGearSourceState reader;

    @Override
    public void prepare(@Nullable ItemStack previous, ItemStack output, MixedLeggingsData data, CompositionRules rules) {
        if (!MixedLeggingsStructure.isMixedItem(output) || output.getCount() != 1
                || previous != null && MixedLeggingsStructure.validData(previous).isEmpty()) {
            throw new IllegalArgumentException("SG composition requires a real mixed leggings output/root");
        }
        var type = SilentGearCompatibility.state();
        var old = previous == null ? null : previous.get(type);
        output.remove(type); // Arbitrary source components can never seed product authority.
        var left = data.left().source();
        var right = data.right().source();
        if (old != null && !SilentGearCompatibility.available()) {
            retain(previous, output, old);
            return;
        }
        if (!SilentGearCompatibility.leggings(left) && !SilentGearCompatibility.leggings(right)) {
            if (old != null && !SilentGearCompatibility.available()) {
                retain(previous, output, old);
            }
            return;
        }
        if (previous != null && old == null) {
            return; // No migration or lazy reseeding of development roots.
        }
        try {
            if (reader == null) {
                reader = new SilentGearSourceState();
            }
            // Reject malformed input scalars before settling any pending native work on copies.
            if (old == null) { reader.seed(left); reader.seed(right); }
            var a = old == null ? reader.seed(SilentGearLiveState.settledSource(left)) : reader.preserve(left, old.left());
            var b = old == null ? reader.seed(SilentGearLiveState.settledSource(right)) : reader.preserve(right, old.right());
            var baseline = output.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            var projection = SilentGearState.Projection.capture(baseline);
            boolean overridden = false;
            if (old != null) {
                var current = previous.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
                overridden = old.armorOverridden() || !SilentGearState.Projection.capture(current).equals(old.armorProjection());
                if (overridden) {
                    baseline = preserveArmor(baseline, current);
                    projection = old.armorProjection();
                }
            }
            var next = new SilentGearState(a, b, rules.attributes().policyFor(SilentGearState.ARMOR), projection,
                    overridden, old == null ? Optional.empty() : old.sample());
            var working = output.copy();
            working.set(DataComponents.ATTRIBUTE_MODIFIERS, baseline);
            var prepared = SilentGearLiveState.prepare(working, data, next);
            // Both lanes and the final projection validate before either output component is written.
            output.remove(SilentGearCompatibility.component("RECALCULATE_FLAG"));
            SilentGearLiveState.publish(output, prepared);
        } catch (IllegalArgumentException malformed) {
            throw malformed; // Invalid participating data must refuse both outputs before payment.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            if (WARNED.compareAndSet(false, true)) {
                LeggingsContent.LOG.warn("Silent Gear leggings composition state unavailable", failure);
            }
            if (old == null) {
                throw new IllegalArgumentException("Cannot initialize participating SG state", failure);
            }
            retain(previous, output, old);
        }
    }

    private static void retain(ItemStack previous, ItemStack output, SilentGearState old) {
        output.set(DataComponents.ATTRIBUTE_MODIFIERS, preserveArmor(
                output.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY),
                previous.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY)));
        output.set(SilentGearCompatibility.state(), old);
    }

    private static ItemAttributeModifiers preserveArmor(ItemAttributeModifiers baseline, ItemAttributeModifiers current) {
        var entries = new ArrayList<>(baseline.modifiers().stream().filter(entry -> !SilentGearState.managed(entry)).toList());
        entries.addAll(current.modifiers().stream().filter(SilentGearState::managed).toList());
        return new ItemAttributeModifiers(entries);
    }
}
