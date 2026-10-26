package games.alejandrocoria.mismatchedboots.menu;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchInput;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchRecipe;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchResult;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class MismatchedWorkbenchMenu extends AbstractContainerMenu {
    public static final int INPUT_START = 0;
    public static final int INPUT_END = 2;
    public static final int RESULT_START = 2;
    public static final int RESULT_END = 4;
    public static final int PLAYER_INVENTORY_START = 4;
    public static final int PLAYER_INVENTORY_END = 31;
    public static final int HOTBAR_START = 31;
    public static final int HOTBAR_END = 40;

    private final Player owner;
    private final ContainerLevelAccess access;
    private final InputContainer inputs;
    private final WorkbenchResultContainer results = new WorkbenchResultContainer();
    // 0: outputs are previews and inputs remain owned by the player. 1: both inputs
    // were consumed once; any remaining output is real and survives recipe reloads.
    private final DataSlot committed = DataSlot.standalone();
    // Input removal notifies the menu synchronously; never rebuild a preview mid-commit.
    private boolean suppressInputChanges;

    public MismatchedWorkbenchMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public MismatchedWorkbenchMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ModContent.mismatchedWorkbenchMenuType()
                        .orElseThrow(() -> new IllegalStateException("Mismatched workbench menu type is not registered")),
              containerId);
        this.owner = inventory.player;
        this.access = access;
        this.inputs = new InputContainer();

        this.addSlot(new InputSlot(inputs, 0, 69, 22));
        this.addSlot(new InputSlot(inputs, 1, 91, 22));
        this.addSlot(new OutputSlot(results, 0, 69, 66));
        this.addSlot(new OutputSlot(results, 1, 91, 66));
        this.addStandardInventorySlots(inventory, 8, 101);
        this.addDataSlot(committed);
    }

    public boolean isCommitted() {
        return committed.get() != 0;
    }

    @Override
    public void slotsChanged(Container container) {
        if (container == inputs && !suppressInputChanges && !isCommitted()) {
            updatePreview();
        }
        super.slotsChanged(container);
    }

    private void updatePreview() {
        if (!(owner.level() instanceof ServerLevel level)) {
            return;
        }

        MismatchedWorkbenchInput input = new MismatchedWorkbenchInput(
                inputs.getItem(0), inputs.getItem(1));
        RecipeHolder<MismatchedWorkbenchRecipe> recipe = level.recipeAccess()
                                                              .getRecipeFor(ModContent.mismatchedWorkbenchRecipeType(), input, level)
                                                              .orElse(null);
        if (recipe == null) {
            clearPreview();
            return;
        }

        MismatchedWorkbenchResult assembled = recipe.value().assemblePair(input);
        if (!assembled.isComplete()) {
            clearPreview();
            return;
        }

        results.setRecipeUsed(recipe);
        results.setItem(0, assembled.primary());
        results.setItem(1, assembled.inverse());
    }

    private void clearPreview() {
        results.setRecipeUsed(null);
        results.clearContent();
    }

    private boolean revalidatePreview() {
        if (isCommitted() || !(owner.level() instanceof ServerLevel level)) {
            return true;
        }

        MismatchedWorkbenchInput input = new MismatchedWorkbenchInput(inputs.getItem(0), inputs.getItem(1));
        RecipeHolder<MismatchedWorkbenchRecipe> current = level.recipeAccess()
                                                               .getRecipeFor(ModContent.mismatchedWorkbenchRecipeType(), input, level).orElse(null);
        if (current == null) {
            clearPreview();
            this.broadcastChanges();
            return false;
        }
        if (current == results.getRecipeUsed()) {
            return true;
        }

        MismatchedWorkbenchResult refreshed = current.value().assemblePair(input);
        if (refreshed.isComplete()
                && ItemStack.matches(results.getItem(0), refreshed.primary())
                && ItemStack.matches(results.getItem(1), refreshed.inverse())) {
            // Keep slot object identity: vanilla click handling may already hold its stack.
            results.setRecipeUsed(current);
            return true;
        }

        if (refreshed.isComplete()) {
            results.setRecipeUsed(current);
            results.setItem(0, refreshed.primary());
            results.setItem(1, refreshed.inverse());
        } else {
            clearPreview();
        }

        this.broadcastChanges();
        // Reject the old click before removal/transfer; the next click sees the new preview.
        return false;
    }

    private void resultTaken(Player player, ItemStack output) {
        // Vanilla has already removed/transferred this output before onTake. Do not
        // remove it again; commit the inputs once and inspect only the remaining slots.
        if (player.level().isClientSide()) {
            return;
        }

        if (!isCommitted()) {
            List<ItemStack> sources = List.of(inputs.getItem(0).copy(), inputs.getItem(1).copy());
            committed.set(1);
            suppressInputChanges = true;
            this.suppressRemoteUpdates();
            try {
                inputs.removeItem(0, 1);
                inputs.removeItem(1, 1);
            } finally {
                this.resumeRemoteUpdates();
                suppressInputChanges = false;
            }

            results.awardUsedRecipes(player, sources);
            results.setRecipeUsed(null);
        }

        output.onCraftedBy(player, output.getCount());
        if (results.isEmpty()) {
            committed.set(0);
        }
        this.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return ModContent.mismatchedWorkbenchBlock()
                         .map(block -> stillValid(access, player, block))
                         .orElse(false);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= this.slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = this.slots.get(slotIndex);
        if (!slot.mayPickup(player) || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        boolean moved;
        if (slotIndex >= RESULT_START && slotIndex < RESULT_END) {
            moved = this.moveItemStackTo(source, PLAYER_INVENTORY_START, HOTBAR_END, true);
            if (moved) {
                slot.onQuickCraft(source, original);
            }
        } else if (slotIndex >= PLAYER_INVENTORY_START) {
            if (!isCommitted() && isValidInputItem(source)) {
                moved = this.moveItemStackTo(source, INPUT_START, INPUT_END, false);
            } else if (slotIndex >= PLAYER_INVENTORY_START && slotIndex < PLAYER_INVENTORY_END) {
                moved = this.moveItemStackTo(source, HOTBAR_START, HOTBAR_END, false);
            } else {
                moved = this.moveItemStackTo(source, PLAYER_INVENTORY_START, PLAYER_INVENTORY_END, false);
            }
        } else if (slotIndex >= INPUT_START && slotIndex < INPUT_END && !isCommitted()) {
            moved = this.moveItemStackTo(source, PLAYER_INVENTORY_START, HOTBAR_END, false);
        } else {
            moved = false;
        }

        if (!moved) {
            return ItemStack.EMPTY;
        }

        if (source.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY, original);
        } else {
            slot.setChanged();
        }

        if (source.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }

        slot.onTake(player, slotIndex >= RESULT_START && slotIndex < RESULT_END ? original : source);
        return original;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return target.container != results && super.canTakeItemForPickAll(carried, target);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            // Closing returns real inventory only: the committed remainder or the
            // untouched inputs, never the disposable two-output preview as well.
            if (isCommitted()) {
                this.clearContainer(player, results);
            } else {
                results.clearContent();
            }
            this.clearContainer(player, inputs);
            results.setRecipeUsed(null);
            committed.set(0);
        }
    }

    private final class InputContainer extends SimpleContainer {
        private InputContainer() {
            super(2);
        }

        @Override
        public void setChanged() {
            MismatchedWorkbenchMenu.this.slotsChanged(this);
        }
    }

    private final class InputSlot extends Slot {
        private InputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public void set(ItemStack stack) {
            // SimpleContainer#setItem already notifies the menu; Slot#set would notify it twice.
            this.container.setItem(this.getContainerSlot(), stack);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !isCommitted() && isValidInputItem(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return !isCommitted();
        }

        @Override
        public boolean isActive() {
            return !isCommitted();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public Identifier getNoItemIcon() {
            return InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
        }
    }

    private static boolean isValidInputItem(ItemStack stack) {
        return !stack.isEmpty() && BootHalf.isValidSource(stack.copyWithCount(1));
    }

    private final class OutputSlot extends Slot {
        private OutputSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean isFake() {
            return true;
        }

        @Override
        public boolean mayPickup(Player player) {
            return revalidatePreview();
        }

        @Override
        public void onTake(Player player, ItemStack output) {
            resultTaken(player, output);
            super.onTake(player, output);
        }
    }

    private static final class WorkbenchResultContainer extends SimpleContainer implements RecipeCraftingHolder {
        private @Nullable RecipeHolder<?> recipe;

        private WorkbenchResultContainer() {
            super(2);
        }

        @Override
        public void setRecipeUsed(@Nullable RecipeHolder<?> recipe) {
            this.recipe = recipe;
        }

        @Override
        public @Nullable RecipeHolder<?> getRecipeUsed() {
            return recipe;
        }
    }

}
