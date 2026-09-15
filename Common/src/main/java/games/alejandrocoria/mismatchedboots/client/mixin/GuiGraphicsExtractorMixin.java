package games.alejandrocoria.mismatchedboots.client.mixin;

import games.alejandrocoria.mismatchedboots.client.render.MixedBootsItemRenderer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
abstract class GuiGraphicsExtractorMixin {
    private static final int ITEM_SIZE = 16;
    private static final int HALF_ITEM_SIZE = ITEM_SIZE / 2;

    @Invoker("item")
    protected abstract void mismatchedboots$invokeItem(
            @Nullable LivingEntity owner,
            @Nullable Level level,
            ItemStack stack,
            int x,
            int y,
            int seed);

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
        if (!mismatchedboots$supportsRectangularClipping(graphics.pose())) {
            return;
        }
        boolean foil = stack.hasFoil();
        mismatchedboots$renderHalf(
                graphics,
                MixedBootsItemRenderer.sourceView(data.left().source(), foil),
                owner,
                level,
                x,
                y,
                seed,
                x,
                x + HALF_ITEM_SIZE);
        mismatchedboots$renderHalf(
                graphics,
                MixedBootsItemRenderer.sourceView(data.right().source(), foil),
                owner,
                level,
                x,
                y,
                seed,
                x + HALF_ITEM_SIZE,
                x + ITEM_SIZE);
        callback.cancel();
    }

    private void mismatchedboots$renderHalf(
            GuiGraphicsExtractor graphics,
            ItemStack source,
            @Nullable LivingEntity owner,
            @Nullable Level level,
            int x,
            int y,
            int seed,
            int clipLeft,
            int clipRight) {
        graphics.enableScissor(clipLeft, y, clipRight, y + ITEM_SIZE);
        try {
            mismatchedboots$invokeItem(owner, level, source, x, y, seed);
        } finally {
            graphics.disableScissor();
        }
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
