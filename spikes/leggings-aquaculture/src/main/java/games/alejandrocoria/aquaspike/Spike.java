package games.alejandrocoria.aquaspike;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Dedicated feasibility fixture; real inventory/fluid priming and native movement methods. */
@Mod("leggings_aqua_spike")
public final class Spike {
    private static final String MODE = System.getProperty("aqua.spike.mode");
    private static final boolean CANDIDATE = MODE.startsWith("candidate");
    private static final StringBuilder REPORT = new StringBuilder();
    private static boolean finished;
    private static int motionComparisons;

    public Spike() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void tick(MinecraftServer server) {
        if (!server.isDedicatedServer() || finished || server.getTickCount() < 20) {
            return;
        }
        finished = true;
        try {
            var level = server.overworld();
            level.setChunkForced(0, 0, true);
            for (int x = 0; x < 4; x++) {
                for (int y = 79; y < 84; y++) {
                    for (int z = 0; z < 4; z++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState(), 3);
                    }
                }
            }
            if (MODE.equals("absent")) {
                var ordinary = pair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS));
                checkGravity(server, ordinary.getFirst(), false);
                REPORT.append("Optional absence: ordinary mixed leggings retain gravity; no native class dependency\n");
            } else {
                verify(server);
            }
            REPORT.append("mode=").append(MODE).append("; native fluid-movement comparisons=").append(motionComparisons).append('\n');
            Files.writeString(report("report.txt"), REPORT.toString());
            Files.writeString(report("passed.txt"), "passed\n");
        } catch (Throwable failure) {
            var trace = new StringWriter();
            failure.printStackTrace(new PrintWriter(trace));
            try {
                Files.writeString(report("failure.txt"), trace.toString());
            } catch (Exception writing) {
                failure.addSuppressed(writing);
            }
            failure.printStackTrace();
        } finally {
            server.halt(false);
        }
    }

    private static void verify(MinecraftServer server) {
        var source = nativeItem("neptunium_leggings");
        var roots = pair(source, new ItemStack(Items.IRON_LEGGINGS));
        for (var root : roots) {
            checkGravity(server, root, CANDIDATE);
            for (String condition : List.of("submerged", "shallow", "dry", "rain", "crouch", "jump", "swim", "spectator")) {
                movement(server, source, root, condition);
            }
            if (CANDIDATE) {
                coexistence(server, source, root);
                lifecycle(server, root);
            }
        }
        for (var root : pair(source, source)) {
            checkGravity(server, root, CANDIDATE);
        }
        if (CANDIDATE) {
            invalid(server, roots.getFirst());
        }
        nativeCleanup(server, source);
        REPORT.append("Both orientations and duplicate sources; strict/inventory boundaries; native controls\n");
    }

    @SuppressWarnings("deprecation")
    private static ServerPlayer actor(MinecraftServer server, ItemStack legs, String condition) {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "aqua-spike"),
                ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        player.setGameMode(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        double x = condition.equals("dry") || condition.equals("rain") ? 8.5 : 1.5;
        double y = condition.equals("shallow") ? 83.5 : 80;
        player.setPos(x, y, 1.5);
        player.setDeltaMovement(Vec3.ZERO);
        // Two real player ticks initialize current fluid and eye-fluid caches. They are disconnected
        // actors, so the fixture holds the chunk ticket and explicitly exercises travel below.
        player.doTick();
        player.doTick();
        player.setPos(x, y, 1.5);
        player.setDeltaMovement(Vec3.ZERO);
        player.setNoGravity(false);
        player.setPose(condition.equals("crouch") ? Pose.CROUCHING : Pose.STANDING);
        player.setJumping(condition.equals("jump"));
        player.setSwimming(condition.equals("swim"));
        if (condition.equals("spectator")) {
            player.setGameMode(GameType.SPECTATOR);
        }
        boolean submerged = !List.of("dry", "rain", "shallow").contains(condition);
        require(player.isEyeInFluid(FluidTags.WATER) == submerged, "Fixture eye-water mismatch: " + condition);
        return player;
    }

    private static void movement(MinecraftServer server, ItemStack source, ItemStack root, String condition) {
        if (condition.equals("rain")) {
            server.setWeatherParameters(0, 600, true, false);
            server.overworld().setRainLevel(1);
        }
        var nativePlayer = actor(server, source.copy(), condition);
        var mixedPlayer = actor(server, root.copy(), condition);
        require(!condition.equals("rain") || server.overworld().isRainingAt(mixedPlayer.blockPosition()),
                "Rain-only fixture control was not exposed to rain");
        var before = mixedPlayer.getItemBySlot(EquipmentSlot.LEGS).copy();
        boolean buoyant = condition.equals("submerged");
        for (int step = 0; step < 8; step++) {
            nativeTick(nativePlayer, source);
            double expected = nativePlayer.getGravity();
            double actual = mixedPlayer.getGravity();
            require(nativePlayer.isNoGravity() == buoyant, "Native condition control changed: " + condition);
            require(!mixedPlayer.isNoGravity(), "Candidate wrote persistent NoGravity");
            if (CANDIDATE || !buoyant) {
                require(Math.abs(expected - actual) < 1.0E-10, "Gravity differed from native: " + condition);
                nativePlayer.travel(Vec3.ZERO);
                mixedPlayer.travel(Vec3.ZERO);
                require(Math.abs(nativePlayer.getY() - mixedPlayer.getY()) < 1.0E-8
                                && Math.abs(nativePlayer.getDeltaMovement().y - mixedPlayer.getDeltaMovement().y) < 1.0E-8,
                        "Actual native fluid/air movement differed: " + condition);
                motionComparisons++;
            } else {
                require(actual > 0 && expected == 0, "Unadapted/refused mixed baseline unexpectedly floated");
            }
        }
        if (condition.equals("dry")) {
            require(mixedPlayer.getDeltaMovement().y < 0, "Movement comparison never exercised falling physics");
        }
        require(ItemStack.matches(before, mixedPlayer.getItemBySlot(EquipmentSlot.LEGS)), "Query/movement rewrote root data");
        server.setWeatherParameters(600, 0, false, false);
        server.overworld().setRainLevel(0);
    }

    private static void checkGravity(MinecraftServer server, ItemStack root, boolean expected) {
        var player = actor(server, root, "submerged");
        require((player.getGravity() == 0) == expected && !player.isNoGravity(), "Unexpected mixed gravity entitlement");
    }

    private static void coexistence(MinecraftServer server, ItemStack source, ItemStack root) {
        for (String carried : List.of("neptunium_boots", "neptunium_helmet", "neptunium_chestplate")) {
            var player = actor(server, root.copy(), "submerged");
            var stack = nativeItem(carried);
            player.getInventory().setItem(10, stack);
            player.doTick();
            for (int tick = 0; tick < 4; tick++) {
                nativeTick(player, stack);
                require(player.getGravity() == 0 && !player.isNoGravity(), "Native inventory tick canceled query buoyancy");
            }
            require(!player.hasEffect(MobEffects.NIGHT_VISION) && !player.hasEffect(MobEffects.WATER_BREATHING),
                    "Carried armor or mixed leggings invented helmet/chestplate benefits");
            player.setItemSlot(EquipmentSlot.HEAD, nativeItem("neptunium_helmet"));
            player.setItemSlot(EquipmentSlot.CHEST, nativeItem("neptunium_chestplate"));
            nativeTick(player, stack);
            require(player.hasEffect(MobEffects.NIGHT_VISION) && player.hasEffect(MobEffects.WATER_BREATHING)
                            && player.getGravity() == 0, "Native other-piece positives were lost");
        }
        if (MODE.equals("candidateBoots")) {
            var player = actor(server, root.copy(), "submerged");
            var originalBoots = nativeItem("neptunium_boots");
            player.setItemSlot(EquipmentSlot.FEET, originalBoots);
            player.doTick();
            require(player.getGravity() == 0, "Native equipped Boots canceled mixed buoyancy");
            try {
                var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
                var pair = factory.getMethod("createPair", ItemStack.class, ItemStack.class)
                        .invoke(null, originalBoots, new ItemStack(Items.IRON_BOOTS));
                player.setItemSlot(EquipmentSlot.FEET, (ItemStack) pair.getClass().getMethod("primary").invoke(pair));
                player.doTick();
                require(player.getGravity() == 0, "Mixed armor needed an original armor anchor");
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError("Cannot build current Boots coexistence control", failure);
            }
        }
        REPORT.append("Native armor worn/carried cannot cancel query; native head/chest effects remain independent\n");
    }

    private static void lifecycle(MinecraftServer server, ItemStack root) {
        var player = actor(server, root.copy(), "submerged");
        require(player.getGravity() == 0 && !save(player).getBooleanOr("NoGravity", false), "Query state leaked into saved data");
        var saved = save(player);
        var restored = actor(server, ItemStack.EMPTY, "submerged");
        restored.load(TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(), saved));
        require(!restored.isNoGravity() && restored.getGravity() == 0, "Serialized restore lost pure-query buoyancy");
        restored.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        require(restored.getGravity() > 0 && !save(restored).getBooleanOr("NoGravity", false), "Restore/unequip left stale gravity");
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
        require(player.getGravity() > 0, "Replacement retained buoyancy");
        player.setItemSlot(EquipmentSlot.LEGS, root.copy());
        player.setPos(8.5, 80, 1.5);
        player.doTick();
        player.doTick();
        require(player.getGravity() > 0 && !player.isNoGravity(), "Leaving water retained query buoyancy");
        player.setPos(1.5, 80, 1.5);
        player.doTick();
        player.doTick();
        var breaking = player.getItemBySlot(EquipmentSlot.LEGS);
        breaking.setDamageValue(breaking.getMaxDamage() - 1);
        breaking.hurtAndBreak(1, player, EquipmentSlot.LEGS);
        require(breaking.isEmpty(), "Armor breakage control did not consume the root");
        require(player.getGravity() > 0, "Empty/broken stack retained buoyancy");
        var foreign = actor(server, ItemStack.EMPTY, "submerged");
        foreign.setNoGravity(true);
        foreign.setItemSlot(EquipmentSlot.LEGS, root.copy());
        require(foreign.getGravity() == 0 && foreign.isNoGravity(), "Candidate changed a pre-existing gravity flag");
        foreign.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        require(foreign.isNoGravity() && foreign.getGravity() == 0, "Candidate erased a foreign NoGravity flag");
        REPORT.append("NoGravity not written/serialized; replacement/empty/suspended/foreign flag boundaries; entity-data restore\n");
    }

    private static void invalid(MinecraftServer server, ItemStack root) {
        var suspended = root.copy();
        suspended.set(LeggingsContent.createSuspendedDataType(),
                SuspendedLeggingsData.of(new CompoundTag(), new CompoundTag(), new CompoundTag()));
        var malformed = root.copy();
        malformed.remove(DataComponents.EQUIPPABLE);
        var spoof = new ItemStack(Items.IRON_LEGGINGS);
        spoof.set(LeggingsContent.mixedLeggingsDataType(), root.get(LeggingsContent.mixedLeggingsDataType()));
        for (var invalid : List.of(new ItemStack(root.getItem()), root.copyWithCount(2), suspended, malformed, spoof)) {
            checkGravity(server, invalid, false);
        }
        var player = actor(server, ItemStack.EMPTY, "submerged");
        player.getInventory().setItem(10, root.copy());
        player.setItemSlot(EquipmentSlot.OFFHAND, root.copy());
        player.setItemSlot(EquipmentSlot.CHEST, root.copy());
        require(player.getGravity() > 0, "Inventory/hand root granted worn buoyancy");
        var stand = new ArmorStand(server.overworld(), 1.5, 80, 1.5);
        double gravity = stand.getGravity();
        stand.setItemSlot(EquipmentSlot.LEGS, root.copy());
        require(stand.getGravity() == gravity, "Player-only effect leaked to an armor stand");
    }

    private static void nativeCleanup(MinecraftServer server, ItemStack source) {
        var player = actor(server, source.copy(), "submerged");
        nativeTick(player, source);
        require(player.isNoGravity(), "Native buoyancy control did not activate");
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        player.getInventory().clearContent();
        player.doTick();
        require(player.isNoGravity() && save(player).getBooleanOr("NoGravity", false),
                "Native-only cleanup behavior changed; re-audit the control");
        REPORT.append("Native-only: removing final native armor leaves NoGravity=true after player tick and in entity save data\n");
    }

    private static CompoundTag save(ServerPlayer player) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, player.registryAccess());
        player.saveWithoutId(output);
        return output.buildResult();
    }

    private static void nativeTick(ServerPlayer player, ItemStack stack) {
        stack.getItem().inventoryTick(stack, player.level(), player, EquipmentSlot.LEGS);
    }

    private static ItemStack nativeItem(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("aquaculture", name)).orElseThrow().value());
    }

    private static List<ItemStack> pair(ItemStack first, ItemStack second) {
        var pair = MixedLeggingsFactory.createPair(first, second, CompositionRules.defaults());
        return List.of(pair.primary(), pair.inverse());
    }

    private static Path report(String name) {
        return Path.of(System.getProperty("aqua.spike.report"), name);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
