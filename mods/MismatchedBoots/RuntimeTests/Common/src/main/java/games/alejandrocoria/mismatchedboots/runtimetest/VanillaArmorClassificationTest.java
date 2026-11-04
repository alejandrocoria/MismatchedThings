package games.alejandrocoria.mismatchedboots.runtimetest;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.PowderSnowBlock;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** Shared production regressions, run by both loader servers without optional mods or a candidate datapack. */
final class VanillaArmorClassificationTest {
    private VanillaArmorClassificationTest() {
    }

    static void run(MinecraftServer server) {
        var player = new ServerPlayer(server, server.overworld(),
                                      new GameProfile(UUID.randomUUID(), "armor-test"), ClientInformation.createDefault());
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player,
                                         CommonListenerCookie.createInitial(player.getGameProfile(), false));
        player.setGameMode(GameType.SURVIVAL);
        player.giveExperienceLevels(100);
        leatherSnowBehavior(player);
        var enchantments = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var unbreaking = enchantments.getOrThrow(Enchantments.UNBREAKING);
        var protection = enchantments.getOrThrow(Enchantments.PROTECTION);
        var thorns = enchantments.getOrThrow(Enchantments.THORNS);
        var feather = enchantments.getOrThrow(Enchantments.FEATHER_FALLING);
        var sharpness = enchantments.getOrThrow(Enchantments.SHARPNESS);
        boolean sourceTableHook = "neoforge".equals(System.getProperty("mismatchedboots.runtimetest.loader"));
        var original = new ItemStack(Items.DIAMOND_BOOTS);
        original.enchant(unbreaking, 3);
        var pair = MixedBootsFactory.createPair(original, new ItemStack(Items.IRON_BOOTS));
        var tool = new ItemStack(Items.DIAMOND_PICKAXE);
        tool.enchant(unbreaking, 3);
        int armorWear = wear(player, original);
        int toolWear = wear(player, tool);
        require(armorWear == 17 && toolWear == 6, "Seeded armor/non-armor Unbreaking controls changed");

        for (var root : List.of(pair.primary(), pair.inverse())) {
            var sources = ModContent.getMixedBootsData(root).orElseThrow();
            require(root.is(ItemTags.ARMOR_ENCHANTABLE) && !root.is(ItemTags.FOOT_ARMOR_ENCHANTABLE)
                            && !root.is(ItemTags.FOOT_ARMOR), "Root armor classification expanded into foot tags");
            require(wear(player, root) == armorWear, "Mixed root does not use armor Unbreaking");
            require(EnchantmentHelper.getItemEnchantmentLevel(unbreaking, root) == 3
                            && sources.equals(ModContent.getMixedBootsData(root).orElseThrow()), "Wear changed enchantments or sources");
            require(protection.value().canEnchant(root) && thorns.value().canEnchant(root)
                            && !feather.value().canEnchant(root), "General/foot-specific enchantment support changed");
            var tableChoices = EnchantmentHelper.getAvailableEnchantmentResults(10, root,
                                                                                Stream.of(protection, thorns, feather));
            require(tableChoices.size() == (sourceTableHook ? 2 : 1)
                            && tableChoices.stream().anyMatch(choice -> choice.enchantment().equals(protection))
                            && tableChoices.stream().noneMatch(choice -> choice.enchantment().equals(thorns))
                            && tableChoices.stream().anyMatch(choice -> choice.enchantment().equals(feather)) == sourceTableHook,
                    "Table candidates lost armor support or changed existing loader-specific source/primary behavior");
            anvil(player, root, protection, true);
        }

        var unusual = MixedBootsFactory.createPair(feet(new ItemStack(Items.PAPER)), feet(new ItemStack(Items.STICK)));
        for (var root : List.of(unusual.primary(), unusual.inverse())) {
            require(protection.value().canEnchant(root), "Root-tag admission control does not actually admit Protection");
            anvil(player, root, protection, false);
        }

        var oneSource = MixedBootsFactory.createPair(feet(new ItemStack(Items.IRON_SWORD)), new ItemStack(Items.IRON_BOOTS));
        for (var root : List.of(oneSource.primary(), oneSource.inverse())) {
            require(!sharpness.value().canEnchant(root), "One-source Sharpness control is already root-native");
            anvil(player, root, sharpness, true);
            anvil(player, root, protection, true);
        }

        var bare = new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID));
        // Give the malformed shell enchantment storage so rejection exercises admission, not vanilla's storage gate.
        bare.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        require(protection.value().canEnchant(bare), "Bare root no longer exercises native tag admission");
        anvil(player, bare, protection, false);
        anvil(player, new ItemStack(Items.DIAMOND_BOOTS), protection, true);
        anvil(player, new ItemStack(Items.IRON_SWORD), protection, false);
        var conflict = pair.primary().copy();
        conflict.enchant(enchantments.getOrThrow(Enchantments.FIRE_PROTECTION), 1);
        anvil(player, conflict, protection, false);
        NetworkAcceptanceFiles.write("vanilla-armor-classification-report.txt",
                                     "both orientations: native/mixed Unbreaking III wear=17; non-armor control=6; sources/enchantments unchanged\n"
                                             + "table candidates retain Protection, not chest-primary Thorns; existing source-hook Feather Falling=" + sourceTableHook + "\n"
                                             + "real survival anvil: two-source/one-source acceptance; source-rejecting, bare and conflicting roots rejected; ordinary decisions preserved\n");
    }

    private static void leatherSnowBehavior(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        require(player.canFreeze(), "Unarmored survival control cannot freeze");
        require(!PowderSnowBlock.canEntityWalkOnPowderSnow(player), "Unarmored control can walk on powder snow");
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.LEATHER_BOOTS));
        require(!player.canFreeze(), "Native leather boots no longer prevent freezing");
        require(PowderSnowBlock.canEntityWalkOnPowderSnow(player), "Native leather boots no longer walk on powder snow");

        var leather = MixedBootsFactory.createPair(new ItemStack(Items.LEATHER_BOOTS), new ItemStack(Items.IRON_BOOTS));
        var allLeather = MixedBootsFactory.createPair(new ItemStack(Items.LEATHER_BOOTS), new ItemStack(Items.LEATHER_BOOTS));
        for (var root : List.of(leather.primary(), leather.inverse(), allLeather.primary(), allLeather.inverse())) {
            var before = root.copy();
            player.setItemSlot(EquipmentSlot.FEET, root);
            require(!player.canFreeze() && root.is(ItemTags.FREEZE_IMMUNE_WEARABLES),
                    "Mixed leather boots did not prevent freezing in both orientations");
            require(PowderSnowBlock.canEntityWalkOnPowderSnow(player),
                    "Mixed leather boots cannot walk on powder snow");
            require(!root.is(ItemTags.FOOT_ARMOR), "Freeze immunity expanded unrelated source classification");
            require(ItemStack.matches(root, before), "Freeze query changed root components or source provenance");
        }

        var ordinary = MixedBootsFactory.createPair(new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.IRON_BOOTS));
        var bare = new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID));
        var malformed = leather.primary().copyWithCount(2);
        var foreign = new ItemStack(Items.STICK);
        foreign.set(ModContent.mixedBootsDataType().orElseThrow(), ModContent.getMixedBootsData(leather.primary()).orElseThrow());
        for (var root : List.of(ordinary.primary(), ordinary.inverse(), bare, malformed, foreign)) {
            player.setItemSlot(EquipmentSlot.FEET, root);
            require(player.canFreeze() && !root.is(ItemTags.FREEZE_IMMUNE_WEARABLES),
                    "No-source, bare, malformed or foreign root inherited freeze immunity");
            require(!PowderSnowBlock.canEntityWalkOnPowderSnow(player),
                    "No-source, bare, malformed or foreign root inherited powder-snow walking");
        }

        // A freeze-immune item adapted to FEET is not necessarily a leather boot.
        var freezeOnly = MixedBootsFactory.createPair(feet(new ItemStack(Items.LEATHER_HELMET)), new ItemStack(Items.IRON_BOOTS));
        player.setItemSlot(EquipmentSlot.FEET, freezeOnly.primary());
        require(!player.canFreeze() && !PowderSnowBlock.canEntityWalkOnPowderSnow(player),
                "Freeze immunity alone granted powder-snow walking");

        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.MAINHAND, leather.primary());
        require(player.canFreeze(), "Unequipped mixed boots still prevent freezing");
        require(!PowderSnowBlock.canEntityWalkOnPowderSnow(player), "Held mixed boots granted powder-snow walking");
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        NetworkAcceptanceFiles.write("vanilla-freezing-report.txt",
                "native leather and both mixed orientations prevent freezing; no-source/bare/malformed/foreign controls freeze\n"
                        + "unequipping removes protection; held boots do not protect; source provenance and unrelated tags unchanged\n"
                        + "one/two leather sources walk on powder snow in both orientations; invalid/held/freeze-only controls do not\n");
    }

    private static int wear(ServerPlayer player, ItemStack stack) {
        player.setItemSlot(EquipmentSlot.FEET, stack);
        player.level().getRandom().setSeed(123456);
        stack.hurtAndBreak(25, player, EquipmentSlot.FEET);
        return stack.getDamageValue();
    }

    private static ItemStack feet(ItemStack source) {
        source.set(DataComponents.MAX_STACK_SIZE, 1);
        source.set(DataComponents.MAX_DAMAGE, 100);
        source.set(DataComponents.DAMAGE, 0);
        source.set(DataComponents.EQUIPPABLE, new ItemStack(Items.DIAMOND_BOOTS).get(DataComponents.EQUIPPABLE));
        return source;
    }

    private static void anvil(ServerPlayer player, ItemStack input, Holder<Enchantment> enchantment, boolean accepted) {
        var menu = new AnvilMenu(1, player.getInventory());
        var before = input.copy();
        var book = new ItemStack(Items.ENCHANTED_BOOK);
        var stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(enchantment, 1);
        book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());
        var bookBefore = book.copy();
        menu.getSlot(AnvilMenu.INPUT_SLOT).set(input);
        menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(book);
        var result = menu.getSlot(AnvilMenu.RESULT_SLOT);
        require(result.hasItem() == accepted, "Unexpected real anvil admission for " + enchantment.unwrapKey());
        require(ItemStack.matches(input, before), "Anvil preview mutated its input");
        int levels = player.experienceLevel;
        var taken = result.safeTake(1, 1, player);
        require(!taken.isEmpty() == accepted, "Anvil preview/extraction admission diverged");
        if (accepted) {
            require(EnchantmentHelper.getItemEnchantmentLevel(enchantment, taken) == 1, "Anvil did not apply book enchantment");
            require(ModContent.getMixedBootsData(before).equals(ModContent.getMixedBootsData(taken)), "Anvil changed sources");
            require(menu.getSlot(AnvilMenu.INPUT_SLOT).getItem().isEmpty()
                            && menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem().isEmpty()
                            && player.experienceLevel < levels, "Anvil did not consume accepted inputs/payment");
        } else {
            require(ItemStack.matches(menu.getSlot(AnvilMenu.INPUT_SLOT).getItem(), before)
                            && ItemStack.matches(menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem(), bookBefore)
                            && player.experienceLevel == levels, "Rejected anvil input was consumed or charged");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
