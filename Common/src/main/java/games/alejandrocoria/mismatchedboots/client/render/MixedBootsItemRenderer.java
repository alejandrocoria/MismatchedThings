package games.alejandrocoria.mismatchedboots.client.render;

import games.alejandrocoria.mismatchedboots.client.mixin.ItemStackRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.mixin.LayerRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

public final class MixedBootsItemRenderer {
    static final float SOURCE_SCALE = 0.8F;
    static final float SOURCE_OFFSET = 0.15F;

    private MixedBootsItemRenderer() {
    }

    public static boolean appendLayers(
            ItemStackRenderState output,
            ItemStack stack,
            ItemModelResolver resolver,
            ItemDisplayContext displayContext,
            @Nullable Level level,
            @Nullable ItemOwner owner,
            int seed) {
        MixedBootsData data = ModContent.getMixedBootsData(stack).orElse(null);
        if (data == null) {
            return false;
        }

        boolean foil = stack.hasFoil();
        appendSource(output, sourceView(data.left().source(), foil), resolver,
                displayContext, level, owner, seed, -SOURCE_OFFSET);
        appendSource(output, sourceView(data.right().source(), foil), resolver,
                displayContext, level, owner, seed, SOURCE_OFFSET);
        output.setOversizedInGui(false);
        return true;
    }

    static ItemStack sourceView(ItemStack source, boolean foil) {
        ItemStack view = source.copy();
        view.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
        return view;
    }

    private static void appendSource(
            ItemStackRenderState output,
            ItemStack source,
            ItemModelResolver resolver,
            ItemDisplayContext displayContext,
            @Nullable Level level,
            @Nullable ItemOwner owner,
            int seed,
            float offset) {
        ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) (Object) output;
        int firstLayer = state.mismatchedboots$getActiveLayerCount();
        resolver.appendItemLayers(output, source, displayContext, level, owner, seed);
        int layerCount = state.mismatchedboots$getActiveLayerCount();
        Matrix4f sideTransform = new Matrix4f().translation(offset, 0.0F, 0.0F)
                .scale(SOURCE_SCALE, SOURCE_SCALE, 1.0F);

        for (int index = firstLayer; index < layerCount; index++) {
            ItemStackRenderState.LayerRenderState layer = state.mismatchedboots$getLayers()[index];
            Matrix4f localTransform = ((LayerRenderStateAccessor) (Object) layer)
                    .mismatchedboots$getLocalTransform();
            localTransform.set(new Matrix4f(sideTransform).mul(localTransform));
        }
    }
}
