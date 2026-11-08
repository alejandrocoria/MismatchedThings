package games.alejandrocoria.mismatchedleggings.test.mystical;

import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

public final class SpikeChecks {
    private static boolean finished;
    public static final StringBuilder REPORT = new StringBuilder();

    private SpikeChecks() {}

    public static boolean enabled() { return Boolean.getBoolean("leggings.test.mysticalSpike"); }
    public static boolean absent() { return Boolean.getBoolean("leggings.test.maAbsent"); }

    public static void seed(MinecraftServer server) {
        if (!server.isDedicatedServer() || finished || server.getTickCount() < 20) { return; }
        finished = true;
        try {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),
                    "setworldspawn 0 " + (server.overworld().getMinY() + 1) + " 0");
            server.saveEverything(true, true, true);
            SkeletonChecks.passed("server");
        } catch (Throwable failure) { SkeletonChecks.failed("server", failure); }
        finally { server.halt(false); }
    }

    public static void initialize(ServerPlayer player) throws Exception {
        player.setGameMode(GameType.SURVIVAL);
        player.setNoGravity(true);
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        player.fallDistance = 0;
        player.connection.teleport(.5, player.level().getMinY() + 1, .5, 0, 0);
        player.getInventory().clearContent();
        player.getFoodData().setFoodLevel(20);
        if (absent()) {
            var pair = games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory.createPair(new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_LEGGINGS));
            require(!pair.primary().isEmpty(), "Optional absence broke ordinary composition");
            return;
        }
        var source = MysticalCandidate.item("awakened_supremium_leggings", "SPEED_I", "SLOWNESS_RESISTANCE");
        var other = MysticalCandidate.item("awakened_supremium_leggings", "SPEED_III", "HEALTH_BOOST_III");
        var first = source.copy();
        var second = other.copy();
        var pair = games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory.createPair(source, other);
        for (var root : List.of(pair.primary(), pair.inverse())) {
            var ids = MysticalCandidate.selected(root).stream().map(MysticalCandidate::id).toList();
            require(ids.size() == 3 && ids.contains("mysticalagriculture:speed_iii") && !ids.contains("mysticalagriculture:speed_i"), "Strongest selection failed");
            player.setItemSlot(EquipmentSlot.LEGS, root);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 200));
            MysticalCandidate.tick(root, player.level(), player, EquipmentSlot.LEGS);
            require(!player.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS), "Native Slowness callback not reused");
        }
        require(ItemStack.matches(source, first) && ItemStack.matches(other, second), "Candidate changed input sources");
        REPORT.append("Strongest family selection in both orientations, source immutability and native Slowness removal passed\n");
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        require(breaks(player, MysticalCandidate.item("inferium_leggings", "LUCK_I"), EquipmentSlot.LEGS, "LUCK_I"), "Native Cucumber refund positive failed");
        for (boolean inverse : List.of(false, true)) {
            var root = MysticalCandidate.mixed("SPEED_III", inverse);
            String purchase = inverse ? "LUCK_I" : "SPEED_III";
            require(breaks(player, root, EquipmentSlot.LEGS, purchase), "Leggings physical refund failed");
        }
        REPORT.append("Native break refund positive and both mixed left-source refunds passed, with no duplicate delivery\n");
        if (!System.getProperty("leggings.test.maGraph").equals("alone")) {
            var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
            Object roots = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null,
                    MysticalCandidate.item("awakened_supremium_boots", "LUCK_I"), MysticalCandidate.item("inferium_boots", "HEALTH_BOOST_I"));
            var boots = (ItemStack) roots.getClass().getMethod("primary").invoke(roots);
            boolean returned = breaks(player, boots, EquipmentSlot.FEET, "LUCK_I");
            REPORT.append("Boots physical refund with candidate ").append(System.getProperty("leggings.test.maRefund")).append(" = ").append(returned).append('\n');
            if (System.getProperty("leggings.test.maRefund").equals("observer")) { require(returned, "Observer disabled Boots refunds"); }
        }
        player.getInventory().clearContent();
        write("server-results.txt", REPORT.toString());
    }

    private static boolean breaks(ServerPlayer player, ItemStack root, EquipmentSlot slot, String purchase) {
        player.getInventory().clearContent();
        Item wanted = (Item) MysticalCandidate.invoke(MysticalCandidate.augment(purchase), "getItem", new Class<?>[0]);
        player.setItemSlot(slot, root);
        root.hurtAndBreak(root.getMaxDamage(), player, slot);
        int count = player.getInventory().countItem(wanted);
        require(root.isEmpty() && count <= 1, "Break did not consume root or duplicated refund");
        return count == 1;
    }

    public static void health(ServerPlayer player, int scene) throws Exception {
        // -1: real worn armor callbacks; 0..3: native/primary/inverse/removal;
        // 4..7: original/mixed Boots coexistence, then removing each contribution.
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        player.removeAllEffects();
        if (scene == -1) {
            var root = games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory.createPair(
                    MysticalCandidate.item("awakened_supremium_leggings", "SLOWNESS_RESISTANCE", "FIRE_RESISTANCE"),
                    MysticalCandidate.item("inferium_leggings", "LUCK_I")).primary();
            player.setItemSlot(EquipmentSlot.LEGS, root);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 200));
            player.setRemainingFireTicks(100);
        }
        if (scene >= 0 && scene < 3) {
            player.setItemSlot(EquipmentSlot.LEGS, scene == 0
                    ? MysticalCandidate.item("awakened_supremium_leggings", "HEALTH_BOOST_III", "STEP_ASSIST")
                    : healthRoot(scene == 2));
        }
        if (scene == 4 || scene == 5) { player.setItemSlot(EquipmentSlot.LEGS, healthRoot(scene == 5)); }
        if (scene == 4) { player.setItemSlot(EquipmentSlot.FEET, MysticalCandidate.item("inferium_boots", "HEALTH_BOOST_I")); }
        if (scene == 5 || scene == 6) {
            var factory = Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
            Object roots = factory.getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null,
                    MysticalCandidate.item("inferium_boots", "HEALTH_BOOST_I"), new ItemStack(Items.IRON_BOOTS));
            player.setItemSlot(EquipmentSlot.FEET, (ItemStack) roots.getClass().getMethod("primary").invoke(roots));
        }
        player.setHealth(Math.min(20, player.getMaxHealth()));
    }

    private static ItemStack healthRoot(boolean inverse) {
        var pair = games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory.createPair(
                MysticalCandidate.item("awakened_supremium_leggings", "HEALTH_BOOST_III", "STEP_ASSIST"),
                MysticalCandidate.item("inferium_leggings", "HEALTH_BOOST_I"));
        return inverse ? pair.inverse() : pair.primary();
    }

    public static void verifyHealth(ServerPlayer player, int scene) {
        if (scene == -1) {
            require(!player.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS)
                    && player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)
                    && player.hasEffect(net.minecraft.world.effect.MobEffects.LUCK) && !player.isOnFire(),
                    "Actual worn mixed inventory tick did not dispatch native armor effects");
            REPORT.append("Actual worn LEGS inventory tick: Slowness removal, Fire Resistance/extinguishing and Luck passed\n");
            return;
        }
        require(Math.abs(player.getMaxHealth() - expectedHealth(scene)) < .001, "Native/candidate health/cache mismatch " + scene + ": " + player.getMaxHealth());
        require(Math.abs(player.getAttributeValue(Attributes.STEP_HEIGHT) - (scene < 3 || scene == 4 || scene == 5 ? 1.6 : .6)) < .001, "Native/candidate step/cache mismatch");
        require(player.isAlive(), "Health cleanup killed real server player");
    }

    public static int expectedHealth(int scene) { return scene >= 0 && scene < 3 || scene == 4 || scene == 5 ? 32 : scene == 6 ? 24 : 20; }

    public static void write(String name, String value) throws Exception {
        Files.writeString(Path.of(System.getProperty("leggings.test.report"), name), value);
    }
}
