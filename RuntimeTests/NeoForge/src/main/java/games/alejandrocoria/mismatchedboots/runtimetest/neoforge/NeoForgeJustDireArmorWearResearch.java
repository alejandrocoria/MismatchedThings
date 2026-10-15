package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.util.List;

/** Characterization only: observes real production/native damage, never substitutes a wear adapter. */
final class NeoForgeJustDireArmorWearResearch {
    private static final String PREFIX = "just-dire-armor-wear";
    private static final StringBuilder report = new StringBuilder();
    private static ServerPlayer observed;
    private static int events, rows;
    private static float original, modified;
    private static boolean finished;

    private NeoForgeJustDireArmorWearResearch() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ArmorHurtEvent event) -> {
            if (event.getEntity() == observed) {
                events++; original = event.getOriginalDamage(EquipmentSlot.FEET); modified = event.getNewDamage(EquipmentSlot.FEET);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (finished) { return; }
            finished = true;
            try {
                matrix(event.getServer());
                record("RESULT", rows + " observations; real player hurtServer and hurtAndBreak; no damage replacement/mixin");
                NetworkAcceptanceFiles.write(PREFIX + "-success.txt", "neoforge\n");
            } catch (Throwable failure) { NetworkAcceptanceFiles.failure(PREFIX, failure); }
            finally { event.getServer().halt(false); }
        });
    }

    private static void matrix(MinecraftServer server) throws Exception {
        server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
        for (String tier : List.of("ferricore", "blazegold", "celestigem", "eclipsealloy")) {
            var stack = boot(tier, 0);
            record("native " + tier, "maxDamage=" + stack.getMaxDamage() + ", damageable=" + stack.isDamageableItem()
                    + ", unbreakable=" + stack.has(DataComponents.UNBREAKABLE)
                    + ", lavaCanHurtItem=" + stack.canBeHurtBy(server.overworld().damageSources().lava()));
        }
        for (int charge : new int[]{0, 49, 50, 199, 200, 1_000}) {
            for (String tier : List.of("celestigem", "eclipsealloy")) {
                hit(server, "native " + tier + " FE=" + charge, boot(tier, charge), true, charge, 0, false);
                for (boolean inverse : new boolean[]{false, true}) {
                    hit(server, tier + "/diamond inverse=" + inverse + " FE=" + charge,
                            mixed(tier, "diamond", charge, inverse), false, charge, 0, false);
                }
            }
            for (boolean inverse : new boolean[]{false, true}) {
                hit(server, "celestigem/eclipsealloy inverse=" + inverse + " FE=" + charge,
                        mixed("celestigem", "eclipsealloy", charge, inverse), false, charge, 0, false);
            }
        }
        hit(server, "vanilla diamond", new ItemStack(Items.DIAMOND_BOOTS), false, 0, 0, false);
        hit(server, "non-powered ferricore", boot("ferricore", 0), false, 0, 0, false);
        for (String control : List.of("tool_enabled", "stepheight_toggle")) {
            var root = mixed("celestigem", "diamond", 1_000, false);
            JustDireAttributeTestSupport.set(root, control, false);
            hit(server, "root " + control + " off", root, false, 1_000, 0, false);
        }
        for (int unbreaking : new int[]{1, 3, 10}) {
            hit(server, "native Unbreaking=" + unbreaking, boot("celestigem", 1_000), true, 1_000, unbreaking, false);
            hit(server, "mixed Unbreaking=" + unbreaking, mixed("celestigem", "diamond", 1_000, false), false, 1_000, unbreaking, false);
            int wear = 0;
            for (int i = 0; i < 32; i++) {
                var player = NeoForgeJustDireNegateFallTest.player(server, GameType.SURVIVAL);
                var root = mixed("celestigem", "diamond", 1_000, false); enchant(server, root, unbreaking);
                player.setItemSlot(EquipmentSlot.FEET, root);
                server.overworld().getRandom().setSeed(1_000 + i);
                root.hurtAndBreak(10, player, EquipmentSlot.FEET);
                require(charge(root) == 1_000, "Ordinary Unbreaking unexpectedly paid FE"); wear += root.getDamageValue();
            }
            require(wear > 0 && wear < 320, "Mixed Unbreaking batch did not retain probabilistic ordinary wear");
            record("mixed Unbreaking batch=" + unbreaking, "32 direct 10-durability requests: wear=" + wear + ", FE unchanged");
        }
        for (boolean nativeItem : new boolean[]{false, true}) {
            var stack = nativeItem ? boot("celestigem", 1_000) : mixed("celestigem", "diamond", 1_000, false);
            stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            hit(server, (nativeItem ? "native" : "mixed") + " Unbreakable", stack, nativeItem, 1_000, 0, false);
        }
        for (GameType mode : List.of(GameType.CREATIVE, GameType.SPECTATOR)) {
            for (boolean nativeItem : new boolean[]{false, true}) {
                var player = NeoForgeJustDireNegateFallTest.player(server, mode);
                var root = nativeItem ? boot("celestigem", 1_000) : mixed("celestigem", "diamond", 1_000, false);
                player.setItemSlot(EquipmentSlot.FEET, root); var before = root.copy();
                boolean accepted = player.hurtServer(server.overworld(), attack(player), 8);
                require(!accepted && ItemStack.matches(before, root), "Normal mode immunity changed wear/FE");
                root.hurtAndBreak(10, player, EquipmentSlot.FEET);
                require(charge(root) == (nativeItem ? 990 : 1_000)
                        && root.getDamageValue() == (nativeItem || player.hasInfiniteMaterials() ? 0 : 10), "Direct mode damage boundary changed");
                record(mode + " " + (nativeItem ? "native" : "mixed"), "incoming hit rejected; direct hurtAndBreak(10): FE="
                        + charge(root) + ", wear=" + root.getDamageValue() + ", infiniteMaterials=" + player.hasInfiniteMaterials());
            }
        }
        hit(server, "native celestigem lava", boot("celestigem", 1_000), true, 1_000, 0, true);
        hit(server, "mixed celestigem/diamond lava", mixed("celestigem", "diamond", 1_000, false), false, 1_000, 0, true);
        hit(server, "mixed celestigem/blazegold lava", mixed("celestigem", "blazegold", 1_000, false), false, 1_000, 0, true);
        for (boolean inverse : new boolean[]{false, true}) {
            var root = mixed("celestigem", "eclipsealloy", 1_000, inverse);
            root.setDamageValue(root.getMaxDamage() - 1);
            var player = NeoForgeJustDireNegateFallTest.player(server, GameType.SURVIVAL);
            player.setItemSlot(EquipmentSlot.FEET, root);
            require(player.hurtServer(server.overworld(), attack(player), 8) && root.isEmpty(), "Powered mixed root no longer breaks through ordinary combat wear");
            record("two-powered break inverse=" + inverse, "charged root consumed at final durability despite 1000 FE before the hit");
        }
        // Combat and direct requests must stay distinct; no x100 scaling is invented for a direct call.
        for (boolean nativeItem : new boolean[]{false, true}) {
            var root = nativeItem ? boot("celestigem", 1_000) : mixed("celestigem", "diamond", 1_000, false);
            var player = NeoForgeJustDireNegateFallTest.player(server, GameType.SURVIVAL);
            player.setItemSlot(EquipmentSlot.FEET, root); root.hurtAndBreak(2, player, EquipmentSlot.FEET);
            require(charge(root) == (nativeItem ? 998 : 1_000) && root.getDamageValue() == (nativeItem ? 0 : 2), "Direct request merged with armor-event scaling");
            record("direct " + (nativeItem ? "native" : "mixed"), "hurtAndBreak(2): FE=" + charge(root) + ", wear=" + root.getDamageValue());
            root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
            root.hurtAndBreak(2, player, EquipmentSlot.FEET);
            require(charge(root) == (nativeItem ? 996 : 1_000), "Direct Unbreakable request changed native FE hook boundary");
            record("direct Unbreakable " + (nativeItem ? "native" : "mixed"), "additional hurtAndBreak(2): FE=" + charge(root) + ", wear=" + root.getDamageValue());
        }
        for (boolean inverse : new boolean[]{false, true}) { depletion(server, inverse); }
        for (int charge : new int[]{0, 1_000}) {
            var player = NeoForgeJustDireNegateFallTest.player(server, GameType.SURVIVAL);
            var root = mixed("celestigem", "diamond", charge, false); root.setDamageValue(12);
            root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
            player.setItemSlot(EquipmentSlot.FEET, root);
            var orb = new ExperienceOrb(EntityType.EXPERIENCE_ORB, server.overworld()); orb.setValue(5); orb.playerTouch(player);
            require(root.getDamageValue() < 12 && charge(root) == charge, "Real Mending pickup did not repair only ordinary root wear");
            record("mixed Mending FE=" + charge, "actual 5-XP orb pickup: wear=12->" + root.getDamageValue() + ", FE unchanged");
        }
    }

    private static void depletion(MinecraftServer server, boolean inverse) {
        var player = NeoForgeJustDireNegateFallTest.player(server, GameType.SURVIVAL);
        var root = mixed("celestigem", "diamond", 1_000, inverse); player.setItemSlot(EquipmentSlot.FEET, root);
        player.doTick(); double chargedArmor = player.getAttributeValue(Attributes.ARMOR);
        var handler = JustDireResourceTestSupport.handler(root);
        try (var transaction = Transaction.openRoot()) { require(handler.extract(1_000, transaction) == 1_000, "Depletion unavailable"); transaction.commit(); }
        player.doTick(); double emptyArmor = player.getAttributeValue(Attributes.ARMOR);
        require(chargedArmor > emptyArmor && emptyArmor > 0, "Live depleted root lost normal armor or retained powered armor");
        require(player.hurtServer(server.overworld(), attack(player), 8) && charge(root) == 0 && root.getDamageValue() == 2,
                "Empty live root did not retain ordinary combat wear");
        try (var transaction = Transaction.openRoot()) { require(handler.insert(1_000, transaction) == 1_000, "Recharge unavailable"); transaction.commit(); }
        player.doTick();
        require(player.getAttributeValue(Attributes.ARMOR) == chargedArmor && root.getDamageValue() == 2, "Recharge repaired root or failed to restore armor");
        record("live depletion/recharge inverse=" + inverse, "armor=" + chargedArmor + "->" + emptyArmor + "->" + player.getAttributeValue(Attributes.ARMOR)
                + "; empty hit wore root by 2; real capability recharge preserved wear");
    }

    private static void hit(MinecraftServer server, String name, ItemStack root, boolean poweredNative, int initialCharge, int unbreaking, boolean fire) {
        if (unbreaking > 0) { enchant(server, root, unbreaking); }
        var player = NeoForgeJustDireNegateFallTest.player(server, GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.FEET, root); player.doTick();
        var before = root.copy(); var history = ModContent.getMixedBootsData(root); float health = player.getHealth();
        observed = player; events = 0; original = -1; modified = -1;
        require(player.hurtServer(server.overworld(), fire ? player.damageSources().lava() : attack(player), 8), "Incoming damage rejected: " + name);
        observed = null;
        int bill = (int) Math.max(0, modified - modified * Math.min(1, unbreaking * .1));
        int expectedCharge = poweredNative ? Math.max(0, initialCharge - bill) : initialCharge;
        require(events == 1 && original >= 0 && modified == original * (poweredNative ? 100 : 1), "Event path/scaling changed: " + name);
        if (!fire && !root.has(DataComponents.UNBREAKABLE)) { require(original == 2, "Normal-difficulty 8-damage control did not request two wear points"); }
        require(charge(root) == expectedCharge && history.equals(ModContent.getMixedBootsData(root)), "Combat spent wrong FE or changed provenance: " + name);
        int delta = root.getDamageValue() - before.getDamageValue();
        require(poweredNative || root.has(DataComponents.UNBREAKABLE) ? delta == 0 : unbreaking == 0 ? delta == (int) modified : delta >= 0 && delta <= modified,
                "Wrong native/current root wear: " + name + "; wear=" + delta);
        var allowed = before.copy(); allowed.setDamageValue(root.getDamageValue());
        if (before.has(JustDireResourceTestSupport.energyComponent())) { allowed.set(JustDireResourceTestSupport.energyComponent(), expectedCharge); }
        require(ItemStack.matches(allowed, root), "Combat changed other root state: " + name);
        record(name, "armorEvent=" + original + "->" + modified + ", FE=" + initialCharge + "->" + charge(root)
                + ", wear=" + delta + ", maxDamage=" + root.getMaxDamage() + ", healthLoss=" + (health - player.getHealth()));
    }

    private static ItemStack mixed(String left, String right, int charge, boolean inverse) {
        var pair = MixedBootsFactory.createPair(source(left), source(right));
        var root = inverse ? pair.inverse() : pair.primary();
        root.set(JustDireResourceTestSupport.energyComponent(), charge); return root;
    }
    private static ItemStack source(String tier) { return tier.equals("diamond") ? new ItemStack(Items.DIAMOND_BOOTS) : boot(tier, 0); }
    private static ItemStack boot(String tier, int charge) { return NeoForgeJustDireResourcesTest.boots(tier, charge); }
    private static int charge(ItemStack root) { return root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0); }
    private static void enchant(MinecraftServer server, ItemStack root, int level) {
        root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), level);
    }
    private static DamageSource attack(ServerPlayer player) { return player.damageSources().mobAttack(new Zombie(EntityType.ZOMBIE, player.level())); }
    private static void record(String name, String result) {
        rows++; report.append(name).append(": ").append(result).append('\n'); NetworkAcceptanceFiles.write(PREFIX + "-report.txt", report.toString());
    }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
