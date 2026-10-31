package games.alejandrocoria.mismatchedleggings.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.LeggingHalf;
import games.alejandrocoria.mismatchedleggings.data.MixedLeggingsData;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Uses world-bound components and the actual registered persistent/network codecs. */
final class SourceDataChecks {
    private SourceDataChecks() {
    }

    static void verify(MinecraftServer server) {
        var data = data();
        snapshots(data);
        codecs(server, data);
        rejection(server, data);
        equipmentProjection(data);
        nativeEquipment(server, data);
    }

    static MixedLeggingsData data() {
        var left = new ItemStack(Items.IRON_LEGGINGS);
        left.set(DataComponents.CUSTOM_NAME, Component.literal("Left provenance"));
        left.setDamageValue(7);
        left.remove(DataComponents.REPAIRABLE);
        return new MixedLeggingsData(new LeggingHalf(left), new LeggingHalf(new ItemStack(Items.GOLDEN_LEGGINGS)));
    }

    private static void snapshots(MixedLeggingsData data) {
        var input = data.left().source();
        var half = new LeggingHalf(input);
        input.setCount(2);
        input.set(DataComponents.CUSTOM_NAME, Component.literal("Changed input"));
        var exposed = half.source();
        exposed.remove(DataComponents.EQUIPPABLE);
        exposed.setDamageValue(99);
        require(half.equals(data.left()), "Source constructor/accessor leaked mutable state");
        var inverse = new MixedLeggingsData(data.right(), data.left());
        require(!data.equals(inverse) && inverse.left().source().is(Items.GOLDEN_LEGGINGS), "Source order was collapsed");
        var sameItem = new MixedLeggingsData(data.left(), new LeggingHalf(new ItemStack(Items.IRON_LEGGINGS)));
        require(!sameItem.left().equals(sameItem.right()), "Same-item sources lost distinct components");
    }

    private static void codecs(MinecraftServer server, MixedLeggingsData data) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var encoded = MixedLeggingsData.CODEC.encodeStart(ops, data).getOrThrow();
        require(MixedLeggingsData.CODEC.parse(ops, encoded).getOrThrow().equals(data), "Persistent source round trip changed provenance");
        var root = root(data);
        var saved = ItemStack.CODEC.encodeStart(ops, root).getOrThrow();
        var loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
        require(ItemStack.matches(root, loaded) && MixedLeggingsStructure.validData(loaded).orElseThrow().equals(data),
                "Registered pair component/root did not survive persistent round trip");
        var buffer = buffer(server);
        try {
            ItemStack.STREAM_CODEC.encode(buffer, root);
            var received = ItemStack.STREAM_CODEC.decode(buffer);
            require(ItemStack.matches(root, received) && MixedLeggingsStructure.validData(received).orElseThrow().equals(data),
                    "Registered pair component/root did not survive network round trip");
        } finally {
            buffer.release();
        }
        require(!loaded.get(LeggingsContent.mixedLeggingsDataType()).left().source().has(DataComponents.REPAIRABLE),
                "Source component removal was lost");
    }

    private static void rejection(MinecraftServer server, MixedLeggingsData data) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var multiple = new ItemStack(Items.IRON_LEGGINGS, 2);
        var invalidComponents = new ItemStack(Items.IRON_LEGGINGS);
        invalidComponents.set(DataComponents.MAX_STACK_SIZE, 2);
        var disguised = new ItemStack(Items.IRON_LEGGINGS);
        disguised.set(LeggingsContent.mixedLeggingsDataType(), data);
        var mixedShell = new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID));
        mixedShell.set(DataComponents.EQUIPPABLE, data.left().source().get(DataComponents.EQUIPPABLE));
        for (var invalid : List.of(ItemStack.EMPTY, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.STONE),
                multiple, invalidComponents, disguised, mixedShell)) {
            require(!LeggingHalf.isValidSource(invalid), "Invalid source admitted: " + invalid);
            expectFailure(() -> new LeggingHalf(invalid), "Source constructor accepted invalid input");
        }
        for (var invalid : List.of(multiple, invalidComponents)) {
            JsonElement encoded = ItemStack.CODEC.encodeStart(ops, invalid).getOrThrow();
            require(LeggingHalf.CODEC.parse(ops, encoded).isError(), "Source codec bypassed count/native validation");
        }
        var missing = new JsonObject();
        missing.addProperty("id", "absent:leggings");
        missing.addProperty("count", 1);
        require(LeggingHalf.CODEC.parse(ops, missing).result().isEmpty(), "Missing source was substituted");

        var nested = MixedLeggingsData.CODEC.encodeStart(ops, data).getOrThrow().getAsJsonObject();
        nested.getAsJsonObject("left").getAsJsonObject("components")
                .add(LeggingsContent.MIXED_LEGGINGS_DATA_ID.toString(), MixedLeggingsData.CODEC.encodeStart(ops, data).getOrThrow());
        require(MixedLeggingsData.CODEC.parse(ops, nested).isError(), "Persistent codec admitted recursive pair data");

        var buffer = buffer(server);
        try {
            // Write raw source stacks, bypassing LeggingHalf's constructor to exercise hostile wire input.
            ItemStack.STREAM_CODEC.encode(buffer, disguised);
            ItemStack.STREAM_CODEC.encode(buffer, data.right().source());
            expectFailure(() -> MixedLeggingsData.STREAM_CODEC.decode(buffer), "Network codec admitted recursive pair data");
        } finally {
            buffer.release();
        }
        codecs(server, data); // A rejected decode must not leave either recursion guard active.
    }

    private static void equipmentProjection(MixedLeggingsData original) {
        var owner = original.left().source();
        var other = original.right().source();
        var nativeEquipment = owner.get(DataComponents.EQUIPPABLE);
        var specialSound = Holder.direct(SoundEvent.createFixedRangeEvent(Identifier.parse("fixture:special_equip"), 9.0F));
        Optional<HolderSet<EntityType<?>>> allowed = Optional.of(HolderSet.direct(EntityType.PLAYER.builtInRegistryHolder()));
        var restricted = new Equippable(EquipmentSlot.LEGS, SoundEvents.ARMOR_EQUIP_IRON, nativeEquipment.assetId(),
                Optional.of(Identifier.parse("fixture:overlay")), allowed, false, false, false, true, true, Holder.direct(SoundEvents.SHEARS_SNIP));
        owner.set(DataComponents.EQUIPPABLE, restricted);
        other.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS).setEquipSound(specialSound).build());
        var data = new MixedLeggingsData(new LeggingHalf(owner), new LeggingHalf(other));
        var equipment = MixedLeggingsStructure.projectEquipment(data);
        require(equipment.slot() == EquipmentSlot.LEGS && equipment.equipSound().equals(specialSound)
                        && equipment.assetId().equals(restricted.assetId()) && equipment.cameraOverlay().isEmpty()
                        && equipment.allowedEntities().equals(allowed) && !equipment.dispensable() && !equipment.swappable()
                        && !equipment.damageOnHurt() && equipment.equipOnInteract() && equipment.canBeSheared()
                        && equipment.shearingSound().equals(restricted.shearingSound()),
                "Equipment projection lost owner settings or lone custom sound");
        require(MixedLeggingsStructure.projectEquipment(new MixedLeggingsData(data.right(), data.left())).equipSound().equals(specialSound),
                "Lone custom sound depends on source orientation");
        owner.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS)
                .setEquipSound(Holder.direct(SoundEvent.createVariableRangeEvent(Identifier.parse("fixture:owner_sound")))).build());
        require(MixedLeggingsStructure.projectEquipment(new MixedLeggingsData(new LeggingHalf(owner), data.right()))
                .equipSound().equals(owner.get(DataComponents.EQUIPPABLE).equipSound()), "Two custom sounds did not retain the owner");
    }

    private static void nativeEquipment(MinecraftServer server, MixedLeggingsData data) {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "leggings-check"),
                ClientInformation.createDefault());
        var valid = root(data);
        require(MixedLeggingsStructure.classify(valid) == MixedLeggingsStructure.State.VALID, "Structural root is invalid");
        require(player.isEquippableInSlot(valid, EquipmentSlot.LEGS) && player.canEquipWithDispenser(valid)
                && player.getEquipmentSlotForItem(valid) == EquipmentSlot.LEGS, "Native equipment rejected a valid root");
        player.setItemInHand(InteractionHand.MAIN_HAND, valid.copy());
        valid.getItem().use(server.overworld(), player, InteractionHand.MAIN_HAND);
        require(ItemStack.matches(player.getItemBySlot(EquipmentSlot.LEGS), valid), "Native use did not equip the valid root");
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);

        var bare = new ItemStack(valid.getItem());
        require(MixedLeggingsStructure.classify(bare) == MixedLeggingsStructure.State.BARE, "Bare classification is wrong");
        var missingData = valid.copy();
        missingData.remove(LeggingsContent.mixedLeggingsDataType());
        var wrongCount = valid.copyWithCount(2);
        var wrongSlot = valid.copy();
        wrongSlot.set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build());
        var missingEquipment = valid.copy();
        missingEquipment.remove(DataComponents.EQUIPPABLE);
        for (var invalid : List.of(bare, missingData, wrongCount, wrongSlot, missingEquipment)) {
            require(MixedLeggingsStructure.validData(invalid).isEmpty(), "Invalid root crossed the consumer boundary");
            require(!player.isEquippableInSlot(invalid, EquipmentSlot.LEGS) && !player.isEquippableInSlot(invalid, EquipmentSlot.FEET)
                    && !player.canEquipWithDispenser(invalid) && player.getEquipmentSlotForItem(invalid) == EquipmentSlot.MAINHAND,
                    "Invalid root can enter a native armor/dispenser path");
            player.setItemInHand(InteractionHand.MAIN_HAND, invalid.copy());
            invalid.getItem().use(server.overworld(), player, InteractionHand.MAIN_HAND);
            require(player.getItemBySlot(EquipmentSlot.LEGS).isEmpty() && player.getItemBySlot(EquipmentSlot.FEET).isEmpty(),
                    "Invalid root equipped through item use");
        }
        var foreign = data.left().source();
        foreign.set(LeggingsContent.mixedLeggingsDataType(), data);
        require(MixedLeggingsStructure.validData(foreign).isEmpty(), "Pair data on a foreign item became a mixed root");
        require(player.isEquippableInSlot(new ItemStack(Items.IRON_LEGGINGS), EquipmentSlot.LEGS)
                && player.canEquipWithDispenser(new ItemStack(Items.IRON_LEGGINGS)), "Guards altered native leggings");
    }

    private static ItemStack root(MixedLeggingsData data) {
        var root = new ItemStack(BuiltInRegistries.ITEM.getValue(LeggingsContent.MIXED_LEGGINGS_ID));
        MixedLeggingsStructure.apply(root, data);
        return root;
    }

    private static RegistryFriendlyByteBuf buffer(MinecraftServer server) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
    }

    static ItemStack clientRoundTrip(RegistryAccess registries) {
        var expected = root(data());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            ItemStack.STREAM_CODEC.encode(buffer, expected);
            var received = ItemStack.STREAM_CODEC.decode(buffer);
            require(ItemStack.matches(expected, received) && MixedLeggingsStructure.validData(received).isPresent(),
                    "Client cannot decode the registered component and structural root");
            return received;
        } finally {
            buffer.release();
        }
    }

    private static void expectFailure(Runnable operation, String message) {
        try {
            operation.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError(message);
    }
}
