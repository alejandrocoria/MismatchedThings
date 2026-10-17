package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireRefundCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireRefundCredits;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Real effects and independent native controls for the production observer, never the spike. */
final class JustDireRefundBreakTest {
    private static final List<String> drops = new ArrayList<>();
    private static ServerPlayer observed;
    private static ItemStack worn, reentrant;
    private static boolean cancel;
    private static Runnable refundCallback;

    private JustDireRefundBreakTest() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (EntityJoinLevelEvent event) -> {
            if (observed == null || !(event.getEntity() instanceof ItemEntity item) || event.getLevel().isClientSide()) { return; }
            var id = BuiltInRegistries.ITEM.getKey(item.getItem().getItem());
            if (!id.getNamespace().equals("justdirethings") || !id.getPath().startsWith("upgrade_")) { return; }
            require(item.getItem().getCount() == 1, "Not a whole single-item refund");
            if (worn.getItem() instanceof MixedBootsItem || worn.isEmpty()) { require(worn.isEmpty(), "Mixed refund preceded actual consumption"); }
            drops.add(id.getPath().substring("upgrade_".length()));
            if (refundCallback != null) { var callback = refundCallback; refundCallback = null; callback.run(); }
            if (reentrant != null) { var root = reentrant; reentrant = null; root.hurtAndBreak(1, observed, EquipmentSlot.FEET); }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ArmorHurtEvent event) -> { if (cancel && event.getEntity() == observed) { event.setCanceled(true); } });
    }

    static List<String> live(MinecraftServer server) throws Exception {
        server.setDifficulty(Difficulty.NORMAL, true);
        var records = new ArrayList<String>();
        var pair = MixedBootsFactory.createPair(boot("ferricore", "stepheight"), boot("ferricore", "jumpboost"));
        direct(server, pair.primary(), "stepheight"); direct(server, pair.inverse(), "jumpboost");
        pair = MixedBootsFactory.createPair(boot("ferricore", "stepheight"), boot("ferricore", "stepheight"));
        direct(server, pair.primary(), "stepheight"); direct(server, pair.inverse(), "stepheight");
        records.add("actual inverse breaks conserve disjoint and identical native purchases; inherited union never becomes a second refund");

        var root = mixed("ferricore", "stepheight", "jumpboost"); last(root); var player = equip(server, root);
        reentrant = root; hit(player); refunds("stepheight", "jumpboost");
        require(root.isEmpty() && reentrant == null, "Combat/drop reentry did not really consume root");
        records.add("actual survival combat consumes before spawning two credited items; drop-event reentry cannot refund again");

        root = mixed("ferricore", "stepheight"); last(root); player = equip(server, root); var before = root.copy(); cancel = true;
        try { hit(player); } finally { cancel = false; }
        require(ItemStack.matches(before, root), "Cancelled wear changed root"); refunds();
        boolean survived = false; server.overworld().getRandom().setSeed(1_234_567);
        for (int attempt = 0; attempt < 32 && !survived; attempt++) {
            root = mixed("ferricore", "stepheight"); last(root);
            root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 10);
            player = equip(server, root); hit(player); survived = !root.isEmpty(); refunds(survived ? new String[0] : new String[]{"stepheight"});
        }
        require(survived, "No actual Unbreaking survivor");
        records.add("cancelled hits and actual Unbreaking survivors return nothing; genuine enchanted breaks still return once");

        for (GameType mode : List.of(GameType.CREATIVE, GameType.SPECTATOR)) {
            root = mixed("ferricore", "stepheight"); last(root); player = equip(server, root); player.setGameMode(mode);
            root.hurtAndBreak(2, player, EquipmentSlot.FEET); refunds();
        }
        root = mixed("ferricore", "stepheight"); last(root); root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        player = equip(server, root); before = root.copy(); root.hurtAndBreak(2, player, EquipmentSlot.FEET);
        require(ItemStack.matches(before, root), "Unbreakable changed"); refunds();
        root = mixed("ferricore", "stepheight"); last(root); player = equip(server, root); player.setGameMode(GameType.ADVENTURE);
        root.hurtAndBreak(2, player, EquipmentSlot.FEET); require(root.isEmpty(), "Adventure break failed"); refunds("stepheight");
        records.add("creative/spectator direct requests never refund; Unbreakable remains immune and adventure actual break returns credited purchase");

        for (String malformed : List.of("absent", "empty", "uninstalled", "data", "detached", "unsupportedSlot", "count", "nonplayer")) {
            root = mixed("ferricore", "stepheight"); last(root); player = equip(server, root);
            switch (malformed) {
                case "absent" -> root.remove(JustDireRefundCompatibility.component());
                case "empty" -> root.set(JustDireRefundCompatibility.component(), new JustDireRefundCredits(List.of()));
                case "uninstalled" -> JustDireAttributeTestSupport.set(root, "stepheight_upgrade_installed", false);
                case "data" -> root.remove(ModContent.mixedBootsDataType().orElseThrow());
                case "detached" -> player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
                case "unsupportedSlot" -> { player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); player.setItemSlot(EquipmentSlot.LEGS, root); }
                case "count" -> root.setCount(2);
                case "nonplayer" -> { var zombie = new Zombie(EntityType.ZOMBIE, player.level()); zombie.setItemSlot(EquipmentSlot.FEET, root); root.hurtAndBreak(2, zombie, EquipmentSlot.FEET); }
            }
            if (!malformed.equals("nonplayer")) { root.hurtAndBreak(2, player, EquipmentSlot.FEET); }
            refunds();
        }
        records.add("absent/empty/inconsistent credits, missing mixed data, detached/unsupported-slot/non-player roots and count-two consumption grant nothing");
        root = mixed("ferricore", "stepheight"); last(root); player = equip(server, root);
        player.containerMenu = new net.minecraft.world.inventory.HopperMenu(99, player.getInventory(), new net.minecraft.world.SimpleContainer(5));
        root.hurtAndBreak(2, player, EquipmentSlot.FEET); refunds("stepheight");
        records.add("ordinary equipped break still refunds with another menu open; action-menu admission is not a refund rule");

        root = mixed("eclipsealloy", "earthquake");
        JustDireAttributeTestSupport.set(root, "tool_enabled", false); JustDireAttributeTestSupport.set(root, "earthquake_toggle", false);
        var clock = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown")
                .getConstructor(String.class, int.class, boolean.class).newInstance("earthquake", 20, false);
        JustDireAttributeTestSupport.set(root, "ability_cooldowns", List.of(clock));
        direct(server, root, "earthquake");
        root = MixedBootsRecomposer.recompose(mixed("ferricore", "jumpboost"), new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.IRON_BOOTS));
        direct(server, root, "jumpboost");
        var flags = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config").getField("AVAILABLE_ABILITY_MAP").get(null);
        var available = (ModConfigSpec.BooleanValue) flags.entrySet().stream().filter(entry -> ((Enum<?>) entry.getKey()).name().equals("STEPHEIGHT")).findFirst().orElseThrow().getValue();
        boolean previous = available.get();
        try { root = mixed("ferricore", "stepheight"); available.set(false); direct(server, root, "stepheight"); }
        finally { available.set(previous); }
        records.add("off tool/ability, zero FE, outstanding cooldown, dormant tier and native config disable do not filter purchased credits");

        root = mixed("blazegold", "groundstomp"); root.setDamageValue(root.getMaxDamage() - 25); player = equip(server, root);
        JustDireAttributeTestSupport.set(root, "groundstomp_bindingmode", 2);
        var binding = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                .getConstructor(String.class, int.class, boolean.class, boolean.class).newInstance("groundstomp", 71, false, true);
        JustDireAttributeTestSupport.set(root, "ability_bindings", List.of(binding));
        var reply = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, 71, false));
        require(JustDireActiveNetwork.activate(player, new JustDireActiveNetwork.Activate(1, reply.nonce(), 71, false)) && root.isEmpty(), "Production GS did not really break at 25 remaining");
        refunds("groundstomp"); records.add("production GS permit/action returns credited item after its actual last-25-durability break");

        for (int charge : new int[]{200, 199}) {
            root = mixed("celestigem", "stepheight"); last(root); root.set(JustDireResourceTestSupport.energyComponent(), charge);
            player = equip(server, root); int damage = root.getDamageValue(); hit(player);
            if (charge == 200) { require(!root.isEmpty() && root.getDamageValue() == damage
                    && root.getOrDefault(JustDireResourceTestSupport.energyComponent(), -1) == 0, "Funded hybrid wear changed"); refunds(); }
            else { require(root.isEmpty(), "Unfunded hybrid did not break"); refunds("stepheight"); }
        }
        root = MixedBootsFactory.createPair(boot("celestigem", "stepheight"), boot("eclipsealloy")).primary(); last(root);
        player = equip(server, root); before = root.copy(); hit(player);
        require(ItemStack.matches(before, root), "Empty two-powered wear protection changed"); refunds();
        records.add("funded one-powered and empty two-powered wear suppress break/refund; unfunded one-powered actual break returns once without inventing FE");

        nativeControls(server); records.add("ordinary vanilla breaks remain unchanged; independent native controls retain predictive cancelled-hit drops, direct-break rejection, GS-25 cutoff and empty powered wear protection");
        observed = null; worn = null;
        return records;
    }

    private static void nativeControls(MinecraftServer server) throws Exception {
        direct(server, new ItemStack(Items.DIAMOND_BOOTS));
        var root = boot("ferricore", "stepheight", "jumpboost"); last(root); var player = equip(server, root); cancel = true;
        try { hit(player); } finally { cancel = false; }
        require(!root.isEmpty(), "Native cancelled control broke"); refunds("stepheight", "jumpboost");
        root = boot("ferricore", "stepheight"); direct(server, root);
        JustDireGroundStompTestSupport.initializeApi(); root = boot("blazegold", "groundstomp"); root.setDamageValue(root.getMaxDamage() - 25);
        player = equip(server, root); JustDireGroundStompTestSupport.nativeUse(player, root, new double[0]);
        require(root.isEmpty(), "Native GS did not consume 25 durability"); refunds();
        root = boot("celestigem", "stepheight"); last(root); player = equip(server, root); var before = root.copy(); hit(player);
        require(ItemStack.matches(before, root), "Native empty powered incoming wear changed"); refunds();
    }

    static void direct(MinecraftServer server, ItemStack root, String... expected) {
        last(root); var player = equip(server, root); root.hurtAndBreak(2, player, EquipmentSlot.FEET);
        require(root.isEmpty(), "Direct break did not consume root"); refunds(expected);
    }
    static void observe(ServerPlayer player, ItemStack root) { drops.clear(); reentrant = null; refundCallback = null; observed = player; worn = root; }
    static void onRefund(Runnable callback) { refundCallback = callback; }
    static void clearObservation() { observed = null; worn = null; refundCallback = null; reentrant = null; drops.clear(); }
    static ServerPlayer equip(MinecraftServer server, ItemStack root) { var player = JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL); observe(player, root); player.setItemSlot(EquipmentSlot.FEET, root); return player; }
    static ItemStack mixed(String tier, String... names) { return MixedBootsFactory.createPair(boot(tier, names), new ItemStack(Items.DIAMOND_BOOTS)).primary(); }
    private static ItemStack boot(String tier, String... names) { var source = NeoForgeJustDireResourcesTest.boots(tier, 0); for (String name : names) { JustDireAttributeTestSupport.set(source, name + "_upgrade_installed", true); } return source; }
    static void last(ItemStack root) { root.setDamageValue(root.getMaxDamage() - 1); }
    private static void hit(ServerPlayer player) { require(player.hurtServer(player.level(), player.damageSources().mobAttack(new Zombie(EntityType.ZOMBIE, player.level())), 8), "Actual survival hit rejected"); }
    static void refunds(String... expected) { require(drops.stream().sorted().toList().equals(List.of(expected).stream().sorted().toList()), "Wrong production refund: " + drops + "; expected=" + List.of(expected)); }
    static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
