package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityInvulnerabilityCheckEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;

import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real Player inventory/effect ticks, advanced on consecutive dedicated-server callbacks. */
public final class AllTheModiumChecks {
    private static final boolean BROKEN = Boolean.getBoolean("leggings.test.allTheModiumBroken");
    private static final List<Actor> actors = new ArrayList<>();
    private static boolean finished;
    private static int clock = 17;
    private static int calls;
    private static ItemStack observed;

    private AllTheModiumChecks() {
    }

    public static void observe(ItemStack stack) {
        calls++;
        observed = stack;
    }

    public static void tick(MinecraftServer server) {
        if (!server.isDedicatedServer() || finished || server.getTickCount() < 20) {
            return;
        }
        try {
            if (actors.isEmpty()) {
                if (!BROKEN) {
                    AllTheModiumWorkstationChecks.verify(server);
                }
                prepare(server);
            }
            clock++;
            for (var actor : actors) {
                advance(actor);
            }
            for (var actor : actors) {
                effects(actor);
            }
            if (clock == 142) {
                SkeletonChecks.passed("server");
                finished = true;
                server.halt(false);
            }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("server", failure);
            server.halt(false);
        }
    }

    private static void prepare(MinecraftServer server) {
        SkeletonChecks.verify();
        var level = server.overworld();
        // Disconnected actors do not hold player chunk tickets; fluid scans require a loaded chunk.
        level.setChunkForced(0, 0, true);
        for (int x = 0; x < 4; x++) {
            for (int y = 79; y < 84; y++) {
                for (int z = 0; z < 4; z++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState(), 3);
                }
            }
        }
        for (String tier : List.of("allthemodium", "vibranium", "unobtainium")) {
            var nativeStack = nativeItem(tier + "_leggings");
            add(server, nativeStack, true, true, EquipmentSlot.LEGS);
            for (var ordinary : List.of(Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS)) {
                for (var root : pair(nativeStack, new ItemStack(ordinary))) {
                    add(server, root, true, true, EquipmentSlot.LEGS);
                }
            }
        }
        for (String other : List.of("allthemodium", "vibranium")) {
            for (var root : pair(nativeItem("allthemodium_leggings"), nativeItem(other + "_leggings"))) {
                add(server, root, true, true, EquipmentSlot.LEGS);
            }
        }
        add(server, new ItemStack(Items.GOLDEN_LEGGINGS), false, true, EquipmentSlot.LEGS);
        add(server, new ItemStack(Items.IRON_LEGGINGS), false, false, EquipmentSlot.LEGS);
        for (var root : pair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS))) {
            add(server, root, false, false, EquipmentSlot.LEGS);
        }

        var root = pair(nativeItem("allthemodium_leggings"), new ItemStack(Items.IRON_LEGGINGS)).getFirst();
        var suspended = root.copy();
        suspended.set(LeggingsContent.createSuspendedDataType(),
                SuspendedLeggingsData.of(new CompoundTag(), new CompoundTag(), new CompoundTag()));
        var spoof = new ItemStack(Items.IRON_LEGGINGS);
        spoof.set(LeggingsContent.mixedLeggingsDataType(), root.get(LeggingsContent.mixedLeggingsDataType()));
        var malformed = root.copy();
        malformed.remove(DataComponents.EQUIPPABLE);
        for (var invalid : List.of(new ItemStack(root.getItem()), root.copyWithCount(2), suspended, spoof, malformed)) {
            add(server, invalid, false, false, EquipmentSlot.LEGS);
        }
        for (var slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
            add(server, root.copy(), false, false, slot);
        }
        add(server, root.copy(), false, false, null);
        // Native piglin safety applies in armor slots, but the wearer tick must remain LEGS-only.
        add(server, root.copy(), false, true, EquipmentSlot.CHEST);

        var player = RepairFixtures.player(server);
        player.setItemSlot(EquipmentSlot.LEGS, root);
        calls = 0;
        root.getItem().inventoryTick(root, level, player, EquipmentSlot.FEET);
        root.getItem().inventoryTick(root, level, player, null);
        root.getItem().inventoryTick(root.copy(), level, player, EquipmentSlot.LEGS);
        var stand = new ArmorStand(level, 8, 80, 0);
        stand.setItemSlot(EquipmentSlot.LEGS, root);
        root.getItem().inventoryTick(root, level, stand, EquipmentSlot.LEGS);
        require(calls == 0, "Wrong slot, detached copy or non-player reached native tick");

        if (Boolean.getBoolean("leggings.test.boots")) {
            var boots = BootsCoexistence.pair(nativeItem("allthemodium_boots"), new ItemStack(Items.DIAMOND_BOOTS)).getFirst();
            var legs = add(server, root.copy(), true, true, EquipmentSlot.LEGS);
            legs.player.setItemSlot(EquipmentSlot.FEET, boots);
            damage(legs.player, true);
            legs.player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            damage(legs.player, true);
            legs.player.setItemSlot(EquipmentSlot.LEGS, legs.stack);
            legs.player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            damage(legs.player, false);
        }
    }

    private static Actor add(MinecraftServer server, ItemStack stack, boolean tick, boolean piglins, EquipmentSlot slot) {
        var player = RepairFixtures.player(server);
        player.setNoGravity(true);
        player.setInvulnerable(true);
        if (slot == null) {
            player.getInventory().setItem(10, stack);
        } else {
            player.setItemSlot(slot, stack);
        }
        require(PiglinAi.isWearingSafeArmor(player) == piglins, "Wrong native piglin consumer result: " + stack);
        damage(player, false);
        for (var effect : List.of(MobEffects.POISON, MobEffects.WITHER, MobEffects.SLOW_FALLING)) {
            player.addEffect(new MobEffectInstance(effect, 500));
        }
        var data = stack.get(LeggingsContent.mixedLeggingsDataType());
        var actor = new Actor(player, stack, stack.copy(), data == null ? ItemStack.EMPTY : data.left().source().copy(),
                data == null ? ItemStack.EMPTY : data.right().source().copy(), tick);
        actors.add(actor);
        return actor;
    }

    private static void advance(Actor actor) {
        var player = actor.player;
        boolean water = clock > 20 && clock <= 40;
        player.setPos(water ? 1.5 : 8.5, 80, 1.5);
        player.setDeltaMovement(Vec3.ZERO);
        // ServerLevel normally increments this before the player tick. These disconnected actors
        // use the real Player inventory/effect flow without a socket or production tick shortcuts.
        player.tickCount = clock;
        if (clock == 61) {
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        }
        calls = 0;
        observed = null;
        player.doTick();
        require(player.isInWater() == water, "Fixture immersion mismatch at " + clock
                + "; fluid=" + player.level().getFluidState(player.blockPosition()) + "; pos=" + player.position());
        boolean active = actor.tick && clock <= 60 && !BROKEN;
        require(calls == (active ? 1 : 0), "Duplicate/missing/idle native invocation at " + clock + ": " + actor.stack);
        require(!active || observed == actor.stack, "Native receiver did not receive the live root/native stack");
        require(ItemStack.matches(actor.stack, actor.before), "Player tick rewrote root state");
        var data = actor.stack.get(LeggingsContent.mixedLeggingsDataType());
        require(data == null || ItemStack.matches(actor.leftBefore, data.left().source())
                        && ItemStack.matches(actor.rightBefore, data.right().source()),
                "Native receiver mutated stored source snapshots");
    }

    private static void effects(Actor actor) {
        var player = actor.player;
        boolean works = actor.tick && !BROKEN;
        require(player.hasEffect(MobEffects.POISON) == (!works || clock < 20)
                        && player.hasEffect(MobEffects.WITHER) == (!works || clock < 20),
                "Wrong native cure cadence at " + clock + ": " + actor.stack);
        require(player.hasEffect(MobEffects.SLOW_FALLING), "Cure removed an unrelated effect");
        var grace = player.getEffect(MobEffects.DOLPHINS_GRACE);
        boolean expected = works && clock >= 40 && clock < 140;
        require((grace != null) == expected, "Wrong water activation or dry/unequipped tail at " + clock
                + "; water=" + player.isInWater() + "; grace=" + grace + "; stack=" + actor.stack);
        if (expected) {
            require(grace.getAmplifier() == 0 && grace.getDuration() == 140 - clock,
                    "Native Dolphin's Grace duration/potency differs at " + clock);
            var nativeGrace = actors.getFirst().player.getEffect(MobEffects.DOLPHINS_GRACE);
            require(nativeGrace != null && grace.getDuration() == nativeGrace.getDuration(),
                    "Mixed effect differs from ticking native control");
        }
    }

    private static void damage(ServerPlayer player, boolean boots) {
        var level = player.level();
        var fall = new LivingFallEvent(player, 9, 1);
        NeoForge.EVENT_BUS.post(fall);
        require(fall.isCanceled() == boots && fall.getDistance() == 9 && fall.getDamageMultiplier() == 1,
                "Leggings gained fall protection or displaced Boots protection");
        for (var source : List.of(level.damageSources().lava(), level.damageSources().freeze(),
                level.damageSources().fellOutOfWorld(), level.damageSources().drown())) {
            var event = new EntityInvulnerabilityCheckEvent(player, source, false);
            NeoForge.EVENT_BUS.post(event);
            require(event.isInvulnerable() == (boots && source == level.damageSources().lava()),
                    "Unexpected leggings damage entitlement: " + source);
        }
        require(PowderSnowBlock.canEntityWalkOnPowderSnow(player) == boots, "Unexpected snow entitlement");
    }

    private static List<ItemStack> pair(ItemStack first, ItemStack second) {
        var pair = MixedLeggingsFactory.createPair(first, second, CompositionRules.defaults());
        return List.of(pair.primary(), pair.inverse());
    }

    private static ItemStack nativeItem(String name) {
        var id = Identifier.fromNamespaceAndPath("allthemodium", name);
        return new ItemStack(BuiltInRegistries.ITEM.get(id).orElseThrow().value());
    }

    private record Actor(ServerPlayer player, ItemStack stack, ItemStack before,
                         ItemStack leftBefore, ItemStack rightBefore, boolean tick) {}
}
