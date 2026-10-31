package games.alejandrocoria.mismatchedleggings.mixin.client;

import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsItemRenderer;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
abstract class GuiGraphicsExtractorMixin {
    @Unique
    private static final int ITEM_SIZE = 16;
    @Unique
    private static final int HALF_ITEM_SIZE = ITEM_SIZE / 2;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private GuiRenderState guiRenderState;

    // This native snapshot includes the private scissor stack's current intersection.
    @Invoker("createDefaultTextParameters")
    protected abstract ActiveTextCollector.Parameters mismatchedleggings$guiParameters(float opacity);

    @Inject(
            method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;"
                    + "Lnet/minecraft/world/item/ItemStack;III)V",
            at = @At("HEAD"),
            cancellable = true)
    private void mismatchedleggings$splitGuiItem(
            @Nullable LivingEntity owner,
            @Nullable Level level,
            ItemStack stack,
            int x,
            int y,
            int seed,
            CallbackInfo callback) {
        MixedLeggingsData data = MixedLeggingsStructure.validData(stack).orElse(null);
        if (data == null) {
            return;
        }

        GuiGraphicsExtractor graphics = (GuiGraphicsExtractor) (Object) this;
        MixedLeggingsItemRenderer.GuiPreparation prepared = MixedLeggingsItemRenderer.prepareGui(
                data, stack.hasFoil(), minecraft.getItemModelResolver(), level, owner, seed);
        if (prepared.left().isEmpty() || prepared.right().isEmpty()) {
            // Native extraction will use the pair-wide diagnostic model in appendLayers.
            return;
        }
        if (prepared.clipped() || !mismatchedleggings$supportsRectangularClipping(graphics.pose())) {
            mismatchedleggings$submitItem(graphics, prepared.compose(), x, y);
        } else {
            // Source sides are anatomical; screen halves are viewed from the front.
            mismatchedleggings$renderHalf(graphics, prepared.left(), x, y, x + HALF_ITEM_SIZE, x + ITEM_SIZE);
            mismatchedleggings$renderHalf(graphics, prepared.right(), x, y, x, x + HALF_ITEM_SIZE);
        }
        callback.cancel();
    }

    private void mismatchedleggings$renderHalf(
            GuiGraphicsExtractor graphics,
            TrackingItemStackRenderState source,
            int x,
            int y,
            int clipLeft,
            int clipRight) {
        graphics.enableScissor(clipLeft, y, clipRight, y + ITEM_SIZE);
        try {
            mismatchedleggings$submitItem(graphics, source, x, y);
        } finally {
            graphics.disableScissor();
        }
    }

    private void mismatchedleggings$submitItem(
            GuiGraphicsExtractor graphics, TrackingItemStackRenderState state, int x, int y) {
        ScreenRectangle scissor = mismatchedleggings$guiParameters(1.0F).scissor();
        guiRenderState.addItem(new GuiItemRenderState(new Matrix3x2f(graphics.pose()), state, x, y, scissor));
    }

    private static boolean mismatchedleggings$supportsRectangularClipping(Matrix3x2fc pose) {
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
