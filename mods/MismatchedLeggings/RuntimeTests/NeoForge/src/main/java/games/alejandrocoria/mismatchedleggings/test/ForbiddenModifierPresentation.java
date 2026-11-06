package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifiers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native names and background extraction, plus real frames rather than a synthetic texture event. */
public final class ForbiddenModifierPresentation {
    private static final ResourceKey<Registry<Object>> REGISTRY = ResourceKey.createRegistryKey(
            Identifier.parse("forbidden_arcanus:item_modifier"));
    private static Identifier expectedTexture;
    private static int backgrounds;
    private static boolean observing;
    private static Boolean shift;

    private ForbiddenModifierPresentation() {}

    public static Boolean key(int key) {
        return key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT ? shift : null;
    }

    static List<ForbiddenArcanusChecks.Sample> installed(MinecraftServer server) {
        var result = new ArrayList<ForbiddenArcanusChecks.Sample>();
        var player = ForbiddenModifierChecks.actor(server);
        try {
            var pair = MixedLeggingsFactory.createPair(ForbiddenArcanusChecks.nativeItem("tyr_leggings"),
                    new ItemStack(Items.DIAMOND_LEGGINGS), CompositionRules.defaults(), server.registryAccess());
            for (var plain : List.of(pair.primary(), pair.inverse())) {
                var soul = ForbiddenModifierSmithingChecks.upgrade(player, plain, "soulbound", false);
                var eternal = ForbiddenModifierSmithingChecks.upgrade(player, plain, "eternal", true);
                var both = ForbiddenModifierSmithingChecks.upgrade(player, soul, "eternal", false);
                for (var root : List.of(soul, eternal, both)) {
                    root.set(DataComponents.CUSTOM_NAME, Component.literal("Installed modifier session root"));
                    root.set(DataComponents.REPAIR_COST, 4);
                    root.set(CompositionFixture.CHARGE, 321L);
                    result.add(new ForbiddenArcanusChecks.Sample("installed " + result.size(), root,
                            plain == pair.primary() ? 2 : 0));
                }
            }
        } finally {
            ForbiddenModifierChecks.close(player);
        }
        for (String kind : List.of("eternal", "soulbound")) {
            result.add(new ForbiddenArcanusChecks.Sample("native " + kind, ForbiddenModifierChecks.source(server, kind, true), 0));
        }
        return result;
    }

    static void verify(Minecraft client, List<ForbiddenArcanusChecks.Sample> samples) throws Exception {
        for (var sample : samples) {
            var root = sample.stack();
            var state = root.getOrDefault(LeggingsContent.forbiddenModifiersType(), ForbiddenModifiers.EMPTY);
            var unnamed = root.copy();
            unnamed.remove(DataComponents.CUSTOM_NAME);
            for (var stack : List.of(root, unnamed)) {
                var before = stack.copy();
                var expected = Component.empty();
                if (!stack.has(DataComponents.CUSTOM_NAME)) {
                    if (state.eternal()) { expected.append(name(client, "eternal")).append(" "); }
                    if (state.soulbound()) { expected.append(name(client, "soulbound")).append(" "); }
                }
                expected.append(stack.getHoverName());
                for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                    for (boolean pressed : List.of(false, true)) {
                        shift = pressed;
                        try {
                            var lines = stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
                            require(lines.getFirst().getString().equals(expected.getString()), "Native root prefix/custom name differs");
                            if (!stack.has(DataComponents.CUSTOM_NAME)) {
                                for (String kind : List.of("eternal", "soulbound")) {
                                    if (kind.equals("eternal") ? state.eternal() : state.soulbound()) {
                                        var prefix = name(client, kind);
                                        require(lines.getFirst().getSiblings().stream().anyMatch(prefix::equals),
                                                "Native translated/styled prefix was flattened or replaced");
                                    }
                                }
                            }
                            var history = MixedLeggingsStructure.validData(stack).orElse(null);
                            if (history != null) {
                                for (var source : List.of(history.left().source(), history.right().source())) {
                                    require(lines.stream().anyMatch(line -> line.getString().contains(source.getHoverName().getString())),
                                            "Source provenance name was lost");
                                }
                            }
                            // EMPTY/null-player requests are safe and do not borrow an unrelated active world.
                            require(stack.getTooltipLines(Item.TooltipContext.EMPTY, null, flag).getFirst().getString()
                                    .equals(stack.getHoverName().getString()), "Empty-context tooltip borrowed a world or changed title");
                        } finally {
                            shift = null;
                        }
                    }
                }
                require(ItemStack.matches(before, stack), "Read-only modifier tooltip changed root/source data");
            }
        }
        Files.writeString(ForbiddenArcanusChecks.report("modifiers-tooltip"),
                "Native translated root prefixes, custom/source names, normal/Shift/F3+H, EMPTY/null and nonmutation passed\n");
    }

    private static Component name(Minecraft client, String kind) throws Exception {
        var modifier = client.level.registryAccess().lookupOrThrow(REGISTRY)
                .getOrThrow(ResourceKey.create(REGISTRY, Identifier.fromNamespaceAndPath("forbidden_arcanus", kind))).value();
        var display = modifier.getClass().getMethod("displaySettings").invoke(modifier);
        return ((Component) display.getClass().getMethod("name").invoke(display)).copy();
    }

    private static Identifier texture(ItemStack root) throws Exception {
        var type = net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE
                .getValue(Identifier.parse("forbidden_arcanus:modifier"));
        var holder = (Holder<?>) root.get(type);
        if (holder == null) { return null; }
        var display = holder.value().getClass().getMethod("displaySettings").invoke(holder.value());
        return (Identifier) display.getClass().getMethod("texture").invoke(display);
    }

    public static void background(Identifier texture) {
        if (observing) {
            require(Objects.equals(expectedTexture, texture), "Rendered tooltip lost the native modifier background: " + texture);
            backgrounds++;
        }
    }

    static final class Scene extends Screen {
        private final List<ItemStack> stacks;
        private int frames;
        private boolean captureStarted;
        private volatile boolean captured;
        private volatile Throwable failure;

        Scene(List<ForbiddenArcanusChecks.Sample> samples) {
            super(Component.literal("Forbidden Arcanus modifier tooltip"));
            stacks = samples.stream().map(ForbiddenArcanusChecks.Sample::stack)
                    .filter(stack -> !ForbiddenModifierChecks.nativeKind(stack).equals("none"))
                    .sorted(Comparator.comparing(stack -> MixedLeggingsStructure.validData(stack).isPresent()))
                    .map(stack -> {
                        var copy = stack.copy();
                        copy.remove(DataComponents.CUSTOM_NAME);
                        return copy;
                    }).toList();
        }

        boolean complete(Minecraft client) {
            if (failure != null) { throw new AssertionError("Modifier tooltip frame failed", failure); }
            if (frames >= stacks.size() + 8 && !captureStarted) {
                captureStarted = true;
                Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                    try (image) {
                        image.writeToFile(Path.of(System.getProperty("leggings.test.report"), "forbidden-modifiers-tooltip.png"));
                        captured = true;
                    } catch (Throwable problem) { failure = problem; }
                });
            }
            return captured;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            try {
                var client = Minecraft.getInstance();
                var stack = stacks.get(Math.min(frames, stacks.size() - 1));
                graphics.fill(0, 0, width, height, 0xFF20252C);
                graphics.text(font, "Native modifier title and background", 20, 12, 0xFFFFFFFF, false);
                var lines = stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.ADVANCED);
                expectedTexture = texture(stack);
                observing = true;
                int before = backgrounds;
                graphics.setTooltipForNextFrame(font, lines, stack.getTooltipImage(), stack, 40, 42);
                graphics.extractDeferredElements(mouseX, mouseY, partialTick);
                require(backgrounds == before + 1, "Native tooltip did not extract a real background");
                frames++;
            } catch (Throwable problem) {
                failure = problem;
            } finally {
                observing = false;
            }
        }
    }
}
