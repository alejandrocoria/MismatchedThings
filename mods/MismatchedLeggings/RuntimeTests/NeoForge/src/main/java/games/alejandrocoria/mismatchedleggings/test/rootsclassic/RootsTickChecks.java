package games.alejandrocoria.mismatchedleggings.test.rootsclassic;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Native item/AI inventory dispatch, actual root damage and native player attachment changes. */
public final class RootsTickChecks {
    private static final String VARIANT = System.getProperty("leggings.test.rootsTicks", "normal");
    private static boolean finished;
    private static int cases;
    private static Object manaSupplier;

    private RootsTickChecks() {
    }

    public static void tick(MinecraftServer server) {
        if (finished || !server.isDedicatedServer() || server.getTickCount() < 20) {
            return;
        }
        finished = true;
        try {
            boolean absent = VARIANT.equals("absent");
            require(ModList.get().isLoaded("rootsclassic") != absent, "Wrong Roots environment");
            ServerPlayer player = FakePlayerFactory.getMinecraft(server.overworld());
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(0, 80, 0);
            player.setNoGravity(true);
            var plain = MixedLeggingsFactory.createPair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS));
            if (absent) {
                check(player, plain.primary(), false, false, 10, 0, 0, EquipmentSlot.LEGS, null);
                check(player, plain.inverse(), false, false, 10, 0, 0, EquipmentSlot.OFFHAND, null);
            } else {
                manaSupplier = Class.forName("elucent.rootsclassic.attachment.RootsAttachments").getField("MANA").get(null);
                ItemStack sylvan = nativeItem("sylvan_tunic");
                ItemStack wildwood = nativeItem("wildwood_leggings");
                proofs(sylvan, wildwood);
                // Native controls remain functional even when our readiness proof is intentionally unavailable.
                check(player, sylvan, true, true, 10, 0, 0, EquipmentSlot.LEGS, null);
                check(player, wildwood, true, false, 10, 0, 0, EquipmentSlot.LEGS, null);
                List<ItemStack> pairs = new ArrayList<>();
                var s = MixedLeggingsFactory.createPair(sylvan, new ItemStack(Items.DIAMOND_LEGGINGS));
                var w = MixedLeggingsFactory.createPair(wildwood, new ItemStack(Items.DIAMOND_LEGGINGS));
                var sw = MixedLeggingsFactory.createPair(sylvan, wildwood);
                pairs.addAll(List.of(s.primary(), s.inverse(), w.primary(), w.inverse(), sw.primary(), sw.inverse(),
                                     MixedLeggingsFactory.createPair(sylvan, sylvan).primary(),
                                     MixedLeggingsFactory.createPair(wildwood, wildwood).primary()));
                for (ItemStack pair : pairs) {
                    boolean hasSylvan = MixedLeggingsStructure.validData(pair).orElseThrow().left().source().is(sylvan.getItem())
                            || MixedLeggingsStructure.validData(pair).orElseThrow().right().source().is(sylvan.getItem());
                    boolean enabled = available(hasSylvan);
                    check(player, pair, enabled, hasSylvan, 10, 0, 0, EquipmentSlot.LEGS, null);
                }

                boolean enabled = available(true);
                check(player, s.primary(), enabled, true, 0, 0, 0, EquipmentSlot.LEGS, null);
                check(player, s.primary(), enabled, true, 10, 40, 0, EquipmentSlot.LEGS, null);
                check(player, s.primary(), enabled, true, 10, 39.5F, 0, EquipmentSlot.LEGS, null);
                check(player, s.primary(), enabled, true, 10, 0, 1, EquipmentSlot.LEGS, null);
                check(player, s.primary(), false, true, 10, 0, 0, null, null);
                check(player, w.primary(), available(false), false, 0, 0, 0, EquipmentSlot.LEGS, null);
                check(player, plain.primary(), false, false, 10, 0, 0, EquipmentSlot.LEGS, null);
                ItemStack bad = s.primary().copy();
                bad.setCount(2);
                check(player, bad, false, true, 10, 0, 0, EquipmentSlot.LEGS, null);
                bad = s.primary().copy();
                bad.remove(LeggingsContent.mixedLeggingsDataType());
                check(player, bad, false, true, 10, 0, 0, EquipmentSlot.LEGS, null);
                bad = new ItemStack(Items.STICK);
                bad.applyComponents(s.primary().getComponents());
                check(player, bad, false, true, 10, 0, 0, EquipmentSlot.LEGS, null);
                bad = s.primary().copy();
                bad.set(DataComponents.EQUIPPABLE,
                        net.minecraft.world.item.equipment.Equippable.builder(EquipmentSlot.HEAD).build());
                check(player, bad, false, true, 10, 0, 0, EquipmentSlot.LEGS, null);
                bad = s.primary().copy();
                bad.set(LeggingsContent.createSuspendedDataType(),
                        SuspendedLeggingsData.of(new CompoundTag(), new CompoundTag(), new CompoundTag()));
                check(player, bad, false, true, 10, 0, 0, EquipmentSlot.LEGS, null);
                for (String location : List.of("legs", "mainhand", "offhand", "backpack")) {
                    boolean active = !location.equals("backpack");
                    int dispatches = check(player, sylvan, active, true, 10, 0, 0, EquipmentSlot.LEGS, location, -1);
                    check(player, s.inverse(), enabled && active, true, 10, 0, 0, EquipmentSlot.LEGS, location, dispatches);
                }

                var nonPlayer = EntityType.ZOMBIE.create(server.overworld(), EntitySpawnReason.COMMAND);
                require(nonPlayer != null, "Missing non-player control");
                ItemStack root = s.primary().copy();
                root.setDamageValue(10);
                try (var scope = RootsTickTestControl.begin(0)) {
                    root.inventoryTick(server.overworld(), nonPlayer, EquipmentSlot.LEGS);
                    require(root.getDamageValue() == (enabled ? 9 : 10), "Non-player repair differs");
                    require(scope.ticks == (enabled ? 1 : 0), "Non-player native tick count differs");
                    cases++;
                } finally {
                    nonPlayer.discard();
                }
                // A repeated declined candidate must not reenter native code or warn every tick.
                if (!enabled) {
                    check(player, s.primary(), false, true, 10, 0, 0, EquipmentSlot.LEGS, null);
                } else {
                    separateRolls(player, s.primary());
                    if (Boolean.getBoolean("leggings.test.boots")) {
                        coexistence(player, s.primary());
                    }
                }
            }

            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "roots-ticks.txt"),
                    VARIANT + ": " + cases + " native callback/root damage/Terra cases; immutable sources; no periodic Terra event posted\n");
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

    private static boolean available(boolean sylvan) {
        return !VARIANT.equals("helper") && (!sylvan || !VARIANT.equals("receiver"));
    }

    private static void proofs(ItemStack sylvan, ItemStack wildwood) throws Exception {
        for (var source : List.of(sylvan, wildwood)) {
            boolean expected = !source.is(sylvan.getItem()) || !VARIANT.equals("receiver");
            require(source.getItem().getClass().getMethod("mismatchedleggings$rootsTickReady").invoke(source.getItem()).equals(expected),
                    "Final native receiver did not publish the Leggings-local proof");
            if (Boolean.getBoolean("leggings.test.boots")) {
                require(source.getItem().getClass().getMethod("mismatchedboots$rootsTickReady").invoke(source.getItem()).equals(expected),
                        "Actual Boots proof disappeared or was overwritten during coexistence");
            }
        }
        var util = Class.forName("elucent.rootsclassic.util.RootsUtil");
        require(util.getMethod("mismatchedleggings$rootsRepairReady").invoke(null).equals(!VARIANT.equals("helper")),
                "Final helper did not publish the Leggings-local proof");
        if (Boolean.getBoolean("leggings.test.boots")) {
            require(util.getMethod("mismatchedboots$rootsRepairReady").invoke(null).equals(!VARIANT.equals("helper")),
                    "Actual Boots helper proof disappeared during coexistence");
        }
    }

    private static void separateRolls(ServerPlayer player, ItemStack input) throws Exception {
        for (int repairRoll : List.of(0, 1)) {
            var root = input.copy();
            root.setDamageValue(10);
            var before = MixedLeggingsStructure.validData(root);
            var mana = attachment(player);
            mana.getClass().getMethod("setMana", float.class).invoke(mana, 0F);
            try (var scope = RootsTickTestControl.begin(repairRoll, 1 - repairRoll)) {
                root.inventoryTick(player.level(), player, EquipmentSlot.LEGS);
                require(scope.ticks == 1 && scope.repairs == 1 && scope.bounds.equals(List.of(80, 40)),
                        "Repair and Terra rolls were merged, reordered or repeated");
                require(root.getDamageValue() == (repairRoll == 0 ? 9 : 10), "Independent repair outcome changed");
                require(((Number) mana.getClass().getMethod("getMana").invoke(mana)).floatValue() == repairRoll,
                        "Independent Terra outcome changed");
                require(before.equals(MixedLeggingsStructure.validData(root)), "Independent rolls changed sources");
                cases++;
            }
        }
    }

    private static void coexistence(ServerPlayer player, ItemStack input) throws Exception {
        player.getInventory().clearContent();
        var legs = input.copy();
        var boots = BootsCoexistence.pair(nativeItem("sylvan_boots"), new ItemStack(Items.IRON_BOOTS)).getFirst();
        legs.setDamageValue(10);
        boots.setDamageValue(10);
        var legsData = MixedLeggingsStructure.validData(legs);
        var feetBefore = boots.copy();
        player.setItemSlot(EquipmentSlot.LEGS, legs);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        var mana = attachment(player);
        mana.getClass().getMethod("setMana", float.class).invoke(mana, 0F);
        try (var scope = RootsTickTestControl.begin(0)) {
            legs.inventoryTick(player.level(), player, EquipmentSlot.LEGS);
            require(legs.getDamageValue() == 9 && ItemStack.matches(boots, feetBefore), "Leggings dispatch mutated sibling Boots");
            boots.inventoryTick(player.level(), player, EquipmentSlot.FEET);
            require(legs.getDamageValue() == 9 && boots.getDamageValue() == 9, "Sibling dispatch did not repair its own root");
            require(scope.ticks == 2 && scope.repairs == 2 && scope.bounds.equals(List.of(80, 40, 80, 40)),
                    "Optional siblings duplicated or suppressed each other's native callbacks");
            require(((Number) mana.getClass().getMethod("getMana").invoke(mana)).floatValue() == 2,
                    "Both native Sylvan callbacks did not contribute independent player Terra");
            feetBefore.setDamageValue(9);
            require(ItemStack.matches(boots, feetBefore) && legsData.equals(MixedLeggingsStructure.validData(legs)),
                    "Sibling tick changed stored sources or unrelated root state");
            cases++;
        }
    }

    private static ItemStack nativeItem(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("rootsclassic", name)).orElseThrow());
    }

    private static void check(ServerPlayer player, ItemStack input, boolean enabled, boolean sylvan,
                              int damage, float mana, int roll, EquipmentSlot slot, String location) throws Exception {
        check(player, input, enabled, sylvan, damage, mana, roll, slot, location, 1);
    }

    private static int check(ServerPlayer player, ItemStack input, boolean enabled, boolean sylvan,
                             int damage, float mana, int roll, EquipmentSlot slot, String location, int dispatches) throws Exception {
        player.getInventory().clearContent();
        for (EquipmentSlot equipment : List.of(EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST,
                                               EquipmentSlot.HEAD, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
            player.setItemSlot(equipment, ItemStack.EMPTY);
        }

        ItemStack root = input.copy();
        root.setDamageValue(damage);
        root.set(DataComponents.CUSTOM_NAME, Component.literal("Roots native callback subject"));
        var history = MixedLeggingsStructure.validData(root);
        ItemStack leftBefore = history.map(data -> data.left().source()).orElse(ItemStack.EMPTY);
        ItemStack rightBefore = history.map(data -> data.right().source()).orElse(ItemStack.EMPTY);
        Object attachment = manaSupplier == null ? null : attachment(player);
        if (attachment != null) {
            attachment.getClass().getMethod("setMaxMana", float.class).invoke(attachment, 40F);
            attachment.getClass().getMethod("setMana", float.class).invoke(attachment, mana);
        }
        if (location != null) {
            switch (location) {
                case "legs" -> player.setItemSlot(EquipmentSlot.LEGS, root);
                case "mainhand" -> player.setItemSlot(EquipmentSlot.MAINHAND, root);
                case "offhand" -> player.setItemSlot(EquipmentSlot.OFFHAND, root);
                case "backpack" -> player.getInventory().setItem(10, root);
                default -> throw new AssertionError(location);
            }
        }

        try (var scope = RootsTickTestControl.begin(roll)) {
            if (location == null) {
                root.inventoryTick(player.level(), player, slot);
            } else {
                player.aiStep();
            }
            if (dispatches < 0) {
                require(!MixedLeggingsStructure.isMixedItem(root), "Native dispatch calibration used mixed leggings");
                dispatches = scope.ticks; // Match native engine dispatch; don't invent per-game-tick deduplication.
                require(dispatches > 0, "Native location control never dispatched its item tick");
            }

            int delta = enabled && damage > 0 && roll == 0 ? dispatches : 0;
            require(root.getDamageValue() == damage - delta, "Root repair differs: " + location + "/" + input);
            if (attachment != null) {
                float expected = enabled && sylvan && roll == 0 ? Math.min(40, mana + dispatches) : mana;
                float actual = ((Number) attachment.getClass().getMethod("getMana").invoke(attachment)).floatValue();
                require(actual == expected, "Extra Terra differs: expected=" + expected + ", actual=" + actual + ", location=" + location);
            }
            // Null/backpack still enter the native receiver, but its slot gate prevents repair/RNG.
            boolean inactiveNative = (slot == null || "backpack".equals(location))
                    && (!MixedLeggingsStructure.isMixedItem(root) || available(sylvan));
            int ticks = enabled || inactiveNative ? dispatches : 0;
            require(scope.ticks == ticks, "Native tick called " + scope.ticks + " times, expected " + ticks + ": " + location);
            require(scope.repairs == (enabled ? dispatches : 0), "Wrong repair callback count: " + scope.repairs);
            List<Integer> bounds = new ArrayList<>();
            for (int invocation = 0; enabled && invocation < dispatches; invocation++) {
                if (damage > 0) {
                    bounds.add(80);
                }
                if (sylvan) {
                    bounds.add(40);
                }
            }
            require(scope.bounds.equals(bounds), "Native roll order/count differs: " + scope.bounds + " vs " + bounds);
            require(history.equals(MixedLeggingsStructure.validData(root)), "Callback mutated source snapshots");
            if (history.isPresent()) {
                var after = MixedLeggingsStructure.validData(root).orElseThrow();
                require(ItemStack.matches(leftBefore, after.left().source())
                                && ItemStack.matches(rightBefore, after.right().source()), "Callback changed recorded source stacks");
            }
            require(Component.literal("Roots native callback subject").equals(root.get(DataComponents.CUSTOM_NAME)),
                    "Callback changed unrelated root state");
            cases++;
            return scope.ticks;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object attachment(ServerPlayer player) {
        return player.getData((Supplier) manaSupplier);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
