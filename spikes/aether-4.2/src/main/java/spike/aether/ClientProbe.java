package spike.aether;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Runtime producer feasibility, not a replacement production formatter or a visual acceptance gallery. */
public final class ClientProbe {
    private static int ticks;
    private static boolean finished;
    private static BackupConfirmScreen confirmed;

    private ClientProbe() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
    }

    private static void tick() {
        if (finished) { return; }
        var client = Minecraft.getInstance();
        var report = new StringBuilder("Aether alpha.4.2 client producer spike\n");
        try {
            Probe.require(++ticks < 2400, "Client timed out: " + client.screen);
            Probe.require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                    && client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Client is not hidden/muted");
            if (client.player == null && client.screen instanceof BackupConfirmScreen backup && confirmed != backup) {
                confirmed = backup;
                backup.children().stream().filter(child -> child instanceof Button button
                                && button.getMessage().equals(Component.translatable("selectWorld.backupJoinSkipButton")))
                        .map(Button.class::cast).findFirst().orElseThrow().onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                return;
            }
            if (client.player == null || client.level == null || client.getOverlay() != null) { return; }
            for (boolean boots : List.of(true, false)) {
                var slot = boots ? EquipmentSlot.FEET : EquipmentSlot.LEGS;
                var source = Probe.item("burrukai_plate" + (boots ? "_boots" : "_leggings"));
                var tier = Probe.component("reinforcement_tier");
                source.set(tier, tier.codec().parse(JsonOps.INSTANCE, JsonParser.parseString("\"SECOND\"")).getOrThrow());
                var roots = Probe.pair(boots, source, new ItemStack(boots ? Items.DIAMOND_BOOTS : Items.DIAMOND_LEGGINGS));
                for (var root : roots) {
                    Probe.clear(client.player);
                    client.player.setItemSlot(EquipmentSlot.HEAD, Probe.item("burrukai_plate_helmet"));
                    client.player.setItemSlot(EquipmentSlot.CHEST, Probe.item("burrukai_plate_chestplate"));
                    client.player.setItemSlot(slot, root);
                    Class.forName("com.aetherteam.aetherii.item.equipment.armor.abilities.BurrukaiPlateArmor")
                            .getMethod("updatePlayerAttributes", PlayerTickEvent.Pre.class).invoke(null, new PlayerTickEvent.Pre(client.player));
                    var modifier = client.player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getModifiers().stream()
                            .filter(value -> value.id().getNamespace().equals("aether_ii")).findFirst().orElseThrow();
                    for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                        var row = Attributes.KNOCKBACK_RESISTANCE.value().toComponent(modifier, flag);
                        TooltipBoundary.attributes = false;
                        var uncontained = lines(client, root, flag);
                        // Only owner-projected roots have the native set; inverse roots deliberately have null.
                        if (root.has(Probe.component("armor_set"))) {
                            Probe.require(uncontained.contains(row), "Relocated native attribute producer did not run");
                        }
                        TooltipBoundary.attributes = true;
                        Probe.require(!lines(client, root, flag).contains(row) && !lines(client, root.copy(), flag).contains(row),
                                "Managed native producer containment failed");
                    }
                    var nativeTier = new ArrayList<Component>();
                    ((TooltipProvider) source.get(tier)).addToTooltip(Item.TooltipContext.EMPTY, nativeTier::add, TooltipFlag.NORMAL, source);
                    Probe.require(!nativeTier.isEmpty(), "Native source tier presentation lost");
                    int before = TooltipBoundary.suppressedTiers;
                    lines(client, root, TooltipFlag.NORMAL);
                    Probe.require(TooltipBoundary.suppressedTiers > before, "Relocated client tier producer was not contained");
                    // A native item of the same type must remain unaffected outside the exact source scope.
                    client.player.setItemSlot(slot, source);
                    var nativeLines = lines(client, source, TooltipFlag.NORMAL);
                    Probe.require(nativeLines.stream().anyMatch(nativeTier::contains), "Native tier was suppressed");
                    int suppressed = TooltipBoundary.suppressedAttributes;
                    TooltipBoundary.source = source;
                    try { lines(client, source, TooltipFlag.NORMAL); }
                    finally { TooltipBoundary.source = null; }
                    Probe.require(TooltipBoundary.suppressedAttributes > suppressed, "Exact source attribute scope missed new producer");
                    report.append(boots ? "Boots" : "Leggings").append(" owner=").append(root.has(Probe.component("armor_set")))
                            .append(": native attributes/tier independent; native source tier reusable; copied/preview root contained\n");
                }
            }
            Probe.require(TooltipBoundary.attributeCalls > 0 && TooltipBoundary.tierCalls > 0, "Producer hooks were never called");
            report.append("attribute calls=").append(TooltipBoundary.attributeCalls).append("; tier calls=")
                    .append(TooltipBoundary.tierCalls).append("; window hidden/muted throughout\n");
            Files.writeString(Probe.REPORT.resolve("client.txt"), report);
            Files.writeString(Probe.REPORT.resolve("client.ok"), "passed\n");
            finished = true;
            client.stop();
        } catch (Throwable failure) {
            finished = true;
            Probe.fail(report, failure);
            client.stop();
        }
    }

    private static List<Component> lines(Minecraft client, ItemStack stack, TooltipFlag flag) {
        return stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
    }
}
