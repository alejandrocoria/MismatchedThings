package games.alejandrocoria.mismatchedleggings.test.aether;

import com.aetherteam.aetherii.item.components.AetherIIDataComponents;
import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsItemRenderer;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPatterns;

import java.util.List;
import java.util.stream.Collectors;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native reference submissions, materials and vertices, independent from production clipping. */
public final class AetherRenderingChecks {
    private AetherRenderingChecks() {}

    public static String verify(Minecraft client) {
        require(EquippedClient.renderer != null, "Native equipment renderer was not captured");
        var model = new HumanoidModel<HumanoidRenderState>(client.getEntityModels().bakeLayer(ModelLayers.ZOMBIE_ARMOR.legs()));
        model.root().getAllParts().forEach(part -> part.visible = false);
        model.root().visible = true;
        model.body.visible = true;
        model.leftLeg.visible = true;
        model.rightLeg.visible = true;
        for (String family : AetherGameplayChecks.FAMILIES) {
            var source = AetherGameplayChecks.item(family + "_leggings");
            source.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3366CC));
            source.set(DataComponents.TRIM, new ArmorTrim(
                    client.level.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.GOLD),
                    client.level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.SENTRY)));
            var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.DIAMOND_LEGGINGS));
            var roots = List.of(pair.primary(), pair.inverse());
            for (var root : roots) {
                root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                var before = root.copy();
                for (boolean crouch : List.of(false, true)) {
                    var state = new HumanoidRenderState();
                    state.entityType = EntityType.ZOMBIE;
                    state.isCrouching = crouch;
                    state.walkAnimationPos = 1.2f;
                    state.walkAnimationSpeed = .8f;
                    var actual = new RecordingSubmits();
                    MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, model, state,
                            EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, root, new PoseStack(), actual, 15728880, 0);
                    require(!actual.nodes.isEmpty() && actual.nodes.stream().allMatch(node -> node.model() instanceof LeggingsView<?>),
                            "Native Aether pair fell back: " + family);
                    var data = MixedLeggingsStructure.validData(root).orElseThrow();
                    int offset = compareSource(model, state, data.left().source(), actual, 0, true);
                    require(compareSource(model, state, data.right().source(), actual, offset, false) == actual.nodes.size(),
                            "Extra or missing queued source layers: " + family);
                }
                item(root, client);
                require(ItemStack.matches(before, root), "Rendering mutated live root/source history");
            }
        }
        return "All six families: both orientations, moving/crouched native model, dye/trim/glint, native material/style submissions, selected-leg/waist geometry and item GUI/hand source clipping\n";
    }

    private static void item(ItemStack root, Minecraft client) {
        var data = MixedLeggingsStructure.validData(root).orElseThrow();
        var prepared = MixedLeggingsItemRenderer.prepareGui(data, true, client.getItemModelResolver(), client.level, client.player, 0);
        require(prepared.clipped() && prepared.compose().sources().size() == 2, "Native Aether item fell back");
        for (var context : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)) {
            var faces = ItemChecks.faces(ItemChecks.resolve(root, context));
            require(!faces.isEmpty() && faces.stream().allMatch(ItemChecks.Face::foil), "Empty item/native root glint lost");
            for (int side = 0; side < 2; side++) {
                var source = side == 0 ? data.left().source() : data.right().source();
                var textures = ItemChecks.faces(ItemChecks.resolve(source, context)).stream().map(ItemChecks.Face::texture).collect(Collectors.toSet());
                var selected = faces.stream().filter(face -> textures.contains(face.texture())).toList();
                require(!selected.isEmpty(), "Native item source texture missing");
                for (var face : selected) {
                    for (var vertex : face.vertices()) {
                        require(side == 0 ? vertex.x >= .5f - .0001f : vertex.x <= .5f + .0001f, "Native item source on wrong anatomical side");
                    }
                }
            }
        }
    }

    private static int compareSource(HumanoidModel<HumanoidRenderState> model, HumanoidRenderState state, ItemStack source,
                                     RecordingSubmits actual, int offset, boolean left) {
        source.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        var nativeOutput = new RecordingSubmits();
        EquippedClient.renderer.renderLayers(EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                source.get(DataComponents.EQUIPPABLE).assetId().orElseThrow(), model, state, source,
                new PoseStack(), nativeOutput, 15728880, 0);
        require(!nativeOutput.nodes.isEmpty(), "Empty native layer reference");
        require(nativeOutput.nodes.size() + offset <= actual.nodes.size(), "Missing native source submissions");
        if (source.has(AetherIIDataComponents.ARMOR_STYLE)) {
            require(nativeOutput.nodes.stream().anyMatch(node -> node.sprite() != null), "Native decorative overlay reference missing");
        }
        for (int index = 0; index < nativeOutput.nodes.size(); index++) {
            var expected = nativeOutput.nodes.get(index);
            var result = actual.nodes.get(offset + index);
            require(expected.type().equals(result.type()) && expected.color() == result.color()
                            && expected.sprite() == result.sprite(), "Wrong native material/dye/style in split output");
            var vertices = AetherVertices.model(result);
            var leg = AetherVertices.part(expected, left ? model.leftLeg : model.rightLeg);
            var wrong = AetherVertices.part(expected, left ? model.rightLeg : model.leftLeg);
            require(!vertices.isEmpty() && !leg.isEmpty() && vertices.containsAll(leg), "Selected native leg geometry/UV lost");
            require(!vertices.containsAll(wrong), "Opposite native leg leaked into source pass");
            require(vertices.size() > leg.size(), "Waist contribution disappeared");
        }
        return offset + nativeOutput.nodes.size();
    }

}
