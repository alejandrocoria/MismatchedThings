package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/** Actual native client/transport rejection probe, not a mixed settings or ability implementation. */
final class NeoForgeJustDireGroundStompClientSpike {
    private static int ticks;
    private static int phase;
    private static int wait;
    private static boolean finished;
    private static Future<?> job;
    private static ItemStack expected;

    private NeoForgeJustDireGroundStompClientSpike() {}

    static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            if (++ticks > 3_600) { throw new AssertionError("Ground Stomp client timeout phase=" + phase + "; screen=" + client.screen); }
            if (client.player == null || client.level == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) { return; }
            if (job != null && !job.isDone()) { return; }
            if (job != null) { job.get(); job = null; }
            var server = client.getSingleplayerServer();
            require(server != null, "Integrated server missing");
            switch (phase) {
                case 0 -> {
                    require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                            && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                            "Spike client is not hidden/muted");
                    setup(client, false, false); phase = 1;
                }
                case 1 -> {
                    if (client.screen != null || !ItemStack.matches(expected, client.player.getItemBySlot(EquipmentSlot.FEET))) { return; }
                    NeoForge.EVENT_BUS.post(new InputEvent.Key(new KeyEvent(GLFW.GLFW_KEY_G, 0, 0), GLFW.GLFW_PRESS));
                    phase = 2;
                }
                case 2 -> {
                    if (charge(client.player.getItemBySlot(EquipmentSlot.FEET)) != 1_000) { return; }
                    var id = client.player.getUUID();
                    job = server.submit(() -> {
                        var owner = server.getPlayerList().getPlayer(id);
                        require(owner != null && charge(owner.getItemBySlot(EquipmentSlot.FEET)) == 1_000,
                                "Native actual key packet did not debit server feet once");
                    });
                    phase = 3;
                }
                case 3 -> { setup(client, false, true); phase = 4; }
                case 4 -> {
                    if (!ItemStack.matches(expected, client.player.getMainHandItem()) || client.screen != null) { return; }
                    openNativeSettings(); phase = 5;
                }
                case 5 -> {
                    if (!nativeScreen(client)) { return; }
                    var tool = field(client.screen, "tool");
                    require(tool instanceof ItemStack stack && stack.is(expected.getItem())
                            && !((Map<?, ?>) field(client.screen, "sliders")).isEmpty(),
                            "Native screen control did not select item/build tier controls");
                    client.screen.onClose(); phase = 6;
                }
                case 6 -> {
                    if (client.screen != null) { return; }
                    setup(client, true, false); phase = 7;
                }
                case 7 -> {
                    if (!ItemStack.matches(expected, client.player.getItemBySlot(EquipmentSlot.FEET))) { return; }
                    NeoForge.EVENT_BUS.post(new InputEvent.Key(new KeyEvent(GLFW.GLFW_KEY_G, 0, 0), GLFW.GLFW_PRESS));
                    wait = 0; phase = 8;
                }
                case 8 -> {
                    if (++wait < 20) { return; }
                    var id = client.player.getUUID();
                    job = server.submit(() -> {
                        var owner = server.getPlayerList().getPlayer(id);
                        require(owner != null && ItemStack.matches(expected, owner.getItemBySlot(EquipmentSlot.FEET)),
                                "Native input unexpectedly wrote/activated mixed FEET");
                    });
                    phase = 9;
                }
                case 9 -> { setup(client, true, true); phase = 10; }
                case 10 -> {
                    if (!ItemStack.matches(expected, client.player.getMainHandItem()) || client.screen != null) { return; }
                    openNativeSettings(); phase = 11;
                }
                case 11 -> {
                    if (!nativeScreen(client)) { return; }
                    require(((ItemStack) field(client.screen, "tool")).isEmpty()
                            && ((Map<?, ?>) field(client.screen, "sliders")).isEmpty(),
                            "Native screen unexpectedly selected/configured mixed-only item");
                    client.screen.onClose();
                    NetworkAcceptanceFiles.write("just-dire-ground-stomp-client-spike-report.txt",
                            "native equipped custom key press through actual client event bus and server packet: 6000 -> 1000 FE once; "
                                    + "native settings open through actual menu packet selects native main-hand boot and builds sliders; "
                                    + "mixed equipped key press leaves server root unchanged; mixed-only settings menu opens but selects no tool/builds no sliders; "
                                    + "normal integrated equipment/inventory packets; hidden/muted; no custom mixed UI/input adapter or physical/manual acceptance\n");
                    NetworkAcceptanceFiles.write("just-dire-ground-stomp-client-spike-success.txt", "neoforge\n");
                    finished = true;
                }
                default -> throw new AssertionError("Unknown spike phase");
            }
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("just-dire-ground-stomp-client-spike", failure); finished = true;
        }
        if (finished) { client.stop(); }
    }

    private static void setup(Minecraft client, boolean mixed, boolean mainHand) throws ReflectiveOperationException {
        var boot = NeoForgeJustDireResourcesTest.boots("eclipsealloy", mixed ? 12_000 : 6_000);
        JustDireAttributeTestSupport.set(boot, "groundstomp_upgrade_installed", true);
        var bindingType = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding");
        var binding = bindingType.getConstructor(String.class, int.class, boolean.class, boolean.class)
                .newInstance("groundstomp", GLFW.GLFW_KEY_G, false, true);
        JustDireAttributeTestSupport.set(boot, "groundstomp_bindingmode", 2);
        JustDireAttributeTestSupport.set(boot, "ability_bindings", List.of(binding));
        expected = mixed ? MixedBootsFactory.createPair(boot, new ItemStack(Items.DIAMOND_BOOTS)).primary() : boot;
        var id = client.player.getUUID(); var stack = expected.copy();
        job = client.getSingleplayerServer().submit(() -> {
            var owner = client.getSingleplayerServer().getPlayerList().getPlayer(id);
            require(owner != null, "Real server owner missing");
            owner.setGameMode(GameType.SURVIVAL);
            owner.getInventory().clearContent();
            owner.setItemSlot(mainHand ? EquipmentSlot.MAINHAND : EquipmentSlot.FEET, stack);
            owner.inventoryMenu.broadcastChanges();
        });
    }

    private static void openNativeSettings() throws ReflectiveOperationException {
        var payload = Class.forName("com.direwolf20.justdirethings.common.network.data.ToolSettingsGUIPayload")
                .getConstructor().newInstance();
        ClientPacketDistributor.sendToServer((CustomPacketPayload) payload);
    }

    private static boolean nativeScreen(Minecraft client) {
        return client.screen != null && client.screen.getClass().getName().equals("com.direwolf20.justdirethings.client.screens.ToolSettingScreen");
    }

    private static Object field(Object owner, String name) throws ReflectiveOperationException {
        var field = owner.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(owner);
    }

    private static int charge(ItemStack root) { return root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
