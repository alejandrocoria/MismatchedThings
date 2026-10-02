package games.alejandrocoria.mismatchedboots.compatibility.jei;

import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

final class MismatchedWorkbenchJeiCategory extends AbstractRecipeCategory<MismatchedWorkbenchJeiExample> {
    static final IRecipeType<MismatchedWorkbenchJeiExample> TYPE = IRecipeType.create(
            Identifier.fromNamespaceAndPath(Constants.MOD_ID, "mismatched_workbench"),
            MismatchedWorkbenchJeiExample.class);

    private static final Identifier ARROWS_TEXTURE = Identifier.fromNamespaceAndPath(
            Constants.MOD_ID, "textures/gui/sprites/container/mismatched_workbench/arrows.png");
    private static final int WIDTH = 130;
    private static final int HEIGHT = 66;
    private static final int LEFT_SLOT_X = 46;
    private static final int RIGHT_SLOT_X = LEFT_SLOT_X + 22;
    private static final int INPUT_Y = 2;
    private static final int OUTPUT_Y = 46;
    private static final int ARROWS_X = 52;
    private static final int ARROWS_Y = 19;

    private final IDrawable arrows;

    MismatchedWorkbenchJeiCategory(IGuiHelper guiHelper) {
        super(
                TYPE,
                Component.translatable("container.mismatchedboots.mismatched_workbench"),
                guiHelper.createDrawableItemLike(ModContent.mismatchedWorkbenchItem().orElseThrow()),
                WIDTH,
                HEIGHT);
        this.arrows = guiHelper.drawableBuilder(ARROWS_TEXTURE, 0, 0, 26, 26)
                .setTextureSize(26, 26)
                .build();
    }

    @Override
    public void setRecipe(
            IRecipeLayoutBuilder builder,
            MismatchedWorkbenchJeiExample recipe,
            IFocusGroup focuses) {
        IRecipeSlotBuilder leftInput = builder.addInputSlot(LEFT_SLOT_X, INPUT_Y)
                .setStandardSlotBackground()
                .addItemStacks(recipe.leftInputs());
        IRecipeSlotBuilder rightInput = builder.addInputSlot(RIGHT_SLOT_X, INPUT_Y)
                .setStandardSlotBackground()
                .addItemStacks(recipe.rightInputs());
        IRecipeSlotBuilder primaryOutput = builder.addOutputSlot(LEFT_SLOT_X, OUTPUT_Y)
                .setStandardSlotBackground()
                .addItemStacks(recipe.primaryOutputs());
        IRecipeSlotBuilder inverseOutput = builder.addOutputSlot(RIGHT_SLOT_X, OUTPUT_Y)
                .setStandardSlotBackground()
                .addItemStacks(recipe.inverseOutputs());
        builder.createFocusLink(leftInput, rightInput, primaryOutput, inverseOutput);
    }

    @Override
    public void createRecipeExtras(
            IRecipeExtrasBuilder builder,
            MismatchedWorkbenchJeiExample recipe,
            IFocusGroup focuses) {
        builder.addDrawableWidget(arrows).setPosition(ARROWS_X, ARROWS_Y);
    }
}
