package games.alejandrocoria.mismatchedleggings.test.silentgear;

import games.alejandrocoria.mismatchedleggings.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedleggings.compatibility.silentgear.SilentGearState;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import games.alejandrocoria.mismatchedthings.api.composition.AttributePolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.fml.ModList;
import net.silentchaos512.gear.setup.SgDataComponents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Disk and player data are reopened in different JVMs. Expectations never replace the saved-world decoder. */
public final class StateChecks {
    private static final BlockPos STORAGE = new BlockPos(0, 65, 0);
    private static int ticks;
    private static int phase;
    private static boolean initialized;
    private static boolean finished;

    private StateChecks() {}

    public static String stage() { return System.getProperty("leggings.test.sgState", ""); }
    public static boolean absent() { return stage().startsWith("absent"); }
    public static Path report(String name) { return Path.of(System.getProperty("leggings.test.report"), name); }
    private static Path baseline(String name) { return Path.of(System.getProperty("leggings.test.sgStateBaseline"), name); }

    public static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer()) { return; }
        try {
            require(++ticks < 4800, "SG state server timeout at " + phase);
            if (ticks < 20) { return; }
            if (!initialized) {
                require(ModList.get().isLoaded("silentgear") != absent(), "Wrong native state graph");
                require(ModList.get().isLoaded("mismatchedboots") == Boolean.getBoolean("leggings.test.boots"), "Wrong sibling graph");
                server.overworld().getChunkAt(STORAGE);
                if (stage().equals("write")) {
                    seed(server, FoundationChecks.player(server));
                } else { reopen(server); }
                initialized = true;
                if (stage().equals("reopen") || stage().equals("absentagain")) { finish(server); return; }
                Files.writeString(report("ready.txt"), "SG dedicated server ready\n");
            }
            var players = server.getPlayerList().getPlayers();
            if (phase == 3) {
                if (Files.exists(report("client.ok")) && players.isEmpty()) { finish(server); }
                return;
            }
            if (players.isEmpty()) { return; }
            var player = players.getFirst();
            if (phase == 0) {
                if (stage().equals("reconnect")) {
                    require(snapshot(player).equals(read(baseline("player.nbt"))), "Restart/reconnect changed saved player items");
                    if (WearerLifecycle.enabled()) {
                        require(WearerLifecycle.snapshot(player).equals(read(baseline("wearer-player.nbt"))), "Reconnect changed wearer equipment/paid roots");
                        if (!WearerLifecycle.rejoined(player)) { return; }
                    }
                }
                player.getInventory().clearContent();
                for (var slot : EquipmentSlot.values()) { player.setItemSlot(slot, ItemStack.EMPTY); }
                player.setNoGravity(true);
                player.setInvulnerable(true);
                player.connection.teleport(.5, 68, .5, 0, 0);
                player.setItemSlot(EquipmentSlot.MAINHAND, barrel(server).getItem(0).copy());
                player.setItemSlot(EquipmentSlot.OFFHAND, barrel(server).getItem(1).copy());
                player.setItemSlot(EquipmentSlot.LEGS, barrel(server).getItem(2).copy());
                player.getInventory().setItem(1, barrel(server).getItem(3).copy());
                publish(player, "initial");
                phase = 1;
            } else if (phase == 1 && Files.exists(report("client-initial.ok"))) {
                var main = player.getMainHandItem();
                player.setItemSlot(EquipmentSlot.MAINHAND, player.getOffhandItem());
                player.setItemSlot(EquipmentSlot.OFFHAND, main);
                var legs = player.getItemBySlot(EquipmentSlot.LEGS);
                player.setItemSlot(EquipmentSlot.LEGS, player.getInventory().getItem(1));
                player.getInventory().setItem(1, legs);
                publish(player, "moved");
                phase = 2;
            } else if (phase == 2 && Files.exists(report("client-moved.ok"))) {
                if (WearerLifecycle.enabled() && !absent()) {
                    WearerLifecycle.start(player, barrel(server));
                    phase = 4;
                    return;
                }
                NbtIo.writeCompressed(snapshot(player), baseline("player.nbt"));
                Files.writeString(report("finish.txt"), "TCP equipment/inventory accepted\n");
                phase = 3;
            } else if (phase == 4 && WearerLifecycle.tick(player, barrel(server))) {
                NbtIo.writeCompressed(snapshot(player), baseline("player.nbt"));
                Files.writeString(report("finish.txt"), "TCP state and native wearer lifecycle accepted\n");
                phase = 3;
            }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("server", failure);
            server.halt(false);
        }
    }

    private static void seed(MinecraftServer server, ServerPlayer player) throws Exception {
        player.giveExperienceLevels(100);
        var older = FoundationChecks.armor("copper");
        older.set(SgDataComponents.OXIDATION_COUNTER.get(), 1199); // Accelerated fixture, never production time.
        var younger = older.copy();
        younger.set(SgDataComponents.OXIDATION_COUNTER.get(), 10);
        var roots = new ArrayList<>(FoundationChecks.paid(player, older, younger));
        for (var root : roots) {
            player.tickCount = 20;
            root.inventoryTick(player.level(), player, null);
            var state = root.get(SilentGearCompatibility.state());
            require(!state.left().equals(state.right()) && (state.left().orElseThrow().phase().equals("oxidized")
                    || state.right().orElseThrow().phase().equals("oxidized")), "Unequal transitioned clock fixture missing");
            root.setDamageValue(17);
            player.tickCount = 21;
            root.inventoryTick(player.level(), player, EquipmentSlot.LEGS);
        }
        var source = FoundationChecks.armor("bone");
        var rules = new CompositionRules(new AttributeRuleSet(AttributePolicy.SEPARATE,
                Map.of(SilentGearState.ARMOR, AttributePolicy.MAXIMUM)), CompositionRules.defaults().components());
        var pair = MixedLeggingsFactory.createPair(source, new ItemStack(Items.DIAMOND_LEGGINGS), rules);
        for (var root : List.of(pair.primary(), pair.inverse())) {
            root.setDamageValue(root.getMaxDamage() / 2);
            root.inventoryTick(player.level(), player, EquipmentSlot.LEGS);
            roots.add(root);
        }
        // Explicit external managed removal must remain sticky across disk, packets, repair and trim.
        var overridden = roots.get(3);
        overridden.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(overridden.getAttributeModifiers().modifiers()
                .stream().filter(entry -> !SilentGearState.managed(entry)).toList()));
        overridden.inventoryTick(player.level(), player, EquipmentSlot.LEGS);
        require(overridden.get(SilentGearCompatibility.state()).armorOverridden(), "Managed removal was not recorded");
        for (int i = 0; i < 4; i++) {
            var root = roots.get(i);
            root.set(LeggingsContent.forbiddenModifiersType(), new games.alejandrocoria.mismatchedthings.api.compatibility.forbiddenarcanus.ForbiddenModifiers(false, true));
            if (ModList.get().isLoaded("undergarden")) { addInfusion(root); }
            roots.set(i, workstations(player, root, i));
        }
        server.overworld().setBlock(STORAGE, Blocks.BARREL.defaultBlockState(), 3);
        var saved = new CompoundTag();
        saved.putLong("pid", ProcessHandle.current().pid());
        for (int i = 0; i < roots.size(); i++) {
            var root = roots.get(i);
            root.set(DataComponents.CUSTOM_NAME, Component.literal("SG paid state " + i));
            barrel(server).setItem(i, root);
            saved.put("root" + i, encode(root, server.registryAccess()));
        }
        // Normal bare/vanilla controls exercise the same real decoder with SG missing.
        barrel(server).setItem(4, new ItemStack(Items.IRON_LEGGINGS));
        barrel(server).setItem(5, new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID)));
        saved.put("root4", encode(barrel(server).getItem(4), server.registryAccess()));
        saved.put("root5", encode(barrel(server).getItem(5), server.registryAccess()));
        barrel(server).setChanged();
        if (WearerLifecycle.enabled()) { WearerLifecycle.seed(player, barrel(server), saved); }
        NbtIo.writeCompressed(saved, baseline("roots.nbt"));
        ManualFixtures.write(player);
    }

    static ItemStack workstations(ServerPlayer player, ItemStack root, int index) {
        var previous = games.alejandrocoria.mismatchedleggings.config.CompositionRuleState.server();
        var rules = new CompositionRules(new AttributeRuleSet(AttributePolicy.SEPARATE,
                Map.of(SilentGearState.ARMOR, root.get(SilentGearCompatibility.state()).armorPolicy())), CompositionRules.defaults().components());
        try {
            games.alejandrocoria.mismatchedleggings.config.CompositionRuleState.installServer(rules);
            return paidWorkstations(player, root, index);
        } finally { games.alejandrocoria.mismatchedleggings.config.CompositionRuleState.installServer(previous); }
    }

    private static ItemStack paidWorkstations(ServerPlayer player, ItemStack root, int index) {
        var state = root.get(SilentGearCompatibility.state());
        var history = MixedLeggingsStructure.validData(root).orElseThrow();
        var donor = history.left().source();
        donor.setDamageValue(0);
        donor.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 1);
        var anvil = new AnvilMenu(71, player.getInventory(), ContainerLevelAccess.create(player.level(), player.blockPosition()));
        anvil.getSlot(0).set(root.copy());
        anvil.getSlot(1).set(donor);
        anvil.setItemName(root.getHoverName().getString());
        int cost = anvil.getCost(), levels = player.experienceLevel;
        var repaired = anvil.getSlot(2).safeTake(1, 1, player);
        require(!repaired.isEmpty() && repaired.getDamageValue() < root.getDamageValue() && cost > 0
                && player.experienceLevel == levels - cost && !anvil.getSlot(0).hasItem() && !anvil.getSlot(1).hasItem(), "Paid donor repair/enchantment failed " + index
                + "; cost=" + cost + "; result=" + repaired + "; levels=" + levels + "/" + player.experienceLevel);
        require(history.equals(MixedLeggingsStructure.validData(repaired).orElseThrow())
                && repaired.get(DataComponents.ENCHANTMENTS).size() > root.get(DataComponents.ENCHANTMENTS).size(), "Anvil changed histories or lost enchantment");
        // Retain actual worn damage for disk/next-tick projection checks after the paid repair.
        repaired.setDamageValue(Math.min(7 + index, repaired.getMaxDamage() - 1));
        repaired.inventoryTick(player.level(), player, EquipmentSlot.LEGS);
        var smith = new SmithingMenu(72, player.getInventory(), ContainerLevelAccess.NULL);
        smith.getSlot(0).set(new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE, 2));
        smith.getSlot(1).set(repaired.copy());
        smith.getSlot(2).set(new ItemStack(Items.DIAMOND, 2));
        smith.createResult();
        var trimmed = smith.getSlot(3).safeTake(1, 1, player);
        require(!trimmed.isEmpty() && smith.getSlot(0).getItem().getCount() == 1 && smith.getSlot(2).getItem().getCount() == 1
                && !smith.getSlot(1).hasItem(), "Paid trim failed " + index);
        var after = trimmed.get(SilentGearCompatibility.state());
        require(after.left().equals(state.left()) && after.right().equals(state.right()) && after.armorOverridden() == state.armorOverridden()
                && after.armorPolicy() == state.armorPolicy(), "Paid menus reset lanes/overrides/policy");
        require(java.util.Objects.equals(root.get(LeggingsContent.forbiddenModifiersType()), trimmed.get(LeggingsContent.forbiddenModifiersType())), "Paid menus changed external Soulbound state");
        if (ModList.get().isLoaded("undergarden")) {
            var type = net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(net.minecraft.resources.Identifier.parse("undergarden:rogdorium_infusion"));
            require(java.util.Objects.equals(root.get(type), trimmed.get(type)), "Paid menus changed external live infusion");
        }
        require(MixedLeggingsStructure.validData(trimmed).orElseThrow().left().source().has(DataComponents.TRIM)
                && MixedLeggingsStructure.validData(trimmed).orElseThrow().right().source().has(DataComponents.TRIM), "Native source trim missing");
        return trimmed;
    }

    @SuppressWarnings("unchecked")
    private static void addInfusion(ItemStack root) {
        var type = (net.minecraft.core.component.DataComponentType<Object>) net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE
                .getValue(net.minecraft.resources.Identifier.parse("undergarden:rogdorium_infusion"));
        root.set(type, type.codec().parse(com.mojang.serialization.JsonOps.INSTANCE,
                com.google.gson.JsonParser.parseString("{\"infusionAmount\":53,\"infusionMax\":3600}")).getOrThrow());
    }

    private static void reopen(MinecraftServer server) throws Exception {
        var expected = read(baseline("roots.nbt"));
        require(expected.getLongOr("pid", -1) != ProcessHandle.current().pid(), "Disk reopen reused save JVM");
        var parked = new CompoundTag();
        for (int i = 0; i < 6; i++) {
            var root = barrel(server).getItem(i);
            if (!absent() || i >= 4) {
                require(encode(root, server.registryAccess()).equals(expected.get("root" + i)), "Saved root/history/state/projection changed " + i);
                if (i < 4) {
                    root = root.copy();
                    var before = root.copy();
                    var actor = FoundationChecks.player(server);
                    actor.tickCount = 20;
                    root.inventoryTick(actor.level(), actor, EquipmentSlot.LEGS);
                    require(ItemStack.matches(root, before), "Reopen changed state " + i + "; before=" + before.get(SilentGearCompatibility.state())
                            + "; after=" + root.get(SilentGearCompatibility.state()) + "; attributesChanged="
                            + !before.getAttributeModifiers().equals(root.getAttributeModifiers()) + "; input=" + root.getAttributeModifiers());
                    if (i < 2) {
                        root.inventoryTick(actor.level(), actor, null);
                        var old = before.get(SilentGearCompatibility.state());
                        var next = root.get(SilentGearCompatibility.state());
                        require(next.left().equals(old.left().map(StateChecks::increment)) && next.right().equals(old.right().map(StateChecks::increment))
                                && MixedLeggingsStructure.validData(root).equals(MixedLeggingsStructure.validData(before)),
                                "Reopened idle clocks reseeded, caught up or replaced histories");
                    }
                }
            } else {
                inactive(root);
                parked.put("root" + i, encode(root, server.registryAccess()));
            }
        }
        if (WearerLifecycle.enabled() && !absent()) { WearerLifecycle.reopen(FoundationChecks.player(server), barrel(server), expected); }
        if (stage().equals("absent")) { NbtIo.writeCompressed(parked, baseline("parked.nbt")); }
        if (stage().equals("absentagain")) { require(parked.equals(read(baseline("parked.nbt"))), "Second absent save changed raw archive"); }
    }

    public static void inactive(ItemStack root) {
        require(LeggingsContent.suspendedLeggingsData(root).isPresent() && !root.has(SilentGearCompatibility.state())
                && MixedLeggingsStructure.validData(root).isEmpty() && root.getAttributeModifiers().modifiers().isEmpty(), "Absent SG root retained active state");
    }

    private static SilentGearState.Oxidation increment(SilentGearState.Oxidation lane) {
        return new SilentGearState.Oxidation(lane.phase(), lane.level(), lane.stage(), lane.counter() + 1);
    }

    private static BarrelBlockEntity barrel(MinecraftServer server) {
        var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(STORAGE);
        require(barrel != null, "SG saved-world barrel missing");
        return barrel;
    }

    public static CompoundTag encode(ItemStack root, HolderLookup.Provider registries) {
        return (CompoundTag) ItemStack.OPTIONAL_CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), root).getOrThrow();
    }

    public static CompoundTag read(Path file) throws Exception { return NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()); }

    public static CompoundTag snapshot(net.minecraft.world.entity.player.Player player) {
        var result = new CompoundTag();
        for (var slot : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.LEGS)) {
            result.put(slot.getSerializedName(), encode(player.getItemBySlot(slot), player.registryAccess()));
        }
        result.put("inventory", encode(player.getInventory().getItem(1), player.registryAccess()));
        return result;
    }

    private static void publish(ServerPlayer player, String name) throws Exception {
        player.inventoryMenu.broadcastChanges();
        NbtIo.writeCompressed(snapshot(player), report(name + ".nbt"));
        Files.writeString(report(name + ".ready"), "full ordinary packet expectation\n");
    }

    private static void finish(MinecraftServer server) throws Exception {
        server.saveEverything(true, true, true);
        Files.writeString(report("state.txt"), stage() + ": independent JVM; full native roots/histories, unequal transitioned clocks, worn projections, captured maximum policy, sticky removal, paid donor/enchant/trim; actual native absence=" + absent() + "\n");
        finished = true;
        SkeletonChecks.passed("server");
        server.halt(false);
    }
}
