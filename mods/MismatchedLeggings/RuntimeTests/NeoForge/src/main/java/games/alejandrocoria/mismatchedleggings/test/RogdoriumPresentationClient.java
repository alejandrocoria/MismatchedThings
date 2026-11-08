package games.alejandrocoria.mismatchedleggings.test;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.PRODUCTS;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.history;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.item;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.lower;
import static games.alejandrocoria.mismatchedleggings.test.RogdoriumStateChecks.pair;
import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Actual packaged tooltip producers and registered decoration rectangles, without prototype hooks. */
public final class RogdoriumPresentationClient {
    private static int ticks;
    private static boolean finished;
    private static int closing;
    private static BackupConfirmScreen confirmed;
    private static CompletableFuture<Void> reload;
    private static CompletableFuture<String> saved;
    private static String first;

    private RogdoriumPresentationClient() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
    }

    private static void tick(Minecraft client) {
        if (finished) {
            return;
        }
        try {
            require(++ticks < 2400, "Rogdorium presentation timeout: " + client.screen);
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
            if (client.player == null || client.level == null || client.getOverlay() != null) {
                return;
            }
            if (!Boolean.getBoolean("leggings.test.rogdoriumAbsent")) {
                if (saved == null) {
                    saved = client.getSingleplayerServer().submit(() -> {
                        try {
                            return RogdoriumLifecycleChecks.reopen(client.getSingleplayerServer());
                        } catch (Exception failure) {
                            throw new IllegalStateException("Saved Rogdorium state verification failed", failure);
                        }
                    });
                    return;
                }
                if (!saved.isDone()) {
                    return;
                }
                saved.join();
            }
            if (reload == null) {
                first = verify(client);
                reload = client.reloadResourcePacks();
                return;
            }
            if (!reload.isDone() || client.getOverlay() != null) {
                return;
            }
            reload.join();
            String second = verify(client);
            require(first.equals(second), "Resource reload changed the presentation contract");
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "presentation.txt"),
                    (saved == null ? "Native absent; no native saved-state claim\n" : saved.join())
                            + first + "All assertions repeated after resource reload; no screenshots or multiplayer transport claimed\n");
            client.disconnectWithSavingScreen();
            closing = 1;
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static String verify(Minecraft client) throws Exception {
        boolean absent = Boolean.getBoolean("leggings.test.rogdoriumAbsent");
        var mods = ModList.get();
        require(mods.isLoaded("undergarden") != absent, "Wrong native presence");
        require(mods.isLoaded("mismatchedboots") == PRODUCTS.contains("Boots")
                && mods.isLoaded("mismatchedleggings") == PRODUCTS.contains("Leggings"), "Wrong product graph");
        var type = infusionType();
        require((type == null) == absent, "Wrong registered infusion component");
        var report = new StringBuilder();
        for (String product : PRODUCTS) {
            var source = item((absent ? "minecraft:iron_" : "undergarden:froststeel_") + lower(product));
            var other = item("minecraft:diamond_" + lower(product));
            if (absent) {
                for (var root : pair(product, source, other)) {
                    require(infusionLines(client, root, TooltipFlag.NORMAL).isEmpty()
                            && infusionLines(client, root, TooltipFlag.ADVANCED).isEmpty(), "Absent adapter invented native text");
                }
                report.append(product).append(": quiet optional absence and normal/advanced source sections passed\n");
                continue;
            }
            charge(source, type, 1201);
            charge(other, type, 300);
            var sourceBefore = source.copy();
            var otherBefore = other.copy();
            var nativeLines = lines(client, source, TooltipFlag.NORMAL);
            require(infusionLines(client, source, TooltipFlag.NORMAL).size() == 1, "Native tooltip positive control failed");
            for (var root : pair(product, source, other)) {
                var historical = history(root, product);
                for (int reserve : new int[]{0, 900, 3600}) {
                    charge(root, type, reserve);
                    var nativeControl = source.copy();
                    charge(nativeControl, type, reserve);
                    for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                        var actual = infusionLines(client, root, flag);
                        require(actual.equals(infusionLines(client, nativeControl, flag)), "Root text/value/style differ from native");
                        require(actual.size() == (reserve > 0 ? 1 : 0), "Historical or duplicate reserve leaked");
                        var all = lines(client, root, flag);
                        if (reserve > 0) {
                            int right = index(all, "tooltip.mismatched" + lower(product) + ".mixed_" + lower(product) + ".right");
                            require(right >= 0 && all.indexOf(actual.getFirst()) > right, "Native live line moved into a source section");
                        }
                    }
                    root.setDamageValue(0);
                    bar(client, root, reserve, false);
                    root.setDamageValue(10);
                    bar(client, root, reserve, true);
                }
                charge(root, type, 900);
                root.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(type, true));
                require(infusionLines(client, root, TooltipFlag.NORMAL).isEmpty(), "Root component hiding ignored");
                bar(client, root, 900, true); // Native decoration does not consult component-tooltip hiding.
                root.remove(DataComponents.TOOLTIP_DISPLAY);
                root.remove(type);
                require(infusionLines(client, root, TooltipFlag.ADVANCED).isEmpty(), "Absent root resurrected historical reserve");
                require(historical.equals(history(root, product)), "Presentation changed source history");
            }
            // A source's own display flag is retained, but must not decide whether the root displays live state.
            var hiddenSource = source.copy();
            hiddenSource.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(type, true));
            for (var root : pair(product, hiddenSource, other)) {
                root.remove(DataComponents.TOOLTIP_DISPLAY);
                charge(root, type, 900);
                require(infusionLines(client, root, TooltipFlag.ADVANCED).size() == 1, "Source display flag suppressed live root");
            }
            require(ItemStack.matches(source, sourceBefore) && ItemStack.matches(other, otherBefore)
                    && lines(client, source, TooltipFlag.NORMAL).equals(nativeLines), "Ordinary native source changed");
            var plain = item("minecraft:iron_" + lower(product));
            for (var root : pair(product, plain, plain)) {
                charge(root, type, 900);
                require(infusionLines(client, root, TooltipFlag.ADVANCED).size() == 1, "Root-only infusion missing");
                bar(client, root, 900, false);
            }
            report.append(product).append(": both orientations; one live native line/style or none when depleted/absent/hidden; ")
                    .append("normal/F3+H, root-only state, source flags/history, native control and full/partial/zero bars passed\n");
        }
        if (!absent && PRODUCTS.contains("Leggings")) {
            report.append(UndergardenOrdinaryRendering.verify(client));
        }
        return report.toString();
    }

    @SuppressWarnings("unchecked")
    private static DataComponentType<Object> infusionType() {
        return (DataComponentType<Object>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .getOptional(Identifier.parse("undergarden:rogdorium_infusion")).orElse(null);
    }

    private static void charge(ItemStack stack, DataComponentType<Object> type, int amount) {
        var record = new JsonObject();
        record.addProperty("infusionAmount", amount);
        record.addProperty("infusionMax", 3600);
        stack.set(type, type.codec().parse(JsonOps.INSTANCE, record).getOrThrow());
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
        require(actual.equals(expected), "Native registered decorator rectangles " + actual + " != " + expected);
        require(ItemStack.matches(root, before), "Decorator mutated root/history");
    }

    private static List<Component> lines(Minecraft client, ItemStack stack, TooltipFlag flag) {
        var before = stack.copy();
        var result = stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
        require(ItemStack.matches(stack, before), "Tooltip producer mutated root/history");
        return result;
    }

    private static List<Component> infusionLines(Minecraft client, ItemStack stack, TooltipFlag flag) {
        return lines(client, stack, flag).stream().filter(line -> hasKey(line, "tooltip.undergarden.rogdorium_infusion")).toList();
    }

    private static int index(List<Component> lines, String key) {
        for (int index = 0; index < lines.size(); index++) {
            if (hasKey(lines.get(index), key)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean hasKey(Component line, String key) {
        return line.getContents() instanceof TranslatableContents text && text.getKey().equals(key)
                || line.getSiblings().stream().anyMatch(sibling -> hasKey(sibling, key));
    }

    private record Rectangle(int x, int y, int width, int height) {}
}
