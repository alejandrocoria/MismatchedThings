package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import games.alejandrocoria.mismatchedboots.client.render.RootsEquipmentRendering;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleState;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
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
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.MaterialAssetGroup;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.NeoForgeNativeEquipmentClientTest.renderPair;

/** Production splitting with real default Roots equipment in a disposable integrated world. */
public final class RootsEquipmentClientTest {
    private static boolean worldRequested;

    private RootsEquipmentClientTest() {
    }

    static boolean worldReady(Minecraft client) {
        if (!worldRequested) {
            worldRequested = true;
            client.options.pauseOnLostFocus = false;
            client.createWorldOpenFlows().createFreshLevel("roots-rendering",
                                                           new LevelSettings("Roots rendering",
                                                                             GameType.CREATIVE,
                                                                             LevelSettings.DifficultySettings.DEFAULT,
                                                                             true,
                                                                             WorldDataConfiguration.DEFAULT),
                                                           new WorldOptions(123L, false, false),
                                                           WorldPresets::createFlatWorldDimensions,
                                                           new TitleScreen());
        }
        return client.player != null && client.level != null && CompositionRulesClientLifecycle.areRulesSynchronized();
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
        ItemStack vanilla = new ItemStack(Items.IRON_BOOTS);
        List<ItemStack> roots = List.of(sylvan, wildwood);
        for (ItemStack boot : roots) {
            HumanoidModel<?> nativeModel = resolve(boot, original);
            if (!isRootsModel(nativeModel) || !nativeModel.root().hasChild("left_foot")
                    || !nativeModel.root().hasChild("right_foot")) {
                throw new AssertionError("Roots model topology changed");
            }
            assertPair(boot, vanilla, original);
            assertPair(vanilla, boot, original);
            assertPair(boot, boot, original);
            assertOrdinary(nativeModel);
        }
        assertPair(sylvan, wildwood, original);
        assertPair(wildwood, sylvan, original);
        assertUnsupported(resolve(sylvan, original), original);
        return "equipment.roots.productionOrderedPairs=8\n"
                + "equipment.roots.worldDefaultAssets=true\n"
                + "equipment.roots.nativeFootVerticesAndAnimation=true\n"
                + "equipment.roots.nativeTextureTrimFoil=true\n"
                + "equipment.roots.unsupportedTopologyAndUnmarked=true\n";
    }

    private static ItemStack boot(String path) {
        ItemStack boot = new ItemStack(BuiltInRegistries.ITEM.getValue(
                Identifier.fromNamespaceAndPath("rootsclassic", path)));
        var asset = boot.get(DataComponents.EQUIPPABLE).assetId().orElseThrow().identifier();
        if (!asset.getNamespace().equals("rootsclassic")) {
            throw new AssertionError("Roots default equipment asset missing: " + asset);
        }
        boot.set(DataComponents.TRIM, new ArmorTrim(
                Holder.direct(new TrimMaterial(MaterialAssetGroup.IRON, Component.literal("iron"))),
                Holder.direct(new TrimPattern(Identifier.withDefaultNamespace("sentry"),
                                              Component.literal("sentry"), false))));
        return boot;
    }

    private static <S> void assertOrdinary(Model<S> model) {
        if (RootsEquipmentRendering.preserveSplit(model, model) != model) {
            throw new AssertionError("Adapter changed ordinary Roots equipment");
        }
    }

    private static HumanoidModel<?> resolve(ItemStack boot, HumanoidModel<?> original) {
        return (HumanoidModel<?>) IClientItemExtensions.of(boot).getGenericArmorModel(
                boot, EquipmentClientInfo.LayerType.HUMANOID, original);
    }

    private static void assertUnsupported(HumanoidModel<?> nativeModel, HumanoidModel<HumanoidRenderState> original) {
        try {
            // Fresh, uncached native model: remove a named child without touching the real extension's cache.
            LayerDefinition layer = (LayerDefinition) nativeModel.getClass().getMethod("createArmorDefinition").invoke(null);
            ModelPart baked = layer.bakeRoot();
            Map<String, ModelPart> children = new HashMap<>();
            for (String name : List.of("head", "body", "left_arm", "right_arm", "left_leg", "right_leg",
                                       "left_foot", "right_foot")) {
                children.put(name, baked.getChild(name));
            }
            HumanoidModel<?> malformed = (HumanoidModel<?>) nativeModel.getClass()
                                                                       .getConstructor(ModelPart.class, ArmorType.class)
                                                                       .newInstance(new ModelPart(List.of(), children), ArmorType.BOOTS);
            var pair = MixedBootsFactory.createPair(new ItemStack(Items.IRON_BOOTS),
                                                    new ItemStack(Items.GOLDEN_BOOTS), AttributeRuleState.current(), ComponentRuleState.current())
                                        .primary();
            var marked = renderPair(pair, original, 0.3F).stream()
                                                         .filter(node -> node.submit().model().root() == original.leftLeg)
                                                         .findFirst().orElseThrow().submit().model();
            assertFallback(marked, malformed, children, "left_foot");
            assertFallback(marked, malformed, children, "right_foot");
            // A humanoid with similarly named parts but an unknown class must still use legs.
            HumanoidModel<HumanoidRenderState> unknown = new HumanoidModel<>(new ModelPart(List.of(), children));
            if (select(marked, unknown).root() != unknown.leftLeg
                    || select(original, nativeModel) != nativeModel) {
                throw new AssertionError("Unknown topology or unmarked native model was adapted");
            }
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Could not construct the bounded unsupported Roots topology control", failure);
        }
    }

    private static void assertFallback(Model<?> marked, HumanoidModel<?> malformed,
                                       Map<String, ModelPart> children, String missing) {
        ModelPart foot = children.remove(missing);
        try {
            if (select(marked, malformed).root() != malformed.leftLeg) {
                throw new AssertionError("Incomplete Roots topology did not retain the normal leg fallback");
            }
        } finally {
            children.put(missing, foot);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Model<?> select(Model<?> original, Model<?> resolved) {
        return RootsEquipmentRendering.preserveSplit((Model) original, (Model) resolved);
    }

    private static void assertPair(ItemStack left, ItemStack right, HumanoidModel<HumanoidRenderState> original) {
        ItemStack leftBefore = left.copy();
        ItemStack rightBefore = right.copy();
        ItemStack pair = MixedBootsFactory.createPair(left, right,
                                                      AttributeRuleState.current(), ComponentRuleState.current()).primary();
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
                throw new AssertionError("Production adapter lost side ownership");
            }
            HumanoidModel<?> nativeModel = isLeft ? leftModel : rightModel;
            ModelPart opposite = part(nativeModel, !isLeft);
            if (view.allParts().contains(opposite)) {
                throw new AssertionError("Production adapter contains opposite native foot");
            }
            animate(node);
            List<Float> actual = vertices(view.root());
            if (actual.isEmpty()) {
                throw new AssertionError("Production adapter has no visible geometry");
            }
            // Compare to the native selected foot after the SAME native pose calculation.
            setup(nativeModel, (HumanoidRenderState) node.submit().state());
            if (!actual.equals(vertices(isLeft ? left : right))) {
                throw new AssertionError("Production adapter geometry diverges from native foot animation");
            }
            if (isRootsModel(nativeModel)) {
                if (!nativeModel.root().getChild("left_foot").visible
                        || !nativeModel.root().getChild("right_foot").visible
                        || nativeModel.leftLeg.visible || nativeModel.rightLeg.visible) {
                    throw new AssertionError("Production adapter contaminated native cached visibility");
                }
                Identifier texture = Identifier.fromNamespaceAndPath("rootsclassic", "textures/models/armor/"
                        + (nativeModel.getClass().getSimpleName().startsWith("Sylvan") ? "sylvan" : "wildwood") + ".png");
                if (node.submit().sprite() == null && node.type() != RenderTypes.armorEntityGlint()
                        && node.type() != RenderTypes.armorCutoutNoCull(texture)) {
                    throw new AssertionError("Production adapter lost native Roots armor texture");
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
            throw new AssertionError("Production adapter lost a side, trim, or root glint");
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

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            positions.add(x);
            positions.add(y);
            positions.add(z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }
}
