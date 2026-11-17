package games.alejandrocoria.mismatchedthings.api.compatibility.neovitae;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ServiceLoader;

/** Optional Hellforged recognition since Things 1.1.0; products retain structural and rule authority. */
public interface NeoVitaeHellforgedService {
    NeoVitaeHellforgedService INSTANCE = ServiceLoader.load(NeoVitaeHellforgedService.class,
                    NeoVitaeHellforgedService.class.getClassLoader())
            .findFirst().orElseGet(() -> new NeoVitaeHellforgedService() {});

    /** Register once after the item is registered. No native mod or other armor product is required. */
    default void register(Item item, SourceProvider provider) {
    }

    /** Only the audited hellforgeable projection; never negates a positive native tag query. */
    default boolean projects(ItemStack root) {
        return false;
    }

    /** Disposable item-render view; never modifies the root or historical source. */
    default ItemStack sourceView(ItemStack root, ItemStack source) {
        return source.copy();
    }

    @FunctionalInterface
    interface SourceProvider {
        /** Read-only sources, or null for invalid data/unavailable rules. The side is logical, not physical. */
        @Nullable Sources sources(ItemStack root, boolean client);
    }

    record Sources(ItemStack left, ItemStack right, boolean shared) {
    }
}
