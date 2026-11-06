package games.alejandrocoria.mismatchedboots.compatibility.forbiddenarcanus;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.interaction.MixedBootsSmithingPolicy;
import games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifierService;
import games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifiers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/** Boots owns live authority, successful-load adoption and transaction admission, not the native API. */
public final class ForbiddenBootsModifiers {
    private static final AtomicBoolean ADOPTION_WARNED = new AtomicBoolean();
    private static final AtomicBoolean SMITHING_WARNED = new AtomicBoolean();

    private ForbiddenBootsModifiers() {}

    public static ForbiddenModifiers state(ItemStack root) {
        return ModContent.forbiddenModifiersType().map(root::get).orElse(ForbiddenModifiers.EMPTY);
    }

    public static void initialize(ItemStack output, ForbiddenModifiers state) {
        ModContent.forbiddenModifiersType().ifPresent(output::remove);
        if (!state.isEmpty()) {
            output.set(ModContent.forbiddenModifiersType().orElseThrow(), state);
        }
    }

    public static ItemStack finish(ItemStack output, HolderLookup.@Nullable Provider registries) {
        var state = state(output);
        var result = ForbiddenModifierService.INSTANCE.finish(output, state, registries);
        if (!state.equals(state(result))) {
            throw new IllegalArgumentException("Native modifier application changed mixed boots authority");
        }
        return result;
    }

    /** A failed/skipped legacy conversion is not permission for a later paid operation to discard its live effect. */
    public static void requireAuthority(ItemStack root) {
        if (ModContent.forbiddenModifiersType().filter(root::has).isEmpty()
                && !ForbiddenModifierService.INSTANCE.read(root).isEmpty()) {
            throw new IllegalArgumentException("Legacy Forbidden Arcanus modifier has not been safely adopted");
        }
    }

    public static Optional<MixedBootsData> validData(ItemStack root) {
        var equipment = root.get(DataComponents.EQUIPPABLE);
        if (root.isEmpty() || root.getCount() != 1 || root.getMaxStackSize() != 1
                || !ModContent.MIXED_BOOTS_ID.equals(BuiltInRegistries.ITEM.getKey(root.getItem()))
                || !ModContent.MIXED_BOOTS_ID.equals(root.get(DataComponents.ITEM_MODEL))
                || ModContent.suspendedBootsData(root).isPresent()
                || equipment == null || equipment.slot() != EquipmentSlot.FEET || ItemStack.validateStrict(root).isError()) {
            return Optional.empty();
        }
        return ModContent.getMixedBootsData(root).filter(data -> BootHalf.isValidSource(data.left().source())
                && BootHalf.isValidSource(data.right().source()));
    }

    /** Successful persistent loads only. Failure must preserve the original complete decoded item, not a partial migration. */
    public static ItemStack adopt(ItemStack root) {
        try {
            if (ModContent.forbiddenModifiersType().isEmpty()
                    || ModContent.forbiddenModifiersType().filter(root::has).isPresent() || validData(root).isEmpty()) {
                return root;
            }
            var state = ForbiddenModifierService.INSTANCE.read(root);
            if (state.isEmpty()) {
                return root;
            }
            var canonical = Component.translatable("item.mismatchedboots.mixed_boots");
            var control = root.copy();
            // Native onApplied omits generated ITEM_NAME prefixes when CUSTOM_NAME exists.
            // Recognize its original structural prefix independently of a later player rename.
            control.remove(DataComponents.CUSTOM_NAME);
            control.set(DataComponents.ITEM_NAME, canonical);
            control = ForbiddenModifierService.INSTANCE.applyExisting(control);
            var storedName = root.get(DataComponents.ITEM_NAME);
            if (!canonical.equals(storedName) && !control.get(DataComponents.ITEM_NAME).equals(storedName)) {
                return root;
            }
            var candidate = ForbiddenModifierService.INSTANCE.applyExisting(root.copy());
            candidate.set(DataComponents.ITEM_NAME, canonical);
            initialize(candidate, state);
            var expected = root.copy();
            expected.set(DataComponents.ITEM_NAME, canonical);
            initialize(expected, state);
            if (state.eternal()) {
                expected.remove(DataComponents.MAX_DAMAGE);
                expected.remove(DataComponents.DAMAGE);
            }
            if (validData(candidate).isEmpty() || !ItemStack.matches(expected, candidate)) {
                throw new IllegalArgumentException("Invalid adopted Forbidden Arcanus mixed boots");
            }
            return candidate;
        } catch (RuntimeException | LinkageError failure) {
            if (ADOPTION_WARNED.compareAndSet(false, true)) {
                Constants.LOG.warn("Forbidden Arcanus legacy mixed boots left unchanged: {}", failure.toString());
            }
            return root;
        }
    }

    public static MixedBootsSmithingPolicy.RootAttempt smithing(
            ServerLevel level, ItemStack template, ItemStack root, ItemStack addition) {
        var data = validData(root).orElse(null);
        if (data == null) {
            return MixedBootsSmithingPolicy.RootAttempt.UNHANDLED;
        }
        try {
            var decision = ForbiddenModifierService.INSTANCE.smithing(level, template, root, addition, state(root),
                    List.of(data.left().source(), data.right().source()));
            if (decision.handled()) {
                requireAuthority(root);
            }
            if (decision.match().isEmpty()) {
                return decision.handled() ? MixedBootsSmithingPolicy.RootAttempt.REJECTED
                        : MixedBootsSmithingPolicy.RootAttempt.UNHANDLED;
            }
            var match = decision.match().orElseThrow();
            var output = root.copy();
            output.set(ModContent.forbiddenModifiersType().orElseThrow(), match.modifiers());
            output = finish(output, level.registryAccess());
            if (validData(output).isEmpty()) {
                throw new IllegalArgumentException("Modifier smithing changed mixed boots structure");
            }
            return new MixedBootsSmithingPolicy.RootAttempt(true,
                    new MixedBootsSmithingPolicy.Result(output, match.recipe(), null));
        } catch (RuntimeException | LinkageError failure) {
            if (SMITHING_WARNED.compareAndSet(false, true)) {
                Constants.LOG.warn("Forbidden Arcanus mixed boots smithing refused: {}", failure.toString());
            }
            return MixedBootsSmithingPolicy.RootAttempt.REJECTED;
        }
    }
}
