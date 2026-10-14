package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleState;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.menu.MismatchedWorkbenchMenu;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchInput;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchResult;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Future;

/** Real NeoForge integrated-server packets, with independent synchronized client preview rules. */
public final class NeoForgeJustDireResourcesClientTest {
    private static int ticks;
    private static int phase;
    private static boolean finished;
    private static Future<?> job;
    private static MismatchedWorkbenchResult preview;
    private static Boolean tooltipShift;
    private static int generatorCharge;
    private static int fallCharge;

    private NeoForgeJustDireResourcesClientTest() {}

    public static Boolean tooltipShift() { return tooltipShift; }

    static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            if (++ticks > 3_600) { throw new IllegalStateException("Resource client timed out at phase " + phase); }
            if (client.player == null || client.level == null || !CompositionRulesClientLifecycle.areRulesSynchronized()) { return; }
            if (job != null && !job.isDone()) { return; }
            if (job != null) { job.get(); job = null; }
            var player = client.player;
            var server = client.getSingleplayerServer();
            require(server != null, "Integrated server missing");
            if (phase == 0) {
                require(client.options.getSoundSourceVolume(SoundSource.MASTER) == 0
                        && GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                        "Resource client was not hidden and muted");
                tooltipMatrix(client);
                preview = new MixedBootsWorkbenchRecipe().assemblePreview(new MismatchedWorkbenchInput(
                        poweredBoots(), new ItemStack(Items.IRON_BOOTS)),
                        AttributeRuleState.current(), ComponentRuleState.current());
                require(preview.isComplete(), "Synchronized client preview failed");
                var id = player.getUUID();
                job = server.submit(() -> {
                    var owner = server.getPlayerList().getPlayer(id);
                    require(owner != null, "Actual server player missing");
                    owner.getInventory().clearContent();
                    var menu = new MismatchedWorkbenchMenu(1, owner.getInventory());
                    menu.getSlot(0).set(poweredBoots());
                    menu.getSlot(1).set(new ItemStack(Items.IRON_BOOTS));
                    ItemStack first = menu.getSlot(2).safeTake(1, 1, owner);
                    ItemStack second = menu.getSlot(3).safeTake(1, 1, owner);
                    require(!first.isEmpty() && !second.isEmpty() && !menu.isCommitted(), "Actual server Workbench takes failed");
                    owner.getInventory().setItem(0, first);
                    owner.getInventory().setItem(1, second);
                    owner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, first.copy());
                    owner.inventoryMenu.broadcastChanges();
                });
                phase = 1;
            } else if (phase == 1) {
                ItemStack first = player.getInventory().getItem(0);
                ItemStack second = player.getInventory().getItem(1);
                if (!ModContent.isMixedBoots(first) || !ModContent.isMixedBoots(second)) { return; }
                if (!equipmentAgrees(client, first)) { return; }
                require(ItemStack.matches(preview.primary(), first) && ItemStack.matches(preview.inverse(), second),
                        "Actual server outputs differed from independently assembled client preview");
                require(JustDireResourceTestSupport.handler(first).getAmountAsInt() == 3_000
                        && JustDireResourceTestSupport.handler(first).getCapacityAsInt() == 10_000, "Client capability initial state differs");
                assertTooltip(client, first, 3_000, 10_000);
                assertTooltip(client, second, 3_000, 10_000);
                var id = player.getUUID();
                job = server.submit(() -> {
                    var owner = server.getPlayerList().getPlayer(id);
                    var energy = ItemAccess.forPlayerSlot(owner, 0).getCapability(Capabilities.Energy.ITEM);
                    try (var transaction = Transaction.openRoot()) {
                        require(energy != null && energy.extract(1_267, transaction) == 1_267, "Server slot FE extraction failed");
                        transaction.commit();
                    }
                    owner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, owner.getInventory().getItem(0).copy());
                    owner.inventoryMenu.broadcastChanges();
                });
                phase = 2;
            } else if (phase == 2) {
                ItemStack first = player.getInventory().getItem(0);
                if (JustDireResourceTestSupport.handler(first).getAmountAsInt() != 1_733) { return; }
                require(ItemStack.matches(preview.inverse(), player.getInventory().getItem(1))
                        && ModContent.getMixedBootsData(first).equals(ModContent.getMixedBootsData(preview.primary())),
                        "Actual FE packet changed inverse output or source snapshots");
                assertTooltip(client, first, 1_733, 10_000);
                var id = player.getUUID();
                job = server.submit(() -> {
                    var owner = server.getPlayerList().getPlayer(id);
                    owner.getInventory().getItem(0).remove(JustDireResourceTestSupport.energyComponent());
                    owner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, owner.getInventory().getItem(0).copy());
                    owner.inventoryMenu.broadcastChanges();
                });
                phase = 3;
            } else if (phase == 3) {
                ItemStack first = player.getInventory().getItem(0);
                if (first.has(JustDireResourceTestSupport.energyComponent())) { return; }
                if (!equipmentAgrees(client, first)) { return; }
                require(JustDireResourceTestSupport.handler(first).getAmountAsInt() == 0
                        && ModContent.getMixedBootsData(first).equals(ModContent.getMixedBootsData(preview.primary())),
                        "Absent live FE field was refilled on the real client");
                assertTooltip(client, first, 0, 10_000);
                var id = player.getUUID();
                job = server.submit(() -> {
                    var owner = server.getPlayerList().getPlayer(id);
                    owner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, ItemStack.EMPTY);
                    // Keep only one receiver: native boots cannot hide an unsupported mixed receiver.
                    owner.getInventory().setItem(1, ItemStack.EMPTY);
                    ItemStack generator = new ItemStack(BuiltInRegistries.ITEM.get(
                            Identifier.fromNamespaceAndPath("justdirethings", "pocket_generator")).orElseThrow().value());
                    generator.set(JustDireResourceTestSupport.energyComponent(), 2_000);
                    JustDireAttributeTestSupport.set(generator, "tool_enabled", true);
                    owner.getInventory().setItem(2, generator);
                    generator.getItem().inventoryTick(generator, server.overworld(), owner,
                            net.minecraft.world.entity.EquipmentSlot.MAINHAND);
                    generatorCharge = JustDireResourceTestSupport.handler(owner.getInventory().getItem(0)).getAmountAsInt();
                    int remainder = JustDireResourceTestSupport.handler(generator).getAmountAsInt();
                    require(generatorCharge > 0 && generatorCharge + remainder == 2_000,
                            "Native Pocket Generator transfer lost FE or failed to charge mixed boots");
                    owner.getInventory().setItem(2, ItemStack.EMPTY);
                    owner.inventoryMenu.broadcastChanges();
                });
                phase = 4;
            } else if (phase == 4) {
                ItemStack first = player.getInventory().getItem(0);
                if (JustDireResourceTestSupport.handler(first).getAmountAsInt() != generatorCharge) { return; }
                assertTooltip(client, first, generatorCharge, 10_000);
                require(ModContent.getMixedBootsData(first).equals(ModContent.getMixedBootsData(preview.primary())),
                        "Pocket Generator recharge changed provenance");
                var id = player.getUUID();
                job = server.submit(() -> {
                    var owner = server.getPlayerList().getPlayer(id);
                    ItemStack root = owner.getInventory().getItem(0);
                    require(generatorCharge >= 50, "Pocket Generator recharge is below native Negate eligibility");
                    owner.getInventory().setItem(0, ItemStack.EMPTY);
                    owner.setItemSlot(EquipmentSlot.FEET, root);
                    server.overworld().setBlock(owner.blockPosition().below(), Blocks.STONE.defaultBlockState(), 3);
                    owner.setGameMode(GameType.SURVIVAL);
                    fallCharge = Math.max(0, generatorCharge - 450);
                    equippedFall(owner, 9, fallCharge, 0);
                });
                phase = 5;
            } else if (phase == 5) {
                ItemStack root = player.getItemBySlot(EquipmentSlot.FEET);
                if (!ModContent.isMixedBoots(root) || JustDireResourceTestSupport.handler(root).getAmountAsInt() != fallCharge
                        || !equipmentAgrees(client, root)) { return; }
                assertTooltip(client, root, fallCharge, 10_000);
                require(player.getInventory().getItem(0).isEmpty(), "Equipping retained a duplicate receiver");
                var id = player.getUUID();
                job = server.submit(() -> {
                    var owner = server.getPlayerList().getPlayer(id);
                    if (fallCharge > 0) {
                        require(fallCharge >= 50, "Native recharge left an unsupported sub-threshold remainder for depletion control");
                        equippedFall(owner, 49, 0, 0);
                    }
                });
                phase = 6;
            } else if (phase == 6) {
                ItemStack root = player.getItemBySlot(EquipmentSlot.FEET);
                if (JustDireResourceTestSupport.handler(root).getAmountAsInt() != 0 || !equipmentAgrees(client, root)) { return; }
                assertTooltip(client, root, 0, 10_000);
                require(ModContent.getMixedBootsData(root).equals(ModContent.getMixedBootsData(preview.primary())), "Actual fall packet changed source charge");
                var id = player.getUUID();
                job = server.submit(() -> {
                    var owner = server.getPlayerList().getPlayer(id);
                    equippedFall(owner, 9, 0, 6);
                    // Actual equipped root is the only receiver; no copy or original boot can absorb FE.
                    ItemStack generator = new ItemStack(BuiltInRegistries.ITEM.get(
                            Identifier.fromNamespaceAndPath("justdirethings", "pocket_generator")).orElseThrow().value());
                    generator.set(JustDireResourceTestSupport.energyComponent(), 2_000);
                    JustDireAttributeTestSupport.set(generator, "tool_enabled", true);
                    owner.getInventory().setItem(2, generator);
                    generator.getItem().inventoryTick(generator, server.overworld(), owner, EquipmentSlot.MAINHAND);
                    generatorCharge = JustDireResourceTestSupport.handler(owner.getItemBySlot(EquipmentSlot.FEET)).getAmountAsInt();
                    require(generatorCharge >= 50 && generatorCharge + JustDireResourceTestSupport.handler(generator).getAmountAsInt() == 2_000,
                            "Native generator failed to recharge sole equipped mixed receiver/conserve FE");
                    owner.getInventory().setItem(2, ItemStack.EMPTY);
                });
                phase = 7;
            } else if (phase == 7) {
                ItemStack root = player.getItemBySlot(EquipmentSlot.FEET);
                if (JustDireResourceTestSupport.handler(root).getAmountAsInt() != generatorCharge || !equipmentAgrees(client, root)) { return; }
                assertTooltip(client, root, generatorCharge, 10_000);
                var id = player.getUUID();
                job = server.submit(() -> {
                    fallCharge = Math.max(0, generatorCharge - 450);
                    equippedFall(server.getPlayerList().getPlayer(id), 9, fallCharge, 0);
                });
                phase = 8;
            } else {
                ItemStack root = player.getItemBySlot(EquipmentSlot.FEET);
                if (JustDireResourceTestSupport.handler(root).getAmountAsInt() != fallCharge || !equipmentAgrees(client, root)) { return; }
                assertTooltip(client, root, fallCharge, 10_000);
                require(ModContent.getMixedBootsData(root).equals(ModContent.getMixedBootsData(preview.primary())), "Recharge/fall rewrote source provenance");
                NetworkAcceptanceFiles.write("just-dire-resources-client-report.txt", "hidden/muted NeoForge client; actual integrated-server Workbench takes; "
                        + "independent synchronized-rule preview equality; production client capability; actual spent/absent FE inventory packets; "
                        + "actual charged/depleted client armor and Step Assist; unchanged inverse/provenance; "
                        + "native normal/controlled-Shift tooltip controls; one exact live FE line in both orientations; "
                        + "maximum capacity; one root-installed upgrade inventory with live switches/parameters; dormant flags; "
                        + "one intrinsic root Lava entry with live switches; no source Lava/qualifier/empty Shift hint; hidden FE/upgrade/Lava/source details; unchanged vanilla lore; "
                        + "actual native Pocket Generator inventory/equipped transfers with FE conservation; actual survival Negate debit/depletion; "
                        + "positive unprotected fall control after depletion; natural equipment/attribute packets and exact live tooltip after debit; "
                        + "charged/depleted/recharged equipped armor/Step Assist and Negate protection restored without source refill\n");
                NetworkAcceptanceFiles.write("just-dire-resources-client-success.txt", "neoforge\n");
                finished = true;
            }
        } catch (Throwable failure) {
            finished = true;
            NetworkAcceptanceFiles.failure("just-dire-resources-client", failure);
        }
        if (finished) { client.stop(); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }

    private static ItemStack poweredBoots() {
        var stack = NeoForgeJustDireResourcesTest.boots("celestigem", 6_000);
        JustDireAttributeTestSupport.set(stack, "stepheight_upgrade_installed", true);
        JustDireAttributeTestSupport.set(stack, "negatefalldamage_upgrade_installed", true);
        return stack;
    }

    private static void equippedFall(net.minecraft.server.level.ServerPlayer owner, double distance, int remaining, float loss) {
        ItemStack root = owner.getItemBySlot(EquipmentSlot.FEET);
        ItemStack before = root.copy();
        owner.setHealth(owner.getMaxHealth()); owner.invulnerableTime = 0;
        owner.causeFallDamage(distance, 1, owner.damageSources().fall());
        require(owner.getMaxHealth() - owner.getHealth() == loss
                        && JustDireResourceTestSupport.handler(root).getAmountAsInt() == remaining,
                "Actual integrated-server Negate fall health/FE differs: distance=" + distance);
        before.set(JustDireResourceTestSupport.energyComponent(), remaining);
        require(owner.getItemBySlot(EquipmentSlot.FEET) == root && ItemStack.matches(before, root), "Negate debit changed equipped identity/non-FE state");
        // No forced equipment replacement or broadcast: normal ticks must synchronize this in-place debit.
    }

    private static void tooltipMatrix(Minecraft client) throws ReflectiveOperationException {
        for (String tier : List.of("ferricore", "blazegold", "celestigem", "eclipsealloy")) {
            ItemStack source = NeoForgeJustDireResourcesTest.boots(tier, tier.equals("eclipsealloy") ? 20_000 : 6_000);
            JustDireAttributeTestSupport.set(source, "stepheight_upgrade_installed", true);
            JustDireAttributeTestSupport.set(source, "jumpboost_upgrade_installed", true);
            JustDireAttributeTestSupport.set(source, "jumpboost_toggle", false);
            ItemStack sourceBefore = source.copy();
            List<Component> nativeLines = capture(client, source, TooltipFlag.NORMAL, true);
            require(nativeLines.stream().anyMatch(line -> key(line).equals("justdirethings.enabled")),
                    "Native control did not advertise source enabled/hotkey state");
            require(nativeLines.stream().anyMatch(line -> key(line).equals("justdirethings.ability.jumpboost")
                            && TextColor.fromLegacyFormat(ChatFormatting.DARK_RED).equals(line.getStyle().getColor())),
                    "Native control did not expose disabled source ability coloring");
            var pair = MixedBootsFactory.createPair(source, new ItemStack(Items.DIAMOND_BOOTS));
            for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
                var handler = JustDireResourceTestSupport.handler(root);
                int amount = handler == null ? 0 : handler.getAmountAsInt();
                int capacity = handler == null ? 0 : handler.getCapacityAsInt();
                if (tier.equals("eclipsealloy")) {
                    JustDireAttributeTestSupport.set(root, "earthquake_upgrade_installed", true);
                    JustDireAttributeTestSupport.set(root, "earthquake_value", 123);
                }
                assertTooltip(client, root, amount, capacity);
                JustDireAttributeTestSupport.set(root, "jumpboost_value", 1);
                JustDireAttributeTestSupport.set(root, "tool_enabled", false);
                assertTooltip(client, root, amount, capacity);
                root.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(
                        JustDireAttributeTestSupport.component("stepheight_upgrade_installed"), true));
                require(capture(client, root, TooltipFlag.NORMAL, true).stream()
                        .filter(line -> key(line).startsWith("tooltip.mismatchedboots.just_dire.upgrade"))
                        .noneMatch(line -> key((Component) ((TranslatableContents) line.getContents()).getArgs()[0])
                                .equals("justdirethings.ability.stepheight")), "Hidden root upgrade was displayed");
                root.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
                if (capacity > 0) {
                    root.set(JustDireResourceTestSupport.energyComponent(), 1);
                    assertTooltip(client, root, 1, capacity);
                    root.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT
                            .withHidden(JustDireResourceTestSupport.energyComponent(), true));
                    require(capture(client, root, TooltipFlag.NORMAL, true).stream()
                            .noneMatch(line -> key(line).equals("justdirethings.festored")), "Root hidden FE was displayed");
                }
            }
            require(ItemStack.matches(source, sourceBefore), "Tooltip matrix mutated native input");
        }
        Map<?, ?> flags = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config")
                .getField("AVAILABLE_ABILITY_MAP").get(null);
        var lavaRepair = (ModConfigSpec.BooleanValue) flags.entrySet().stream()
                .filter(entry -> ((Enum<?>) entry.getKey()).name().equals("LAVAREPAIR"))
                .findFirst().orElseThrow().getValue();
        boolean original = lavaRepair.get();
        var intrinsicPair = MixedBootsFactory.createPair(NeoForgeJustDireResourcesTest.boots("blazegold", 0),
                NeoForgeJustDireResourcesTest.boots("blazegold", 0));
        try {
            lavaRepair.set(false);
            assertTooltip(client, intrinsicPair.primary(), 0, 0);
            assertTooltip(client, intrinsicPair.inverse(), 0, 0);
            lavaRepair.set(true);
            assertTooltip(client, intrinsicPair.primary(), 0, 0);
            assertTooltip(client, intrinsicPair.inverse(), 0, 0);
            JustDireAttributeTestSupport.set(intrinsicPair.primary(), "lavarepair_toggle", false);
            assertTooltip(client, intrinsicPair.primary(), 0, 0);
            intrinsicPair.primary().set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT
                    .withHidden(JustDireAttributeTestSupport.component("lavarepair_toggle"), true));
            assertTooltip(client, intrinsicPair.primary(), 0, 0);
        } finally { lavaRepair.set(original); }
        ItemStack hiddenIntrinsic = NeoForgeJustDireResourcesTest.boots("blazegold", 0);
        hiddenIntrinsic.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));
        var hiddenIntrinsicRoot = MixedBootsFactory.createPair(hiddenIntrinsic, new ItemStack(Items.DIAMOND_BOOTS)).primary();
        hiddenIntrinsicRoot.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        assertTooltip(client, hiddenIntrinsicRoot, 0, 0);
        var pair = MixedBootsFactory.createPair(NeoForgeJustDireResourcesTest.boots("celestigem", 6_000),
                NeoForgeJustDireResourcesTest.boots("eclipsealloy", 20_000));
        assertTooltip(client, pair.primary(), 13_000, 500_000);
        assertTooltip(client, pair.inverse(), 13_000, 500_000);

        ItemStack hidden = poweredBoots();
        hidden.set(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(true, TooltipDisplay.DEFAULT.hiddenComponents()));
        ItemStack root = MixedBootsFactory.createPair(hidden, new ItemStack(Items.IRON_BOOTS)).primary();
        root.set(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        List<Component> lines = capture(client, root, TooltipFlag.NORMAL, true);
        require(lines.stream().noneMatch(line -> key(line).equals("tooltip.mismatchedboots.just_dire.source_abilities"))
                        && lines.stream().noneMatch(line -> key(line).startsWith("justdirethings.ability.")),
                "Hidden source abilities leaked through the adapter");
        require(lines.stream().filter(line -> key(line).equals("tooltip.mismatchedboots.just_dire.installed_upgrades")).count() == 1,
                "Hidden provenance incorrectly hid root installations");
        root = MixedBootsRecomposer.recompose(root, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
        assertTooltip(client, root, 0, 0); // Dormant installations remain visible without an eligible tier/wallet.

        ItemStack vanilla = new ItemStack(Items.IRON_BOOTS);
        Component lore = Component.translatable("justdirethings.enabled");
        Component hintLore = Component.translatable("justdirethings.shiftmoreinfo");
        vanilla.set(DataComponents.LORE, new ItemLore(List.of(lore, hintLore)));
        root = MixedBootsFactory.createPair(vanilla, new ItemStack(Items.DIAMOND_BOOTS)).primary();
        require(capture(client, root, TooltipFlag.NORMAL, false).stream()
                        .anyMatch(line -> key(line).equals("justdirethings.enabled")),
                "Just Dire Things adapter filtered unrelated source lore");
        require(capture(client, root, TooltipFlag.NORMAL, false).stream()
                        .anyMatch(line -> key(line).equals("justdirethings.shiftmoreinfo")),
                "Just Dire Things adapter filtered unrelated Shift lore");
    }

    private static void assertTooltip(Minecraft client, ItemStack root, int amount, int capacity) throws ReflectiveOperationException {
        ItemStack before = root.copy();
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        boolean lava = false;
        for (ItemStack source : List.of(data.left().source(), data.right().source())) {
            if (BuiltInRegistries.ITEM.getKey(source.getItem()).equals(Identifier.fromNamespaceAndPath("justdirethings", "blazegold_boots"))) {
                var abilities = (java.util.Set<?>) source.getItem().getClass().getMethod("getAbilities").invoke(source.getItem());
                lava |= abilities.stream().anyMatch(ability -> ability.toString().equals("LAVAREPAIR"));
            }
        }
        for (TooltipFlag flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
            for (boolean shift : new boolean[]{false, true}) {
                List<Component> lines = capture(client, root, flag, shift);
                List<Component> energy = lines.stream().filter(line -> key(line).equals("justdirethings.festored")).toList();
                require(energy.size() == (capacity > 0 ? 1 : 0), "Historical/duplicate/missing FE lines: " + lines);
                if (capacity > 0) {
                    Object[] args = ((TranslatableContents) energy.getFirst().getContents()).getArgs();
                    require(args.length == 2 && args[0].equals(String.format(Locale.ROOT, "%,d", amount))
                            && args[1].equals(String.format(Locale.ROOT, "%,d", capacity)), "FE text does not represent root charge/capacity");
                }
                require(lines.stream().noneMatch(line -> key(line).equals("justdirethings.enabled")
                        || key(line).equals("justdirethings.disabled")), "Snapshot enablement/hotkey was advertised");
                List<Component> abilities = lines.stream().filter(line -> key(line).startsWith("justdirethings.ability.")).toList();
                require(abilities.isEmpty(), "Historical source Lava/upgrade lore remained");
                require(lines.stream().filter(line -> key(line).equals("justdirethings.shiftmoreinfo"))
                        .count() == 0, "Source Shift hint advertised no retained details");
                require(lines.stream().filter(line -> key(line).equals("tooltip.mismatchedboots.just_dire.source_abilities"))
                        .count() == 0, "Unused source qualifier remained");
                var intrinsic = lines.stream().filter(line -> key(line).equals("justdirethings.ability")).toList();
                boolean showLava = lava && root.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT)
                        .shows(JustDireAttributeTestSupport.component("lavarepair_toggle"));
                require(intrinsic.size() == (showLava ? 1 : 0), "Root Lava entry missing/duplicated/historical");
                if (showLava) {
                    Object[] args = ((TranslatableContents) intrinsic.getFirst().getContents()).getArgs();
                    boolean enabled = root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("tool_enabled"), true)
                            && root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("lavarepair_toggle"), true);
                    require(args.length == 2 && key((Component) args[0]).equals("justdirethings.ability.lavarepair")
                            && key((Component) args[1]).equals("justdirethings." + (enabled ? "enabled" : "disabled")),
                            "Intrinsic entry ignored current root switches");
                    int label = java.util.stream.IntStream.range(0, lines.size())
                            .filter(index -> key(lines.get(index)).equals("tooltip.mismatchedboots.just_dire.installed_upgrades")).findFirst().orElse(lines.size());
                    require(lines.indexOf(intrinsic.getFirst()) < label, "Intrinsic Lava was grouped under Installed upgrades");
                }
                var installed = List.of("stepheight", "jumpboost", "negatefalldamage", "groundstomp", "earthquake").stream()
                        .filter(name -> root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component(name + "_upgrade_installed"), false)).toList();
                var inventory = lines.stream().filter(line -> key(line).equals("tooltip.mismatchedboots.just_dire.upgrade")
                        || key(line).equals("tooltip.mismatchedboots.just_dire.upgrade_setting")).toList();
                require(inventory.size() == installed.size() && lines.stream()
                        .filter(line -> key(line).equals("tooltip.mismatchedboots.just_dire.installed_upgrades")).count()
                        == (installed.isEmpty() ? 0 : 1), "Root installed inventory is duplicated/missing");
                for (int index = 0; index < installed.size(); index++) {
                    String name = installed.get(index);
                    Object[] args = ((TranslatableContents) inventory.get(index).getContents()).getArgs();
                    boolean enabled = root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("tool_enabled"), true)
                            && root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component(name + "_toggle"), true);
                    require(key((Component) args[0]).equals("justdirethings.ability." + name)
                            && key((Component) args[1]).equals("tooltip.mismatchedboots.just_dire." + (enabled ? "on" : "off")),
                            "Root inventory advertised historical name/control");
                    var setting = BuiltInRegistries.DATA_COMPONENT_TYPE.get(Identifier.fromNamespaceAndPath("justdirethings", name + "_value"));
                    if (!name.equals("earthquake") && setting.isPresent() && root.has(setting.get().value())) {
                        require(args.length == 3 && args[2].equals(root.get(setting.get().value())), "Root parameter missing/historical");
                    } else { require(args.length == 2, "Missing root parameter used historical setting"); }
                    require(TextColor.fromLegacyFormat(ChatFormatting.GRAY).equals(inventory.get(index).getStyle().getColor()),
                            "Installed inventory claimed active availability");
                }
                require(ItemStack.matches(root, before), "Tooltip generation mutated root/provenance");
            }
        }
    }

    private static List<Component> capture(Minecraft client, ItemStack stack, TooltipFlag flag, boolean shift) {
        tooltipShift = shift;
        try {
            require(client.hasShiftDown() == shift, "Controlled tooltip Shift input was not installed");
            return stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, flag);
        } finally { tooltipShift = null; }
    }

    private static String key(Component line) {
        return line.getContents() instanceof TranslatableContents text ? text.getKey() : "";
    }

    private static boolean equipmentAgrees(Minecraft client, ItemStack expected) {
        var feet = client.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET);
        if (!ItemStack.matches(feet, expected)) { return false; }
        var modifiers = expected.getAttributeModifiers();
        for (var attribute : java.util.List.of(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR,
                net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT)) {
            double amount = modifiers.modifiers().stream().filter(entry -> entry.attribute().equals(attribute))
                    .mapToDouble(entry -> entry.modifier().amount()).sum();
            double base = client.player.getAttributeBaseValue(attribute);
            if (Math.abs(client.player.getAttributeValue(attribute) - base - amount) > 1e-7) { return false; }
        }
        return true;
    }
}
