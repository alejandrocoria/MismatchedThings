package games.alejandrocoria.mismatchedleggings.test.aether;

import com.aetherteam.aetherii.item.components.AetherIIDataComponents;
import com.aetherteam.aetherii.item.components.Charms;
import com.aetherteam.aetherii.item.components.ReinforcementTier;
import com.aetherteam.aetherii.item.equipment.EquipmentUtil;
import com.aetherteam.aetherii.item.equipment.armor.abilities.BurrukaiPlateArmor;
import com.mojang.datafixers.util.Either;
import games.alejandrocoria.mismatchedleggings.client.compatibility.aether.AetherSourceCharms;
import games.alejandrocoria.mismatchedleggings.client.compatibility.aether.AetherTooltipClient;
import games.alejandrocoria.mismatchedleggings.compatibility.aether.AetherTooltipState;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.BackupConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;
import static games.alejandrocoria.mismatchedleggings.test.aether.AetherGameplayChecks.item;
import static games.alejandrocoria.mismatchedleggings.test.aether.AetherGameplayChecks.report;

/** Real packaged tooltip producers, gather/factories and a relevant integrated render; no candidate hooks. */
public final class AetherTooltipChecks {
    private static int ticks;
    private static boolean finished;
    private static BackupConfirmScreen confirmed;
    private static Panel panel;
    private static volatile boolean captured;
    private static volatile Throwable captureFailure;
    private static List<Either<FormattedText, TooltipComponent>> gathered = List.of();
    private static final StringBuilder REPORT = new StringBuilder();

    private AetherTooltipChecks() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,
                (RenderTooltipEvent.GatherComponents event) -> gathered = List.copyOf(event.getTooltipElements()));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick(Minecraft.getInstance()));
    }

    private static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 2400, "Aether tooltip client timed out: " + client.screen);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                    && client.options.getSoundSourceVolume(SoundSource.MASTER) == 0, "Client must remain hidden/muted");
            if (client.player == null && client.screen instanceof BackupConfirmScreen backup && confirmed != backup) {
                confirmed = backup;
                backup.children().stream().filter(child -> child instanceof Button button
                                && button.getMessage().equals(Component.translatable("selectWorld.backupJoinSkipButton")))
                        .map(Button.class::cast).findFirst().orElseThrow().onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                return;
            }
            if (client.player == null || client.level == null || client.getOverlay() != null) { return; }
            if (panel == null) {
                var sample = verify(client);
                panel = new Panel(sample, lines(client, sample, TooltipFlag.ADVANCED));
                client.setScreen(panel);
            } else if (panel.frames >= 8 && !panel.requested) {
                panel.requested = true;
                Screenshot.takeScreenshot(client.getMainRenderTarget(), image -> {
                    try (image) {
                        image.writeToFile(report("aether-tooltip.png"));
                        captured = true;
                    } catch (Throwable failure) { captureFailure = failure; }
                });
            } else if (captured) {
                Files.writeString(report("aether-tooltips.txt"), REPORT.toString());
                SkeletonChecks.passed("client");
                finished = true;
                client.stop();
            }
            if (captureFailure != null) { throw new AssertionError("Tooltip screenshot failed", captureFailure); }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static ItemStack verify(Minecraft client) throws Exception {
        clear(client);
        for (String family : AetherGameplayChecks.FAMILIES) {
            var source = item(family + "_leggings");
            source.set(AetherIIDataComponents.REINFORCEMENT_TIER, ReinforcementTier.FIRST);
            source.set(DataComponents.CUSTOM_NAME, Component.literal("Original " + family));
            source.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Source lore retained"))));
            for (var root : pair(source, new ItemStack(Items.DIAMOND_LEGGINGS))) {
                for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                    var output = lines(client, root, flag);
                    require(AetherTooltipState.attributesReady() && AetherTooltipState.tiersReady(), "Presentation guard unavailable");
                    int header = index(output, "tooltip.mismatchedleggings.mixed_leggings." +
                            (MixedLeggingsStructure.validData(root).orElseThrow().left().source().is(source.getItem()) ? "left" : "right"));
                    require(output.stream().anyMatch(line -> line.getString().contains("Original " + family))
                            && output.stream().anyMatch(line -> line.getString().equals("Source lore retained")), "Source identity/lore lost");
                    int tier = index(output, "aether_ii.tooltip.item.reinforcement");
                    require(tier > header && count(output, "aether_ii.tooltip.item.reinforcement") == 1, "Tier duplicated/misplaced");
                    String ability = source.getItem().getDescriptionId() + ".nitrogen_internals.ability.tooltip.";
                    require(count(output, ability + "1") == (family.equals("arkenium") ? 0 : 1), "Wrong native ability/hint");
                    require(count(output, ability + "2") == 1 && output.stream().anyMatch(line -> line.getString().contains("0/3")), "Idle real count absent");
                    require(output.stream().anyMatch(line -> line.getString().equals("aether_ii:" + family + "_leggings")) == flag.isAdvanced(), "Advanced source ID");
                    client.player.setItemSlot(EquipmentSlot.HEAD, item(family + "_helmet"));
                    require(lines(client, root, flag).stream().anyMatch(line -> line.getString().contains("1/3")), "Inventory preview did not read live equipment");
                    client.player.setItemSlot(EquipmentSlot.CHEST, item(family + "_chestplate"));
                    client.player.setItemSlot(EquipmentSlot.LEGS, root);
                    require(lines(client, root, flag).stream().anyMatch(line -> line.getString().contains("3/3")), "Worn complete count");
                    require(lines(client, root.copy(), flag).stream().anyMatch(line -> line.getString().contains("3/3")), "Copy must still show actual count");
                    clear(client);
                }
                noPlayer(client, root, source);
            }
        }
        REPORT.append("Six families, both orientations, normal/F3+H, original identities/lore/IDs; source tiers once; Arkenium hint omitted; live 0/1/3 counts and no-player safety\n");
        attributes(client);
        var sample = graphics(client);
        visibility(client);
        scopes(client);
        if (ModList.get().isLoaded("mismatchedboots")) { sibling(client); }
        failure(client, sample);
        return sample;
    }

    private static void attributes(Minecraft client) {
        var source = item("burrukai_plate_leggings");
        var roots = pair(source, source);
        for (var root : roots) {
            clear(client);
            client.player.setItemSlot(EquipmentSlot.HEAD, item("burrukai_plate_helmet"));
            client.player.setItemSlot(EquipmentSlot.CHEST, item("burrukai_plate_chestplate"));
            client.player.setItemSlot(EquipmentSlot.LEGS, root);
            BurrukaiPlateArmor.updatePlayerAttributes(new PlayerTickEvent.Pre(client.player));
            var modifier = client.player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getModifiers().stream()
                    .filter(value -> value.id().getNamespace().equals("aether_ii")).findFirst().orElseThrow();
            for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                var row = Attributes.KNOCKBACK_RESISTANCE.value().toComponent(modifier, flag);
                var output = lines(client, root, flag);
                require(output.stream().filter(row::equals).count() == 1, "Duplicate/missing native set row");
                int ordinary = index(output, "item.modifiers.legs");
                require(ordinary >= 0 && output.indexOf(row) > ordinary, "Set rows not after ordinary root attributes");
                require(!lines(client, root.copy(), flag).contains(row), "Copy borrowed equipped bonuses");
                client.player.setItemSlot(EquipmentSlot.MAINHAND, root.copy());
                require(!lines(client, client.player.getMainHandItem(), flag).contains(row), "Held copy borrowed bonuses");
                for (var component : List.of(DataComponents.ATTRIBUTE_MODIFIERS, AetherIIDataComponents.ARMOR_SET.get())) {
                    root.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(component, true));
                    require(!lines(client, root, flag).contains(row), "Hidden root attribute/set group leaked transient row");
                }
                root.remove(DataComponents.TOOLTIP_DISPLAY);
            }
            client.player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            BurrukaiPlateArmor.updatePlayerAttributes(new PlayerTickEvent.Pre(client.player));
            require(client.player.getAttribute(Attributes.KNOCKBACK_RESISTANCE).getModifier(modifier.id()) == null, "Native effect removal");
        }
        clear(client);
        REPORT.append("Exact LEGS identity; duplicate families emit transient native rows once after ordinary attributes; held/copy/removal negatives\n");
    }

    private static ItemStack graphics(Minecraft client) throws Exception {
        var source = AetherGameplayChecks.charmed();
        source.set(AetherIIDataComponents.REINFORCEMENT_TIER, ReinforcementTier.SECOND);
        var second = source.copy();
        second.set(AetherIIDataComponents.CHARMS, new Charms(
                new Charms.CharmHolder(Charms.Type.ARMOR, Charms.Tier.TWO, item("charm_of_health_1")),
                new Charms.CharmHolder(Charms.Type.ARMOR, Charms.Tier.TWO, item("charm_of_health_1"))));
        var roots = pair(source, second);
        for (var root : roots) {
            String before = encoded(client, root);
            for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                var components = gather(client, root, lines(client, root, flag));
                var graphics = components.stream().filter(AetherTooltipChecks::nativeGraphic).toList();
                require(graphics.size() == 2, "Missing two installed source graphics");
                int first = index(gathered, "tooltip.mismatchedleggings.mixed_leggings.left", true);
                int right = index(gathered, "tooltip.mismatchedleggings.mixed_leggings.right", true);
                var positions = new ArrayList<Integer>();
                for (int i = 0; i < gathered.size(); i++) {
                    if (gathered.get(i).right().filter(value -> value.getClass().getName().contains("AetherSourceCharms$Prepared")).isPresent()) { positions.add(i); }
                }
                require(positions.size() == 2 && first < positions.get(0) && positions.get(0) < right && right < positions.get(1), "Graphics escaped own source sections");
                int expected = MixedLeggingsStructure.validData(root).orElseThrow().left().source().get(AetherIIDataComponents.CHARMS).charmHolders()
                        .stream().filter(holder -> !holder.getStack().isEmpty()).toList().size();
                require(((List<?>) graphics.getFirst().getClass().getMethod("charmHolders").invoke(graphics.getFirst())).size() == expected, "Empty capacity or installed duplicates lost");
                require(graphics.stream().allMatch(value -> value.getWidth(client.font) > 0), "Invalid native client graphic");
            }
            require(!root.has(AetherIIDataComponents.CHARMS) && before.equals(encoded(client, root)), "Presentation mutated root/history");
            var override = root.copy();
            override.set(AetherIIDataComponents.CHARMS, source.get(AetherIIDataComponents.CHARMS));
            require(gather(client, override, lines(client, override, TooltipFlag.NORMAL)).stream().filter(AetherTooltipChecks::nativeGraphic).count() == 3,
                    "Explicit live root holders lost native graphic route");
        }
        var empty = pair(item("neptune_leggings"), new ItemStack(Items.IRON_LEGGINGS)).getFirst();
        require(gather(client, empty, lines(client, empty, TooltipFlag.NORMAL)).stream().noneMatch(AetherTooltipChecks::nativeGraphic), "Empty source capacity shown");
        var crowded = source.copy();
        var holder = source.get(AetherIIDataComponents.CHARMS).charmHolders().getFirst();
        crowded.set(AetherIIDataComponents.CHARMS, new Charms(List.of(holder, holder, holder, holder, holder)));
        var crowdedRoot = pair(crowded, new ItemStack(Items.IRON_LEGGINGS)).getFirst();
        require(gather(client, crowdedRoot, lines(client, crowdedRoot, TooltipFlag.NORMAL)).stream().noneMatch(AetherTooltipChecks::nativeGraphic)
                && gathered.stream().filter(value -> value.left().filter(text -> text.getString().equals(item("charm_of_health_1").getHoverName().getString())).isPresent()).count() == 5,
                "Oversize native layout did not retain all installed names");
        var payload = (TooltipComponent) Class.forName("com.aetherteam.aetherii.client.renderer.item.tooltip.ClientCharmTooltip$CharmTooltip")
                .getConstructor(ItemStack.class, List.class).newInstance(source, List.of());
        var foreign = Either.<FormattedText, TooltipComponent>right(payload);
        var event = new RenderTooltipEvent.GatherComponents(ItemStack.EMPTY, 960, 540, new ArrayList<>(List.of(foreign)), -1);
        NeoForge.EVENT_BUS.post(event);
        require(event.getTooltipElements().size() == 1 && event.getTooltipElements().getFirst() == foreign, "Foreign rich tooltip changed");
        REPORT.append("Actual gather/native client factory; per-source ordered installed-only graphics; duplicates, empty capacity, oversized names, root override and foreign graphic; unchanged serialized histories\n");
        return roots.getFirst();
    }

    private static void visibility(Minecraft client) {
        var source = AetherGameplayChecks.charmed();
        source.set(AetherIIDataComponents.REINFORCEMENT_TIER, ReinforcementTier.FIRST);
        for (var hidden : List.of(AetherIIDataComponents.ARMOR_SET.get(), AetherIIDataComponents.REINFORCEMENT_TIER.get(), AetherIIDataComponents.CHARMS.get())) {
            var altered = source.copy();
            altered.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(hidden, true));
            var root = pair(altered, new ItemStack(Items.IRON_LEGGINGS)).getFirst();
            var lines = lines(client, root, TooltipFlag.ADVANCED);
            require((count(lines, "aether_ii.tooltip.item.reinforcement") == 0) == (hidden == AetherIIDataComponents.REINFORCEMENT_TIER.get()), "Tier visibility not independent");
            require((count(lines, source.getItem().getDescriptionId() + ".nitrogen_internals.ability.tooltip.") == 0)
                    == (hidden == AetherIIDataComponents.ARMOR_SET.get()), "Set visibility not independent");
            require((gather(client, root, lines).stream().noneMatch(AetherTooltipChecks::nativeGraphic))
                    == (hidden == AetherIIDataComponents.CHARMS.get()), "Charm visibility not independent");
        }
        var hidden = source.copy();
        hidden.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new java.util.LinkedHashSet<>()));
        var root = pair(hidden, new ItemStack(Items.IRON_LEGGINGS)).getFirst();
        var output = lines(client, root, TooltipFlag.ADVANCED);
        require(count(output, "aether_ii.tooltip.item.reinforcement") == 0
                && gather(client, root, output).stream().noneMatch(AetherTooltipChecks::nativeGraphic), "Hidden source leaked details");
        root.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, new java.util.LinkedHashSet<>()));
        require(lines(client, root, TooltipFlag.NORMAL).isEmpty(), "Whole-root hiding leaked content");
        var changedSet = source.copy();
        changedSet.set(AetherIIDataComponents.ARMOR_SET, net.minecraft.tags.ItemTags.TRIMMABLE_ARMOR);
        var changed = pair(changedSet, new ItemStack(Items.IRON_LEGGINGS)).getFirst();
        require(count(lines(client, changed, TooltipFlag.NORMAL), source.getItem().getDescriptionId() + ".nitrogen_internals.ability.tooltip.") == 0,
                "Known item with altered set key invented native family description");
        REPORT.append("Whole/source hiding and independent native set/tier/charm visibility passed\n");
    }

    private static void sibling(Minecraft client) throws Exception {
        var pair = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory")
                .getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, item("burrukai_plate_boots"), new ItemStack(Items.IRON_BOOTS));
        var boots = (ItemStack) pair.getClass().getMethod("inverse").invoke(pair);
        var legs = pair(item("burrukai_plate_leggings"), new ItemStack(Items.IRON_LEGGINGS)).get(1);
        clear(client);
        client.player.setItemSlot(EquipmentSlot.HEAD, item("burrukai_plate_helmet"));
        client.player.setItemSlot(EquipmentSlot.FEET, boots);
        client.player.setItemSlot(EquipmentSlot.LEGS, legs);
        require(EquipmentUtil.getArmorCount(client.player, item("burrukai_plate_leggings").get(AetherIIDataComponents.ARMOR_SET)) == 3, "Mixed LEGS/FEET native count");
        require(lines(client, legs, TooltipFlag.NORMAL).stream().anyMatch(line -> line.getString().contains("3/3"))
                && lines(client, boots, TooltipFlag.NORMAL).stream().anyMatch(line -> line.getString().contains("3/3")), "Sibling producer paths no longer coexist");
        clear(client);
        REPORT.append("Current packaged Boots/Leggings native tooltip producers and LEGS+FEET counts coexist without native anchors\n");
    }

    private static void noPlayer(Minecraft client, ItemStack root, ItemStack source) {
        var previous = client.player;
        var world = client.level;
        try {
            client.player = null;
            var output = lines(client, root, TooltipFlag.NORMAL);
            require(count(output, source.getItem().getDescriptionId() + ".nitrogen_internals.ability.tooltip.2") == 0, "No-player preview invented count");
            client.level = null;
            output = root.getTooltipLines(Item.TooltipContext.EMPTY, null, TooltipFlag.NORMAL);
            require(count(output, source.getItem().getDescriptionId() + ".nitrogen_internals.ability.tooltip.2") == 0, "No-world preview invented count");
        } finally {
            client.level = world;
            client.player = previous;
        }
    }

    private static void failure(Minecraft client, ItemStack sample) throws Exception {
        // Force a local formatting failure after proven containment. Native owner output must stay contained.
        var field = AetherTooltipClient.class.getDeclaredField("setsDisabled");
        field.setAccessible(true);
        field.setBoolean(null, true);
        try {
            require(AetherTooltipState.attributesReady() && AetherTooltipState.suppressAttributes(sample), "Formatting failure released native containment");
            var nativeSource = item("neptune_leggings");
            require(!AetherTooltipState.suppressAttributes(nativeSource) && !AetherTooltipState.suppressTier(nativeSource), "Unrelated native producer suppressed");
            var output = lines(client, sample, TooltipFlag.NORMAL);
            require(count(output, "aether_ii.tooltip.item.reinforcement") == 2, "Set failure disabled independent tier formatting");
            require(gather(client, sample, output).stream().filter(AetherTooltipChecks::nativeGraphic).count() == 2, "Set failure disabled independent graphic route");
        } finally { field.setBoolean(null, false); }
        var graphics = AetherSourceCharms.class.getDeclaredField("graphicsDisabled");
        graphics.setAccessible(true);
        graphics.setBoolean(null, true);
        try {
            require(gather(client, sample, lines(client, sample, TooltipFlag.NORMAL)).stream().noneMatch(AetherTooltipChecks::nativeGraphic)
                    && gathered.stream().anyMatch(value -> value.left().filter(text -> text.getString().equals(item("charm_of_health_1").getHoverName().getString())).isPresent()),
                    "Unavailable graphics lost installed native-name fallback");
            require(AetherTooltipState.attributesReady() && AetherTooltipState.tiersReady(), "Graphics failure disabled producer containment");
        } finally { graphics.setBoolean(null, false); }
        REPORT.append("Formatting failure does not release established containment or disable tiers/charms/gameplay; foreign native paths preserved\n");
    }

    private static void scopes(Minecraft client) {
        var source = item("neptune_leggings");
        var foreign = item("burrukai_plate_leggings");
        source.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Scoped source"))));
        foreign.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Nested source"))));
        boolean[] observed = {false};
        var context = Item.TooltipContext.of(client.level);
        AetherTooltipState.details(source, context, TooltipDisplay.DEFAULT, null, TooltipFlag.NORMAL, line -> {
            require(AetherTooltipState.suppressAttributes(source) && !AetherTooltipState.suppressAttributes(foreign), "Source scope borrowed foreign identity");
            if (!observed[0]) {
                observed[0] = true;
                AetherTooltipState.details(foreign, context, TooltipDisplay.DEFAULT, null, TooltipFlag.NORMAL, nested ->
                        require(AetherTooltipState.suppressAttributes(foreign) && !AetherTooltipState.suppressAttributes(source), "Nested source scope not exact"));
                require(AetherTooltipState.suppressAttributes(source) && !AetherTooltipState.suppressAttributes(foreign), "Nested scope not restored");
            }
        });
        require(observed[0] && !AetherTooltipState.suppressAttributes(source), "Source scope leaked after details");
        var sentinel = new IllegalStateException("Expected test consumer failure");
        try {
            AetherTooltipState.details(source, context, TooltipDisplay.DEFAULT, null, TooltipFlag.NORMAL, line -> { throw sentinel; });
            throw new AssertionError("Expected details consumer to fail");
        } catch (IllegalStateException expected) {
            require(expected == sentinel && !AetherTooltipState.suppressAttributes(source), "Exceptional source scope not restored");
        }
        REPORT.append("Exact source scope, nested foreign identity and exceptional restoration passed\n");
    }

    private static List<Component> lines(Minecraft client, ItemStack root, TooltipFlag flag) {
        return root.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
    }

    private static List<ClientTooltipComponent> gather(Minecraft client, ItemStack root, List<Component> lines) {
        return ClientHooks.gatherTooltipComponents(root, lines, 20, client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight(), client.font);
    }

    private static boolean nativeGraphic(ClientTooltipComponent value) {
        return value.getClass().getName().equals("com.aetherteam.aetherii.client.renderer.item.tooltip.ClientCharmTooltip");
    }

    private static List<ItemStack> pair(ItemStack left, ItemStack right) {
        var value = MixedLeggingsFactory.createPair(left, right);
        return List.of(value.primary(), value.inverse());
    }

    private static String encoded(Minecraft client, ItemStack root) {
        return ItemStack.CODEC.encodeStart(client.level.registryAccess().createSerializationContext(NbtOps.INSTANCE), root).getOrThrow().toString();
    }

    private static void clear(Minecraft client) {
        for (var slot : EquipmentSlot.values()) { client.player.setItemSlot(slot, ItemStack.EMPTY); }
        BurrukaiPlateArmor.updatePlayerAttributes(new PlayerTickEvent.Pre(client.player));
    }

    private static boolean key(Component line, String prefix) {
        return line.getContents() instanceof TranslatableContents text && text.getKey().startsWith(prefix)
                || line.getSiblings().stream().anyMatch(child -> key(child, prefix));
    }

    private static long count(List<Component> lines, String prefix) {
        return lines.stream().filter(line -> key(line, prefix)).count();
    }

    private static int index(List<Component> lines, String prefix) {
        for (int i = 0; i < lines.size(); i++) { if (key(lines.get(i), prefix)) { return i; } }
        return -1;
    }

    private static int index(List<Either<FormattedText, TooltipComponent>> lines, String prefix, boolean rich) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).left().filter(value -> value instanceof Component component && key(component, prefix)).isPresent()) { return i; }
        }
        return -1;
    }

    private static final class Panel extends Screen {
        private final ItemStack root;
        private final List<Component> lines;
        private int frames;
        private boolean requested;

        private Panel(ItemStack root, List<Component> lines) {
            super(Component.literal("Aether II native source details"));
            this.root = root;
            this.lines = lines;
        }

        @Override
        public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, 0xFF20252C);
            graphics.text(font, title, 12, 12, 0xFFFFFFFF, false);
            graphics.setTooltipForNextFrame(font, lines, Optional.empty(), root, 30, 40);
            frames++;
        }
    }
}
