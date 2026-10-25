package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedboots.client.render.MixedBootsEquipmentRenderer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.neoforge.mixin.ModelSubmitStorageAccessor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.MaterialAssetGroup;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.NeoForgeEquipmentRenderingClientTest.mixed;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.NeoForgeEquipmentRenderingClientTest.model;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.NeoForgeEquipmentRenderingClientTest.source;

/** Runs through the native NeoForge replacement hook and inspects its actual deferred nodes. */
public final class NeoForgeNativeEquipmentClientTest {
    private static final int LIGHT = 0x00F000F0;
    private static final int OUTLINE = 0x11223344;
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "mismatchedboots_runtime_test", "textures/custom_armor.png");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("mismatchedboots_runtime_test");
    private static final DeferredItem<Item> LEFT = ITEMS.registerSimpleItem("custom_left");
    private static final DeferredItem<Item> RIGHT = ITEMS.registerSimpleItem("custom_right");
    private static final DeferredItem<Item> DEFAULT = ITEMS.registerSimpleItem("default_armor");
    private static final DeferredItem<Item> GENERIC = ITEMS.registerSimpleItem("generic_armor");
    private static final Extension LEFT_EXTENSION = new Extension();
    private static final Extension RIGHT_EXTENSION = new Extension();
    private static final Extension DEFAULT_EXTENSION = new Extension();
    private static final Extension GENERIC_EXTENSION = new Extension();
    private static EquipmentLayerRenderer renderer;

    private NeoForgeNativeEquipmentClientTest() {
    }

    public static void registerFixtures(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }

    public static void registerExtensions(RegisterClientExtensionsEvent event) {
        // Test-only items isolate the test: no vanilla/third-party extension is replaced.
        event.registerItem(LEFT_EXTENSION, LEFT);
        event.registerItem(RIGHT_EXTENSION, RIGHT);
        event.registerItem(DEFAULT_EXTENSION, DEFAULT);
        event.registerItem(GENERIC_EXTENSION, GENERIC);
    }

    public static void captureRenderer(EntityRenderersEvent.AddLayers event) {
        renderer = event.getContext().getEquipmentRenderer();
    }

    public static String assertNativeRoute() {
        if (renderer == null) {
            throw new AssertionError("Native equipment renderer was not captured after reload");
        }
        HumanoidModel<HumanoidRenderState> original = model();
        AnimatedModel left = new AnimatedModel();
        AnimatedModel right = new AnimatedModel();
        LEFT_EXTENSION.replacement = left;
        RIGHT_EXTENSION.replacement = right;
        ItemStack leftStack = source(LEFT.get(), EquipmentAssets.LEATHER);
        leftStack.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3366CC));
        leftStack.set(DataComponents.TRIM, new ArmorTrim(
                Holder.direct(new TrimMaterial(MaterialAssetGroup.IRON, Component.literal("iron"))),
                Holder.direct(new TrimPattern(Identifier.withDefaultNamespace("sentry"),
                                              Component.literal("sentry"), false))));
        ItemStack rightStack = source(RIGHT.get(), EquipmentAssets.IRON);
        ItemStack pair = mixed(leftStack, rightStack);
        ItemStack leftSnapshot = ModContent.getMixedBootsData(pair).orElseThrow().left().source().copy();
        ItemStack rightSnapshot = ModContent.getMixedBootsData(pair).orElseThrow().right().source().copy();
        pair.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        List<Node> nodes = renderPair(pair, original);
        assertRoots(nodes, left.leftLeg, right.rightLeg, left.rightLeg, right.leftLeg);
        assertResolutions(LEFT_EXTENSION, 1);
        assertResolutions(RIGHT_EXTENSION, 1);
        if (nodes.size() != 6 || nodes.stream().filter(n -> n.type() == RenderTypes.armorEntityGlint()).count() != 2
                || nodes.stream().noneMatch(n -> n.submit().tintedColor() == 0xFF3366CC)
                || nodes.stream().filter(n -> n.type() == Sheets.armorTrimsSheet(false)
                && n.submit().sprite() != null && n.submit().model().root() == left.leftLeg).count() != 1
                || nodes.stream().filter(n -> n.type() == RenderTypes.armorCutoutNoCull(TEXTURE)).count() != 3) {
            throw new AssertionError("Native custom armor lost leather layers, dye, trim, root foil, or extension texture: "
                                             + nodes.stream().map(n -> n.type() + "/" + Integer.toHexString(n.submit().tintedColor())
                    + "/trim=" + (n.submit().sprite() != null)).toList());
        }
        assertAnimation(nodes, left, right);
        if (!ItemStack.matches(ModContent.getMixedBootsData(pair).orElseThrow().left().source(), leftSnapshot)
                || !ItemStack.matches(ModContent.getMixedBootsData(pair).orElseThrow().right().source(), rightSnapshot)) {
            throw new AssertionError("Native rendering mutated source snapshots");
        }

        // Cached model used by BOTH sides: do not hide a shared leg or change queued ownership.
        RIGHT_EXTENSION.replacement = left;
        List<Node> shared = renderPair(pair, original);
        List<Node> nextEntity = renderPair(pair, original, 1.3F);
        assertRoots(shared, left.leftLeg, left.rightLeg, left.rightLeg, left.leftLeg);
        assertRoots(nextEntity, left.leftLeg, left.rightLeg, left.rightLeg, left.leftLeg);
        assertResolutions(LEFT_EXTENSION, 2);
        assertResolutions(RIGHT_EXTENSION, 2);
        assertSharedAnimation(shared, left, 0.7F);
        assertSharedAnimation(nextEntity, left, 1.3F);
        RIGHT_EXTENSION.replacement = right;
        assertRoots(renderPair(mixed(rightStack, leftStack), original),
                    right.leftLeg, left.rightLeg, right.rightLeg, left.leftLeg);
        assertResolutions(LEFT_EXTENSION, 1);
        assertResolutions(RIGHT_EXTENSION, 1);
        assertRoots(renderPair(mixed(source(DEFAULT.get(), EquipmentAssets.IRON),
                                     source(DEFAULT.get(), EquipmentAssets.GOLD)), original),
                    original.leftLeg, original.rightLeg, original.rightLeg, original.leftLeg);
        assertResolutions(DEFAULT_EXTENSION, 2);
        Model<HumanoidRenderState> generic = new Model<>(model().root(), RenderTypes::armorCutoutNoCull) {
            @Override
            public void setupAnim(HumanoidRenderState state) {
            }
        };
        GENERIC_EXTENSION.replacement = generic;
        List<Node> genericNodes = renderPair(mixed(source(GENERIC.get(), EquipmentAssets.IRON),
                                                   source(GENERIC.get(), EquipmentAssets.GOLD)), original);
        if (genericNodes.size() != 2 || genericNodes.stream().anyMatch(n -> n.submit().model() != generic)) {
            throw new AssertionError("Non-humanoid extension was altered");
        }
        assertResolutions(GENERIC_EXTENSION, 2);
        SubmitNodeStorage ordinary = new SubmitNodeStorage();
        renderer.renderLayers(EquipmentClientInfo.LayerType.HUMANOID, EquipmentAssets.IRON,
                              original, new HumanoidRenderState(), source(LEFT.get(), EquipmentAssets.IRON),
                              new PoseStack(), ordinary, LIGHT, OUTLINE);
        if (nodes(ordinary).getFirst().submit().model() != left) {
            throw new AssertionError("Ordinary equipment replacement was split");
        }
        assertResolutions(LEFT_EXTENSION, 1);
        String report = "equipment.nativeReplacement=true\nequipment.sharedModel=true\n"
                + "equipment.deferredAnimation=true\nequipment.nativeDecorations=true\n"
                + "equipment.ordinaryAndGenericUnchanged=true\n";
        if (Boolean.getBoolean("mismatchedboots.runtimetest.iceAndFireTest")) {
            report += assertIceAndFire(original);
        }
        if (Boolean.getBoolean("mismatchedboots.runtimetest.rootsRenderingTest")) {
            report += RootsEquipmentClientTest.assertRoute(original);
        }
        return report;
    }

    static String assertIceAndFire(HumanoidModel<HumanoidRenderState> original) {
        int custom = 0;
        int ordinary = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals("iceandfire") || !id.getPath().endsWith("_boots")) {
                continue;
            }
            // Before world startup, registry defaults are unbound. A controlled native asset
            // exercises the real item's model/texture extension, not its gameplay/default assets.
            ItemStack stack = source(item, EquipmentAssets.IRON);
            Model<?> resolved = IClientItemExtensions.of(stack).getGenericArmorModel(
                    stack, EquipmentClientInfo.LayerType.HUMANOID, original);
            if (!(resolved instanceof HumanoidModel<?> humanoid)) {
                throw new AssertionError(id + " did not resolve humanoid equipment");
            }
            ItemStack vanilla = source(Items.IRON_BOOTS, EquipmentAssets.IRON);
            assertRoots(renderPair(mixed(stack, vanilla), original),
                        humanoid.leftLeg, original.rightLeg, humanoid.rightLeg, original.leftLeg);
            assertRoots(renderPair(mixed(vanilla, stack), original),
                        original.leftLeg, humanoid.rightLeg, original.rightLeg, humanoid.leftLeg);
            assertRoots(renderPair(mixed(stack, stack), original),
                        humanoid.leftLeg, humanoid.rightLeg, humanoid.rightLeg, humanoid.leftLeg);
            if (resolved == original) {
                ordinary++;
            } else {
                custom++;
            }
        }
        if (custom != 30 || ordinary != 1) {
            throw new AssertionError("Ice and Fire coverage changed: custom=" + custom + ", ordinary=" + ordinary);
        }
        return "equipment.iceAndFire.customBoots=30\nequipment.iceAndFire.ordinaryBoots=1\n"
                + "equipment.iceAndFire.orderedPairs=93\n";
    }

    private static List<Node> renderPair(ItemStack stack, HumanoidModel<HumanoidRenderState> original) {
        return renderPair(stack, original, 0.7F);
    }

    static List<Node> renderPair(ItemStack stack, HumanoidModel<HumanoidRenderState> original, float phase) {
        HumanoidRenderState state = new HumanoidRenderState();
        state.walkAnimationPos = phase;
        SubmitNodeStorage storage = new SubmitNodeStorage();
        if (!MixedBootsEquipmentRenderer.renderLayers(renderer, EquipmentClientInfo.LayerType.HUMANOID,
                                                      original, state, stack, new PoseStack(), storage, LIGHT, OUTLINE)) {
            throw new AssertionError("Native fixture declined the split route");
        }
        List<Node> nodes = nodes(storage);
        if (nodes.isEmpty()) {
            throw new AssertionError("Native fixture submitted no equipment nodes");
        }
        for (Node node : nodes) {
            if (node.submit().lightCoords() != LIGHT || node.submit().outlineColor() != OUTLINE
                    || node.submit().overlayCoords() != OverlayTexture.NO_OVERLAY || node.submit().state() != state) {
                throw new AssertionError("Native equipment submission parameters changed");
            }
        }
        return nodes;
    }

    private static List<Node> nodes(SubmitNodeStorage storage) {
        List<Node> result = new ArrayList<>();
        storage.getSubmitsPerOrder().values().forEach(collection -> {
            ModelSubmitStorageAccessor accessor = (ModelSubmitStorageAccessor) collection.getModelSubmits();
            Map<RenderType, List<SubmitNodeStorage.ModelSubmit<?>>> buckets =
                    accessor.mismatchedboots$getSolidModelSubmits();
            buckets.forEach((type, submissions) -> submissions.forEach(submit -> result.add(new Node(type, submit))));
            accessor.mismatchedboots$getTranslucentModelSubmits().forEach(
                    submit -> result.add(new Node(submit.renderType(), submit.modelSubmit())));
        });
        return result;
    }

    private static void assertRoots(List<Node> nodes, ModelPart left, ModelPart right,
                                    ModelPart excludedLeft, ModelPart excludedRight) {
        boolean foundLeft = false;
        boolean foundRight = false;
        for (Node node : nodes) {
            Model<?> model = node.submit().model();
            boolean isLeft = model.root() == left;
            boolean isRight = model.root() == right;
            if ((!isLeft && !isRight) || model.allParts().contains(isLeft ? excludedLeft : excludedRight)) {
                throw new AssertionError("Resolved equipment node contains the full/opposite model");
            }
            foundLeft |= isLeft;
            foundRight |= isRight;
        }
        if (!foundLeft || !foundRight) {
            throw new AssertionError("Resolved equipment lost a source side");
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertSharedAnimation(List<Node> nodes, AnimatedModel shared, float phase) {
        for (Node node : nodes) {
            ((Model<HumanoidRenderState>) node.submit().model()).setupAnim((HumanoidRenderState) node.submit().state());
            if (shared.leftLeg.xRot != phase || shared.rightLeg.xRot != -phase
                    || !shared.leftLeg.visible || !shared.rightLeg.visible) {
                throw new AssertionError("Queued entities sharing an armor model lost animation/visibility isolation");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void assertAnimation(List<Node> nodes, AnimatedModel left, AnimatedModel right) {
        for (Node node : nodes) {
            ((Model<HumanoidRenderState>) node.submit().model()).setupAnim((HumanoidRenderState) node.submit().state());
        }
        if (left.animations != 4 || right.animations != 2 || left.leftLeg.xRot != 0.7F
                || right.rightLeg.xRot != -0.7F || !left.leftLeg.visible || !left.rightLeg.visible
                || !right.leftLeg.visible || !right.rightLeg.visible) {
            throw new AssertionError("Deferred side model did not delegate custom animation or changed visibility");
        }
    }

    private static void assertResolutions(Extension extension, int expected) {
        if (extension.resolutions != expected) {
            throw new AssertionError("Armor extension resolved " + extension.resolutions + " times; expected " + expected);
        }
        extension.resolutions = 0;
    }

    private static final class AnimatedModel extends HumanoidModel<HumanoidRenderState> {
        private int animations;

        private AnimatedModel() {
            super(model().root());
        }

        @Override
        public void setupAnim(HumanoidRenderState state) {
            animations++;
            leftLeg.xRot = state.walkAnimationPos;
            rightLeg.xRot = -state.walkAnimationPos;
        }
    }

    private static final class Extension implements IClientItemExtensions {
        private Model<?> replacement;
        private int resolutions;

        @Override
        public Model<?> getHumanoidArmorModel(ItemStack stack, EquipmentClientInfo.LayerType type, Model original) {
            resolutions++;
            return replacement == null ? original : replacement;
        }

        @Override
        public Identifier getArmorTexture(ItemStack stack, EquipmentClientInfo.LayerType type,
                                          EquipmentClientInfo.Layer layer, Identifier original) {
            return TEXTURE;
        }
    }

    record Node(RenderType type, SubmitNodeStorage.ModelSubmit<?> submit) {
    }
}
