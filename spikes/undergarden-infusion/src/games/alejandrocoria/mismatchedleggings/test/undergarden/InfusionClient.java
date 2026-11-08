package games.alejandrocoria.mismatchedleggings.test.undergarden;

import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import quek.undergarden.registry.UGDataComponents;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;
import static games.alejandrocoria.mismatchedleggings.test.undergarden.InfusionSpike.CANDIDATE;
import static games.alejandrocoria.mismatchedleggings.test.undergarden.InfusionSpike.CHEST;
import static games.alejandrocoria.mismatchedleggings.test.undergarden.InfusionSpike.PRODUCTS;
import static games.alejandrocoria.mismatchedleggings.test.undergarden.InfusionSpike.amount;
import static games.alejandrocoria.mismatchedleggings.test.undergarden.InfusionSpike.charge;
import static games.alejandrocoria.mismatchedleggings.test.undergarden.InfusionSpike.history;
import static games.alejandrocoria.mismatchedleggings.test.undergarden.InfusionSpike.item;
import static games.alejandrocoria.mismatchedleggings.test.undergarden.InfusionSpike.pair;
import static games.alejandrocoria.mismatchedleggings.test.undergarden.InfusionSpike.report;
import static games.alejandrocoria.mismatchedleggings.test.undergarden.InfusionSpike.valid;

public final class InfusionClient {
    private static boolean finished;
    private static int ticks;
    private static CompletableFuture<Void> saved;
    private static CompletableFuture<Void> reload;
    private static String first;
    private static BackupConfirmScreen confirmed;
    private static int closing;

    private InfusionClient() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
    }

    private static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 2400, "Infusion client timeout: " + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                    && client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Client must remain hidden/muted");
            if (closing > 0) {
                require(client.level == null && client.getSingleplayerServer() == null, "Fixture did not disconnect cleanly");
                if (++closing >= 4) {
                    SkeletonChecks.passed("client");
                    finished = true;
                    client.stop();
                }
                return;
            }
            if (client.player == null && client.screen instanceof BackupConfirmScreen backup && confirmed != backup) {
                confirmed = backup;
                backup.children().stream().filter(child -> child instanceof Button button
                                && button.getMessage().equals(Component.translatable("selectWorld.backupJoinSkipButton")))
                        .map(Button.class::cast).findFirst().orElseThrow().onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                return;
            }
            if (client.player == null || client.level == null || client.getOverlay() != null) { return; }
            if (saved == null) {
                saved = client.getSingleplayerServer().submit(() -> {
                    var chest = (ChestBlockEntity) client.getSingleplayerServer().overworld().getBlockEntity(CHEST);
                    require(chest != null, "Saved chest not reopened");
                    int index = 0;
                    for (String product : PRODUCTS) {
                        require(valid(chest.getItem(index)) && valid(chest.getItem(index + 1)), "Saved product root invalid");
                        require(amount(chest.getItem(index)) == 10
                                && amount(chest.getItem(index + 1)) == (CANDIDATE ? 600 : 0), "Saved root reserve changed");
                        index += 2;
                    }
                });
                return;
            }
            if (!saved.isDone()) { return; }
            saved.join();
            if (reload == null) {
                first = verify(client);
                reload = client.reloadResourcePacks();
                return;
            }
            if (!reload.isDone() || client.getOverlay() != null) { return; }
            reload.join();
            String second = verify(client);
            Files.writeString(report("client.txt"), "Hidden/muted client; saved world reopened\n" + first
                    + "Resource reload completed; all assertions repeated\n" + second);
            client.disconnectWithSavingScreen();
            closing = 1;
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static String verify(Minecraft client) throws Exception {
        var report = new StringBuilder();
        for (String product : PRODUCTS) {
            var source = item("undergarden:froststeel_" + product);
            charge(source, 1201);
            var other = item("minecraft:diamond_" + product);
            var roots = pair(product, source, other);
            for (int side = 0; side < 2; side++) {
                var root = roots.get(side);
                var historical = history(root, product);
                for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                    var lines = lines(client, root, flag);
                    long expected = CANDIDATE ? 1 : side == 0 ? 2 : 1;
                    require(count(lines) == expected, "Actual root/source infusion lines " + product + ": " + lines);
                    report.append(product).append(" side=").append(side).append(" advanced=").append(flag.isAdvanced()).append(": ")
                            .append(lines.stream().map(Component::getString).toList()).append('\n');
                }
                charge(root, 900);
                require(lines(client, root, TooltipFlag.NORMAL).stream().anyMatch(line -> key(line)
                        && line.getString().contains("900/3600")), "Native root line not live");
                bar(client, root, 900, false);
                root.setDamageValue(10);
                bar(client, root, 900, true);
                charge(root, 3600);
                bar(client, root, 3600, true);
                charge(root, 0);
                require(count(lines(client, root, TooltipFlag.NORMAL)) == (CANDIDATE ? 0 : 1), "Depleted root presents historical source charge");
                bar(client, root, 0, true);
                charge(root, 600);
                root.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(UGDataComponents.ROGDORIUM_INFUSION.get(), true));
                require(count(lines(client, root, TooltipFlag.NORMAL)) == (CANDIDATE ? 0 : 1), "Root hiding not respected");
                require(history(root, product).equals(historical), "Tooltip/bar mutated source histories");
            }
            var nativeLines = lines(client, source, TooltipFlag.NORMAL);
            require(count(nativeLines) == 1, "Candidate hid ordinary native tooltip");
            report.append(product).append(": actual registered decorator, live/depleted/full/damaged root, visibility, immutable sources passed\n");
        }
        if (PRODUCTS.contains("leggings")) { report.append(OrdinaryRendering.verify(client)); }
        return report.toString();
    }

    private static void bar(Minecraft client, ItemStack root, int amount, boolean damaged) {
        var state = new GuiRenderState();
        var before = root.copy();
        new GuiGraphicsExtractor(client, state, 0, 0).itemDecorations(client.font, root, 20, 20);
        var actual = new ArrayList<Rectangle>();
        state.forEachElement(element -> {
            if (element instanceof ColoredRectangleRenderState rect && rect.col1() == -8540239) {
                var bounds = rect.bounds();
                actual.add(new Rectangle(bounds.left(), bounds.top(), bounds.width(), bounds.height()));
            }
        }, GuiRenderState.TraverseRange.ALL);
        var expected = amount == 0 ? List.of() : List.of(new Rectangle(22, damaged ? 31 : 33, Math.round(amount * 13F / 3600), 1));
        require(actual.equals(expected), "Native decorator rectangles " + actual + " != " + expected);
        require(ItemStack.matches(root, before), "Decorator mutated state");
    }

    private static List<Component> lines(Minecraft client, ItemStack stack, TooltipFlag flag) {
        return stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
    }

    private static long count(List<Component> lines) { return lines.stream().filter(InfusionClient::key).count(); }

    private static boolean key(Component line) {
        return line.getContents() instanceof TranslatableContents text && text.getKey().equals("tooltip.undergarden.rogdorium_infusion")
                || line.getSiblings().stream().anyMatch(InfusionClient::key);
    }

    private record Rectangle(int x, int y, int width, int height) {}
}
