package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedboots.client.mixin.ItemStackRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.mixin.LayerRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.render.MixedBootsGuiRenderState;
import games.alejandrocoria.mismatchedboots.client.render.geometry.BakedPolygonTessellator;
import games.alejandrocoria.mismatchedboots.client.render.geometry.BakedQuadClipper;
import games.alejandrocoria.mismatchedboots.client.render.geometry.QuadClipper;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class NeoForgeGeometryClientTest {
    private static final Identifier RED_MODEL = testId("intersecting_red_boots");
    private static final Identifier BLUE_MODEL = testId("intersecting_blue_boots");
    private static final float EPSILON = QuadClipper.GEOMETRY_EPSILON;
    private static final int STARTUP_TICKS = 20;
    private static final int TIMEOUT_TICKS = 3_600;

    private static int ticks;
    private static boolean resourcesLoaded;
    private static boolean finished;

    private NeoForgeGeometryClientTest() {
    }

    public static void resourcesLoaded() {
        ticks = 0;
        resourcesLoaded = true;
    }

    public static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            ticks++;
            assertUnattended(client);
            if (ticks < STARTUP_TICKS || !resourcesLoaded) {
                if (ticks > TIMEOUT_TICKS) {
                    throw new AssertionError("Geometry client timed out before resource loading completed");
                }
                return;
            }

            GeometryReport red = characterize(client, modelBoot(Items.IRON_BOOTS, RED_MODEL));
            GeometryReport blue = characterize(client, modelBoot(Items.GOLDEN_BOOTS, BLUE_MODEL));
            assertExpectedTopology(red, "red fixture");
            assertExpectedTopology(blue, "blue fixture");
            assertProductionClipping(client);
            assertGenuinelyIncompatibleFallback(client);
            assertGuiClipping(client);
            NeoForgeEquipmentRenderingClientTest.assertStandardRoute();

            NetworkAcceptanceFiles.write("geometry-report.txt", red.describe("red") + blue.describe("blue")
                    + "gui.canonicalOwnership=true\ngui.depthSharing=true\ngui.atomicFallback=true\n"
                    + "equipment.standardRoute=true\nequipment.rootOwnership=true\n"
                    + "equipment.atomicFallback=true\n");
            NetworkAcceptanceFiles.write("geometry-success.txt", "neoforge\n");
            finished = true;
            client.stop();
        } catch (Throwable throwable) {
            NetworkAcceptanceFiles.failure("geometry-client", throwable);
            finished = true;
            client.stop();
        }
    }

    private static GeometryReport characterize(Minecraft client, ItemStack stack) {
        ItemStackRenderState state = resolve(client, stack);
        ItemStackRenderStateAccessor stateAccessor = (ItemStackRenderStateAccessor) (Object) state;
        if (stateAccessor.mismatchedboots$getActiveLayerCount() != 1) {
            throw new AssertionError("Synthetic geometry resolved "
                    + stateAccessor.mismatchedboots$getActiveLayerCount() + " layers instead of one");
        }

        ItemStackRenderState.LayerRenderState renderLayer = stateAccessor.mismatchedboots$getLayers()[0];
        LayerRenderStateAccessor layer = (LayerRenderStateAccessor) (Object) renderLayer;
        if (layer.mismatchedboots$getSpecialRenderer() != null || layer.mismatchedboots$getQuads().isEmpty()) {
            throw new AssertionError("Synthetic geometry did not resolve as ordinary baked quads");
        }

        Map<Topology, Integer> topologies = new EnumMap<>(Topology.class);
        Map<BakedQuadClipper.Incompatibility, Integer> incompatibilities =
                new EnumMap<>(BakedQuadClipper.Incompatibility.class);
        int currentKeep = 0;
        int currentDrop = 0;
        int currentSplit = 0;
        int maximumCandidateExpansion = 0;
        List<BakedQuad> leftCandidates = new ArrayList<>();
        List<BakedQuad> rightCandidates = new ArrayList<>();
        Matrix4f transform = new Matrix4f(layer.mismatchedboots$getLocalTransform());
        for (BakedQuad quad : layer.mismatchedboots$getQuads()) {
            Topology topology = classify(quad, transform);
            topologies.merge(topology, 1, Integer::sum);

            BakedQuadClipper.Result current = BakedQuadClipper.clip(
                    quad, transform, QuadClipper.Side.LEFT, BakedQuadClipper.ExtensionMetadata.NONE);
            switch (current.decision()) {
                case KEEP -> currentKeep++;
                case DROP -> currentDrop++;
                case SPLIT -> currentSplit++;
                case INCOMPATIBLE -> incompatibilities.merge(current.incompatibility(), 1, Integer::sum);
            }

            List<BakedQuad> left = candidatePrimitives(quad, transform, QuadClipper.Side.LEFT);
            List<BakedQuad> right = candidatePrimitives(quad, transform, QuadClipper.Side.RIGHT);
            BakedQuadClipper.Result currentRight = BakedQuadClipper.clip(
                    quad, transform, QuadClipper.Side.RIGHT, BakedQuadClipper.ExtensionMetadata.NONE);
            if (!current.quads().equals(left) || !currentRight.quads().equals(right)) {
                throw new AssertionError("Production clipping diverged from the characterized candidate");
            }
            maximumCandidateExpansion = Math.max(maximumCandidateExpansion, Math.max(left.size(), right.size()));
            leftCandidates.addAll(left);
            rightCandidates.addAll(right);
        }
        submitCandidates(state, renderLayer, layer, leftCandidates, "left");
        submitCandidates(state, renderLayer, layer, rightCandidates, "right");
        return new GeometryReport(
                layer.mismatchedboots$getQuads().size(), topologies, incompatibilities,
                currentKeep, currentDrop, currentSplit,
                leftCandidates.size(), rightCandidates.size(),
                triangleCount(leftCandidates), triangleCount(rightCandidates),
                maximumCandidateExpansion, 2);
    }

    private static List<BakedQuad> candidatePrimitives(
            BakedQuad input, Matrix4f transform, QuadClipper.Side side) {
        QuadClipper.Vertex[] vertices = new QuadClipper.Vertex[BakedQuad.VERTEX_COUNT];
        for (int index = 0; index < BakedQuad.VERTEX_COUNT; index++) {
            Vector3f raw = new Vector3f(input.position(index));
            Vector3f canonical = transform.transformPosition(new Vector3f(raw)).sub(0.5F, 0.5F, 0.5F);
            long packedUv = input.packedUV(index);
            vertices[index] = new QuadClipper.Vertex(
                    point(raw),
                    new QuadClipper.Uv(UVPair.unpackU(packedUv), UVPair.unpackV(packedUv)),
                    point(canonical));
        }

        QuadClipper.PolygonResult clipped = QuadClipper.clipPolygon(new QuadClipper.Quad(
                vertices[0], vertices[1], vertices[2], vertices[3]), side);
        List<BakedQuad> output = switch (clipped.decision()) {
            case KEEP -> List.of(input);
            case DROP -> List.of();
            case SPLIT -> BakedPolygonTessellator.tessellate(input, clipped.polygon());
            case INCOMPATIBLE -> throw new AssertionError(
                    "Candidate tessellation rejected fixture geometry: " + clipped.incompatibility());
        };
        if (output.size() > 2) {
            throw new AssertionError("Candidate tessellation exceeded its two-primitive bound");
        }
        for (BakedQuad candidate : output) {
            if (candidate.direction() != input.direction() || candidate.materialInfo() != input.materialInfo()) {
                throw new AssertionError("Candidate tessellation changed source metadata");
            }
            Vector3f normal = new Vector3f(candidate.position1()).sub(candidate.position0())
                    .cross(new Vector3f(candidate.position2()).sub(candidate.position1()));
            if (!normal.isFinite() || normal.lengthSquared() <= EPSILON * EPSILON) {
                throw new AssertionError("Candidate tessellation produced a degenerate primitive");
            }
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                float x = transform.transformPosition(
                        new Vector3f(candidate.position(vertex))).sub(0.5F, 0.5F, 0.5F).x;
                if ((side == QuadClipper.Side.LEFT && x > EPSILON)
                        || (side == QuadClipper.Side.RIGHT && x < -EPSILON)) {
                    throw new AssertionError("Candidate tessellation retained geometry from the other side");
                }
            }
        }
        return output;
    }

    private static void submitCandidates(
            ItemStackRenderState state,
            ItemStackRenderState.LayerRenderState renderLayer,
            LayerRenderStateAccessor layer,
            List<BakedQuad> candidates,
            String side) {
        List<BakedQuad> original = List.copyOf(renderLayer.prepareQuadList());
        ItemStackRenderState.FoilType originalFoil = layer.mismatchedboots$getFoilType();
        try {
            renderLayer.prepareQuadList().clear();
            renderLayer.prepareQuadList().addAll(candidates);
            renderLayer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
            NeoForgeGeometrySubmissionProbe.arm();
            state.submit(new PoseStack(), new SubmitNodeStorage(), 0x00F000F0, 0, 0);
            NeoForgeGeometrySubmissionProbe.Submission submission = NeoForgeGeometrySubmissionProbe.take();
            if (submission.displayContext() != ItemDisplayContext.FIXED
                    || submission.foilType() != ItemStackRenderState.FoilType.STANDARD
                    || !submission.quads().equals(candidates)) {
                throw new AssertionError(side + " candidate submission changed its render-state payload");
            }
        } finally {
            renderLayer.prepareQuadList().clear();
            renderLayer.prepareQuadList().addAll(original);
            renderLayer.setFoilType(originalFoil);
        }
    }

    private static int triangleCount(List<BakedQuad> quads) {
        int result = 0;
        for (BakedQuad quad : quads) {
            boolean repeatedPosition = quad.position2().equals(quad.position3());
            boolean repeatedUv = quad.packedUV2() == quad.packedUV3();
            if (repeatedPosition != repeatedUv) {
                throw new AssertionError("Candidate triangle position and UV conventions diverged");
            }
            if (repeatedPosition) {
                result++;
            }
        }
        return result;
    }

    private static QuadClipper.Point point(Vector3f vector) {
        return new QuadClipper.Point(vector.x, vector.y, vector.z);
    }

    private static Topology classify(BakedQuad quad, Matrix4f transform) {
        float[] x = new float[BakedQuad.VERTEX_COUNT];
        int left = 0;
        int right = 0;
        int center = 0;
        for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
            Vector3f canonical = transform.transformPosition(quad.position(vertex), new Vector3f())
                    .sub(0.5F, 0.5F, 0.5F);
            if (!canonical.isFinite()) {
                throw new AssertionError("Synthetic geometry contains a non-finite vertex");
            }
            x[vertex] = canonical.x;
            if (x[vertex] < -EPSILON) {
                left++;
            } else if (x[vertex] > EPSILON) {
                right++;
            } else {
                center++;
            }
        }
        if (center != 0) {
            return Topology.CENTER_VERTEX;
        }
        if (right == 0) {
            return Topology.WHOLE_LEFT;
        }
        if (left == 0) {
            return Topology.WHOLE_RIGHT;
        }

        int firstCrossing = -1;
        int secondCrossing = -1;
        int crossings = 0;
        for (int edge = 0; edge < BakedQuad.VERTEX_COUNT; edge++) {
            float current = x[edge];
            float next = x[(edge + 1) % BakedQuad.VERTEX_COUNT];
            if ((current < -EPSILON && next > EPSILON)
                    || (current > EPSILON && next < -EPSILON)) {
                if (crossings == 0) {
                    firstCrossing = edge;
                } else if (crossings == 1) {
                    secondCrossing = edge;
                }
                crossings++;
            }
        }
        if (crossings != 2) {
            return Topology.OTHER_CROSSING;
        }
        return Math.floorMod(firstCrossing - secondCrossing, BakedQuad.VERTEX_COUNT) == 2
                ? Topology.OPPOSITE_EDGE_CROSSING
                : left == 1
                ? Topology.ADJACENT_EDGE_LEFT_TRIANGLE
                : Topology.ADJACENT_EDGE_RIGHT_TRIANGLE;
    }

    private static void assertExpectedTopology(GeometryReport report, String scenario) {
        assertCount(report.totalQuads(), 12, scenario + " total quads");
        assertCount(report.count(Topology.WHOLE_LEFT), 2, scenario + " whole-left quads");
        assertCount(report.count(Topology.WHOLE_RIGHT), 2, scenario + " whole-right quads");
        assertCount(report.count(Topology.OPPOSITE_EDGE_CROSSING), 4,
                scenario + " opposite-edge crossings");
        assertCount(report.count(Topology.ADJACENT_EDGE_LEFT_TRIANGLE), 2,
                scenario + " left-triangle crossings");
        assertCount(report.count(Topology.ADJACENT_EDGE_RIGHT_TRIANGLE), 2,
                scenario + " right-triangle crossings");
        assertCount(report.count(Topology.CENTER_VERTEX), 0, scenario + " center-vertex quads");
        assertCount(report.count(Topology.OTHER_CROSSING), 0, scenario + " other crossings");
        assertCount(report.currentKeep(), 2, scenario + " current keeps");
        assertCount(report.currentDrop(), 2, scenario + " current drops");
        assertCount(report.currentSplit(), 8, scenario + " current splits");
        assertCount(report.incompatibilities().getOrDefault(
                BakedQuadClipper.Incompatibility.UNSUPPORTED_TOPOLOGY, 0), 0,
                scenario + " unsupported-topology results");
        assertCount(report.incompatibilities().getOrDefault(
                BakedQuadClipper.Incompatibility.NOT_RECTANGLE, 0), 0,
                scenario + " non-rectangle results");
        assertCount(report.incompatibilities().getOrDefault(
                BakedQuadClipper.Incompatibility.UNSUPPORTED_EXTENSION_METADATA, 0), 0,
                scenario + " extension-metadata results");
        assertCount(report.candidateLeftPrimitives(), 12, scenario + " left candidate primitives");
        assertCount(report.candidateRightPrimitives(), 12, scenario + " right candidate primitives");
        assertCount(report.candidateLeftTriangles(), 4, scenario + " left candidate triangles");
        assertCount(report.candidateRightTriangles(), 4, scenario + " right candidate triangles");
        assertCount(report.maximumCandidateExpansion(), 2, scenario + " maximum candidate expansion");
        assertCount(report.candidateSubmissions(), 2, scenario + " candidate submissions");
    }

    private static void assertProductionClipping(Minecraft client) {
        ItemStack leftSource = modelBoot(Items.IRON_BOOTS, RED_MODEL);
        ItemStack rightSource = modelBoot(Items.GOLDEN_BOOTS, BLUE_MODEL);
        ItemStackRenderState state = resolve(client, mixedStack(leftSource, rightSource));
        ItemStackRenderStateAccessor accessor = (ItemStackRenderStateAccessor) (Object) state;
        if (accessor.mismatchedboots$getActiveLayerCount() != 2) {
            throw new AssertionError("Mixed synthetic fixture did not resolve two source layers");
        }
        assertProductionLayer(accessor.mismatchedboots$getLayers()[0], QuadClipper.Side.LEFT);
        assertProductionLayer(accessor.mismatchedboots$getLayers()[1], QuadClipper.Side.RIGHT);

        int[] extents = {0};
        state.visitExtents(position -> {
            if (!position.isFinite()) {
                throw new AssertionError("Production clipping exposed a non-finite extent");
            }
            extents[0]++;
        });
        if (extents[0] == 0) {
            throw new AssertionError("Production clipping exposed no model extents");
        }
    }

    private static void assertGenuinelyIncompatibleFallback(Minecraft client) {
        ItemStack special = modelBoot(Items.IRON_BOOTS, Identifier.withDefaultNamespace("chest"));
        ItemStack ordinary = modelBoot(Items.GOLDEN_BOOTS, BLUE_MODEL);
        ItemStackRenderState ordinaryBaseline = resolve(client, ordinary);
        ItemStackRenderState state = resolve(client, mixedStack(ordinary, special));
        ItemStackRenderStateAccessor accessor = (ItemStackRenderStateAccessor) (Object) state;
        if (accessor.mismatchedboots$getActiveLayerCount() != 2
                || ((LayerRenderStateAccessor) (Object) accessor.mismatchedboots$getLayers()[1])
                .mismatchedboots$getSpecialRenderer() == null) {
            throw new AssertionError("Genuinely incompatible fixture did not resolve a special layer");
        }
        LayerRenderStateAccessor ordinaryLayer = (LayerRenderStateAccessor) (Object)
                accessor.mismatchedboots$getLayers()[0];
        LayerRenderStateAccessor baselineLayer = (LayerRenderStateAccessor) (Object)
                ((ItemStackRenderStateAccessor) (Object) ordinaryBaseline).mismatchedboots$getLayers()[0];
        if (!ordinaryLayer.mismatchedboots$getQuads().equals(baselineLayer.mismatchedboots$getQuads())) {
            throw new AssertionError("Late special-renderer failure partially committed the ordinary source");
        }
        Matrix4f left = new Matrix4f(((LayerRenderStateAccessor) (Object)
                accessor.mismatchedboots$getLayers()[0]).mismatchedboots$getLocalTransform());
        Matrix4f right = new Matrix4f(((LayerRenderStateAccessor) (Object)
                accessor.mismatchedboots$getLayers()[1]).mismatchedboots$getLocalTransform());
        assertNear(left.m00(), 0.8F, "left incompatible fallback scale");
        assertNear(right.m00(), 0.8F, "right incompatible fallback scale");
        assertNear(left.m30(), -0.15F, "left incompatible fallback offset");
        assertNear(right.m30(), 0.15F, "right incompatible fallback offset");
    }

    private static void assertGuiClipping(Minecraft client) {
        ItemStack left = modelBoot(Items.IRON_BOOTS, RED_MODEL);
        ItemStack right = modelBoot(Items.GOLDEN_BOOTS, BLUE_MODEL);
        ItemStack mixed = mixedStack(left, right);
        mixed.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        ItemStack inverse = mixedStack(right, left);
        inverse.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        GuiRenderState gui = new GuiRenderState();
        GuiGraphicsExtractor graphics = new GuiGraphicsExtractor(client, gui, 0, 0);
        graphics.item(mixed, 40, 40);
        graphics.item(inverse, 80, 40);
        List<GuiItemRenderState> items = new ArrayList<>();
        gui.forEachItem(items::add);
        assertCount(items.size(), 2, "GUI compositions");
        if (!(items.getFirst().itemStackRenderState() instanceof MixedBootsGuiRenderState combined)
                || items.stream().anyMatch(item -> item.scissorArea() != null)
                || items.getFirst().itemStackRenderState().getModelIdentity()
                .equals(items.getLast().itemStackRenderState().getModelIdentity())) {
            throw new AssertionError("Oblique GUI models did not select distinct canonical compositions");
        }
        for (int side = 0; side < 2; side++) {
            ItemStackRenderState source = combined.sources().get(side);
            ItemStackRenderState.LayerRenderState layer = ((ItemStackRenderStateAccessor) (Object) source)
                    .mismatchedboots$getLayers()[0];
            assertProductionLayer(layer, side == 0 ? QuadClipper.Side.LEFT : QuadClipper.Side.RIGHT);
        }
        NeoForgeGeometrySubmissionProbe.arm(2);
        combined.submit(new PoseStack(), new SubmitNodeStorage(), 0x00F000F0, 0, 0);
        for (var submission : NeoForgeGeometrySubmissionProbe.takeAll()) {
            if (submission.displayContext() != ItemDisplayContext.GUI
                    || submission.foilType() != ItemStackRenderState.FoilType.STANDARD) {
                throw new AssertionError("Composed GUI submissions changed context or root foil");
            }
        }
        combined.visitExtents(position -> {
            if (!position.isFinite()) throw new AssertionError("GUI composition has non-finite extents");
        });

        ItemStack special = modelBoot(Items.GOLDEN_BOOTS, Identifier.withDefaultNamespace("chest"));
        ItemStack incompatible = mixedStack(left, special);
        for (boolean rotated : List.of(false, true)) {
            gui = new GuiRenderState();
            graphics = new GuiGraphicsExtractor(client, gui, 0, 0);
            if (rotated) graphics.pose().rotate(0.2F);
            else graphics.enableScissor(42, 40, 54, 56);
            graphics.item(incompatible, 40, 40);
            if (!rotated) graphics.disableScissor();
            graphics.item(modelBoot(Items.DIAMOND_BOOTS, Identifier.withDefaultNamespace("diamond_boots")), 80, 40);
            items.clear();
            gui.forEachItem(items::add);
            assertCount(items.size(), rotated ? 2 : 3, "GUI incompatible route submissions");
            if (items.getLast().scissorArea() != null) {
                throw new AssertionError("GUI fallback leaked the caller scissor");
            }
            if (rotated) {
                if (!(items.getFirst().itemStackRenderState() instanceof MixedBootsGuiRenderState fallback)) {
                    throw new AssertionError("Unsafe GUI pose did not select side-by-side fallback");
                }
                Matrix4f transform = ((LayerRenderStateAccessor) (Object)
                        ((ItemStackRenderStateAccessor) (Object) fallback.sources().getFirst())
                                .mismatchedboots$getLayers()[0]).mismatchedboots$getLocalTransform();
                assertNear(transform.m00(), 0.8F, "GUI side-by-side scale");
                assertNear(transform.m30(), -0.15F, "GUI side-by-side offset");
            } else {
                if (!new ScreenRectangle(42, 40, 6, 16).equals(items.get(0).scissorArea())
                        || !new ScreenRectangle(48, 40, 6, 16).equals(items.get(1).scissorArea())) {
                    throw new AssertionError("GUI fallback lost native parent-scissor intersection");
                }
                ItemStackRenderState ordinary = items.getFirst().itemStackRenderState();
                LayerRenderStateAccessor layer = (LayerRenderStateAccessor) (Object)
                        ((ItemStackRenderStateAccessor) (Object) ordinary).mismatchedboots$getLayers()[0];
                assertCount(layer.mismatchedboots$getQuads().size(), 12, "GUI intact source primitive count");
                assertCount(triangleCount(layer.mismatchedboots$getQuads()), 0, "GUI intact source triangle count");
                assertMatrix(layer.mismatchedboots$getLocalTransform(), new Matrix4f(), "GUI intact source transform");
            }
        }
    }

    private static ItemStack mixedStack(ItemStack leftSource, ItemStack rightSource) {
        DataComponentType<MixedBootsData> dataType = ModContent.mixedBootsDataType()
                .orElseThrow(() -> new AssertionError("Mismatched boots data component is not registered"));
        Item mixedItem = BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID);
        if (mixedItem == null) {
            throw new AssertionError("Mismatched boots item is not registered");
        }
        DataComponentPatch components = DataComponentPatch.builder()
                .set(DataComponents.ITEM_MODEL, ModContent.MIXED_BOOTS_ID)
                .set(dataType, new MixedBootsData(new BootHalf(leftSource), new BootHalf(rightSource)))
                .build();
        return new ItemStack(Holder.direct(mixedItem), 1, components);
    }

    private static void assertProductionLayer(
            ItemStackRenderState.LayerRenderState renderLayer, QuadClipper.Side side) {
        LayerRenderStateAccessor layer = (LayerRenderStateAccessor) (Object) renderLayer;
        Matrix4f transform = new Matrix4f(layer.mismatchedboots$getLocalTransform());
        assertMatrix(transform, new Matrix4f(), side + " production transform");
        assertCount(layer.mismatchedboots$getQuads().size(), 12,
                side + " production primitive count");
        assertCount(triangleCount(layer.mismatchedboots$getQuads()), 4,
                side + " production triangle count");
        for (BakedQuad quad : layer.mismatchedboots$getQuads()) {
            for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
                float x = transform.transformPosition(new Vector3f(quad.position(vertex)))
                        .sub(0.5F, 0.5F, 0.5F).x;
                if ((side == QuadClipper.Side.LEFT && x > EPSILON)
                        || (side == QuadClipper.Side.RIGHT && x < -EPSILON)) {
                    throw new AssertionError(side + " production layer retained opposite-side geometry");
                }
            }
        }
    }

    private static ItemStackRenderState resolve(Minecraft client, ItemStack stack) {
        ItemStackRenderState state = new ItemStackRenderState();
        client.getItemModelResolver().updateForTopItem(
                state, stack, ItemDisplayContext.FIXED, null, null, 0x22B5);
        return state;
    }

    private static ItemStack modelBoot(Item item, Identifier model) {
        DataComponentPatch components = DataComponentPatch.builder()
                .set(DataComponents.ITEM_MODEL, model)
                .set(DataComponents.MAX_STACK_SIZE, 1)
                .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build())
                .build();
        return new ItemStack(Holder.direct(item), 1, components);
    }

    private static void assertUnattended(Minecraft client) {
        if (client.options.getSoundSourceVolume(SoundSource.MASTER) != 0.0F) {
            throw new AssertionError("Geometry client master volume is not muted");
        }
        if (Boolean.getBoolean("mismatchedboots.test.hideClientWindow")
                && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) != GLFW.GLFW_FALSE) {
            throw new AssertionError("Geometry client window is visible");
        }
    }

    private static void assertNear(float actual, float expected, String description) {
        if (!Float.isFinite(actual) || Math.abs(actual - expected) > EPSILON) {
            throw new AssertionError(description + " expected " + expected + " but was " + actual);
        }
    }

    private static void assertMatrix(Matrix4f actual, Matrix4f expected, String description) {
        float[] actualValues = actual.get(new float[16]);
        float[] expectedValues = expected.get(new float[16]);
        for (int index = 0; index < actualValues.length; index++) {
            assertNear(actualValues[index], expectedValues[index], description + " index " + index);
        }
    }

    private static void assertCount(int actual, int expected, String description) {
        if (actual != expected) {
            throw new AssertionError(description + " expected " + expected + " but was " + actual);
        }
    }

    private static Identifier testId(String path) {
        return Identifier.fromNamespaceAndPath("mismatchedboots_runtime_test", path);
    }

    private enum Topology {
        WHOLE_LEFT(4, 0),
        WHOLE_RIGHT(0, 4),
        OPPOSITE_EDGE_CROSSING(4, 4),
        ADJACENT_EDGE_LEFT_TRIANGLE(3, 5),
        ADJACENT_EDGE_RIGHT_TRIANGLE(5, 3),
        CENTER_VERTEX(-1, -1),
        OTHER_CROSSING(-1, -1);

        private final int leftVertices;
        private final int rightVertices;

        Topology(int leftVertices, int rightVertices) {
            this.leftVertices = leftVertices;
            this.rightVertices = rightVertices;
        }
    }

    private record GeometryReport(
            int totalQuads,
            Map<Topology, Integer> topologies,
            Map<BakedQuadClipper.Incompatibility, Integer> incompatibilities,
            int currentKeep,
            int currentDrop,
            int currentSplit,
            int candidateLeftPrimitives,
            int candidateRightPrimitives,
            int candidateLeftTriangles,
            int candidateRightTriangles,
            int maximumCandidateExpansion,
            int candidateSubmissions) {
        GeometryReport {
            topologies = Map.copyOf(topologies);
            incompatibilities = Map.copyOf(incompatibilities);
        }

        int count(Topology topology) {
            return topologies.getOrDefault(topology, 0);
        }

        String describe(String name) {
            StringBuilder result = new StringBuilder(name)
                    .append(".totalQuads=").append(totalQuads).append('\n');
            for (Topology topology : Topology.values()) {
                result.append(name).append('.').append(topology.name().toLowerCase())
                        .append('=').append(count(topology));
                if (topology.leftVertices >= 0) {
                    result.append(" (left=").append(topology.leftVertices)
                            .append(", right=").append(topology.rightVertices).append(')');
                }
                result.append('\n');
            }
            result.append(name).append(".current.keep=").append(currentKeep).append('\n')
                    .append(name).append(".current.drop=").append(currentDrop).append('\n')
                    .append(name).append(".current.split=").append(currentSplit).append('\n')
                    .append(name).append(".candidate.leftPrimitives=")
                    .append(candidateLeftPrimitives).append('\n')
                    .append(name).append(".candidate.rightPrimitives=")
                    .append(candidateRightPrimitives).append('\n')
                    .append(name).append(".candidate.leftTriangles=")
                    .append(candidateLeftTriangles).append('\n')
                    .append(name).append(".candidate.rightTriangles=")
                    .append(candidateRightTriangles).append('\n')
                    .append(name).append(".candidate.maximumExpansion=")
                    .append(maximumCandidateExpansion).append('\n')
                    .append(name).append(".candidate.submissions=")
                    .append(candidateSubmissions).append('\n');
            for (BakedQuadClipper.Incompatibility incompatibility : BakedQuadClipper.Incompatibility.values()) {
                result.append(name).append(".current.incompatible.")
                        .append(incompatibility.name().toLowerCase()).append('=')
                        .append(incompatibilities.getOrDefault(incompatibility, 0)).append('\n');
            }
            return result.toString();
        }
    }
}
