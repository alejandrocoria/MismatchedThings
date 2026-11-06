package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.ForbiddenModifiers;
import games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsItemCodec;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import games.alejandrocoria.mismatchedthings.api.composition.DurabilityPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Persistent native world/player/entity loads across independent dedicated servers and TCP clients. */
public final class RecoveryChecks {
    private static final BlockPos STORAGE = new BlockPos(0, 65, 0);
    private static final long PAID = 9_007_199_254_740_993L;
    private static boolean initialized;
    private static boolean finished;
    private static int state;
    private static int ticks;
    private static ServerPlayer dead;

    private RecoveryChecks() {
    }

    public static Path report(String name) {
        return Path.of(System.getProperty("leggings.test.report"), name);
    }

    static void tick(MinecraftServer server) {
        if (!server.isDedicatedServer() || finished) {
            return;
        }
        try {
            require(++ticks < 3600, "Recovery server timed out at " + state);
            if (ticks < 20) {
                return;
            }
            if (!initialized) {
                initialize(server);
                initialized = true;
                if (!RecoveryFixture.connected()) {
                    finish(server);
                    return;
                }
                Files.writeString(report("ready.txt"), "dedicated recovery server ready\n");
            }
            var players = server.getPlayerList().getPlayers();
            if (state == 4) {
                if (Files.exists(report("client.ok")) && players.isEmpty()) {
                    finish(server);
                }
                return;
            }
            if (players.isEmpty()) {
                return;
            }
            var player = players.getFirst();
            if (state == 0) {
                connected(server, player);
                snapshot(player, "initial.nbt");
                state = 1;
            } else if (state == 1 && Files.exists(report("client-initial.ok"))) {
                if (!RecoveryFixture.stage().equals("absent")) {
                    state = 4;
                    Files.writeString(report("finish.txt"), "network accepted\n");
                    return;
                }
                // Dedicated logins retain native spawn protection even after setInvulnerable(false).
                if (player.tickCount < 100) {
                    return;
                }
                // Real combat and Mending must not wear/repair parked state before native death.
                RecoveryModelChecks.inactiveWear(server, player);
                edit(player.getItemBySlot(EquipmentSlot.LEGS), 0);
                edit(player.getInventory().getItem(0), 1);
                player.setInvulnerable(false);
                require(player.hurtServer(server.overworld(), player.damageSources().genericKill(), 1000)
                                && player.isDeadOrDying(), "Native recovery death failed");
                var drops = drops(server);
                require(drops.size() == 2, "Death duplicated/lost roots or refunded parked resources: " + drops.size());
                drops.forEach(drop -> {
                    drop.setNeverPickUp();
                    drop.setNoGravity(true);
                    drop.setDeltaMovement(Vec3.ZERO);
                    drop.setPos(.5, 66, .5);
                });
                dead = player;
                state = 2;
            } else if (state == 2 && player != dead && !player.isDeadOrDying()) {
                var drops = drops(server);
                require(drops.size() == 2, "Respawn changed death drops");
                var inverse = drops.stream().filter(drop -> named(drop.getItem(), 1)).findFirst().orElseThrow();
                var primary = drops.stream().filter(drop -> named(drop.getItem(), 0)).findFirst().orElseThrow();
                inverse.setNoPickUpDelay();
                inverse.playerTouch(player);
                require(inverse.isRemoved() && named(player.getInventory().getItem(0), 1), "Native pickup failed");
                player.setItemSlot(EquipmentSlot.LEGS, player.getInventory().getItem(0));
                player.getInventory().setItem(0, ItemStack.EMPTY);
                player.connection.teleport(2.5, 65, 2.5, 0, 0);
                player.setNoGravity(true);
                var manifest = new CompoundTag();
                manifest.putString("drop", primary.getUUID().toString());
                manifest.putString("player", player.getUUID().toString());
                NbtIo.writeCompressed(manifest, baseline("lifecycle.nbt"));
                player.inventoryMenu.broadcastChanges();
                snapshot(player, "final.nbt");
                state = 3;
            } else if (state == 3 && Files.exists(report("client-final.ok"))) {
                state = 4;
                Files.writeString(report("finish.txt"), "death/respawn/pickup accepted\n");
            }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("server", failure);
            server.halt(false);
        }
    }

    private static void initialize(MinecraftServer server) throws Exception {
        require(CompositionRuleState.server().components().durability() == (RecoveryFixture.stage().equals("write")
                        ? DurabilityPolicy.AVERAGE : DurabilityPolicy.SEPARATE),
                "Recovery fixture did not actually change its loaded composition policy");
        server.overworld().getChunkAt(STORAGE);
        if (RecoveryFixture.stage().equals("write")) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    server.overworld().setBlock(new BlockPos(x, 64, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            server.overworld().setBlock(STORAGE, Blocks.BARREL.defaultBlockState(), 3);
            var temporary = new ItemStack(BuiltInRegistries.ITEM.getValue(RecoveryFixture.SOURCE));
            temporary.set(RecoveryFixture.WALLET, PAID + 100);
            var pair = MixedLeggingsFactory.createPair(temporary, new ItemStack(Items.IRON_LEGGINGS), CompositionRules.defaults());
            var roots = List.of(decorate(server, pair.primary(), 0), decorate(server, pair.inverse(), 1),
                    decorate(server, MixedLeggingsFactory.createPair(new ItemStack(Items.IRON_LEGGINGS),
                            new ItemStack(Items.GOLDEN_LEGGINGS), CompositionRules.defaults()).primary(), 2),
                    new ItemStack(Items.IRON_LEGGINGS), new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID)));
            var baseline = new CompoundTag();
            for (int i = 0; i < roots.size(); i++) {
                storage(server).setItem(i, roots.get(i));
                baseline.put("root" + i, encode(roots.get(i), server.registryAccess()));
            }
            if (Boolean.getBoolean("leggings.test.boots")) {
                var boots = BootsCoexistence.mixed();
                boots.set(RecoveryFixture.WALLET, PAID);
                storage(server).setItem(5, boots);
                baseline.put("root5", encode(boots, server.registryAccess()));
            }
            storage(server).setChanged();
            NbtIo.writeCompressed(baseline, baseline("roots.nbt"));
            RecoveryModelChecks.verify(server, roots.getFirst());
        } else {
            verifyStored(server);
        }
        if (Boolean.getBoolean("leggings.test.forbiddenRecovery")) {
            Class.forName("games.alejandrocoria.mismatchedleggings.test.ForbiddenModifierRecoveryChecks")
                    .getMethod("stored", MinecraftServer.class).invoke(null, server);
        }
    }

    private static ItemStack decorate(MinecraftServer server, ItemStack root, int index) {
        // Own registry-independent state must survive the same real absent-source cycle.
        root.set(LeggingsContent.forbiddenModifiersType(), new ForbiddenModifiers(true, true));
        root.set(RecoveryFixture.WALLET, PAID);
        root.set(CompositionFixture.CHARGE, 123L);
        root.setDamageValue(17 + index);
        root.set(DataComponents.REPAIR_COST, 3);
        root.set(DataComponents.CUSTOM_NAME, Component.literal("Live recovery " + index));
        var typed = new CompoundTag();
        typed.putLongArray("opaque", new long[]{Long.MIN_VALUE, Long.MAX_VALUE});
        typed.putByteArray("bytes", new byte[]{0, -1, 127});
        root.set(DataComponents.CUSTOM_DATA, CustomData.of(typed));
        root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
        root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SWIFT_SNEAK), 2);
        return root;
    }

    private static void verifyStored(MinecraftServer server) throws Exception {
        var baseline = read(baseline("roots.nbt"));
        boolean active = RecoveryFixture.stage().equals("restore") || RecoveryFixture.stage().equals("reloaded");
        for (int i = 0; i < 5; i++) {
            var root = storage(server).getItem(i);
            require(!root.isEmpty(), "Native barrel loading lost recovery slot " + i);
            if (i < 3) {
                if (active) {
                    var expected = baseline.getCompoundOrEmpty("root" + i).copy();
                    var decoded = decode(expected, server.registryAccess());
                    edit(decoded, i);
                    require(encode(root, server.registryAccess()).equals(encode(decoded, server.registryAccess()))
                                    && MixedLeggingsStructure.validData(root).isPresent(), "Incomplete recovery in slot " + i);
                    require(root.get(RecoveryFixture.WALLET) == PAID, "Recovery refilled/rounded the paid wallet");
                    require(new ForbiddenModifiers(true, true).equals(root.get(LeggingsContent.forbiddenModifiersType())),
                            "Recovery lost product-owned modifier flags");
                } else {
                    inactive(root);
                    if (RecoveryFixture.stage().equals("absent")) {
                        edit(root, i);
                        storage(server).setItem(i, root);
                    }
                    var raw = encode(root, server.registryAccess());
                    require(ItemStack.matches(root, decode(raw, server.registryAccess())), "Suspension grew/changed on reload");
                    if (!RecoveryFixture.stage().equals("absent")) {
                        var absent = read(baseline("absent.nbt"));
                        require(raw.equals(absent.get("root" + i)), "Repeated/partial save changed protected root " + i);
                    }
                }
            } else {
                require(encode(root, server.registryAccess()).equals(baseline.get("root" + i)), "Native/bare control changed");
                if (i == 4) {
                    require(!MixedLeggingsStructure.canEquip(root), "Bare give root acquired history permission");
                }
            }
        }
        if (baseline.contains("root5")) {
            var boots = storage(server).getItem(5);
            var raw = encode(boots, server.registryAccess());
            require(active ? raw.equals(baseline.get("root5"))
                            : raw.getCompoundOrEmpty("components").contains("mismatchedboots:suspended_boots"),
                    "Older Boots wrapper did not coexist/recover its own root");
            require(!raw.getCompoundOrEmpty("components").contains(SuspendedLeggingsItemCodec.ARCHIVE),
                    "Leggings wrapper captured a sibling root");
        }
        storage(server).setChanged();
        if (RecoveryFixture.stage().equals("absent")) {
            var absent = new CompoundTag();
            for (int i = 0; i < 3; i++) {
                absent.put("root" + i, encode(storage(server).getItem(i), server.registryAccess()));
            }
            NbtIo.writeCompressed(absent, baseline("absent.nbt"));
            RecoveryModelChecks.workstations(server, storage(server).getItem(0));
        }
    }

    private static void connected(MinecraftServer server, ServerPlayer player) throws Exception {
        player.setGameMode(GameType.SURVIVAL);
        if (RecoveryFixture.stage().equals("write")) {
            player.getInventory().clearContent();
            player.setItemSlot(EquipmentSlot.LEGS, storage(server).getItem(0).copy());
            player.getInventory().setItem(0, storage(server).getItem(1).copy());
            player.connection.teleport(2.5, 65, 2.5, 0, 0);
            player.setNoGravity(true);
        } else if (RecoveryFixture.stage().equals("absent")) {
            inactive(player.getItemBySlot(EquipmentSlot.LEGS));
            inactive(player.getInventory().getItem(0));
            require(encode(player.getItemBySlot(EquipmentSlot.LEGS), server.registryAccess())
                            .equals(encode(expected(server, 0, false), server.registryAccess()))
                            && ItemStack.matches(player.getInventory().getItem(0), expected(server, 1, false)),
                    "Saved worn/inventory state did not suspend completely");
        } else {
            var manifest = read(baseline("lifecycle.nbt"));
            require(player.getUUID().toString().equals(manifest.getStringOr("player", "")), "Reconnect changed player identity");
            require(ItemStack.matches(player.getItemBySlot(EquipmentSlot.LEGS), expected(server, 1, true))
                            && player.getInventory().getNonEquipmentItems().stream().allMatch(ItemStack::isEmpty),
                    "Reconnected worn inverse did not recover or inventory duplicated");
            var drop = server.overworld().getEntity(UUID.fromString(manifest.getStringOr("drop", "")));
            require(drop instanceof ItemEntity entity && ItemStack.matches(entity.getItem(), expected(server, 0, true)),
                    "Saved native death-drop did not fully recover");
        }
        player.inventoryMenu.broadcastChanges();
    }

    private static ItemStack expected(MinecraftServer server, int index, boolean edited) throws Exception {
        var root = decode(read(baseline("roots.nbt")).getCompoundOrEmpty("root" + index), server.registryAccess());
        if (edited) {
            edit(root, index);
        }
        return root;
    }

    static void inactive(ItemStack root) {
        var archive = LeggingsContent.suspendedLeggingsData(root).orElseThrow();
        require(!archive.quarantined() && !archive.conflicted() && MixedLeggingsStructure.canEquip(root)
                        && MixedLeggingsStructure.validData(root).isEmpty()
                        && !root.get(DataComponents.EQUIPPABLE).damageOnHurt()
                        && root.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().isEmpty()
                        && !root.isEnchanted() && !root.has(RecoveryFixture.WALLET) && !root.has(CompositionFixture.CHARGE),
                "Suspended history activated gameplay or lost ordinary equipment permission");
    }

    static void edit(ItemStack root, int index) {
        if (index == 1) {
            root.remove(DataComponents.CUSTOM_NAME);
        } else {
            root.set(DataComponents.CUSTOM_NAME, Component.literal("Edited while absent " + index));
        }
    }

    private static boolean named(ItemStack root, int index) {
        return index == 1 ? !root.has(DataComponents.CUSTOM_NAME)
                : Component.literal("Edited while absent " + index).equals(root.get(DataComponents.CUSTOM_NAME));
    }

    private static List<ItemEntity> drops(MinecraftServer server) {
        return server.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(-8, 60, -8, 8, 74, 8));
    }

    private static void snapshot(ServerPlayer player, String name) throws Exception {
        var value = new CompoundTag();
        value.put("legs", encode(player.getItemBySlot(EquipmentSlot.LEGS), player.registryAccess()));
        value.put("inventory", encode(player.getInventory().getItem(0), player.registryAccess()));
        NbtIo.writeCompressed(value, report(name));
        Files.writeString(report(name + ".ready"), "complete snapshot\n");
    }

    static CompoundTag encode(ItemStack root, HolderLookup.Provider registries) {
        return root.isEmpty() ? new CompoundTag() : (CompoundTag) ItemStack.CODEC.encodeStart(
                registries.createSerializationContext(NbtOps.INSTANCE), root).getOrThrow();
    }

    static ItemStack decode(CompoundTag raw, HolderLookup.Provider registries) {
        return ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), raw).getOrThrow();
    }

    static CompoundTag read(Path path) throws Exception {
        return NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
    }

    private static Path baseline(String name) {
        return Path.of(System.getProperty("leggings.test.recoveryBaseline"), name);
    }

    private static BarrelBlockEntity storage(MinecraftServer server) {
        var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(STORAGE);
        require(barrel != null, "Native world load lost the recovery barrel");
        return barrel;
    }

    private static void finish(MinecraftServer server) throws Exception {
        finished = true;
        server.getPlayerList().saveAll();
        server.saveAllChunks(false, true, true);
        SkeletonChecks.passed("server");
        server.halt(false);
    }
}
