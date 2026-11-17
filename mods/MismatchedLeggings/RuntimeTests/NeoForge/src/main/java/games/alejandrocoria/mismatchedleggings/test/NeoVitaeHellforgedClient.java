package games.alejandrocoria.mismatchedleggings.test;

import com.breakinblocks.neovitae.client.helper.HellforgedTooltipHelper;
import com.breakinblocks.neovitae.common.datacomponent.NVDataComponents;
import com.breakinblocks.neovitae.common.item.soul.HellforgedHelper;
import com.breakinblocks.neovitae.common.tag.NVTags;
import com.mojang.blaze3d.vertex.PoseStack;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsItemRenderer;
import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.mixin.client.ItemStackRenderStateAccessor;
import games.alejandrocoria.mismatchedleggings.test.NeoVitaeHellforgedLifecycle.Sample;
import games.alejandrocoria.mismatchedleggings.test.render.EquippedClient;
import games.alejandrocoria.mismatchedleggings.test.render.ItemChecks;
import games.alejandrocoria.mismatchedleggings.test.render.RecordingSubmits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native submissions and tooltip producers from saved items; no image gallery or TCP claim. */
public final class NeoVitaeHellforgedClient {
    private static Boolean shift;
    private static boolean finished;
    private static int ticks;
    private static BackupConfirmScreen confirmed;
    private static CompletableFuture<List<Sample>> loading;
    private static List<Sample> samples;
    private static CompletableFuture<Void> reload;
    private static EquipmentLayerRenderer oldRenderer;

    private NeoVitaeHellforgedClient() {
    }

    public static Boolean key(int code) {
        return code == GLFW.GLFW_KEY_LEFT_SHIFT || code == GLFW.GLFW_KEY_RIGHT_SHIFT ? shift : null;
    }

    static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(++ticks < 3600, "Hellforged presentation timed out: " + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                    && client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Automated client must stay hidden/muted");
            if (client.player == null && client.screen instanceof BackupConfirmScreen backup) {
                if (confirmed != backup) {
                    confirmed = backup;
                    backup.children().stream().filter(child -> child instanceof Button button
                                    && button.getMessage().equals(Component.translatable("selectWorld.backupJoinSkipButton")))
                            .map(Button.class::cast).findFirst().orElseThrow().onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                }
                return;
            }
            if (client.player == null || client.level == null || client.getOverlay() != null
                    || CompositionRuleState.synchronizedPreview() == null
                    || ModList.get().isLoaded("mismatchedboots") && !Boots.rulesReady()) {
                return;
            }
            if (loading == null) {
                var server = client.getSingleplayerServer();
                require(server != null, "Missing integrated server");
                loading = server.submit(() -> {
                    try {
                        return NeoVitaeHellforgedLifecycle.load(server);
                    } catch (Exception failure) {
                        throw new CompletionException(failure);
                    }
                });
                return;
            }
            if (!loading.isDone()) {
                return;
            }
            samples = loading.join();
            if (reload != null) {
                if (!reload.isDone()) {
                    return;
                }
                reload.join();
                require(EquippedClient.renderer != oldRenderer, "Actual reload retained equipment renderer");
            }
            verify(client);
            if (reload == null) {
                oldRenderer = EquippedClient.renderer;
                reload = client.reloadResourcePacks();
                return;
            }
            Files.writeString(NeoVitaeHellforgedLifecycle.report("neovitae-hellforged-client.txt"),
                    samples.size() + " saved items: native GUI/both-hand tint submissions; split GUI and full-model fallback; equipped textures/trims/glint\n"
                            + "Normal/Shift/F3+H native root summary/fire/resource/repair/source information; synchronized OWNER and absent-rule controls; read-only full roots\n"
                            + "All repeated after actual resource reload; hidden before window creation and muted; no pixel or TCP acceptance claimed\n");
            finished = true;
            SkeletonChecks.passed("client");
            client.stop();
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static void verify(Minecraft client) throws Exception {
        for (var sample : samples) {
            var root = sample.stack();
            var before = root.copy();
            tooltip(client, sample);
            if (sample.label().equals("native")) {
                require(tints(ItemChecks.resolve(root, ItemDisplayContext.GUI))
                        .contains(0xFF000000 | HellforgedTooltipHelper.HELLFORGED_COLOR), "Native tint oracle is inactive");
            } else {
                render(client, sample);
                equipment(client, sample);
                if (sample.label().equals("legacy")) {
                    try {
                        NeoVitaeHellforgedChecks.preview(sample.slot(), null);
                        require(!HellforgedHelper.isHellforged(root), "Unsynchronized view borrowed server rules");
                        render(client, sample);
                        NeoVitaeHellforgedChecks.preview(sample.slot(), "OWNER");
                        require(!HellforgedHelper.isHellforged(root), "View ignored synchronized OWNER");
                        tooltip(client, sample);
                        render(client, sample);
                    } finally {
                        NeoVitaeHellforgedChecks.preview(sample.slot(), "SHARE");
                    }
                    require(HellforgedHelper.isHellforged(root), "SHARE legacy view was not restored");
                }
            }
            require(ItemStack.matches(before, root), "Presentation changed complete root/history: " + sample.label());
        }
    }

    private static void tooltip(Minecraft client, Sample sample) {
        var root = sample.stack();
        for (boolean expanded : new boolean[]{false, true}) {
            shift = expanded;
            try {
                for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                    var lines = root.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
                    boolean forged = HellforgedHelper.isHellforged(root);
                    require(count(lines, "tooltip.neovitae.hellforged") == (forged ? 1 : 0), "Missing/duplicate native root summary");
                    require(count(lines, "tooltip.neovitae.hellforged.fire_resistant")
                            == (forged && root.has(DataComponents.DAMAGE_RESISTANT) ? 1 : 0), "Fire detail misrepresents root protection");
                    var nativeLines = new ArrayList<Component>();
                    HellforgedTooltipHelper.append(root, nativeLines);
                    require(lines.containsAll(nativeLines), "Native Hellforged translation/style changed");
                    if (root.has(NVDataComponents.SPIRITUS_AMOUNT.get())) {
                        require(count(lines, "tooltip.neovitae.spiritus_stored") == 1
                                && count(lines, "tooltip.neovitae.blood_mending") == 1, "Lost native live resource/repair tooltip");
                    }
                    if (flag.isAdvanced() && !sample.label().equals("native")) {
                        require(lines.stream().anyMatch(line -> line.getString().contains("neovitae:sentient_")), "Missing advanced source ID");
                    }
                }
            } finally {
                shift = null;
            }
        }
    }

    private static long count(List<Component> lines, String key) {
        return lines.stream().filter(line -> translation(line, key)).count();
    }

    private static boolean translation(Component line, String key) {
        return line.getContents() instanceof TranslatableContents text && text.getKey().equals(key)
                || line.getSiblings().stream().anyMatch(child -> translation(child, key));
    }

    private static void render(Minecraft client, Sample sample) {
        var root = sample.stack();
        var sources = NeoVitaeHellforgedChecks.sources(root, sample.slot());
        var expected = new ArrayList<ItemStack>();
        boolean direct = Boolean.TRUE.equals(root.get(NVDataComponents.HELLFORGED.get()))
                && sources.stream().noneMatch(source -> Boolean.TRUE.equals(source.get(NVDataComponents.HELLFORGED.get())));
        for (var source : sources) {
            var view = source.copy();
            if (direct && source.is(NVTags.Items.HELLFORGEABLE)) {
                view.set(NVDataComponents.HELLFORGED.get(), true);
            }
            view.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, root.hasFoil());
            expected.add(view);
            var actual = sample.slot() == EquipmentSlot.LEGS ? MixedLeggingsItemRenderer.sourceView(root, source, root.hasFoil())
                    : Boots.sourceView(root, source);
            require(ItemStack.matches(view, actual), "Temporary render view differs from selected native markers");
        }
        for (var context : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)) {
            var resolved = ItemChecks.resolve(root, context);
            require(!ItemChecks.faces(resolved).isEmpty(), "Resolved mixed item is invisible");
            var reference = new ArrayList<Integer>();
            for (var source : expected) {
                reference.addAll(tints(ItemChecks.resolve(source, context)));
            }
            require(tints(resolved).equals(reference), "Native source tint submission differs: " + sample.label() + '/' + context);
        }
        var gui = sample.slot() == EquipmentSlot.LEGS ? gui(client, root) : Boots.gui(client, root);
        require(gui.clipped() != sample.label().startsWith("fallback"), "Fixture missed ordinary clipping/full-model fallback");
        for (int i = 0; i < 2; i++) {
            require(tints(gui.sources().get(i)).equals(tints(ItemChecks.resolve(expected.get(i), ItemDisplayContext.GUI))), "Split/fallback GUI lost native tint");
        }
    }

    private static Gui gui(Minecraft client, ItemStack root) {
        var prepared = MixedLeggingsItemRenderer.prepareGui(root, MixedLeggingsStructure.validData(root).orElseThrow(),
                root.hasFoil(), client.getItemModelResolver(), client.level, client.player, 0);
        return new Gui(prepared.clipped(), prepared.compose().sources());
    }

    private static List<Integer> tints(ItemStackRenderState state) {
        var fields = (ItemStackRenderStateAccessor) (Object) state;
        var result = new ArrayList<Integer>();
        for (int i = 0; i < fields.mismatchedleggings$getActiveLayerCount(); i++) {
            for (int tint : fields.mismatchedleggings$getLayers()[i].tintLayers()) {
                result.add(tint);
            }
        }
        return result;
    }

    private static void equipment(Minecraft client, Sample sample) {
        require(EquippedClient.renderer != null, "Native equipment renderer missing");
        var model = new HumanoidModel<HumanoidRenderState>(client.getEntityModels().bakeLayer(ModelLayers.ZOMBIE_ARMOR.legs()));
        model.root().getAllParts().forEach(part -> part.visible = false);
        model.root().visible = true;
        model.body.visible = true;
        model.leftLeg.visible = true;
        model.rightLeg.visible = true;
        var state = new HumanoidRenderState();
        var output = new RecordingSubmits();
        var root = sample.stack();
        var type = sample.slot() == EquipmentSlot.LEGS ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS : EquipmentClientInfo.LayerType.HUMANOID;
        if (sample.slot() == EquipmentSlot.LEGS) {
            MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, model, state, type, root, new PoseStack(), output, 15728880, 0);
        } else {
            Boots.equipment(root, model, state, type, output);
        }
        int index = 0;
        for (var source : NeoVitaeHellforgedChecks.sources(root, sample.slot())) {
            var view = source.copy();
            view.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, root.hasFoil());
            var reference = new RecordingSubmits();
            EquippedClient.renderer.renderLayers(type, view.get(DataComponents.EQUIPPABLE).assetId().orElseThrow(), model, state,
                    view, new PoseStack(), reference, 15728880, 0);
            require(!reference.nodes.isEmpty(), "Empty native equipment control");
            for (var expected : reference.nodes) {
                require(index < output.nodes.size(), "Missing mixed equipment layer");
                var actual = output.nodes.get(index++);
                require(expected.type().equals(actual.type()) && expected.color() == actual.color() && expected.sprite() == actual.sprite(),
                        "Native equipped texture/tint/trim/glint differs");
            }
        }
        require(index == output.nodes.size(), "Unexpected mixed equipment layer");
    }

    private record Gui(boolean clipped, List<? extends ItemStackRenderState> sources) {
    }

    /** No sibling class links in the standalone Leggings path. */
    private static final class Boots {
        private static boolean rulesReady() {
            return games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle.synchronizedRules() != null;
        }

        private static ItemStack sourceView(ItemStack root, ItemStack source) {
            return games.alejandrocoria.mismatchedboots.client.render.MixedBootsItemRenderer.sourceView(root, source, root.hasFoil());
        }

        private static Gui gui(Minecraft client, ItemStack root) {
            var data = games.alejandrocoria.mismatchedboots.content.ModContent.getMixedBootsData(root).orElseThrow();
            var prepared = games.alejandrocoria.mismatchedboots.client.render.MixedBootsItemRenderer.prepareGui(root, data, root.hasFoil(),
                    client.getItemModelResolver(), client.level, client.player, 0);
            return new Gui(prepared.clipped(), prepared.compose().sources());
        }

        private static void equipment(ItemStack root, HumanoidModel<HumanoidRenderState> model, HumanoidRenderState state,
                                      EquipmentClientInfo.LayerType type, RecordingSubmits output) {
            require(games.alejandrocoria.mismatchedboots.client.render.MixedBootsEquipmentRenderer.renderLayers(
                    EquippedClient.renderer, type, model, state, root, new PoseStack(), output, 15728880, 0), "Boots equipment fallback");
        }
    }
}
