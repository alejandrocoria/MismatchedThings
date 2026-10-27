package games.alejandrocoria.mismatchedthings.spike.core;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Present only in the v1.1 core JAR; the newer consumer cannot run against v1.0. */
public final class WorkbenchExtensions {
    private WorkbenchExtensions() {
    }

    public static void registerAddon(Identifier owner, Predicate<ItemStack> predicate) {
        WorkbenchCore.register(owner, predicate);
    }
}
