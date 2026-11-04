package games.alejandrocoria.rulesspike;

import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Shared source inputs; deliberately free of client-only class references. */
public final class ClientInput {
    private ClientInput() {
    }

    public static WorkbenchInput input() {
        return new WorkbenchInput(new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.IRON_BOOTS));
    }
}
