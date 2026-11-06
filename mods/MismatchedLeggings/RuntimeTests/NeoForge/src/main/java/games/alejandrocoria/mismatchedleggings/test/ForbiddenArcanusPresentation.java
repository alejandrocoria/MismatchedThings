package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
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

final class ForbiddenArcanusPresentation {
    private ForbiddenArcanusPresentation() {}

    static void verify(Minecraft client) {
        require(EquippedClient.renderer != null, "Native equipment renderer was not captured");
        var rig = new HumanoidModel<HumanoidRenderState>(client.getEntityModels().bakeLayer(ModelLayers.PLAYER_ARMOR.legs()));
        for (String tier : List.of("draco_arcanus", "tyr")) {
            for (boolean trimmed : List.of(false, true)) {
                var source = ForbiddenArcanusChecks.nativeItem(tier + "_leggings");
                if (trimmed) {
                    trim(client, source);
                }
                var nativeOutput = new RecordingSubmits();
                EquippedClient.renderer.renderLayers(EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                        source.get(DataComponents.EQUIPPABLE).assetId().orElseThrow(), rig, new HumanoidRenderState(),
                        source, new PoseStack(), nativeOutput, 15728880, 0);
                require(nativeOutput.nodes.size() == (trimmed ? 2 : 1), "Native base/trim positive control changed");
                var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.IRON_LEGGINGS), CompositionRules.defaults());
                for (var root : List.of(pair.primary(), pair.inverse())) {
                    var before = root.copy();
                    for (boolean moving : List.of(false, true)) {
                        var state = new HumanoidRenderState();
                        state.isCrouching = moving;
                        state.walkAnimationPos = moving ? 1.4F : 0;
                        state.walkAnimationSpeed = moving ? .7F : 0;
                        var output = new RecordingSubmits();
                        MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, rig, state,
                                EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, root, new PoseStack(), output, 15728880, 0);
                        require(output.nodes.size() == nativeOutput.nodes.size() + 1
                                        && output.nodes.stream().allMatch(node -> node.model() instanceof LeggingsView<?>),
                                "Native layer was lost or complete-owner fallback replaced the split");
                        boolean left = root == pair.primary();
                        for (int index = 0; index < nativeOutput.nodes.size(); index++) {
                            var expected = nativeOutput.nodes.get(index);
                            var actual = output.nodes.get((left ? 0 : 1) + index);
                            require(actual.type() == expected.type() && actual.sprite() == expected.sprite()
                                            && actual.model().root().getChild("leg") == (left ? rig.leftLeg : rig.rightLeg)
                                            && actual.model().root().getChild("waist") != null,
                                    "Native material or split leg/waist changed: " + tier);
                        }
                    }
                    for (var context : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                            ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.FIXED)) {
                        var faces = ItemChecks.faces(ItemChecks.resolve(root, context));
                        require(faces.stream().anyMatch(face -> face.texture().contains(tier + "_leggings"))
                                        && faces.stream().anyMatch(face -> face.texture().contains("iron_leggings")),
                                "Mixed item lost native or partner texture in " + context);
                    }
                    for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                        var lines = root.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
                        String attributeName = Component.translatable(ForbiddenArcanusChecks.attribute().value().getDescriptionId()).getString();
                        require(lines.stream().filter(line -> line.getString().contains(attributeName)).count()
                                        == (root == pair.primary() ? 1 : 0),
                                "Tooltip lost/duplicated Aureal or ignored separate policy");
                    }
                    require(ItemStack.matches(before, root), "Rendering/tooltip mutated root or source history");
                }
            }
        }
    }

    static void trim(Minecraft client, ItemStack stack) {
        var registries = client.level.registryAccess();
        stack.set(DataComponents.TRIM, new ArmorTrim(registries.lookupOrThrow(Registries.TRIM_MATERIAL)
                .getOrThrow(ResourceKey.create(Registries.TRIM_MATERIAL, Identifier.parse("minecraft:diamond"))),
                registries.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.SENTRY)));
    }
}
