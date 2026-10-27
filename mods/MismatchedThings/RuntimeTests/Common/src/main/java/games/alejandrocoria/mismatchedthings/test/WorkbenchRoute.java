package games.alejandrocoria.mismatchedthings.test;

import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.api.WorkbenchResult;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public record WorkbenchRoute(String name, Item first, Item second) {
    public static final WorkbenchRoute FIXTURE = new WorkbenchRoute("fixture", Items.IRON_HELMET, Items.GOLDEN_HELMET);
    public static final WorkbenchRoute BOOTS = new WorkbenchRoute("boots", Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
    public static final WorkbenchRoute BASELINE = new WorkbenchRoute("baseline", Items.IRON_LEGGINGS, Items.GOLDEN_LEGGINGS);
    public static final WorkbenchRoute SIBLING = new WorkbenchRoute("sibling", Items.IRON_HELMET, Items.GOLDEN_HELMET);

    public WorkbenchInput input() {
        ItemStack left = new ItemStack(first);
        ItemStack right = new ItemStack(second);
        if (this == BOOTS) {
            left.set(DataComponents.CUSTOM_NAME, Component.literal("Left source"));
            right.set(DataComponents.CUSTOM_NAME, Component.literal("Right source"));
            left.setDamageValue(4);
            right.setDamageValue(7);
        }
        return new WorkbenchInput(left, right);
    }

    public WorkbenchResult assemble(ServerLevel level) {
        WorkbenchInput input = input();
        return level.recipeAccess().getRecipeFor(MismatchedWorkbench.recipeType(), input, level).orElseThrow().value().assemblePair(input);
    }
}
