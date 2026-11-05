package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.client.content.equipment.armor.CardboardArmorHandlerClient;
import com.zurrtum.create.client.content.equipment.armor.CardboardArmorStealthOverlay;
import com.zurrtum.create.client.content.equipment.armor.CardboardRenderState;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedChecks;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import games.alejandrocoria.mismatchedleggings.test.render.ItemChecks;
import games.alejandrocoria.mismatchedleggings.test.render.RecordingSubmits;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPatterns;

import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class CreateFlyRenderChecks {
    private CreateFlyRenderChecks() {
    }

    static String verify(Minecraft client) {
        require(EquippedClient.renderer != null, "Native equipment renderer was not observed");
        var rig = new HumanoidModel<HumanoidRenderState>(client.getEntityModels().bakeLayer(ModelLayers.PLAYER_ARMOR.legs()));
        for (boolean trimmed : List.of(false, true)) {
            var cardboard = source(trimmed);
            var gold = new ItemStack(Items.GOLDEN_LEGGINGS);
            var pair = MixedLeggingsFactory.createPair(cardboard, gold);
            var nativeNodes = full(rig, cardboard);
            require(nativeNodes.nodes.size() == (trimmed ? 2 : 1), "Native Cardboard layer control changed");
            if (trimmed) {
                var sprite = nativeNodes.nodes.getLast().sprite();
                require(sprite != null && sprite.contents().name().getNamespace().equals("create"),
                        "Native Cardboard did not select Create's trim sprite");
            }
            for (var root : List.of(pair.primary(), pair.inverse())) {
                var before = root.copy();
                var output = new RecordingSubmits();
                var state = new HumanoidRenderState();
                state.isCrouching = true;
                state.walkAnimationPos = 1.4F;
                state.walkAnimationSpeed = 0.7F;
                MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, rig, state,
                        EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS, root, new PoseStack(), output, 15728880, 0);
                require(output.nodes.size() == nativeNodes.nodes.size() + 1
                                && output.nodes.stream().allMatch(node -> node.model() instanceof LeggingsView<?>),
                        "Cardboard source fell back to complete-owner rendering or lost a native pass");
                boolean left = root == pair.primary();
                int nativeStart = left ? 0 : 1;
                for (int index = 0; index < nativeNodes.nodes.size(); index++) {
                    var expected = nativeNodes.nodes.get(index);
                    var actual = output.nodes.get(nativeStart + index);
                    require(actual.type() == expected.type() && actual.sprite() == expected.sprite(),
                            "Split Cardboard changed its native base/trim material");
                    require(actual.model().root().getChild("leg") == (left ? rig.leftLeg : rig.rightLeg),
                            "Cardboard selected the opposite native leg");
                    require(!vertices(actual).isEmpty(), "Native Cardboard split geometry was empty");
                }
                item(root, trimmed);
                require(ItemStack.matches(before, root), "Rendering changed source components");
            }
        }
        boxAndOverlay(client);
        return "cardboard.nativeBaseAndCreateTrimSprites=true\ncardboard.splitWaistAndNativeLegs=true\n"
                + "cardboard.itemContextsAndGuiTrim=true\ncardboard.nativeBoxAndOverlay=true\n";
    }

    static ItemStack source(boolean trimmed) {
        var source = AllItems.CARDBOARD_LEGGINGS.getDefaultInstance();
        if (trimmed) {
            var registry = Minecraft.getInstance().level.registryAccess();
            source.set(DataComponents.TRIM, new ArmorTrim(
                    registry.lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.DIAMOND),
                    registry.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.SENTRY)));
        }
        return source;
    }

    private static RecordingSubmits full(HumanoidModel<HumanoidRenderState> rig, ItemStack source) {
        var output = new RecordingSubmits();
        EquippedClient.renderer.renderLayers(EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                source.get(DataComponents.EQUIPPABLE).assetId().orElseThrow(), rig, new HumanoidRenderState(),
                source, new PoseStack(), output, 15728880, 0);
        return output;
    }

    private static <S> List<EquippedChecks.Vertex> vertices(RecordingSubmits.Node<S> node) {
        return EquippedChecks.vertices(node.model(), node.state());
    }

    private static void item(ItemStack root, boolean trimmed) {
        for (var context : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                ItemDisplayContext.THIRD_PERSON_LEFT_HAND, ItemDisplayContext.FIXED)) {
            var resolved = ItemChecks.resolve(root, context);
            require(!resolved.isEmpty(), "Native Cardboard mixed item was empty");
            var faces = ItemChecks.faces(resolved);
            require(faces.stream().anyMatch(face -> face.texture().contains("cardboard"))
                            && faces.stream().anyMatch(face -> face.texture().contains("golden_leggings")),
                    "Cardboard mixed item lost a source texture");
            if (trimmed) {
                require(faces.stream().anyMatch(face -> face.texture().contains("trim")), "Source item trim was lost");
            }
        }
    }

    private static void boxAndOverlay(Minecraft client) {
        var player = client.player;
        var roots = MixedLeggingsFactory.createPair(source(false), new ItemStack(Items.GOLDEN_LEGGINGS));
        var boots = Boolean.getBoolean("leggings.test.boots")
                ? BootsCoexistence.pair(AllItems.CARDBOARD_BOOTS.getDefaultInstance(), new ItemStack(Items.IRON_BOOTS))
                : List.of(AllItems.CARDBOARD_BOOTS.getDefaultInstance());
        try {
            for (var feet : boots) {
                for (var legs : List.of(source(false), roots.primary(), roots.inverse())) {
                    player.setItemSlot(EquipmentSlot.HEAD, AllItems.CARDBOARD_HELMET.getDefaultInstance());
                    player.setItemSlot(EquipmentSlot.CHEST, AllItems.CARDBOARD_CHESTPLATE.getDefaultInstance());
                    player.setItemSlot(EquipmentSlot.LEGS, legs);
                    player.setItemSlot(EquipmentSlot.FEET, feet);
                    player.setPose(Pose.CROUCHING);
                    var state = box(legs, feet);
                    var renderer = (AvatarRenderer<?>) (Object) client.getEntityRenderDispatcher().getRenderer(state);
                    require(CardboardArmorHandlerClient.playerRendersAsBoxWhenSneaking(renderer, state,
                            new PoseStack(), new RecordingSubmits()), "Native box renderer rejected a valid mixed set");
                    overlay(client, true);
                    player.getAbilities().flying = true;
                    var flying = box(legs, feet);
                    require(!CardboardArmorHandlerClient.playerRendersAsBoxWhenSneaking(renderer, flying,
                            new PoseStack(), new RecordingSubmits()), "Flying mixed set still rendered as a box");
                    player.getAbilities().flying = false;
                    state.headEquipment = new ItemStack(Items.IRON_HELMET);
                    require(!CardboardArmorHandlerClient.playerRendersAsBoxWhenSneaking(renderer, state,
                            new PoseStack(), new RecordingSubmits()), "Incomplete set still rendered as a box");
                    player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                    overlay(client, false);
                }
            }
        } finally {
            player.getAbilities().flying = false;
            for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
                player.setItemSlot(slot, ItemStack.EMPTY);
            }
            player.setPose(Pose.STANDING);
        }
    }

    static AvatarRenderState box(ItemStack legs, ItemStack feet) {
        var state = new AvatarRenderState();
        state.entityType = net.minecraft.world.entity.EntityType.PLAYER;
        state.id = Minecraft.getInstance().player.getId();
        state.isCrouching = true;
        state.pose = Pose.CROUCHING;
        state.headEquipment = AllItems.CARDBOARD_HELMET.getDefaultInstance();
        state.chestEquipment = AllItems.CARDBOARD_CHESTPLATE.getDefaultInstance();
        state.legsEquipment = legs;
        state.feetEquipment = feet;
        state.scale = 1;
        var client = Minecraft.getInstance();
        var camera = client.options.getCameraType();
        try {
            // The native first-person skip must not make a box-submission assertion vacuously pass.
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            ((CardboardRenderState) (Object) state).create$update(client.player, 0);
            require(!((CardboardRenderState) (Object) state).create$isSkip(), "Native box submission was skipped");
        } finally {
            client.options.setCameraType(camera);
        }
        return state;
    }

    private static void overlay(Minecraft client, boolean hidden) {
        for (int tick = 0; tick < 32; tick++) {
            CardboardArmorStealthOverlay.clientTick(client);
        }
        float opacity = CardboardArmorStealthOverlay.getOverlayOpacity(client.getDeltaTracker());
        require(hidden ? opacity > 0.9F : opacity < 0.1F, "Native stealth overlay disagreed with the equipped set");
    }
}
