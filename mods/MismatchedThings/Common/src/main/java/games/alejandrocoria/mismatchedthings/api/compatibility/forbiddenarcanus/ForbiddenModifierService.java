package games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingRecipe;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;

/** Optional native operations only; products own state registration, admission and transactions. */
public interface ForbiddenModifierService {
    ForbiddenModifierService INSTANCE = ServiceLoader.load(ForbiddenModifierService.class).findFirst()
            .orElseGet(() -> new ForbiddenModifierService() {});

    default ForbiddenModifiers read(ItemStack stack) {
        return ForbiddenModifiers.EMPTY;
    }

    default ForbiddenModifiers inherit(ItemStack left, ItemStack right) {
        return read(left).union(read(right));
    }

    /** Finalize a disposable output. Products must discard it on failure and validate their own state. */
    default ItemStack finish(ItemStack output, ForbiddenModifiers state, HolderLookup.@Nullable Provider registries) {
        return output;
    }

    /**
     * Apply only the supported scalar holder already decoded on this disposable output.
     * No registry lookup or union; native ITEM_NAME changes are left to the caller to normalize.
     */
    default ItemStack applyExisting(ItemStack output) {
        return output;
    }

    /** No output mutation or payment: the caller applies a match and revalidates it on take. */
    default SmithingDecision smithing(ServerLevel level, ItemStack template, ItemStack root, ItemStack addition,
                                     ForbiddenModifiers current, List<ItemStack> sources) {
        return SmithingDecision.PASS;
    }

    /** Returns a native-prefixed title without changing stored item data. Call only in a valid display context. */
    default Component title(Component original, ForbiddenModifiers state, HolderLookup.Provider registries) {
        return original;
    }

    record SmithingMatch(RecipeHolder<SmithingRecipe> recipe, ForbiddenModifiers modifiers) {}

    /** Handled denial must not fall through to installing an effect in inactive source history. */
    record SmithingDecision(boolean handled, Optional<SmithingMatch> match) {
        public static final SmithingDecision PASS = new SmithingDecision(false, Optional.empty());
        public static final SmithingDecision DENIED = new SmithingDecision(true, Optional.empty());
    }
}
