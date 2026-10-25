package games.alejandrocoria.mismatchedboots.runtimetest.missingmods;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.UUID;

/** Real production menus and wearer/XP paths; no substitute recovery codec or gameplay implementation. */
public final class MissingModsInteractionChecks {
    private MissingModsInteractionChecks() {}

    public static ItemStack suspend(MinecraftServer server, ItemStack active) {
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var encoded = (CompoundTag) ItemStack.CODEC.encodeStart(ops, active).getOrThrow();
        encoded.getCompoundOrEmpty("components").putInt("future:unavailable", 17);
        var root = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        require(ModContent.suspendedBootsData(root).isPresent(), "Packaged production capture did not suspend");
        return root;
    }

    static void run(MinecraftServer server, ItemStack original) {
        var active = original.copy();
        active.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
        active.setDamageValue(12);
        var root = suspend(server, active);
        var archived = ModContent.suspendedBootsData(root).orElseThrow();
        var player = player(server);
        var access = ContainerLevelAccess.create(server.overworld(), BlockPos.ZERO);
        var anvil = new AnvilMenu(1, player.getInventory(), access);
        anvil.getSlot(AnvilMenu.INPUT_SLOT).set(root.copy());
        anvil.setItemName("renamed suspended boots");
        var renamed = anvil.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        require(!renamed.isEmpty() && renamed.getHoverName().getString().equals("renamed suspended boots"), "Suspended anvil rename rejected");
        var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var reloaded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, renamed).getOrThrow()).getOrThrow();
        require(ModContent.suspendedBootsData(reloaded).orElseThrow().equals(archived), "Native rename changed protected state or created a conflict");
        require(reloaded.getDamageValue() == 12, "Rename changed live damage");
        anvil.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.DIAMOND));
        require(anvil.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty() && anvil.getCost() == 0, "Suspended material repair accepted");
        anvil.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.DIAMOND_BOOTS));
        require(anvil.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(), "Suspended donor repair accepted");
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        book.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 1);
        player.setGameMode(GameType.CREATIVE); // Native anvils otherwise bypass enchantment eligibility in creative.
        anvil.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(book);
        require(anvil.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(), "Suspended book enchant accepted");
        player.setGameMode(GameType.SURVIVAL);
        anvil.getSlot(AnvilMenu.INPUT_SLOT).set(new ItemStack(Items.DIAMOND_BOOTS));
        anvil.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(root.copy());
        require(anvil.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty(), "Suspended donor consumed by ordinary anvil input");

        var smith = new SmithingMenu(2, player.getInventory(), access);
        smith.getSlot(SmithingMenu.BASE_SLOT).set(root.copy());
        smith.getSlot(SmithingMenu.TEMPLATE_SLOT).set(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        smith.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(Items.NETHERITE_INGOT));
        require(smith.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(), "Suspended smithing accepted");
        var grind = new GrindstoneMenu(3, player.getInventory(), access);
        grind.getSlot(0).set(root.copy());
        require(grind.getSlot(2).getItem().isEmpty(), "Suspended grindstone accepted");
        require(!root.isEnchantable() && !root.isValidRepairItem(new ItemStack(Items.DIAMOND)), "Native enchanting/repair entitlement leaked");
        require(!MixedBootsSources.anySource(root, source -> true), "Suspended sources exposed to compatibility predicates");
        var sources = ModContent.getMixedBootsData(active).orElseThrow();
        try {
            MixedBootsRecomposer.recompose(root, sources.left().source(), sources.right().source());
            throw new AssertionError("Suspended recomposition accepted");
        } catch (IllegalArgumentException expected) { /* Gameplay denial, not a failed codec or partial replacement. */ }

        player.setItemSlot(EquipmentSlot.FEET, root);
        player.doTick();
        require(player.getArmorValue() == 0, "Suspended root grants armor");
        require(player.hurtServer(server.overworld(), player.damageSources().generic(), 4)
                && player.getHealth() == 16 && root.getDamageValue() == 12, "Suspended root protects or takes combat wear");
        var orb = new ExperienceOrb(server.overworld(), player.getX(), player.getY(), player.getZ(), 5);
        orb.playerTouch(player);
        require(root.getDamageValue() == 12 && player.totalExperience == 5, "Suspended Mending consumed XP or repaired");
        var tooltip = new ArrayList<net.minecraft.network.chat.Component>();
        root.getItem().appendHoverText(root, Item.TooltipContext.of(server.overworld()), TooltipDisplay.DEFAULT,
                tooltip::add, TooltipFlag.NORMAL);
        require(tooltip.size() == 1 && tooltip.getFirst().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                && text.getKey().endsWith(".suspended"), "Suspended tooltip reused bare/source presentation");
        require(ModContent.suspendedBootsData(root).orElseThrow().equals(archived), "Interaction modified archived state");
        breakDoesNotRefund(server, root);
    }

    static void breakDoesNotRefund(MinecraftServer server, ItemStack root) {
        var player = player(server);
        player.setItemSlot(EquipmentSlot.FEET, root);
        int drops = server.overworld().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4)).size();
        root.hurtAndBreak(root.getMaxDamage(), player, EquipmentSlot.FEET);
        require(root.isEmpty(), "Explicit direct-wear break did not consume suspended root");
        require(player.getInventory().isEmpty()
                && server.overworld().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4)).size() == drops,
                "Archived upgrades were refunded on a direct break");
    }

    private static ServerPlayer player(MinecraftServer server) {
        var player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "recovery-check"), ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                CommonListenerCookie.createInitial(player.getGameProfile(), false));
        player.connection.handleAcceptPlayerLoad(new net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket());
        return player;
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
