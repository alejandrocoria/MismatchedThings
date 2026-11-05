package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimPatterns;

import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class AllTheModiumRenderChecks {
    private AllTheModiumRenderChecks() {
    }

    static String verify(Minecraft client) {
        require(EquippedClient.renderer != null, "Native equipment renderer was not observed");
        var rig = new HumanoidModel<HumanoidRenderState>(client.getEntityModels().bakeLayer(ModelLayers.PLAYER_ARMOR.legs()));
        for (String tier : List.of("allthemodium", "vibranium", "unobtainium")) {
            for (String trim : List.of("none", "minecraft:diamond", "allthemodium:allthemodium")) {
                var source = source(tier, trim);
                var nativeOutput = new RecordingSubmits();
                EquippedClient.renderer.renderLayers(EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                        source.get(DataComponents.EQUIPPABLE).assetId().orElseThrow(), rig, new HumanoidRenderState(),
                        source, new PoseStack(), nativeOutput, 15728880, 0);
                require(nativeOutput.nodes.size() == (trim.equals("none") ? 1 : 2), "Native leggings layer/trim control changed");
                var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.IRON_LEGGINGS));
                for (var root : List.of(pair.primary(), pair.inverse())) {
                    var before = root.copy();
                    for (boolean moving : List.of(false, true)) {
                        var output = new RecordingSubmits();
                        var state = new HumanoidRenderState();
                        state.isCrouching = moving;
                        state.walkAnimationPos = moving ? 1.4F : 0;
                        state.walkAnimationSpeed = moving ? 0.7F : 0;
                        MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, rig, state,
                                EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, root, new PoseStack(), output, 15728880, 0);
                        require(output.nodes.size() == nativeOutput.nodes.size() + 1
                                        && output.nodes.stream().allMatch(node -> node.model() instanceof LeggingsView<?>),
                                "Native source lost a pass or fell back to a complete owner");
                        boolean left = root == pair.primary();
                        int start = left ? 0 : 1;
                        for (int index = 0; index < nativeOutput.nodes.size(); index++) {
                            var expected = nativeOutput.nodes.get(index);
                            var actual = output.nodes.get(start + index);
                            require(actual.type() == expected.type() && actual.sprite() == expected.sprite(),
                                    "Mixed base/trim material differs from native " + tier + " / " + trim);
                            require(actual.model().root().getChild("leg") == (left ? rig.leftLeg : rig.rightLeg)
                                            && actual.model().root().getChild("waist") != null && !vertices(actual).isEmpty(),
                                    "Mixed native leg/waist geometry was lost or switched sides");
                        }
                    }
                    itemAndTooltip(client, root, source, !trim.equals("none"));
                    require(ItemStack.matches(before, root), "Rendering/tooltip changed root or source state");
                }
            }
        }
        return "allthemodium.threeNativeTiersBothOrientations=true\nallthemodium.nativeBaseAndOrdinaryCustomTrims=true\n"
                + "allthemodium.splitWaistLegsMovement=true\nallthemodium.guiHandsAndTooltips=true\n";
    }

    private static <S> List<EquippedChecks.Vertex> vertices(RecordingSubmits.Node<S> node) {
        return EquippedChecks.vertices(node.model(), node.state());
    }

    private static void itemAndTooltip(Minecraft client, ItemStack root, ItemStack source, boolean trimmed) {
        String texture = BuiltInRegistries.ITEM.getKey(source.getItem()).getPath();
        for (var context : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.FIXED)) {
            var nativeFaces = ItemChecks.faces(ItemChecks.resolve(source, context));
            var faces = ItemChecks.faces(ItemChecks.resolve(root, context));
            require(!nativeFaces.isEmpty() && faces.stream().anyMatch(face -> face.texture().contains(texture))
                            && faces.stream().anyMatch(face -> face.texture().contains("iron_leggings")),
                    "Native/mixed item lost a source texture in " + context);
            if (trimmed) {
                var trimTextures = nativeFaces.stream().filter(face -> face.texture().contains("trim"))
                        .map(ItemChecks.Face::texture).distinct().toList();
                require(!trimTextures.isEmpty() && trimTextures.stream()
                        .allMatch(trimTexture -> faces.stream().anyMatch(face -> face.texture().equals(trimTexture))),
                        "Mixed item lost native ordinary/custom trim sprites");
            }
        }
        for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
            var lines = root.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
            require(lines.stream().noneMatch(line -> line.getString().equals(Component.translatable("item.unbreakable").getString())),
                    "One native source falsely advertised an Unbreakable root");
            if (flag == TooltipFlag.ADVANCED) {
                String id = BuiltInRegistries.ITEM.getKey(source.getItem()).toString();
                require(lines.stream().anyMatch(line -> line.getString().equals(id)), "Advanced source ID was lost");
            }
        }
    }

    static ItemStack source(String tier, String material) {
        var source = AllTheModiumWorkstationChecks.nativeItem(tier + "_leggings");
        if (!material.equals("none")) {
            var registry = Minecraft.getInstance().level.registryAccess();
            source.set(DataComponents.TRIM, new ArmorTrim(registry.lookupOrThrow(Registries.TRIM_MATERIAL)
                    .getOrThrow(ResourceKey.create(Registries.TRIM_MATERIAL, Identifier.parse(material))),
                    registry.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.SENTRY)));
        }
        return source;
    }

    static void gallery() {
        var all = source("allthemodium", "none");
        var vib = source("vibranium", "minecraft:diamond");
        var uno = source("unobtainium", "allthemodium:allthemodium");
        var iron = new ItemStack(Items.IRON_LEGGINGS);
        RenderFixture.gallery = List.of(new RenderFixture.Sample("Native Allthemodium", all, 0),
                new RenderFixture.Sample("Allthemodium / iron", RenderFixture.pair(all, iron), 0),
                new RenderFixture.Sample("Iron / Allthemodium", RenderFixture.pair(iron, all), (float) Math.PI),
                new RenderFixture.Sample("Native Vibranium + diamond trim", vib, 0),
                new RenderFixture.Sample("Vibranium trim / iron", RenderFixture.pair(vib, iron), 0),
                new RenderFixture.Sample("Iron / Vibranium trim", RenderFixture.pair(iron, vib), (float) Math.PI),
                new RenderFixture.Sample("Native Unobtainium + custom trim", uno, 0),
                new RenderFixture.Sample("Unobtainium trim / iron (posed)", RenderFixture.pair(uno, iron), 0));
    }
}
