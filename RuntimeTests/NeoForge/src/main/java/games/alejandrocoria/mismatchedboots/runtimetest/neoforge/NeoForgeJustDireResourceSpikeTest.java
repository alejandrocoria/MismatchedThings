package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.List;
import java.util.UUID;

/** Real FE/ItemAccess lifecycle experiment, not acceptance of production resource integration. */
public final class NeoForgeJustDireResourceSpikeTest {
    private static boolean finished;
    private static int cases;

    private NeoForgeJustDireResourceSpikeTest() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void tick(MinecraftServer server) {
        if (finished) { return; }
        finished = true;
        try {
            matrix(server);
            NetworkAcceptanceFiles.write("just-dire-resources-report.txt", cases + " storage spike scenarios; "
                    + "native tier capacities; maximum capacity/floor-average initial charge; both inverse outputs; "
                    + "external recharge/source debit; transaction rollback; repeated/partial extraction; "
                    + "native PoweredItem consumption/transmitter primitive; actual player-slot access; "
                    + "retained handler tier/count replacement; live recomposition including projection equality/zero/absence; "
                    + "save/load; invalid roots/charge; immutable provenance; frozen powered-attribute characterization; "
                    + "no production resource/ability hooks\n");
            NetworkAcceptanceFiles.write("just-dire-resources-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("just-dire-resources", failure);
        } finally { server.halt(false); }
    }

    private static void matrix(MinecraftServer server) throws ReflectiveOperationException {
        ItemStack iron = new ItemStack(Items.IRON_BOOTS);
        require(energy(boots("celestigem", 6_000)).getCapacityAsInt() == 10_000
                && energy(boots("eclipsealloy", 1)).getCapacityAsInt() == 500_000, "Native tier capacity mismatch");
        creation(boots("celestigem", 6_000), iron, 10_000, 3_000);
        creation(iron, boots("celestigem", 6_000), 10_000, 3_000);
        creation(boots("celestigem", 1), iron, 10_000, 0);
        creation(boots("celestigem", 0), boots("eclipsealloy", 0), 500_000, 0);
        creation(boots("celestigem", 10_000), boots("eclipsealloy", 500_000), 500_000, 255_000);
        creation(boots("celestigem", 5), boots("celestigem", 4), 10_000, 4);
        var unpowered = JustDireResourceSpike.createPair(boots("ferricore", 0), iron);
        require(energy(unpowered.primary()) == null && energy(unpowered.inverse()) == null
                && !unpowered.primary().has(JustDireResourceSpike.energyComponent())
                && !unpowered.inverse().has(JustDireResourceSpike.energyComponent()), "Unpowered creation acquired FE storage");
        cases++;

        var pair = JustDireResourceSpike.createPair(boots("celestigem", 6_000), iron);
        ItemStack root = pair.primary();
        ItemStack inverseBefore = pair.inverse().copy();
        var provenance = data(root);
        ItemStack source = boots("eclipsealloy", 20_000);
        require(EnergyHandlerUtil.move(energy(source), energy(root), 20_000, null) == 7_000
                && energy(source).getAmountAsInt() == 13_000 && energy(root).getAmountAsInt() == 10_000,
                "Recharge duplicated FE or ignored maximum space");
        require(EnergyHandlerUtil.move(energy(source), energy(root), 1, null) == 0, "Full root accepted more energy");
        unchanged(root, provenance, pair.inverse(), inverseBefore);
        cases++;

        ItemStack before = root.copy();
        ItemStack sourceBefore = source.copy();
        try (var transaction = Transaction.openRoot()) {
            require(energy(root).extract(2_000, transaction) == 2_000, "Extraction simulation unavailable");
        }
        require(ItemStack.matches(before, root), "Aborted extraction mutated live root");
        extract(root, 2_000);
        before = root.copy();
        try (var transaction = Transaction.openRoot()) {
            require(EnergyHandlerUtil.move(energy(source), energy(root), 2_000, transaction) == 2_000,
                    "Nested recharge unavailable");
        }
        require(ItemStack.matches(before, root) && ItemStack.matches(sourceBefore, source), "Aborted recharge changed endpoints");
        require(extract(root, 3_000) == 3_000 && extract(root, 50_000) == 5_000 && extract(root, 1) == 0,
                "Repeated/partial extraction did not exhaust current root");
        require(EnergyHandlerUtil.move(energy(source), energy(root), 10_000, null) == 10_000
                && energy(root).getAmountAsInt() == 10_000 && energy(source).getAmountAsInt() == 3_000,
                "Exhausted root could not externally recharge");
        unchanged(root, provenance, pair.inverse(), inverseBefore);
        cases++;

        Class<?> powered = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.PoweredItem");
        require((int) powered.getMethod("getAvailableEnergy", ItemStack.class).invoke(null, root) == 10_000
                && (int) powered.getMethod("getMaxEnergyFor", ItemStack.class).invoke(null, root) == 10_000,
                "Native static FE queries did not see mixed capability");
        require((boolean) powered.getMethod("consumeEnergy", net.minecraft.world.entity.player.Player.class,
                ItemStack.class, int.class).invoke(null, null, root, 50) && energy(root).getAmountAsInt() == 9_950,
                "Native FE consumption did not reach live root");
        require(!(boolean) powered.getMethod("consumeEnergy", net.minecraft.world.entity.player.Player.class,
                ItemStack.class, int.class).invoke(null, null, root, 20_000) && energy(root).getAmountAsInt() == 0,
                "Native insufficient payment no longer drains partial energy; review later ability semantics");
        require(!powered.isInstance(root.getItem()), "Prototype impersonated native PoweredItem");
        ItemStack nativeControl = boots("celestigem", 100);
        require((boolean) powered.getMethod("consumeEnergy", net.minecraft.world.entity.player.Player.class,
                ItemStack.class, int.class).invoke(null, null, nativeControl, 50)
                && energy(nativeControl).getAmountAsInt() == 50,
                "Native original exact-payment control failed");
        require(!(boolean) powered.getMethod("consumeEnergy", net.minecraft.world.entity.player.Player.class,
                ItemStack.class, int.class).invoke(null, null, nativeControl, 100)
                && energy(nativeControl).getAmountAsInt() == 0, "Native original partial-payment control failed");
        cases++;

        Class<?> transmitter = Class.forName("com.direwolf20.justdirethings.common.blockentities.EnergyTransmitterBE");
        var block = BuiltInRegistries.BLOCK.get(Identifier.fromNamespaceAndPath("justdirethings", "energytransmitter"))
                .orElseThrow().value();
        Object machine = transmitter.getConstructor(BlockPos.class, net.minecraft.world.level.block.state.BlockState.class)
                .newInstance(BlockPos.ZERO, block.defaultBlockState());
        require((int) transmitter.getMethod("transmitPower", EnergyHandler.class, EnergyHandler.class, int.class)
                .invoke(machine, energy(source), energy(root), 1_000) == 1_000
                && energy(root).getAmountAsInt() == 1_000 && energy(source).getAmountAsInt() == 2_000,
                "Native transmitter transfer primitive did not charge/debit actual stacks");
        unchanged(root, provenance, pair.inverse(), inverseBefore);
        cases++;

        ServerPlayer player = new ServerPlayer(server, server.overworld(),
                new GameProfile(UUID.randomUUID(), "resources-spike"), ClientInformation.createDefault());
        player.getInventory().setItem(0, root.copy());
        ItemAccess slot = ItemAccess.forPlayerSlot(player, 0);
        EnergyHandler retained = slot.getCapability(Capabilities.Energy.ITEM);
        require(retained != null, "Player-slot capability unavailable");
        ItemStack slotBefore = player.getInventory().getItem(0).copy();
        try (var transaction = Transaction.openRoot()) { retained.extract(500, transaction); }
        require(ItemStack.matches(slotBefore, player.getInventory().getItem(0)), "Slot simulation replaced its stack");
        try (var transaction = Transaction.openRoot()) {
            require(retained.extract(500, transaction) == 500, "Slot extraction unavailable");
            transaction.commit();
        }
        require(energy(player.getInventory().getItem(0)).getAmountAsInt() == 500,
                "Slot commit did not persist exchanged resource");
        cases++;

        ItemStack higher = JustDireResourceSpike.recompose(player.getInventory().getItem(0), boots("eclipsealloy", 500_000), iron);
        player.getInventory().setItem(0, higher);
        require(retained.getCapacityAsInt() == 500_000 && retained.getAmountAsInt() == 500,
                "Retained handler used stale tier capacity");
        player.getInventory().setItem(0, new ItemStack(Items.IRON_BOOTS));
        require(retained.getCapacityAsInt() == 0 && retained.getAmountAsInt() == 0, "Retained handler exposed replacement iron");
        player.getInventory().setItem(0, root.copyWithCount(2));
        try (var transaction = Transaction.openRoot()) {
            require(retained.insert(1, transaction) == 0 && retained.extract(1, transaction) == 0,
                    "Retained handler accepted malformed root count");
            transaction.commit();
        }
        cases++;

        root.setDamageValue(31);
        root.set(DataComponents.CUSTOM_NAME, Component.literal("Live resource state"));
        root.set(DataComponents.REPAIR_COST, 11);
        root.set(integerComponent("jumpboost_value"), 1);
        ItemStack current = root.copy();
        ItemStack upgraded = JustDireResourceSpike.recompose(root, boots("eclipsealloy", 500_000), iron);
        require(energy(upgraded).getAmountAsInt() == 1_000 && energy(upgraded).getCapacityAsInt() == 500_000
                && ItemStack.matches(current, root) && upgraded.getDamageValue() == 31
                && upgraded.getHoverName().equals(root.getHoverName()) && upgraded.get(DataComponents.REPAIR_COST) == 11
                && upgraded.get(integerComponent("jumpboost_value")) == 1,
                "Higher tier refilled energy, mutated input, or lost live state");
        upgraded.set(JustDireResourceSpike.energyComponent(), 450_000);
        ItemStack downgraded = JustDireResourceSpike.recompose(upgraded, boots("celestigem", 0), iron);
        require(energy(downgraded).getAmountAsInt() == 10_000 && energy(downgraded).getCapacityAsInt() == 10_000,
                "Reduced capacity did not clamp current charge");
        ItemStack removed = JustDireResourceSpike.recompose(downgraded, boots("ferricore", 0), iron);
        require(energy(removed) == null && !removed.has(JustDireResourceSpike.energyComponent()), "Removed powered source retained energy");
        cases++;

        ItemStack equality = JustDireResourceSpike.createPair(boots("celestigem", 0), iron).primary();
        ItemStack equalityResult = JustDireResourceSpike.recompose(equality, boots("eclipsealloy", 500_000), iron);
        require(energy(equalityResult).getAmountAsInt() == 0, "Old owner projection equality resurrected source charge");
        equality.remove(JustDireResourceSpike.energyComponent());
        equalityResult = JustDireResourceSpike.recompose(equality, boots("eclipsealloy", 500_000), iron);
        require(!equalityResult.has(JustDireResourceSpike.energyComponent()) && energy(equalityResult).getAmountAsInt() == 0,
                "Explicit absence resurrected source charge");
        ItemStack equalChargedSource = boots("celestigem", 6_000);
        equality = JustDireResourceSpike.createPair(equalChargedSource, equalChargedSource).primary();
        equalityResult = JustDireResourceSpike.recompose(equality, boots("eclipsealloy", 500_000), equalChargedSource);
        require(energy(equalityResult).getAmountAsInt() == 6_000, "Nonzero owner projection equality resurrected source charge");
        cases++;

        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var encoded = ItemStack.CODEC.encodeStart(ops, root).getOrThrow();
        ItemStack decoded = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        require(ItemStack.matches(root, decoded) && energy(decoded).getAmountAsInt() == 1_000,
                "Persistence recreated original source charge");
        cases++;

        for (ItemStack invalid : List.of(new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID)),
                root.copyWithCount(2), MixedBootsFactory.createPair(iron, iron).primary())) {
            invalid.set(JustDireResourceSpike.energyComponent(), 6_000);
            ItemStack invalidBefore = invalid.copy();
            require(energy(invalid) == null && ItemStack.matches(invalidBefore, invalid),
                    "Invalid/forged root gained FE or changed on lookup: " + invalid);
        }
        for (int invalidAmount : new int[]{-1, 10_001}) {
            ItemStack invalid = root.copy(); invalid.set(JustDireResourceSpike.energyComponent(), invalidAmount);
            ItemStack invalidBefore = invalid.copy();
            require(energy(invalid) == null && ItemStack.matches(invalidBefore, invalid), "Invalid charge was usable or silently repaired");
        }
        ItemStack forged = iron.copy(); forged.set(JustDireResourceSpike.energyComponent(), 9_999);
        creation(boots("celestigem", 6_000), forged, 10_000, 3_000);
        for (int invalidAmount : new int[]{-1, 10_001}) {
            ItemStack invalidSource = boots("celestigem", invalidAmount);
            ItemStack invalidBefore = invalidSource.copy();
            boolean rejected = false;
            try { JustDireResourceSpike.createPair(invalidSource, iron); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected && ItemStack.matches(invalidBefore, invalidSource), "Invalid input charge was accepted/mutated");
        }
        cases++;

        // Characterize a known missing production boundary, rather than silently certifying it.
        ItemStack nativeAttributes = boots("celestigem", 50);
        require(!nativeAttributes.getAttributeModifiers().modifiers().isEmpty(), "Charged native armor had no attributes");
        nativeAttributes.set(JustDireResourceSpike.energyComponent(), 49);
        require(nativeAttributes.getAttributeModifiers().modifiers().isEmpty(), "Native empty-energy attribute threshold changed");
        ItemStack frozen = JustDireResourceSpike.createPair(boots("celestigem", 6_000), iron).primary();
        var captured = frozen.getAttributeModifiers();
        extract(frozen, 3_000);
        require(!captured.modifiers().isEmpty() && frozen.getAttributeModifiers().equals(captured),
                "Storage-only spike unexpectedly changed powered attribute behavior");
        cases++;
    }

    private static void creation(ItemStack left, ItemStack right, int capacity, int charge) {
        ItemStack leftBefore = left.copy();
        ItemStack rightBefore = right.copy();
        var pair = JustDireResourceSpike.createPair(left, right);
        for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
            require(energy(root).getCapacityAsInt() == capacity && energy(root).getAmountAsInt() == charge,
                    "Initial maximum capacity/floor-average charge mismatch");
        }
        require(ItemStack.matches(leftBefore, left) && ItemStack.matches(rightBefore, right), "Creation changed its sources");
        cases++;
    }

    private static ItemStack boots(String tier, int charge) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", tier + "_boots"))
                .orElseThrow().value());
        if (tier.equals("celestigem") || tier.equals("eclipsealloy")) { stack.set(JustDireResourceSpike.energyComponent(), charge); }
        return stack;
    }

    private static EnergyHandler energy(ItemStack stack) { return JustDireResourceSpike.handler(stack); }

    @SuppressWarnings("unchecked")
    private static DataComponentType<Integer> integerComponent(String path) {
        return (DataComponentType<Integer>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value();
    }

    private static int extract(ItemStack root, int amount) {
        try (var transaction = Transaction.openRoot()) {
            int extracted = energy(root).extract(amount, transaction);
            transaction.commit();
            return extracted;
        }
    }

    private static MixedBootsData data(ItemStack root) {
        return root.get(ModContent.mixedBootsDataType().orElseThrow());
    }

    private static void unchanged(ItemStack root, MixedBootsData provenance,
                                  ItemStack inverse, ItemStack inverseBefore) {
        require(provenance.equals(data(root)) && ItemStack.matches(inverse, inverseBefore), "Spending changed sources/inverse output");
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }
}
