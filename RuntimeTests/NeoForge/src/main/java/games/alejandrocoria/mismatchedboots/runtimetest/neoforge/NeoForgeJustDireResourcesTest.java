package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentRule;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.menu.MismatchedWorkbenchMenu;
import games.alejandrocoria.mismatchedboots.recipe.MismatchedWorkbenchInput;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsWorkbenchRecipe;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.List;
import java.util.UUID;

/** Production-JAR FE lifecycle gate, with no fixture resource provider or composition wrappers. */
public final class NeoForgeJustDireResourcesTest {
    private static boolean finished;
    private static int cases;

    private NeoForgeJustDireResourcesTest() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void tick(MinecraftServer server) {
        if (finished) { return; }
        finished = true;
        try {
            if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireResourcesRestart")) {
                verifyRestart(server);
                NetworkAcceptanceFiles.write("just-dire-resources-restart-success.txt", "neoforge\n");
                return;
            }
            matrix(server);
            NetworkAcceptanceFiles.write("just-dire-resources-report.txt", cases + " production storage scenario groups; "
                    + "native tier capacities; maximum capacity/floor-average initial charge; both inverse outputs; "
                    + "external recharge/source debit; transaction rollback; repeated/partial extraction; "
                    + "native PoweredItem consumption/transmitter primitive; actual player-slot access; "
                    + "retained handler tier/count replacement; live recomposition including projection equality/zero/absence; "
                    + "save/load; invalid roots/charge; immutable provenance; live powered-attribute depletion; "
                    + "actual Workbench previews/takes/blocked take/close; loaded native smithing upgrade/takes; "
                    + "configured rejection/generic FE policy; real world save fixture; production storage/attribute lifecycle; powered abilities deferred\n");
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
        var unpowered = MixedBootsFactory.createPair(boots("ferricore", 0), iron);
        require(energy(unpowered.primary()) == null && energy(unpowered.inverse()) == null
                && !unpowered.primary().has(JustDireResourceTestSupport.energyComponent())
                && !unpowered.inverse().has(JustDireResourceTestSupport.energyComponent()), "Unpowered creation acquired FE storage");
        cases++;

        var pair = MixedBootsFactory.createPair(boots("celestigem", 6_000), iron);
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
        require(!powered.isInstance(root.getItem()), "Mixed item impersonated native PoweredItem");
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
                new GameProfile(UUID.randomUUID(), "resources-test"), ClientInformation.createDefault());
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

        ItemStack higher = MixedBootsRecomposer.recompose(player.getInventory().getItem(0), boots("eclipsealloy", 500_000), iron);
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
        ItemStack upgraded = MixedBootsRecomposer.recompose(root, boots("eclipsealloy", 500_000), iron);
        require(energy(upgraded).getAmountAsInt() == 1_000 && energy(upgraded).getCapacityAsInt() == 500_000
                && ItemStack.matches(current, root) && upgraded.getDamageValue() == 31
                && upgraded.getHoverName().equals(root.getHoverName()) && upgraded.get(DataComponents.REPAIR_COST) == 11
                && upgraded.get(integerComponent("jumpboost_value")) == 1,
                "Higher tier refilled energy, mutated input, or lost live state");
        upgraded.set(JustDireResourceTestSupport.energyComponent(), 450_000);
        ItemStack downgraded = MixedBootsRecomposer.recompose(upgraded, boots("celestigem", 0), iron);
        require(energy(downgraded).getAmountAsInt() == 10_000 && energy(downgraded).getCapacityAsInt() == 10_000,
                "Reduced capacity did not clamp current charge");
        ItemStack removed = MixedBootsRecomposer.recompose(downgraded, boots("ferricore", 0), iron);
        require(energy(removed) == null && !removed.has(JustDireResourceTestSupport.energyComponent()), "Removed powered source retained energy");
        cases++;

        ItemStack equality = MixedBootsFactory.createPair(boots("celestigem", 0), iron).primary();
        ItemStack equalityResult = MixedBootsRecomposer.recompose(equality, boots("eclipsealloy", 500_000), iron);
        require(energy(equalityResult).getAmountAsInt() == 0, "Old owner projection equality resurrected source charge");
        equality.remove(JustDireResourceTestSupport.energyComponent());
        equalityResult = MixedBootsRecomposer.recompose(equality, boots("eclipsealloy", 500_000), iron);
        require(!equalityResult.has(JustDireResourceTestSupport.energyComponent()) && energy(equalityResult).getAmountAsInt() == 0,
                "Explicit absence resurrected source charge");
        ItemStack equalChargedSource = boots("celestigem", 6_000);
        equality = MixedBootsFactory.createPair(equalChargedSource, equalChargedSource).primary();
        equalityResult = MixedBootsRecomposer.recompose(equality, boots("eclipsealloy", 500_000), equalChargedSource);
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
            invalid.set(JustDireResourceTestSupport.energyComponent(), 6_000);
            ItemStack invalidBefore = invalid.copy();
            require(energy(invalid) == null && ItemStack.matches(invalidBefore, invalid),
                    "Invalid/forged root gained FE or changed on lookup: " + invalid);
        }
        for (int invalidAmount : new int[]{-1, 10_001}) {
            ItemStack invalid = root.copy(); invalid.set(JustDireResourceTestSupport.energyComponent(), invalidAmount);
            ItemStack invalidBefore = invalid.copy();
            require(energy(invalid) == null && ItemStack.matches(invalidBefore, invalid), "Invalid charge was usable or silently repaired");
        }
        ItemStack forged = iron.copy(); forged.set(JustDireResourceTestSupport.energyComponent(), 9_999);
        creation(boots("celestigem", 6_000), forged, 10_000, 3_000);
        for (int invalidAmount : new int[]{-1, 10_001}) {
            ItemStack invalidSource = boots("celestigem", invalidAmount);
            ItemStack invalidBefore = invalidSource.copy();
            boolean rejected = false;
            try { MixedBootsFactory.createPair(invalidSource, iron); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected && ItemStack.matches(invalidBefore, invalidSource), "Invalid input charge was accepted/mutated");
        }
        cases++;

        // Storage transfers must refresh the actual production powered contribution too.
        ItemStack nativeAttributes = boots("celestigem", 50);
        require(!nativeAttributes.getAttributeModifiers().modifiers().isEmpty(), "Charged native armor had no attributes");
        nativeAttributes.set(JustDireResourceTestSupport.energyComponent(), 49);
        require(nativeAttributes.getAttributeModifiers().modifiers().isEmpty(), "Native empty-energy attribute threshold changed");
        ItemStack dynamicRoot = MixedBootsFactory.createPair(boots("celestigem", 6_000), iron).primary();
        var captured = dynamicRoot.getAttributeModifiers();
        extract(dynamicRoot, 3_000);
        require(!captured.modifiers().isEmpty() && !dynamicRoot.getAttributeModifiers().equals(captured),
                "Production powered attributes did not respond to depletion");
        cases++;
        workbench(server);
        smithing(server);
        componentRules();
        saveFixture(server);
    }

    private static void workbench(MinecraftServer server) {
        ServerPlayer player = player(server);
        for (boolean reverse : List.of(false, true)) {
            for (int firstSlot : new int[]{2, 3}) {
                player.getInventory().clearContent();
                ItemStack powered = boots("celestigem", 6_000);
                ItemStack iron = new ItemStack(Items.IRON_BOOTS);
                var menu = workbench(player, reverse ? iron : powered, reverse ? powered : iron);
                ItemStack firstPreview = menu.getSlot(firstSlot).getItem().copy();
                ItemStack secondPreview = menu.getSlot(5 - firstSlot).getItem().copy();
                for (int repeat = 0; repeat < 3; repeat++) {
                    menu.slotsChanged(menu.getSlot(0).container);
                    require(ItemStack.matches(firstPreview, menu.getSlot(firstSlot).getItem())
                            && ItemStack.matches(secondPreview, menu.getSlot(5 - firstSlot).getItem()), "Repeated preview changed initial FE");
                }
                require(energy(firstPreview).getAmountAsInt() == 3_000 && energy(secondPreview).getAmountAsInt() == 3_000,
                        "Workbench did not conserve source charge");
                for (int slot = 0; slot < player.getInventory().getNonEquipmentItems().size(); slot++) {
                    player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64));
                }
                require(menu.quickMoveStack(player, firstSlot).isEmpty() && !menu.isCommitted()
                        && ItemStack.matches(powered, menu.getSlot(reverse ? 1 : 0).getItem()), "Blocked take debited source FE");
                player.getInventory().clearContent();
                menu.clicked(firstSlot, 0, ContainerInput.PICKUP, player);
                ItemStack taken = menu.getCarried();
                require(ItemStack.matches(taken, firstPreview) && menu.isCommitted()
                        && menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(), "Workbench take did not commit exactly once");
                ItemStack charger = boots("eclipsealloy", 20_000);
                require(EnergyHandlerUtil.move(energy(charger), energy(taken), 7_000, null) == 7_000
                        && energy(charger).getAmountAsInt() == 13_000
                        && ItemStack.matches(secondPreview, menu.getSlot(5 - firstSlot).getItem()), "Taken root recharge affected the pending inverse/source");
                menu.removed(player);
                var returned = player.getInventory().getNonEquipmentItems().stream().filter(stack -> !stack.isEmpty()).toList();
                require(returned.size() == 2 && returned.stream().allMatch(ModContent::isMixedBoots)
                        && returned.stream().mapToInt(stack -> energy(stack).getAmountAsInt()).sum() == 13_000,
                        "Close duplicated/lost pending output or live charge");
                cases++;
            }
            player.getInventory().clearContent();
            ItemStack powered = boots("celestigem", 6_000);
            var menu = workbench(player, reverse ? new ItemStack(Items.IRON_BOOTS) : powered,
                    reverse ? powered : new ItemStack(Items.IRON_BOOTS));
            menu.removed(player);
            var returned = player.getInventory().getNonEquipmentItems().stream().filter(stack -> !stack.isEmpty()).toList();
            require(returned.size() == 2 && returned.stream().noneMatch(ModContent::isMixedBoots)
                    && returned.stream().filter(stack -> stack.is(powered.getItem())).mapToInt(stack -> energy(stack).getAmountAsInt()).sum() == 6_000,
                    "Uncommitted close duplicated FE through disposable previews");
            cases++;
        }
        for (int invalid : new int[]{-1, 10_001}) {
            ItemStack source = boots("celestigem", invalid);
            var menu = workbench(player, source, new ItemStack(Items.IRON_BOOTS));
            require(menu.getSlot(2).getItem().isEmpty() && menu.getSlot(3).getItem().isEmpty()
                    && ItemStack.matches(source, menu.getSlot(0).getItem()), "Invalid source charge produced a real menu output");
        }
        cases++;
    }

    private static MismatchedWorkbenchMenu workbench(ServerPlayer player, ItemStack left, ItemStack right) {
        var menu = new MismatchedWorkbenchMenu(1, player.getInventory());
        menu.getSlot(0).set(left.copy()); menu.getSlot(1).set(right.copy());
        return menu;
    }

    private static void smithing(MinecraftServer server) {
        ServerPlayer player = player(server);
        for (boolean inverse : List.of(false, true)) {
            for (int charge : new int[]{0, 1_733, 3_000}) {
                var pair = MixedBootsFactory.createPair(boots("celestigem", 6_000), new ItemStack(Items.IRON_BOOTS));
                ItemStack root = inverse ? pair.inverse() : pair.primary();
                extract(root, 3_000 - charge);
                for (boolean tierUpgrade : List.of(false, true)) {
                    player.getInventory().clearContent();
                    var menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.create(server.overworld(), BlockPos.ZERO));
                    menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(tierUpgrade ? nativeItem("template_eclipsealloy") : ItemStack.EMPTY);
                    menu.getSlot(SmithingMenu.BASE_SLOT).set(root.copy());
                    menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(nativeItem(tierUpgrade ? "eclipsealloy_ingot" : "upgrade_stepheight"));
                    ItemStack preview = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
                    require(!preview.isEmpty() && energy(preview).getAmountAsInt() == charge
                            && energy(preview).getCapacityAsInt() == (tierUpgrade ? 500_000 : 10_000), "Native smithing preview restored source FE");
                    menu.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
                    require(ItemStack.matches(preview, menu.getCarried()) && menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty()
                            && menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().isEmpty(), "Actual smithing take lost live FE or did not consume one addition");
                    cases++;
                }
            }
        }
        ItemStack unpowered = MixedBootsFactory.createPair(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS)).primary();
        unpowered.set(JustDireResourceTestSupport.energyComponent(), 9_999);
        ItemStack gained = MixedBootsRecomposer.recompose(unpowered, boots("celestigem", 6_000), new ItemStack(Items.GOLDEN_BOOTS));
        require(energy(gained).getAmountAsInt() == 0 && !gained.has(JustDireResourceTestSupport.energyComponent()), "New storage seeded forged unrelated/source FE");
        cases++;
    }

    private static void componentRules() {
        var defaults = ComponentRulesLifecycle.serverRules();
        var declarations = new java.util.LinkedHashMap<>(defaults.components().rules());
        declarations.put(Identifier.fromNamespaceAndPath("justdirethings", "forge_energy"), ComponentRule.of(ComponentPolicy.SHARE));
        var sharing = new ComponentRules(defaults.durability(), defaults.repairCost(), new ComponentRuleSet(declarations));
        ItemStack left = boots("celestigem", 6_000);
        ItemStack before = left.copy();
        require(!new MixedBootsWorkbenchRecipe().assemblePreview(new MismatchedWorkbenchInput(left, new ItemStack(Items.IRON_BOOTS)),
                new AttributeRuleSet(AttributePolicy.REJECT, java.util.Map.of()), sharing).isComplete()
                && ItemStack.matches(before, left), "Storage finalization bypassed configured composition rejection");
        var result = new MixedBootsWorkbenchRecipe().assemblePreview(new MismatchedWorkbenchInput(left, new ItemStack(Items.IRON_BOOTS)),
                new AttributeRuleSet(AttributePolicy.AVERAGE, java.util.Map.of()), sharing);
        require(result.isComplete() && energy(result.primary()).getAmountAsInt() == 3_000
                && energy(result.inverse()).getAmountAsInt() == 3_000, "Generic FE share rule duplicated managed resources");
        cases++;
    }

    private static void saveFixture(MinecraftServer server) {
        BlockPos pos = new BlockPos(0, 80, 0);
        server.overworld().setBlock(pos, Blocks.BARREL.defaultBlockState(), 3);
        var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(pos);
        require(barrel != null, "Persistence fixture unavailable");
        ItemStack root = MixedBootsFactory.createPair(boots("celestigem", 6_000), new ItemStack(Items.IRON_BOOTS)).primary();
        extract(root, 1_267);
        barrel.setItem(0, root);
        ItemStack frozenAttributes = MixedBootsFactory.createPair(boots("celestigem", 0), new ItemStack(Items.IRON_BOOTS),
                new AttributeRuleSet(AttributePolicy.COMBINE, java.util.Map.of()), ComponentRulesLifecycle.serverRules()).primary();
        frozenAttributes.set(JustDireResourceTestSupport.energyComponent(), 100);
        barrel.setItem(3, frozenAttributes);
        ItemStack zero = root.copy(); extract(zero, 1_733); barrel.setItem(1, zero);
        ItemStack absent = root.copy(); absent.remove(JustDireResourceTestSupport.energyComponent()); barrel.setItem(2, absent);
        barrel.setChanged();
        var json = ItemStack.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), root).getOrThrow();
        NetworkAcceptanceFiles.write("just-dire-resources-saved.json", json.toString());
        cases++;
    }

    private static void verifyRestart(MinecraftServer server) throws java.io.IOException {
        var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(new BlockPos(0, 80, 0));
        require(barrel != null, "Actual restart lost world container");
        ItemStack frozenAttributes = barrel.getItem(3);
        require(frozenAttributes.has(games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireAttributesCompatibility.projections()),
                "Actual world restart lost production attribute metadata");
        var chargedAttributes = frozenAttributes.getAttributeModifiers();
        require(!chargedAttributes.equals(frozenAttributes.get(DataComponents.ATTRIBUTE_MODIFIERS)), "Restart lost live charged projection");
        frozenAttributes.remove(JustDireResourceTestSupport.energyComponent());
        require(frozenAttributes.getAttributeModifiers().equals(frozenAttributes.get(DataComponents.ATTRIBUTE_MODIFIERS)),
                "Restarted root did not return to its frozen depleted baseline");
        frozenAttributes.set(JustDireResourceTestSupport.energyComponent(), 100);
        require(frozenAttributes.getAttributeModifiers().equals(chargedAttributes), "World restart/recharge lost frozen non-default rules");
        var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(
                System.getProperty("mismatchedboots.runtimetest.markers"), "just-dire-resources-saved.json")));
        ItemStack expected = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), json).getOrThrow();
        require(ItemStack.matches(expected, barrel.getItem(0)) && energy(barrel.getItem(0)).getAmountAsInt() == 1_733
                && energy(barrel.getItem(1)).getAmountAsInt() == 0 && energy(barrel.getItem(2)).getAmountAsInt() == 0
                && !barrel.getItem(2).has(JustDireResourceTestSupport.energyComponent()), "World restart restored snapshot charge or lost zero/absence");
        require(extract(barrel.getItem(0), 50) == 50 && energy(barrel.getItem(0)).getAmountAsInt() == 1_683,
                "Restarted root cannot consume live FE");
        NetworkAcceptanceFiles.write("just-dire-resources-restart-report.txt", "actual second server process; persisted barrel contents/live FE/provenance; zero/absence preserved; live extraction after load; frozen non-default attributes through depletion/recharge\n");
    }

    private static ServerPlayer player(MinecraftServer server) {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "resources-menu"),
                ClientInformation.createDefault()) {
            @Override public GameType gameMode() { return GameType.CREATIVE; }
            @Override public boolean hasDisconnected() { return false; }
        };
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        return player;
    }

    private static ItemStack nativeItem(String path) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow().value());
    }

    private static void creation(ItemStack left, ItemStack right, int capacity, int charge) {
        ItemStack leftBefore = left.copy();
        ItemStack rightBefore = right.copy();
        var pair = MixedBootsFactory.createPair(left, right);
        for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
            require(energy(root).getCapacityAsInt() == capacity && energy(root).getAmountAsInt() == charge,
                    "Initial maximum capacity/floor-average charge mismatch");
        }
        require(ItemStack.matches(leftBefore, left) && ItemStack.matches(rightBefore, right), "Creation changed its sources");
        cases++;
    }

    static ItemStack boots(String tier, int charge) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", tier + "_boots"))
                .orElseThrow().value());
        if (tier.equals("celestigem") || tier.equals("eclipsealloy")) { stack.set(JustDireResourceTestSupport.energyComponent(), charge); }
        return stack;
    }

    private static EnergyHandler energy(ItemStack stack) { return JustDireResourceTestSupport.handler(stack); }

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
