package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.client.overlays.AbilityCooldownOverlay;
import com.direwolf20.justdirethings.client.screens.ToolSettingScreen;
import com.direwolf20.justdirethings.common.containers.ToolSettingContainer;
import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.ToolRecords.AbilityCooldown;
import games.alejandrocoria.mismatchedleggings.client.compatibility.justdirethings.JustDireSettingsScreenAccess;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Exact native draw witnesses; temporary oracle equipment is test-local and never sent to the server. */
public final class JustDireHudClient {
    public static boolean capturing;
    public static final List<Draw> draws = new ArrayList<>();
    private static int ticks;
    private static int stage;
    private static boolean finished;
    private static boolean projectionChecked;

    private JustDireHudClient() {}

    public static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(++ticks < 1800, "Decoy HUD client timeout at " + stage);
            require(!Files.exists(JustDireConnectedServer.report("server.failure.txt")), "HUD server failed");
            if (client.player == null || client.level == null || client.getOverlay() != null
                    || !Files.exists(JustDireConnectedServer.report("case-" + stage + ".ready"))) {
                return;
            }
            require(client.getSingleplayerServer() == null
                            && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                            && client.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER) == 0,
                    "HUD fixture is not independent/hidden/muted");
            var root = client.player.getItemBySlot(EquipmentSlot.LEGS);
            List<?> clocks = root.getOrDefault(JustDireDataComponents.ABILITY_COOLDOWNS.get(), List.of());
            if (root.getOrDefault(JustDireDataComponents.FORGE_ENERGY, -1) != 5000
                    || stage < 2 && (clocks.size() != 1 || ((AbilityCooldown) clocks.getFirst()).isactive() != (stage == 0))
                    || stage == 2 && !clocks.isEmpty()) {
                return;
            }
            if (client.screen != null) {
                client.setScreen(null);
            }
            if (!projectionChecked) {
                projection(client);
                var menu = new ToolSettingContainer(980, client.player.getInventory(), client.player);
                var screen = new ToolSettingScreen(menu, client.player.getInventory(), Component.literal("HUD independence"));
                require(((JustDireSettingsScreenAccess) screen).mismatchedleggings$jdtReady(), "HUD guard disabled native settings bridge");
                projectionChecked = true;
            }
            compare(client, root, "eclipsealloy", clocks.isEmpty() ? List.of() : List.of((AbilityCooldown) clocks.getFirst()));
            Files.writeString(JustDireConnectedServer.report("case-" + stage + ".ok"), "Native draw/real clock state matched\n");
            if (++stage == 3) {
                Files.writeString(JustDireConnectedServer.report("decoy-hud.txt"), "Exact native active/inactive/clear/clamped draw data, inverse/unequal tiers, foreign/invalid filtering, sibling offsets and read-only roots passed\n");
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

    private static void projection(Minecraft client) {
        for (String tier : List.of("celestigem", "eclipsealloy")) {
            var source = JustDirePassiveChecks.nativeStack(tier);
            var pair = MixedLeggingsFactory.createPair(source, JustDirePassiveChecks.nativeStack("celestigem"));
            for (var root : List.of(pair.primary(), pair.inverse())) {
                for (boolean active : List.of(false, true)) {
                    int maximum = active ? 200 : tier.equals("celestigem") ? 2400 : 1200;
                    root.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(new AbilityCooldown("walkspeed", 17, false),
                            new AbilityCooldown("decoy", Integer.MAX_VALUE, active)));
                    compare(client, root, tier, List.of(new AbilityCooldown("decoy", maximum, active)));
                }
                for (var invalid : List.of(List.<AbilityCooldown>of(), List.of(new AbilityCooldown("decoy", 0, true)),
                        List.of(new AbilityCooldown("decoy", 1, true), new AbilityCooldown("decoy", 1, false)))) {
                    root.set(JustDireDataComponents.ABILITY_COOLDOWNS, invalid);
                    compare(client, root, tier, List.of());
                }
            }
        }
    }

    private static void compare(Minecraft client, ItemStack root, String tier, List<AbilityCooldown> expected) {
        var original = client.player.getItemBySlot(EquipmentSlot.LEGS);
        var before = root.copy();
        boolean fault = System.getProperty("leggings.test.jdtFault", "").equals("hud");
        try {
            client.player.setItemSlot(EquipmentSlot.LEGS, root);
            var actual = extract(client);
            var nativeStack = JustDirePassiveChecks.nativeStack(tier);
            nativeStack.applyComponents(root.getComponents());
            nativeStack.remove(JustDireDataComponents.ABILITY_COOLDOWNS);
            if (!expected.isEmpty()) {
                nativeStack.set(JustDireDataComponents.ABILITY_COOLDOWNS, expected);
            }
            client.player.setItemSlot(EquipmentSlot.LEGS, fault ? ItemStack.EMPTY : nativeStack);
            var oracle = extract(client);
            require(actual.equals(oracle) && ItemStack.matches(root, before), "HUD projection differs from native/changes actual root: " + actual + " != " + oracle);
            require(actual.stream().filter(draw -> draw.texture.getPath().contains("decoy")).count() == (!fault && !expected.isEmpty() ? 2 : 0),
                    "Wrong Decoy layers under healthy/refused hook");
            if (Boolean.getBoolean("leggings.test.jdtBoots")) {
                require(actual.stream().filter(draw -> draw.texture.getPath().contains("groundstomp")).count() == 2, "Leggings HUD disabled sibling HUD");
            }
        } finally {
            client.player.setItemSlot(EquipmentSlot.LEGS, original);
        }
    }

    private static List<Draw> extract(Minecraft client) {
        draws.clear();
        capturing = true;
        try {
            AbilityCooldownOverlay.INSTANCE.render(new GuiGraphicsExtractor(client, new GuiRenderState(), 0, 0), client.getDeltaTracker());
        } finally {
            capturing = false;
        }
        return List.copyOf(draws);
    }

    public record Draw(Identifier texture, int x, int y, float u, float v, int width, int height) {}
}
