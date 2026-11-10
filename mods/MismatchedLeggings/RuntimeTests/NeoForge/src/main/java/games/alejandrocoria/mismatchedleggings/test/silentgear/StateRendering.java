package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsItemRenderer;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import games.alejandrocoria.mismatchedleggings.test.render.ItemChecks;
import games.alejandrocoria.mismatchedleggings.test.render.RecordingSubmits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.stream.Collectors;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Only ordinary native references; no copied textures/models or alternate render path. */
final class StateRendering {
    static String verify(Minecraft client, List<ItemStack> roots) throws Exception {
        require(EquippedClient.renderer != null, "Native renderer not observed");
        var model = new HumanoidModel<HumanoidRenderState>(client.getEntityModels().bakeLayer(ModelLayers.ZOMBIE_ARMOR.legs()));
        model.root().getAllParts().forEach(part -> part.visible = false);
        model.root().visible = true;
        model.body.visible = true;
        model.leftLeg.visible = true;
        model.rightLeg.visible = true;
        String family = "SG native source pair";
        for (var received : roots) {
            var root = received.copy();
            root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            var data = MixedLeggingsStructure.validData(root).orElseThrow();
            var prepared = MixedLeggingsItemRenderer.prepareGui(data, true, client.getItemModelResolver(), client.level, client.player, 0);
            require(prepared.clipped() && prepared.compose().sources().size() == 2, "Ordinary item fallback " + family);
            for (var context : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)) {
                var faces = ItemChecks.faces(ItemChecks.resolve(root, context));
                require(!faces.isEmpty() && faces.stream().allMatch(ItemChecks.Face::foil), "Item glint missing");
                var sources = List.of(data.left().source(), data.right().source());
                var texturesBySide = sources.stream().map(source -> ItemChecks.faces(ItemChecks.resolve(source, context))
                        .stream().map(ItemChecks.Face::texture).collect(Collectors.toSet())).toList();
                for (int side = 0; side < 2; side++) {
                    var textures = texturesBySide.get(side);
                    var otherTextures = texturesBySide.get(1 - side);
                    var selected = faces.stream().filter(face -> textures.contains(face.texture())).toList();
                    require(!selected.isEmpty(), "Missing native texture " + family);
                    final boolean left = side == 0;
                    require(selected.stream().anyMatch(face -> face.vertices().stream()
                            .allMatch(vertex -> left ? vertex.x >= .4999F : vertex.x <= .5001F)), "Missing native source half");
                    for (var face : selected) {
                        // Native SG sources can share the same atlas texture. Geometry still must be clipped,
                        // but that shared texture alone cannot identify which source produced a face.
                        require(face.vertices().stream().allMatch(vertex -> vertex.x >= .4999F)
                                || face.vertices().stream().allMatch(vertex -> vertex.x <= .5001F), "Unclipped source face");
                        if (!otherTextures.contains(face.texture())) {
                            require(face.vertices().stream().allMatch(vertex -> left ? vertex.x >= .4999F : vertex.x <= .5001F), "Wrong source side");
                        }
                    }
                }
            }
            for (boolean crouched : List.of(false, true)) {
                var state = new HumanoidRenderState();
                state.entityType = EntityType.ZOMBIE;
                state.isCrouching = crouched;
                state.walkAnimationPos = 1.2F;
                state.walkAnimationSpeed = .8F;
                var actual = new RecordingSubmits();
                MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, model, state,
                        EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, root, new PoseStack(), actual, 15728880, 0);
                require(!actual.nodes.isEmpty() && actual.nodes.stream().allMatch(node -> node.model() instanceof LeggingsView<?>), "Equipment fallback " + family);
                int offset = 0;
                int selectedSide = 0;
                for (var history : List.of(data.left().source(), data.right().source())) {
                    history.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, root.hasFoil());
                    var nativeOutput = new RecordingSubmits();
                    EquippedClient.renderer.renderLayers(EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                            history.get(DataComponents.EQUIPPABLE).assetId().orElseThrow(), model, state, history,
                            new PoseStack(), nativeOutput, 15728880, 0);
                    require(!nativeOutput.nodes.isEmpty(), "Missing native reference");
                    for (var node : nativeOutput.nodes) {
                        var result = actual.nodes.get(offset++);
                        require(node.type().equals(result.type()) && node.color() == result.color() && node.sprite() == result.sprite(),
                                "Native material/trim lost: " + family + " expected=" + node.type() + " actual=" + result.type());
                        var view = (LeggingsView<?>) result.model();
                        require(view.root().getChild("leg") == (selectedSide == 0 ? model.leftLeg : model.rightLeg),
                                "Wrong native moving leg selected: " + family);
                        require(view.root().getChild("waist").getAllParts().stream().anyMatch(part -> !part.isEmpty()),
                                "Clipped native waist missing: " + family);
                    }
                    selectedSide++;
                }
                require(offset == actual.nodes.size(), "Unexpected source layer");
            }
        }
        return "SG leggings: both item orientations GUI/hand, native materials/trims/glint and moving/crouched split-model submissions passed (not pixel/manual certification)\n";
    }
}
