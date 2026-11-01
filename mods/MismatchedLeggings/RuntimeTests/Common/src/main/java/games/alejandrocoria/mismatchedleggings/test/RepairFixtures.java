package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;

import java.util.UUID;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class RepairFixtures {
    private RepairFixtures() {
    }

    static ServerPlayer player(MinecraftServer server) {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "leggings-repair"),
                ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        player.setGameMode(GameType.SURVIVAL);
        player.giveExperienceLevels(100);
        return player;
    }

    static ItemStack mixed(boolean inverse) {
        var iron = new ItemStack(Items.IRON_LEGGINGS);
        iron.set(CompositionFixture.CHARGE, 900L);
        var pair = MixedLeggingsFactory.createPair(iron, new ItemStack(Items.GOLDEN_LEGGINGS), CompositionRules.defaults());
        var root = inverse ? pair.inverse() : pair.primary();
        root.setDamageValue(root.getMaxDamage() - 20);
        root.set(DataComponents.CUSTOM_NAME, Component.literal("Live repair target"));
        root.set(DataComponents.REPAIR_COST, 3);
        var opaque = new CompoundTag();
        opaque.putString("repair_fixture", "preserve live state");
        root.set(DataComponents.CUSTOM_DATA, CustomData.of(opaque));
        root.set(CompositionFixture.CHARGE, 123L);
        return root;
    }

    static void preserved(ItemStack original, ItemStack result) {
        require(!result.isEmpty() && result.getCount() == 1 && MixedLeggingsStructure.validData(result).isPresent(),
                "Repair did not retain a structurally valid root");
        require(original.get(LeggingsContent.mixedLeggingsDataType()).equals(result.get(LeggingsContent.mixedLeggingsDataType()))
                        && result.getMaxDamage() == original.getMaxDamage()
                        && result.get(DataComponents.EQUIPPABLE).equals(original.get(DataComponents.EQUIPPABLE))
                        && result.get(DataComponents.ATTRIBUTE_MODIFIERS).equals(original.get(DataComponents.ATTRIBUTE_MODIFIERS))
                        && result.get(DataComponents.CUSTOM_DATA).equals(original.get(DataComponents.CUSTOM_DATA))
                        && result.get(CompositionFixture.CHARGE).equals(123L),
                "Repair changed provenance, composed stats or unrecognized live-root state");
    }
}
