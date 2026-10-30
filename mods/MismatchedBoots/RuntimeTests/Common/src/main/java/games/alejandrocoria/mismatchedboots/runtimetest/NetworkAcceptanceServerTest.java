package games.alejandrocoria.mismatchedboots.runtimetest;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentRule;
import games.alejandrocoria.mismatchedboots.component.DurabilityPolicy;
import games.alejandrocoria.mismatchedboots.component.RepairCostPolicy;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;

public final class NetworkAcceptanceServerTest {
    private static final Identifier MOVEMENT_SPEED = Identifier.withDefaultNamespace("movement_speed");
    private static final Identifier DORMANT_ATTRIBUTE = Identifier.fromNamespaceAndPath("futuremod", "dormant");
    private static final Identifier ENCHANTABLE = Identifier.withDefaultNamespace("enchantable");
    private static final Identifier DORMANT_COMPONENT =
            Identifier.fromNamespaceAndPath("futuremod", "dormant_energy");
    private static final int TIMEOUT_TICKS = 3_600;

    private static int ticks;
    private static boolean ready;
    private static boolean playerSeen;
    private static boolean resultDelivered;
    private static boolean stopping;

    private NetworkAcceptanceServerTest() {
    }

    public static void tick(MinecraftServer server) {
        if (stopping) {
            return;
        }

        try {
            ticks++;
            if (!ready) {
                assertSharedWorkbench();
                assertRules();
                assertBehaviorTagProjection();
                assertOptionalModAbsent();
                VanillaArmorClassificationTest.run(server);
                NetworkAcceptanceFiles.write(
                        "server-snapshot.txt", NetworkAcceptanceFiles.snapshot(
                                AttributeRulesLifecycle.serverRules(), ComponentRulesLifecycle.serverRules()));
                NetworkAcceptanceFiles.write("server-ready.txt", loaderName());
                ready = true;
                if (Boolean.getBoolean("mismatchedboots.runtimetest.stopAfterReady")) {
                    succeedAndStop(server);
                    return;
                }
            }

            if (Boolean.getBoolean("mismatchedboots.runtimetest.expectLoaderRejection")) {
                if (!server.getPlayerList().getPlayers().isEmpty()) {
                    throw new AssertionError("Incompatible client entered the server world");
                }
                if (NetworkAcceptanceFiles.exists("client-loader-rejected.txt")) {
                    succeedAndStop(server);
                } else if (ticks > TIMEOUT_TICKS) {
                    throw new AssertionError("Loader rejection client did not report its disconnect reason");
                }
                return;
            }

            if (!resultDelivered && !server.getPlayerList().getPlayers().isEmpty()) {
                playerSeen = true;
                craftAndDeliver(server.getPlayerList().getPlayers().getFirst());
                resultDelivered = true;
            } else if (resultDelivered && playerSeen && server.getPlayerList().getPlayers().isEmpty()) {
                succeedAndStop(server);
            }

            if (ticks > TIMEOUT_TICKS) {
                throw new AssertionError("Network acceptance server timed out");
            }
        } catch (Throwable throwable) {
            NetworkAcceptanceFiles.failure("server", throwable);
            stopping = true;
            server.halt(false);
        }
    }

    private static void assertSharedWorkbench() {
        Identifier current = Identifier.parse("mismatchedthings:mismatched_workbench");
        Identifier legacy = Identifier.parse("mismatchedboots:mismatched_workbench");
        if (!BuiltInRegistries.BLOCK.containsKey(current) || !BuiltInRegistries.ITEM.containsKey(current)
                || !BuiltInRegistries.MENU.containsKey(current) || !BuiltInRegistries.RECIPE_TYPE.containsKey(current)
                || BuiltInRegistries.BLOCK.containsKey(legacy) || BuiltInRegistries.ITEM.containsKey(legacy)
                || BuiltInRegistries.MENU.containsKey(legacy) || BuiltInRegistries.RECIPE_TYPE.containsKey(legacy)) {
            throw new AssertionError("Workbench registration ownership is not exclusively mismatchedthings");
        }
        if (!BuiltInRegistries.RECIPE_SERIALIZER.containsKey(Identifier.parse("mismatchedboots:mixed_boots_workbench"))
                || !BuiltInRegistries.ITEM.containsKey(Identifier.parse("mismatchedboots:mixed_boots"))
                || !BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(Identifier.parse("mismatchedboots:mixed_boots_data"))) {
            throw new AssertionError("Boots-owned content changed identity during the station handoff");
        }
        String origin = MismatchedWorkbench.class.getProtectionDomain().getCodeSource().getLocation().toString();
        if (!origin.contains(".jar") || origin.replace('\\', '/').contains("/mods/MismatchedThings/")) {
            throw new AssertionError("Packaged fixture is using a loose core compile dependency: " + origin);
        }
    }

    private static void assertRules() {
        var rules = AttributeRulesLifecycle.serverRules();
        if (rules.policyFor(MOVEMENT_SPEED) != AttributePolicy.COMBINE
                || rules.policyFor(DORMANT_ATTRIBUTE) != AttributePolicy.MINIMUM) {
            throw new AssertionError("Server did not load the isolated rules: " + rules);
        }

        var components = ComponentRulesLifecycle.serverRules();
        if (components.durability() != DurabilityPolicy.SEPARATE
                || components.repairCost() != RepairCostPolicy.OWNER
                || !ComponentRule.of(ComponentPolicy.OWNER).equals(components.declarations().get(ENCHANTABLE))
                || !ComponentRule.of(ComponentPolicy.SHARE).equals(
                components.declarations().get(DORMANT_COMPONENT))
                || components.components().rules().containsKey(DORMANT_COMPONENT)) {
            throw new AssertionError("Server did not load the isolated component rules: " + components);
        }
    }

    private static void assertBehaviorTagProjection() {
        var qualifying = MixedBootsFactory.createPair(
                new ItemStack(Items.GOLDEN_BOOTS), new ItemStack(Items.IRON_BOOTS));
        if (!qualifying.primary().is(ItemTags.PIGLIN_SAFE_ARMOR)
                || !qualifying.inverse().is(ItemTags.PIGLIN_SAFE_ARMOR)) {
            throw new AssertionError("Loader server did not project piglin safety in both orientations");
        }
        ItemStack ordinary = MixedBootsFactory.createPair(
                new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)).primary();
        if (ordinary.is(ItemTags.PIGLIN_SAFE_ARMOR) || qualifying.primary().is(ItemTags.FOOT_ARMOR)) {
            throw new AssertionError("Loader server projected an unsupported behavior tag");
        }
    }

    private static void assertOptionalModAbsent() {
        String hookClass = "com.autovw.advancednetherite.api.impl.IAdvancedHooks";
        String hookResource = hookClass.replace('.', '/') + ".class";
        if (NetworkAcceptanceServerTest.class.getClassLoader().getResource(hookResource) != null) {
            throw new AssertionError("Advanced Netherite unexpectedly entered the canonical runtime classpath");
        }
        var mixedBoots = BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID);
        if (mixedBoots == null) {
            throw new AssertionError("Mismatched Boots item is not registered");
        }
        if (Arrays.stream(mixedBoots.getClass().getInterfaces())
                  .anyMatch(type -> hookClass.equals(type.getName()))) {
            throw new AssertionError("Optional Advanced Netherite interface was applied while its API was absent");
        }
    }

    private static void craftAndDeliver(ServerPlayer player) {
        WorkbenchMenu menu = new WorkbenchMenu(1, player.getInventory());
        menu.getSlot(WorkbenchMenu.INPUT_START).set(NetworkAcceptanceStacks.firstSource());
        menu.getSlot(WorkbenchMenu.INPUT_START + 1).set(NetworkAcceptanceStacks.secondSource());

        ItemStack crafted = menu.getSlot(WorkbenchMenu.RESULT_START).safeTake(1, 1, player);
        String description = NetworkAcceptanceStacks.describePrimary(crafted);
        ItemStack inverse = menu.getSlot(WorkbenchMenu.RESULT_START + 1).safeTake(1, 1, player);
        NetworkAcceptanceStacks.assertInverse(inverse);
        assertEquipmentRoutes(player, crafted);
        if (menu.slots.subList(WorkbenchMenu.INPUT_START, WorkbenchMenu.RESULT_END)
                      .stream().anyMatch(slot -> !slot.getItem().isEmpty())) {
            throw new AssertionError("Workbench transaction retained inputs or outputs after both were taken");
        }
        if (!player.getInventory().add(crafted.copy())) {
            throw new AssertionError("Could not deliver the crafted stack to the client");
        }
        games.alejandrocoria.mismatchedboots.runtimetest.missingmods.MissingModsWireChecks.deliver(player, crafted);
        player.inventoryMenu.broadcastChanges();
        NetworkAcceptanceFiles.write("server-result.txt", description);
    }

    private static void assertEquipmentRoutes(ServerPlayer player, ItemStack valid) {
        ItemStack bare = new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID));
        ItemStack previousHand = player.getMainHandItem().copy();
        ItemStack previousFeet = player.getItemBySlot(EquipmentSlot.FEET).copy();
        ItemStack previousCarried = player.inventoryMenu.getCarried().copy();
        BlockPos pos = player.blockPosition().offset(4, 1, 0);
        var previousBlock = player.level().getBlockState(pos);
        if (player.level().getBlockEntity(pos) != null) {
            throw new AssertionError("Equipment fixture would replace a block entity");
        }

        ArmorStand target = new ArmorStand(player.level(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
        target.setNoGravity(true);
        player.level().addFreshEntity(target);
        try {
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.MAIN_HAND, bare.copy());
            if (bare.getItem().use(player.level(), player, InteractionHand.MAIN_HAND) != InteractionResult.PASS
                    || !player.getItemBySlot(EquipmentSlot.FEET).isEmpty()) {
                throw new AssertionError("Bare shell equipped through ordinary use");
            }
            player.inventoryMenu.setCarried(bare.copy());
            player.inventoryMenu.clicked(8, 0, ContainerInput.PICKUP, player);
            if (!player.getItemBySlot(EquipmentSlot.FEET).isEmpty()
                    || !ItemStack.matches(player.inventoryMenu.getCarried(), bare)) {
                throw new AssertionError("Bare shell equipped or disappeared through armor-slot placement");
            }
            player.inventoryMenu.setCarried(ItemStack.EMPTY);
            player.inventoryMenu.quickMoveStack(player, 36);
            if (!player.getItemBySlot(EquipmentSlot.FEET).isEmpty()) {
                throw new AssertionError("Bare shell equipped through shift-click");
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, valid.copy());
            valid.getItem().use(player.level(), player, InteractionHand.MAIN_HAND);
            if (!ItemStack.matches(player.getItemBySlot(EquipmentSlot.FEET), valid)) {
                throw new AssertionError("Valid mixed boots lost ordinary-use equipment behavior");
            }
            // Exercise the actual dispenser selection path, not merely the component predicate.
            player.level().setBlockAndUpdate(pos, Blocks.DISPENSER.defaultBlockState()
                                                                  .setValue(DispenserBlock.FACING, Direction.SOUTH));
            var dispenser = (DispenserBlockEntity) player.level().getBlockEntity(pos);
            dispenser.setItem(0, bare.copy());
            player.level().getBlockState(pos).tick(player.level(), pos, player.level().getRandom());
            var ejected = player.level().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3),
                                                            entity -> ItemStack.matches(entity.getItem(), bare));
            if (!target.getItemBySlot(EquipmentSlot.FEET).isEmpty() || !dispenser.isEmpty() || ejected.size() != 1) {
                throw new AssertionError("Dispenser did not eject exactly one unequipped bare shell");
            }
            ejected.forEach(ItemEntity::discard);
            dispenser.setItem(0, valid.copy());
            player.level().getBlockState(pos).tick(player.level(), pos, player.level().getRandom());
            if (!ItemStack.matches(target.getItemBySlot(EquipmentSlot.FEET), valid) || !dispenser.isEmpty()) {
                throw new AssertionError("Dispenser no longer equips valid mixed boots");
            }
            NetworkAcceptanceFiles.write("equipment-success.txt", "bareRejected=true\nvalidEquippable=true\n");
        } finally {
            target.discard();
            player.level().setBlockAndUpdate(pos, previousBlock);
            player.setItemInHand(InteractionHand.MAIN_HAND, previousHand);
            player.setItemSlot(EquipmentSlot.FEET, previousFeet);
            player.inventoryMenu.setCarried(previousCarried);
            // Shift-click can route an unequippable shell into a normal inventory slot.
            player.getInventory().getNonEquipmentItems().replaceAll(stack -> ItemStack.matches(stack, bare)
                                                                             ? ItemStack.EMPTY : stack);
        }
    }

    private static void succeedAndStop(MinecraftServer server) {
        NetworkAcceptanceFiles.write("server-success.txt", loaderName());
        stopping = true;
        server.halt(false);
    }

    private static String loaderName() {
        return System.getProperty("mismatchedboots.runtimetest.loader", "unknown");
    }
}
