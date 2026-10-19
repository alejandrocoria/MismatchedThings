package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.MaterialAssetGroup;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.NeoForgeEquipmentRenderingClientTest.mixed;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.NeoForgeEquipmentRenderingClientTest.source;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.NeoForgeNativeEquipmentClientTest.renderPair;

/** Real cached Roots models, native deferred submissions, and a strictly test-only foot selector. */
public final class RootsRenderingSpike {
    private static boolean candidate;

    private RootsRenderingSpike() {
    }

    public static <S> Model<? super S> adapt(Model<? super S> result, Model<? super S> resolved) {
        if (!candidate || !(resolved instanceof HumanoidModel<?> humanoid)
                || !isRootsModel(resolved) || result == resolved) {
            return result;
        }
        boolean left = result.root() == humanoid.leftLeg;
        if (!left && result.root() != humanoid.rightLeg) {
            return result;
        }
        String name = left ? "left_foot" : "right_foot";
        if (!resolved.root().hasChild("left_foot") || !resolved.root().hasChild("right_foot")) {
            return result;
        }
        // Same structural view as production, but select the audited native foot subtree.
        // Native setupAnim owns all transforms/visibility; this adapter writes none of them.
        return new Model<S>(resolved.root().getChild(name), resolved.renderType()) {
            @Override
            public void setupAnim(S state) {
                resolved.setupAnim(state);
            }
        };
    }

    private static boolean isRootsModel(Model<?> model) {
        return switch (model.getClass().getName()) {
            case "elucent.rootsclassic.client.model.SylvanArmorModel",
                 "elucent.rootsclassic.client.model.WildwoodArmorModel" -> true;
            default -> false;
        };
    }

    static String assertRoute(HumanoidModel<HumanoidRenderState> original) {
        ItemStack sylvan = boot("sylvan_boots");
        ItemStack wildwood = boot("wildwood_boots");
        ItemStack vanilla = source(Items.IRON_BOOTS, EquipmentAssets.IRON);
        List<ItemStack> roots = List.of(sylvan, wildwood);
        for (ItemStack boot : roots) {
            HumanoidModel<?> nativeModel = resolve(boot, original);
            if (!isRootsModel(nativeModel) || !nativeModel.root().hasChild("left_foot")
                    || !nativeModel.root().hasChild("right_foot")) {
                throw new AssertionError("Roots model topology changed");
            }
            // Demonstrate the actual production defect before enabling the prototype.
            var baseline = renderPair(mixed(boot, vanilla), original, 0.3F);
            boolean foundHiddenLeg = false;
            for (var node : baseline) {
                if (node.submit().model().root() == nativeModel.leftLeg) {
                    animate(node);
                    if (!vertices(node.submit().model().root()).isEmpty()) {
                        throw new AssertionError("Expected production Roots leg to contain no visible boot geometry");
                    }
                    foundHiddenLeg = true;
                }
            }
            if (!foundHiddenLeg || vertices(nativeModel.root().getChild("left_foot")).isEmpty()) {
                throw new AssertionError("Production defect control did not distinguish hidden leg from native foot");
            }
        }
        candidate = true;
        try {
            for (ItemStack boot : roots) {
                assertPair(boot, vanilla, original);
                assertPair(vanilla, boot, original);
                assertPair(boot, boot, original);
            }
            assertPair(sylvan, wildwood, original);
            assertPair(wildwood, sylvan, original);
            // Ordinary native models must not become split views merely because they are Roots.
            for (ItemStack boot : roots) {
                assertOrdinary(resolve(boot, original));
            }
            NeoForgeEquipmentRenderingClientTest.assertStandardRoute();
            if (Boolean.getBoolean("mismatchedboots.runtimetest.iceAndFireTest")) {
                NeoForgeNativeEquipmentClientTest.assertIceAndFire(original);
            }
        } finally {
            candidate = false;
        }
        return "equipment.roots.productionHiddenLegReproduced=true\n"
                + "equipment.roots.candidateOrderedPairs=8\n"
                + "equipment.roots.nativeFootVerticesAndAnimation=true\n"
                + "equipment.roots.nativeTextureTrimFoil=true\n"
                + "equipment.roots.productionChanged=false\n";
    }

    private static ItemStack boot(String path) {
        ItemStack boot = source(BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath("rootsclassic", path)), EquipmentAssets.IRON);
        boot.set(DataComponents.TRIM, new ArmorTrim(
                Holder.direct(new TrimMaterial(MaterialAssetGroup.IRON, Component.literal("iron"))),
                Holder.direct(new TrimPattern(Identifier.withDefaultNamespace("sentry"),
                        Component.literal("sentry"), false))));
        return boot;
    }

    private static <S> void assertOrdinary(Model<S> model) {
        if (adapt(model, model) != model) {
            throw new AssertionError("Candidate changed ordinary Roots equipment");
        }
    }

    private static HumanoidModel<?> resolve(ItemStack boot, HumanoidModel<?> original) {
        return (HumanoidModel<?>) IClientItemExtensions.of(boot).getGenericArmorModel(
                boot, EquipmentClientInfo.LayerType.HUMANOID, original);
    }

    private static void assertPair(ItemStack left, ItemStack right, HumanoidModel<HumanoidRenderState> original) {
        ItemStack leftBefore = left.copy();
        ItemStack rightBefore = right.copy();
        ItemStack pair = mixed(left, right);
        pair.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        HumanoidModel<?> leftModel = resolve(left, original);
        HumanoidModel<?> rightModel = resolve(right, original);
        ModelPart leftPart = part(leftModel, true);
        ModelPart rightPart = part(rightModel, false);
        var first = renderPair(pair, original, 0.3F);
        var second = renderPair(pair, original, 1.4F);
        List<Float> firstGeometry = assertNodes(first, leftPart, rightPart, leftModel, rightModel);
        List<Float> secondGeometry = assertNodes(second, leftPart, rightPart, leftModel, rightModel);
        if (firstGeometry.equals(secondGeometry)
                || !firstGeometry.equals(assertNodes(first, leftPart, rightPart, leftModel, rightModel))) {
            throw new AssertionError("Queued cached Roots models lost animation or per-state reproducibility");
        }
        var stored = ModContent.getMixedBootsData(pair).orElseThrow();
        if (!ItemStack.matches(left, leftBefore) || !ItemStack.matches(right, rightBefore)
                || !ItemStack.matches(stored.left().source(), leftBefore)
                || !ItemStack.matches(stored.right().source(), rightBefore)) {
            throw new AssertionError("Roots rendering changed source snapshots");
        }
    }

    private static ModelPart part(HumanoidModel<?> model, boolean left) {
        return isRootsModel(model) ? model.root().getChild(left ? "left_foot" : "right_foot")
                : left ? model.leftLeg : model.rightLeg;
    }

    private static List<Float> assertNodes(List<NeoForgeNativeEquipmentClientTest.Node> nodes,
                                          ModelPart left, ModelPart right,
                                          HumanoidModel<?> leftModel, HumanoidModel<?> rightModel) {
        boolean foundLeft = false;
        boolean foundRight = false;
        int trims = 0;
        int glints = 0;
        List<Float> geometry = new ArrayList<>();
        for (var node : nodes) {
            Model<?> view = node.submit().model();
            boolean isLeft = view.root() == left;
            if (!isLeft && view.root() != right) {
                throw new AssertionError("Candidate lost side ownership");
            }
            HumanoidModel<?> nativeModel = isLeft ? leftModel : rightModel;
            ModelPart opposite = part(nativeModel, !isLeft);
            if (view.allParts().contains(opposite)) {
                throw new AssertionError("Candidate contains opposite native foot");
            }
            animate(node);
            List<Float> actual = vertices(view.root());
            if (actual.isEmpty()) {
                throw new AssertionError("Candidate has no visible geometry");
            }
            // Compare to the native selected foot after the SAME native pose calculation.
            setup(nativeModel, (HumanoidRenderState) node.submit().state());
            if (!actual.equals(vertices(isLeft ? left : right))) {
                throw new AssertionError("Candidate geometry diverges from native foot animation");
            }
            if (isRootsModel(nativeModel)) {
                if (!nativeModel.root().getChild("left_foot").visible
                        || !nativeModel.root().getChild("right_foot").visible
                        || nativeModel.leftLeg.visible || nativeModel.rightLeg.visible) {
                    throw new AssertionError("Candidate contaminated native cached visibility");
                }
                Identifier texture = Identifier.fromNamespaceAndPath("rootsclassic", "textures/models/armor/"
                        + (nativeModel.getClass().getSimpleName().startsWith("Sylvan") ? "sylvan" : "wildwood") + ".png");
                if (node.submit().sprite() == null && node.type() != RenderTypes.armorEntityGlint()
                        && node.type() != RenderTypes.armorCutoutNoCull(texture)) {
                    throw new AssertionError("Candidate lost native Roots armor texture");
                }
            }
            trims += node.submit().sprite() != null ? 1 : 0;
            glints += node.type() == RenderTypes.armorEntityGlint() ? 1 : 0;
            foundLeft |= isLeft;
            foundRight |= !isLeft;
            geometry.addAll(actual);
        }
        int expectedTrims = (isRootsModel(leftModel) ? 1 : 0) + (isRootsModel(rightModel) ? 1 : 0);
        if (!foundLeft || !foundRight || trims != expectedTrims || glints != 2) {
            throw new AssertionError("Candidate lost a side, trim, or root glint");
        }
        return geometry;
    }

    private static void animate(NeoForgeNativeEquipmentClientTest.Node node) {
        HumanoidRenderState state = (HumanoidRenderState) node.submit().state();
        state.walkAnimationSpeed = 0.7F;
        state.isCrouching = state.walkAnimationPos > 1.0F;
        setup(node.submit().model(), state);
    }

    @SuppressWarnings("unchecked")
    private static void setup(Model<?> model, HumanoidRenderState state) {
        ((Model<HumanoidRenderState>) model).setupAnim(state);
    }

    private static List<Float> vertices(ModelPart part) {
        Vertices buffer = new Vertices();
        part.render(new PoseStack(), buffer, 0x00F000F0, 0);
        return buffer.positions;
    }

    private static final class Vertices implements VertexConsumer {
        private final List<Float> positions = new ArrayList<>();

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            positions.add(x); positions.add(y); positions.add(z); return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setColor(int color) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
}
