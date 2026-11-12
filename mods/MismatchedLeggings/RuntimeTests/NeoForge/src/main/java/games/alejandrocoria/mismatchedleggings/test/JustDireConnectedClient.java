package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.setup.JDTRegistration;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;

import static games.alejandrocoria.mismatchedleggings.test.JustDirePassiveChecks.close;
import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** No integrated-server access, client-created roots or client-authoritative settings. */
public final class JustDireConnectedClient {
    private static int ticks;
    private static int selected;
    private static boolean finished;

    private JustDireConnectedClient() {}

    public static void tick(Minecraft client) {
        if (finished) { return; }
        try {
            require(++ticks < 4800, "JDT connected client timeout at " + selected);
            require(GLFW.glfwGetWindowAttrib(client.getWindow().handle(), GLFW.GLFW_VISIBLE) == GLFW.GLFW_FALSE
                    && client.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER) == 0, "Client must remain hidden/muted");
            require(!Files.exists(JustDireConnectedServer.report("server.failure.txt")), "Dedicated endpoint failed");
            if (client.player == null || client.level == null || client.getOverlay() != null) { return; }
            require(client.getSingleplayerServer() == null, "JDT synchronization used an integrated server");
            if (client.screen != null) { return; }
            var root = client.player.getItemBySlot(EquipmentSlot.LEGS);
            if (!Files.exists(JustDireConnectedServer.report("case-" + selected + ".ready"))
                    || !root.has(LeggingsContent.mixedLeggingsDataType()) || !root.getHoverName().getString().equals("JDT connected " + selected)
                    || root.getOrDefault(JustDireDataComponents.FORGE_ENERGY, -1) != JustDireConnectedServer.charge(selected)) { return; }
            double phase = selected == 1 || selected == 2 || selected == 4 || selected == 6 ? 0 : 1;
            if (Math.abs(client.player.getAttributeValue(JDTRegistration.PHASE) - phase) > 1E-5) { return; }
            close(JustDirePassiveChecks.phase(root), phase, "Received root Phase projection");
            double armor = JustDirePassiveChecks.amount(root.getAttributeModifiers(), Attributes.ARMOR);
            if (Boolean.getBoolean("leggings.test.jdtBoots")) {
                var feet = client.player.getItemBySlot(EquipmentSlot.FEET);
                require(!feet.isEmpty() && !feet.has(LeggingsContent.mixedLeggingsDataType()), "Sibling FEET state was lost/routed as LEGS");
                armor += JustDirePassiveChecks.amount(feet.getAttributeModifiers(), Attributes.ARMOR);
                require(client.player.getAttributeValue(Attributes.STEP_HEIGHT) > client.player.getAttributeBaseValue(Attributes.STEP_HEIGHT), "Boots Step Assist stopped working");
            }
            close(client.player.getAttributeValue(Attributes.ARMOR), armor, "Actual synchronized armor differs from received roots");
            var snapshot = root.copy();
            movement(client, root, selected != 4);
            consumers(client, phase > 0);
            require(ItemStack.matches(root, snapshot), "Client observations edited authoritative state/history");
            Files.writeString(JustDireConnectedServer.report("case-" + selected + ".ok"), "Received root, worn attributes and native consumers matched\n");
            if (++selected == 8) {
                finished = true;
                SkeletonChecks.passed("client");
                client.stop();
            }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("client", failure);
            client.stop();
        }
    }

    private static void movement(Minecraft client, ItemStack root, boolean enabled) throws Exception {
        var player = client.player;
        var oldMotion = player.getDeltaMovement();
        float oldForward = player.zza;
        boolean sprint = player.isSprinting();
        boolean ground = player.onGround();
        double fall = player.fallDistance;
        var water = Entity.class.getDeclaredField("wasTouchingWater");
        water.setAccessible(true);
        boolean wet = water.getBoolean(player);
        try {
            player.zza = 1;
            player.setSprinting(false);
            player.setOnGround(true);
            player.fallDistance = 0;
            water.setBoolean(player, false);
            player.setDeltaMovement(Vec3.ZERO);
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
            close(player.getDeltaMovement().length(), enabled ? .2 : 0, "Received root native client callback executed zero/multiple times");
        } finally {
            player.zza = oldForward;
            player.setSprinting(sprint);
            player.setOnGround(ground);
            player.fallDistance = fall;
            water.setBoolean(player, wet);
            player.setDeltaMovement(oldMotion);
        }
    }

    private static void consumers(Minecraft client, boolean active) {
        var state = new net.minecraft.client.renderer.state.LightmapRenderState();
        var extractor = new net.minecraft.client.renderer.LightmapRenderStateExtractor(client.gameRenderer, client);
        extractor.tick();
        extractor.extract(state, 0);
        close(state.nightVisionEffectIntensity, active ? 1 : 0, "Native Phase lightmap consumer");
        var event = new net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent(client.player,
                new com.mojang.blaze3d.vertex.PoseStack(), net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent.OverlayType.BLOCK,
                Blocks.STONE.defaultBlockState(), client.player.blockPosition(), null, null);
        com.direwolf20.justdirethings.client.events.RenderEvents.onRenderBlockScreenEffect(event);
        require(event.isCanceled() == active, "Native Phase block overlay consumer");
        var position = client.player.blockPosition();
        var before = client.level.getBlockState(position);
        try {
            client.level.setBlock(position, Blocks.STONE.defaultBlockState(), 3);
            var box = new AABB(position).deflate(.1);
            boolean collides = client.level.getBlockCollisions(client.player, box).iterator().hasNext();
            require(collides != active, "Native connected wall collision consumer");
        } finally { client.level.setBlock(position, before, 3); }
    }
}
