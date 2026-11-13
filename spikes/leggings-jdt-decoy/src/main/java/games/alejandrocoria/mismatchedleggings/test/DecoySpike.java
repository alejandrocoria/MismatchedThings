package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.entities.DecoyEntity;
import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.common.items.interfaces.AbilityMethods;
import com.direwolf20.justdirethings.common.items.interfaces.LeftClickableTool;
import com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool;
import com.direwolf20.justdirethings.common.items.interfaces.ToolRecords.AbilityBinding;
import com.direwolf20.justdirethings.common.items.interfaces.ToolRecords.AbilityCooldown;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireInputNetwork;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.test.mixin.EquipmentUpdatesInvoker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Bounded candidate/native comparisons, followed by three actual TCP/HUD observations. */
public final class DecoySpike {
    public static boolean captureSounds;
    public static final List<Identifier> sounds = new ArrayList<>();
    private static boolean cancelSpawn;
    private static boolean ready;
    private static boolean seeded;
    private static boolean finished;
    private static int ticks;
    private static int stage;

    private DecoySpike() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            if (cancelSpawn && event.getEntity() instanceof DecoyEntity) { event.setCanceled(true); }
        });
    }

    public static void sound(SoundEvent event) { sounds.add(BuiltInRegistries.SOUND_EVENT.getKey(event)); }

    static ItemStack root(String tier, boolean inverse, int energy) {
        var source = DecoyCandidate.nativeItem(tier);
        source.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.DECOY), true);
        source.set(JustDireDataComponents.FORGE_ENERGY, Math.min(tier.equals("celestigem") ? 10000 : 500000, energy * 2));
        var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.DIAMOND_LEGGINGS));
        var root = inverse ? pair.inverse() : pair.primary();
        root.set(JustDireDataComponents.FORGE_ENERGY, energy);
        return root;
    }

    private static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer()) { return; }
        try {
            require(++ticks < 3600, "Decoy research timed out at " + stage);
            if (ticks == 20) {
                server.overworld().setChunkForced(0, 0, true);
                server.overworld().getChunkAt(new BlockPos(0, 70, 0));
            }
            if (!ready && ticks > 20 && !server.overworld().areEntitiesActuallyLoadedAndTicking(new net.minecraft.world.level.ChunkPos(0, 0))) {
                require(ticks < 200, "Native entity chunk did not become ticking");
                return;
            }
            if (!ready && ticks > 20) {
                JustDireFoundationChecks.graph();
                checks(server);
                Files.writeString(JustDireConnectedServer.report("ready.txt"), "Native and candidate Decoy checks passed\n");
                ready = true;
            }
            require(!Files.exists(JustDireConnectedServer.report("client.failure.txt")), "Decoy client failed");
            if (seeded && stage == 2 && Files.exists(JustDireConnectedServer.report("client.ok"))) {
                Files.writeString(JustDireConnectedServer.report("connected.txt"), "Native entity and three root/HUD states observed by independent TCP client\n");
                SkeletonChecks.passed("server");
                finished = true;
                server.overworld().setChunkForced(0, 0, false);
                server.halt(false);
                return;
            }
            if (!ready || server.getPlayerList().getPlayers().isEmpty()) { return; }
            var player = server.getPlayerList().getPlayers().getFirst();
            if (!seeded) {
                player.getInventory().clearContent();
                player.setInvulnerable(true);
                player.setNoGravity(true);
                player.connection.teleport(.5, 70, .5, 0, 0);
                var root = root("eclipsealloy", true, 10000);
                player.setItemSlot(EquipmentSlot.LEGS, root);
                require(DecoyCandidate.activate(player, JustDireInputNetwork.legsSlot(), root, player.level()::addFreshEntity, false), "Connected candidate refused");
                if (Boolean.getBoolean("leggings.test.jdtBoots")) {
                    var nativeBoots = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:eclipsealloy_boots")));
                    var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
                    var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, nativeBoots, new ItemStack(Items.DIAMOND_BOOTS));
                    var boots = (ItemStack) pair.getClass().getMethod("primary").invoke(pair);
                    boots.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(new AbilityCooldown("groundstomp", 100000, false)));
                    player.setItemSlot(EquipmentSlot.FEET, boots);
                }
                seeded = true;
                publish(player);
            } else if (stage < 2 && Files.exists(JustDireConnectedServer.report("case-" + stage + ".ok"))) {
                var root = player.getItemBySlot(EquipmentSlot.LEGS);
                root.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(new AbilityCooldown("decoy", 1, stage == 0)));
                DecoyCandidate.tick(player);
                stage++;
                publish(player);
            }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("server", failure);
            server.overworld().setChunkForced(0, 0, false);
            server.halt(false);
        }
    }

    private static void publish(ServerPlayer player) throws Exception {
        ((EquipmentUpdatesInvoker) player).leggings$detectEquipmentUpdates();
        player.inventoryMenu.broadcastChanges();
        Files.writeString(JustDireConnectedServer.report("case-" + stage + ".ready"), "Authoritative candidate stage ready\n");
    }

    private static void checks(MinecraftServer server) throws Exception {
        var player = RepairFixtures.player(server);
        player.setNoGravity(true);
        player.setPos(.5, 70, .5);
        captureSounds = true;
        try {
            for (String tier : List.of("celestigem", "eclipsealloy")) {
                for (boolean inverse : List.of(false, true)) {
                    for (int charge : List.of(0, 4999, 5000)) {
                        var root = root(tier, inverse, charge);
                        var history = root.get(LeggingsContent.mixedLeggingsDataType());
                        player.getInventory().setItem(0, root);
                        var nativeStack = DecoyCandidate.view(root);
                        sounds.clear();
                        boolean nativeReturn = AbilityMethods.decoy(player.level(), player, nativeStack);
                        boolean nativeSuccess = nativeStack.has(JustDireDataComponents.ABILITY_COOLDOWNS);
                        require(!nativeReturn && nativeSuccess == (charge >= 5000), "Native Decoy return/affordability changed");
                        var nativeSounds = List.copyOf(sounds);
                        require(!nativeSuccess || nativeSounds.equals(List.of(Identifier.parse("minecraft:entity.evoker.prepare_summon"))),
                                "Native summon sound probe did not observe the real emission");
                        var nativeName = nativeSuccess ? entities(player).getFirst().getName() : null;
                        clearEntities(player);
                        sounds.clear();
                        boolean result = DecoyCandidate.activate(player, 0, root, player.level()::addFreshEntity, false);
                        require(result == nativeSuccess && sounds.equals(nativeSounds)
                                        && root.getOrDefault(JustDireDataComponents.FORGE_ENERGY, 0)
                                        == nativeStack.getOrDefault(JustDireDataComponents.FORGE_ENERGY, 0)
                                        && root.getDamageValue() == 0 && history.equals(root.get(LeggingsContent.mixedLeggingsDataType())),
                                "Candidate action/payment/sound differs from native " + tier + '/' + inverse + '/' + charge);
                        if (result) {
                            var entity = entities(player).getFirst();
                            require(entity.getOwnerUUID().orElseThrow().equals(player.getUUID())
                                            && entity.getName().equals(nativeName), "Native entity owner/name lost");
                            var mob = EntityType.ZOMBIE.create((net.minecraft.server.level.ServerLevel) player.level(), EntitySpawnReason.COMMAND);
                            mob.setPos(1.5, 70, .5);
                            mob.setNoAi(true);
                            player.level().addFreshEntity(mob);
                            entity.aggroMobs();
                            require(mob.getTarget() == entity, "Native Decoy AI was not reused");
                            entity.tickCount = 199;
                            entity.tick();
                            require(!entity.isRemoved(), "Native entity expired before tick 200");
                            entity.tickCount = 200;
                            entity.tick();
                            require(entity.isRemoved(), "Native entity duration was not retained");
                            mob.discard();
                            require(!DecoyCandidate.activate(player, 0, root, player.level()::addFreshEntity, false), "Active cooldown allowed duplicate cast");
                        }
                        clearEntities(player);
                    }
                }
                clock(player, tier);
            }
            refusal(player);
            providers(player);
            bindings(player);
            sourceImport(player);
            Files.writeString(JustDireConnectedServer.report("decoy-spike.txt"),
                    "Native false-success/FE thresholds; both tiers/orientations; native entity owner/AI/lifetime; root payment/rollback; both clock phases/sounds; source clock import limitation passed\n");
        } finally {
            captureSounds = false;
            clearEntities(player);
        }
    }

    private static void clock(ServerPlayer player, String tier) {
        var root = root(tier, false, 10000);
        var nativeStack = DecoyCandidate.view(root);
        var clock = new AbilityCooldown("decoy", 200, true);
        root.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(clock));
        nativeStack.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(clock));
        player.setItemSlot(EquipmentSlot.LEGS, root);
        int total = 200 + DecoyCandidate.parameters(root).cooldown;
        for (int i = 0; i < total; i++) {
            sounds.clear();
            ToggleableTool.tickCooldowns(player.level(), nativeStack, player);
            var nativeSounds = List.copyOf(sounds);
            require(nativeSounds.equals(i == 199 ? List.of(Identifier.parse("minecraft:block.conduit.deactivate"))
                            : i == total - 1 ? List.of(Identifier.parse("minecraft:entity.ender_eye.death")) : List.of()),
                    "Native completion sound probe changed at " + i);
            sounds.clear();
            DecoyCandidate.tick(player);
            require(java.util.Objects.equals(root.get(JustDireDataComponents.ABILITY_COOLDOWNS), nativeStack.get(JustDireDataComponents.ABILITY_COOLDOWNS))
                            && sounds.equals(nativeSounds), "Candidate clock/sounds differ from native at " + tier + '/' + i);
        }
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        root.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(clock));
        var before = root.copy();
        DecoyCandidate.tick(player);
        require(ItemStack.matches(root, before), "Unequipped clock did not freeze");
    }

    private static void refusal(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        var root = root("eclipsealloy", false, 10000);
        player.getInventory().setItem(0, root);
        var before = root.copy();
        require(!DecoyCandidate.activate(player, 0, root, player.level()::addFreshEntity, true)
                        && ItemStack.matches(root, before) && entities(player).isEmpty(), "Partial debit escaped rollback");
        cancelSpawn = true;
        try {
            require(!DecoyCandidate.activate(player, 0, root, player.level()::addFreshEntity, false)
                            && ItemStack.matches(root, before) && entities(player).isEmpty(), "Cancelled entity spent root state");
        } finally { cancelSpawn = false; }
        require(!DecoyCandidate.activate(player, 0, root.copy(), player.level()::addFreshEntity, false), "Stale root reference activated");
        var original = root;
        require(!DecoyCandidate.activate(player, 0, root, entity -> {
                    boolean added = player.level().addFreshEntity(entity);
                    player.getInventory().setItem(0, original.copy());
                    return added;
                }, false) && ItemStack.matches(original, before)
                        && ItemStack.matches(player.getInventory().getItem(0), before) && entities(player).isEmpty(),
                "Spawn callback replacement left a paid root or free entity");
        player.getInventory().setItem(0, root);
        root.set(JustDireDataComponents.TOOL_ENABLED, false);
        require(!DecoyCandidate.activate(player, 0, root, player.level()::addFreshEntity, false), "Disabled root activated");
        player.getInventory().setItem(0, new ItemStack(root.getItem()));
        require(!DecoyCandidate.activate(player, 0, player.getInventory().getItem(0), player.level()::addFreshEntity, false), "Bare root activated");
        root = root("eclipsealloy", false, 10000);
        player.getInventory().setItem(0, root);
        root.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 3);
        var nativeStack = DecoyCandidate.view(root);
        AbilityMethods.decoy(player.level(), player, nativeStack);
        clearEntities(player);
        require(DecoyCandidate.activate(player, 0, root, player.level()::addFreshEntity, false)
                        && root.get(JustDireDataComponents.FORGE_ENERGY).equals(nativeStack.get(JustDireDataComponents.FORGE_ENERGY)),
                "Unbreaking-adjusted native FE price differs");
        clearEntities(player);
        player.setGameMode(GameType.CREATIVE);
        root = root("eclipsealloy", false, 10000);
        player.getInventory().setItem(0, root);
        require(DecoyCandidate.activate(player, 0, root, player.level()::addFreshEntity, false)
                        && root.get(JustDireDataComponents.FORGE_ENERGY) == 10000, "Creative payment differs");
        clearEntities(player);
        player.setGameMode(GameType.SURVIVAL);
    }

    private static void sourceImport(ServerPlayer player) {
        var first = DecoyCandidate.nativeItem("celestigem");
        first.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(new AbilityCooldown("decoy", 200, true)));
        var pair = MixedLeggingsFactory.createPair(first, new ItemStack(Items.DIAMOND_LEGGINGS));
        require(pair.primary().has(JustDireDataComponents.ABILITY_COOLDOWNS)
                        && !pair.inverse().has(JustDireDataComponents.ABILITY_COOLDOWNS), "Foundation owner-only clock characterization changed");
        var second = DecoyCandidate.nativeItem("eclipsealloy");
        second.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(new AbilityCooldown("decoy", 1200, false)));
        var mixed = MixedLeggingsFactory.createPair(first, second);
        require(DecoyCandidate.view(mixed.primary()).is(second.getItem()) && DecoyCandidate.view(mixed.inverse()).is(second.getItem())
                        && !mixed.primary().get(JustDireDataComponents.ABILITY_COOLDOWNS)
                        .equals(mixed.inverse().get(JustDireDataComponents.ABILITY_COOLDOWNS)),
                "Effective tier or owner-only unequal clock characterization changed");
        var originalFirst = first.copy();
        var originalSecond = second.copy();
        var template = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:template_eclipsealloy")));
        var ingot = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:eclipsealloy_ingot")));
        var menu = SmithingChecks.ready(player, template, first.copy(), ingot);
        var upgraded = menu.getSlot(3).safeTake(1, 1, player);
        require(upgraded.is(second.getItem()) && !menu.getSlot(0).hasItem() && !menu.getSlot(1).hasItem() && !menu.getSlot(2).hasItem(),
                "Native live-clock tier transformation was not paid");
        for (var root : List.of(mixed.primary(), mixed.inverse())) {
            DecoyCandidate.importClock(root);
            require(DecoyCandidate.clocks(root).equals(DecoyCandidate.clocks(upgraded)),
                    "Both outputs did not inherit the restrictive clock under the effective native tier");
            player.setItemSlot(EquipmentSlot.LEGS, root);
            var nativeControl = upgraded.copy();
            for (int i = 0; i < 200; i++) {
                ToggleableTool.tickCooldowns(player.level(), nativeControl, player);
                DecoyCandidate.tick(player);
            }
            require(DecoyCandidate.clocks(root).equals(DecoyCandidate.clocks(nativeControl)),
                    "Inherited active phase disagrees with actual native tier upgrade");
        }
        require(ItemStack.matches(first, originalFirst) && ItemStack.matches(second, originalSecond), "Clock import changed original sources");
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
    }

    private static void bindings(ServerPlayer player) {
        var root = root("eclipsealloy", false, 10000);
        root.set(JustDireDataComponents.ABILITY_BINDING_MODES.get(Ability.DECOY), 2);
        root.set(JustDireDataComponents.ABILITY_BINDINGS, List.of(new AbilityBinding("decoy", 71, false, true)));
        var nativeStack = DecoyCandidate.view(root);
        player.setItemSlot(EquipmentSlot.LEGS, nativeStack);
        require(LeftClickableTool.getCustomBindingListFor(nativeStack, 71, false, player).contains(Ability.DECOY),
                "Native active custom binding positive control failed");
        player.setItemSlot(EquipmentSlot.LEGS, root);
        require(LeftClickableTool.getCustomBindingListFor(root, 71, false, player).isEmpty()
                        && LeftClickableTool.getCustomBindingListFor(nativeStack, 71, false, player).isEmpty(),
                "Native helper equipped-identity characterization changed");
        require(DecoyCandidate.bound(root, 71, false, true) && !DecoyCandidate.bound(root, 71, false, false)
                        && !DecoyCandidate.bound(root, 72, false, true), "Local actual-slot active binding proof failed");
        root.set(JustDireDataComponents.ABILITY_BINDINGS, List.of(new AbilityBinding("decoy", 4, true, false)));
        require(LeftClickableTool.getCustomBindingListFor(root, 4, true, player).contains(Ability.DECOY)
                        && DecoyCandidate.bound(root, 4, true, false) && !DecoyCandidate.bound(root, 4, false, false),
                "Native/local inventory mouse binding proof failed");
        var params = DecoyCandidate.parameters(root);
        require(params.minSlider == 1 && params.maxSlider == 1, "Decoy native fixed-value metadata changed");
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
    }

    private static void providers(ServerPlayer player) {
        var celestigem = DecoyCandidate.nativeItem("celestigem");
        celestigem.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.DECOY), true);
        celestigem.set(JustDireDataComponents.FORGE_ENERGY, 10000);
        for (String tier : List.of("celestigem", "eclipsealloy")) {
            var other = DecoyCandidate.nativeItem(tier);
            other.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.DECOY), true);
            other.set(JustDireDataComponents.FORGE_ENERGY, 10000);
            var pair = MixedLeggingsFactory.createPair(celestigem, other);
            for (var root : List.of(pair.primary(), pair.inverse())) {
                var history = root.get(LeggingsContent.mixedLeggingsDataType());
                player.getInventory().setItem(0, root);
                require(DecoyCandidate.parameters(root).cooldown == ((ToggleableTool) other.getItem()).getAbilityParams(Ability.DECOY).cooldown,
                        "Duplicate/unequal providers selected owner metadata");
                require(DecoyCandidate.activate(player, 0, root, player.level()::addFreshEntity, false), "Two-provider candidate refused");
                require(entities(player).size() == 1 && root.get(JustDireDataComponents.FORGE_ENERGY) == 5000,
                        "Two providers spawned/paid twice: entities=" + entities(player).size() + ", FE=" + root.get(JustDireDataComponents.FORGE_ENERGY));
                require(history.equals(root.get(LeggingsContent.mixedLeggingsDataType())), "Two-provider action edited source histories");
                clearEntities(player);
            }
        }
    }

    private static List<DecoyEntity> entities(ServerPlayer player) {
        return player.level().getEntitiesOfClass(DecoyEntity.class, new AABB(-20, 40, -20, 20, 100, 20));
    }

    private static void clearEntities(ServerPlayer player) { entities(player).forEach(DecoyEntity::discard); }
}
