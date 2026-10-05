package games.alejandrocoria.mismatchedboots.runtimetest.createfly.fabric;

import com.zurrtum.create.AllBlocks;
import com.zurrtum.create.AllItems;
import com.zurrtum.create.AllSynchedDatas;
import com.zurrtum.create.content.equipment.armor.CardboardArmorHandler;
import com.zurrtum.create.content.equipment.armor.DivingBootsItem;
import com.zurrtum.create.content.kinetics.belt.BeltBlock;
import com.zurrtum.create.content.kinetics.belt.BeltBlockEntity;
import com.zurrtum.create.content.kinetics.belt.BeltSlope;
import com.zurrtum.create.content.kinetics.belt.transport.BeltMovementHandler;
import games.alejandrocoria.mismatchedboots.compatibility.createfly.CreateFlyMixinPlugin;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.runtimetest.createfly.fabric.mixin.EntityFluidInteractionInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public final class CreateFlyCompatibilityGameTests {
    private static final String EMPTY_STRUCTURE =
            "mismatchedboots_create_fly_compatibility_test:empty";

    @GameTest(structure = EMPTY_STRUCTURE)
    public void beltExitStaysLocalWithASecondNativeStackPredicate(GameTestHelper helper) {
        var state = AllBlocks.BELT.defaultBlockState().setValue(BeltBlock.SLOPE, BeltSlope.HORIZONTAL);
        BlockPos position = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlockAndUpdate(position, state);
        var belt = (BeltBlockEntity) helper.getLevel().getBlockEntity(position);
        require(belt != null, "Real Belt block entity missing");
        belt.setController(position);
        belt.passengers = new java.util.HashMap<>();
        Player player = GameTestPlayers.makeMockServerPlayer(helper, GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        var pair = MixedBootsFactory.createPair(AllItems.CARDBOARD_BOOTS.getDefaultInstance(),
                Items.IRON_BOOTS.getDefaultInstance());
        for (ItemStack boots : java.util.List.of(AllItems.CARDBOARD_BOOTS.getDefaultInstance(),
                pair.primary(), pair.inverse(),
                MixedBootsFactory.createPair(AllItems.CARDBOARD_BOOTS.getDefaultInstance(),
                        AllItems.CARDBOARD_BOOTS.getDefaultInstance()).primary())) {
            assertBeltExit(helper, player, belt, position, boots, true, false);
        }
        assertBeltExit(helper, player, belt, position, Items.IRON_BOOTS.getDefaultInstance(), false, false);
        assertBeltExit(helper, player, belt, position, pair.primary().copyWithCount(2), false, false);
        ItemStack spoof = Items.IRON_BOOTS.getDefaultInstance();
        spoof.set(ModContent.mixedBootsDataType().orElseThrow(),
                ModContent.getMixedBootsData(pair.primary()).orElseThrow());
        assertBeltExit(helper, player, belt, position, spoof, false, false);
        // Deliberately in FEET: proves the fixture observation is a real item predicate, not a fixed false.
        assertBeltExit(helper, player, belt, position, Items.DIAMOND.getDefaultInstance(), false, true);
        player.setShiftKeyDown(false);
        assertBeltExit(helper, player, belt, position, Items.IRON_BOOTS.getDefaultInstance(), true, false);
        player.setShiftKeyDown(true);
        player.getAbilities().flying = true;
        assertBeltExit(helper, player, belt, position, pair.primary().copy(), false, false);
        player.getAbilities().flying = false;
        var diving = MixedBootsFactory.createPair(AllItems.COPPER_DIVING_BOOTS.getDefaultInstance(),
                Items.IRON_BOOTS.getDefaultInstance());
        assertBeltExit(helper, player, belt, position, diving.primary(), false, false);
        helper.succeed();
    }

    private static void assertBeltExit(GameTestHelper helper, Player player, BeltBlockEntity belt,
                                       BlockPos position, ItemStack boots, boolean transported, boolean diamond) {
        belt.passengers.clear();
        player.setItemSlot(EquipmentSlot.FEET, boots);
        var before = boots.copy();
        BeltExtraPredicateFixture.calls = 0;
        ((BeltBlock) AllBlocks.BELT).entityInside(belt.getBlockState(), helper.getLevel(), position, player, null, true);
        require(BeltExtraPredicateFixture.calls == 1 && BeltExtraPredicateFixture.result == diamond,
                "Production adapter changed or skipped the unrelated ItemStack.is(DIAMOND)");
        require(belt.passengers.containsKey(player) == transported, "Native Belt passenger result changed");
        require(player.getItemBySlot(EquipmentSlot.FEET) == boots && ItemStack.matches(before, boots),
                "Belt compatibility replaced or mutated the equipped root");
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void productionGameplayGuardsAcceptTheExactArtifact(GameTestHelper helper) {
        CreateFlyMixinPlugin plugin = new CreateFlyMixinPlugin();
        require(plugin.shouldApplyMixin(
                        "com.zurrtum.create.content.equipment.armor.CardboardArmorHandler",
                        "games.alejandrocoria.mismatchedboots.mixin.compatibility.createfly."
                                + "CreateFlyCardboardArmorHandlerMixin"),
                "Production Cardboard guard rejected the exact Create Fly artifact");
        require(plugin.shouldApplyMixin(
                        "com.zurrtum.create.content.kinetics.belt.BeltBlock",
                        "games.alejandrocoria.mismatchedboots.mixin.compatibility.createfly."
                                + "CreateFlyBeltBlockMixin"),
                "Production Belt guard rejected the exact Create Fly artifact");
        require(plugin.shouldApplyMixin(
                        "com.zurrtum.create.content.equipment.armor.DivingBootsItem",
                        "games.alejandrocoria.mismatchedboots.mixin.compatibility.createfly."
                                + "CreateFlyDivingBootsItemMixin"),
                "Production Diving Boots guard rejected the exact Create Fly artifact");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void cardboardAdapterRecognizesMixedSources(GameTestHelper helper) {
        Player wearer = GameTestPlayers.makeMockServerPlayer(helper, GameType.SURVIVAL);
        wearer.setPose(Pose.CROUCHING);
        equipCardboardSet(wearer, AllItems.CARDBOARD_BOOTS.getDefaultInstance());

        require(CardboardArmorHandler.testForStealth(wearer),
                "Ordinary full Cardboard armor did not enable stealth");
        require(CardboardArmorHandler.playerHitboxChangesWhenHidingAsBox(wearer) != null,
                "Ordinary Cardboard stealth did not provide the box dimensions");
        wearer.setShiftKeyDown(true);
        require(BeltMovementHandler.canBeTransported(wearer),
                "Ordinary hidden Cardboard player was rejected by Belt movement helper");
        assertTargetCleared(helper, wearer, 0, "ordinary");

        ItemStack cardboardSource = AllItems.CARDBOARD_BOOTS.getDefaultInstance();
        ItemStack ironSource = Items.IRON_BOOTS.getDefaultInstance();
        ItemStack cardboardBefore = cardboardSource.copy();
        ItemStack ironBefore = ironSource.copy();
        MixedBootsPair pair = MixedBootsFactory.createPair(cardboardSource, ironSource);
        assertCardboardCompatibility(helper, wearer, pair.primary(), "primary", 1);
        assertCardboardCompatibility(helper, wearer, pair.inverse(), "inverse", 2);

        require(ItemStack.matches(cardboardBefore, cardboardSource),
                "Cardboard source input changed during compatibility verification");
        require(ItemStack.matches(ironBefore, ironSource),
                "Iron source input changed during compatibility verification");

        equipCardboardSet(wearer, AllItems.CARDBOARD_BOOTS.getDefaultInstance());
        wearer.setItemSlot(EquipmentSlot.HEAD, Items.IRON_HELMET.getDefaultInstance());
        wearer.setShiftKeyDown(true);
        require(!CardboardArmorHandler.testForStealth(wearer),
                "An incomplete Cardboard set enabled stealth");
        require(!BeltMovementHandler.canBeTransported(wearer),
                "An incomplete crouching Cardboard set passed Belt movement helper");
        assertTargetPreserved(helper, wearer);

        equipCardboardSet(wearer, AllItems.CARDBOARD_BOOTS.getDefaultInstance());
        wearer.setPose(Pose.STANDING);
        require(!CardboardArmorHandler.testForStealth(wearer),
                "A standing wearer enabled Cardboard stealth");

        equipCardboardSet(wearer, AllItems.CARDBOARD_BOOTS.getDefaultInstance());
        wearer.setPose(Pose.CROUCHING);
        wearer.getAbilities().flying = true;
        require(!CardboardArmorHandler.testForStealth(wearer),
                "A flying player enabled Cardboard stealth");

        wearer.getAbilities().flying = false;
        MixedBootsPair unrelated = MixedBootsFactory.createPair(
                Items.IRON_BOOTS.getDefaultInstance(), Items.GOLDEN_BOOTS.getDefaultInstance());
        assertNotCardboard(wearer, unrelated.primary(), "unrelated primary");
        assertNotCardboard(wearer, unrelated.inverse(), "unrelated inverse");

        ItemStack invalidCount = pair.primary().copyWithCount(2);
        require(!CardboardArmorHandler.isCardboardArmor(invalidCount),
                "An invalid stacked Mismatched root passed Cardboard recognition");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void divingAdapterReturnsPrioritizedDefensiveSources(GameTestHelper helper) {
        Player player = GameTestPlayers.makeMockServerPlayer(helper, GameType.SURVIVAL);
        assertOrdinaryDivingRecognition(
                player, AllItems.COPPER_DIVING_BOOTS.getDefaultInstance(), "Copper");
        assertOrdinaryDivingRecognition(
                player, AllItems.NETHERITE_DIVING_BOOTS.getDefaultInstance(), "Netherite");

        assertDivingRecognition(player, AllItems.COPPER_DIVING_BOOTS.getDefaultInstance(),
                Items.IRON_BOOTS.getDefaultInstance(), AllItems.COPPER_DIVING_BOOTS,
                "Copper/iron");
        assertDivingRecognition(player, AllItems.NETHERITE_DIVING_BOOTS.getDefaultInstance(),
                Items.IRON_BOOTS.getDefaultInstance(), AllItems.NETHERITE_DIVING_BOOTS,
                "Netherite/iron");
        assertDivingRecognition(player, AllItems.COPPER_DIVING_BOOTS.getDefaultInstance(),
                AllItems.NETHERITE_DIVING_BOOTS.getDefaultInstance(),
                AllItems.NETHERITE_DIVING_BOOTS, "Copper/Netherite");

        MixedBootsPair validPair = MixedBootsFactory.createPair(
                AllItems.COPPER_DIVING_BOOTS.getDefaultInstance(),
                Items.IRON_BOOTS.getDefaultInstance());
        ItemStack invalidCount = validPair.primary().copyWithCount(2);
        assertNotDiving(player, invalidCount, "invalid stacked Mismatched root");

        ItemStack foreignRoot = Items.IRON_BOOTS.getDefaultInstance();
        foreignRoot.set(ModContent.mixedBootsDataType().orElseThrow(), mixedData(validPair.primary()));
        assertNotDiving(player, foreignRoot, "foreign root carrying mixed source data");

        player.setItemSlot(EquipmentSlot.FEET, Items.IRON_BOOTS.getDefaultInstance());
        require(!DivingBootsItem.isWornBy(player),
                "Unrelated iron boots were recognized as Diving Boots");
        require(DivingBootsItem.getWornItem(player).isEmpty(),
                "Unrelated iron boots were returned as worn Diving Boots");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void divingAdapterPreservesWaterMovementAndSyncedState(GameTestHelper helper) {
        Player player = GameTestPlayers.makeMockServerPlayer(helper, GameType.SURVIVAL);
        assertWaterBehavior(helper, player,
                AllItems.COPPER_DIVING_BOOTS.getDefaultInstance(), "ordinary Copper");

        MixedBootsPair copperIron = MixedBootsFactory.createPair(
                AllItems.COPPER_DIVING_BOOTS.getDefaultInstance(),
                Items.IRON_BOOTS.getDefaultInstance());
        assertWaterBehavior(helper, player, copperIron.primary(), "Copper/iron primary");
        assertWaterBehavior(helper, player, copperIron.inverse(), "Copper/iron inverse");

        MixedBootsPair copperNetherite = MixedBootsFactory.createPair(
                AllItems.COPPER_DIVING_BOOTS.getDefaultInstance(),
                AllItems.NETHERITE_DIVING_BOOTS.getDefaultInstance());
        assertWaterBehavior(
                helper, player, copperNetherite.primary(), "Copper/Netherite primary");
        assertWaterBehavior(
                helper, player, copperNetherite.inverse(), "Copper/Netherite inverse");
        helper.succeed();
    }

    @GameTest(structure = EMPTY_STRUCTURE)
    public void divingAdapterPreservesNetheriteLavaDistinction(GameTestHelper helper) {
        Player player = GameTestPlayers.makeMockServerPlayer(helper, GameType.SURVIVAL);
        assertLavaBehavior(
                player, AllItems.COPPER_DIVING_BOOTS.getDefaultInstance(), false,
                "ordinary Copper");
        assertLavaBehavior(
                player, AllItems.NETHERITE_DIVING_BOOTS.getDefaultInstance(), true,
                "ordinary Netherite");

        MixedBootsPair copperIron = MixedBootsFactory.createPair(
                AllItems.COPPER_DIVING_BOOTS.getDefaultInstance(),
                Items.IRON_BOOTS.getDefaultInstance());
        assertLavaBehavior(player, copperIron.primary(), false, "Copper/iron primary");
        assertLavaBehavior(player, copperIron.inverse(), false, "Copper/iron inverse");

        MixedBootsPair netheriteIron = MixedBootsFactory.createPair(
                AllItems.NETHERITE_DIVING_BOOTS.getDefaultInstance(),
                Items.IRON_BOOTS.getDefaultInstance());
        assertLavaBehavior(player, netheriteIron.primary(), true, "Netherite/iron primary");
        assertLavaBehavior(player, netheriteIron.inverse(), true, "Netherite/iron inverse");

        MixedBootsPair copperNetherite = MixedBootsFactory.createPair(
                AllItems.COPPER_DIVING_BOOTS.getDefaultInstance(),
                AllItems.NETHERITE_DIVING_BOOTS.getDefaultInstance());
        assertLavaBehavior(
                player, copperNetherite.primary(), true, "Copper/Netherite primary");
        assertLavaBehavior(
                player, copperNetherite.inverse(), true, "Copper/Netherite inverse");
        helper.succeed();
    }

    private static void assertCardboardCompatibility(
            GameTestHelper helper,
            Player wearer,
            ItemStack mixed,
            String orientation,
            int targetOffset) {
        ItemStack rootBefore = mixed.copy();
        MixedBootsData sourcesBefore = mixedData(mixed);
        equipCardboardSet(wearer, mixed);
        wearer.setPose(Pose.CROUCHING);
        wearer.setShiftKeyDown(true);

        require(CardboardArmorHandler.isCardboardArmor(mixed),
                orientation + " Mismatched Cardboard source was not recognized");
        require(CardboardArmorHandler.testForStealth(wearer),
                orientation + " Mismatched Cardboard source did not enable stealth");
        require(CardboardArmorHandler.playerHitboxChangesWhenHidingAsBox(wearer) != null,
                orientation + " Mismatched Cardboard source did not change dimensions");
        require(wearer.isShiftKeyDown(),
                orientation + " mock player did not retain its crouch input state");
        require(BeltMovementHandler.canBeTransported(wearer),
                orientation + " hidden player was rejected by Belt movement helper");
        assertTargetCleared(helper, wearer, targetOffset, orientation);

        equipOnlyBoots(wearer, mixed);
        wearer.setPose(Pose.CROUCHING);
        wearer.setShiftKeyDown(true);
        require(!CardboardArmorHandler.testForStealth(wearer),
                orientation + " Cardboard source enabled stealth without the complete set");
        require(!BeltMovementHandler.canBeTransported(wearer),
                orientation + " non-hidden crouching player passed Belt movement helper");
        assertUnchanged(mixed, rootBefore, sourcesBefore, orientation + " Cardboard");
    }

    private static void assertNotCardboard(
            LivingEntity wearer, ItemStack mixed, String description) {
        equipCardboardSet(wearer, mixed);
        wearer.setPose(Pose.CROUCHING);
        require(!CardboardArmorHandler.isCardboardArmor(mixed),
                description + " unexpectedly passed Cardboard recognition");
        require(!CardboardArmorHandler.testForStealth(wearer),
                description + " unexpectedly enabled Cardboard stealth");
        require(CardboardArmorHandler.playerHitboxChangesWhenHidingAsBox(wearer) == null,
                description + " unexpectedly changed dimensions");
    }

    private static void assertTargetCleared(
            GameTestHelper helper, Player wearer, int offset, String description) {
        Zombie mob = helper.spawn(EntityType.ZOMBIE, new BlockPos(offset, 0, 0));
        mob.tickCount = 16;
        mob.setTarget(wearer);
        CardboardArmorHandler.mobsMayLoseTargetWhenItIsWearingCardboard(mob);
        require(mob.getTarget() == null,
                description + " Mismatched Cardboard stealth did not clear a mob target");
    }

    private static void assertTargetPreserved(GameTestHelper helper, Player wearer) {
        Zombie mob = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 0, 0));
        mob.tickCount = 16;
        mob.setTarget(wearer);
        CardboardArmorHandler.mobsMayLoseTargetWhenItIsWearingCardboard(mob);
        require(mob.getTarget() == wearer,
                "Incomplete Cardboard armor unexpectedly cleared a mob target");
    }

    private static void assertOrdinaryDivingRecognition(
            Player player, ItemStack boots, String description) {
        player.setItemSlot(EquipmentSlot.FEET, boots);
        require(DivingBootsItem.isWornBy(player),
                "Ordinary " + description + " Diving Boots were not recognized");
        require(ItemStack.matches(boots, DivingBootsItem.getWornItem(player)),
                "Ordinary " + description + " Diving Boots were not returned as worn");
    }

    private static void assertDivingRecognition(
            Player player,
            ItemStack first,
            ItemStack second,
            Item expectedItem,
            String description) {
        ItemStack firstBefore = first.copy();
        ItemStack secondBefore = second.copy();
        MixedBootsPair pair = MixedBootsFactory.createPair(first, second);
        assertDivingRecognition(
                player, pair.primary(), expectedItem, description + " primary");
        assertDivingRecognition(
                player, pair.inverse(), expectedItem, description + " inverse");
        require(ItemStack.matches(firstBefore, first),
                description + " first source input changed during compatibility verification");
        require(ItemStack.matches(secondBefore, second),
                description + " second source input changed during compatibility verification");
    }

    private static void assertDivingRecognition(
            Player player, ItemStack mixed, Item expectedItem, String description) {
        ItemStack rootBefore = mixed.copy();
        MixedBootsData sourcesBefore = mixedData(mixed);
        player.setItemSlot(EquipmentSlot.FEET, mixed);
        require(DivingBootsItem.isWornBy(player),
                description + " did not pass Diving Boots recognition");
        ItemStack returned = DivingBootsItem.getWornItem(player);
        require(returned.getItem() == expectedItem,
                description + " returned the wrong Diving Boots capability");
        require(returned != mixed,
                description + " exposed the equipped Mismatched root as a Diving Boots stack");
        returned.shrink(1);
        require(DivingBootsItem.getWornItem(player).getItem() == expectedItem,
                description + " did not return a fresh defensive source stack");
        assertUnchanged(mixed, rootBefore, sourcesBefore, description);
    }

    private static void assertNotDiving(
            Player player, ItemStack stack, String description) {
        player.setItemSlot(EquipmentSlot.FEET, stack);
        require(!DivingBootsItem.isWornBy(player),
                description + " unexpectedly passed Diving Boots recognition");
        require(DivingBootsItem.getWornItem(player).isEmpty(),
                description + " unexpectedly returned a Diving Boots source");
    }

    private static void assertWaterBehavior(
            GameTestHelper helper, Player player, ItemStack boots, String description) {
        ItemStack rootBefore = boots.copy();
        MixedBootsData sourcesBefore = ModContent.getMixedBootsData(boots).orElse(null);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        player.setPose(Pose.STANDING);
        player.getAbilities().flying = false;
        player.setShiftKeyDown(false);
        player.setJumping(false);
        player.zza = 0.0F;
        player.xxa = 0.0F;
        enterWater(helper, player);

        player.setOnGround(false);
        player.verticalCollision = false;
        player.setDeltaMovement(Vec3.ZERO);
        DivingBootsItem.accelerateDescentUnderwater(player);
        require(heavyBoots(player), description + " did not activate HEAVY_BOOTS");
        assertVector(player.getDeltaMovement(), new Vec3(0.0D, -0.05D, 0.0D),
                description + " descent");

        player.zza = 1.0F;
        player.setDeltaMovement(new Vec3(0.1D, 0.0D, 0.0D));
        DivingBootsItem.accelerateDescentUnderwater(player);
        assertVector(player.getDeltaMovement(), new Vec3(0.13D, -0.05D, 0.0D),
                description + " horizontal movement");

        player.zza = 0.0F;
        player.setJumping(true);
        player.setOnGround(true);
        player.setDeltaMovement(Vec3.ZERO);
        DivingBootsItem.accelerateDescentUnderwater(player);
        assertVector(player.getDeltaMovement(), new Vec3(0.0D, 0.5D, 0.0D),
                description + " grounded jump");
        require(!player.onGround(), description + " grounded jump did not leave the ground");

        player.setJumping(false);
        player.setPose(Pose.SWIMMING);
        Vec3 swimmingMovement = new Vec3(0.2D, 0.3D, 0.4D);
        player.setDeltaMovement(swimmingMovement);
        DivingBootsItem.accelerateDescentUnderwater(player);
        assertVector(player.getDeltaMovement(), swimmingMovement, description + " swimming negative");

        player.setPose(Pose.STANDING);
        player.getAbilities().flying = true;
        Vec3 flyingMovement = new Vec3(0.3D, 0.2D, 0.1D);
        player.setDeltaMovement(flyingMovement);
        DivingBootsItem.accelerateDescentUnderwater(player);
        assertVector(player.getDeltaMovement(), flyingMovement, description + " flying negative");

        player.getAbilities().flying = false;
        leaveWater(helper, player);
        Vec3 dryMovement = new Vec3(0.4D, 0.1D, 0.2D);
        player.setDeltaMovement(dryMovement);
        DivingBootsItem.accelerateDescentUnderwater(player);
        assertVector(player.getDeltaMovement(), dryMovement, description + " dry negative");
        require(heavyBoots(player),
                description + " cleared HEAVY_BOOTS while valid boots remained equipped");

        player.setItemSlot(EquipmentSlot.FEET, Items.IRON_BOOTS.getDefaultInstance());
        DivingBootsItem.accelerateDescentUnderwater(player);
        require(!heavyBoots(player), description + " did not clear HEAVY_BOOTS after removal");
        assertUnchanged(boots, rootBefore, sourcesBefore, description);
    }

    private static void assertLavaBehavior(
            Player player, ItemStack boots, boolean expected, String description) {
        ItemStack rootBefore = boots.copy();
        MixedBootsData sourcesBefore = ModContent.getMixedBootsData(boots).orElse(null);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        player.setJumping(false);
        player.setSprinting(false);
        Vec3 initial = new Vec3(0.2D, -0.2D, 0.3D);
        player.setDeltaMovement(initial);
        DivingBootsItem.onLavaTravel(player, false);
        Vec3 result = expected ? new Vec3(0.35D, -0.42D, 0.525D) : initial;
        assertVector(player.getDeltaMovement(), result, description + " lava movement");
        assertUnchanged(boots, rootBefore, sourcesBefore, description);
    }

    private static void enterWater(GameTestHelper helper, Player player) {
        BlockPos relative = BlockPos.ZERO;
        BlockPos absolute = helper.absolutePos(relative);
        helper.setBlock(relative, Blocks.WATER);
        player.setPos(absolute.getX() + 0.5D, absolute.getY() + 0.05D, absolute.getZ() + 0.5D);
        require(((EntityFluidInteractionInvoker) player).mismatchedboots$updateFluidInteraction(),
                "Water fixture did not detect any fluid");
        require(player.isInWater(), "Water fixture did not mark the player as in water");
    }

    private static void leaveWater(GameTestHelper helper, Player player) {
        helper.setBlock(BlockPos.ZERO, Blocks.AIR);
        ((EntityFluidInteractionInvoker) player).mismatchedboots$updateFluidInteraction();
        require(!player.isInWater(), "Water fixture did not clear the player's water state");
    }

    private static boolean heavyBoots(Player player) {
        return AllSynchedDatas.HEAVY_BOOTS.get(player);
    }

    private static void assertVector(Vec3 actual, Vec3 expected, String description) {
        double tolerance = 1.0E-6D;
        require(Math.abs(actual.x - expected.x) <= tolerance
                        && Math.abs(actual.y - expected.y) <= tolerance
                        && Math.abs(actual.z - expected.z) <= tolerance,
                description + " was " + actual + " instead of " + expected);
    }

    private static void equipCardboardSet(LivingEntity entity, ItemStack feet) {
        entity.setItemSlot(EquipmentSlot.HEAD, AllItems.CARDBOARD_HELMET.getDefaultInstance());
        entity.setItemSlot(EquipmentSlot.CHEST, AllItems.CARDBOARD_CHESTPLATE.getDefaultInstance());
        entity.setItemSlot(EquipmentSlot.LEGS, AllItems.CARDBOARD_LEGGINGS.getDefaultInstance());
        entity.setItemSlot(EquipmentSlot.FEET, feet);
    }

    private static void equipOnlyBoots(LivingEntity entity, ItemStack feet) {
        entity.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        entity.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        entity.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        entity.setItemSlot(EquipmentSlot.FEET, feet);
    }

    private static void assertUnchanged(
            ItemStack stack, ItemStack expectedRoot, MixedBootsData expectedSources,
            String description) {
        require(ItemStack.matches(expectedRoot, stack),
                description + " compatibility check changed root item data");
        if (expectedSources == null) {
            require(ModContent.getMixedBootsData(stack).isEmpty(),
                    description + " compatibility check added stored source snapshots");
        } else {
            require(expectedSources.equals(mixedData(stack)),
                    description + " compatibility check changed stored source snapshots");
        }
    }

    private static MixedBootsData mixedData(ItemStack stack) {
        return ModContent.getMixedBootsData(stack)
                .orElseThrow(() -> new AssertionError("Missing mixed-boots source data"));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
