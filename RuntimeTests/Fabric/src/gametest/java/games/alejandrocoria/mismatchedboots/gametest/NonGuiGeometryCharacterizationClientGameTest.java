package games.alejandrocoria.mismatchedboots.gametest;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedboots.client.mixin.ItemStackRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.mixin.LayerRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.gametest.mixin.LayerRenderStateTestAccessor;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.render.FabricLayerRenderState;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.fabric.impl.client.renderer.LayerRenderStateExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("UnstableApiUsage")
public final class NonGuiGeometryCharacterizationClientGameTest implements FabricClientGameTest {
    private static final float GEOMETRY_EPSILON = 0.000001F;
    private static final float OBSERVED_ERROR_LIMIT = GEOMETRY_EPSILON / 10.0F;
    private static final float MAXIMUM_CENTER_SPAN = 0.0F;
    private static final List<ItemDisplayContext> NON_GUI_CONTEXTS = Arrays.stream(ItemDisplayContext.values())
            .filter(context -> context != ItemDisplayContext.GUI)
            .toList();
    private static final List<Item> VANILLA_BOOTS = List.of(
            Items.LEATHER_BOOTS,
            Items.IRON_BOOTS,
            Items.GOLDEN_BOOTS,
            Items.DIAMOND_BOOTS,
            Items.NETHERITE_BOOTS);
    private static final Identifier FULL_MODEL = testId("geometry_full");
    private static final Identifier TRANSLATED_MODEL = testId("geometry_translated");
    private static final Identifier SCALED_MODEL = testId("geometry_scaled");
    private static final Identifier ROTATED_MODEL = testId("geometry_rotated");

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            singleplayer.getConnection().waitForChunksRender();
            context.waitFor(client -> client.player != null && client.level != null);
            context.runOnClient(client -> {
                characterizeVanillaBoots(client);
                characterizeLocalTransforms(client);
                verifyEpsilonAndCenterBoundaries();
                verifyFabricExtensionBoundary();
            });
        } finally {
            ItemSubmissionPoseProbe.takeIfArmed();
        }
    }

    private static void characterizeVanillaBoots(Minecraft client) {
        for (Item item : VANILLA_BOOTS) {
            ItemStack stack = new ItemStack(item);
            GeometrySnapshot baseline = characterizeAcrossContexts(client, stack, item.toString());
            int expectedFrontBackCrossings = item == Items.LEATHER_BOOTS ? 4 : 2;
            int expectedQuadCount = item == Items.LEATHER_BOOTS ? 92 : 66;
            assertCounts(baseline, expectedQuadCount, expectedFrontBackCrossings, 0,
                    expectedFrontBackCrossings, item.toString());
            int expectedTintCount = item == Items.LEATHER_BOOTS ? 1 : 0;
            if (baseline.tintLayers().size() != expectedTintCount
                    || baseline.usesBlockLight()
                    || baseline.foilType() != ItemStackRenderState.FoilType.NONE) {
                throw new AssertionError(item + " changed its generated-item layer metadata");
            }
            assertNear(baseline.rawBounds().minX, 0.0F, item + " raw min X");
            assertNear(baseline.rawBounds().maxX, 1.0F, item + " raw max X");
            assertNear(baseline.rawBounds().minY, 0.0F, item + " raw min Y");
            assertNear(baseline.rawBounds().maxY, 1.0F, item + " raw max Y");
            assertNear(baseline.rawBounds().minZ, 0.46875F, item + " raw min Z");
            assertNear(baseline.rawBounds().maxZ, 0.53125F, item + " raw max Z");
            assertNear(baseline.canonicalBounds().minX, -0.5F, item + " canonical min X");
            assertNear(baseline.canonicalBounds().maxX, 0.5F, item + " canonical max X");
            assertComplementaryExtents(baseline, item.toString());
        }
    }

    private static void characterizeLocalTransforms(Minecraft client) {
        GeometrySnapshot full = characterizeAcrossContexts(client, modelStack(FULL_MODEL), "full synthetic model");
        assertIdentity(full.localTransform(), "full synthetic local transform");
        assertCounts(full, 66, 2, 0, 2, "full synthetic model");
        assertGeneratedLayerMetadata(full, "full synthetic model");

        GeometrySnapshot translated = characterizeAcrossContexts(
                client, modelStack(TRANSLATED_MODEL), "translated synthetic model");
        assertNear(translated.localTransform().m30(), 0.125F, "translated local X");
        assertNear(translated.localTransform().m31(), -0.0625F, "translated local Y");
        assertNear(translated.localTransform().m32(), 0.03125F, "translated local Z");
        assertCounts(translated, 66, 2, 0, 2, "translated synthetic model");
        assertGeneratedLayerMetadata(translated, "translated synthetic model");
        assertComplementaryExtents(translated, "translated synthetic model");

        GeometrySnapshot scaled = characterizeAcrossContexts(
                client, modelStack(SCALED_MODEL), "scaled synthetic model");
        assertNear(scaled.localTransform().m00(), 0.75F, "scaled local X");
        assertNear(scaled.localTransform().m11(), 0.5F, "scaled local Y");
        assertNear(scaled.localTransform().m22(), 1.25F, "scaled local Z");
        assertCounts(scaled, 66, 4, 0, 2, "scaled synthetic model");
        assertGeneratedLayerMetadata(scaled, "scaled synthetic model");
        assertComplementaryExtents(scaled, "scaled synthetic model");

        GeometrySnapshot rotated = characterizeAcrossContexts(
                client, modelStack(ROTATED_MODEL), "rotated synthetic model");
        if (Math.abs(rotated.localTransform().m01()) < 0.5F
                || Math.abs(rotated.localTransform().m10()) < 0.5F) {
            throw new AssertionError("The rotated fixture did not retain its local rotation");
        }
        assertCounts(rotated, 66, 2, 2, 0, "rotated synthetic model");
        assertGeneratedLayerMetadata(rotated, "rotated synthetic model");
    }

    private static GeometrySnapshot characterizeAcrossContexts(
            Minecraft client, ItemStack stack, String scenario) {
        GeometrySnapshot baseline = null;
        for (ItemDisplayContext displayContext : NON_GUI_CONTEXTS) {
            ItemStackRenderState state = resolve(client, stack, displayContext);
            LayerView layer = requireOrdinaryLayer(state, scenario + " in " + displayContext);
            GeometrySnapshot snapshot = inspect(layer, scenario + " in " + displayContext);
            if (baseline == null) {
                baseline = snapshot;
            } else {
                assertSameGeometry(baseline, snapshot, scenario + " changed in " + displayContext);
            }
            verifySubmittedPose(state, layer, displayContext, scenario);
        }
        return baseline;
    }

    private static ItemStackRenderState resolve(
            Minecraft client, ItemStack stack, ItemDisplayContext displayContext) {
        ItemStackRenderState state = new ItemStackRenderState();
        client.getItemModelResolver().updateForTopItem(
                state, stack, displayContext, client.level, client.player, 0x2B17);
        return state;
    }

    private static LayerView requireOrdinaryLayer(ItemStackRenderState state, String scenario) {
        ItemStackRenderStateAccessor stateAccessor = (ItemStackRenderStateAccessor) (Object) state;
        if (stateAccessor.mismatchedboots$getActiveLayerCount() != 1) {
            throw new AssertionError(scenario + " resolved "
                    + stateAccessor.mismatchedboots$getActiveLayerCount() + " layers instead of one");
        }
        ItemStackRenderState.LayerRenderState layer = stateAccessor.mismatchedboots$getLayers()[0];
        LayerRenderStateAccessor common = (LayerRenderStateAccessor) (Object) layer;
        LayerRenderStateTestAccessor test = (LayerRenderStateTestAccessor) (Object) layer;
        if (test.mismatchedboots$getSpecialRenderer() != null) {
            throw new AssertionError(scenario + " unexpectedly resolved through a special renderer");
        }
        if (common.mismatchedboots$getQuads().isEmpty()) {
            throw new AssertionError(scenario + " did not resolve vanilla quads");
        }
        if (test.mismatchedboots$getParticleMaterial() == null) {
            throw new AssertionError(scenario + " lost its particle material");
        }
        MutableMesh extensionMesh = ((LayerRenderStateExtension) (Object) layer).fabric_getMutableMesh();
        if (extensionMesh != null && extensionMesh.size() != 0) {
            throw new AssertionError(scenario + " unexpectedly contains Fabric extension geometry");
        }
        return new LayerView(common, test);
    }

    private static GeometrySnapshot inspect(LayerView layer, String scenario) {
        Matrix4f localTransform = new Matrix4f(layer.common().mismatchedboots$getLocalTransform());
        Bounds rawBounds = new Bounds();
        Bounds canonicalBounds = new Bounds();
        Bounds proposedLeftBounds = new Bounds();
        Bounds proposedRightBounds = new Bounds();
        List<QuadFingerprint> fingerprints = new ArrayList<>();
        IntList tintList = layer.test().mismatchedboots$getTintLayers();
        List<Integer> tintLayers = tintList == null
                ? List.of()
                : tintList.intStream().boxed().toList();
        int safeCrossings = 0;
        int incompatibleCrossings = 0;
        int frontBackCrossings = 0;
        int leftQuads = 0;
        int rightQuads = 0;
        float maximumResidual = 0.0F;

        for (BakedQuad quad : layer.common().mismatchedboots$getQuads()) {
            Vector3f[] raw = new Vector3f[4];
            Vector3f[] canonical = new Vector3f[4];
            long[] packedUvs = new long[4];
            for (int vertex = 0; vertex < 4; vertex++) {
                raw[vertex] = new Vector3f(quad.position(vertex));
                canonical[vertex] = localTransform.transformPosition(raw[vertex], new Vector3f())
                        .sub(0.5F, 0.5F, 0.5F);
                packedUvs[vertex] = quad.packedUV(vertex);
                requireFinite(raw[vertex], scenario + " raw vertex");
                requireFinite(canonical[vertex], scenario + " canonical vertex");
                rawBounds.include(raw[vertex]);
                canonicalBounds.include(canonical[vertex]);
            }

            validateMaterialAndUvs(quad, packedUvs, scenario);
            validateWindingAndArea(quad, raw, scenario);
            float residual = rectangleResidual(canonical);
            maximumResidual = Math.max(maximumResidual, residual);
            Classification classification = classify(canonical);
            if (classification == Classification.SAFE_RECTANGULAR_CROSSING) {
                safeCrossings++;
                if (quad.direction() == Direction.NORTH || quad.direction() == Direction.SOUTH) {
                    frontBackCrossings++;
                    validateFrontBackUvCorrespondence(quad, raw, packedUvs, scenario);
                }
            } else if (classification == Classification.INCOMPATIBLE_CROSSING) {
                incompatibleCrossings++;
            } else if (classification == Classification.LEFT) {
                leftQuads++;
            } else {
                rightQuads++;
            }

            includeClippedBounds(canonical, true, proposedLeftBounds);
            includeClippedBounds(canonical, false, proposedRightBounds);
            fingerprints.add(fingerprint(quad, raw, canonical, packedUvs));
        }

        if (maximumResidual > OBSERVED_ERROR_LIMIT) {
            throw new AssertionError(scenario + " exceeds the characterized rectangle error margin: "
                    + maximumResidual);
        }

        Bounds suppliedExtents = new Bounds();
        Vector3fc[] extents = layer.test().mismatchedboots$getExtents().get();
        if (extents.length == 0) {
            throw new AssertionError(scenario + " has no model extents");
        }
        for (Vector3fc extent : extents) {
            Vector3f canonicalExtent = localTransform.transformPosition(new Vector3f(extent))
                    .sub(0.5F, 0.5F, 0.5F);
            requireFinite(canonicalExtent, scenario + " extent");
            suppliedExtents.include(canonicalExtent);
        }
        if (!suppliedExtents.contains(canonicalBounds, GEOMETRY_EPSILON)) {
            throw new AssertionError(scenario + " extents do not contain its quad bounds");
        }

        return new GeometrySnapshot(
                localTransform,
                List.copyOf(fingerprints),
                layer.test().mismatchedboots$usesBlockLight(),
                layer.test().mismatchedboots$getParticleMaterial().sprite().contents().name(),
                tintLayers,
                layer.common().mismatchedboots$getFoilType(),
                rawBounds,
                canonicalBounds,
                suppliedExtents,
                proposedLeftBounds,
                proposedRightBounds,
                safeCrossings,
                incompatibleCrossings,
                frontBackCrossings,
                leftQuads,
                rightQuads,
                maximumResidual);
    }

    private static void validateWindingAndArea(
            BakedQuad quad, Vector3f[] raw, String scenario) {
        Vector3f normal = new Vector3f(raw[1]).sub(raw[0])
                .cross(new Vector3f(raw[2]).sub(raw[0]));
        if (!normal.isFinite() || normal.lengthSquared() <= GEOMETRY_EPSILON * GEOMETRY_EPSILON) {
            throw new AssertionError(scenario + " has a degenerate quad");
        }
        if (normal.dot(quad.direction().getUnitVec3f()) <= 0.0F) {
            throw new AssertionError(scenario + " has winding opposite to " + quad.direction());
        }
    }

    private static void validateMaterialAndUvs(BakedQuad quad, long[] packedUvs, String scenario) {
        BakedQuad.MaterialInfo material = quad.materialInfo();
        if (material.sprite() == null || material.layer() == null || material.itemRenderType() == null) {
            throw new AssertionError(scenario + " has incomplete MaterialInfo");
        }
        if (material.tintIndex() < -1 || material.lightEmission() < 0) {
            throw new AssertionError(scenario + " has invalid material scalar metadata: " + material);
        }
        float minU = Math.min(material.sprite().getU0(), material.sprite().getU1()) - GEOMETRY_EPSILON;
        float maxU = Math.max(material.sprite().getU0(), material.sprite().getU1()) + GEOMETRY_EPSILON;
        float minV = Math.min(material.sprite().getV0(), material.sprite().getV1()) - GEOMETRY_EPSILON;
        float maxV = Math.max(material.sprite().getV0(), material.sprite().getV1()) + GEOMETRY_EPSILON;
        for (long packedUv : packedUvs) {
            float u = UVPair.unpackU(packedUv);
            float v = UVPair.unpackV(packedUv);
            if (!Float.isFinite(u) || !Float.isFinite(v)
                    || u < minU || u > maxU || v < minV || v > maxV) {
                throw new AssertionError(scenario + " has an invalid packed UV pair: " + u + ", " + v);
            }
        }
    }

    private static void validateFrontBackUvCorrespondence(
            BakedQuad quad, Vector3f[] raw, long[] packedUvs, String scenario) {
        Set<Integer> uValues = new HashSet<>();
        Set<Integer> vValues = new HashSet<>();
        for (long packedUv : packedUvs) {
            uValues.add(Float.floatToIntBits(UVPair.unpackU(packedUv)));
            vValues.add(Float.floatToIntBits(UVPair.unpackV(packedUv)));
        }
        if (uValues.size() != 2 || vValues.size() != 2) {
            throw new AssertionError(scenario + " front/back UVs are not a rectangular mapping");
        }
        for (int first = 0; first < 4; first++) {
            for (int second = first + 1; second < 4; second++) {
                if (near(raw[first].x, raw[second].x)
                        != near(UVPair.unpackU(packedUvs[first]), UVPair.unpackU(packedUvs[second]))) {
                    throw new AssertionError(scenario + " lost the vertex-to-U correspondence for "
                            + quad.direction());
                }
                if (near(raw[first].y, raw[second].y)
                        != near(UVPair.unpackV(packedUvs[first]), UVPair.unpackV(packedUvs[second]))) {
                    throw new AssertionError(scenario + " lost the vertex-to-V correspondence for "
                            + quad.direction());
                }
            }
        }
    }

    private static void verifySubmittedPose(
            ItemStackRenderState state,
            LayerView layer,
            ItemDisplayContext displayContext,
            String scenario) {
        PoseStack poseStack = new PoseStack();
        poseStack.translate(1.25F, -0.75F, 2.5F);
        poseStack.mulPose(new Quaternionf().rotationXYZ(0.17F, -0.31F, 0.23F));
        Matrix4f initialPose = new Matrix4f(poseStack.last().pose());

        ItemSubmissionPoseProbe.arm(layer.common().mismatchedboots$getQuads());
        state.submit(poseStack, new SubmitNodeStorage(), 0x00F000F0, 0, 0);
        ItemSubmissionPoseProbe.Submission submission = ItemSubmissionPoseProbe.take();
        if (submission.displayContext() != displayContext) {
            throw new AssertionError(scenario + " submitted the wrong display context");
        }

        Matrix4f expected = expectedSubmittedPose(
                initialPose,
                layer.test().mismatchedboots$getItemTransform(),
                layer.common().mismatchedboots$getLocalTransform(),
                displayContext.leftHand());
        assertMatrix(submission.pose(), expected,
                scenario + " has the wrong display/center/local multiplication order in " + displayContext);
    }

    private static Matrix4f expectedSubmittedPose(
            Matrix4fc initial,
            ItemTransform itemTransform,
            Matrix4fc localTransform,
            boolean leftHand) {
        Matrix4f expected = new Matrix4f(initial);
        if (ItemTransform.NO_TRANSFORM.equals(itemTransform)) {
            expected.translate(-0.5F, -0.5F, -0.5F);
        } else {
            float translationX = leftHand ? -itemTransform.translation().x() : itemTransform.translation().x();
            float rotationY = leftHand ? -itemTransform.rotation().y() : itemTransform.rotation().y();
            float rotationZ = leftHand ? -itemTransform.rotation().z() : itemTransform.rotation().z();
            float radians = (float) (Math.PI / 180.0);
            expected.translate(translationX, itemTransform.translation().y(), itemTransform.translation().z());
            expected.rotate(new Quaternionf().rotationXYZ(
                    itemTransform.rotation().x() * radians,
                    rotationY * radians,
                    rotationZ * radians));
            expected.scale(itemTransform.scale().x(), itemTransform.scale().y(), itemTransform.scale().z());
            expected.translate(-0.5F, -0.5F, -0.5F);
        }
        return expected.mul(localTransform);
    }

    private static void verifyEpsilonAndCenterBoundaries() {
        Vector3f[] accepted = rectangle(0.5F * GEOMETRY_EPSILON);
        Vector3f[] rejected = rectangle(4.0F * GEOMETRY_EPSILON);
        if (!isRectangle(accepted) || isRectangle(rejected)) {
            throw new AssertionError("geometryEpsilon does not separate bounded float noise from warped geometry");
        }

        Vector3f[] narrowUnsupported = {
                new Vector3f(-0.00001F, 0.0F, 0.0F),
                new Vector3f(0.00001F, 0.2F, 0.0F),
                new Vector3f(-0.00001F, 0.4F, 0.0F),
                new Vector3f(0.00001F, 0.6F, 0.0F)
        };
        if (MAXIMUM_CENTER_SPAN != 0.0F
                || classify(narrowUnsupported) != Classification.INCOMPATIBLE_CROSSING) {
            throw new AssertionError("The disabled center shortcut accepted unsupported crossing geometry");
        }
    }

    private static void verifyFabricExtensionBoundary() {
        ItemStackRenderState state = new ItemStackRenderState();
        ItemStackRenderState.LayerRenderState layer = state.newLayer();
        LayerRenderStateExtension extension = (LayerRenderStateExtension) (Object) layer;
        if (extension.fabric_getMutableMesh() != null) {
            throw new AssertionError("A new layer unexpectedly starts with extension geometry");
        }

        QuadEmitter emitter = ((FabricLayerRenderState) (Object) layer).emitter();
        emitter.square(Direction.SOUTH, 0.0F, 0.0F, 1.0F, 1.0F, 0.5F)
                .color(0xFF3366CC, 0xFF3366CC, 0xFF3366CC, 0xFF3366CC)
                .lightmap(1, 2, 3, 4)
                .normal(0, 0.0F, 0.0F, 1.0F)
                .normal(1, 0.0F, 0.0F, 1.0F)
                .normal(2, 0.0F, 0.0F, 1.0F)
                .normal(3, 0.0F, 0.0F, 1.0F)
                .ambientOcclusion(TriState.FALSE)
                .tag(37)
                .emit();

        MutableMesh mesh = extension.fabric_getMutableMesh();
        if (mesh == null || mesh.size() != 1 || !layer.prepareQuadList().isEmpty()) {
            throw new AssertionError("Fabric extension geometry was not kept separate from vanilla BakedQuad data");
        }
        mesh.forEach(quad -> {
            if (quad.color(0) != 0xFF3366CC
                    || quad.lightmap(3) != 4
                    || !quad.hasNormal(0)
                    || quad.ambientOcclusion() != TriState.FALSE
                    || quad.tag() != 37) {
                throw new AssertionError("Fabric extension metadata was not retained in its separate mesh");
            }
        });
    }

    private static Classification classify(Vector3f[] vertices) {
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        for (Vector3f vertex : vertices) {
            minX = Math.min(minX, vertex.x);
            maxX = Math.max(maxX, vertex.x);
        }
        if (maxX < -GEOMETRY_EPSILON) {
            return Classification.LEFT;
        }
        if (minX > GEOMETRY_EPSILON) {
            return Classification.RIGHT;
        }
        if (minX >= -GEOMETRY_EPSILON && maxX <= GEOMETRY_EPSILON) {
            return Classification.RIGHT;
        }
        if (minX < -GEOMETRY_EPSILON && maxX > GEOMETRY_EPSILON) {
            int firstCrossingEdge = -1;
            int secondCrossingEdge = -1;
            int crossingEdges = 0;
            for (int edge = 0; edge < 4; edge++) {
                float first = vertices[edge].x;
                float second = vertices[(edge + 1) % 4].x;
                if ((first < -GEOMETRY_EPSILON && second > GEOMETRY_EPSILON)
                        || (first > GEOMETRY_EPSILON && second < -GEOMETRY_EPSILON)) {
                    if (crossingEdges == 0) {
                        firstCrossingEdge = edge;
                    } else if (crossingEdges == 1) {
                        secondCrossingEdge = edge;
                    }
                    crossingEdges++;
                }
            }
            if (isRectangle(vertices)
                    && crossingEdges == 2
                    && Math.floorMod(firstCrossingEdge - secondCrossingEdge, 4) == 2) {
                return Classification.SAFE_RECTANGULAR_CROSSING;
            }
            return Classification.INCOMPATIBLE_CROSSING;
        }
        float centerX = 0.0F;
        for (Vector3f vertex : vertices) {
            centerX += vertex.x;
        }
        return centerX / 4.0F < 0.0F ? Classification.LEFT : Classification.RIGHT;
    }

    private static boolean isRectangle(Vector3f[] vertices) {
        if (vertices.length != 4) {
            return false;
        }
        for (Vector3f vertex : vertices) {
            if (!vertex.isFinite()) {
                return false;
            }
        }
        return rectangleResidual(vertices) <= GEOMETRY_EPSILON;
    }

    private static float rectangleResidual(Vector3f[] vertices) {
        Vector3f edge0 = new Vector3f(vertices[1]).sub(vertices[0]);
        Vector3f edge1 = new Vector3f(vertices[2]).sub(vertices[1]);
        Vector3f edge2 = new Vector3f(vertices[3]).sub(vertices[2]);
        Vector3f edge3 = new Vector3f(vertices[0]).sub(vertices[3]);
        float length0 = edge0.length();
        float length1 = edge1.length();
        float length2 = edge2.length();
        float length3 = edge3.length();
        float minimumLength = Math.min(Math.min(length0, length1), Math.min(length2, length3));
        if (!Float.isFinite(minimumLength) || minimumLength <= GEOMETRY_EPSILON) {
            return Float.POSITIVE_INFINITY;
        }

        float parallel02 = new Vector3f(edge0).cross(edge2).length() / (length0 * length2);
        float parallel13 = new Vector3f(edge1).cross(edge3).length() / (length1 * length3);
        float orthogonal = Math.abs(edge0.dot(edge1)) / (length0 * length1);
        Vector3f normal = new Vector3f(edge0).cross(edge1);
        float normalLength = normal.length();
        if (!Float.isFinite(normalLength) || normalLength <= GEOMETRY_EPSILON) {
            return Float.POSITIVE_INFINITY;
        }
        float planarity = Math.abs(new Vector3f(vertices[3]).sub(vertices[0]).dot(normal)) / normalLength;
        return Math.max(Math.max(parallel02, parallel13), Math.max(orthogonal, planarity));
    }

    private static void includeClippedBounds(Vector3f[] vertices, boolean left, Bounds bounds) {
        List<Vector3f> input = Arrays.stream(vertices).map(Vector3f::new).toList();
        List<Vector3f> output = new ArrayList<>();
        for (int index = 0; index < input.size(); index++) {
            Vector3f current = input.get(index);
            Vector3f next = input.get((index + 1) % input.size());
            boolean currentInside = left ? current.x <= 0.0F : current.x >= 0.0F;
            boolean nextInside = left ? next.x <= 0.0F : next.x >= 0.0F;
            if (currentInside) {
                output.add(new Vector3f(current));
            }
            if (currentInside != nextInside) {
                float factor = -current.x / (next.x - current.x);
                output.add(new Vector3f(current).lerp(next, factor));
            }
        }
        output.forEach(bounds::include);
    }

    private static QuadFingerprint fingerprint(
            BakedQuad quad, Vector3f[] raw, Vector3f[] canonical, long[] packedUvs) {
        List<Integer> coordinateBits = new ArrayList<>(24);
        for (int index = 0; index < 4; index++) {
            addBits(coordinateBits, raw[index]);
            addBits(coordinateBits, canonical[index]);
        }
        BakedQuad.MaterialInfo material = quad.materialInfo();
        MaterialFingerprint materialFingerprint = new MaterialFingerprint(
                material.sprite().contents().name(),
                material.layer().toString(),
                material.itemRenderType().toString(),
                material.tintIndex(),
                material.shade(),
                material.lightEmission(),
                material.flags());
        return new QuadFingerprint(
                quad.direction(), List.copyOf(coordinateBits), List.of(
                        packedUvs[0], packedUvs[1], packedUvs[2], packedUvs[3]), materialFingerprint);
    }

    private static void addBits(List<Integer> target, Vector3f vector) {
        target.add(Float.floatToIntBits(vector.x));
        target.add(Float.floatToIntBits(vector.y));
        target.add(Float.floatToIntBits(vector.z));
    }

    private static void assertSameGeometry(
            GeometrySnapshot expected, GeometrySnapshot actual, String scenario) {
        assertMatrix(actual.localTransform(), expected.localTransform(), scenario + " local transform");
        if (!actual.fingerprints().equals(expected.fingerprints())
                || actual.usesBlockLight() != expected.usesBlockLight()
                || !actual.particleSprite().equals(expected.particleSprite())
                || !actual.tintLayers().equals(expected.tintLayers())
                || actual.foilType() != expected.foilType()
                || actual.safeCrossings() != expected.safeCrossings()
                || actual.incompatibleCrossings() != expected.incompatibleCrossings()
                || actual.frontBackCrossings() != expected.frontBackCrossings()
                || actual.leftQuads() != expected.leftQuads()
                || actual.rightQuads() != expected.rightQuads()) {
            throw new AssertionError(scenario + ": " + actual.summary());
        }
    }

    private static void assertGeneratedLayerMetadata(GeometrySnapshot snapshot, String scenario) {
        if (snapshot.usesBlockLight()
                || !snapshot.tintLayers().isEmpty()
                || snapshot.foilType() != ItemStackRenderState.FoilType.NONE) {
            throw new AssertionError(scenario + " changed its generated-item layer metadata");
        }
    }

    private static void assertCounts(
            GeometrySnapshot snapshot,
            int quads,
            int safeCrossings,
            int incompatibleCrossings,
            int frontBackCrossings,
            String scenario) {
        if (snapshot.fingerprints().size() != quads
                || snapshot.safeCrossings() != safeCrossings
                || snapshot.incompatibleCrossings() != incompatibleCrossings
                || snapshot.frontBackCrossings() != frontBackCrossings
                || snapshot.leftQuads() + snapshot.rightQuads()
                + snapshot.safeCrossings() + snapshot.incompatibleCrossings() != quads) {
            throw new AssertionError(scenario + " changed its characterized geometry: " + snapshot.summary());
        }
    }

    private static void assertComplementaryExtents(GeometrySnapshot snapshot, String scenario) {
        if (!snapshot.proposedLeftBounds().defined()
                || !snapshot.proposedRightBounds().defined()
                || snapshot.proposedLeftBounds().maxX > GEOMETRY_EPSILON
                || snapshot.proposedRightBounds().minX < -GEOMETRY_EPSILON) {
            throw new AssertionError(scenario + " proposed half extents cross the canonical plane");
        }
        if (snapshot.suppliedExtents().minX >= 0.0F || snapshot.suppliedExtents().maxX <= 0.0F) {
            throw new AssertionError(scenario + " original extents do not demonstrate the stale-bounds risk");
        }
    }

    private static Vector3f[] rectangle(float zDeviation) {
        return new Vector3f[]{
                new Vector3f(-0.5F, -0.5F, 0.0F),
                new Vector3f(0.5F, -0.5F, 0.0F),
                new Vector3f(0.5F, 0.5F, 0.0F),
                new Vector3f(-0.5F, 0.5F, zDeviation)
        };
    }

    private static void requireFinite(Vector3f vector, String scenario) {
        if (!vector.isFinite()) {
            throw new AssertionError(scenario + " is non-finite: " + vector);
        }
    }

    private static void assertIdentity(Matrix4fc matrix, String scenario) {
        assertMatrix(matrix, new Matrix4f(), scenario);
    }

    private static void assertMatrix(Matrix4fc actual, Matrix4fc expected, String scenario) {
        float[] actualValues = new Matrix4f(actual).get(new float[16]);
        float[] expectedValues = new Matrix4f(expected).get(new float[16]);
        for (int index = 0; index < actualValues.length; index++) {
            if (Math.abs(actualValues[index] - expectedValues[index]) > GEOMETRY_EPSILON) {
                throw new AssertionError(scenario + " differs at matrix index " + index + ": "
                        + actualValues[index] + " instead of " + expectedValues[index]);
            }
        }
    }

    private static void assertNear(float actual, float expected, String scenario) {
        if (!near(actual, expected)) {
            throw new AssertionError(scenario + " is " + actual + " instead of " + expected);
        }
    }

    private static boolean near(float first, float second) {
        return Math.abs(first - second) <= GEOMETRY_EPSILON;
    }

    private static ItemStack modelStack(Identifier model) {
        ItemStack stack = new ItemStack(Items.IRON_BOOTS);
        stack.set(DataComponents.ITEM_MODEL, model);
        return stack;
    }

    private static Identifier testId(String path) {
        return Identifier.fromNamespaceAndPath("mismatchedboots-test", path);
    }

    private enum Classification {
        LEFT,
        RIGHT,
        SAFE_RECTANGULAR_CROSSING,
        INCOMPATIBLE_CROSSING
    }

    private record LayerView(
            LayerRenderStateAccessor common,
            LayerRenderStateTestAccessor test) {
    }

    private record GeometrySnapshot(
            Matrix4fc localTransform,
            List<QuadFingerprint> fingerprints,
            boolean usesBlockLight,
            Identifier particleSprite,
            List<Integer> tintLayers,
            ItemStackRenderState.FoilType foilType,
            Bounds rawBounds,
            Bounds canonicalBounds,
            Bounds suppliedExtents,
            Bounds proposedLeftBounds,
            Bounds proposedRightBounds,
            int safeCrossings,
            int incompatibleCrossings,
            int frontBackCrossings,
            int leftQuads,
            int rightQuads,
            float maximumResidual) {
        String summary() {
            return "quads=" + fingerprints.size()
                    + ", safeCrossings=" + safeCrossings
                    + ", incompatibleCrossings=" + incompatibleCrossings
                    + ", frontBackCrossings=" + frontBackCrossings
                    + ", leftQuads=" + leftQuads
                    + ", rightQuads=" + rightQuads
                    + ", maximumResidual=" + maximumResidual;
        }
    }

    private record QuadFingerprint(
            Direction direction,
            List<Integer> coordinateBits,
            List<Long> packedUvs,
            MaterialFingerprint material) {
    }

    private record MaterialFingerprint(
            Identifier sprite,
            String chunkLayer,
            String itemRenderType,
            int tintIndex,
            boolean shade,
            int lightEmission,
            int flags) {
    }

    private static final class Bounds {
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;

        void include(Vector3fc point) {
            minX = Math.min(minX, point.x());
            minY = Math.min(minY, point.y());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxY = Math.max(maxY, point.y());
            maxZ = Math.max(maxZ, point.z());
        }

        boolean defined() {
            return minX != Float.POSITIVE_INFINITY;
        }

        boolean contains(Bounds other, float epsilon) {
            return defined()
                    && other.defined()
                    && minX <= other.minX + epsilon
                    && minY <= other.minY + epsilon
                    && minZ <= other.minZ + epsilon
                    && maxX >= other.maxX - epsilon
                    && maxY >= other.maxY - epsilon
                    && maxZ >= other.maxZ - epsilon;
        }
    }
}
