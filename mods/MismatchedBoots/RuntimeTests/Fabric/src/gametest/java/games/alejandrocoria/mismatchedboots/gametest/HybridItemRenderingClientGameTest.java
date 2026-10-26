package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.client.mixin.ItemStackRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.mixin.LayerRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.render.MixedBootsItemRenderer;
import games.alejandrocoria.mismatchedboots.client.render.geometry.LayerGeometryExtension;
import games.alejandrocoria.mismatchedboots.client.render.geometry.QuadClipper;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.gametest.mixin.LayerRenderStateTestAccessor;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;
import java.util.Set;

public final class HybridItemRenderingClientGameTest implements FabricClientGameTest {
    private static final float EPSILON = 0.000001F;
    private static final float FALLBACK_ANGLE = (float) Math.toRadians(-1.0);
    private static final Identifier FULL_MODEL = testId("geometry_full");
    private static final Identifier ROTATED_MODEL = testId("geometry_rotated");
    private static final Identifier EXTENSION_MODEL = ItemLayerResolutionProbe.NONPLANAR_EXTENSION_MODEL;
    private static final Identifier MULTI_ROTATED_MODEL = testId("geometry_multi_late_rotated");
    private static final Identifier RIGHT_ONLY_MODEL = testId("geometry_right_only");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getClientLevel().waitForChunksRender();
            context.waitFor(client -> client.player != null && client.level != null);
            context.runOnClient(client -> {
                verifyCompatibleVanillaPairs(client);
                verifyMetadataAndSingleResolution(client);
                verifyMultiLayerRotatedClipping(client);
                verifySpecialRendererFallback(client);
                verifyInvalidStackFallback(client);
                verifyFabricExtensionFallback(client);
                verifyEmptyAndDiscardedSides(client);
                verifyMissingAndRecursiveModelsStayBounded(client);
            });
        } finally {
            ItemLayerResolutionProbe.clear();
        }
    }

    private static void verifyCompatibleVanillaPairs(Minecraft client) {
        MixedBootsPair pair = MixedBootsFactory.createPair(
                new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS));
        for (ItemDisplayContext context : ItemDisplayContext.values()) {
            ItemStackRenderState primary = resolve(client, pair.primary(), context);
            assertClippedPair(primary, "iron_boots", "golden_boots", "primary " + context);

            ItemStackRenderState inverse = resolve(client, pair.inverse(), context);
            assertClippedPair(inverse, "golden_boots", "iron_boots", "inverse " + context);
        }
    }

    private static void verifyMetadataAndSingleResolution(Minecraft client) {
        ItemStack leather = new ItemStack(Items.LEATHER_BOOTS);
        ItemStack iron = new ItemStack(Items.IRON_BOOTS);
        Identifier leatherModel = leather.get(DataComponents.ITEM_MODEL);
        Identifier ironModel = iron.get(DataComponents.ITEM_MODEL);
        if (leatherModel == null || ironModel == null) {
            throw new AssertionError("Vanilla boots lost their item-model components");
        }

        ItemStackRenderState leatherBaseline = resolve(client,
                                                       MixedBootsItemRenderer.sourceView(leather, true), ItemDisplayContext.FIXED);
        ItemStackRenderState ironBaseline = resolve(client,
                                                    MixedBootsItemRenderer.sourceView(iron, true), ItemDisplayContext.FIXED);
        ItemLayerResolutionProbe.arm(Set.of(leatherModel, ironModel));
        ItemStack mixed = MixedBootsFactory.createPair(leather, iron).primary();
        mixed.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        ItemStackRenderState output = resolve(client, mixed, ItemDisplayContext.FIXED);

        if (ItemLayerResolutionProbe.count(leatherModel) != 1
                || ItemLayerResolutionProbe.count(ironModel) != 1) {
            throw new AssertionError("A source model was resolved more than once: leather="
                                             + ItemLayerResolutionProbe.count(leatherModel) + ", iron="
                                             + ItemLayerResolutionProbe.count(ironModel));
        }
        ItemLayerResolutionProbe.clear();

        assertClippedPair(output, "leather_boots", "iron_boots", "metadata pair");
        assertLayerMetadata(layer(leatherBaseline, 0), layer(output, 0), true, "leather metadata");
        assertLayerMetadata(layer(ironBaseline, 0), layer(output, 1), true, "iron metadata");
        if (output.isAnimated() != (leatherBaseline.isAnimated() || ironBaseline.isAnimated())) {
            throw new AssertionError("The aggregate animation flag differs from its resolved sources");
        }

        ItemStack animated = modelBoot(Items.IRON_BOOTS, testId("animated_source"));
        ItemStack ordinary = modelBoot(Items.GOLDEN_BOOTS, FULL_MODEL);
        ItemStackRenderState animatedBaseline = resolve(client, animated, ItemDisplayContext.FIXED);
        ItemStackRenderState ordinaryBaseline = resolve(client, ordinary, ItemDisplayContext.FIXED);
        ItemStackRenderState animatedOutput = resolve(client,
                                                      MixedBootsFactory.createPair(animated, ordinary).primary(), ItemDisplayContext.FIXED);
        if (!animatedBaseline.isAnimated()
                || animatedOutput.isAnimated() != (animatedBaseline.isAnimated() || ordinaryBaseline.isAnimated())) {
            throw new AssertionError("Clipping did not preserve the source animation flag");
        }
    }

    private static void verifyMultiLayerRotatedClipping(Minecraft client) {
        ItemStack left = modelBoot(Items.IRON_BOOTS, MULTI_ROTATED_MODEL);
        ItemStack right = modelBoot(Items.GOLDEN_BOOTS, FULL_MODEL);
        ItemStackRenderState leftBaseline = resolve(client, left, ItemDisplayContext.FIXED);
        ItemStackRenderState rightBaseline = resolve(client, right, ItemDisplayContext.FIXED);
        ItemStackRenderState output = resolve(client,
                                              MixedBootsFactory.createPair(left, right).primary(), ItemDisplayContext.FIXED);

        if (layerCount(leftBaseline) != 2 || layerCount(rightBaseline) != 1 || layerCount(output) != 3) {
            throw new AssertionError("The multi-layer rotated fixture resolved unexpected layer counts");
        }
        assertClippedLayer(output, 0, QuadClipper.Side.RIGHT, false,
                           "ordinary layer before rotated layer");
        assertClippedLayer(output, 1, QuadClipper.Side.RIGHT, false,
                           "rotated layer");
        assertClippedLayer(output, 2, QuadClipper.Side.LEFT, false,
                           "opposite source after rotated layer");
        assertMatrix(layer(output, 0).mismatchedboots$getLocalTransform(),
                     layer(leftBaseline, 0).mismatchedboots$getLocalTransform(),
                     "ordinary clipped layer transform");
        assertMatrix(layer(output, 1).mismatchedboots$getLocalTransform(),
                     layer(leftBaseline, 1).mismatchedboots$getLocalTransform(),
                     "rotated clipped layer transform");
        assertMatrix(layer(output, 2).mismatchedboots$getLocalTransform(),
                     layer(rightBaseline, 0).mismatchedboots$getLocalTransform(),
                     "opposite clipped layer transform");
    }

    private static void verifySpecialRendererFallback(Minecraft client) {
        ItemStack special = modelBoot(Items.IRON_BOOTS, Identifier.withDefaultNamespace("chest"));
        ItemStack ordinary = modelBoot(Items.GOLDEN_BOOTS, FULL_MODEL);
        for (ItemDisplayContext displayContext : ItemDisplayContext.values()) {
            ItemStackRenderState specialBaseline = resolve(client, special, displayContext);
            ItemStackRenderState ordinaryBaseline = resolve(client, ordinary, displayContext);
            MixedBootsPair pair = MixedBootsFactory.createPair(special, ordinary);
            ItemStackRenderState output = resolve(client, pair.primary(), displayContext);

            if (layerCount(output) != 2
                    || testLayer(output, 0).mismatchedboots$getSpecialRenderer() == null) {
                throw new AssertionError("The special-renderer fixture did not reach atomic fallback");
            }
            assertFallbackLayer(layer(specialBaseline, 0), layer(output, 0), FALLBACK_ANGLE,
                                "special renderer " + displayContext);
            assertFallbackLayer(layer(ordinaryBaseline, 0), layer(output, 1), -FALLBACK_ANGLE,
                                "ordinary layer paired with special renderer " + displayContext);
            ItemStackRenderState inverse = resolve(client, pair.inverse(), displayContext);
            assertFallbackLayer(layer(ordinaryBaseline, 0), layer(inverse, 0), FALLBACK_ANGLE,
                                "inverse ordinary layer " + displayContext);
            assertFallbackLayer(layer(specialBaseline, 0), layer(inverse, 1), -FALLBACK_ANGLE,
                                "inverse special renderer " + displayContext);
        }
    }

    private static void verifyInvalidStackFallback(Minecraft client) {
        ItemStack invalid = new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID));
        if (ModContent.getMixedBootsData(invalid).isPresent()) {
            throw new AssertionError("Give-style fallback fixture unexpectedly has source data");
        }

        List<ItemStack> sources = List.of(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS));
        for (ItemDisplayContext displayContext : ItemDisplayContext.values()) {
            ItemStackRenderState output = resolve(client, invalid, displayContext);
            if (layerCount(output) != 2) {
                throw new AssertionError("Invalid stack did not resolve its two JSON fallback layers");
            }
            for (int side = 0; side < 2; side++) {
                LayerRenderStateAccessor baseline = layer(resolve(client, sources.get(side), displayContext), 0);
                LayerRenderStateAccessor actual = layer(output, side);
                float angle = side == 0 ? FALLBACK_ANGLE : -FALLBACK_ANGLE;
                assertFallbackLayer(baseline, actual, angle, "Give-style JSON fallback " + displayContext + " side=" + side);
            }
        }
    }

    private static void verifyFabricExtensionFallback(Minecraft client) {
        ItemStack extended = modelBoot(Items.IRON_BOOTS, EXTENSION_MODEL);
        ItemStack ordinary = modelBoot(Items.GOLDEN_BOOTS, FULL_MODEL);
        ItemStackRenderState extendedBaseline = resolve(client, extended, ItemDisplayContext.FIXED);
        ItemStackRenderState ordinaryBaseline = resolve(client, ordinary, ItemDisplayContext.FIXED);
        ItemStackRenderState output = resolve(client,
                                              MixedBootsFactory.createPair(extended, ordinary).primary(), ItemDisplayContext.FIXED);

        if (!(rawLayer(output, 0) instanceof LayerGeometryExtension metadata)
                || metadata.mismatchedboots$planGeometry(
                layer(output, 0).mismatchedboots$getLocalTransform(), QuadClipper.Side.RIGHT).compatible()) {
            throw new AssertionError("The unsupported nonplanar Fabric mesh was not retained for fallback");
        }
        assertFallbackLayer(layer(extendedBaseline, 0), layer(output, 0), FALLBACK_ANGLE,
                            "Fabric extension layer");
        assertFallbackLayer(layer(ordinaryBaseline, 0), layer(output, 1), -FALLBACK_ANGLE,
                            "ordinary layer paired with Fabric extension");
    }

    private static void verifyEmptyAndDiscardedSides(Minecraft client) {
        ItemStack noModel = new ItemStack(Items.IRON_BOOTS);
        noModel.remove(DataComponents.ITEM_MODEL);
        ItemStack right = modelBoot(Items.GOLDEN_BOOTS, FULL_MODEL);
        ItemStackRenderState oneSided = resolve(client,
                                                MixedBootsFactory.createPair(noModel, right).primary(), ItemDisplayContext.FIXED);
        if (layerCount(oneSided) != 1) {
            throw new AssertionError("A model-less source produced unexpected layers: " + layerCount(oneSided));
        }
        assertClippedLayer(oneSided, 0, QuadClipper.Side.LEFT, false, "model-less left source");

        ItemStack rightOnlyRight = modelBoot(Items.IRON_BOOTS, RIGHT_ONLY_MODEL);
        ItemStackRenderState discarded = resolve(client,
                                                 MixedBootsFactory.createPair(right, rightOnlyRight).primary(), ItemDisplayContext.FIXED);
        if (layerCount(discarded) != 2 || !layer(discarded, 1).mismatchedboots$getQuads().isEmpty()) {
            throw new AssertionError("A completely discarded source layer was not retained as empty");
        }
        if (testLayer(discarded, 1).mismatchedboots$getExtents().get().length != 0) {
            throw new AssertionError("A completely discarded layer retained stale extents");
        }
        assertClippedLayer(discarded, 0, QuadClipper.Side.RIGHT, false, "discarded right source");

        ItemStackRenderState repeated = resolve(client,
                                                MixedBootsFactory.createPair(right, rightOnlyRight).primary(), ItemDisplayContext.FIXED);
        if (!layer(discarded, 0).mismatchedboots$getQuads()
                                .equals(layer(repeated, 0).mismatchedboots$getQuads())
                || !layer(discarded, 1).mismatchedboots$getQuads()
                                       .equals(layer(repeated, 1).mismatchedboots$getQuads())) {
            throw new AssertionError("Empty-side clipping was not deterministic");
        }
    }

    private static void verifyMissingAndRecursiveModelsStayBounded(Minecraft client) {
        ItemStack missing = modelBoot(Items.IRON_BOOTS, testId("missing_hybrid_source"));
        ItemStackRenderState missingOutput = resolve(client,
                                                     MixedBootsFactory.createPair(missing, new ItemStack(Items.GOLDEN_BOOTS)).primary(),
                                                     ItemDisplayContext.FIXED);
        if (layerCount(missingOutput) < 1 || layerCount(missingOutput) > 3) {
            throw new AssertionError("Missing-model handling produced an unbounded layer count: "
                                             + layerCount(missingOutput));
        }

        ItemStack recursive = modelBoot(Items.IRON_BOOTS, ModContent.MIXED_BOOTS_ID);
        ItemStackRenderState recursiveOutput = resolve(client,
                                                       MixedBootsFactory.createPair(recursive, new ItemStack(Items.GOLDEN_BOOTS)).primary(),
                                                       ItemDisplayContext.FIXED);
        if (layerCount(recursiveOutput) < 2 || layerCount(recursiveOutput) > 4) {
            throw new AssertionError("Recursive item-model handling produced an unbounded layer count: "
                                             + layerCount(recursiveOutput));
        }
    }

    private static void assertClippedPair(
            ItemStackRenderState state,
            String expectedLeftSprite,
            String expectedRightSprite,
            String scenario) {
        if (layerCount(state) != 2) {
            throw new AssertionError(scenario + " expected two layers but got " + layerCount(state));
        }
        assertClippedLayer(state, 0, QuadClipper.Side.RIGHT, false, scenario + " left source / visual right");
        assertClippedLayer(state, 1, QuadClipper.Side.LEFT, false, scenario + " right source / visual left");
        assertSprite(layer(state, 0).mismatchedboots$getQuads(), expectedLeftSprite, scenario + " left");
        assertSprite(layer(state, 1).mismatchedboots$getQuads(), expectedRightSprite, scenario + " right");
        var bounds = state.getModelBoundingBox();
        if (!Double.isFinite(bounds.minX)
                || !Double.isFinite(bounds.minY)
                || !Double.isFinite(bounds.minZ)
                || !Double.isFinite(bounds.maxX)
                || !Double.isFinite(bounds.maxY)
                || !Double.isFinite(bounds.maxZ)) {
            throw new AssertionError(scenario + " produced non-finite combined bounds");
        }
    }

    private static void assertClippedLayer(
            ItemStackRenderState state,
            int layerIndex,
            QuadClipper.Side side,
            boolean allowEmpty,
            String scenario) {
        LayerRenderStateAccessor accessor = layer(state, layerIndex);
        if (!allowEmpty && accessor.mismatchedboots$getQuads().isEmpty()) {
            throw new AssertionError(scenario + " has no clipped geometry");
        }

        Matrix4fc localTransform = accessor.mismatchedboots$getLocalTransform();
        for (BakedQuad quad : accessor.mismatchedboots$getQuads()) {
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                Vector3f canonical = localTransform.transformPosition(
                        quad.position(vertex), new Vector3f()).sub(0.5F, 0.5F, 0.5F);
                assertOwnedX(canonical.x, side, scenario + " quad");
            }
        }

        Vector3fc[] extents = testLayer(state, layerIndex).mismatchedboots$getExtents().get();
        if (!allowEmpty && extents.length == 0) {
            throw new AssertionError(scenario + " has no clipped extents");
        }
        assertExtentsBoundQuads(accessor.mismatchedboots$getQuads(), extents, scenario);
    }

    private static void assertExtentsBoundQuads(
            List<BakedQuad> quads, Vector3fc[] extents, String scenario) {
        if (quads.isEmpty()) {
            if (extents.length != 0) {
                throw new AssertionError(scenario + " retained extents for empty geometry");
            }
            return;
        }
        if (extents.length != 8) {
            throw new AssertionError(scenario + " expected eight AABB extents but got " + extents.length);
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

        Vector3fc[] expected = {
                new Vector3f(minX, minY, minZ),
                new Vector3f(minX, minY, maxZ),
                new Vector3f(minX, maxY, minZ),
                new Vector3f(minX, maxY, maxZ),
                new Vector3f(maxX, minY, minZ),
                new Vector3f(maxX, minY, maxZ),
                new Vector3f(maxX, maxY, minZ),
                new Vector3f(maxX, maxY, maxZ)
        };
        assertExtentsEqual(expected, extents, scenario);
    }

    private static void assertOwnedX(float x, QuadClipper.Side side, String scenario) {
        if ((side == QuadClipper.Side.LEFT && x > EPSILON)
                || (side == QuadClipper.Side.RIGHT && x < -EPSILON)) {
            throw new AssertionError(scenario + " crosses canonical X = 0 at " + x);
        }
    }

    private static void assertLayerMetadata(
            LayerRenderStateAccessor expected,
            LayerRenderStateAccessor actual,
            boolean expectedFoil,
            String scenario) {
        LayerRenderStateTestAccessor expectedTest = (LayerRenderStateTestAccessor) expected;
        LayerRenderStateTestAccessor actualTest = (LayerRenderStateTestAccessor) actual;
        if (actual.mismatchedboots$getFoilType() != (expectedFoil
                                                     ? ItemStackRenderState.FoilType.STANDARD
                                                     : ItemStackRenderState.FoilType.NONE)
                || expectedTest.mismatchedboots$usesBlockLight()
                != actualTest.mismatchedboots$usesBlockLight()
                || expectedTest.mismatchedboots$getParticleMaterial()
                != actualTest.mismatchedboots$getParticleMaterial()
                || !expectedTest.mismatchedboots$getItemTransform()
                                .equals(actualTest.mismatchedboots$getItemTransform())
                || !tints(expectedTest).equals(tints(actualTest))) {
            throw new AssertionError(scenario + " changed layer metadata");
        }
    }

    private static List<Integer> tints(LayerRenderStateTestAccessor accessor) {
        IntList tints = accessor.mismatchedboots$getTintLayers();
        return tints == null ? List.of() : tints.intStream().boxed().toList();
    }

    private static void assertFallbackLayer(
            LayerRenderStateAccessor baseline,
            LayerRenderStateAccessor actual,
            float angle,
            String scenario) {
        Matrix4f expectedTransform = new Matrix4f().translation(0.5F, 0.5F, 0.5F)
                                                   .rotateY(angle).translate(-0.5F, -0.5F, -0.5F)
                                                   .mul(baseline.mismatchedboots$getLocalTransform());
        assertMatrix(actual.mismatchedboots$getLocalTransform(), expectedTransform,
                     scenario + " transform");
        if (!actual.mismatchedboots$getQuads().equals(baseline.mismatchedboots$getQuads())) {
            throw new AssertionError(scenario + " partially committed clipped geometry");
        }

        LayerRenderStateTestAccessor baselineTest = (LayerRenderStateTestAccessor) baseline;
        LayerRenderStateTestAccessor actualTest = (LayerRenderStateTestAccessor) actual;
        if ((baseline.mismatchedboots$getSpecialRenderer() == null)
                != (actual.mismatchedboots$getSpecialRenderer() == null)
                || baseline.mismatchedboots$getFoilType() != actual.mismatchedboots$getFoilType()
                || baselineTest.mismatchedboots$usesBlockLight()
                != actualTest.mismatchedboots$usesBlockLight()
                || baselineTest.mismatchedboots$getParticleMaterial()
                != actualTest.mismatchedboots$getParticleMaterial()
                || !baselineTest.mismatchedboots$getItemTransform()
                                .equals(actualTest.mismatchedboots$getItemTransform())
                || !tints(baselineTest).equals(tints(actualTest))) {
            throw new AssertionError(scenario + " changed metadata during fallback");
        }
        assertExtentsEqual(
                baselineTest.mismatchedboots$getExtents().get(),
                actualTest.mismatchedboots$getExtents().get(),
                scenario);
    }

    private static void assertExtentsEqual(
            Vector3fc[] expected, Vector3fc[] actual, String scenario) {
        if (expected.length != actual.length) {
            throw new AssertionError(scenario + " changed the number of fallback extents");
        }
        for (int index = 0; index < expected.length; index++) {
            if (!new Vector3f(expected[index]).equals(actual[index], EPSILON)) {
                throw new AssertionError(scenario + " changed fallback extent " + index);
            }
        }
    }

    private static void assertMatrix(Matrix4fc actual, Matrix4fc expected, String scenario) {
        float[] actualValues = new Matrix4f(actual).get(new float[16]);
        float[] expectedValues = new Matrix4f(expected).get(new float[16]);
        for (int index = 0; index < actualValues.length; index++) {
            if (Math.abs(actualValues[index] - expectedValues[index]) > EPSILON) {
                throw new AssertionError(scenario + " differs at matrix index " + index);
            }
        }
    }

    private static void assertSprite(List<BakedQuad> quads, String expected, String scenario) {
        if (quads.stream()
                 .map(quad -> quad.materialInfo().sprite().contents().name().getPath())
                 .noneMatch(path -> path.contains(expected))) {
            throw new AssertionError(scenario + " did not retain sprite " + expected);
        }
    }

    private static ItemStackRenderState resolve(
            Minecraft client, ItemStack stack, ItemDisplayContext displayContext) {
        ItemStackRenderState state = new ItemStackRenderState();
        client.getItemModelResolver().updateForTopItem(
                state, stack, displayContext, client.level, client.player, 0x4152);
        return state;
    }

    private static ItemStack modelBoot(Item item, Identifier model) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.ITEM_MODEL, model);
        return stack;
    }

    private static int layerCount(ItemStackRenderState state) {
        return access(state).mismatchedboots$getActiveLayerCount();
    }

    private static ItemStackRenderStateAccessor access(ItemStackRenderState state) {
        return (ItemStackRenderStateAccessor) (Object) state;
    }

    private static ItemStackRenderState.LayerRenderState rawLayer(
            ItemStackRenderState state, int index) {
        return access(state).mismatchedboots$getLayers()[index];
    }

    private static LayerRenderStateAccessor layer(ItemStackRenderState state, int index) {
        return (LayerRenderStateAccessor) (Object) rawLayer(state, index);
    }

    private static LayerRenderStateTestAccessor testLayer(ItemStackRenderState state, int index) {
        return (LayerRenderStateTestAccessor) (Object) rawLayer(state, index);
    }

    private static Identifier testId(String path) {
        return Identifier.fromNamespaceAndPath("mismatchedboots-test", path);
    }
}
