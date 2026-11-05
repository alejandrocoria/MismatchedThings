package games.alejandrocoria.mismatchedleggings.test;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.content.equipment.armor.CardboardArmorHandler;
import com.zurrtum.create.content.kinetics.belt.BeltBlock;
import com.zurrtum.create.content.kinetics.belt.BeltBlockEntity;
import com.zurrtum.create.content.kinetics.belt.BeltSlope;
import com.zurrtum.create.content.kinetics.belt.transport.BeltMovementHandler;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.SuspendedLeggingsData;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Real native consumers, loaded only by the optional Fabric profile. */
final class CreateFlyChecks {
    private static boolean serverFinished;

    private CreateFlyChecks() {
    }

    static void serverTick(MinecraftServer server) {
        if (!server.isDedicatedServer() || serverFinished || server.getTickCount() < 20) {
            return;
        }
        serverFinished = true;
        try {
            SkeletonChecks.verify();
            var player = RepairFixtures.player(server);
            equippedChecks(player);
            serverConsumers(player);
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

    static void equippedChecks(Player player) {
        var cardboard = AllItems.CARDBOARD_LEGGINGS.getDefaultInstance();
        var iron = new ItemStack(Items.IRON_LEGGINGS);
        var cardboardBefore = cardboard.copy();
        var ironBefore = iron.copy();
        var pair = MixedLeggingsFactory.createPair(cardboard, iron);
        var same = MixedLeggingsFactory.createPair(cardboard, cardboard);
        var roots = List.of(cardboard, pair.primary(), pair.inverse(), same.primary(), same.inverse());
        var originalScale = player.getAttribute(Attributes.SCALE).getBaseValue();
        try {
            for (var root : roots) {
                var before = root.copy();
                var data = MixedLeggingsStructure.validData(root).orElse(null);
                var left = data == null ? ItemStack.EMPTY : data.left().source().copy();
                var right = data == null ? ItemStack.EMPTY : data.right().source().copy();
                equip(player, root, AllItems.CARDBOARD_BOOTS.getDefaultInstance());
                require(CardboardArmorHandler.isCardboardArmor(root), "Cardboard source was not recognized");
                checkStealth(player, true);
                for (double scale : List.of(1.0, 1.5)) {
                    player.getAttribute(Attributes.SCALE).setBaseValue(scale);
                    var dimensions = CardboardArmorHandler.playerHitboxChangesWhenHidingAsBox(player);
                    require(dimensions != null && Math.abs(dimensions.width() - 0.6 * player.getScale()) < 0.0001
                                    && Math.abs(dimensions.height() - 0.8 * player.getScale()) < 0.0001,
                            "Mixed Cardboard lost native scaled box dimensions");
                }
                player.setPose(Pose.STANDING);
                checkStealth(player, false);
                player.setPose(Pose.CROUCHING);
                player.getAbilities().flying = true;
                checkStealth(player, false);
                player.getAbilities().flying = false;
                player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                checkStealth(player, false);
                require(ItemStack.matches(before, root), "Native Cardboard consumers mutated the mixed root");
                require(data == null || ItemStack.matches(left, data.left().source())
                                && ItemStack.matches(right, data.right().source()),
                        "Native Cardboard consumers mutated source provenance");
            }

            var malformed = pair.primary().copy();
            malformed.remove(DataComponents.EQUIPPABLE);
            var suspended = pair.primary().copy();
            suspended.set(LeggingsContent.createSuspendedDataType(),
                    SuspendedLeggingsData.of(new CompoundTag(), new CompoundTag(), new CompoundTag()));
            var spoof = new ItemStack(Items.IRON_LEGGINGS);
            spoof.set(LeggingsContent.mixedLeggingsDataType(), MixedLeggingsStructure.validData(pair.primary()).orElseThrow());
            var unrelated = MixedLeggingsFactory.createPair(iron, new ItemStack(Items.GOLDEN_LEGGINGS));
            for (var invalid : List.of(new ItemStack(pair.primary().getItem()), pair.primary().copyWithCount(2),
                    malformed, suspended, spoof, iron, unrelated.primary(), unrelated.inverse())) {
                require(!CardboardArmorHandler.isCardboardArmor(invalid), "Invalid or unrelated root passed Cardboard recognition");
                equip(player, invalid, AllItems.CARDBOARD_BOOTS.getDefaultInstance());
                checkStealth(player, false);
            }

            equip(player, ItemStack.EMPTY, AllItems.CARDBOARD_BOOTS.getDefaultInstance());
            player.getInventory().add(pair.primary().copy());
            checkStealth(player, false);
            player.getInventory().clearContent();
            player.setItemSlot(EquipmentSlot.MAINHAND, pair.primary());
            checkStealth(player, false);

            for (var boots : bootsControls()) {
                // Check Boots alone first, then both optional RETURN fallbacks in the same set.
                for (var leggings : List.of(cardboard, pair.primary(), pair.inverse())) {
                    equip(player, leggings, boots);
                    checkStealth(player, true);
                }
            }
            require(ItemStack.matches(cardboardBefore, cardboard) && ItemStack.matches(ironBefore, iron),
                    "Composition/recognition changed original inputs");
        } finally {
            player.getAttribute(Attributes.SCALE).setBaseValue(originalScale);
            player.getAbilities().flying = false;
            player.setShiftKeyDown(false);
            player.setPose(Pose.STANDING);
            clear(player);
        }
    }

    private static void checkStealth(Player player, boolean expected) {
        require(CardboardArmorHandler.testForStealth(player) == expected, "Wrong native full-set recognition");
        require((CardboardArmorHandler.playerHitboxChangesWhenHidingAsBox(player) != null) == expected,
                "Box dimensions disagreed with native stealth");
        require(BeltMovementHandler.canBeTransported(player) == expected,
                "Native crouching Belt movement consumer disagreed with stealth");
    }

    private static void serverConsumers(ServerPlayer player) {
        var server = player.level().getServer();
        var pair = MixedLeggingsFactory.createPair(AllItems.CARDBOARD_LEGGINGS.getDefaultInstance(),
                new ItemStack(Items.IRON_LEGGINGS));
        var position = new BlockPos(4, 64, 4);
        var level = player.level();
        var oldBlock = level.getBlockState(position);
        try {
            var state = AllBlocks.BELT.defaultBlockState().setValue(BeltBlock.SLOPE, BeltSlope.HORIZONTAL);
            level.setBlockAndUpdate(position, state);
            var belt = (BeltBlockEntity) level.getBlockEntity(position);
            require(belt != null, "Real native Belt block entity missing");
            belt.setController(position);
            belt.passengers = new HashMap<>();

            for (var boots : bootsControls()) {
                for (var leggings : List.of(AllItems.CARDBOARD_LEGGINGS.getDefaultInstance(), pair.primary(), pair.inverse())) {
                    equip(player, leggings, boots);
                    var before = leggings.copy();
                    var advancement = server.getAdvancements().get(Identifier.parse("create:cardboard_armor"));
                    require(advancement != null, "Native Full Stealth advancement missing");
                    for (var criterion : advancement.value().criteria().keySet()) {
                        player.getAdvancements().revoke(advancement, criterion);
                    }
                    require(!player.getAdvancements().getOrStartProgress(advancement).isDone(), "Advancement control was already done");
                    checkStealth(player, true);
                    require(player.getAdvancements().getOrStartProgress(advancement).isDone(), "Native Full Stealth trigger did not fire");
                    mobTarget(player, true);
                    beltPassenger(player, belt, position, true);
                    player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                    mobTarget(player, false);
                    // The feet gate admits Cardboard; full-set transport remains a separate native consumer.
                    beltPassenger(player, belt, position, true);
                    require(!BeltMovementHandler.canBeTransported(player), "Incomplete set passed the native movement helper");
                    player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
                    beltPassenger(player, belt, position, false);
                    player.setShiftKeyDown(false);
                    beltPassenger(player, belt, position, true);
                    require(ItemStack.matches(before, leggings), "Belt/mob consumers mutated leggings");
                }
            }
        } finally {
            level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(position, oldBlock);
            clear(player);
        }
    }

    private static void mobTarget(ServerPlayer wearer, boolean cleared) {
        var mob = new Zombie(EntityType.ZOMBIE, wearer.level());
        mob.tickCount = 16;
        mob.setTarget(wearer);
        mob.setLastHurtByMob(wearer);
        CardboardArmorHandler.mobsMayLoseTargetWhenItIsWearingCardboard(mob);
        require((mob.getTarget() == null) == cleared && (mob.getLastHurtByMob() == null) == cleared,
                "Native mob target/attacker consumer disagreed with full-set stealth");
        mob.discard();
    }

    private static void beltPassenger(Player player, BeltBlockEntity belt, BlockPos position, boolean expected) {
        belt.passengers.clear();
        ((BeltBlock) AllBlocks.BELT).entityInside(belt.getBlockState(), player.level(), position, player, null, true);
        require(belt.passengers.containsKey(player) == expected, "Wrong real native Belt passenger gate");
    }

    private static List<ItemStack> bootsControls() {
        var boots = new ArrayList<ItemStack>();
        boots.add(AllItems.CARDBOARD_BOOTS.getDefaultInstance());
        if (Boolean.getBoolean("leggings.test.boots")) {
            boots.addAll(BootsCoexistence.pair(AllItems.CARDBOARD_BOOTS.getDefaultInstance(), new ItemStack(Items.IRON_BOOTS)));
        }
        return boots;
    }

    private static void equip(Player player, ItemStack leggings, ItemStack boots) {
        clear(player);
        player.setItemSlot(EquipmentSlot.HEAD, AllItems.CARDBOARD_HELMET.getDefaultInstance());
        player.setItemSlot(EquipmentSlot.CHEST, AllItems.CARDBOARD_CHESTPLATE.getDefaultInstance());
        player.setItemSlot(EquipmentSlot.LEGS, leggings);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        player.setPose(Pose.CROUCHING);
        player.setShiftKeyDown(true);
    }

    private static void clear(Player player) {
        for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
            player.setItemSlot(slot, ItemStack.EMPTY);
        }
        player.getInventory().clearContent();
    }
}
