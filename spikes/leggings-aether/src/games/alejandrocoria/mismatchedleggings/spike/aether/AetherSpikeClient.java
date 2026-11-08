package games.alejandrocoria.mismatchedleggings.spike.aether;

import com.aetherteam.aetherii.client.event.hooks.RenderHooks;
import com.aetherteam.aetherii.item.components.AetherIIDataComponents;
import com.aetherteam.aetherii.item.components.Charms;
import com.aetherteam.aetherii.item.equipment.EquipmentUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.datafixers.util.Either;
import games.alejandrocoria.mismatchedleggings.client.render.LeggingsView;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import games.alejandrocoria.mismatchedleggings.test.render.RecordingSubmits;
import games.alejandrocoria.mismatchedthings.api.tooltip.TooltipSeparator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.item.equipment.trim.TrimPatterns;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Actual native layer submissions and final client factories; no resource copies or second renderer. */
public final class AetherSpikeClient {
    private static int ticks;
    private static boolean finished;
    private static BackupConfirmScreen confirmedBackup;
    private static CompletableFuture<Void> reload;
    private static final StringBuilder REPORT = new StringBuilder();

    private AetherSpikeClient() {}

    public static void setup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> tick(Minecraft.getInstance()));
    }

    private static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 2400, "Aether client timed out: " + (client.screen == null ? "none" : client.screen.getClass().getName()));
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE, "Client window visible");
            if (client.player == null && client.screen instanceof BackupConfirmScreen backup && confirmedBackup != backup) {
                confirmedBackup = backup;
                // Native confirmation applies only to this profile's disposable copied world.
                backup.children().stream().filter(child -> child instanceof Button button
                                && button.getMessage().equals(Component.translatable("selectWorld.backupJoinSkipButton")))
                        .map(Button.class::cast).findFirst().orElseThrow().onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                return;
            }
            if (client.level == null || client.player == null || client.getOverlay() != null) { return; }
            if (reload == null) {
                verify(client);
                reload = client.reloadResourcePacks();
            } else if (reload.isDone()) {
                reload.join();
                verify(client);
                Files.writeString(AetherSpike.report("aether-client.txt"), REPORT + "Actual resource reload completed; window stayed hidden\n");
                SkeletonChecks.passed("client");
                finished = true;
                client.stop();
            }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static void verify(Minecraft client) throws Exception {
        require(EquippedClient.renderer != null, "Native equipment renderer was not captured");
        var model = new HumanoidModel<HumanoidRenderState>(client.getEntityModels().bakeLayer(ModelLayers.ZOMBIE_ARMOR.legs()));
        model.root().getAllParts().forEach(part -> part.visible = false);
        model.root().visible = true;
        model.body.visible = true;
        model.leftLeg.visible = true;
        model.rightLeg.visible = true;
        for (String family : AetherSpike.FAMILIES) {
            var source = AetherSpike.item(family + "_leggings");
            source.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3366CC));
            source.set(DataComponents.TRIM, new ArmorTrim(
                    client.level.registryAccess().lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.GOLD),
                    client.level.registryAccess().lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.SENTRY)));
            var roots = AetherSpike.pair(source, new ItemStack(Items.DIAMOND_LEGGINGS));
            for (var root : roots) {
                root.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
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
                    var data = games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure.validData(root).orElseThrow();
                    int offset = compareSource(model, state, data.left().source(), actual, 0, true);
                    require(compareSource(model, state, data.right().source(), actual, offset, false) == actual.nodes.size(),
                            "Extra or missing queued source layers: " + family);
                }
            }
        }
        presentation(client);
        REPORT.append("All six families: both orientations, moving/crouched native model, dye/trim/glint, native material/style submissions and correct selected-leg geometry\n");
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

    private static void presentation(Minecraft client) throws Exception {
        var source = AetherSpike.charmed();
        var roots = AetherSpike.pair(source, AetherSpike.item("burrukai_plate_leggings"));
        var preview = new StringBuilder("Native translations/source preview feasibility, not production tooltip acceptance:\n");
        for (var root : roots) {
            var original = root.copy();
            var normal = root.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL);
            require(normal.stream().filter(TooltipSeparator.INSTANCE::equals).count() == 2, "Product source boundaries lost");
            preview.append("Current root: ").append(normal.stream().map(Component::getString).toList()).append('\n');
            var data = games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure.validData(root).orElseThrow();
            for (var nativeSource : List.of(data.left().source(), data.right().source())) {
                var set = nativeSource.get(AetherIIDataComponents.ARMOR_SET);
                String key = nativeSource.getItem().getDescriptionId() + ".nitrogen_internals.ability.tooltip.";
                var ability = Component.translatable(key + "1");
                require(!ability.getString().equals(key + "1"), "Missing native leggings ability translation");
                for (boolean equipped : List.of(false, true)) {
                    client.player.setItemSlot(EquipmentSlot.HEAD, equipped ? AetherSpike.item("neptune_helmet") : ItemStack.EMPTY);
                    client.player.setItemSlot(EquipmentSlot.CHEST, equipped ? AetherSpike.item("neptune_chestplate") : ItemStack.EMPTY);
                    client.player.setItemSlot(EquipmentSlot.LEGS, equipped ? root : ItemStack.EMPTY);
                    int count = Math.min(3, EquipmentUtil.getArmorCount(client.player, set));
                    int expected = equipped ? (set == source.get(AetherIIDataComponents.ARMOR_SET) ? 2 : 0)
                            + 1 : 0;
                    require(count == expected, "Client native set count differs from server semantics");
                    var progress = Component.translatable(key + "2", Component.literal(count + "/3"));
                    require(!progress.getString().contains(key), "Missing native set-progress translation");
                    preview.append(nativeSource.getItemName().getString()).append(" | ").append(ability.getString())
                            .append(" | ").append(progress.getString()).append('\n');
                }
            }
            require(ItemStack.matches(original, root), "Native tooltip/preview rewrote root state");
        }
        client.player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        client.player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        client.player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);

        var originalSource = source.copy();
        var installedView = source.copy();
        installedView.set(AetherIIDataComponents.CHARMS, new Charms(source.get(AetherIIDataComponents.CHARMS).charmHolders().stream()
                .filter(holder -> !holder.getStack().isEmpty()).map(holder -> new Charms.CharmHolder(holder, holder.getStack().copy())).toList()));
        var graphics = new ArrayList<Either<FormattedText, TooltipComponent>>();
        graphics.add(Either.left(installedView.getItemName()));
        RenderHooks.addCharmTooltip(installedView, graphics);
        var nativeGrid = graphics.stream().flatMap(entry -> entry.right().stream()).findFirst().orElseThrow();
        var clientGrid = ClientTooltipComponent.create(nativeGrid);
        require(clientGrid.getWidth(client.font) > 0 && clientGrid.getHeight(client.font) > 0, "Native charm grid factory failed");
        require(ItemStack.matches(originalSource, source), "Installed-only graphic view rewrote source holders");
        preview.append("Native charm grid factory: ").append(clientGrid.getClass().getName()).append(';')
                .append(clientGrid.getWidth(client.font)).append('x').append(clientGrid.getHeight(client.font)).append('\n');
        Files.writeString(AetherSpike.report("aether-tooltip-preview.txt"), preview);
    }
}
