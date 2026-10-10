package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.interaction.MixedBootsAnvilPolicy;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;

/** Baseline/candidate datapack audit of vanilla armor classification, not a production tag fix. */
final class NeoForgeVanillaArmorTagProbe {
    private NeoForgeVanillaArmorTagProbe() {}

    static void run(MinecraftServer server) {
        boolean candidate = Boolean.getBoolean("mismatchedboots.runtimetest.armorTagCandidate");
        var player = NeoForgeJustDireGroundStompSpike.player(server, GameType.SURVIVAL);
        var original = new ItemStack(Items.DIAMOND_BOOTS);
        var pair = MixedBootsFactory.createPair(original, new ItemStack(Items.IRON_BOOTS));
        var unbreaking = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
        var protection = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION).value();
        var thorns = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.THORNS).value();
        var feather = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FEATHER_FALLING).value();
        var text = new StringBuilder(candidate ? "candidate datapack\n" : "unchanged baseline\n");
        original.enchant(unbreaking, 3);
        player.setItemSlot(EquipmentSlot.FEET, original);
        server.overworld().getRandom().setSeed(123456);
        original.hurtAndBreak(25, player, EquipmentSlot.FEET);
        for (var root : java.util.List.of(pair.primary(), pair.inverse())) {
            require(root.is(ItemTags.ARMOR_ENCHANTABLE) == candidate && !root.is(ItemTags.FOOT_ARMOR_ENCHANTABLE), "Unexpected candidate tag scope");
            require(protection.canEnchant(root) == candidate && protection.isPrimaryItem(root) == candidate,
                    "General armor book/table eligibility does not follow actual membership");
            require(thorns.canEnchant(root) == candidate && !thorns.isPrimaryItem(root) && !feather.canEnchant(root),
                    "Candidate expanded chest/foot-specific eligibility");
            root.enchant(unbreaking, 3);
            player.setItemSlot(EquipmentSlot.FEET, root);
            server.overworld().getRandom().setSeed(123456);
            root.hurtAndBreak(25, player, EquipmentSlot.FEET);
            require(candidate ? root.getDamageValue() == original.getDamageValue() : root.getDamageValue() < original.getDamageValue(), "Actual root Unbreaking branch unchanged");
            require(root.getDamageValue() == (candidate ? 17 : 6), "Seeded durability characterization changed");
            text.append("both orientations: native armor wear=17; root wear=").append(root.getDamageValue())
                    .append("; Protection book/primary=").append(candidate)
                    .append("; Thorns book=").append(candidate).append(" but not primary; foot-specific Feather Falling remains false\n");
        }
        // Component-defined FEET items are supported inputs, but these source item identities reject Protection.
        var paper = componentFeet(new ItemStack(Items.PAPER));
        var stick = componentFeet(new ItemStack(Items.STICK));
        var custom = MixedBootsFactory.createPair(paper, stick).primary();
        require(!protection.canEnchant(paper) && !protection.canEnchant(stick), "Source rejection control does not reject");
        require(protection.canEnchant(custom) == candidate
                && MixedBootsAnvilPolicy.canApplyEnchantment(protection, custom, protection.canEnchant(custom)) == candidate,
                "Production anvil shortcut behavior changed; review source eligibility seam");
        text.append("valid component-defined FEET sources both reject Protection; production root/anvil admission=")
                .append(candidate).append("; candidate must preserve source-based book admission before production\n");
        NetworkAcceptanceFiles.write("vanilla-armor-tag-probe-report.txt", text.toString());
        NetworkAcceptanceFiles.write("vanilla-armor-tag-probe-success.txt", "neoforge\n");
    }

    private static ItemStack componentFeet(ItemStack source) {
        source.set(DataComponents.MAX_STACK_SIZE, 1);
        source.set(DataComponents.MAX_DAMAGE, 100);
        source.set(DataComponents.DAMAGE, 0);
        source.set(DataComponents.EQUIPPABLE, new ItemStack(Items.DIAMOND_BOOTS).get(DataComponents.EQUIPPABLE));
        return source;
    }

    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
