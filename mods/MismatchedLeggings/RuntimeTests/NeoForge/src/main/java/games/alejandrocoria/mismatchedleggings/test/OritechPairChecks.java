package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.NeoForgeEquipmentSources;
import games.alejandrocoria.mismatchedleggings.client.compatibility.oritech.OritechEquipmentCapture;
import games.alejandrocoria.mismatchedleggings.client.compatibility.oritech.OritechRenderState;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import games.alejandrocoria.mismatchedleggings.test.render.RecordingSubmits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.armorstand.ArmorStandArmorModel;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real product dispatch and atomic fallbacks; no renderer interception or duplicate adapter. */
final class OritechPairChecks {
    private OritechPairChecks() {
    }

    static void verify(Minecraft client) {
        var source = OritechCaptureChecks.exo();
        var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.IRON_LEGGINGS), CompositionRules.defaults());
        var both = pair(source, source);
        var rig = new ArmorStandArmorModel(client.getEntityModels().bakeLayer(ModelLayers.ARMOR_STAND_ARMOR.legs()));
        for (boolean posed : List.of(false, true)) {
            for (var root : List.of(pair.primary(), pair.inverse(), both)) {
                var before = root.copy();
                var state = OritechCaptureChecks.state(client, OritechCaptureChecks.actor(client, root, posed));
                var output = render(root, state, rig);
                require(output.custom.nodes.size() == (root == both ? 2 : 1), "Production pair lost a native Exo pass");
                require(output.models.nodes.size() == (root == both ? 0 : 1), "Production pair lost/duplicated the ordinary half");
                var expected = OritechEquipmentCapture.capture(source, state, new PoseStack(), 0);
                require(expected.prepareSplit(root != pair.inverse()), "Expected native side was not prepared");
                if (root != both) {
                    require(OritechCaptureChecks.vertices(output.custom).equals(OritechCaptureChecks.vertices(OritechCaptureChecks.flush(expected))),
                            "Production dispatch selected the wrong Exo leg or changed native geometry");
                    var node = output.models.nodes.getFirst();
                    require(node.model() instanceof LeggingsView<?> && node.model().root().getChild("leg")
                                    == (root == pair.primary() ? rig.rightLeg : rig.leftLeg),
                            "Ordinary half lost its split leg/waist or changed sides");
                } else {
                    require(expected.prepareFull(), "Complete native reference failed");
                    require(OritechCaptureChecks.vertices(output.custom).equals(OritechCaptureChecks.vertices(OritechCaptureChecks.flush(expected))),
                            "Two Exo sources duplicated or lost a leg");
                }
                require(ItemStack.matches(before, root), "Production pair rendering mutated source/history");
            }
        }

        var unknown = new ItemStack(Items.GOLDEN_LEGGINGS);
        // A present-but-missing asset yields a non-null, empty standard capture. The left
        // Exo split is prepared first, so this proves nothing leaked before full-owner fallback.
        unknown.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS)
                .setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.parse("leggings_skeleton_test:unavailable"))).build());
        var ownerRoot = pair(source, unknown);
        var ownerState = OritechCaptureChecks.state(client, OritechCaptureChecks.actor(client, ownerRoot, true));
        var fullOwner = render(ownerRoot, ownerState, rig);
        var nativeOwner = OritechEquipmentCapture.capture(source, ownerState, new PoseStack(), 0);
        require(nativeOwner.prepareFull() && fullOwner.models.nodes.isEmpty()
                        && OritechCaptureChecks.vertices(fullOwner.custom).equals(OritechCaptureChecks.vertices(OritechCaptureChecks.flush(nativeOwner))),
                "Rejected right source leaked a split or failed to use complete native Exo owner");
        var unknownRoot = pair(unknown, source);
        var unknownState = OritechCaptureChecks.state(client, OritechCaptureChecks.actor(client, unknownRoot, false));
        neutral(render(unknownRoot, unknownState, rig), rig);

        var missingContext = OritechCaptureChecks.state(client, OritechCaptureChecks.actor(client, pair.primary(), false));
        ((OritechRenderState) missingContext).mismatchedleggings$exoFrame(null);
        neutral(render(pair.primary(), missingContext, rig), rig);
        var validContext = OritechCaptureChecks.state(client, OritechCaptureChecks.actor(client, pair.primary(), false));
        require(new NeoForgeEquipmentSources().captureCustom(source, null, validContext, false, new PoseStack(), 0, 0) == null,
                "Unknown request rig was accepted");
        require(render(pair.primary(), validContext, rig).custom.nodes.size() == 1, "A rejected request disabled valid Exo pairs");

        var ordinary = pair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.GOLDEN_LEGGINGS));
        var ordinaryState = OritechCaptureChecks.state(client, OritechCaptureChecks.actor(client, ordinary, false));
        var normal = render(ordinary, ordinaryState, rig);
        require(normal.custom.nodes.isEmpty() && normal.models.nodes.size() == 2
                        && normal.models.nodes.stream().allMatch(node -> node.model() instanceof LeggingsView<?>),
                "Standard pairs changed when enabling Exo dispatch");
    }

    static void failedEpoch(Minecraft client) {
        var rig = new ArmorStandArmorModel(client.getEntityModels().bakeLayer(ModelLayers.ARMOR_STAND_ARMOR.legs()));
        var source = OritechCaptureChecks.exo();
        var left = pair(source, new ItemStack(Items.GOLDEN_LEGGINGS));
        var right = pair(new ItemStack(Items.GOLDEN_LEGGINGS), source);
        for (int repeat = 0; repeat < 2; repeat++) {
            neutral(render(left, OritechCaptureChecks.state(client, OritechCaptureChecks.actor(client, left, false)), rig), rig);
            var full = render(right, OritechCaptureChecks.state(client, OritechCaptureChecks.actor(client, right, false)), rig);
            require(full.custom.nodes.isEmpty() && full.models.nodes.size() == 1 && full.models.nodes.getFirst().model() == rig
                            && full.models.nodes.getFirst().type() == material("gold"),
                    "Unusable right Exo did not retain the safe complete ordinary owner");
        }
    }

    private static void neutral(PairSubmits output, HumanoidModel<?> rig) {
        require(output.custom.nodes.isEmpty() && output.models.nodes.size() == 1
                        && output.models.nodes.getFirst().model() == rig && output.models.nodes.getFirst().type() == material("iron"),
                "Unsafe/absent owner did not atomically fall back to the neutral asset");
    }

    private static RenderType material(String name) {
        return RenderTypes.armorCutoutNoCull(Identifier.withDefaultNamespace("textures/entity/equipment/humanoid_leggings/" + name + ".png"));
    }

    private static ItemStack pair(ItemStack first, ItemStack second) {
        return MixedLeggingsFactory.createPair(first, second, CompositionRules.defaults()).primary();
    }

    private static PairSubmits render(ItemStack root, ArmorStandRenderState state, ArmorStandArmorModel rig) {
        var output = new PairSubmits();
        MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, rig, state, EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                root, new PoseStack(), output, 15728880, 0);
        return output;
    }

    static final class Scene extends Screen {
        private final List<ArmorStand> actors = new ArrayList<>();
        private final List<String> labels = List.of("Native Exo", "Exo / iron", "Iron / Exo", "Exo / Exo",
                "Native Exo posed", "Exo / iron posed", "Iron / Exo posed", "Exo / Exo posed");
        int frames;

        Scene(Minecraft client) {
            super(Component.literal("Production Oritech mixed leggings"));
            var source = OritechCaptureChecks.exo();
            var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.IRON_LEGGINGS), CompositionRules.defaults());
            var samples = List.of(source, pair.primary(), pair.inverse(), pair(source, source));
            for (int index = 0; index < 8; index++) {
                var actor = OritechCaptureChecks.actor(client, samples.get(index % 4), index >= 4);
                if (Boolean.getBoolean("leggings.test.boots")) {
                    actor.setItemSlot(EquipmentSlot.FEET, BootsCoexistence.mixed());
                }
                actors.add(actor);
            }
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF20252C);
            int cellWidth = width / 4;
            int cellHeight = height / 2;
            for (int index = 0; index < actors.size(); index++) {
                var state = OritechCaptureChecks.state(Minecraft.getInstance(), actors.get(index));
                int x = index % 4 * cellWidth;
                int y = index / 4 * cellHeight;
                graphics.text(font, labels.get(index), x + 5, y + 5, 0xFFFFFFFF, false);
                graphics.entity(state, 65, new Vector3f(0, 1.05F, 0), new Quaternionf().rotateZ((float) Math.PI),
                        new Quaternionf(), x + 4, y + 18, x + cellWidth - 4, y + cellHeight - 4);
            }
            frames++;
        }
    }

    private static final class PairSubmits extends SubmitNodeStorage {
        final OritechCaptureChecks.Geometry custom = new OritechCaptureChecks.Geometry();
        final RecordingSubmits models = new RecordingSubmits();

        @Override
        public SubmitNodeCollection order(int order) {
            var modelTarget = models.order(order);
            var geometryTarget = custom.order(order);
            return new SubmitNodeCollection(this) {
                @Override
                public void submitCustomGeometry(PoseStack pose, RenderType type, SubmitNodeCollector.CustomGeometryRenderer renderer) {
                    geometryTarget.submitCustomGeometry(pose, type, renderer);
                }

                @Override
                public <S> void submitModel(Model<? super S> model, S state, PoseStack pose, RenderType type, int light, int overlay,
                                              int color, TextureAtlasSprite sprite, int outline, ModelFeatureRenderer.CrumblingOverlay crumbling) {
                    modelTarget.submitModel(model, state, pose, type, light, overlay, color, sprite, outline, crumbling);
                }
            };
        }
    }
}
