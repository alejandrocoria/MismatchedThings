package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedChecks;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import games.alejandrocoria.mismatchedleggings.test.render.ItemChecks;
import games.alejandrocoria.mismatchedleggings.test.render.RecordingSubmits;
import games.alejandrocoria.mismatchedleggings.test.render.RenderFixture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPatterns;

import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class AquacultureRenderChecks {
    private AquacultureRenderChecks() {
    }

    static String verify(Minecraft client) {
        require(EquippedClient.renderer != null, "Native equipment renderer was not observed");
        var rig = new HumanoidModel<HumanoidRenderState>(client.getEntityModels().bakeLayer(ModelLayers.PLAYER_ARMOR.legs()));
        for (boolean trimmed : List.of(false, true)) {
            for (boolean foil : List.of(false, true)) {
                var source = source(client, trimmed, foil);
                for (var partner : List.of(new ItemStack(Items.IRON_LEGGINGS), leather())) {
                    partner.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
                    var roots = AquacultureChecks.pair(source, partner);
                    for (int orientation = 0; orientation < roots.size(); orientation++) {
                        var root = roots.get(orientation);
                        root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
                        var before = root.copy();
                        for (boolean moving : List.of(false, true)) {
                            var state = new HumanoidRenderState();
                            state.isCrouching = moving;
                            state.walkAnimationPos = moving ? 1.4F : 0;
                            state.walkAnimationSpeed = moving ? .7F : 0;
                            var left = nativePasses(orientation == 0 ? source : partner, rig, state);
                            var right = nativePasses(orientation == 0 ? partner : source, rig, state);
                            var output = new RecordingSubmits();
                            MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, rig, state,
                                    EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, root, new PoseStack(), output, 15728880, 0);
                            require(output.nodes.size() == left.nodes.size() + right.nodes.size(), "Mixed output lost a base/trim/foil pass");
                            for (int index = 0; index < output.nodes.size(); index++) {
                                boolean leftSide = index < left.nodes.size();
                                var expected = leftSide ? left.nodes.get(index) : right.nodes.get(index - left.nodes.size());
                                var actual = output.nodes.get(index);
                                require(actual.model() instanceof LeggingsView<?> && actual.type() == expected.type()
                                                && actual.sprite() == expected.sprite() && actual.color() == expected.color(),
                                        "Native material, trim, foil or partner dye differs in mixed output");
                                require(actual.model().root().getChild("leg") == (leftSide ? rig.leftLeg : rig.rightLeg)
                                                && actual.model().root().getChild("waist") != null && !vertices(actual).isEmpty(),
                                        "Mixed leg/waist split was lost or reversed");
                            }
                        }
                        for (var context : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                                ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.FIXED)) {
                            var nativeFaces = ItemChecks.faces(ItemChecks.resolve(source, context));
                            var mixedFaces = ItemChecks.faces(ItemChecks.resolve(root, context));
                            require(!nativeFaces.isEmpty() && nativeFaces.stream().noneMatch(face -> face.texture().contains("trim"))
                                            && mixedFaces.stream().anyMatch(face -> face.texture().contains("neptunium_leggings"))
                                            && mixedFaces.stream().anyMatch(face -> face.texture().contains(
                                                    partner.is(Items.LEATHER_LEGGINGS) ? "leather_leggings" : "iron_leggings")),
                                    "Mixed item lost a source texture or native static-item control changed");
                        }
                        require(ItemStack.matches(root, before), "Rendering changed root or source history");
                    }
                }
            }
        }
        return "Native base/trim/foil materials; both split leg/waist orientations; posed geometry; partner leather dye;\n"
                + "GUI/hand/fixed textures; native static item has no trim selector; root/history immutable\n";
    }

    private static RecordingSubmits nativePasses(ItemStack stack, HumanoidModel<HumanoidRenderState> rig, HumanoidRenderState state) {
        var output = new RecordingSubmits();
        EquippedClient.renderer.renderLayers(EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                stack.get(DataComponents.EQUIPPABLE).assetId().orElseThrow(), rig, state, stack, new PoseStack(), output, 15728880, 0);
        require(!output.nodes.isEmpty(), "Native layer control produced no geometry");
        return output;
    }

    private static <S> List<EquippedChecks.Vertex> vertices(RecordingSubmits.Node<S> node) {
        return EquippedChecks.vertices(node.model(), node.state());
    }

    static ItemStack source(Minecraft client, boolean trimmed, boolean foil) {
        var source = AquacultureChecks.nativeItem("neptunium_leggings");
        source.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
        if (trimmed) {
            var registry = client.level.registryAccess();
            source.set(DataComponents.TRIM, new ArmorTrim(registry.lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.DIAMOND),
                    registry.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.SENTRY)));
        }
        return source;
    }

    private static ItemStack leather() {
        var leather = new ItemStack(Items.LEATHER_LEGGINGS);
        leather.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3366CC));
        return leather;
    }

    static void gallery(Minecraft client) {
        var plain = source(client, false, false);
        var trim = source(client, true, true);
        var iron = new ItemStack(Items.IRON_LEGGINGS);
        RenderFixture.gallery = List.of(new RenderFixture.Sample("Native Neptunium", plain, 0),
                new RenderFixture.Sample("Neptunium / iron", RenderFixture.pair(plain, iron), 0),
                new RenderFixture.Sample("Iron / Neptunium", RenderFixture.pair(iron, plain), 0),
                new RenderFixture.Sample("Native trim + foil", trim, 0),
                new RenderFixture.Sample("Trim + foil / blue leather", RenderFixture.pair(trim, leather()), 0),
                new RenderFixture.Sample("Blue leather / trim + foil (back)", RenderFixture.pair(leather(), trim), (float) Math.PI),
                new RenderFixture.Sample("Native Neptunium (back)", plain, (float) Math.PI),
                new RenderFixture.Sample("Two trimmed Neptunium (posed)", RenderFixture.pair(trim, trim), 0));
    }
}
