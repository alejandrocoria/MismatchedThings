package games.alejandrocoria.mismatchedboots;

import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.InventoryMenu;

public class MismatchedBoots {
    public static void init() {
        MismatchedWorkbench.registerContributor(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "boots"),
                BootHalf::isValidSource, InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS);
    }
}
