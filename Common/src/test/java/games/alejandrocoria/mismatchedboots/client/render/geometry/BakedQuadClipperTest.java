package games.alejandrocoria.mismatchedboots.client.render.geometry;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BakedQuadClipperTest {
    private static final float EPSILON = QuadClipper.GEOMETRY_EPSILON;

    @Test
    void rebuildsPositionsAndIndependentUvComponentsFromAHandAuthoredOracle() {
        try (MaterialFixture fixture = MaterialFixture.animatedTranslucent()) {
            BakedQuad input = quad(fixture.material());

            BakedQuadClipper.Result leftResult = clip(input, new Matrix4f(), QuadClipper.Side.LEFT);
            BakedQuadClipper.Result rightResult = clip(input, new Matrix4f(), QuadClipper.Side.RIGHT);

            assertEquals(QuadClipper.Decision.SPLIT, leftResult.decision());
            assertEquals(QuadClipper.Decision.SPLIT, rightResult.decision());
            BakedQuad left = leftResult.quad();
            BakedQuad right = rightResult.quad();

            assertPosition(left.position(0), 0.0F, 0.0F, 0.5F);
            assertPosition(left.position(1), 0.5F, 0.0F, 0.5F);
            assertPosition(left.position(2), 0.5F, 1.0F, 0.5F);
            assertPosition(left.position(3), 0.0F, 1.0F, 0.5F);
            assertUv(left.packedUV(0), 2.0F, 4.0F);
            assertUv(left.packedUV(1), 6.0F, 6.0F);
            assertUv(left.packedUV(2), 10.0F, 18.0F);
            assertUv(left.packedUV(3), 6.0F, 16.0F);

            assertPosition(right.position(0), 0.5F, 0.0F, 0.5F);
            assertPosition(right.position(1), 1.0F, 0.0F, 0.5F);
            assertPosition(right.position(2), 1.0F, 1.0F, 0.5F);
            assertPosition(right.position(3), 0.5F, 1.0F, 0.5F);
            assertUv(right.packedUV(0), 6.0F, 6.0F);
            assertUv(right.packedUV(1), 10.0F, 8.0F);
            assertUv(right.packedUV(2), 14.0F, 20.0F);
            assertUv(right.packedUV(3), 10.0F, 18.0F);

            assertPositionBits(input.position0(), left.position0());
            assertPositionBits(input.position3(), left.position3());
            assertPositionBits(input.position1(), right.position1());
            assertPositionBits(input.position2(), right.position2());
            assertEquals(input.packedUV0(), left.packedUV0());
            assertEquals(input.packedUV3(), left.packedUV3());
            assertEquals(input.packedUV1(), right.packedUV1());
            assertEquals(input.packedUV2(), right.packedUV2());
            assertSouthWinding(left);
            assertSouthWinding(right);
        }
    }

    @Test
    void preservesDirectionAndEveryMaterialFieldByIdentity() {
        try (MaterialFixture fixture = MaterialFixture.animatedTranslucent()) {
            BakedQuad input = quad(fixture.material());
            BakedQuad output = clip(input, new Matrix4f(), QuadClipper.Side.LEFT).quad();

            assertEquals(Direction.SOUTH, output.direction());
            assertSame(input.materialInfo(), output.materialInfo());
            assertSame(fixture.sprite(), output.materialInfo().sprite());
            assertSame(ChunkSectionLayer.TRANSLUCENT, output.materialInfo().layer());
            assertSame(fixture.renderType(), output.materialInfo().itemRenderType());
            assertEquals(7, output.materialInfo().tintIndex());
            assertFalse(output.materialInfo().shade());
            assertEquals(13, output.materialInfo().lightEmission());
            assertEquals(BakedQuad.FLAG_TRANSLUCENT | BakedQuad.FLAG_ANIMATED,
                    output.materialInfo().flags());
        }
    }

    @Test
    void returnsTheOriginalQuadForKeepAndNoQuadForDrop() {
        try (MaterialFixture fixture = MaterialFixture.animatedTranslucent()) {
            BakedQuad input = quad(fixture.material());
            Matrix4f whollyLeft = new Matrix4f().translation(-2.0F, 0.0F, 0.0F);

            BakedQuadClipper.Result kept = clip(input, whollyLeft, QuadClipper.Side.LEFT);
            BakedQuadClipper.Result dropped = clip(input, whollyLeft, QuadClipper.Side.RIGHT);

            assertEquals(QuadClipper.Decision.KEEP, kept.decision());
            assertSame(input, kept.quad());
            assertNull(kept.incompatibility());
            assertEquals(QuadClipper.Decision.DROP, dropped.decision());
            assertNull(dropped.quad());
            assertNull(dropped.incompatibility());
        }
    }

    @Test
    void appliesTheLocalTransformOnlyToClassificationAndKeepsRawOutputSpace() {
        try (MaterialFixture fixture = MaterialFixture.animatedTranslucent()) {
            BakedQuad input = quad(fixture.material());
            Matrix4f translated = new Matrix4f().translation(0.25F, 0.0F, 0.0F);

            BakedQuad left = clip(input, translated, QuadClipper.Side.LEFT).quad();

            assertPosition(left.position(1), 0.25F, 0.0F, 0.5F);
            assertPosition(left.position(2), 0.25F, 1.0F, 0.5F);
            assertUv(left.packedUV(1), 4.0F, 5.0F);
            assertUv(left.packedUV(2), 8.0F, 17.0F);
        }
    }

    @Test
    void rejectsUnsupportedExtensionMetadataBeforeReconstruction() {
        try (MaterialFixture fixture = MaterialFixture.animatedTranslucent()) {
            BakedQuad input = quad(fixture.material());

            BakedQuadClipper.Result result = BakedQuadClipper.clip(
                    input,
                    new Matrix4f(),
                    QuadClipper.Side.LEFT,
                    BakedQuadClipper.ExtensionMetadata.UNSUPPORTED);

            assertEquals(QuadClipper.Decision.INCOMPATIBLE, result.decision());
            assertEquals(BakedQuadClipper.Incompatibility.UNSUPPORTED_EXTENSION_METADATA,
                    result.incompatibility());
            assertNull(result.quad());
            assertEquals(quad(fixture.material()), input);
        }
    }

    @Test
    void mapsFiniteGeometryFailuresToStableAdapterReasons() {
        try (MaterialFixture fixture = MaterialFixture.animatedTranslucent()) {
            BakedQuad trapezoid = new BakedQuad(
                    new Vector3f(0.0F, 0.0F, 0.5F),
                    new Vector3f(1.0F, 0.0F, 0.5F),
                    new Vector3f(0.75F, 1.0F, 0.5F),
                    new Vector3f(0.0F, 1.0F, 0.5F),
                    UVPair.pack(0.0F, 0.0F),
                    UVPair.pack(1.0F, 0.0F),
                    UVPair.pack(1.0F, 1.0F),
                    UVPair.pack(0.0F, 1.0F),
                    Direction.SOUTH,
                    fixture.material());

            BakedQuadClipper.Result result = clip(trapezoid, new Matrix4f(), QuadClipper.Side.LEFT);

            assertEquals(QuadClipper.Decision.INCOMPATIBLE, result.decision());
            assertEquals(BakedQuadClipper.Incompatibility.NOT_RECTANGLE, result.incompatibility());
            assertNull(result.quad());
        }
    }

    @Test
    void rejectsAFiniteTransformThatTurnsTheSourceRectangleIntoAShear() {
        try (MaterialFixture fixture = MaterialFixture.animatedTranslucent()) {
            BakedQuad input = quad(fixture.material());
            Matrix4f shear = new Matrix4f().m10(0.5F);

            BakedQuadClipper.Result result = clip(input, shear, QuadClipper.Side.LEFT);

            assertEquals(QuadClipper.Decision.INCOMPATIBLE, result.decision());
            assertEquals(BakedQuadClipper.Incompatibility.NOT_RECTANGLE, result.incompatibility());
            assertNull(result.quad());
        }
    }

    @Test
    void rejectsNonFiniteVerticesAndUvsExplicitly() {
        try (MaterialFixture fixture = MaterialFixture.animatedTranslucent()) {
            BakedQuad invalidPosition = replacingPosition(
                    quad(fixture.material()), new Vector3f(Float.NaN, 0.0F, 0.5F));
            BakedQuad invalidUv = replacingUv(quad(fixture.material()), UVPair.pack(0.0F, Float.POSITIVE_INFINITY));

            assertThrows(IllegalArgumentException.class,
                    () -> clip(invalidPosition, new Matrix4f(), QuadClipper.Side.LEFT));
            assertThrows(IllegalArgumentException.class,
                    () -> clip(invalidUv, new Matrix4f(), QuadClipper.Side.LEFT));
        }
    }

    @Test
    void isDeterministicAcrossRunsAndEqualQuadIdentities() {
        try (MaterialFixture fixture = MaterialFixture.animatedTranslucent()) {
            BakedQuad first = quad(fixture.material());
            BakedQuad equal = quad(fixture.material());

            BakedQuadClipper.Result firstResult = clip(first, new Matrix4f(), QuadClipper.Side.RIGHT);
            BakedQuadClipper.Result repeated = clip(first, new Matrix4f(), QuadClipper.Side.RIGHT);
            BakedQuadClipper.Result equalIdentity = clip(equal, new Matrix4f(), QuadClipper.Side.RIGHT);

            assertEquals(firstResult, repeated);
            assertEquals(firstResult, equalIdentity);
            assertEquals(quad(fixture.material()), first);
        }
    }

    @Test
    void validatesMalformedAdapterInputAndResultStates() {
        try (MaterialFixture fixture = MaterialFixture.animatedTranslucent()) {
            BakedQuad input = quad(fixture.material());
            BakedQuad missingDirection = new BakedQuad(
                    input.position0(), input.position1(), input.position2(), input.position3(),
                    input.packedUV0(), input.packedUV1(), input.packedUV2(), input.packedUV3(),
                    null, input.materialInfo());

            assertThrows(IllegalArgumentException.class,
                    () -> clip(missingDirection, new Matrix4f(), QuadClipper.Side.LEFT));
            assertThrows(IllegalArgumentException.class, () -> new BakedQuadClipper.Result(
                    QuadClipper.Decision.SPLIT, null, null));
        }
    }

    private static BakedQuadClipper.Result clip(
            BakedQuad input, Matrix4f transform, QuadClipper.Side side) {
        return BakedQuadClipper.clip(
                input, transform, side, BakedQuadClipper.ExtensionMetadata.NONE);
    }

    private static BakedQuad replacingPosition(BakedQuad input, Vector3f position0) {
        return new BakedQuad(
                position0,
                input.position1(),
                input.position2(),
                input.position3(),
                input.packedUV0(),
                input.packedUV1(),
                input.packedUV2(),
                input.packedUV3(),
                input.direction(),
                input.materialInfo());
    }

    private static BakedQuad replacingUv(BakedQuad input, long packedUv0) {
        return new BakedQuad(
                input.position0(),
                input.position1(),
                input.position2(),
                input.position3(),
                packedUv0,
                input.packedUV1(),
                input.packedUV2(),
                input.packedUV3(),
                input.direction(),
                input.materialInfo());
    }

    private static BakedQuad quad(BakedQuad.MaterialInfo material) {
        return new BakedQuad(
                new Vector3f(0.0F, 0.0F, 0.5F),
                new Vector3f(1.0F, 0.0F, 0.5F),
                new Vector3f(1.0F, 1.0F, 0.5F),
                new Vector3f(0.0F, 1.0F, 0.5F),
                UVPair.pack(2.0F, 4.0F),
                UVPair.pack(10.0F, 8.0F),
                UVPair.pack(14.0F, 20.0F),
                UVPair.pack(6.0F, 16.0F),
                Direction.SOUTH,
                material);
    }

    private static void assertPosition(
            org.joml.Vector3fc actual, float x, float y, float z) {
        assertEquals(x, actual.x(), EPSILON);
        assertEquals(y, actual.y(), EPSILON);
        assertEquals(z, actual.z(), EPSILON);
    }

    private static void assertPositionBits(
            org.joml.Vector3fc expected, org.joml.Vector3fc actual) {
        assertEquals(Float.floatToIntBits(expected.x()), Float.floatToIntBits(actual.x()));
        assertEquals(Float.floatToIntBits(expected.y()), Float.floatToIntBits(actual.y()));
        assertEquals(Float.floatToIntBits(expected.z()), Float.floatToIntBits(actual.z()));
    }

    private static void assertSouthWinding(BakedQuad quad) {
        Vector3f edge0 = new Vector3f(quad.position1()).sub(quad.position0());
        Vector3f edge1 = new Vector3f(quad.position2()).sub(quad.position1());
        assertTrue(edge0.cross(edge1).z > 0.0F);
    }

    private static void assertUv(long packed, float u, float v) {
        assertEquals(u, UVPair.unpackU(packed), EPSILON);
        assertEquals(v, UVPair.unpackV(packed), EPSILON);
    }

    private record MaterialFixture(
            NativeImage image,
            TextureAtlasSprite sprite,
            RenderType renderType,
            BakedQuad.MaterialInfo material) implements AutoCloseable {
        static MaterialFixture animatedTranslucent() {
            NativeImage image = new NativeImage(1, 2, true);
            image.setPixel(0, 0, 0xFFFFFFFF);
            image.setPixel(0, 1, 0x80FFFFFF);
            AnimationMetadataSection animation = new AnimationMetadataSection(
                    Optional.empty(), Optional.of(1), Optional.of(1), 1, false);
            SpriteContents contents = new SpriteContents(
                    Identifier.fromNamespaceAndPath("mismatchedboots", "test/animated_translucent"),
                    new FrameSize(1, 1),
                    image,
                    Optional.of(animation),
                    List.of(),
                    Optional.empty());
            TextureAtlasSprite sprite = new TestTextureAtlasSprite(
                    Identifier.fromNamespaceAndPath("mismatchedboots", "test_atlas"),
                    contents,
                    1,
                    2,
                    0,
                    0,
                    0);
            RenderType renderType = Sheets.translucentItemSheet();
            BakedQuad.MaterialInfo material = new BakedQuad.MaterialInfo(
                    sprite, ChunkSectionLayer.TRANSLUCENT, renderType, 7, false, 13);
            assertTrue(contents.isAnimated());
            return new MaterialFixture(image, sprite, renderType, material);
        }

        @Override
        public void close() {
            sprite.close();
        }
    }

    private static final class TestTextureAtlasSprite extends TextureAtlasSprite {
        private TestTextureAtlasSprite(
                Identifier atlasLocation,
                SpriteContents contents,
                int atlasWidth,
                int atlasHeight,
                int x,
                int y,
                int padding) {
            super(atlasLocation, contents, atlasWidth, atlasHeight, x, y, padding);
        }
    }
}
