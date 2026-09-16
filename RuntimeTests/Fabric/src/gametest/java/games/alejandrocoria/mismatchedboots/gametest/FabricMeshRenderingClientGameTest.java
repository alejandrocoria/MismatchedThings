package games.alejandrocoria.mismatchedboots.gametest;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedboots.client.mixin.ItemStackRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.mixin.LayerRenderStateAccessor;
import games.alejandrocoria.mismatchedboots.client.render.geometry.FabricMeshClipper;
import games.alejandrocoria.mismatchedboots.client.render.geometry.QuadClipper;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.gametest.mixin.LayerRenderStateTestAccessor;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.renderer.v1.Renderer;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableMesh;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadAtlas;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.fabric.impl.client.renderer.LayerRenderStateExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPatterns;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class FabricMeshRenderingClientGameTest implements FabricClientGameTest {
    private static final Identifier RED = id("mesh_rotated_red");
    private static final Identifier BLUE = id("mesh_rotated_blue");
    private static final Matrix4f IDENTITY = new Matrix4f();

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            context.waitFor(client -> client.player != null && client.level != null);
            context.runOnClient(client -> {
                verifyAttributes();
                verifyTessellation();
                verifyBoundaries();
                verifyProduction(client);
                verifyVisualComponents(client);
                verifyAtomicFallback(client);
            });
        } finally {
            MeshTestFixtures.clear();
            MeshSubmissionProbe.take();
        }
    }

    private static void verifyAttributes() {
        for (var foil : new ItemStackRenderState.FoilType[]{null, ItemStackRenderState.FoilType.NONE,
                ItemStackRenderState.FoilType.STANDARD, ItemStackRenderState.FoilType.SPECIAL}) {
            for (int normals = 0; normals < 4; normals++) {
                for (boolean itemAtlas : List.of(false, true)) {
                    MutableMesh source = gradient(normals, foil, itemAtlas);
                    var original = MeshSubmissionProbe.fingerprint(source);
                    var plan = FabricMeshClipper.plan(source, IDENTITY, QuadClipper.Side.LEFT);
                    require(plan.compatible(), "Gradient mesh was rejected");
                    require(original.equals(MeshSubmissionProbe.fingerprint(source)), "Planning mutated the source");
                    plan.commit().run();
                    var output = MeshSubmissionProbe.fingerprint(source);
                    require(output.size() == 1, "Opposite-edge cut did not emit one quad");
                    var face = output.getFirst();
                    require(face.properties().equals(original.getFirst().properties()), "Quad metadata changed");
                    require(face.vertices().getFirst().equals(original.getFirst().vertices().getFirst()), "Retained vertex changed");
                    var intersection = face.vertices().get(1);
                    close(intersection.position().x, 0.5F, 0.000001F, "Intersection position");
                    close(intersection.u(), 20, 0.000001F, "Intersection U");
                    close(intersection.v(), 22, 0.000001F, "Intersection V");
                    require(intersection.color() == 0x6080A040, "ARGB channels were not interpolated independently");
                    require(intersection.lightmap() == LightCoordsUtil.smoothPack(61, 82), "Smooth light interpolation lost precision");
                    require((intersection.normal() != null) == (normals != 0), "Generated normal presence changed");
                    if (normals != 0) {
                        Vector3f expected = (normals & 2) != 0 ? new Vector3f(0, 0.3F, 0.9F).normalize() : new Vector3f(0, 0, 1);
                        close(intersection.normal().y, expected.y, 0.02F, "Interpolated normal Y");
                        close(intersection.normal().z, expected.z, 0.02F, "Interpolated normal Z");
                    }
                    require(plan.animated(), "Animated/foil mesh did not propagate animation");
                    MutableMesh unanimated = gradient(normals, foil, itemAtlas);
                    unanimated.forEachMutable(quad -> quad.animated(false));
                    var foilPlan = FabricMeshClipper.plan(unanimated, IDENTITY, QuadClipper.Side.LEFT);
                    require(foilPlan.animated() == (foil != null && foil != ItemStackRenderState.FoilType.NONE),
                            "Foil-only animation or inherited/null foil semantics changed");
                }
            }
        }
        MutableMesh source = gradient(3, null, false);
        source.forEachMutable(quad -> quad.normal(1, 0, 0, -1));
        var original = MeshSubmissionProbe.fingerprint(source);
        require(!FabricMeshClipper.plan(source, IDENTITY, QuadClipper.Side.LEFT).compatible(), "Cancelling normals were accepted");
        require(original.equals(MeshSubmissionProbe.fingerprint(source)), "Invalid normal planning mutated source");
    }

    private static MutableMesh gradient(int normals, ItemStackRenderState.FoilType foil, boolean itemAtlas) {
        MutableMesh mesh = Renderer.get().mutableMesh();
        var emitter = mesh.emitter();
        emitter.pos(0, 0, 0, 0.5F).pos(1, 1, 0, 0.5F).pos(2, 1, 1, 0.5F).pos(3, 0, 1, 0.5F)
                .uv(0, 10, 20).uv(1, 30, 24).uv(2, 34, 44).uv(3, 14, 40)
                .color(0x20406080, 0xA0C0E000, 0xA0C0E000, 0x20406080)
                .lightmap(LightCoordsUtil.smoothPack(31, 47), LightCoordsUtil.smoothPack(91, 117),
                        LightCoordsUtil.smoothPack(91, 117), LightCoordsUtil.smoothPack(31, 47))
                .atlas(itemAtlas ? QuadAtlas.ITEM : QuadAtlas.BLOCK)
                .itemRenderType(itemAtlas ? Sheets.translucentItemSheet() : Sheets.translucentBlockItemSheet())
                .chunkLayer(ChunkSectionLayer.TRANSLUCENT)
                .cullFace(Direction.SOUTH).nominalFace(Direction.SOUTH)
                .emissive(true).diffuseShade(false).ambientOcclusion(TriState.FALSE)
                .shadeMode(ShadeMode.ENHANCED).animated(true).foilType(foil).tintIndex(2).tag(381);
        if ((normals & 1) != 0) emitter.normal(0, 0, 0, 1).normal(3, 0, 0, 1);
        if ((normals & 2) != 0) emitter.normal(1, 0, 0.6F, 0.8F).normal(2, 0, 0.6F, 0.8F);
        emitter.emit();
        return mesh;
    }

    private static void verifyTessellation() {
        // This diamond's two hand-calculated intersections are (0.5,-0.5)
        // and (0.5,1.5). They remain the same for every cyclic start/winding.
        Vector3f[] positions = {new Vector3f(-0.5F, 0.5F, 0.5F), new Vector3f(1.5F, -1.5F, 0.5F),
                new Vector3f(3.5F, 0.5F, 0.5F), new Vector3f(1.5F, 2.5F, 0.5F)};
        for (boolean reverse : List.of(false, true)) {
            for (int start = 0; start < 4; start++) {
                for (var side : QuadClipper.Side.values()) {
                    MutableMesh source = Renderer.get().mutableMesh();
                    var emitter = source.emitter();
                    int first = start;
                    gradient(3, ItemStackRenderState.FoilType.SPECIAL, false).forEach(quad -> {
                        emitter.copyFrom(quad);
                        for (int index = 0; index < 4; index++) {
                            int original = Math.floorMod(first + (reverse ? -index : index), 4);
                            emitter.pos(index, positions[original]).uv(index, quad.u(original), quad.v(original))
                                    .color(index, quad.color(original)).lightmap(index, quad.lightmap(original))
                                    .normal(index, quad.copyNormal(original, null));
                        }
                        emitter.emit();
                    });
                    var original = MeshSubmissionProbe.fingerprint(source).getFirst();
                    var plan = FabricMeshClipper.plan(source, IDENTITY, side);
                    require(plan.compatible(), "Metadata-rich adjacent-edge cut rejected");
                    plan.commit().run();
                    var output = MeshSubmissionProbe.fingerprint(source);
                    require(output.size() == (side == QuadClipper.Side.LEFT ? 1 : 2), "Adjacent-edge primitive count");
                    require(output.stream().filter(face -> face.vertices().get(2).equals(face.vertices().get(3))).count() == 1,
                            "Encoded triangle did not repeat every vertex attribute");
                    for (var face : output) {
                        require(face.properties().equals(original.properties()), "Tessellation changed metadata");
                        close(face.normal().z, original.normal().z, 0.000001F, "Tessellation changed winding");
                        for (var vertex : face.vertices()) {
                            if (vertex.position().x != 0.5F) {
                                require(original.vertices().contains(vertex), "Tessellation changed retained attributes");
                            } else {
                                boolean lower = vertex.position().y == -0.5F;
                                require(lower || vertex.position().y == 1.5F, "Unexpected intersection position");
                                close(vertex.u(), lower ? 20 : 12, 0.000001F, "Adjacent-edge U");
                                close(vertex.v(), lower ? 22 : 30, 0.000001F, "Adjacent-edge V");
                                require(vertex.color() == (lower ? 0x6080A040 : 0x20406080), "Adjacent-edge color");
                                require(vertex.lightmap() == LightCoordsUtil.smoothPack(lower ? 61 : 31, lower ? 82 : 47),
                                        "Adjacent-edge lightmap");
                            }
                        }
                    }
                }
            }
        }
    }

    private static void verifyBoundaries() {
        require(FabricMeshClipper.plan(null, IDENTITY, QuadClipper.Side.LEFT).compatible(), "Absent mesh rejected");
        MutableMesh empty = Renderer.get().mutableMesh();
        FabricMeshClipper.plan(empty, IDENTITY, QuadClipper.Side.LEFT).commit().run();
        require(empty.size() == 0, "Empty mesh acquired geometry");
        for (float offset : List.of(-2F, 2F, 0F, -0.0000005F, 0.0000005F)) {
            for (var side : QuadClipper.Side.values()) {
                MutableMesh source = gradient(0, null, false);
                source.forEachMutable(quad -> {
                    for (int i = 0; i < 4; i++) quad.pos(i, Math.abs(offset) < 0.000001F ? 0.5F + offset : quad.x(i) + offset,
                            quad.y(i), quad.z(i));
                });
                var original = MeshSubmissionProbe.fingerprint(source);
                var plan = FabricMeshClipper.plan(source, IDENTITY, side);
                require(plan.compatible(), "Whole-side boundary rejected");
                plan.commit().run();
                boolean kept = offset < -0.000001F ? side == QuadClipper.Side.LEFT : side == QuadClipper.Side.RIGHT;
                require(kept ? original.equals(MeshSubmissionProbe.fingerprint(source)) : source.size() == 0,
                        "Whole-side or exact-plane ownership incorrect");
            }
        }
        MutableMesh source = gradient(0, null, false);
        require(!FabricMeshClipper.plan(source, new Matrix4f().scaling(Float.NaN), QuadClipper.Side.LEFT).compatible(),
                "Nonfinite transform accepted");
    }

    private static void verifyProduction(Minecraft client) {
        MeshTestFixtures.models.put(RED, MeshTestFixtures.Mode.MESH_ONLY);
        MeshTestFixtures.models.put(BLUE, MeshTestFixtures.Mode.MESH_ONLY);
        var red = boot(RED);
        var blue = boot(BLUE);
        var pair = MixedBootsFactory.createPair(red, blue);
        pair.primary().set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        pair.inverse().set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        require(mesh(resolve(client, red, ItemDisplayContext.FIXED), 0).size() == 12, "Red source should have 12 mesh faces");
        for (var context : ItemDisplayContext.values()) {
            for (var stack : List.of(pair.primary(), pair.inverse())) {
                ItemStackRenderState output = resolve(client, stack, context);
                for (int layer = 0; layer < 2; layer++) {
                    require(access(output, layer).mismatchedboots$getQuads().isEmpty(), "Mesh-only source acquired vanilla geometry");
                    var faces = MeshSubmissionProbe.fingerprint(mesh(output, layer));
                    require(faces.size() == 12, "Rotated clipped mesh should emit 12 faces per source");
                    require(faces.stream().filter(face -> face.vertices().get(2).equals(face.vertices().get(3))).count() == 4,
                            "Rotated clipped mesh should contain four encoded triangles");
                    assertOwnership(output, layer);
                }
                MeshSubmissionProbe.arm();
                output.submit(new PoseStack(), new SubmitNodeStorage(), 0x00F000F0, 0, 0);
                var submissions = MeshSubmissionProbe.take();
                require(submissions.size() == 2, "Mesh-only output did not reach ExtendedItemSubmit twice");
                for (int layer = 0; layer < 2; layer++) {
                    require(submissions.get(layer).mesh().equals(MeshSubmissionProbe.fingerprint(mesh(output, layer))),
                            "Extended submission lost geometry or metadata");
                    require(submissions.get(layer).foil() == ItemStackRenderState.FoilType.STANDARD, "Submission lost root foil");
                    require(submissions.get(layer).quads().isEmpty(), "Extended mesh-only submission had vanilla quads");
                    require(submissions.get(layer).context() == context && submissions.get(layer).pose().isFinite(),
                            "Extended submission changed display context or pose");
                }
                // Fabric's extents contribution must equal actual emitted mesh bounds.
                var visited = new Bounds();
                output.visitExtents(visited::add);
                var actual = new Bounds();
                for (int layer = 0; layer < 2; layer++) {
                    // visitExtents includes the display transform, just like submission,
                    // not only the source's local geometry transform.
                    var transform = submissions.get(layer).pose();
                    for (var face : MeshSubmissionProbe.fingerprint(mesh(output, layer))) {
                        for (var vertex : face.vertices()) actual.add(transform.transformPosition(vertex.position(), new Vector3f()));
                    }
                }
                require(visited.matches(actual), "Mesh-only visited extents do not match emitted vertices");
                resolveInto(client, output, stack, context);
                require(mesh(output, 0).size() == 12, "Repeated resolution accumulated stale mesh data");
            }
        }
        // Both collections in one layer, and baked/mesh pairs in both orders.
        for (var mode : List.of(MeshTestFixtures.Mode.BOTH, MeshTestFixtures.Mode.MESH_ONLY,
                MeshTestFixtures.Mode.EMPTY, MeshTestFixtures.Mode.DROP)) {
            MeshTestFixtures.models.put(RED, mode);
            MeshTestFixtures.models.remove(BLUE);
            for (var stack : List.of(pair.primary(), pair.inverse())) {
                var output = resolve(client, stack, ItemDisplayContext.FIXED);
                assertOwnership(output, 0);
                assertOwnership(output, 1);
                int meshLayer = stack == pair.primary() ? 0 : 1;
                if (mode == MeshTestFixtures.Mode.BOTH) {
                    require(!access(output, meshLayer).mismatchedboots$getQuads().isEmpty()
                                    && mesh(output, meshLayer).size() == 12,
                            "Both-collection fixture lost vanilla or mesh geometry");
                }
                if (mode == MeshTestFixtures.Mode.EMPTY || (mode == MeshTestFixtures.Mode.DROP && meshLayer == 0)) {
                    require(mesh(output, meshLayer).size() == 0 && access(output, meshLayer).mismatchedboots$getQuads().isEmpty(),
                            "Discarded mesh retained geometry");
                    require(testAccess(output, meshLayer).mismatchedboots$getExtents().get().length == 0,
                            "Discarded mesh retained vanilla extents");
                }
                verifyBounds(output);
            }
        }
        MeshTestFixtures.models.put(RED, MeshTestFixtures.Mode.ANIMATED);
        require(!resolve(client, red, ItemDisplayContext.FIXED).isAnimated(), "Animation fixture already marked its source state");
        var plain = MixedBootsFactory.createPair(red, blue).primary();
        var animatedOutput = resolve(client, plain, ItemDisplayContext.FIXED);
        require(animatedOutput.isAnimated(), "Surviving animated mesh did not mark the production state");
        MeshTestFixtures.clear();
    }

    private static void verifyAtomicFallback(Minecraft client) {
        for (var model : List.of(RED, id("geometry_multi_late_rotated"))) {
            for (var mode : List.of(MeshTestFixtures.Mode.INVALID_LATE, MeshTestFixtures.Mode.INVALID_NORMAL)) {
                MeshTestFixtures.models.put(model, mode);
                var red = boot(model);
                var blue = boot(BLUE);
                for (boolean swap : List.of(false, true)) {
                    ItemStack first = swap ? blue : red;
                    ItemStack second = swap ? red : blue;
                    var firstBase = resolve(client, first, ItemDisplayContext.FIXED);
                    var secondBase = resolve(client, second, ItemDisplayContext.FIXED);
                    var output = resolve(client, MixedBootsFactory.createPair(first, second).primary(), ItemDisplayContext.FIXED);
                    int outputIndex = 0;
                    for (var baseline : List.of(firstBase, secondBase)) {
                        int layerCount = ((ItemStackRenderStateAccessor) (Object) baseline).mismatchedboots$getActiveLayerCount();
                        for (int index = 0; index < layerCount; index++, outputIndex++) {
                            require(MeshSubmissionProbe.fingerprint(mesh(baseline, index)).equals(MeshSubmissionProbe.fingerprint(mesh(output, outputIndex))),
                                    "Late incompatibility partially changed mesh geometry or metadata: " + model + "/" + mode
                                            + "/swap=" + swap + "/layer=" + outputIndex);
                            require(access(baseline, index).mismatchedboots$getQuads().equals(access(output, outputIndex).mismatchedboots$getQuads()),
                                    "Late incompatibility partially changed vanilla geometry");
                            var expected = new Matrix4f().translation(baseline == firstBase ? -0.15F : 0.15F, 0, 0).scale(0.8F, 0.8F, 1)
                                    .mul(access(baseline, index).mismatchedboots$getLocalTransform());
                            require(expected.equals(access(output, outputIndex).mismatchedboots$getLocalTransform(), 0.000001F), "Fallback transform changed");
                        }
                    }
                }
                MeshTestFixtures.clear();
            }
        }
    }

    private static void verifyBounds(ItemStackRenderState output) {
        var visited = new Bounds();
        output.visitExtents(visited::add);
        MeshSubmissionProbe.arm();
        output.submit(new PoseStack(), new SubmitNodeStorage(), 0, 0, 0);
        var submissions = MeshSubmissionProbe.take();
        var expected = new Bounds();
        int count = ((ItemStackRenderStateAccessor) (Object) output).mismatchedboots$getActiveLayerCount();
        require(submissions.size() == count, "Bounds probe missed a vanilla or extended submission");
        for (int layer = 0; layer < count; layer++) {
            var transform = submissions.get(layer).pose();
            for (var face : MeshSubmissionProbe.fingerprint(mesh(output, layer))) {
                for (var vertex : face.vertices()) expected.add(transform.transformPosition(vertex.position(), new Vector3f()));
            }
            for (var extent : testAccess(output, layer).mismatchedboots$getExtents().get()) {
                expected.add(transform.transformPosition(extent, new Vector3f()));
            }
        }
        require(visited.matches(expected), "Mixed vanilla/mesh bounds union changed");
    }

    private static void verifyVisualComponents(Minecraft client) {
        MeshTestFixtures.runtimeRoute = true;
        var leather = new ItemStack(Items.LEATHER_BOOTS);
        leather.set(DataComponents.DYED_COLOR, new DyedItemColor(0xCC3344));
        leather.set(DataComponents.TRIM, new ArmorTrim(
                client.level.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.QUARTZ),
                client.level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.COAST)));
        var diamond = new ItemStack(Items.DIAMOND_BOOTS);
        leather.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        diamond.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        var pair = MixedBootsFactory.createPair(leather, diamond);
        for (var stack : List.of(pair.primary(), pair.inverse())) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            for (var context : ItemDisplayContext.values()) {
                var baseline = resolve(client, stack == pair.primary() ? leather : diamond, context);
                var output = resolve(client, stack, context);
                int sourceLayers = ((ItemStackRenderStateAccessor) (Object) baseline).mismatchedboots$getActiveLayerCount();
                for (int layer = 0; layer < sourceLayers; layer++) {
                    var before = testAccess(baseline, layer);
                    var after = testAccess(output, layer);
                    require(java.util.Objects.equals(before.mismatchedboots$getTintLayers(), after.mismatchedboots$getTintLayers()),
                            "Layer tint array changed");
                    require(before.mismatchedboots$getParticleMaterial() == after.mismatchedboots$getParticleMaterial(), "Particle material changed");
                    require(before.mismatchedboots$getItemTransform() == after.mismatchedboots$getItemTransform(), "Display transform changed");
                    require(before.mismatchedboots$usesBlockLight() == after.mismatchedboots$usesBlockLight(), "Layer lighting changed");
                    require(access(baseline, layer).mismatchedboots$getLocalTransform().equals(
                            access(output, layer).mismatchedboots$getLocalTransform()), "Visual-component fixture used fallback");
                }
                MeshSubmissionProbe.arm();
                output.submit(new PoseStack(), new SubmitNodeStorage(), 0, 0, 0);
                var submissions = MeshSubmissionProbe.take();
                require(submissions.size() >= 2, "Dyed/trimmed output did not submit meshes");
                for (int layer = 0; layer < submissions.size(); layer++) {
                    var tints = testAccess(output, layer).mismatchedboots$getTintLayers();
                    require(submissions.get(layer).tints().equals(tints == null ? List.of() : tints.intStream().boxed().toList()),
                            "Extended submission changed tints");
                    require(submissions.get(layer).foil() == ItemStackRenderState.FoilType.STANDARD, "Dyed/trimmed submission lost foil");
                }
                verifyBounds(output);
            }
        }
        MeshTestFixtures.clear();
    }

    private static void assertOwnership(ItemStackRenderState state, int layer) {
        var transform = access(state, layer).mismatchedboots$getLocalTransform();
        for (var face : MeshSubmissionProbe.fingerprint(mesh(state, layer))) {
            for (var vertex : face.vertices()) {
                float x = transform.transformPosition(vertex.position(), new Vector3f()).x - 0.5F;
                require(layer == 0 ? x <= 0.000001F : x >= -0.000001F, "Mesh vertex crossed the owned side");
            }
        }
        for (var quad : access(state, layer).mismatchedboots$getQuads()) {
            for (int vertex = 0; vertex < 4; vertex++) {
                float x = transform.transformPosition(quad.position(vertex), new Vector3f()).x - 0.5F;
                require(layer == 0 ? x <= 0.000001F : x >= -0.000001F, "Vanilla vertex crossed the owned side");
            }
        }
    }

    private static ItemStack boot(Identifier model) {
        ItemStack result = new ItemStack(Items.IRON_BOOTS);
        result.set(DataComponents.ITEM_MODEL, model);
        return result;
    }

    private static ItemStackRenderState resolve(Minecraft client, ItemStack stack, ItemDisplayContext context) {
        var state = new ItemStackRenderState();
        resolveInto(client, state, stack, context);
        return state;
    }

    private static void resolveInto(Minecraft client, ItemStackRenderState state, ItemStack stack, ItemDisplayContext context) {
        client.getItemModelResolver().updateForTopItem(state, stack, context, client.level, client.player, 0x3451);
    }

    private static LayerRenderStateAccessor access(ItemStackRenderState state, int index) {
        return (LayerRenderStateAccessor) (Object) ((ItemStackRenderStateAccessor) (Object) state).mismatchedboots$getLayers()[index];
    }

    private static LayerRenderStateTestAccessor testAccess(ItemStackRenderState state, int index) {
        return (LayerRenderStateTestAccessor) (Object) ((ItemStackRenderStateAccessor) (Object) state).mismatchedboots$getLayers()[index];
    }

    private static MutableMesh mesh(ItemStackRenderState state, int index) {
        return ((LayerRenderStateExtension) (Object) ((ItemStackRenderStateAccessor) (Object) state)
                .mismatchedboots$getLayers()[index]).fabric_getMutableMesh();
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("mismatchedboots-test", path);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void close(float actual, float expected, float epsilon, String message) {
        require(Float.isFinite(actual) && Math.abs(actual - expected) <= epsilon, message + ": " + actual + " != " + expected);
    }

    private static final class Bounds {
        final Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        final Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        void add(org.joml.Vector3fc value) { min.min(value); max.max(value); }
        boolean matches(Bounds other) { return min.equals(other.min, 0.000001F) && max.equals(other.max, 0.000001F); }
    }
}
