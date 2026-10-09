package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Native fall/payment characterization and production FE access, not a mixed ability prototype. */
public final class NeoForgeJustDireNegateFallTest {
    private static boolean finished;
    private static int cases;
    private static final StringBuilder report = new StringBuilder();
    private static ServerPlayer observed;
    private static int fallEvents;
    private static double finalDistance;

    private NeoForgeJustDireNegateFallTest() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, NeoForgeJustDireNegateFallTest::observeFall);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void observeFall(LivingFallEvent event) {
        if (event.getEntity() == observed) {
            fallEvents++;
            finalDistance = event.getDistance();
        }
    }

    private static void tick(MinecraftServer server) {
        if (finished) { return; }
        finished = true;
        try {
            Class<?> ability = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
            Object negate = ability.getField("NEGATEFALLDAMAGE").get(null);
            require((int) ability.getMethod("getFeCost").invoke(negate) == 50, "Native base cost changed; revise characterization");
            nativeMatrix(server);
            equippedTransactions(server);
            NetworkAcceptanceFiles.write("just-dire-negate-fall-report.txt", cases
                    + " characterization cases; native originals through event bus; survival actual health; "
                    + "partial payment; authoritative game modes; Unbreaking; FEET transactions; no mixed Negate implementation\n" + report);
            NetworkAcceptanceFiles.write("just-dire-negate-fall-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("just-dire-negate-fall", failure);
        } finally {
            observed = null;
            server.halt(false);
        }
    }

    private static void nativeMatrix(MinecraftServer server) throws ReflectiveOperationException {
        Fall unprotected = fall(player(server, GameType.SURVIVAL), 9);
        require(unprotected.distance == 9 && unprotected.loss == 6, "Survival control is immune or does not take expected real fall damage");
        record("unprotected survival control", "distance=9 healthLoss=6; connected fixture marked client-loaded");
        for (String tier : List.of("celestigem", "eclipsealloy")) {
            for (int charge : new int[]{0, 49, 50, 51}) {
                nativeFall(server, tier + " threshold " + charge, boots(tier, charge), 9,
                        charge >= 50 ? 0 : 9, charge >= 50 ? 0 : charge, GameType.SURVIVAL);
            }
            nativeFall(server, tier + " full bill", boots(tier, 1_000), 9, 0, 550, GameType.SURVIVAL);
        }
        for (double distance : new double[]{0, .9, 2.5, 4.5}) {
            int remaining = switch ((int) distance) { case 2 -> 900; case 4 -> 800; default -> 1_000; };
            nativeFall(server, "fractional distance " + distance, boots("celestigem", 1_000), distance,
                    0, remaining, GameType.SURVIVAL);
        }
        nativeFall(server, "zero bill still needs base FE", boots("celestigem", 49), .9, .9, 49, GameType.SURVIVAL);
        for (int level : new int[]{1, 3, 10, 12}) {
            ItemStack boots = boots("celestigem", 1_000);
            boots.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), level);
            int remaining = switch (level) { case 1 -> 595; case 3 -> 685; default -> 1_000; };
            nativeFall(server, "Unbreaking " + level, boots, 9, 0, remaining, GameType.SURVIVAL);
        }
        ItemStack discounted = boots("celestigem", 49);
        discounted.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 10);
        nativeFall(server, "discount does not lower eligibility", discounted, 9, 9, 49, GameType.SURVIVAL);
        ItemStack rounded = boots("celestigem", 1_000);
        rounded.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 1);
        nativeFall(server, "discounted fractional bill", rounded, 4.5, 0, 820, GameType.SURVIVAL);
        ItemStack unbreakable = boots("celestigem", 1_000);
        unbreakable.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        nativeFall(server, "native Unbreakable still pays FE", unbreakable, 9, 0, 550, GameType.SURVIVAL);
        ItemStack feather = boots("celestigem", 1_000);
        feather.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FEATHER_FALLING), 4);
        nativeFall(server, "Feather Falling does not discount FE", feather, 9, 0, 550, GameType.SURVIVAL);

        for (GameType mode : List.of(GameType.CREATIVE, GameType.SPECTATOR)) {
            for (int charge : new int[]{0, 49, 50}) {
                nativeFall(server, mode + " threshold " + charge, boots("celestigem", charge), 9,
                        charge >= 50 ? 0 : 9, charge, mode);
            }
        }
        for (String disabled : List.of("tool_enabled", "negatefalldamage_toggle", "negatefalldamage_upgrade_installed")) {
            ItemStack boots = boots("celestigem", 1_000);
            JustDireAttributeTestSupport.set(boots, disabled, false);
            nativeFall(server, disabled + " false", boots, 9, 9, 1_000, GameType.SURVIVAL);
        }
        Map<?, ?> flags = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config")
                .getField("AVAILABLE_ABILITY_MAP").get(null);
        ModConfigSpec.BooleanValue enabled = flags.entrySet().stream()
                .filter(entry -> ((Enum<?>) entry.getKey()).name().equals("NEGATEFALLDAMAGE"))
                .map(entry -> (ModConfigSpec.BooleanValue) entry.getValue()).findFirst().orElseThrow();
        boolean previous = enabled.get();
        try {
            enabled.set(false);
            nativeFall(server, "native config disabled", boots("celestigem", 1_000), 9, 9, 1_000, GameType.SURVIVAL);
        } finally { enabled.set(previous); }

        ItemStack jump = boots("celestigem", 50);
        JustDireAttributeTestSupport.set(jump, "jumpboost_upgrade_installed", true);
        JustDireAttributeTestSupport.set(jump, "jumpboost_value", 2);
        nativeFall(server, "Negate before Jump", jump, 9, 0, 0, GameType.SURVIVAL);
        jump = jump.copy();
        jump.set(JustDireResourceTestSupport.energyComponent(), 49);
        nativeFall(server, "rejected Negate permits native Jump", jump, 9, 7, 49, GameType.SURVIVAL);
        for (EquipmentSlot hand : List.of(EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
            ServerPlayer player = player(server, GameType.SURVIVAL);
            ItemStack boots = boots("celestigem", 50);
            player.setItemSlot(EquipmentSlot.FEET, boots);
            player.setItemSlot(hand, new ItemStack(BuiltInRegistries.ITEM.getValue(
                    Identifier.fromNamespaceAndPath("justdirethings", "blazejet_wand"))));
            Fall result = fall(player, 9);
            require(result.distance == 0 && result.loss == 0 && energy(boots).getAmountAsInt() == 50,
                    "Native hand protection did not precede Negate debit: " + hand + " distance=" + result.distance
                            + " loss=" + result.loss + " FE=" + energy(boots).getAmountAsInt()
                            + " held=" + player.getItemBySlot(hand));
            record("hand precedence " + hand, "distance=0 FE=50 healthLoss=0");
        }
    }

    private static void nativeFall(MinecraftServer server, String label, ItemStack boots, double distance,
                                   double expectedDistance, int expectedEnergy, GameType mode) {
        ServerPlayer player = player(server, mode);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        ItemStack before = boots.copy();
        Fall actual = fall(player, distance);
        require(actual.distance == expectedDistance && energy(boots).getAmountAsInt() == expectedEnergy,
                label + ": distance=" + actual.distance + " FE=" + energy(boots).getAmountAsInt());
        if (mode == GameType.SURVIVAL) {
            ServerPlayer control = player(server, GameType.SURVIVAL);
            ItemStack iron = new ItemStack(Items.IRON_BOOTS);
            if (boots.has(DataComponents.ENCHANTMENTS)) { iron.set(DataComponents.ENCHANTMENTS, boots.get(DataComponents.ENCHANTMENTS)); }
            control.setItemSlot(EquipmentSlot.FEET, iron);
            float expectedLoss = fall(control, expectedDistance).loss;
            require(expectedDistance <= 3 || expectedLoss > 0, label + ": ordinary long-fall control took no damage");
            require(actual.loss == expectedLoss, label + ": wrong actual survival health loss " + actual.loss + " expected=" + expectedLoss);
        }
        unchangedExceptEnergy(before, boots, label);
        record(label, "distance=" + actual.distance + " FE=" + expectedEnergy + " healthLoss=" + actual.loss
                + (mode == GameType.SURVIVAL ? " physical fall" : " event-bus mode control, not physical fall"));
    }

    private static void equippedTransactions(MinecraftServer server) {
        int feetIndex = EquipmentSlot.FEET.getIndex(Inventory.INVENTORY_SIZE);
        require(Inventory.EQUIPMENT_SLOT_MAPPING.get(feetIndex) == EquipmentSlot.FEET, "FEET inventory mapping changed");
        for (boolean inverse : new boolean[]{false, true}) {
            var pair = MixedBootsFactory.createPair(boots("celestigem", 102), new ItemStack(Items.IRON_BOOTS));
            ItemStack root = inverse ? pair.inverse() : pair.primary();
            ItemStack sibling = inverse ? pair.primary() : pair.inverse();
            ItemStack siblingBefore = sibling.copy();
            root.setDamageValue(19);
            ServerPlayer player = player(server, GameType.SURVIVAL);
            player.setItemSlot(EquipmentSlot.FEET, root);
            player.getInventory().setItem(0, boots("eclipsealloy", 999));
            ItemStack mainBefore = player.getInventory().getItem(0).copy();
            ItemAccess slot = ItemAccess.forPlayerSlot(player, feetIndex);
            require(ItemStack.matches(slot.getResource().toStack(), root) && slot.getAmount() == 1,
                    "Slot access does not resolve actual equipped root");
            EnergyHandler handler = slot.getCapability(Capabilities.Energy.ITEM);
            require(handler != null && handler.getAmountAsInt() == 51, "Equipped production FE capability unavailable");
            ItemStack before = root.copy();
            try (var transaction = Transaction.openRoot()) {
                require(handler.extract(450, transaction) == 51 && handler.getAmountAsInt() == 0, "Partial slot extraction unavailable");
            }
            require(ItemStack.matches(before, player.getItemBySlot(EquipmentSlot.FEET)), "Uncommitted FEET debit did not roll back");
            try (var transaction = Transaction.openRoot()) {
                require(handler.extract(450, transaction) == 51, "Partial equipped commit unavailable");
                transaction.commit();
            }
            ItemStack equipped = player.getItemBySlot(EquipmentSlot.FEET);
            require(energy(equipped).getAmountAsInt() == 0, "Slot commit missed actual equipped FE");
            unchangedExceptEnergy(before, equipped, "FEET slot commit");
            require(ItemStack.matches(siblingBefore, sibling) && ItemStack.matches(mainBefore, player.getInventory().getItem(0)),
                    "Equipped debit changed sibling or main inventory receiver");
            record("FEET slot rollback/partial commit inverse=" + inverse,
                    "mapped index=" + feetIndex + "; equipped FE=51 -> 0; captured reference FE=" + energy(root).getAmountAsInt()
                            + "; same equipped reference=" + (equipped == root) + "; damage=19; sources/sibling/main slot unchanged");
        }

        ItemStack root = MixedBootsFactory.createPair(boots("eclipsealloy", 200), new ItemStack(Items.IRON_BOOTS)).primary();
        root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE), 1);
        root.setDamageValue(23);
        ServerPlayer player = player(server, GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.FEET, root);
        ItemStack before = root.copy();
        EnergyHandler slotHandler = ItemAccess.forPlayerSlot(player, feetIndex)
                .getCapability(Capabilities.Energy.ITEM);
        require(slotHandler != null, "Cursed equipped capability unavailable");
        try (var transaction = Transaction.openRoot()) {
            require(slotHandler.extract(450, transaction) == 0, "Review curse slot-exchange limitation: expected blocked armor extraction");
        }
        require(ItemStack.matches(before, player.getItemBySlot(EquipmentSlot.FEET)), "Blocked cursed slot debit changed equipment");
        EnergyHandler direct = energy(player.getItemBySlot(EquipmentSlot.FEET));
        try (var transaction = Transaction.openRoot()) {
            require(direct.extract(450, transaction) == 100, "Direct equipped partial extraction unavailable");
        }
        require(ItemStack.matches(before, root), "Direct equipped rollback changed root");
        try (var transaction = Transaction.openRoot()) {
            require(direct.extract(450, transaction) == 100, "Direct equipped debit unexpectedly obeyed armor removal curse");
            transaction.commit();
        }
        require(player.getItemBySlot(EquipmentSlot.FEET) == root && direct.getAmountAsInt() == 0,
                "Direct equipped commit changed item identity or missed live FE");
        unchangedExceptEnergy(before, root, "Direct cursed equipped commit");
        record("cursed equipped access", "slot exchange rejects; direct equipped rollback/partial commit succeeds; same stack; sources/wear/curse unchanged");

        ItemStack original = boots("celestigem", 100);
        original.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE), 1);
        nativeFall(server, "native cursed boots still pay", original, 9, 0, 0, GameType.SURVIVAL);
        // This is a production baseline, not a test-only mixed Negate implementation.
        root = MixedBootsFactory.createPair(boots("celestigem", 2_000), new ItemStack(Items.IRON_BOOTS)).primary();
        nativeFall(server, "mixed Negate remains unimplemented", root, 9, 9, 1_000, GameType.SURVIVAL);
    }

    private static Fall fall(ServerPlayer player, double distance) {
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        observed = player;
        fallEvents = 0;
        finalDistance = Double.NaN;
        try {
            if (player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL) {
                player.causeFallDamage(distance, 1, player.damageSources().fall());
            } else {
                // Creative/spectator physical falls bypass LivingFallEvent; test the native consumer via its actual bus.
                NeoForge.EVENT_BUS.post(new LivingFallEvent(player, distance, 1));
            }
            require(fallEvents == 1, "Expected one actual native fall event, got " + fallEvents);
            return new Fall(finalDistance, player.getMaxHealth() - player.getHealth());
        } finally { observed = null; }
    }

    private static ServerPlayer player(MinecraftServer server, GameType mode) {
        ServerPlayer player = new ServerPlayer(server, server.overworld(),
                new GameProfile(UUID.randomUUID(), "negate-test"), ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        // Newly constructed listeners have a load timeout; ServerPlayer rejects real damage until it clears.
        player.connection.markClientLoaded();
        player.setGameMode(mode);
        require(player.gameMode.getGameModeForPlayer() == mode && player.gameMode() == mode, "Fixture did not set authoritative game mode");
        return player;
    }

    private static ItemStack boots(String tier, int charge) {
        ItemStack boots = NeoForgeJustDireResourcesTest.boots(tier, charge);
        JustDireAttributeTestSupport.set(boots, "negatefalldamage_upgrade_installed", true);
        return boots;
    }

    private static EnergyHandler energy(ItemStack stack) { return JustDireResourceTestSupport.handler(stack); }

    private static void unchangedExceptEnergy(ItemStack before, ItemStack after, String label) {
        ItemStack expected = before.copy();
        ItemStack actual = after.copy();
        expected.remove(JustDireResourceTestSupport.energyComponent());
        actual.remove(JustDireResourceTestSupport.energyComponent());
        require(ItemStack.matches(expected, actual), label + ": changed components/count/provenance/wear outside FE");
    }

    private static void record(String label, String details) {
        cases++;
        report.append(label).append(": ").append(details).append('\n');
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    private record Fall(double distance, float loss) {}
}
