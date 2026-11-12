package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.client.screens.ToolSettingScreen;
import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.common.items.interfaces.ToolRecords;
import games.alejandrocoria.mismatchedleggings.client.compatibility.justdirethings.JustDireSettingsScreenAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Actual native screen against a separate dedicated endpoint; no integrated-server access. */
public final class JustDireSettingsClientChecks {
    private static int ticks;
    private static int stage;
    private static int originalMenu;
    private static boolean sent;
    private static boolean finished;

    private JustDireSettingsClientChecks() {}

    public static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 4200, "Native settings client timeout at " + stage);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                    && client.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER) == 0, "Client not hidden/muted");
            require(!Files.exists(JustDireConnectedServer.report("server.failure.txt")), "Settings server failed");
            if (client.player == null || client.level == null || client.getOverlay() != null) { return; }
            require(client.getSingleplayerServer() == null, "Settings used integrated-server authority");
            if (!(client.screen instanceof ToolSettingScreen screen)) { return; }
            boolean fault = System.getProperty("leggings.test.jdtFault", "").equals("screen");
            var access = (JustDireSettingsScreenAccess) screen;
            if (stage == 0) {
                if (!sent) {
                    originalMenu = screen.getMenu().containerId;
                    select(screen, 0);
                    if (fault) {
                        require(!access.mismatchedleggings$jdtReady() && buttons(screen).isEmpty(), "Missing owned seam still admitted mixed UI");
                    } else {
                        widgets(screen);
                        screen.setSetting("walkspeed", 3);
                        screen.toggleSetting("phase");
                        // Exercise native mouse capture, including its row-local binding record.
                        field(screen, "shownAbilityButton").set(screen, icon(screen, Ability.WALKSPEED));
                        field(screen, "requireEquipped").setBoolean(screen, false);
                        field(screen, "bindingEnabled").setBoolean(screen, true);
                        screen.mouseClicked(mouse(4), false);
                        require(!field(screen, "bindingEnabled").getBoolean(screen), "Native mouse capture stayed active");
                    }
                    sent = true;
                }
                if (!Files.exists(JustDireConnectedServer.report("case-0.ready"))) { return; }
                done(0);
            } else if (stage == 1) {
                var root = client.player.getInventory().getItem(0);
                if (root.getOrDefault(JustDireDataComponents.FORGE_ENERGY, 0) != 888) { return; }
                if (!sent && !fault) { screen.setSetting("walkspeed", 4); sent = true; }
                if (!fault && root.getOrDefault(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED), 0) != 4) { return; }
                done(1);
            } else if (stage == 2) {
                if (!sent) {
                    if (!fault) { field(screen, "bindingEnabled").setBoolean(screen, true); }
                    select(screen, 1);
                    require(!field(screen, "bindingEnabled").getBoolean(screen), "Native slot switch did not cancel mixed capture");
                    require(buttons(screen).containsValue(Ability.LAVAREPAIR) && !buttons(screen).containsValue(Ability.PHASE), "Native controls changed");
                    screen.toggleSetting("walkspeed");
                    sent = true;
                }
                if (client.player.getInventory().getItem(1).getOrDefault(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.WALKSPEED), true)) { return; }
                done(2);
            } else if (stage == 3) {
                if (!sent) {
                    select(screen, 2);
                    if (!fault) {
                        widgets(screen);
                        require(access.mismatchedleggings$jdtTool().getOrDefault(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.PHASE), true), "Primary toggle leaked to inverse");
                        screen.setSetting("walkspeed", 2);
                    }
                    sent = true;
                }
                if (!fault && client.player.getInventory().getItem(2).getOrDefault(JustDireDataComponents.ABILITY_VALUES.get(Ability.WALKSPEED), 0) != 2) { return; }
                done(3);
            } else if (stage == 4) {
                if (!sent) {
                    if (Boolean.getBoolean("leggings.test.jdtBoots")) {
                        select(screen, 3);
                        var sibling = screen.getClass().getMethod("mismatchedboots$jdtReady");
                        require((boolean) sibling.invoke(screen), "Leggings hooks disabled Boots guard");
                        require(buttons(screen).containsValue(Ability.JUMPBOOST) && !buttons(screen).containsValue(Ability.WALKSPEED), "Sibling controls conflated");
                        screen.setSetting("jumpboost", 2);
                    } else { select(screen, 1); }
                    sent = true;
                }
                if (Boolean.getBoolean("leggings.test.jdtBoots") && client.player.getInventory().getItem(3)
                        .getOrDefault(JustDireDataComponents.ABILITY_VALUES.get(Ability.JUMPBOOST), 0) != 2) { return; }
                done(4);
                client.player.closeContainer();
            } else if (stage == 5) {
                if (screen.getMenu().containerId == originalMenu) { return; }
                if (!sent) {
                    select(screen, EquipmentSlot.LEGS.getIndex(Inventory.INVENTORY_SIZE));
                    if (!fault) {
                        widgets(screen);
                        screen.setSetting("runspeed", 3);
                    }
                    sent = true;
                }
                if (!fault && client.player.getItemBySlot(EquipmentSlot.LEGS).getOrDefault(JustDireDataComponents.ABILITY_VALUES.get(Ability.RUNSPEED), 0) != 3) { return; }
                done(5);
            } else if (stage == 6) {
                if (!sent) {
                    select(screen, Inventory.SLOT_OFFHAND);
                    if (!fault) { screen.toggleSetting("swimspeed"); }
                    sent = true;
                }
                if (!fault && client.player.getOffhandItem().getOrDefault(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.SWIMSPEED), true)) { return; }
                done(6);
            } else if (stage == 7) {
                if (!sent) {
                    select(screen, 0);
                    if (!fault) {
                        screen.setSetting("walkspeed", 5); // Queued before SELECT's reply, not an authorized edit yet.
                        select(screen, 1);
                        select(screen, 0);
                        // A refresh cancels native capture; old queued edits must not follow a new selection.
                        field(screen, "bindingEnabled").setBoolean(screen, true);
                        screen.refreshButtons();
                        require(!field(screen, "bindingEnabled").getBoolean(screen), "Widget refresh retained capture");
                        widgets(screen);
                        // Escape on an unbound row must retain its location preference, not reset to equipped-only.
                        field(screen, "shownAbilityButton").set(screen, icon(screen, Ability.RUNSPEED));
                        field(screen, "requireEquipped").setBoolean(screen, false);
                        field(screen, "bindingEnabled").setBoolean(screen, true);
                        screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
                        require(!field(screen, "requireEquipped").getBoolean(screen), "Escape erased unbound location preference");
                        field(screen, "bindingEnabled").setBoolean(screen, true);
                        screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_G, 0, 0));
                        require(!field(screen, "bindingEnabled").getBoolean(screen), "Native keyboard capture stayed active");
                    }
                    sent = true;
                }
                if (!fault && client.player.getInventory().getItem(0)
                        .getOrDefault(JustDireDataComponents.ABILITY_BINDINGS.get(), List.<ToolRecords.AbilityBinding>of()).stream()
                        .noneMatch(record -> record.abilityName().equals("runspeed") && record.key() == GLFW.GLFW_KEY_G
                                && !record.isMouse() && !record.requireEquipped())) { return; }
                done(7);
                finished = true;
                Files.writeString(JustDireConnectedServer.report("client.ok"), "Native screen and authoritative received edits passed\n");
                client.player.closeContainer();
                client.stop();
            }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static void done(int index) throws Exception {
        Files.writeString(JustDireConnectedServer.report("case-" + index + ".ok"), "Native GUI stage observed\n");
        stage++;
        sent = false;
    }

    @SuppressWarnings("unchecked")
    private static Map<Button, Ability> buttons(ToolSettingScreen screen) throws Exception {
        return (Map<Button, Ability>) field(screen, "buttonToAbilityMap").get(screen);
    }

    private static Button icon(ToolSettingScreen screen, Ability ability) throws Exception {
        return buttons(screen).entrySet().stream().filter(entry -> entry.getValue() == ability).findFirst().orElseThrow().getKey();
    }

    private static void widgets(ToolSettingScreen screen) throws Exception {
        require(((JustDireSettingsScreenAccess) screen).mismatchedleggings$jdtReady(), "Local transformed guard not published");
        require(buttons(screen).values().containsAll(List.of(Ability.WALKSPEED, Ability.RUNSPEED, Ability.SWIMSPEED, Ability.PHASE))
                && buttons(screen).size() == 4, "Unsupported/missing mixed ability widgets");
        var sliders = (Map<?, ?>) field(screen, "sliders").get(screen);
        require(sliders.size() == 3 && !sliders.containsKey(icon(screen, Ability.PHASE)), "Movement sliders/Phase presentation changed");
    }

    private static void select(ToolSettingScreen screen, int index) throws Exception {
        var inventory = Minecraft.getInstance().player.getInventory();
        var slot = screen.getMenu().slots.stream().filter(candidate -> candidate.getSlotIndex() == index && candidate.container == inventory)
                .findFirst().orElseThrow();
        field(screen, "hoveredSlot").set(screen, slot);
        screen.mouseClicked(mouse(0), false);
        field(screen, "hoveredSlot").set(screen, null);
    }

    private static MouseButtonEvent mouse(int button) { return new MouseButtonEvent(0, 0, new MouseButtonInfo(button, 0)); }

    private static Field field(Object target, String name) throws Exception {
        for (Class<?> owner = target.getClass(); owner != null; owner = owner.getSuperclass()) {
            try {
                var field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException absent) { /* Native inherited UI state. */ }
        }
        throw new NoSuchFieldException(name);
    }
}
