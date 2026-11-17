package games.alejandrocoria.mismatchedleggings.test.silentgear;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedboots.client.render.MixedBootsEquipmentRenderer;
import games.alejandrocoria.mismatchedboots.client.render.MixedBootsItemRenderer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.silentchaos512.gear.setup.SgDataComponents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Actual material resources and native render submissions, not a screenshot gallery or copied assets. */
final class AtmPresentationChecks {
    private AtmPresentationChecks() {}

    static void verify(Minecraft client, int count) throws Exception {
        require(count == 20 && EquippedClient.renderer != null, "Missing actual ATM presentation samples/renderer");
        var leggings = new ArrayList<ItemStack>();
        var nativeColors = new HashSet<Integer>();
        var model = new HumanoidModel<HumanoidRenderState>(client.getEntityModels().bakeLayer(ModelLayers.ZOMBIE_ARMOR.legs()));
        model.root().getAllParts().forEach(part -> part.visible = false);
        model.root().visible = true;
        model.leftLeg.visible = true;
        model.rightLeg.visible = true;
        for (int i = 8; i < count; i++) {
            var root = client.player.containerMenu.getSlot(i).getItem();
            var before = root.copy();
            var sources = TooltipFixtures.sources(root);
            var material = sources.stream().filter(source -> source.has(SgDataComponents.GEAR_CONSTRUCTION.get())).findFirst().orElseThrow();
            require(material.has(SgDataComponents.GEAR_PROPERTIES.get()) && material.has(DataComponents.DYED_COLOR),
                    "Received material lacks native cached properties/tint");
            nativeColors.add(material.get(DataComponents.DYED_COLOR).rgb());
            if (MixedLeggingsStructure.isMixedItem(root)) {
                leggings.add(root);
            } else {
                var data = ModContent.getMixedBootsData(root).orElseThrow();
                require(MixedBootsItemRenderer.prepareGui(data, root.hasFoil(), client.getItemModelResolver(),
                        client.level, client.player, 0).clipped(), "ATM Boots item fell back instead of splitting");
                for (var context : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)) {
                    var faces = ItemChecks.faces(ItemChecks.resolve(root, context));
                    require(!faces.isEmpty(), "ATM Boots item submitted no geometry");
                    for (int side = 0; side < 2; side++) {
                        var nativeFaces = ItemChecks.faces(ItemChecks.resolve(sources.get(side), context));
                        int selected = side;
                        require(!nativeFaces.isEmpty() && faces.stream().anyMatch(face -> nativeFaces.stream()
                                .anyMatch(nativeFace -> nativeFace.texture().equals(face.texture()))
                                && face.vertices().stream().allMatch(vertex -> selected == 0 ? vertex.x >= .4999F : vertex.x <= .5001F)),
                                "ATM Boots lost native item texture/side");
                    }
                }
                var actual = new RecordingSubmits();
                var state = new HumanoidRenderState();
                require(MixedBootsEquipmentRenderer.renderLayers(EquippedClient.renderer, EquipmentClientInfo.LayerType.HUMANOID,
                        model, state, root, new PoseStack(), actual, 15728880, 0), "ATM Boots equipment fell back");
                int offset = 0;
                for (var source : sources) {
                    var reference = source.copy();
                    reference.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, root.hasFoil());
                    var nativeOutput = new RecordingSubmits();
                    EquippedClient.renderer.renderLayers(EquipmentClientInfo.LayerType.HUMANOID,
                            reference.get(DataComponents.EQUIPPABLE).assetId().orElseThrow(), model, state,
                            reference, new PoseStack(), nativeOutput, 15728880, 0);
                    require(!nativeOutput.nodes.isEmpty(), "Missing native ATM Boots equipment control");
                    for (var node : nativeOutput.nodes) {
                        require(offset < actual.nodes.size(), "ATM Boots lost a native layer");
                        var mixed = actual.nodes.get(offset++);
                        require(node.type().equals(mixed.type()) && node.color() == mixed.color() && node.sprite() == mixed.sprite(),
                                "ATM Boots native equipment texture/tint differs");
                    }
                }
                require(offset == actual.nodes.size(), "ATM Boots added an unexpected layer");
            }
            require(ItemStack.matches(before, root), "ATM presentation changed root/source state");
        }
        require(nativeColors.size() == 3 && leggings.size() == 6, "Three distinct native material tints/both leggings orientations were not exercised");
        var snapshots = leggings.stream().map(ItemStack::copy).toList();
        StateRendering.verify(client, leggings);
        for (int i = 0; i < leggings.size(); i++) {
            require(ItemStack.matches(leggings.get(i), snapshots.get(i)), "ATM leggings rendering changed provenance");
        }
    }
}
