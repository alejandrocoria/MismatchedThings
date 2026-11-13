package games.alejandrocoria.mismatchedleggings.test;

import com.direwolf20.justdirethings.common.entities.DecoyEntity;
import com.direwolf20.justdirethings.common.items.datacomponents.JustDireDataComponents;
import com.direwolf20.justdirethings.common.items.interfaces.Ability;
import com.direwolf20.justdirethings.common.items.interfaces.AbilityMethods;
import com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool;
import com.direwolf20.justdirethings.common.items.interfaces.ToolRecords.AbilityCooldown;
import com.direwolf20.justdirethings.setup.Config;
import games.alejandrocoria.mismatchedleggings.compatibility.justdirethings.JustDireDecoy;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsRecomposer;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Actual packaged action/state, with independent native controls and narrowly scoped failure probes. */
public final class JustDireDecoyChecks {
    public static boolean captureSounds;
    public static boolean partialDebit;
    public static final List<Identifier> sounds = new ArrayList<>();
    private static boolean cancelSpawn;
    private static ServerPlayer replacePlayer;
    private static boolean finished;

    private JustDireDecoyChecks() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            if (!(event.getEntity() instanceof DecoyEntity)) { return; }
            if (cancelSpawn) { event.setCanceled(true); }
            if (replacePlayer != null) {
                var root = replacePlayer.getInventory().getItem(0);
                replacePlayer.getInventory().setItem(0, root.copy());
            }
        });
    }

    private static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer() || server.getTickCount() < 20) { return; }
        if (server.getTickCount() == 20) {
            server.overworld().setChunkForced(0, 0, true);
            server.overworld().getChunkAt(new BlockPos(0, 70, 0));
        }
        if (!server.overworld().areEntitiesActuallyLoadedAndTicking(new ChunkPos(0, 0)) && server.getTickCount() < 200) { return; }
        finished = true;
        captureSounds = true;
        try {
            require(server.getTickCount() < 200, "Decoy entity chunk never became ticking");
            JustDireFoundationChecks.graph();
            var player = RepairFixtures.player(server);
            player.setNoGravity(true);
            player.setPos(.5, 70, .5);
            actions(player);
            clocks(player);
            refusals(player);
            inheritance(player);
            invalidClocks(player);
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "decoy-action.txt"),
                    "Packaged Decoy: native entity/action/nominal FE/Unbreaking/creative parity, cancellation/partial rollback/stale replacement, worn two-phase clock/sounds, both-output effective-tier import/native paid upgrade, live preservation, invalid locks and wide counters passed\n");
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "foundation.txt"), "Production Decoy A dedicated acceptance passed\n");
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            captureSounds = false;
            partialDebit = false;
            replacePlayer = null;
            server.overworld().setChunkForced(0, 0, false);
            server.halt(false);
        }
    }

    private static ItemStack nativeItem(String tier) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:" + tier + "_leggings")));
    }

    private static ItemStack root(String tier, boolean inverse, int charge) {
        var source = nativeItem(tier);
        source.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.DECOY), true);
        var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.DIAMOND_LEGGINGS));
        var result = inverse ? pair.inverse() : pair.primary();
        result.set(JustDireDataComponents.FORGE_ENERGY, charge);
        return result;
    }

    private static ItemStack control(ItemStack root, String tier) {
        var nativeStack = nativeItem(tier);
        nativeStack.applyComponents(root.getComponents());
        return nativeStack;
    }

    private static List<DecoyEntity> entities(ServerPlayer player) {
        return player.level().getEntitiesOfClass(DecoyEntity.class, new AABB(-20, 40, -20, 20, 100, 20));
    }

    private static void clear(ServerPlayer player) {
        entities(player).forEach(DecoyEntity::discard);
    }

    private static void wornTick(ServerPlayer player) {
        var stack = player.getItemBySlot(EquipmentSlot.LEGS);
        stack.getItem().inventoryTick(stack, player.level(), player, EquipmentSlot.LEGS);
    }

    private static void actions(ServerPlayer player) {
        for (String tier : List.of("celestigem", "eclipsealloy")) {
            for (boolean inverse : List.of(false, true)) {
                for (int charge : List.of(0, 4999, 5000)) {
                    var root = root(tier, inverse, charge);
                    var history = root.get(LeggingsContent.mixedLeggingsDataType());
                    player.getInventory().setItem(0, root);
                    var nativeStack = control(root, tier);
                    sounds.clear();
                    boolean nativeReturn = AbilityMethods.decoy(player.level(), player, nativeStack);
                    boolean success = nativeStack.has(JustDireDataComponents.ABILITY_COOLDOWNS);
                    var nativeSounds = List.copyOf(sounds);
                    require(!nativeReturn && success == (charge >= 5000), "Native Decoy receipt/affordability changed");
                    require(!success || nativeSounds.equals(List.of(Identifier.parse("minecraft:entity.evoker.prepare_summon"))), "Native summon probe is not active: " + nativeSounds);
                    var nativeName = success ? entities(player).getFirst().getName() : null;
                    clear(player);
                    sounds.clear();
                    require(JustDireDecoy.activate(player, 0, root) == success && sounds.equals(nativeSounds)
                                    && root.getOrDefault(JustDireDataComponents.FORGE_ENERGY, 0)
                                    == nativeStack.getOrDefault(JustDireDataComponents.FORGE_ENERGY, 0)
                                    && root.getDamageValue() == 0 && history.equals(root.get(LeggingsContent.mixedLeggingsDataType())),
                            "Production action/payment/history differs from native " + tier + '/' + inverse + '/' + charge);
                    if (success) {
                        require(entities(player).size() == 1, "Action did not create exactly one decoy");
                        var entity = entities(player).getFirst();
                        require(entity.getOwnerUUID().orElseThrow().equals(player.getUUID()) && entity.getName().equals(nativeName), "Native owner/name lost");
                        var mob = EntityType.ZOMBIE.create(player.level(), EntitySpawnReason.COMMAND);
                        mob.setPos(1.5, 70, .5);
                        mob.setNoAi(true);
                        player.level().addFreshEntity(mob);
                        entity.aggroMobs();
                        require(mob.getTarget() == entity, "Native Decoy attraction lost");
                        entity.tickCount = 199;
                        entity.tick();
                        require(!entity.isRemoved(), "Entity expired early");
                        entity.tickCount = 200;
                        entity.tick();
                        require(entity.isRemoved(), "Native entity expiry lost");
                        mob.discard();
                        var after = root.copy();
                        sounds.clear();
                        require(!JustDireDecoy.activate(player, 0, root) && ItemStack.matches(root, after) && sounds.isEmpty(), "Cooldown replay spent or spawned");
                    }
                    clear(player);
                }
            }
        }
        for (String tier : List.of("celestigem", "eclipsealloy")) {
            var root = root(tier, false, 10000);
            root.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 3);
            var nativeStack = control(root, tier);
            player.getInventory().setItem(0, root);
            AbilityMethods.decoy(player.level(), player, nativeStack);
            clear(player);
            require(JustDireDecoy.activate(player, 0, root) && root.get(JustDireDataComponents.FORGE_ENERGY)
                    .equals(nativeStack.get(JustDireDataComponents.FORGE_ENERGY)), "Unbreaking native debit differs");
            clear(player);
        }
        player.setGameMode(GameType.CREATIVE);
        for (int charge : List.of(0, 4999, 5000)) {
            var root = root("eclipsealloy", false, charge);
            player.getInventory().setItem(0, root);
            require(JustDireDecoy.activate(player, 0, root) == (charge >= 5000)
                    && root.get(JustDireDataComponents.FORGE_ENERGY) == charge, "Creative bypassed admission or paid");
            clear(player);
        }
        player.setGameMode(GameType.SURVIVAL);
    }

    private static void clocks(ServerPlayer player) {
        for (String tier : List.of("celestigem", "eclipsealloy")) {
            var root = root(tier, false, 0);
            root.set(JustDireDataComponents.TOOL_ENABLED, false);
            var nativeStack = control(root, tier);
            var initial = new AbilityCooldown("decoy", 200, true);
            root.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(initial));
            nativeStack.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(initial));
            var history = root.get(LeggingsContent.mixedLeggingsDataType());
            player.setItemSlot(EquipmentSlot.LEGS, root);
            int total = 200 + ((ToggleableTool) nativeStack.getItem()).getAbilityParams(Ability.DECOY).cooldown;
            for (int tick = 0; tick < total; tick++) {
                sounds.clear();
                ToggleableTool.tickCooldowns(player.level(), nativeStack, player);
                var nativeSounds = List.copyOf(sounds);
                require(nativeSounds.equals(tick == 199 ? List.of(Identifier.parse("minecraft:block.conduit.deactivate"))
                                : tick == total - 1 ? List.of(Identifier.parse("minecraft:entity.ender_eye.death")) : List.of()), "Native completion emissions changed");
                sounds.clear();
                wornTick(player);
                require(java.util.Objects.equals(root.get(JustDireDataComponents.ABILITY_COOLDOWNS), nativeStack.get(JustDireDataComponents.ABILITY_COOLDOWNS))
                        && sounds.equals(nativeSounds), "Production worn clock/sound differs at " + tier + '/' + tick);
            }
            require(history.equals(root.get(LeggingsContent.mixedLeggingsDataType())) && root.get(JustDireDataComponents.FORGE_ENERGY) == 0,
                    "Clock tick paid or changed history");
            root.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(initial));
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
            var before = root.copy();
            root.getItem().inventoryTick(root, player.level(), player, null);
            require(ItemStack.matches(root, before), "Stored inventory tick advanced Decoy");
        }
        var root = root("eclipsealloy", false, 0);
        var foreign = new AbilityCooldown("walkspeed", 17, false);
        root.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(foreign, new AbilityCooldown("decoy", 1, false)));
        player.setItemSlot(EquipmentSlot.LEGS, root);
        wornTick(player);
        require(root.get(JustDireDataComponents.ABILITY_COOLDOWNS).equals(List.of(foreign)), "Clock erased/ticked unrelated entry");
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
    }

    private static void refusals(ServerPlayer player) {
        var root = root("eclipsealloy", false, 10000);
        player.getInventory().setItem(0, root);
        var before = root.copy();
        partialDebit = true;
        try {
            require(!JustDireDecoy.activate(player, 0, root) && ItemStack.matches(root, before) && entities(player).isEmpty(), "Partial transfer did not roll back");
        } finally { partialDebit = false; }
        cancelSpawn = true;
        try {
            require(!JustDireDecoy.activate(player, 0, root) && ItemStack.matches(root, before) && entities(player).isEmpty(), "Cancelled spawn spent state");
        } finally { cancelSpawn = false; }
        require(!JustDireDecoy.activate(player, 0, root.copy()) && !JustDireDecoy.activate(player, -1, root)
                && !JustDireDecoy.activate(player, 1000, root), "Stale/out-of-range slot activated");
        replacePlayer = player;
        try {
            require(!JustDireDecoy.activate(player, 0, root) && ItemStack.matches(root, before)
                    && ItemStack.matches(player.getInventory().getItem(0), before) && entities(player).isEmpty(), "Spawn replacement escaped rollback");
        } finally { replacePlayer = null; }
        player.getInventory().setItem(0, root);
        root.set(JustDireDataComponents.TOOL_ENABLED, false);
        require(!JustDireDecoy.activate(player, 0, root), "Disabled root activated");
        root.remove(JustDireDataComponents.TOOL_ENABLED);
        root.set(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.DECOY), false);
        require(!JustDireDecoy.activate(player, 0, root), "Ability-off root activated");
        root.remove(JustDireDataComponents.ABILITY_TOGGLES.get(Ability.DECOY));
        var config = Config.AVAILABLE_ABILITY_MAP.get(Ability.DECOY);
        boolean previous = config.get();
        try {
            config.set(false);
            require(!JustDireDecoy.activate(player, 0, root), "Native config-off root activated");
            var record = new AbilityCooldown("decoy", 1, true);
            root.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(record));
            player.setItemSlot(EquipmentSlot.LEGS, root);
            wornTick(player);
            require(root.get(JustDireDataComponents.ABILITY_COOLDOWNS).equals(List.of(new AbilityCooldown("decoy", 1200, false))),
                    "Config-off froze an existing clock");
        } finally {
            config.set(previous);
            root.remove(JustDireDataComponents.ABILITY_COOLDOWNS);
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        }
        root.remove(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.DECOY));
        require(!JustDireDecoy.activate(player, 0, root), "Historical source installation resurrected");
        root.setCount(2);
        require(!JustDireDecoy.activate(player, 0, root), "Stacked root activated");
        var bare = new ItemStack(root.getItem());
        player.getInventory().setItem(0, bare);
        require(!JustDireDecoy.activate(player, 0, bare), "Bare root activated");
    }

    private static void inheritance(ServerPlayer player) {
        var first = nativeItem("celestigem");
        first.set(JustDireDataComponents.ABILITY_UPGRADE_INSTALLS.get(Ability.DECOY), true);
        var active = new AbilityCooldown("decoy", 200, true);
        first.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(active));
        var second = nativeItem("eclipsealloy");
        second.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(new AbilityCooldown("decoy", 1200, false)));
        var firstBefore = first.copy();
        var secondBefore = second.copy();
        var template = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:template_eclipsealloy")));
        var ingot = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse("justdirethings:eclipsealloy_ingot")));
        var menu = SmithingChecks.ready(player, template.copy(), first.copy(), ingot.copy());
        var upgraded = menu.getSlot(3).safeTake(1, 1, player);
        require(upgraded.is(second.getItem()) && !menu.getSlot(0).hasItem() && !menu.getSlot(1).hasItem() && !menu.getSlot(2).hasItem(), "Native clock upgrade was not paid");
        for (var other : List.of(new ItemStack(Items.DIAMOND_LEGGINGS), first.copy(), second.copy())) {
            var pair = MixedLeggingsFactory.createPair(first, other);
            for (var root : List.of(pair.primary(), pair.inverse())) {
                require(root.get(JustDireDataComponents.ABILITY_COOLDOWNS).equals(List.of(active)), "Inverse/duplicate/unequal inherited clock differs");
                var history = root.get(LeggingsContent.mixedLeggingsDataType());
                var refreshed = MixedLeggingsRecomposer.recompose(root, history.left().source(), history.right().source());
                require(refreshed.get(JustDireDataComponents.ABILITY_COOLDOWNS).equals(List.of(active)), "Recomposition lost live clock");
                refreshed.remove(JustDireDataComponents.ABILITY_COOLDOWNS);
                refreshed = MixedLeggingsRecomposer.recompose(refreshed, history.left().source(), history.right().source());
                require(!refreshed.has(JustDireDataComponents.ABILITY_COOLDOWNS), "Recomposition resurrected historical clock");
                if (!other.is(Items.DIAMOND_LEGGINGS)) {
                    player.setItemSlot(EquipmentSlot.LEGS, root);
                    if (other.is(second.getItem())) {
                        var nativeStack = upgraded.copy();
                        for (int tick = 0; tick < 200; tick++) {
                            ToggleableTool.tickCooldowns(player.level(), nativeStack, player);
                            wornTick(player);
                        }
                        require(root.get(JustDireDataComponents.ABILITY_COOLDOWNS).equals(nativeStack.get(JustDireDataComponents.ABILITY_COOLDOWNS)), "Effective-tier import disagrees with paid native upgrade");
                    }
                    root.remove(JustDireDataComponents.ABILITY_COOLDOWNS);
                    root.set(JustDireDataComponents.FORGE_ENERGY, 10000);
                    require(JustDireDecoy.activate(player, EquipmentSlot.LEGS.getIndex(net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE), root)
                            && entities(player).size() == 1 && root.get(JustDireDataComponents.FORGE_ENERGY) == 5000, "Unequal/duplicate source dispatched twice or lost eligibility");
                    clear(player);
                }
            }
        }
        require(ItemStack.matches(first, firstBefore) && ItemStack.matches(second, secondBefore), "Import changed sources");
        for (boolean inverse : List.of(false, true)) {
            var root = root("celestigem", inverse, 9000);
            var liveClock = List.of(new AbilityCooldown("decoy", 73, false));
            root.set(JustDireDataComponents.ABILITY_COOLDOWNS, liveClock);
            root.set(JustDireDataComponents.TOOL_ENABLED, false);
            var before = root.copy();
            var mixedMenu = SmithingChecks.ready(player, template.copy(), root, ingot.copy());
            require(ItemStack.matches(root, before), "Mixed tier preview mutated the input");
            var result = mixedMenu.getSlot(3).safeTake(1, 1, player);
            require(!result.isEmpty() && !mixedMenu.getSlot(0).hasItem() && !mixedMenu.getSlot(1).hasItem() && !mixedMenu.getSlot(2).hasItem()
                            && result.get(JustDireDataComponents.ABILITY_COOLDOWNS).equals(liveClock)
                            && result.get(JustDireDataComponents.FORGE_ENERGY) == 9000 && !result.get(JustDireDataComponents.TOOL_ENABLED),
                    "Paid mixed tier change reset live clock/FE/control or did not consume inputs");
        }
        // Equal effective wait: active wins in either order, not whichever source owns the output.
        second.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(new AbilityCooldown("decoy", 1400, false)));
        for (var pair : List.of(MixedLeggingsFactory.createPair(first, second), MixedLeggingsFactory.createPair(second, first))) {
            require(pair.primary().get(JustDireDataComponents.ABILITY_COOLDOWNS).equals(List.of(active))
                    && pair.inverse().get(JustDireDataComponents.ABILITY_COOLDOWNS).equals(List.of(active)), "Equal-wait selection depends on owner/order");
        }
        var foreign = new AbilityCooldown("phase", 11, false);
        first.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(foreign, active));
        var forged = new ItemStack(Items.DIAMOND_LEGGINGS);
        forged.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(new AbilityCooldown("decoy", Integer.MAX_VALUE, true)));
        var filtered = MixedLeggingsFactory.createPair(first, forged);
        require(filtered.primary().get(JustDireDataComponents.ABILITY_COOLDOWNS).equals(List.of(foreign, active))
                        && filtered.inverse().get(JustDireDataComponents.ABILITY_COOLDOWNS).equals(List.of(active)),
                "Creation lost owner foreign records or imported a non-provider clock");
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
    }

    private static void invalidClocks(ServerPlayer player) {
        var invalid = List.of(List.of(new AbilityCooldown("decoy", 0, false)), List.of(new AbilityCooldown("decoy", -1, true)),
                List.of(new AbilityCooldown("decoy", 4, true), new AbilityCooldown("decoy", 8, false)));
        for (var records : invalid) {
            var root = root("eclipsealloy", false, 10000);
            root.set(JustDireDataComponents.ABILITY_COOLDOWNS, records);
            player.setItemSlot(EquipmentSlot.LEGS, root);
            var before = root.copy();
            wornTick(player);
            require(ItemStack.matches(root, before) && !JustDireDecoy.activate(player,
                    EquipmentSlot.LEGS.getIndex(net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE), root), "Invalid clock ticked/activated");
            var source = nativeItem("eclipsealloy");
            source.set(JustDireDataComponents.ABILITY_COOLDOWNS, records);
            var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.IRON_LEGGINGS));
            for (var inherited : List.of(pair.primary(), pair.inverse())) {
                require(inherited.get(JustDireDataComponents.ABILITY_COOLDOWNS).equals(records), "Invalid source clock normalized away");
            }
        }
        var source = nativeItem("celestigem");
        source.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(new AbilityCooldown("decoy", Integer.MAX_VALUE, true)));
        var other = nativeItem("eclipsealloy");
        other.set(JustDireDataComponents.ABILITY_COOLDOWNS, List.of(new AbilityCooldown("decoy", Integer.MAX_VALUE, false)));
        var pair = MixedLeggingsFactory.createPair(source, other);
        for (var root : List.of(pair.primary(), pair.inverse())) {
            require(root.get(JustDireDataComponents.ABILITY_COOLDOWNS).equals(source.get(JustDireDataComponents.ABILITY_COOLDOWNS)), "Wait comparison overflowed");
            player.setItemSlot(EquipmentSlot.LEGS, root);
            wornTick(player);
            require(root.get(JustDireDataComponents.ABILITY_COOLDOWNS).equals(List.of(new AbilityCooldown("decoy", Integer.MAX_VALUE - 1, true))), "Large clock reset/overflowed");
        }
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
    }
}
