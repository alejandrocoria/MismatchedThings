package games.alejandrocoria.mismatchedleggings.test;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.renderer.GeoArmorRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.compatibility.oritech.OritechEquipmentCapture;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.mixin.compatibility.oritech.ArmorModelAccessor;
import games.alejandrocoria.mismatchedleggings.mixin.compatibility.oritech.LivingRendererAccessor;
import games.alejandrocoria.mismatchedleggings.test.render.ItemChecks;
import games.alejandrocoria.mismatchedleggings.test.render.RenderFixture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native armor-layer oracle across actual renderer/rig subtypes and decorated product dispatch. */
final class OritechWearerChecks {
    private static final int OUTLINE = 0xFF123456;
    private static Object previousModel;
    private static OritechCaptureChecks.Geometry queued;
    private static List<OritechCaptureChecks.Vertex> queuedVertices;

    private OritechWearerChecks() {
    }

    static void verify(Minecraft client) throws ReflectiveOperationException {
        queued = null;
        queuedVertices = null;
        // A remote player follows entity pose; LocalPlayer keeps crouching in its
        // input tick state and cannot be posed synchronously by setPose alone.
        var player = new RemotePlayer(client.level, client.player.getGameProfile());
        var equipment = player.getItemBySlot(EquipmentSlot.LEGS);
        var pose = player.getPose();
        try {
            player.setPose(Pose.STANDING);
            compare(client, player);
            player.walkAnimation.update(0.7F, 1, 1);
            require(state(client, player).walkAnimationSpeed > 0, "Moving player control has no walk animation");
            compare(client, player);
            player.setPose(Pose.CROUCHING);
            require(state(client, player).isCrouching, "Crouching player control is not crouched");
            compare(client, player);
        } finally {
            player.setItemSlot(EquipmentSlot.LEGS, equipment);
            player.setPose(pose);
        }
        var small = OritechCaptureChecks.actor(client, ItemStack.EMPTY, true);
        var setSmall = small.getClass().getDeclaredMethod("setSmall", boolean.class);
        setSmall.setAccessible(true);
        setSmall.invoke(small, true);
        compare(client, small);
        var zombie = new Zombie(EntityType.ZOMBIE, client.level);
        zombie.walkAnimation.update(0.6F, 1, 1);
        compare(client, zombie);
        zombie.setBaby(true);
        compare(client, zombie);

        var source = OritechCaptureChecks.exo();
        var renderer = GeoRenderProvider.of(source).getGeoArmorRenderer(source, EquipmentSlot.LEGS);
        var state = state(client, OritechCaptureChecks.actor(client, source, false));
        var model = renderer.getGeoModel().getBakedModel(renderer.getGeoModel().getModelResource((GeoRenderState) state));
        require(previousModel == null || model != previousModel, "Native baked model was retained across resource replacement");
        previousModel = model;
        presentation(client);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void compare(Minecraft client, LivingEntity actor) {
        var source = OritechCaptureChecks.exo();
        var original = source.copy();
        source.set(DataComponents.TRIM, RenderFixture.sources(2)[0].get(DataComponents.TRIM));
        source.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        var iron = new ItemStack(Items.IRON_LEGGINGS);
        iron.set(DataComponents.TRIM, source.get(DataComponents.TRIM));
        var pair = MixedLeggingsFactory.createPair(source, iron, CompositionRules.defaults());
        for (boolean foil : List.of(false, true)) {
            // Native Exo ignores equipped trim/glint; the ordinary half must not inherit that limitation.
            var projected = source.copy();
            projected.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
            actor.setItemSlot(EquipmentSlot.LEGS, projected);
            var nativeState = state(client, actor);
            nativeState.outlineColor = OUTLINE;
            var model = model(client, actor, nativeState);
            var control = new OritechCaptureChecks.Geometry();
            java.util.function.BiFunction nativeModel = (s, slot) -> model;
            require(GeoArmorRenderer.tryRenderGeoArmorPiece(nativeModel, new PoseStack(), control, projected,
                    EquipmentSlot.LEGS, 15728880, (HumanoidRenderState & GeoRenderState) nativeState), "Native wearer control not handled");
            require(control.nodes.size() == 1, "Native Exo decoration/outline now adds a pass; revisit its presentation contract");
            for (var root : List.of(pair.primary(), pair.inverse())) {
                root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
                var before = root.copy();
                actor.setItemSlot(EquipmentSlot.LEGS, root);
                var mixedState = state(client, actor);
                var capture = OritechEquipmentCapture.capture(source, mixedState, new PoseStack(), OUTLINE);
                require(capture != null && capture.prepareFull(), "Production capture rejected a supported wearer/rig");
                var full = OritechCaptureChecks.flush(capture);
                var vertices = OritechCaptureChecks.vertices(full);
                require(vertices.equals(OritechCaptureChecks.vertices(control))
                                && full.nodes.getFirst().type() == control.nodes.getFirst().type(),
                        "Production wearer pose/material/light differs from its native armor-layer oracle");
                if (queued != null) {
                    require(queuedVertices.equals(OritechCaptureChecks.vertices(queued)), "Another rig/foil request changed deferred native data");
                }
                queued = full;
                queuedVertices = vertices;
                var rendered = OritechPairChecks.render(root, mixedState, model(client, actor, mixedState), OUTLINE);
                require(rendered.custom.nodes.size() == 1 && rendered.models.nodes.size() == (foil ? 3 : 2),
                        "Production lost native Exo or ordinary trim/root glint passes");
                require(rendered.models.nodes.stream().allMatch(node -> node.outline() == OUTLINE), "Ordinary outline was lost");
                require(rendered.models.nodes.stream().anyMatch(node -> node.type() == RenderTypes.armorEntityGlint()) == foil,
                        "Ordinary half ignored live root foil");
                require(ItemStack.matches(root, before), "Decorated rendering changed source/root components");
            }
        }
        // The independent undecorated control must have the same native geometry/material/pass count.
        actor.setItemSlot(EquipmentSlot.LEGS, original);
        var plainState = state(client, actor);
        var plain = new OritechCaptureChecks.Geometry();
        var plainModel = model(client, actor, plainState);
        java.util.function.BiFunction plainRig = (s, slot) -> plainModel;
        require(GeoArmorRenderer.tryRenderGeoArmorPiece(plainRig, new PoseStack(), plain, original,
                EquipmentSlot.LEGS, 15728880, (HumanoidRenderState & GeoRenderState) plainState), "Plain native control missing");
        require(plain.nodes.size() == 1 && queuedVertices.equals(OritechCaptureChecks.vertices(plain))
                        && plain.nodes.getFirst().type() == queued.nodes.getFirst().type(),
                "Native trim/foil/outline presentation changed from the pinned contract");
    }

    private static HumanoidRenderState state(Minecraft client, LivingEntity actor) {
        return (HumanoidRenderState) client.getEntityRenderDispatcher().getRenderer(actor).createRenderState(actor, 0.375F);
    }

    private static HumanoidModel<?> model(Minecraft client, LivingEntity actor, HumanoidRenderState state) {
        var renderer = (LivingRendererAccessor) client.getEntityRenderDispatcher().getRenderer(actor);
        return renderer.mismatchedleggings$layers().stream().filter(layer -> layer instanceof HumanoidArmorLayer<?, ?, ?>)
                .map(layer -> ((ArmorModelAccessor) layer).mismatchedleggings$model(state, EquipmentSlot.LEGS)).findFirst().orElseThrow();
    }

    private static void presentation(Minecraft client) {
        var source = OritechCaptureChecks.exo();
        var before = source.copy();
        var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.IRON_LEGGINGS), CompositionRules.defaults());
        for (var root : List.of(pair.primary(), pair.inverse())) {
            require(root.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL).stream()
                    .filter(line -> line.getString().equals("Increases movement speed")).count() == 1, "Native source description missing/duplicated");
            for (var context : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
                var faces = ItemChecks.faces(ItemChecks.resolve(root, context));
                require(faces.stream().anyMatch(face -> face.texture().contains("exo_leggings"))
                                && faces.stream().anyMatch(face -> face.texture().contains("iron_leggings")),
                        "Generated item lost a source in " + context);
            }
        }
        require(ItemStack.matches(source, before), "Presentation changed original source");
    }
}
