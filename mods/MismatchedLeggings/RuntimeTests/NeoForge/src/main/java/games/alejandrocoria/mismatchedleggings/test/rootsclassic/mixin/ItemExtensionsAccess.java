package games.alejandrocoria.mismatchedleggings.test.rootsclassic.mixin;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.extensions.common.ClientExtensionsManager;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = ClientExtensionsManager.class, remap = false)
public interface ItemExtensionsAccess {
    @Accessor("ITEM_EXTENSIONS")
    static Map<Item, IClientItemExtensions> leggingsRootsTest$extensions() {
        throw new AssertionError("Mixin accessor was not applied");
    }
}
