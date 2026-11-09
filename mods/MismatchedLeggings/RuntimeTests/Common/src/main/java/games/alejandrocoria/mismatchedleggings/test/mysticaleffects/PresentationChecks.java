package games.alejandrocoria.mismatchedleggings.test.mysticaleffects;

import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsItemRenderer;
import games.alejandrocoria.mismatchedleggings.compatibility.mysticalagriculture.MysticalAugments;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import games.alejandrocoria.mismatchedleggings.test.render.ItemChecks;
import games.alejandrocoria.mismatchedleggings.test.render.RecordingSubmits;
import games.alejandrocoria.mismatchedthings.api.tooltip.TooltipSeparator;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.ArmorStandRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPatterns;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Compares native component trees/styles and native material submissions, without a new producer. */
final class PresentationChecks {
    private PresentationChecks() {}

    static void verify(Minecraft client) throws Exception {
        tooltip(client);
        rendering(client);
    }

    private static void tooltip(Minecraft client) {
        var weak = NativeAugments.item("awakened_supremium_leggings", "HEALTH_BOOST_I", "STEP_ASSIST");
        var strong = NativeAugments.item("awakened_supremium_leggings", "HEALTH_BOOST_V", "SPEED_III");
        weak.set(DataComponents.CUSTOM_NAME, Component.literal("Original purchases"));
        var pair = MixedLeggingsFactory.createPair(weak, strong);
        Component heading = Component.translatable("tooltip.mysticalagriculture.augments").withStyle(ChatFormatting.GRAY);
        var context = Item.TooltipContext.of(client.level);
        try {
            for (boolean shift : List.of(false, true)) {
                client.options.keyShift.setDown(shift); client.player.setShiftKeyDown(shift);
                for (var root : List.of(pair.primary(), pair.inverse())) {
                    var before = root.copy();
                    var data = MixedLeggingsStructure.validData(root).orElseThrow();
                    for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                        var lines = root.getTooltipLines(context, client.player, flag);
                        int first = lines.indexOf(TooltipSeparator.INSTANCE), last = lines.lastIndexOf(TooltipSeparator.INSTANCE);
                        require(first > 0 && last > first, "Source/root separation lost");
                        var left = nativeLines(client, data.left().source());
                        var right = nativeLines(client, data.right().source());
                        require(Collections.indexOfSubList(lines.subList(0, first), left) >= 0
                                && Collections.indexOfSubList(lines.subList(first + 1, last), right) >= 0, "Native purchase names/styles replaced by union");
                        require(lines.stream().filter(heading::equals).count() == 2
                                && lines.subList(last + 1, lines.size()).stream().noneMatch(line -> left.contains(line) || right.contains(line)), "Duplicate root augment list");
                        require(lines.stream().filter(line -> line.getString().equals("mysticalagriculture:awakened_supremium_leggings")).count() == (flag.isAdvanced() ? 2 : 0), "F3+H source IDs changed");
                        require(ItemStack.matches(before, root), "Tooltip mutated complete history");
                    }
                }
            }
        } finally { client.options.keyShift.setDown(false); client.player.setShiftKeyDown(false); }
        var hidden = weak.copy(); hidden.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new java.util.LinkedHashSet<>()));
        var root = MixedLeggingsFactory.createPair(hidden, strong).primary();
        root.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        require(root.getTooltipLines(context, client.player, TooltipFlag.NORMAL).stream().filter(heading::equals).count() == 1, "Hidden source leaked");
        root.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new java.util.LinkedHashSet<>()));
        require(root.getTooltipLines(context, client.player, TooltipFlag.NORMAL).isEmpty(), "Hidden root leaked");
        for (String path : MysticalAugments.LEGGINGS) {
            var source = NativeAugments.item(path, "LUCK_I");
            var ordinary = MixedLeggingsFactory.createPair(source, new ItemStack(Items.IRON_LEGGINGS));
            for (var stack : List.of(ordinary.primary(), ordinary.inverse())) {
                var lines = stack.getTooltipLines(context, null, TooltipFlag.NORMAL);
                require(lines.stream().filter(heading::equals).count() == 1, "Ordinary source invented native heading");
                require(Collections.indexOfSubList(lines, nativeLines(client, source)) >= 0, "Tier source lost native style/list");
            }
        }
    }

    private static List<Component> nativeLines(Minecraft client, ItemStack source) {
        var lines = new ArrayList<Component>();
        int slots = (Integer) NativeAugments.invoke(source.getItem(), "getAugmentSlots", new Class<?>[0]);
        NativeAugments.invoke(NativeAugments.type("api.util.AugmentUtils"), "addAugmentListToTooltip",
                new Class<?>[]{Consumer.class, ItemStack.class, int.class, Player.class}, (Consumer<Component>) lines::add, source, slots, client.player);
        return lines;
    }

    private static ItemStack leather(Minecraft client) {
        var stack = new ItemStack(Items.LEATHER_LEGGINGS);
        stack.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3366CC));
        stack.set(DataComponents.TRIM, new ArmorTrim(
                client.level.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.GOLD),
                client.level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.SENTRY)));
        return stack;
    }

    private static void rendering(Minecraft client) {
        require(EquippedClient.renderer != null, "Native renderer not observed");
        var model = new HumanoidModel<HumanoidRenderState>(client.getEntityModels().bakeLayer(ModelLayers.ZOMBIE_ARMOR.legs()));
        model.root().getAllParts().forEach(part -> part.visible = false);
        model.root().visible = true; model.body.visible = true; model.leftLeg.visible = true; model.rightLeg.visible = true;
        for (String path : MysticalAugments.LEGGINGS) {
            var source = NativeAugments.item(path, "LUCK_I");
            source.set(DataComponents.TRIM, leather(client).get(DataComponents.TRIM));
            var pair = MixedLeggingsFactory.createPair(source, leather(client));
            for (var root : List.of(pair.primary(), pair.inverse())) {
                for (boolean foil : List.of(false, true)) {
                    root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
                    var before = root.copy();
                    var data = MixedLeggingsStructure.validData(root).orElseThrow();
                    var gui = MixedLeggingsItemRenderer.prepareGui(data, foil, client.getItemModelResolver(), client.level, client.player, 0);
                    require(gui.clipped() && gui.compose().sources().size() == 2, "Native item fallback " + path);
                    for (var context : ItemDisplayContext.values()) {
                        var faces = ItemChecks.faces(ItemChecks.resolve(root, context));
                        require(!faces.isEmpty() && faces.stream().allMatch(face -> face.foil() == foil), "Native item glint missing");
                        int side = 0;
                        for (var history : List.of(data.left().source(), data.right().source())) {
                            var textures = ItemChecks.faces(ItemChecks.resolve(history, context)).stream().map(ItemChecks.Face::texture).collect(java.util.stream.Collectors.toSet());
                            var selected = faces.stream().filter(face -> textures.contains(face.texture())).toList();
                            require(!selected.isEmpty(), "Native item texture missing");
                            for (var face : selected) {
                                for (var vertex : face.vertices()) { require(side == 0 ? vertex.x >= .4999F : vertex.x <= .5001F, "Wrong item side"); }
                            }
                            side++;
                        }
                    }
                    for (boolean crouch : List.of(false, true)) {
                        var state = new HumanoidRenderState(); state.entityType = EntityType.ZOMBIE; state.isCrouching = crouch;
                        state.walkAnimationPos = 1.2F; state.walkAnimationSpeed = .8F;
                        var actual = new RecordingSubmits();
                        MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, model, state, EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                                root, new PoseStack(), actual, 15728880, 0);
                        require(!actual.nodes.isEmpty(), "Native equipment missing");
                        int offset = 0, side = 0;
                        for (var history : List.of(data.left().source(), data.right().source())) {
                            history.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, foil);
                            var nativeOutput = new RecordingSubmits();
                            EquippedClient.renderer.renderLayers(EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS,
                                    history.get(DataComponents.EQUIPPABLE).assetId().orElseThrow(), model, state, history, new PoseStack(), nativeOutput, 15728880, 0);
                            require(!nativeOutput.nodes.isEmpty(), "Missing native reference");
                            for (var node : nativeOutput.nodes) {
                                var result = actual.nodes.get(offset++);
                                require(node.type().equals(result.type()) && node.color() == result.color() && node.sprite() == result.sprite()
                                        && result.model() instanceof LeggingsView<?>, "Native dye/trim/glint lost");
                                var view = (LeggingsView<?>) result.model();
                                require(view.root().getChild("leg") == (side == 0 ? model.leftLeg : model.rightLeg)
                                        && view.root().getChild("waist").getAllParts().stream().anyMatch(part -> !part.isEmpty()), "Native moving leg/waist missing");
                            }
                            side++;
                        }
                        require(offset == actual.nodes.size(), "Unexpected material submission");
                    }
                    require(ItemStack.matches(before, root), "Rendering mutated complete source history");
                }
            }
        }
    }

    static final class Panel extends Screen {
        int frames;
        private final List<ItemStack> samples = new ArrayList<>();

        Panel(Minecraft client) {
            super(Component.literal("Mystical Agriculture production presentation"));
            for (String path : MysticalAugments.LEGGINGS) {
                var source = NativeAugments.item(path, "LUCK_I");
                source.set(DataComponents.TRIM, leather(client).get(DataComponents.TRIM));
                var pair = MixedLeggingsFactory.createPair(source, leather(client));
                for (var root : List.of(source, pair.primary(), pair.inverse())) {
                    root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true); samples.add(root);
                }
            }
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF20252C);
            graphics.text(font, "Native / mixed / inverse - dye, gold trim and glint", 8, 6, 0xFFFFFFFF, false);
            int rowHeight = (height - 22) / 6, cellWidth = width / 3;
            for (int index = 0; index < samples.size(); index++) {
                int x = index % 3 * cellWidth, y = 22 + index / 3 * rowHeight;
                var stack = samples.get(index);
                graphics.item(stack, x + 8, y + 8);
                var stand = new ArmorStandRenderState(); stand.entityType = EntityType.ARMOR_STAND;
                stand.boundingBoxHeight = 1.9F; stand.boundingBoxWidth = .6F; stand.legsEquipment = stack;
                graphics.entity(stand, Math.min(30, rowHeight / 2), new Vector3f(0, 1, 0),
                        new Quaternionf().rotateZ((float) Math.PI).rotateY(index % 3 == 2 ? (float) Math.PI : 0),
                        new Quaternionf(), x + 35, y, x + cellWidth - 3, y + rowHeight);
            }
            frames++;
        }
    }
}
