package games.alejandrocoria.jdtspike;

import games.alejandrocoria.jdtspike.mixin.ScreenAccess;

import com.direwolf20.justdirethings.client.screens.ToolSettingScreen;
import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool;
import com.direwolf20.justdirethings.setup.JDTRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ClientProbe {
    private static int ticks;
    private static int stage;
    private static int pause;
    private static boolean finished;
    private static CompletableFuture<Void> pending;

    private ClientProbe() {
    }

    public static void setup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post tick) -> tick(Minecraft.getInstance()));
    }

    private static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            Checks.require(++ticks < 1800, "Client spike timed out at stage " + stage);
            Checks.require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE,
                    "Client became visible");
            if (client.player == null || client.level == null || client.getOverlay() != null) { return; }
            if (pending != null && !pending.isDone()) { return; }
            if (pending != null) { pending.join(); pending = null; }
            if (pause-- > 0) { return; }
            if (stage == 0) {
                if (client.screen != null) { return; }
                Checks.movement(client.player, true);
                pending = client.getSingleplayerServer().submit(() -> {
                    var owner = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    owner.setInvulnerable(true);
                    var roots = Checks.pair(Checks.nativeStack("eclipsealloy"), new ItemStack(Items.DIAMOND_LEGGINGS));
                    roots.getFirst().set(JustDireDataComponents.FORGE_ENERGY, 50);
                    owner.setItemSlot(EquipmentSlot.LEGS, roots.getFirst());
                });
                stage++;
                pause = 15;
            } else if (stage == 1) {
                if (client.player.getAttributeValue(JDTRegistration.PHASE) != 1) { return; }
                consumers(client, true);
                pending = client.getSingleplayerServer().submit(() -> {
                    var worn = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst().getItemBySlot(EquipmentSlot.LEGS);
                    Checks.close(worn.getOrDefault(JustDireDataComponents.FORGE_ENERGY, 0), 50, "Native Phase drained FE while worn");
                    worn.set(JustDireDataComponents.FORGE_ENERGY, 49);
                });
                stage++;
                pause = 15;
            } else if (stage == 2) {
                if (client.player.getAttributeValue(JDTRegistration.PHASE) != 0) { return; }
                consumers(client, false);
                Checks.note("phase-sync.txt", "Live worn root 50 -> 49 FE: actual server equipment recalculation and native synced Phase attribute reached client, without recomposition; native lightmap/block-overlay positives and negatives passed; no FE drain while active\n");
                pending = client.getSingleplayerServer().submit(() -> {
                    var owner = client.getSingleplayerServer().getPlayerList().getPlayers().getFirst();
                    owner.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
                    var roots = Checks.pair(Checks.nativeStack("eclipsealloy"), new ItemStack(Items.DIAMOND_LEGGINGS));
                    owner.getInventory().setItem(0, roots.getFirst());
                    owner.getInventory().setItem(1, Checks.nativeStack("blazegold"));
                    owner.getInventory().setItem(2, roots.getLast());
                    if (Boolean.getBoolean("jdt.spike.boots")) {
                        var boot = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
                                net.minecraft.resources.Identifier.fromNamespaceAndPath("justdirethings", "eclipsealloy_boots")));
                        boot.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.JUMPBOOST), true);
                        owner.getInventory().setItem(3, games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory
                                .createPair(boot, new ItemStack(Items.DIAMOND_BOOTS)).primary());
                    }
                    owner.inventoryMenu.broadcastChanges();
                    owner.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, player) ->
                            new com.direwolf20.justdirethings.common.containers.ToolSettingContainer(id, inv, player), Component.literal("JDT spike")));
                });
                stage++;
                pause = 15;
            } else if (stage == 3) {
                if (!(client.screen instanceof ToolSettingScreen screen)) { return; }
                screenChecks(screen);
                client.player.closeContainer();
                Checks.note("screen.txt", "Actual native menu and slots: mixed/native/inverse/optional Boots selection, range/Phase widgets, local edit destination, source immutability and capture cancellation passed. Leggings edit transport intentionally not implemented\n");
                Spike.result("client", null);
                finished = true;
                client.stop();
            }
        } catch (Throwable failure) {
            finished = true;
            Spike.result("client", failure);
            client.stop();
        }
    }

    private static void consumers(Minecraft client, boolean active) {
        var state = new net.minecraft.client.renderer.state.LightmapRenderState();
        var extractor = new net.minecraft.client.renderer.LightmapRenderStateExtractor(client.gameRenderer, client);
        extractor.tick();
        extractor.extract(state, 0);
        Checks.close(state.nightVisionEffectIntensity, active ? 1 : 0, "Native Phase lightmap consumer");
        var event = new net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent(client.player,
                new com.mojang.blaze3d.vertex.PoseStack(),
                net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent.OverlayType.BLOCK,
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), client.player.blockPosition(), null, null);
        com.direwolf20.justdirethings.client.events.RenderEvents.onRenderBlockScreenEffect(event);
        Checks.require(event.isCanceled() == active, "Native Phase block overlay consumer");
    }

    private static void screenChecks(ToolSettingScreen screen) throws Exception {
        var access = (ScreenAccess) screen;
        Checks.require(Candidate.mixed(access.spikeTool()), "Mixed Leggings not selected at native screen entry");
        verifyLeggings(screen);
        var first = access.spikeTool();
        var sources = Candidate.sources(first);
        var snapshot = first.copy();
        screen.setSetting(Ability.WALKSPEED.getName(), 3);
        Checks.close(ToggleableTool.getToolValue(Candidate.view(first, Ability.WALKSPEED), Ability.WALKSPEED.getName()), 3,
                "Slider did not address actual root");
        screen.toggleSetting(Ability.PHASE.getName());
        Checks.require(!ToggleableTool.getSetting(first, Ability.PHASE.getName()), "Phase toggle not on selected root");
        Checks.require(ItemStack.matches(Candidate.sources(first).getFirst(), sources.getFirst())
                && ItemStack.matches(Candidate.sources(first).getLast(), sources.getLast()), "UI changed history");
        access.spikeCapture(true);
        select(screen, 1);
        Checks.require(!access.spikeCapture() && !Candidate.mixed(access.spikeTool()), "Native slot did not cancel mixed capture");
        Checks.require(access.spikeButtons().containsValue(Ability.LAVAREPAIR)
                && !access.spikeButtons().containsValue(Ability.PHASE), "Native Blazegold controls changed");
        select(screen, 2);
        verifyLeggings(screen);
        Checks.require(ToggleableTool.getSetting(access.spikeTool(), Ability.PHASE.getName()), "Setting leaked to inverse");
        if (Boolean.getBoolean("jdt.spike.boots")) {
            select(screen, 3);
            var boots = (games.alejandrocoria.mismatchedboots.client.compatibility.justdirethings.JustDireSettingsScreenAccess) screen;
            Checks.require(boots.mismatchedboots$jdtReady(), "Candidate disabled Boots local transformed-shape guard");
            Checks.require(access.spikeButtons().containsValue(Ability.JUMPBOOST)
                    && !access.spikeButtons().containsValue(Ability.WALKSPEED), "Boots selection routed into Leggings controls");
            select(screen, 0);
            verifyLeggings(screen);
        }
        // The local candidate deliberately does not promise server-side edit synchronization.
        Checks.require(!ItemStack.matches(first, snapshot), "UI edit positive control absent");
    }

    private static void verifyLeggings(ToolSettingScreen screen) {
        var access = (ScreenAccess) screen;
        Checks.require(access.spikeButtons().values().containsAll(List.of(Ability.WALKSPEED, Ability.RUNSPEED,
                Ability.SWIMSPEED, Ability.DECOY, Ability.PHASE)), "Missing native ability widgets");
        var phaseButton = access.spikeButtons().entrySet().stream().filter(entry -> entry.getValue() == Ability.PHASE).findFirst().orElseThrow().getKey();
        Checks.require(!access.spikeSliders().containsKey(phaseButton), "Phase gained a meaningless slider");
        Checks.require(access.spikeSliders().size() == 3, "Movement sliders not native");
    }

    private static void select(ToolSettingScreen screen, int index) throws Exception {
        var slot = screen.getMenu().slots.stream().filter(candidate -> candidate.getSlotIndex() == index
                && candidate.container == Minecraft.getInstance().player.getInventory()).findFirst().orElseThrow();
        field(screen.getClass(), "hoveredSlot").set(screen, slot);
        screen.mouseClicked(new MouseButtonEvent(0, 0, new MouseButtonInfo(0, 0)), false);
        Checks.require(((ScreenAccess) screen).spikeSlot() == index, "Native selection chose wrong inventory slot");
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        for (var current = type; current != null; current = current.getSuperclass()) {
            try {
                var field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException missing) {
                // Native protected slot is declared in AbstractContainerScreen, not the JDT subclass.
            }
        }
        throw new NoSuchFieldException(name);
    }
}
