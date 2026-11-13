package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.client.overlays.AbilityCooldownOverlay;
import com.direwolf20.justdirethings.common.entities.DecoyEntity;
import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

public final class DecoySpikeClient {
    public static boolean capturing;
    public static final List<Draw> draws = new ArrayList<>();
    private static int ticks;
    private static int stage;
    private static boolean finished;

    private DecoySpikeClient() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
    }

    private static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 1800, "Decoy client timed out at " + stage);
            require(!Files.exists(JustDireConnectedServer.report("server.failure.txt")), "Decoy server failed");
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE, "Visible research client");
            if (client.player == null || client.level == null || client.getOverlay() != null
                    || !Files.exists(JustDireConnectedServer.report("case-" + stage + ".ready"))) { return; }
            require(client.getSingleplayerServer() == null, "Integrated authority used");
            var root = client.player.getItemBySlot(EquipmentSlot.LEGS);
            var records = DecoyCandidate.clocks(root);
            if (root.getOrDefault(JustDireDataComponents.FORGE_ENERGY, -1) != 5000
                    || stage == 0 && (records.size() != 1 || !records.getFirst().isactive())
                    || stage == 1 && (records.size() != 1 || records.getFirst().isactive())
                    || stage == 2 && !records.isEmpty()) { return; }
            if (client.screen != null) { client.setScreen(null); }
            if (stage == 0) {
                var entity = client.level.getEntitiesOfClass(DecoyEntity.class, client.player.getBoundingBox().inflate(10)).stream().findFirst().orElse(null);
                if (entity == null) { return; }
                require(entity.getOwnerUUID().orElseThrow().equals(client.player.getUUID())
                                && client.getEntityRenderDispatcher().getRenderer(entity).getClass().getName().contains("Decoy"),
                        "Native Decoy owner/renderer did not reach independent client");
            }
            var before = root.copy();
            var actual = extract(client);
            client.player.setItemSlot(EquipmentSlot.LEGS, DecoyCandidate.hud(root, EquipmentSlot.LEGS));
            List<Draw> nativeDraws;
            try { nativeDraws = extract(client); }
            finally { client.player.setItemSlot(EquipmentSlot.LEGS, root); }
            require(actual.equals(nativeDraws) && ItemStack.matches(root, before), "Native HUD extraction differs or mutated root");
            long own = actual.stream().filter(draw -> draw.texture.getPath().contains("decoy")).count();
            require(own == (stage < 2 ? 2 : 0), "Missing/unexpected native Decoy HUD layers: " + actual);
            if (Boolean.getBoolean("leggings.test.jdtBoots")) {
                require(actual.stream().filter(draw -> draw.texture.getPath().contains("groundstomp")).count() == 2,
                        "Sibling HUD route was lost");
            }
            Files.writeString(JustDireConnectedServer.report("case-" + stage + ".ok"), "TCP root/entity and native HUD extraction matched\n");
            if (++stage == 3) {
                Files.writeString(JustDireConnectedServer.report("decoy-client.txt"), "Native entity owner/renderer; exact active/inactive/clear HUD including sibling offsets; read-only root passed\n");
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

    private static List<Draw> extract(Minecraft client) {
        draws.clear();
        capturing = true;
        try {
            AbilityCooldownOverlay.INSTANCE.render(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), client.getDeltaTracker());
        } finally { capturing = false; }
        return List.copyOf(draws);
    }

    public record Draw(Identifier texture, int x, int y, float u, float v, int width, int height) {}
}
