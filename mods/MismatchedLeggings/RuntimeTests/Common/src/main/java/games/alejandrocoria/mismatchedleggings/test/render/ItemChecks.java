package games.alejandrocoria.mismatchedleggings.test.render;

import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsItemRenderer;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.mixin.client.ItemStackRenderStateAccessor;
import games.alejandrocoria.mismatchedleggings.mixin.client.LayerRenderStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native resolution, with independent side/area oracles rather than renderer implementation calls. */
public final class ItemChecks {
    public static Function<ItemStackRenderState.LayerRenderState, List<Face>> platformFaces = layer -> List.of();
    private static final float EPSILON = 0.0001F;

    private ItemChecks() {}

    public static void run(Minecraft client) {
        for (boolean inverse : new boolean[]{false, true}) {
            for (boolean foil : new boolean[]{false, true}) {
                ItemStack first = model(inverse ? "blue" : "red");
                ItemStack second = model(inverse ? "red" : "blue");
                first.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, !foil);
                ItemStack root = RenderFixture.pair(first, second);
                root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
                for (ItemDisplayContext context : ItemDisplayContext.values()) {
                    ItemStackRenderState resolved = resolve(root, context);
                    assertSide(resolved, inverse ? "lapis_block" : "redstone_block", true, foil);
                    assertSide(resolved, inverse ? "redstone_block" : "lapis_block", false, foil);
                }
                var prepared = MixedLeggingsItemRenderer.prepareGui(
                        root.get(LeggingsContent.mixedLeggingsDataType()), foil, client.getItemModelResolver(),
                        client.level, client.player, 0);
                require(prepared.clipped(), "Controlled GUI geometry did not clip");
                var composed = prepared.compose();
                require(!composed.isEmpty() && composed.sources().size() == 2, "GUI composition lost a source");
                assertSide(composed.sources().getFirst(), inverse ? "lapis_block" : "redstone_block", true, foil);
                assertSide(composed.sources().getLast(), inverse ? "redstone_block" : "lapis_block", false, foil);
                var again = MixedLeggingsItemRenderer.prepareGui(
                        root.get(LeggingsContent.mixedLeggingsDataType()), foil, client.getItemModelResolver(),
                        client.level, client.player, 0).compose();
                require(composed.getModelIdentity().equals(again.getModelIdentity()), "Stable GUI identity changed");
                require(ItemStack.isSameItemSameComponents(first,
                        root.get(LeggingsContent.mixedLeggingsDataType()).left().source()), "Rendering mutated provenance");
            }
        }

        verifyFallbacks();
        verifyLiveAppearance();
        var prefix = resolve(new ItemStack(Items.DIAMOND), ItemDisplayContext.FIXED);
        client.getItemModelResolver().appendItemLayers(prefix,
                RenderFixture.pair(model("red"), model("blue")), ItemDisplayContext.FIXED, client.level, client.player, 0);
        require(faces(prefix).stream().anyMatch(face -> face.texture.contains("diamond")),
                "Appending mixed layers overwrote an earlier native layer");
        assertSide(prefix, "redstone_block", true, false);
        assertSide(prefix, "lapis_block", false, false);
        if (Boolean.getBoolean("leggings.test.boots")) {
            var boots = resolve(BootsCoexistence.mixed(), ItemDisplayContext.FIXED);
            var faces = faces(boots);
            require(faces.stream().anyMatch(face -> face.texture.contains("iron_boots"))
                    && faces.stream().anyMatch(face -> face.texture.contains("golden_boots")),
                    "Boots item resolver was suppressed by Leggings");
            for (Face face : faces) {
                for (Vector3f vertex : face.vertices) {
                    require(face.texture.contains("iron_boots") ? vertex.x >= 0.5F - EPSILON : vertex.x <= 0.5F + EPSILON,
                            "Boots anatomical orientation changed");
                }
            }
        }
    }

    private static void verifyFallbacks() {
        ItemStack unsupported = new ItemStack(Items.IRON_LEGGINGS);
        unsupported.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("shield"));
        for (boolean inverse : new boolean[]{false, true}) {
            ItemStack root = RenderFixture.pair(inverse ? unsupported : model("red"), inverse ? model("red") : unsupported);
            var state = resolve(root, ItemDisplayContext.FIXED);
            var accessor = (ItemStackRenderStateAccessor) (Object) state;
            boolean special = false;
            boolean complete = false;
            for (int i = 0; i < accessor.mismatchedleggings$getActiveLayerCount(); i++) {
                var layer = accessor.mismatchedleggings$getLayers()[i];
                special |= ((LayerRenderStateAccessor) (Object) layer).mismatchedleggings$getSpecialRenderer() != null;
            }
            for (Face face : faces(state)) {
                if (face.texture.contains("redstone_block")) {
                    float min = face.vertices.stream().map(v -> v.x).min(Float::compare).orElseThrow();
                    float max = face.vertices.stream().map(v -> v.x).max(Float::compare).orElseThrow();
                    complete |= min < 0.01F && max > 0.99F;
                }
            }
            require(special && complete, "Fallback mixed special geometry with a clipped ordinary source");
            var prepared = MixedLeggingsItemRenderer.prepareGui(
                    root.get(LeggingsContent.mixedLeggingsDataType()), false, Minecraft.getInstance().getItemModelResolver(),
                    Minecraft.getInstance().level, Minecraft.getInstance().player, 0);
            require(!prepared.clipped(), "GUI accepted an unsupported special renderer");
            var composed = prepared.compose();
            require(!composed.isEmpty(), "Fallback GUI became empty");
        }

        ItemStack missing = new ItemStack(Items.IRON_LEGGINGS);
        missing.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("leggings_test", "absent"));
        require(!resolve(RenderFixture.pair(missing, model("blue")), ItemDisplayContext.GROUND).isEmpty(),
                "Missing asset made the mixed item invisible");
        ItemStack empty = model("empty");
        var diagnostic = resolve(RenderFixture.pair(empty, model("blue")), ItemDisplayContext.FIXED);
        require(faces(diagnostic).stream().anyMatch(f -> f.texture.contains("iron_leggings"))
                && faces(diagnostic).stream().anyMatch(f -> f.texture.contains("golden_leggings")),
                "Empty source model did not choose the complete diagnostic fallback");
        ItemStack bare = new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID));
        require(faces(resolve(bare, ItemDisplayContext.GUI)).stream().anyMatch(f -> f.texture.contains("golden_leggings")),
                "Bare diagnostic model is still the temporary single iron icon");

        // Unrelated items carrying copied provenance must retain their own native model.
        ItemStack foreign = new ItemStack(Items.DIAMOND);
        foreign.set(LeggingsContent.mixedLeggingsDataType(), RenderFixture.pair(0).get(LeggingsContent.mixedLeggingsDataType()));
        var nativeFaces = faces(resolve(foreign, ItemDisplayContext.FIXED));
        require(!nativeFaces.isEmpty() && nativeFaces.stream().allMatch(f -> f.texture.contains("diamond")),
                "Foreign item was intercepted because it carried pair data");
    }

    private static void verifyLiveAppearance() {
        ItemStack root = RenderFixture.pair(2);
        root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        var unfoiled = resolve(root, ItemDisplayContext.FIXED);
        require(faces(unfoiled).stream().noneMatch(Face::foil), "Source foil survived a live-root override");
        root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        var foiled = faces(resolve(root, ItemDisplayContext.FIXED));
        require(foiled.stream().allMatch(Face::foil), "Live-root foil did not reach both sources/material passes");
        require(foiled.stream().anyMatch(f -> f.texture.contains("leather"))
                && foiled.stream().anyMatch(f -> f.texture.contains("trim"))
                && foiled.stream().anyMatch(f -> f.texture.contains("golden")), "Dye/trim/source layers were lost");
    }

    private static void assertSide(ItemStackRenderState state, String texture, boolean viewerRight, boolean foil) {
        var selected = faces(state).stream().filter(face -> face.texture.contains(texture)).toList();
        require(!selected.isEmpty(), "Missing controlled source " + texture);
        float area = 0;
        for (Face face : selected) {
            require(face.foil == foil, "Root foil mismatch: " + texture);
            for (Vector3f point : face.vertices) {
                require(viewerRight ? point.x >= 0.5F - EPSILON : point.x <= 0.5F + EPSILON,
                        "Source on wrong anatomical side: " + texture + " " + point);
            }
            Vector3f a = new Vector3f(face.vertices.get(1)).sub(face.vertices.getFirst());
            Vector3f b = new Vector3f(face.vertices.get(2)).sub(face.vertices.getFirst());
            Vector3f c = new Vector3f(face.vertices.get(3)).sub(face.vertices.getFirst());
            area += a.cross(b, new Vector3f()).length() / 2 + b.cross(c, new Vector3f()).length() / 2;
        }
        require(Math.abs(area - 0.5F) < EPSILON, "Controlled half changed scale or area: " + area);
    }

    public static List<Face> faces(ItemStackRenderState state) {
        var result = new ArrayList<Face>();
        var accessor = (ItemStackRenderStateAccessor) (Object) state;
        for (int i = 0; i < accessor.mismatchedleggings$getActiveLayerCount(); i++) {
            var layer = accessor.mismatchedleggings$getLayers()[i];
            var fields = (LayerRenderStateAccessor) (Object) layer;
            Matrix4f transform = fields.mismatchedleggings$getLocalTransform();
            for (var quad : fields.mismatchedleggings$getQuads()) {
                var vertices = new ArrayList<Vector3f>();
                for (int vertex = 0; vertex < 4; vertex++) {
                    vertices.add(transform.transformPosition(new Vector3f(quad.position(vertex))));
                }
                result.add(new Face(quad.materialInfo().sprite().contents().name().toString(), vertices,
                        fields.mismatchedleggings$getFoilType() != ItemStackRenderState.FoilType.NONE));
            }
            result.addAll(platformFaces.apply(layer));
        }
        return result;
    }

    public static TrackingItemStackRenderState resolve(ItemStack stack, ItemDisplayContext context) {
        var client = Minecraft.getInstance();
        var state = new TrackingItemStackRenderState();
        client.getItemModelResolver().updateForTopItem(state, stack, context, client.level, client.player, 0);
        return state;
    }

    public static ItemStack model(String path) {
        ItemStack source = new ItemStack(Items.IRON_LEGGINGS);
        source.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("leggings_test", path));
        return source;
    }

    public record Face(String texture, List<Vector3f> vertices, boolean foil) {}
}
