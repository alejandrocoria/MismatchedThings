package games.alejandrocoria.mismatchedboots.client.render;

import games.alejandrocoria.mismatchedboots.client.mixin.ItemStackRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.mixin.LayerRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.render.geometry.BakedQuadClipper;
import games.alejandrocoria.mismatchedboots.client.render.geometry.LayerExtensionMetadata;
import games.alejandrocoria.mismatchedboots.client.render.geometry.QuadClipper;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

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
        LayerRange left = appendSource(output, sourceView(data.left().source(), foil), resolver,
                displayContext, level, owner, seed, QuadClipper.Side.LEFT, -SOURCE_OFFSET);
        LayerRange right = appendSource(output, sourceView(data.right().source(), foil), resolver,
                displayContext, level, owner, seed, QuadClipper.Side.RIGHT, SOURCE_OFFSET);

        if (displayContext == ItemDisplayContext.GUI || !clipAtomically(output, left, right)) {
            applySideBySideFallback(output, left);
            applySideBySideFallback(output, right);
        }
        ((ItemStackRenderStateAccessor) (Object) output).mismatchedboots$setCachedModelBoundingBox(null);
        output.setOversizedInGui(false);
        return true;
    }

    public static ItemStack sourceView(ItemStack source, boolean foil) {
        ItemStack view = source.copy();
        view.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
        return view;
    }

    private static LayerRange appendSource(
            ItemStackRenderState output,
            ItemStack source,
            ItemModelResolver resolver,
            ItemDisplayContext displayContext,
            @Nullable Level level,
            @Nullable ItemOwner owner,
            int seed,
            QuadClipper.Side side,
            float fallbackOffset) {
        ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) (Object) output;
        int firstLayer = state.mismatchedboots$getActiveLayerCount();
        resolver.appendItemLayers(output, source, displayContext, level, owner, seed);
        return new LayerRange(
                firstLayer,
                state.mismatchedboots$getActiveLayerCount(),
                side,
                fallbackOffset);
    }

    private static boolean clipAtomically(
            ItemStackRenderState output, LayerRange left, LayerRange right) {
        List<LayerPlan> plans = new ArrayList<>(left.size() + right.size());
        if (!planRange(output, left, plans) || !planRange(output, right, plans)) {
            return false;
        }
        for (LayerPlan plan : plans) {
            List<BakedQuad> quads = plan.layer().prepareQuadList();
            quads.clear();
            quads.addAll(plan.quads());
            plan.layer().setExtents(plan.extents());
        }
        return true;
    }

    private static boolean planRange(
            ItemStackRenderState output, LayerRange range, List<LayerPlan> plans) {
        ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) (Object) output;
        for (int index = range.firstLayer(); index < range.lastLayer(); index++) {
            ItemStackRenderState.LayerRenderState layer = state.mismatchedboots$getLayers()[index];
            LayerRenderStateAccessor accessor = (LayerRenderStateAccessor) (Object) layer;
            if (accessor.mismatchedboots$getSpecialRenderer() != null) {
                return false;
            }

            BakedQuadClipper.ExtensionMetadata extensionMetadata =
                    layer instanceof LayerExtensionMetadata metadata
                            && metadata.mismatchedboots$hasUnsupportedGeometry()
                            ? BakedQuadClipper.ExtensionMetadata.UNSUPPORTED
                            : BakedQuadClipper.ExtensionMetadata.NONE;
            if (extensionMetadata == BakedQuadClipper.ExtensionMetadata.UNSUPPORTED) {
                return false;
            }
            List<BakedQuad> replacement = new ArrayList<>(accessor.mismatchedboots$getQuads().size());
            for (BakedQuad quad : accessor.mismatchedboots$getQuads()) {
                BakedQuadClipper.Result result = BakedQuadClipper.clip(
                        quad,
                        accessor.mismatchedboots$getLocalTransform(),
                        range.side(),
                        extensionMetadata);
                switch (result.decision()) {
                    case KEEP, SPLIT -> replacement.add(result.quad());
                    case DROP -> {
                    }
                    case INCOMPATIBLE -> {
                        return false;
                    }
                }
            }
            plans.add(new LayerPlan(layer, List.copyOf(replacement), extents(replacement)));
        }
        return true;
    }

    private static Supplier<Vector3fc[]> extents(List<BakedQuad> quads) {
        if (quads.isEmpty()) {
            return ItemStackRenderState.LayerRenderState.NO_EXTENTS_SUPPLIER;
        }

        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (BakedQuad quad : quads) {
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                Vector3fc position = quad.position(vertex);
                minX = Math.min(minX, position.x());
                minY = Math.min(minY, position.y());
                minZ = Math.min(minZ, position.z());
                maxX = Math.max(maxX, position.x());
                maxY = Math.max(maxY, position.y());
                maxZ = Math.max(maxZ, position.z());
            }
        }

        Vector3fc[] result = {
                new Vector3f(minX, minY, minZ),
                new Vector3f(minX, minY, maxZ),
                new Vector3f(minX, maxY, minZ),
                new Vector3f(minX, maxY, maxZ),
                new Vector3f(maxX, minY, minZ),
                new Vector3f(maxX, minY, maxZ),
                new Vector3f(maxX, maxY, minZ),
                new Vector3f(maxX, maxY, maxZ)
        };
        return () -> result;
    }

    private static void applySideBySideFallback(ItemStackRenderState output, LayerRange range) {
        ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) (Object) output;
        Matrix4f sideTransform = new Matrix4f().translation(range.fallbackOffset(), 0.0F, 0.0F)
                .scale(SOURCE_SCALE, SOURCE_SCALE, 1.0F);
        for (int index = range.firstLayer(); index < range.lastLayer(); index++) {
            ItemStackRenderState.LayerRenderState layer = state.mismatchedboots$getLayers()[index];
            Matrix4f localTransform = ((LayerRenderStateAccessor) (Object) layer)
                    .mismatchedboots$getLocalTransform();
            localTransform.set(new Matrix4f(sideTransform).mul(localTransform));
        }
    }

    private record LayerRange(
            int firstLayer,
            int lastLayer,
            QuadClipper.Side side,
            float fallbackOffset) {
        int size() {
            return lastLayer - firstLayer;
        }
    }

    private record LayerPlan(
            ItemStackRenderState.LayerRenderState layer,
            List<BakedQuad> quads,
            Supplier<Vector3fc[]> extents) {
    }
}
