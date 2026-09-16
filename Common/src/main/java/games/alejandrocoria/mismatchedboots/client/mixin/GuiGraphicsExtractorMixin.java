package games.alejandrocoria.mismatchedboots.client.mixin;

import games.alejandrocoria.mismatchedboots.client.render.MixedBootsItemRenderer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
abstract class GuiGraphicsExtractorMixin {
    private static final int ITEM_SIZE = 16;
    private static final int HALF_ITEM_SIZE = ITEM_SIZE / 2;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private GuiRenderState guiRenderState;

    // This native snapshot includes the private scissor stack's current intersection.
    @Invoker("createDefaultTextParameters")
    protected abstract ActiveTextCollector.Parameters mismatchedboots$guiParameters(float opacity);

    @Inject(
            method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/world/item/ItemStack;III)V",
            at = @At("HEAD"),
            cancellable = true)
    private void mismatchedboots$splitGuiItem(
            @Nullable LivingEntity owner,
            @Nullable Level level,
            ItemStack stack,
            int x,
            int y,
            int seed,
            CallbackInfo callback) {
        MixedBootsData data = ModContent.getMixedBootsData(stack).orElse(null);
        if (data == null) {
            return;
        }

        GuiGraphicsExtractor graphics = (GuiGraphicsExtractor) (Object) this;
        MixedBootsItemRenderer.GuiPreparation prepared = MixedBootsItemRenderer.prepareGui(
                data, stack.hasFoil(), minecraft.getItemModelResolver(), level, owner, seed);
        if (prepared.clipped() || !mismatchedboots$supportsRectangularClipping(graphics.pose())) {
            mismatchedboots$submitItem(graphics, prepared.compose(), x, y);
        } else {
            mismatchedboots$renderHalf(graphics, prepared.left(), x, y, x, x + HALF_ITEM_SIZE);
            mismatchedboots$renderHalf(graphics, prepared.right(), x, y, x + HALF_ITEM_SIZE, x + ITEM_SIZE);
        }
        callback.cancel();
    }

    private void mismatchedboots$renderHalf(
            GuiGraphicsExtractor graphics,
            TrackingItemStackRenderState source,
            int x,
            int y,
            int clipLeft,
            int clipRight) {
        graphics.enableScissor(clipLeft, y, clipRight, y + ITEM_SIZE);
        try {
            mismatchedboots$submitItem(graphics, source, x, y);
        } finally {
            graphics.disableScissor();
        }
    }

    private void mismatchedboots$submitItem(
            GuiGraphicsExtractor graphics, TrackingItemStackRenderState state, int x, int y) {
        ScreenRectangle scissor = mismatchedboots$guiParameters(1.0F).scissor();
        guiRenderState.addItem(new GuiItemRenderState(new Matrix3x2f(graphics.pose()), state, x, y, scissor));
    }

    private static boolean mismatchedboots$supportsRectangularClipping(Matrix3x2fc pose) {
        return Float.isFinite(pose.m00())
                && Float.isFinite(pose.m01())
                && Float.isFinite(pose.m10())
                && Float.isFinite(pose.m11())
                && Float.isFinite(pose.m20())
                && Float.isFinite(pose.m21())
                && pose.m01() == 0.0F
                && pose.m10() == 0.0F;
    }
}
