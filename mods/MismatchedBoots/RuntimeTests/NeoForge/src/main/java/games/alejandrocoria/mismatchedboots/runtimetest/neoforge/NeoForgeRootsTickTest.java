package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Native item/AI inventory dispatch, actual root damage and native player attachment changes. */
public final class NeoForgeRootsTickTest {
    private static final String VARIANT = System.getProperty("mismatchedboots.runtimetest.rootsTicks", "");
    private static boolean finished;
    private static int cases;
    private static Object manaSupplier;

    private NeoForgeRootsTickTest() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void tick(MinecraftServer server) {
        if (finished) {
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
            var plain = MixedBootsFactory.createPair(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
            if (absent) {
                check(player, plain.primary(), false, false, 10, 0, 0, EquipmentSlot.FEET, null);
                check(player, plain.inverse(), false, false, 10, 0, 0, EquipmentSlot.OFFHAND, null);
            } else {
                manaSupplier = Class.forName("elucent.rootsclassic.attachment.RootsAttachments").getField("MANA").get(null);
                ItemStack sylvan = boot("sylvan_boots");
                ItemStack wildwood = boot("wildwood_boots");
                // Native controls remain functional even when our readiness proof is intentionally unavailable.
                check(player, sylvan, true, true, 10, 0, 0, EquipmentSlot.FEET, null);
                check(player, wildwood, true, false, 10, 0, 0, EquipmentSlot.FEET, null);
                List<ItemStack> pairs = new ArrayList<>();
                var s = MixedBootsFactory.createPair(sylvan, new ItemStack(Items.DIAMOND_BOOTS));
                var w = MixedBootsFactory.createPair(wildwood, new ItemStack(Items.DIAMOND_BOOTS));
                var sw = MixedBootsFactory.createPair(sylvan, wildwood);
                pairs.addAll(List.of(s.primary(), s.inverse(), w.primary(), w.inverse(), sw.primary(), sw.inverse(),
                                     MixedBootsFactory.createPair(sylvan, sylvan).primary(),
                                     MixedBootsFactory.createPair(wildwood, wildwood).primary()));
                for (ItemStack pair : pairs) {
                    boolean hasSylvan = ModContent.getMixedBootsData(pair).orElseThrow().left().source().is(sylvan.getItem())
                            || ModContent.getMixedBootsData(pair).orElseThrow().right().source().is(sylvan.getItem());
                    boolean enabled = available(hasSylvan);
                    check(player, pair, enabled, hasSylvan, 10, 0, 0, EquipmentSlot.FEET, null);
                }

                boolean enabled = available(true);
                check(player, s.primary(), enabled, true, 0, 0, 0, EquipmentSlot.FEET, null);
                check(player, s.primary(), enabled, true, 10, 40, 0, EquipmentSlot.FEET, null);
                check(player, s.primary(), enabled, true, 10, 39.5F, 0, EquipmentSlot.FEET, null);
                check(player, s.primary(), enabled, true, 10, 0, 1, EquipmentSlot.FEET, null);
                check(player, s.primary(), false, true, 10, 0, 0, null, null);
                check(player, w.primary(), available(false), false, 0, 0, 0, EquipmentSlot.FEET, null);
                check(player, plain.primary(), false, false, 10, 0, 0, EquipmentSlot.FEET, null);
                ItemStack bad = s.primary().copy();
                bad.setCount(2);
                check(player, bad, false, true, 10, 0, 0, EquipmentSlot.FEET, null);
                bad = s.primary().copy();
                bad.remove(ModContent.mixedBootsDataType().orElseThrow());
                check(player, bad, false, true, 10, 0, 0, EquipmentSlot.FEET, null);
                bad = new ItemStack(Items.STICK);
                bad.applyComponents(s.primary().getComponents());
                check(player, bad, false, true, 10, 0, 0, EquipmentSlot.FEET, null);
                bad = s.primary().copy();
                bad.set(DataComponents.EQUIPPABLE,
                        net.minecraft.world.item.equipment.Equippable.builder(EquipmentSlot.HEAD).build());
                check(player, bad, false, true, 10, 0, 0, EquipmentSlot.FEET, null);
                for (String location : List.of("feet", "mainhand", "offhand", "backpack")) {
                    boolean active = !location.equals("backpack");
                    int dispatches = check(player, sylvan, active, true, 10, 0, 0, EquipmentSlot.FEET, location, -1);
                    check(player, s.inverse(), enabled && active, true, 10, 0, 0, EquipmentSlot.FEET, location, dispatches);
                }

                var nonPlayer = EntityType.ZOMBIE.create(server.overworld(), EntitySpawnReason.COMMAND);
                require(nonPlayer != null, "Missing non-player control");
                ItemStack root = s.primary().copy();
                root.setDamageValue(10);
                try (var scope = RootsTickTestControl.begin(0)) {
                    root.inventoryTick(server.overworld(), nonPlayer, EquipmentSlot.FEET);
                    require(root.getDamageValue() == (enabled ? 9 : 10), "Non-player repair differs");
                    require(scope.ticks == (enabled ? 1 : 0), "Non-player native tick count differs");
                    cases++;
                } finally {
                    nonPlayer.discard();
                }
                // A repeated declined candidate must not reenter native code or warn every tick.
                if (!enabled) {
                    check(player, s.primary(), false, true, 10, 0, 0, EquipmentSlot.FEET, null);
                }
            }

            int setCases = absent ? 0 : NeoForgeRootsSetTest.run(player);
            NetworkAcceptanceFiles.write("roots-ticks-report.txt", VARIANT + ": " + cases
                    + (absent ? " ordinary inverse-item tick cases; quiet optional absence; no native Roots loaded\n"
                              : " cases; actual native callbacks/repair/attachment; item and player AI inventory dispatch; "
                                + "single receiver; immutable sources; no periodic regeneration event posted during tick cases\n")
                    + "Native staff/set cases: " + setCases + "\n");
            NetworkAcceptanceFiles.write("roots-ticks-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("roots-ticks", failure);
        } finally {
            server.halt(false);
        }
    }

    private static boolean available(boolean sylvan) {
        return !VARIANT.equals("helper") && (!sylvan || !VARIANT.equals("receiver"));
    }

    private static ItemStack boot(String name) {
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
        var history = ModContent.getMixedBootsData(root);
        ItemStack leftBefore = history.map(data -> data.left().source()).orElse(ItemStack.EMPTY);
        ItemStack rightBefore = history.map(data -> data.right().source()).orElse(ItemStack.EMPTY);
        Object attachment = manaSupplier == null ? null : attachment(player);
        if (attachment != null) {
            attachment.getClass().getMethod("setMaxMana", float.class).invoke(attachment, 40F);
            attachment.getClass().getMethod("setMana", float.class).invoke(attachment, mana);
        }
        if (location != null) {
            switch (location) {
                case "feet" -> player.setItemSlot(EquipmentSlot.FEET, root);
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
                require(!ModContent.isMixedBoots(root), "Native dispatch calibration used mixed boots");
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
                    && (!ModContent.isMixedBoots(root) || available(sylvan));
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
            require(history.equals(ModContent.getMixedBootsData(root)), "Callback mutated source snapshots");
            if (history.isPresent()) {
                var after = ModContent.getMixedBootsData(root).orElseThrow();
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
