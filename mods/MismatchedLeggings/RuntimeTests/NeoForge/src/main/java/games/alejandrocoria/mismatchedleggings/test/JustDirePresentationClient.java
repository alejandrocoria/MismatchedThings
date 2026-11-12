package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.client.KeyBindings;
import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.util.MagicHelpers;
import com.mojang.blaze3d.platform.InputConstants;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Structured Components and extracted rectangles, plus connected production input handlers. */
public final class JustDirePresentationClient {
    public static Boolean shift;
    private static int ticks;
    private static int stage;
    private static int wait;
    private static boolean sent;
    private static boolean finished;

    private JustDirePresentationClient() {}

    public static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 1800, "JDT presentation client timeout at " + stage
                    + ", main=" + (client.player == null ? "no player" : client.player.getMainHandItem()));
            require(!Files.exists(JustDireConnectedServer.report("server.failure.txt")), "Presentation server failed");
            if (client.player == null || client.level == null || client.getOverlay() != null) { return; }
            require(client.getSingleplayerServer() == null, "Integrated authority used");
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE, "Visible test client");
            if (!sent) {
                if (client.screen != null) { client.setScreen(null); }
                if (stage == 0) {
                    presentation(client);
                    key(71, GLFW.GLFW_RELEASE);
                    key(71, GLFW.GLFW_REPEAT);
                    client.setScreen(new InventoryScreen(client.player));
                    key(71, GLFW.GLFW_PRESS);
                    client.setScreen(null);
                    mouse(0);
                    mouse(1);
                } else if (stage == 1) { key(71, GLFW.GLFW_PRESS); }
                else if (stage == 2) { mouse(4); }
                else if (stage == 3) {
                    KeyBindings.toggleTool.setKey(InputConstants.Type.KEYSYM.getOrCreate(72));
                    key(86, GLFW.GLFW_PRESS);
                    key(72, GLFW.GLFW_PRESS);
                } else if (stage == 4) {
                    KeyBindings.toggleTool.setKey(InputConstants.Type.MOUSE.getOrCreate(5));
                    mouse(5);
                } else if (stage == 5) {
                    if (!client.player.getMainHandItem().is(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("justdirethings:eclipsealloy_leggings")))) { return; }
                    KeyBindings.toggleTool.setKey(InputConstants.Type.KEYSYM.getOrCreate(72));
                    key(72, GLFW.GLFW_PRESS);
                } else if (stage == 6) {
                    if (Boolean.getBoolean("leggings.test.jdtBoots") ? !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(client.player.getMainHandItem().getItem()).getNamespace().equals("mismatchedboots")
                            : !client.player.getMainHandItem().isEmpty()) { return; }
                    key(72, GLFW.GLFW_PRESS);
                } else if (stage == 7) {
                    client.setScreen(new InventoryScreen(client.player));
                    key(71, GLFW.GLFW_PRESS);
                    client.setScreen(null);
                }
                sent = true;
                wait = 0;
            }
            if (++wait < 16) { return; }
            if (!Files.exists(JustDireConnectedServer.report("case-" + stage + ".ready"))) {
                var root = client.player.getInventory().getItem(0);
                var inverse = client.player.getInventory().getItem(2);
                if (stage == 1 && JustDireInputChecks.on(inverse, Ability.WALKSPEED)) { return; }
                if (stage == 2 && JustDireInputChecks.on(inverse, Ability.SWIMSPEED)) { return; }
                if (stage == 3 && !root.getOrDefault(JustDireDataComponents.TOOL_ENABLED, false)) { return; }
                if (stage == 4 && root.getOrDefault(JustDireDataComponents.TOOL_ENABLED, true)) { return; }
                if (stage == 6 && !client.player.getOffhandItem().getOrDefault(JustDireDataComponents.TOOL_ENABLED, false)) { return; }
                Files.writeString(JustDireConnectedServer.report("case-" + stage + ".ok"), "Client stage " + stage + " observed\n");
                return; // The server may replace the slot after acknowledging this observation.
            }
            sent = false;
            if (++stage == 8) {
                KeyBindings.toggleTool.setKey(InputConstants.Type.KEYSYM.getOrCreate(86));
                SkeletonChecks.passed("client");
                finished = true;
                client.disconnectFromWorld(Component.literal("JDT presentation passed"));
                client.stop();
            }
        } catch (Throwable failure) {
            shift = null;
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static void presentation(Minecraft client) throws Exception {
        var source = JustDirePassiveChecks.nativeStack("eclipsealloy");
        source.set(JustDireDataComponents.FORGE_ENERGY, 400_000);
        source.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("Preserved source lore"))));
        var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.DIAMOND_LEGGINGS));
        for (var root : List.of(pair.primary(), pair.inverse())) {
            root.set(JustDireDataComponents.FORGE_ENERGY, 88_680);
            root.set(JustDireDataComponents.TOOL_ENABLED, false);
            root.set(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.PHASE), false);
            root.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.SWIMSPEED), false);
            KeyBindings.toggleTool.setKey(InputConstants.Type.KEYSYM.getOrCreate(72));
            shift = false;
            var before = root.copy();
            var compact = lines(client, root, TooltipFlag.NORMAL);
            var advanced = lines(client, root, TooltipFlag.ADVANCED);
            require(compact.stream().anyMatch(line -> line.getString().equals("Preserved source lore"))
                    && advanced.stream().anyMatch(line -> line.getString().equals("justdirethings:eclipsealloy_leggings")), "Source lore/advanced identity lost");
            require(keyLines(compact, "justdirethings.festored").size() == 1, "Stale or duplicate source FE");
            require(keyLines(compact, "justdirethings.festored").getFirst().getString().contains(MagicHelpers.tidyValue(88_680F)), "Not native abbreviated FE");
            require(keyLines(advanced, "justdirethings.shiftmoreinfo").size() == 1
                    && keyLines(advanced, "justdirethings.ability.walkspeed").isEmpty(), "F3+H substituted for physical Shift");
            var disabled = keyLines(compact, "justdirethings.disabled").getFirst();
            require(disabled.getStyle().getColor().getValue() == ChatFormatting.DARK_RED.getColor()
                    && disabled.getString().contains("H"), "Global state or rebound hint style/text");
            require(keyLines(compact, "justdirethings.shiftmoreinfo").getFirst().getStyle().getColor().getValue() == ChatFormatting.GRAY.getColor(), "Shift hint style");
            shift = true;
            var full = lines(client, root, TooltipFlag.NORMAL);
            require(keyLines(full, "justdirethings.festored").getFirst().getString().contains(MagicHelpers.formatted(88_680)), "Not native full FE");
            require(keyLines(full, "justdirethings.shiftmoreinfo").isEmpty(), "Empty/redundant Shift hints");
            var names = List.of("walkspeed", "runspeed", "swimspeed", "phase");
            var rows = full.stream().filter(line -> names.stream().anyMatch(name -> has(line, "justdirethings.ability." + name))).toList();
            require(rows.size() == 4, "Ability rows duplicated or missing");
            for (int index = 0; index < 4; index++) {
                var row = rows.get(index);
                int color = index == 2 ? ChatFormatting.GRAY.getColor() : index == 3 ? ChatFormatting.DARK_RED.getColor() : ChatFormatting.GREEN.getColor();
                require(has(row, "justdirethings.ability." + names.get(index)) && row.getStyle().getColor().getValue() == color, "Ability order/saved toggle color");
            }
            require(keyLines(full, "justdirethings.ability.decoy").isEmpty() && keyLines(full, "justdirethings.ability.lavarepair").isEmpty(), "Deferred ability claimed");
            require(ItemStack.matches(root, before), "Tooltip mutated root/history");
            var hidden = TooltipDisplay.DEFAULT;
            for (String name : List.of("walkspeed", "runspeed", "swimspeed", "phase")) {
                var ability = Ability.valueOf(name.toUpperCase(java.util.Locale.ROOT));
                hidden = hidden.withHidden(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(ability).get(), true);
            }
            hidden = hidden.withHidden(JustDireDataComponents.FORGE_ENERGY.get(), true).withHidden(JustDireDataComponents.TOOL_ENABLED.get(), true);
            root.set(DataComponents.TOOLTIP_DISPLAY, hidden);
            shift = false;
            require(keyLines(lines(client, root, TooltipFlag.NORMAL), "justdirethings.shiftmoreinfo").isEmpty()
                    && keyLines(lines(client, root, TooltipFlag.NORMAL), "justdirethings.festored").isEmpty(), "Hidden components/hint leaked");
            root.remove(DataComponents.TOOLTIP_DISPLAY);
            bars(client, root, false);
            root.setDamageValue(root.getMaxDamage() / 2);
            bars(client, root, true);
            root.set(DataComponents.UNBREAKABLE, net.minecraft.util.Unit.INSTANCE);
            bars(client, root, false);
            root.remove(DataComponents.UNBREAKABLE);
            root.set(JustDireDataComponents.FORGE_ENERGY, 500_000);
            bars(client, root, true);
        }
        var nativeLines = lines(client, source, TooltipFlag.NORMAL);
        require(!keyLines(nativeLines, "justdirethings.festored").isEmpty(), "Native presentation changed");
        var bare = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                net.minecraft.resources.Identifier.parse("mismatchedleggings:mixed_leggings")));
        require(keyLines(lines(client, bare, TooltipFlag.NORMAL), "justdirethings.festored").isEmpty(), "Invalid root exposes JDT state");
        shift = null;
        Files.writeString(JustDireConnectedServer.report("presentation.txt"), "Native number/key/style/order/hiding/read-only/source-history and extracted registered FE/durability lanes passed\n");
    }

    private static void bars(Minecraft client, ItemStack root, boolean damaged) {
        var state = new GuiRenderState();
        var before = root.copy();
        new GuiGraphicsExtractor(client, state, 0, 0).itemDecorations(client.font, root, 20, 20);
        var rects = new ArrayList<List<Integer>>();
        var durability = new ArrayList<List<Integer>>();
        state.forEachElement(element -> {
            if (element instanceof ColoredRectangleRenderState rect && rect.col1() == 0xFF40CFFF) {
                var box = rect.bounds();
                rects.add(List.of(box.left(), box.top(), box.width(), box.height()));
            }
            if (element instanceof ColoredRectangleRenderState rect && rect.col1() == (0xFF000000 | root.getBarColor())) {
                var box = rect.bounds();
                durability.add(List.of(box.left(), box.top(), box.width(), box.height()));
            }
        }, GuiRenderState.TraverseRange.ALL);
        var nativeItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse("justdirethings:eclipsealloy_leggings"));
        var expected = nativeItem.isBarVisible(root) ? List.of(List.of(22, damaged ? 30 : 33, nativeItem.getBarWidth(root), 1)) : List.of();
        require(rects.equals(expected), "Registered FE rectangles " + rects + " != " + expected);
        require(durability.equals(damaged ? List.of(List.of(22, 33, root.getBarWidth(), 1)) : List.of()), "Vanilla durability lane changed");
        require(ItemStack.matches(root, before), "Bar query mutated history");
    }

    private static List<Component> lines(Minecraft client, ItemStack root, TooltipFlag flag) {
        return root.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
    }

    private static List<Component> keyLines(List<Component> lines, String key) { return lines.stream().filter(line -> has(line, key)).toList(); }
    private static boolean has(Component line, String key) {
        return line.getContents() instanceof TranslatableContents text && text.getKey().equals(key)
                || line.getSiblings().stream().anyMatch(part -> has(part, key));
    }
    private static void key(int key, int action) { NeoForge.EVENT_BUS.post(new InputEvent.Key(new KeyEvent(key, 0, 0), action)); }
    private static void mouse(int button) { NeoForge.EVENT_BUS.post(new InputEvent.MouseButton.Post(new MouseButtonInfo(button, 0), GLFW.GLFW_PRESS)); }
}
