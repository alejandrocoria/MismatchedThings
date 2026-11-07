package games.alejandrocoria.mismatchedleggings.test.rootsclassic;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.compatibility.rootsclassic.RootsEquipmentLayers;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsGeometryCache;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import games.alejandrocoria.mismatchedleggings.test.render.ItemChecks;
import games.alejandrocoria.mismatchedleggings.test.render.RecordingSubmits;
import games.alejandrocoria.mismatchedleggings.test.render.RenderFixture;
import games.alejandrocoria.mismatchedleggings.test.rootsclassic.mixin.ItemExtensionsAccess;
import games.alejandrocoria.mismatchedleggings.test.rootsclassic.mixin.LivingLayersAccess;
import games.alejandrocoria.mismatchedleggings.test.rootsclassic.mixin.NativeArmorAccess;
import games.alejandrocoria.mismatchedthings.api.tooltip.TooltipSeparator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.Rotations;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native independent controls against packaged production routing, with no test render interception. */
public final class RootsRenderChecks {
    private static final int LIGHT = 0x00A000A0;
    private static final int OUTLINE = 0xFF123456;
    private static int phase;
    private static int ticks;
    private static Scene scene;
    private static CompletableFuture<Void> reload;
    private static long generation;
    private static volatile boolean captured;
    private static volatile Throwable captureFailure;
    private static RecordingSubmits.Node<?> queued;
    private static List<Vertices.Vertex> queuedBefore;

    private RootsRenderChecks() {
    }

    public static boolean roots(ItemStack stack) {
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.equals("rootsclassic:sylvan_tunic") || id.equals("rootsclassic:wildwood_leggings");
    }

    public static void tick(Minecraft client) {
        if (phase == 3) {
            return;
        }
        try {
            require(++ticks < 2400, "Roots rendering checks timed out at phase " + phase);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Automated client became visible");
            if (captureFailure != null) {
                throw new AssertionError("Roots panel capture failed", captureFailure);
            }
            if (client.level == null || client.player == null || client.getOverlay() != null) {
                return;
            }
            if (phase == 0 && client.screen == null) {
                SkeletonChecks.verify();
                require(EquippedClient.renderer != null, "Equipment renderer was not observed");
                verify(client);
                scene = new Scene(client);
                client.setScreen(scene);
                phase = 1;
            } else if (phase == 1 && scene.frames >= 8) {
                screenshot(client, "roots-pairs.png");
                phase = 2;
            } else if (phase == 2 && captured) {
                if (reload == null) {
                    generation = LeggingsGeometryCache.statistics().generation();
                    reload = client.reloadResourcePacks();
                    return;
                }
                if (!reload.isDone()) {
                    return;
                }
                reload.join();
                require(LeggingsGeometryCache.statistics().generation() > generation, "Real reload did not invalidate product geometry");
                verify(client);
                Files.writeString(report("roots-rendering.txt"),
                        "original.nativeLegsEmpty=true\nproduction.mixedOnlyAlternateLayer=true\n"
                                + "resources.requestedPriorityMissingAlternateAndUnknownBinding=true\ngeometry.nativeLegSubtreesUvNormalsMaterialsExact=true\n"
                                + "geometry.noSyntheticWaistOrFeet=true\nqueue.cachedModelsAndDistinctPoses=true\n"
                                + "decorations.rootFoilNativeAlternateLayerTrimOutline=true\nfallback.fullRootsOwnerOrNeutralAtomic=true\n"
                                + "wearers.movingCrouchedPlayerPosedSmallStandAdultBabyZombie=true\nreload.sameAssertions=true\n"
                                + "data.sourcesAndRootsUnchanged=true\n");
                SkeletonChecks.passed("client");
                phase = 3;
                client.stop();
            }
        } catch (Throwable failure) {
            phase = 3;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static ItemStack source(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("rootsclassic:" + name)));
    }

    private static List<ItemStack> samples() {
        return List.of(source("sylvan_tunic"), source("wildwood_leggings"), new ItemStack(Items.IRON_LEGGINGS));
    }

    private static ItemStack pair(ItemStack first, ItemStack second) {
        return MixedLeggingsFactory.createPair(first, second).primary();
    }

    private static void verify(Minecraft client) throws Exception {
        queued = null;
        queuedBefore = null;
        // The packaged mixed-only route must not silently fix or rewrite originals.
        for (var source : samples().subList(0, 2)) {
            var nativeActor = stand(client, source, false);
            var original = new RecordingSubmits();
            armor(client, nativeActor).leggingsRootsTest$render(new PoseStack(), original, source, EquipmentSlot.LEGS, LIGHT, state(client, nativeActor));
            require(original.nodes.isEmpty(), "Mixed-only routing changed native leggings");
        }
        var player = new RemotePlayer(client.level, client.player.getGameProfile());
        player.walkAnimation.update(0.8F, 1, 1);
        require(state(client, player).walkAnimationSpeed > 0, "Moving player control has no walk animation");
        compare(client, player);
        player.setPose(Pose.CROUCHING);
        require(state(client, player).isCrouching, "Player fixture is not crouched");
        compare(client, player);
        var stand = stand(client, ItemStack.EMPTY, true);
        compare(client, stand);
        var small = ArmorStand.class.getDeclaredMethod("setSmall", boolean.class);
        small.setAccessible(true);
        small.invoke(stand, true);
        compare(client, stand);
        var zombie = new Zombie(EntityType.ZOMBIE, client.level);
        zombie.walkAnimation.update(0.7F, 1, 1);
        compare(client, zombie);
        zombie.setBaby(true);
        compare(client, zombie);
        decorations(client);
        fallback(client);
        resources(client);
        presentation(client);
    }

    private static void compare(Minecraft client, LivingEntity actor) {
        for (var first : samples()) {
            for (var second : samples()) {
                if (!roots(first) && !roots(second)) {
                    continue;
                }
                var beforeFirst = first.copy();
                var beforeSecond = second.copy();
                var root = pair(first, second);
                var before = root.copy();
                var output = mixed(client, actor, root);
                require(output.nodes.size() == 2 && output.nodes.stream().allMatch(node -> node.model() instanceof LeggingsView<?>),
                        "Pair fell back or lost a source after alternate-layer routing");
                for (int side = 0; side < 2; side++) {
                    var input = side == 0 ? first : second;
                    var node = output.nodes.get(side);
                    require(node.light() == LIGHT && node.outline() == OUTLINE, "Native source light/outline was lost");
                    if (!roots(input)) {
                        continue;
                    }
                    var state = state(client, actor);
                    var control = alternate(client, actor, input, state).nodes.getFirst();
                    var nativeModel = (HumanoidModel<?>) control.model();
                    var expected = Vertices.part(control, side == 0 ? nativeModel.leftLeg : nativeModel.rightLeg);
                    require(!expected.isEmpty() && Vertices.model(node).equals(expected) && node.type() == control.type(),
                            "Split differs from the independent native selected-leg subtree");
                    var union = new ArrayList<>(Vertices.part(control, nativeModel.leftLeg));
                    union.addAll(Vertices.part(control, nativeModel.rightLeg));
                    require(Vertices.sorted(union).equals(Vertices.sorted(Vertices.model(control))),
                            "Native LEGS renders unexpected body/waist/feet geometry");
                    if (queued != null) {
                        require(queuedBefore.equals(Vertices.model(queued)), "Another cached model request changed earlier queued geometry");
                    }
                    queued = node;
                    queuedBefore = Vertices.model(node);
                }
                require(ItemStack.matches(root, before) && ItemStack.matches(first, beforeFirst) && ItemStack.matches(second, beforeSecond),
                        "Routing/render capture changed root/source data");
            }
        }
    }

    private static void decorations(Minecraft client) {
        for (var input : samples().subList(0, 2)) {
            input.set(DataComponents.TRIM, RenderFixture.sources(2)[0].get(DataComponents.TRIM));
            var partner = new ItemStack(Items.IRON_LEGGINGS);
            partner.set(DataComponents.TRIM, input.get(DataComponents.TRIM));
            for (boolean inverse : List.of(false, true)) {
                var root = inverse ? pair(partner, input) : pair(input, partner);
                for (boolean foil : List.of(false, true)) {
                    root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
                    var actor = stand(client, root, false);
                    var output = mixed(client, actor, root);
                    require(output.nodes.size() == (foil ? 6 : 4), "Ordinary native layer trim/glint submissions changed");
                    var projected = input.copy();
                    projected.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
                    var control = alternate(client, actor, projected, state(client, actor));
                    require(control.nodes.size() == (foil ? 3 : 2), "Alternate-layer decoration control changed");
                    int start = inverse ? control.nodes.size() : 0;
                    for (int pass = 0; pass < control.nodes.size(); pass++) {
                        require(output.nodes.get(start + pass).type() == control.nodes.get(pass).type(), "Source trim/glint material changed");
                    }
                }
            }
        }
    }

    private static void fallback(Minecraft client) {
        var source = source("sylvan_tunic");
        var unsupported = new ItemStack(Items.GOLDEN_LEGGINGS);
        unsupported.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS)
                .setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.parse("leggings_skeleton_test:missing"))).build());
        var actor = stand(client, ItemStack.EMPTY, false);
        var full = mixed(client, actor, pair(source, unsupported));
        var control = alternate(client, actor, source, state(client, actor));
        require(full.nodes.size() == 1 && Vertices.model(full.nodes.getFirst()).equals(Vertices.model(control.nodes.getFirst())),
                "Rejected right source leaked a half or lost the complete Roots owner");
        var neutral = mixed(client, actor, pair(unsupported, source));
        require(neutral.nodes.size() == 1 && neutral.nodes.getFirst().type() == RenderTypes.armorCutoutNoCull(
                        Identifier.withDefaultNamespace("textures/entity/equipment/humanoid_leggings/iron.png")),
                "Unsafe first source did not use neutral atomically");
    }

    private static ItemStack asset(ItemStack input, String name) {
        var changed = input.copy();
        changed.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS)
                .setAsset(ResourceKey.create(EquipmentAssets.ROOT_ID, Identifier.parse("leggings_skeleton_test:" + name))).build());
        return changed;
    }

    private static void presentation(Minecraft client) {
        String perk = Component.translatable("rootsclassic.attribute.increasedmanaregen").getString();
        for (var first : samples()) {
            for (var second : samples()) {
                if (!roots(first) && !roots(second)) {
                    continue;
                }
                var root = pair(first, second);
                var before = root.copy();
                for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                    var lines = root.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
                    int separator = lines.indexOf(TooltipSeparator.INSTANCE);
                    int last = lines.lastIndexOf(TooltipSeparator.INSTANCE);
                    require(separator > 0 && last > separator, "Roots tooltip lost its source sections");
                    for (int side = 0; side < 2; side++) {
                        var section = side == 0 ? lines.subList(1, separator) : lines.subList(separator + 1, last);
                        require(section.stream().filter(line -> line.getString().contains(perk)).count()
                                        == (roots(side == 0 ? first : second) ? 1 : 0),
                                "Native Roots perk was lost, duplicated or assigned to the other source");
                    }
                    require(lines.subList(last + 1, lines.size()).stream().noneMatch(line -> line.getString().contains(perk)),
                            "Source perk was duplicated in a new root tooltip block");
                }
                for (var context : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                        ItemDisplayContext.THIRD_PERSON_LEFT_HAND)) {
                    var nativeTextures = new ArrayList<String>();
                    nativeTextures.addAll(ItemChecks.faces(ItemChecks.resolve(first, context)).stream().map(ItemChecks.Face::texture).toList());
                    nativeTextures.addAll(ItemChecks.faces(ItemChecks.resolve(second, context)).stream().map(ItemChecks.Face::texture).toList());
                    var mixedTextures = ItemChecks.faces(ItemChecks.resolve(root, context)).stream().map(ItemChecks.Face::texture).toList();
                    require(!nativeTextures.isEmpty() && mixedTextures.containsAll(nativeTextures),
                            "Roots mixed inventory/hand model lost native source textures");
                }
                require(ItemStack.matches(root, before), "Roots tooltip/item rendering mutated source data");
            }
        }
    }

    private static void resources(Minecraft client) {
        var requested = EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS;
        var renderer = EquippedClient.renderer;
        var actor = stand(client, ItemStack.EMPTY, false);
        for (var source : samples().subList(0, 2)) {
            require(RootsEquipmentLayers.select(source, requested, renderer) == EquipmentClientInfo.LayerType.HUMANOID,
                    "Pinned Roots missing layer did not select the available native alternate");
            var supplied = asset(source, "roots_requested");
            require(RootsEquipmentLayers.select(supplied, requested, renderer) == requested,
                    "A supplied requested resource layer lost priority to the compatibility fallback");
            require(RootsEquipmentLayers.select(supplied, EquipmentClientInfo.LayerType.HUMANOID_BABY, renderer)
                            == EquipmentClientInfo.LayerType.HUMANOID_BABY,
                    "A supplied baby resource layer lost priority");
            for (boolean inverse : List.of(false, true)) {
                var iron = new ItemStack(Items.IRON_LEGGINGS);
                var output = mixed(client, actor, inverse ? pair(iron, supplied) : pair(supplied, iron));
                require(output.nodes.size() == 2, "Supplied requested layer failed actual production capture");
                var node = output.nodes.get(inverse ? 1 : 0);
                var control = nativeLayer(client, actor, supplied, state(client, actor), requested).nodes.getFirst();
                var nativeModel = (HumanoidModel<?>) control.model();
                require(node.type() == control.type() && Vertices.model(node).equals(Vertices.part(control,
                                inverse ? nativeModel.rightLeg : nativeModel.leftLeg)),
                        "Requested resource layer differs from its independent native geometry/material control");
            }

            var missing = asset(source, "missing");
            require(RootsEquipmentLayers.select(missing, requested, renderer) == null, "Missing all layers was admitted");
            var neutral = mixed(client, actor, pair(missing, source));
            require(neutral.nodes.size() == 1 && neutral.nodes.getFirst().type() == RenderTypes.armorCutoutNoCull(
                            Identifier.withDefaultNamespace("textures/entity/equipment/humanoid_leggings/iron.png")),
                    "Missing Roots owner did not fall back atomically to neutral");
            var full = mixed(client, actor, pair(new ItemStack(Items.GOLDEN_LEGGINGS), missing));
            require(full.nodes.size() == 1 && full.nodes.getFirst().type() == RenderTypes.armorCutoutNoCull(
                            Identifier.withDefaultNamespace("textures/entity/equipment/humanoid_leggings/gold.png")),
                    "Missing Roots counterpart did not retain the complete ordinary owner");
            var noAlternate = asset(source, "roots_no_alternate");
            require(RootsEquipmentLayers.select(noAlternate, EquipmentClientInfo.LayerType.HUMANOID_BABY, renderer) == null,
                    "Missing requested and HUMANOID layers incorrectly selected another layer");

            // Replace the real native binding, then restore it even if a rejection assertion fails.
            var extensions = ItemExtensionsAccess.leggingsRootsTest$extensions();
            var nativeExtension = extensions.put(source.getItem(), new IClientItemExtensions() {});
            try {
                require(nativeExtension != null, "Pinned native extension was never registered");
                require(RootsEquipmentLayers.select(source, requested, renderer) == null,
                        "Unsupported native binding was admitted to alternate rendering");
                require(RootsEquipmentLayers.select(supplied, requested, renderer) == requested,
                        "Unknown binding incorrectly disabled an available ordinary requested layer");
                var rejected = mixed(client, actor, pair(source, new ItemStack(Items.GOLDEN_LEGGINGS)));
                require(rejected.nodes.size() == 1 && rejected.nodes.getFirst().type() == RenderTypes.armorCutoutNoCull(
                                Identifier.withDefaultNamespace("textures/entity/equipment/humanoid_leggings/iron.png")),
                        "Unsupported Roots owner leaked or rendered an unsafe alternate");
            } finally {
                extensions.put(source.getItem(), nativeExtension);
            }
        }
        var ordinary = asset(new ItemStack(Items.IRON_LEGGINGS), "roots_requested");
        require(RootsEquipmentLayers.select(ordinary, requested, renderer) == requested,
                "Ordinary source was captured by Roots-specific policy");
    }

    private static NativeArmorAccess armor(Minecraft client, LivingEntity actor) {
        var renderer = (LivingLayersAccess) client.getEntityRenderDispatcher().getRenderer(actor);
        return renderer.leggingsRootsTest$layers().stream().filter(layer -> layer instanceof HumanoidArmorLayer<?, ?, ?>)
                .map(layer -> (NativeArmorAccess) layer).findFirst().orElseThrow();
    }

    private static HumanoidRenderState state(Minecraft client, LivingEntity actor) {
        return (HumanoidRenderState) client.getEntityRenderDispatcher().getRenderer(actor).createRenderState(actor, 0.375F);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RecordingSubmits alternate(Minecraft client, LivingEntity actor, ItemStack source, HumanoidRenderState state) {
        return nativeLayer(client, actor, source, state, EquipmentClientInfo.LayerType.HUMANOID);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RecordingSubmits nativeLayer(Minecraft client, LivingEntity actor, ItemStack source,
                                                HumanoidRenderState state, EquipmentClientInfo.LayerType layer) {
        var output = new RecordingSubmits();
        var asset = source.get(DataComponents.EQUIPPABLE).assetId().orElseThrow();
        EquippedClient.renderer.renderLayers(layer, asset,
                (HumanoidModel) armor(client, actor).leggingsRootsTest$model(state, EquipmentSlot.LEGS), state,
                source, new PoseStack(), output, LIGHT, OUTLINE);
        return output;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static RecordingSubmits mixed(Minecraft client, LivingEntity actor, ItemStack root) {
        actor.setItemSlot(EquipmentSlot.LEGS, root);
        var state = state(client, actor);
        var output = new RecordingSubmits();
        var requested = state.isBaby && state.entityType != EntityType.ARMOR_STAND
                ? EquipmentClientInfo.LayerType.HUMANOID_BABY : EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS;
        MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer,
                (HumanoidModel) armor(client, actor).leggingsRootsTest$model(state, EquipmentSlot.LEGS), state,
                requested, root, new PoseStack(), output, LIGHT, OUTLINE);
        return output;
    }

    private static ArmorStand stand(Minecraft client, ItemStack legs, boolean posed) {
        var actor = new ArmorStand(client.level, 0, 80, 0);
        actor.setItemSlot(EquipmentSlot.LEGS, legs);
        if (posed) {
            actor.setLeftLegPose(new Rotations(-35, 10, -10));
            actor.setRightLegPose(new Rotations(25, -10, 12));
        }
        if (Boolean.getBoolean("leggings.test.boots")) {
            actor.setItemSlot(EquipmentSlot.FEET, BootsCoexistence.pair(source("sylvan_boots"), new ItemStack(Items.IRON_BOOTS)).getFirst());
        }
        return actor;
    }

    private static Path report(String name) {
        return Path.of(System.getProperty("leggings.test.report"), name);
    }

    private static void screenshot(Minecraft client, String name) {
        captured = false;
        Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
            try (image) {
                image.writeToFile(report(name));
                captured = true;
            } catch (Throwable failure) {
                captureFailure = failure;
            }
        });
    }

    private static final class Scene extends Screen {
        private final List<ArmorStand> actors = new ArrayList<>();
        private final List<String> labels = List.of("Native Sylvan (original)", "Native Wildwood (original)", "Sylvan / iron", "Iron / Sylvan",
                "Wildwood / iron", "Iron / Wildwood", "Sylvan / Wildwood", "Wildwood / Sylvan");
        int frames;

        Scene(Minecraft client) {
            super(Component.literal("Roots leggings: production mixed-only routing"));
            var sylvan = source("sylvan_tunic");
            var wildwood = source("wildwood_leggings");
            var iron = new ItemStack(Items.IRON_LEGGINGS);
            for (var sample : List.of(sylvan, wildwood, pair(sylvan, iron), pair(iron, sylvan), pair(wildwood, iron),
                    pair(iron, wildwood), pair(sylvan, wildwood), pair(wildwood, sylvan))) {
                actors.add(stand(client, sample, false));
            }
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF20252C);
            int cellWidth = width / 4;
            int cellHeight = height / 2;
            for (int index = 0; index < actors.size(); index++) {
                int x = index % 4 * cellWidth;
                int y = index / 4 * cellHeight;
                graphics.text(font, labels.get(index), x + 5, y + 5, 0xFFFFFFFF, false);
                graphics.entity(state(Minecraft.getInstance(), actors.get(index)), 65, new Vector3f(0, 1.05F, 0),
                        new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(),
                        x + 4, y + 18, x + cellWidth - 4, y + cellHeight - 4);
            }
            frames++;
        }
    }
}
