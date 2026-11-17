package games.alejandrocoria.mismatchedleggings.test;

import com.breakinblocks.neovitae.common.item.soul.HellforgedHelper;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.client.render.MixedBootsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.client.render.MixedLeggingsEquipmentRenderer;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.mixin.client.ItemStackRenderStateAccessor;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Existing hidden-window fixture, native item/equipment/tooltip consumers; no screenshot gallery. */
public final class NeoVitaeUpdateClient {
    private static boolean capturing;
    private static boolean shift;
    private static boolean finished;
    private static int ticks;
    private static CompletableFuture<Void> reload;
    private static EquipmentLayerRenderer oldRenderer;
    private static BackupConfirmScreen confirmed;

    private NeoVitaeUpdateClient() {}

    public static void setup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> tick(Minecraft.getInstance()));
    }

    public static Boolean key(int code) {
        return capturing ? shift && (code == GLFW.GLFW_KEY_LEFT_SHIFT || code == GLFW.GLFW_KEY_RIGHT_SHIFT) : null;
    }

    private static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 3600, "Research client timed out: " + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                    && client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Research client must stay hidden/muted");
            if (client.player == null && client.screen instanceof BackupConfirmScreen backup) {
                if (confirmed != backup) {
                    confirmed = backup;
                    // Only this profile's disposable server-world copy, never an owner world.
                    backup.children().stream().filter(child -> child instanceof Button button
                                    && button.getMessage().equals(Component.translatable("selectWorld.backupJoinSkipButton")))
                            .map(Button.class::cast).findFirst().orElseThrow()
                            .onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                }
                return;
            }
            if (client.player == null || client.level == null || client.getOverlay() != null) { return; }
            if (reload != null) {
                if (!reload.isDone()) { return; }
                reload.join();
                require(EquippedClient.renderer != oldRenderer, "Resource reload retained native renderer");
            }
            var report = verify(client);
            if (reload == null) {
                oldRenderer = EquippedClient.renderer;
                reload = client.reloadResourcePacks();
                return;
            }
            Files.writeString(NeoVitaeUpdateSpike.report("presentation.txt"), report + "Repeated after actual resource reload; native render submissions, not pixel certification or TCP acceptance\n");
            SkeletonChecks.passed("client");
            finished = true;
            client.stop();
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static String verify(Minecraft client) throws Exception {
        var json = JsonParser.parseString(Files.readString(Path.of(System.getProperty("nv.spike.expected")))).getAsJsonArray();
        var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var report = new StringBuilder();
        int directWithoutTint = 0;
        for (var entry : json) {
            var object = entry.getAsJsonObject();
            var root = ItemStack.CODEC.parse(ops, object.get("stack")).getOrThrow();
            var before = root.copy();
            String label = object.get("label").getAsString();
            for (boolean expanded : List.of(false, true)) {
                for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                    capturing = true;
                    shift = expanded;
                    try {
                        var lines = root.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
                        require(lines.stream().filter(line -> translation(line, "tooltip.neovitae.hellforged")).count()
                                == (HellforgedHelper.isHellforged(root) ? 1 : 0), "Native Hellforged root summary absent/duplicated");
                    } finally { capturing = false; }
                }
            }
            var sources = NeoVitaeUpdateSpike.sources(root);
            boolean sourceTint = sources.isEmpty() ? HellforgedHelper.isHellforged(root)
                    : sources.stream().anyMatch(HellforgedHelper::isHellforged);
            for (var context : List.of(ItemDisplayContext.GUI, ItemDisplayContext.FIRST_PERSON_LEFT_HAND, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)) {
                var resolved = ItemChecks.resolve(root, context);
                var faces = ItemChecks.faces(resolved);
                require(!faces.isEmpty(), "Native/mixed item has no geometry");
                boolean orange = tints(resolved).contains(-26006);
                require(orange == sourceTint, "Source-native Hellforged tint differs: " + label + ' ' + tints(resolved));
            }
            if (!sources.isEmpty()) {
                equipment(client, root, sources);
                if (label.contains("-direct-") && !sourceTint) { directWithoutTint++; }
            }
            require(ItemStack.matches(before, root), "Presentation changed complete root/source state");
        }
        report.append("Native and mixed normal/Shift/F3+H root summaries; no duplicate native Hellforged label; GUI/both hands preserve source-native tint; native equipped layers; unchanged histories\n")
                .append("Direct-root Hellforged items with unchanged, untinted native source icons: ").append(directWithoutTint).append('\n');
        return report.toString();
    }

    private static Set<Integer> tints(ItemStackRenderState state) throws Exception {
        var fields = (ItemStackRenderStateAccessor) (Object) state;
        var result = new HashSet<Integer>();
        for (int i = 0; i < fields.mismatchedleggings$getActiveLayerCount(); i++) {
            for (int color : fields.mismatchedleggings$getLayers()[i].tintLayers()) { result.add(color); }
        }
        return result;
    }

    private static void equipment(Minecraft client, ItemStack root, List<ItemStack> sources) {
        require(EquippedClient.renderer != null, "Native equipment renderer missing");
        var model = new HumanoidModel<HumanoidRenderState>(client.getEntityModels().bakeLayer(ModelLayers.ZOMBIE_ARMOR.legs()));
        model.root().getAllParts().forEach(part -> part.visible = false);
        model.root().visible = true;
        model.body.visible = true;
        model.leftLeg.visible = true;
        model.rightLeg.visible = true;
        var state = new HumanoidRenderState();
        var output = new RecordingSubmits();
        boolean legs = MixedLeggingsStructure.isMixedItem(root);
        var type = legs ? EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS : EquipmentClientInfo.LayerType.HUMANOID;
        if (legs) { MixedLeggingsEquipmentRenderer.render(EquippedClient.renderer, model, state, type, root, new PoseStack(), output, 15728880, 0); }
        else { require(MixedBootsEquipmentRenderer.renderLayers(EquippedClient.renderer, type, model, state, root, new PoseStack(), output, 15728880, 0), "Boots equipment fallback"); }
        int index = 0;
        for (var source : sources) {
            var view = source.copy();
            view.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, root.hasFoil());
            var reference = new RecordingSubmits();
            EquippedClient.renderer.renderLayers(type, view.get(DataComponents.EQUIPPABLE).assetId().orElseThrow(),
                    model, state, view, new PoseStack(), reference, 15728880, 0);
            require(!reference.nodes.isEmpty(), "Native equipment control empty");
            for (var expected : reference.nodes) {
                require(index < output.nodes.size(), "Missing native equipment layer");
                var actual = output.nodes.get(index++);
                require(expected.type().equals(actual.type()) && expected.color() == actual.color() && expected.sprite() == actual.sprite(),
                        "Native equipment texture/tint/trim differs");
            }
        }
        require(index == output.nodes.size(), "Extra mixed equipment layer");
    }

    private static boolean translation(Component line, String key) {
        return line.getContents() instanceof TranslatableContents text && text.getKey().equals(key)
                || line.getSiblings().stream().anyMatch(child -> translation(child, key));
    }
}
