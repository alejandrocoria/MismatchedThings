package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
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
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Characterization only: actual native drops/current mixed rejection, no refund adapter or candidate. */
final class NeoForgeJustDireRefundResearch {
    private static final String PREFIX = "just-dire-refunds-research";
    private static final List<String> ABILITIES = List.of("stepheight", "jumpboost", "groundstomp", "negatefalldamage", "earthquake");
    private static final List<String> drops = new ArrayList<>();
    private static final StringBuilder report = new StringBuilder();
    private static ServerPlayer observed;
    private static boolean cancel, finished;
    private static int destroyed, rows;

    private NeoForgeJustDireRefundResearch() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (EntityJoinLevelEvent event) -> {
            if (observed != null && event.getEntity() instanceof ItemEntity item && !event.getLevel().isClientSide()) {
                var id = BuiltInRegistries.ITEM.getKey(item.getItem().getItem());
                if (id.getNamespace().equals("justdirethings") && id.getPath().startsWith("upgrade_")) {
                    require(item.getItem().getCount() == 1, "Native refund changed item count"); drops.add(id.getPath());
                }
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (PlayerDestroyItemEvent event) -> {
            if (event.getEntity() == observed) { destroyed++; }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ArmorHurtEvent event) -> {
            if (event.getEntity() == observed && cancel) { event.setCanceled(true); }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (finished) { return; } finished = true;
            try {
                matrix(event.getServer());
                record("RESULT", rows + " characterization groups; native/current production only, no refund implementation");
                NetworkAcceptanceFiles.write(PREFIX + "-report.txt", report.toString());
                NetworkAcceptanceFiles.write(PREFIX + "-success.txt", "neoforge\n");
            } catch (Throwable failure) { NetworkAcceptanceFiles.failure(PREFIX, failure); }
            finally { observed = null; event.getServer().halt(false); }
        });
    }

    private static void matrix(MinecraftServer server) throws Exception {
        server.setDifficulty(Difficulty.NORMAL, true);
        for (String tier : List.of("ferricore", "blazegold")) {
            var root = nativeBoot(tier); root.setDamageValue(root.getMaxDamage() - 1);
            JustDireAttributeTestSupport.set(root, "tool_enabled", false);
            ABILITIES.forEach(name -> JustDireAttributeTestSupport.set(root, name + "_toggle", false));
            var player = equip(server, root); hit(player);
            require(root.isEmpty() && destroyed == 0, "Combat break unexpectedly emitted destroy-item event");
            refund(tier.equals("ferricore") ? List.of("stepheight", "jumpboost") : List.of("stepheight", "jumpboost", "groundstomp"));
            record("native " + tier + " combat", "actual break; refund despite switches off; destroy events=" + destroyed + "; drops=" + drops);
        }
        var root = nativeBoot("ferricore"); root.setDamageValue(root.getMaxDamage() - 1);
        var player = equip(server, root); cancel = true;
        try { hit(player); } finally { cancel = false; }
        require(!root.isEmpty() && root.getDamageValue() == root.getMaxDamage() - 1 && destroyed == 0, "Cancelled wear changed root");
        refund(List.of("stepheight", "jumpboost"));
        record("native late-cancelled wear", "two upgrade drops without any wear or actual break");

        boolean survived = false;
        server.overworld().getRandom().setSeed(1_234_567);
        for (int seed = 0; seed < 32 && !survived; seed++) {
            root = nativeBoot("ferricore"); root.setDamageValue(root.getMaxDamage() - 1);
            root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 10);
            player = equip(server, root); hit(player);
            refund(List.of("stepheight", "jumpboost"));
            if (!root.isEmpty()) { survived = true; record("native Unbreaking preflight", "attempt=" + seed + "; two upgrade drops but boots survive"); }
        }
        require(survived, "No surviving Unbreaking control found");

        @SuppressWarnings("unchecked") var config = (Map<Object, ModConfigSpec.BooleanValue>) Class.forName("com.direwolf20.justdirethings.setup.Config")
                .getField("AVAILABLE_ABILITY_MAP").get(null);
        var step = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability").getField("STEPHEIGHT").get(null);
        var available = config.get(step); boolean previous = available.get();
        try {
            available.set(false); root = nativeBoot("ferricore"); root.setDamageValue(root.getMaxDamage() - 1);
            player = equip(server, root); hit(player); refund(List.of("jumpboost"));
            record("native config-disabled Step", "installed Step omitted from refund; Jump returned");
        } finally { available.set(previous); }

        root = nativeBoot("ferricore"); root.setDamageValue(root.getMaxDamage() - 1); player = equip(server, root);
        root.hurtAndBreak(2, player, EquipmentSlot.FEET);
        require(root.isEmpty() && destroyed == 0 && drops.isEmpty(), "Direct wear acquired a generic break/refund event");
        record("native direct FEET break", "actual break; no ArmorHurt/destroy-item event, no refund");
        for (int remaining : new int[]{1, 25}) {
            root = nativeBoot("blazegold"); root.setDamageValue(root.getMaxDamage() - remaining); player = equip(server, root);
            EventHooks.onPlayerDestroyItem(player, root.copy(), InteractionHand.MAIN_HAND);
            refund(remaining == 1 ? List.of("stepheight", "jumpboost", "groundstomp") : List.of());
            require(!root.isEmpty() && destroyed == 1, "Synthetic original-stack event destroyed actual root");
            record("native destroy-event original remaining=" + remaining, "synthetic event only; actual native handler; drops=" + drops);
        }
        JustDireGroundStompTestSupport.initializeApi();
        root = nativeBoot("blazegold"); root.setDamageValue(root.getMaxDamage() - 25); player = equip(server, root);
        JustDireGroundStompTestSupport.nativeUse(player, root, new double[0]);
        require(root.isEmpty() && destroyed == 1 && drops.isEmpty(), "Native 25-wear GS break/refund boundary changed");
        record("native Ground Stomp exact 25 durability", "actual ability breaks boots; helper emits destroy event with old remaining=25; no refund");
        root = nativeBoot("celestigem"); root.setDamageValue(root.getMaxDamage() - 1); player = equip(server, root); hit(player);
        require(!root.isEmpty() && root.getDamageValue() == root.getMaxDamage() - 1 && drops.isEmpty(), "Native empty powered boots broke/refunded");
        record("native empty Celestigem", "actual incoming hit; no ordinary wear/break/refund");

        var pair = MixedBootsFactory.createPair(nativeBoot("ferricore"), new ItemStack(Items.DIAMOND_BOOTS));
        for (var mixed : List.of(pair.primary(), pair.inverse())) {
            require(mixed.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("stepheight_upgrade_installed"), false)
                    && mixed.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("jumpboost_upgrade_installed"), false), "Creation did not import union on both roots");
            mixed.setDamageValue(mixed.getMaxDamage() - 1); player = equip(server, mixed); hit(player);
            require(mixed.isEmpty() && destroyed == 0 && drops.isEmpty(), "Current mixed combat unexpectedly refunded");
            record("mixed Ferricore/diamond combat orientation", "actual break; imported upgrades on both outputs; zero refund");
        }
        root = MixedBootsFactory.createPair(nativeBoot("blazegold"), new ItemStack(Items.DIAMOND_BOOTS)).primary();
        root.setDamageValue(root.getMaxDamage() - 25); player = equip(server, root);
        JustDireAttributeTestSupport.set(root, "groundstomp_bindingmode", 2);
        var binding = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                .getConstructor(String.class, int.class, boolean.class, boolean.class).newInstance("groundstomp", 71, false, true);
        JustDireAttributeTestSupport.set(root, "ability_bindings", List.of(binding));
        var reply = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, 71, false));
        require(JustDireActiveNetwork.activate(player, new JustDireActiveNetwork.Activate(1, reply.nonce(), 71, false))
                && root.isEmpty() && destroyed == 0 && drops.isEmpty(), "Current mixed GS break/refund boundary changed");
        record("mixed Ground Stomp exact 25 durability", "production permit/action; actual break; no native destroy event/refund");
        root = MixedBootsFactory.createPair(nativeBoot("ferricore"), new ItemStack(Items.DIAMOND_BOOTS)).primary();
        root.setDamageValue(root.getMaxDamage() - 1); player = equip(server, root);
        EventHooks.onPlayerDestroyItem(player, root.copy(), null);
        require(destroyed == 1 && drops.isEmpty(), "Native handler accepted mixed item identity");
        record("mixed explicit destroy event", "synthetic original at last durability; native ToggleableTool check refuses root");
    }

    private static ItemStack nativeBoot(String tier) {
        var root = NeoForgeJustDireResourcesTest.boots(tier, 0);
        ABILITIES.forEach(name -> JustDireAttributeTestSupport.set(root, name + "_upgrade_installed", true));
        return root;
    }
    private static ServerPlayer equip(MinecraftServer server, ItemStack root) {
        drops.clear(); destroyed = 0;
        observed = JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL);
        observed.setItemSlot(EquipmentSlot.FEET, root); return observed;
    }
    private static void hit(ServerPlayer player) {
        require(player.hurtServer(player.level(), player.damageSources().mobAttack(new Zombie(EntityType.ZOMBIE, player.level())), 8), "Actual incoming hit rejected");
    }
    private static void refund(List<String> expected) {
        require(drops.stream().sorted().toList().equals(expected.stream().map(name -> "upgrade_" + name).sorted().toList()), "Unexpected refund: " + drops + "; expected=" + expected);
    }
    private static void record(String label, String result) { rows++; report.append(label).append(": ").append(result).append('\n'); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
