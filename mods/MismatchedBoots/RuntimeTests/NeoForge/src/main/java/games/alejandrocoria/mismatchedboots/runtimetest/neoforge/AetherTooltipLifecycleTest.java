package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonArray;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.client.compatibility.aether.AetherTooltipClient;
import games.alejandrocoria.mismatchedboots.compatibility.aether.AetherTooltipCompatibility;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import games.alejandrocoria.mismatchedthings.internal.client.WorkbenchScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import net.neoforged.neoforge.event.AddAttributeTooltipsEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Future;

import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.component;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.item;
import static games.alejandrocoria.mismatchedboots.runtimetest.neoforge.AetherIICompatibilityTest.require;

/** Real native menus and same-JVM disconnect/rejoin; mouse coordinates are fixture-local, never OS input. */
public final class AetherTooltipLifecycleTest {
    private static List<ItemStack> samples;
    private static JsonArray saved;
    private static String savedFeet;
    private static UUID uuid;
    private static LocalPlayer oldPlayer;
    private static ItemStack oldFeet;
    private static MinecraftServer oldServer;
    private static Future<?> job;
    private static int phase;
    private static int targetSlot = -1;
    private static int observed;
    private static int ticks;
    private static AbstractContainerScreen<?> hoverScreen;
    private static ItemStack hoverStack;
    private static List<Component> hoverLines;
    private static String hoverBefore;

    private AetherTooltipLifecycleTest() {
    }

    static void begin(List<ItemStack> inputs) {
        samples = inputs;
    }

    public static Double mouse(boolean x) {
        if (targetSlot < 0 || hoverScreen == null || Minecraft.getInstance().screen != hoverScreen) {
            return null;
        }
        var slot = hoverScreen.getMenu().getSlot(targetSlot);
        return (double) (x ? hoverScreen.getLeftPos() + slot.x + 8 : hoverScreen.getTopPos() + slot.y + 8);
    }

    static void observe(ItemTooltipEvent event) {
        if (targetSlot >= 0 && Minecraft.getInstance().screen == hoverScreen && ModContent.isMixedBoots(event.getItemStack())) {
            hoverStack = event.getItemStack();
            hoverLines = List.copyOf(event.getToolTip());
            observed++;
        }
    }

    static boolean tick(Minecraft client, StringBuilder report) throws Exception {
        if (++ticks % 100 == 0) {
            games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles.write("aether-ii-tooltip-lifecycle-waiting.txt",
                                                                                          "phase=" + phase + " target=" + targetSlot + " observations=" + observed + " screen="
                                                                                                  + (client.screen == null
                                                                                                     ? "none"
                                                                                                     : client.screen.getClass()
                                                                                                                    .getName()) + " player=" + (client.player != null));
        }
        if (job != null) {
            if (!job.isDone()) {
                return false;
            }
            job.get();
            job = null;
        }
        if (phase == 0) {
            uuid = client.player.getUUID();
            oldPlayer = client.player;
            oldServer = client.getSingleplayerServer();
            job = oldServer.submit(() -> {
                var owner = oldServer.getPlayerList().getPlayer(uuid);
                owner.setItemSlot(EquipmentSlot.FEET, samples.get(9).copy());
                owner.setItemSlot(EquipmentSlot.HEAD, item("burrukai_plate_helmet"));
                owner.setItemSlot(EquipmentSlot.CHEST, item("burrukai_plate_chestplate"));
                owner.openMenu(new SimpleMenuProvider((id, inv, player) -> new WorkbenchMenu(id, inv),
                                                      Component.literal("Tooltip UI control")));
                owner.containerMenu.getSlot(0).set(item("burrukai_plate_boots"));
                owner.containerMenu.getSlot(1).set(new ItemStack(net.minecraft.world.item.Items.DIAMOND_BOOTS));
                owner.containerMenu.broadcastChanges();
                owner.inventoryMenu.broadcastChanges();
            });
            phase = 1;
            return false;
        }
        if (phase == 1) {
            if (!(client.screen instanceof WorkbenchScreen screen) || screen.getMenu().getSlot(2).getItem().isEmpty()
                    || AetherIITooltipClientTest.nativeSetRows(client, TooltipFlag.NORMAL).size() != 2) {
                return false;
            }
            boundaryProbes(client, client.player.getItemBySlot(EquipmentSlot.FEET));
            report.append(
                    "BOUNDARIES actual active worn object: missing player, missing world and both absent omit count/active attributes; unknown source set emits no known capability; original history unchanged\n");
            aim(screen, 2);
            phase = 2;
            return false;
        }
        if (phase == 2 || phase == 3) {
            if (observed < 2) {
                return false;
            }
            checkHover(client, report, "Workbench output " + targetSlot);
            if (phase == 2) {
                aim(hoverScreen, 3);
                phase = 3;
                return false;
            }
            targetSlot = -1;
            hoverScreen = null;
            job = client.getSingleplayerServer().submit(() -> {
                var owner = client.getSingleplayerServer().getPlayerList().getPlayer(uuid);
                owner.openMenu(new SimpleMenuProvider((id, inv, player) -> {
                    try {
                        return (net.minecraft.world.inventory.AbstractContainerMenu) Class.forName(
                                                                                                  "com.aetherteam.aetherii.inventory.menu.ArkeniumForgeMenu")
                                                                                          .getConstructor(int.class,
                                                                                                          net.minecraft.world.entity.player.Inventory.class)
                                                                                          .newInstance(id, inv);
                    } catch (ReflectiveOperationException failure) {
                        throw new IllegalStateException(failure);
                    }
                }, Component.literal("Native Forge tooltip control")));
                var menu = owner.containerMenu;
                var input = samples.get(9).copy();
                require(menu.getSlot(0).mayPlace(input), "Native Forge rejected its input slot; do not force invalid input");
                menu.setCarried(input);
                menu.clicked(0, 0, ContainerInput.PICKUP, owner);
                require(menu.getCarried().isEmpty() && !menu.getSlot(3).mayPlace(item("charm_of_health_1")), "Native mixed Forge contract changed");
                menu.broadcastChanges();
            });
            phase = 4;
            return false;
        }
        if (phase == 4) {
            if (!(client.screen instanceof AbstractContainerScreen<?> screen)
                    || !screen.getMenu().getClass().getName().endsWith("ArkeniumForgeMenu") || screen.getMenu().getSlot(0).getItem().isEmpty()) {
                return false;
            }
            aim(screen, 0);
            phase = 5;
            return false;
        }
        if (phase == 5) {
            if (observed < 2) {
                return false;
            }
            checkHover(client, report, "Native Forge mixed input (input allowed, charm installation rejected)");
            targetSlot = -1;
            hoverScreen = null;
            hoverStack = null;
            hoverLines = null;
            job = client.getSingleplayerServer().submit(() -> {
                var owner = client.getSingleplayerServer().getPlayerList().getPlayer(uuid);
                owner.closeContainer();
                owner.getInventory().clearContent();
                for (int i = 0; i < samples.size(); i++) {
                    owner.getInventory().setItem(i, samples.get(i).copy());
                }
                owner.setItemSlot(EquipmentSlot.FEET, samples.get(9).copy());
                owner.setItemSlot(EquipmentSlot.HEAD, item("burrukai_plate_helmet"));
                owner.setItemSlot(EquipmentSlot.CHEST, item("burrukai_plate_chestplate"));
                saved = new JsonArray();
                for (int i = 0; i < samples.size(); i++) {
                    saved.add(ItemStack.CODEC.encodeStart(owner.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                                                          owner.getInventory().getItem(i)).getOrThrow());
                }
                savedFeet = AetherIICompatibilityTest.snapshot(owner.level().getServer(), owner.getItemBySlot(EquipmentSlot.FEET));
                owner.inventoryMenu.broadcastChanges();
                owner.level().getServer().getPlayerList().saveAll();
            });
            phase = 6;
            return false;
        }
        if (phase == 6) {
            if (!sameSaved(client)) {
                return false;
            }
            oldFeet = client.player.getItemBySlot(EquipmentSlot.FEET);
            client.disconnectWithSavingScreen();
            phase = 7;
            return false;
        }
        if (phase == 7) {
            if (client.player != null || client.level != null || client.getSingleplayerServer() != null) {
                return false;
            }
            neutralFacade(oldFeet); // Real missing-world/missing-player state, not a context-only imitation.
            client.createWorldOpenFlows().openWorld("tooltip", () -> client.setScreen(new TitleScreen()));
            phase = 8;
            return false;
        }
        if (phase == 8) {
            if (client.player == null || client.level == null || !CompositionRulesClientLifecycle.areRulesSynchronized() || !sameSaved(client)
                    || AetherIITooltipClientTest.nativeSetRows(client, TooltipFlag.NORMAL).size() != 2) {
                return false;
            }
            require(client.player != oldPlayer && client.getSingleplayerServer() != oldServer
                            && client.player.getUUID().equals(uuid) && client.player.getItemBySlot(EquipmentSlot.FEET) != oldFeet,
                    "Reconnect did not replace actual player/server/FEET references");
            var worn = client.player.getItemBySlot(EquipmentSlot.FEET);
            var live = AetherTooltipTestControl.capture(client, worn, TooltipFlag.NORMAL, false);
            var stale = AetherTooltipTestControl.capture(client, oldFeet, TooltipFlag.NORMAL, false);
            var idle = AetherTooltipTestControl.capture(client, client.player.getInventory().getItem(9), TooltipFlag.NORMAL, false);
            for (var row : AetherIITooltipClientTest.nativeSetRows(client, TooltipFlag.NORMAL)) {
                require(live.lines().stream().filter(row::equals).count() == 1 && !stale.lines().contains(row) && !idle.lines().contains(row),
                        "Reconnect used old player/FEET ownership");
            }
            require(stale.nativeAttributes() == 0 && stale.lines().stream().anyMatch(line -> line.getString().contains("3/3")),
                    "Old reference lost current equipment progress");
            var sourceRoot = client.player.getInventory().getItem(24);
            AetherSourceCharmTest.verify(client,
                                         sourceRoot,
                                         AetherTooltipTestControl.capture(client, sourceRoot, TooltipFlag.ADVANCED, false).lines());
            var plain = ClientHooks.gatherTooltipComponents(new ItemStack(net.minecraft.world.item.Items.DIAMOND_BOOTS),
                                                            List.of(Component.literal("Unrelated tooltip")),
                                                            20,
                                                            client.getWindow().getGuiScaledWidth(),
                                                            client.getWindow().getGuiScaledHeight(),
                                                            client.font);
            require(plain.stream().noneMatch(value -> value.getClass().getName().endsWith("ClientCharmTooltip")),
                    "A source marker escaped to a later unrelated hover");
            require(sameSaved(client), "Post-rejoin presentation changed complete saved roots/history");
            report.append(
                    "LIFECYCLE same-JVM actual disconnect/save/rejoin: same UUID; fresh server/player/FEET; all 33 full roots/history match before presentation callbacks; live versus old-reference ownership; native charm bridge and unrelated-hover isolation retained\n");
            samples = null;
            saved = null;
            oldFeet = null;
            oldPlayer = null;
            oldServer = null;
            return true;
        }

        return false;
    }

    private static void aim(AbstractContainerScreen<?> screen, int slot) {
        hoverScreen = screen;
        targetSlot = slot;
        observed = 0;
        hoverStack = null;
        hoverLines = null;
        hoverBefore = AetherTooltipTestControl.encoded(Minecraft.getInstance(), screen.getMenu().getSlot(slot).getItem());
    }

    private static void checkHover(Minecraft client, StringBuilder report, String label) throws Exception {
        var slot = hoverScreen.getMenu().getSlot(targetSlot);
        require(hoverScreen.getHoveredSlot() == slot && ItemStack.matches(hoverStack, slot.getItem()), "Did not hover the actual native UI slot");
        require(hoverBefore.equals(AetherTooltipTestControl.encoded(client, slot.getItem())), "Actual UI hover changed root/history");
        require(hoverLines.stream().anyMatch(line -> line.getString().contains("3/3")), "Menu preview lost current equipment progress");
        var rows = AetherIITooltipClientTest.nativeSetRows(client, TooltipFlag.NORMAL);
        require(rows.size() == 2, "UI no-borrowing probe lacks an actually active Burrukai set");
        for (var row : rows) {
            require(!hoverLines.contains(row), "Actual menu hover borrowed worn modifiers");
        }
        require(hoverStack != client.player.getItemBySlot(EquipmentSlot.FEET), "Menu target aliases actual FEET");
        report.append("UI ")
              .append(label)
              .append(": real screen/hit-test/tooltip; target==slot ")
              .append(hoverStack == slot.getItem())
              .append("; real equipment count, no borrowed active rows\n");
    }

    private static boolean sameSaved(Minecraft client) {
        var ops = client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int i = 0; i < saved.size(); i++) {
            if (!ItemStack.matches(ItemStack.CODEC.parse(ops, saved.get(i)).getOrThrow(), client.player.getInventory().getItem(i))) {
                return false;
            }
        }
        return ItemStack.matches(ItemStack.CODEC.parse(ops, com.google.gson.JsonParser.parseString(savedFeet)).getOrThrow(),
                                 client.player.getItemBySlot(EquipmentSlot.FEET));
    }

    private static void neutralFacade(ItemStack root) {
        var lines = new ArrayList<Component>();
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        AetherTooltipCompatibility.source(data.left().source(), TooltipFlag.NORMAL, lines::add);
        AetherTooltipCompatibility.source(data.right().source(), TooltipFlag.NORMAL, lines::add);
        var context = AttributeTooltipContext.of(Minecraft.getInstance().player,
                                                 net.minecraft.world.item.Item.TooltipContext.EMPTY,
                                                 TooltipDisplay.DEFAULT,
                                                 TooltipFlag.NORMAL);
        AetherTooltipClient.attributes(new AddAttributeTooltipsEvent(root, lines::add, context));
        require(lines.stream()
                     .anyMatch(line -> line.getContents() instanceof TranslatableContents text && text.getKey().endsWith("ability.tooltip.1"))
                        && lines.stream().noneMatch(line -> line.getContents() instanceof TranslatableContents text
                        && (text.getKey().endsWith("ability.tooltip.2") || text.getKey().startsWith("attribute.modifier"))),
                "Missing player/world should omit counts and active attributes, not invent progress");
    }

    private static void boundaryProbes(Minecraft client, ItemStack worn) {
        var oldPlayer = client.player;
        var oldLevel = client.level;
        String before = AetherTooltipTestControl.encoded(client, worn);
        try {
            client.player = null;
            neutralFacade(worn);
            client.player = oldPlayer;
            client.level = null;
            neutralFacade(worn);
        } finally {
            client.player = oldPlayer;
            client.level = oldLevel;
        }

        var data = ModContent.getMixedBootsData(worn).orElseThrow();
        var left = data.left().source();
        var right = data.right().source();
        for (var source : List.of(left, right)) {
            if (source.has(component("armor_set"))) {
                source.set(component("armor_set"), TagKey.create(Registries.ITEM, Identifier.parse("aether_ii:armor/unknown_fixture_set")));
            }
        }

        var unknown = worn.copy();
        unknown.set(ModContent.mixedBootsDataType().orElseThrow(), new MixedBootsData(new BootHalf(left), new BootHalf(right)));
        var lines = AetherTooltipTestControl.capture(client, unknown, TooltipFlag.NORMAL, false).lines();
        require(lines.stream()
                     .noneMatch(line -> line.getContents() instanceof TranslatableContents text && text.getKey()
                                                                                                       .contains("nitrogen_internals.ability")),
                "Unknown set invented a known ability");
        require(before.equals(AetherTooltipTestControl.encoded(client, worn)), "Boundary probe changed original worn history");
    }
}
