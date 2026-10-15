package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireEarthquakeCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireLavaRepairCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

/** Normal packaged production wear/FE acceptance; no candidate consumer or permission property. */
public final class NeoForgeJustDireArmorWearTest {
    private static final String PREFIX = "just-dire-armor-wear";
    private static final StringBuilder report = new StringBuilder();
    private static ServerPlayer observed;
    private static String mutation = "none";
    private static boolean finished;
    private static int rows;

    public static void ordinary(ItemStack root, int amount, LivingEntity entity, EquipmentSlot slot) { root.hurtAndBreak(amount, entity, slot); }

    static void registerAbilityWitness() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, (ServerTickEvent.Post event) -> {
            if (finished) { return; } finished = true;
            try {
                event.getServer().setDifficulty(Difficulty.NORMAL, true);
                hit(event.getServer(), "active wear in EQ/GS process", mixed("celestigem", "diamond", 1_000, false), 800, 0);
                NetworkAcceptanceFiles.write(PREFIX + "-armed.txt", report.toString());
            } catch (Throwable failure) { NetworkAcceptanceFiles.failure(PREFIX, failure); event.getServer().halt(false); }
        });
    }

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ArmorHurtEvent event) -> {
            if (event.getEntity() != observed) { return; }
            if (mutation.equals("cancel")) { event.setCanceled(true); }
            else if (!mutation.equals("none")) { event.setNewDamage(EquipmentSlot.FEET, Float.parseFloat(mutation)); }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (finished) { return; } finished = true;
            try {
                matrix(event.getServer());
                record("RESULT", rows + " production observations; actual packaged hook, wallet, ordinary controls and ability payments");
                NetworkAcceptanceFiles.write(PREFIX + "-success.txt", "neoforge\n");
            } catch (Throwable failure) { NetworkAcceptanceFiles.failure(PREFIX, failure); }
            finally { observed = null; event.getServer().halt(false); }
        });
    }

    private static void matrix(MinecraftServer server) throws Exception {
        server.setDifficulty(Difficulty.NORMAL, true);
        // Force the actual CommonHooks transformation before reading its published local guard.
        CommonHooks.onArmorHurt(server.overworld().damageSources().generic(), new EquipmentSlot[0], 2, player(server));
        String variant = System.getProperty("mismatchedboots.runtimetest.justDireArmorWearVariant", "live");
        boolean ready;
        try {
            var getter = CommonHooks.class.getDeclaredMethod("mismatchedboots$justDireArmorWearReady"); getter.setAccessible(true);
            ready = (boolean) getter.invoke(null);
        } catch (NoSuchMethodException absent) { ready = false; }
        boolean active = List.of("live", "wrapperBefore", "wrapperAfter", "transactions", "queryFailure", "storageMissing").contains(variant);
        require(ready == active, "Wrong final production permission: " + variant);
        if (variant.equals("absent")) {
            var pair = MixedBootsFactory.createPair(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
            var root = pair.primary(); var player = player(server); player.setItemSlot(EquipmentSlot.FEET, root);
            require(player.hurtServer(server.overworld(), attack(player), 8) && root.getDamageValue() == 2, "Absent JDT changed ordinary wear");
            record("absent optional JDT", "ordinary wear=2; no native component lookup"); return;
        }
        if (variant.equals("transactions")) { transactions(server); return; }
        if (variant.equals("queryFailure")) { JustDireArmorWearFaults.fault = "query"; }
        if (!variant.equals("live")) {
            boolean wrappers = variant.startsWith("wrapper");
            int hooks = JustDireArmorWearFaults.before;
            for (boolean inverse : new boolean[]{false, true}) {
                for (String partner : List.of("diamond", "eclipsealloy")) {
                    hit(server, variant + "/" + partner + " inverse=" + inverse, mixed("celestigem", partner, 1_000, inverse), wrappers ? 800 : 1_000, wrappers ? 0 : 2);
                }
            }
            if (wrappers) {
                int ordinary = JustDireArmorWearFaults.before;
                hit(server, "foreign wrapper ordinary control", new ItemStack(Items.DIAMOND_BOOTS), 0, 2);
                hit(server, "foreign wrapper native control", boot("celestigem", 1_000), 800, 0);
                require(JustDireArmorWearFaults.before == ordinary + 2 && JustDireArmorWearFaults.before == JustDireArmorWearFaults.after
                        && JustDireArmorWearFaults.surrounding >= 6 && JustDireArmorWearFaults.before >= hooks + 2, "Competing wrappers were not actually exercised");
                record("real forwarding wrapper", "before=" + JustDireArmorWearFaults.before + "; after=" + JustDireArmorWearFaults.after
                        + "; surrounding=" + JustDireArmorWearFaults.surrounding + "; funded bills paid once; ordinary/native hooks executed");
                postEvent(server);
            }
            if (!variant.equals("storageMissing")) {
                var wallet = mixed("celestigem", "diamond", 1_000, false);
                require(JustDireResourceTestSupport.handler(wallet).getAmountAsInt() == 1_000, "Wear refusal disabled safe storage");
                abilities(server);
                var live = player(server); live.setItemSlot(EquipmentSlot.FEET, wallet); live.doTick();
                double chargedArmor = live.getAttributeValue(Attributes.ARMOR);
                wallet.set(JustDireResourceTestSupport.energyComponent(), 0); live.doTick();
                require(chargedArmor > live.getAttributeValue(Attributes.ARMOR), "Wear refusal disabled live attributes");
                JustDireEarthquakeTestSupport.bind();
                var earthquake = JustDireEarthquakeTestSupport.mixed(50_000, false, false);
                require(JustDireEarthquakeCompatibility.quote(earthquake) != null
                        && JustDireSettingsCompatibility.metadata(earthquake, "earthquake") != null,
                        "Wear refusal disabled independent Earthquake/settings eligibility");
            } else {
                require(JustDireResourceTestSupport.handler(mixed("celestigem", "diamond", 1_000, false)) == null, "Storage fault did not affect the actual binding");
                var passive = mixed("ferricore", "diamond", 0, false); JustDireAttributeTestSupport.set(passive, "jumpboost_upgrade_installed", true);
                var player = player(server); player.setItemSlot(EquipmentSlot.FEET, passive); player.jumpFromGround();
                require(player.getDeltaMovement().y > .5, "Storage failure disabled independent Jump");
            }
            var lava = mixed("blazegold", "diamond", 0, false);
            require(JustDireLavaRepairCompatibility.eligible(lava)
                    && !lava.canBeHurtBy(player(server).damageSources().lava()), "Wear refusal disabled intrinsic Lava protection");
            record("independent adapters", "storage/attributes/ability controls retained where their own API is available; Lava protection retained");
            return;
        }
        for (String tier : List.of("celestigem", "eclipsealloy")) {
            for (int charge : new int[]{0, 49, 199, 200, 1_000}) {
                hit(server, "native " + tier + " FE=" + charge, boot(tier, charge), Math.max(0, charge - 200), 0);
            }
            for (boolean inverse : new boolean[]{false, true}) {
                for (int charge : new int[]{0, 49, 199, 200, 1_000}) {
                    hit(server, tier + "/diamond inverse=" + inverse + " FE=" + charge,
                            mixed(tier, "diamond", charge, inverse), charge >= 200 ? charge - 200 : charge, charge >= 200 ? 0 : 2);
                }
            }
        }
        for (boolean inverse : new boolean[]{false, true}) {
            for (int charge : new int[]{0, 49, 199, 200, 1_000}) {
                hit(server, "two powered inverse=" + inverse + " FE=" + charge, mixed("celestigem", "eclipsealloy", charge, inverse), Math.max(0, charge - 200), 0);
            }
        }
        hit(server, "ordinary diamond", new ItemStack(Items.DIAMOND_BOOTS), 0, 2);
        hit(server, "ordinary non-powered mixed", mixed("ferricore", "diamond", 0, false), 0, 2);
        var forged = mixed("ferricore", "diamond", 1_000, false);
        hit(server, "forged FE does not grant provenance", forged, 1_000, 2);
        for (int level : new int[]{1, 3, 10}) {
            var root = mixed("celestigem", "diamond", 1_000, false); enchant(server, root, level);
            hit(server, "Unbreaking " + level, root, 1_000 - (int) (200 - 200 * Math.min(1, level * .1)), 0);
        }
        var zero = mixed("celestigem", "diamond", 0, false); enchant(server, zero, 10);
        hit(server, "zero bill without base-FE threshold", zero, 0, 0);
        int fallbackWear = 0;
        for (int i = 0; i < 16; i++) {
            var root = mixed("celestigem", "diamond", 49, false); enchant(server, root, 3);
            var player = player(server); player.setItemSlot(EquipmentSlot.FEET, root);
            CommonHooks.onArmorHurt(attack(player), new EquipmentSlot[]{EquipmentSlot.FEET}, 4, player);
            require(charge(root) == 49, "Unfunded Unbreaking fallback also paid FE"); fallbackWear += root.getDamageValue();
        }
        require(fallbackWear > 0 && fallbackWear < 64, "Unfunded fallback lost ordinary armor Unbreaking");
        record("Unbreaking III unfunded fallback", "16 ordinary requests of 4: wear=" + fallbackWear + "; no FE paid");
        for (String flag : List.of("tool_enabled", "stepheight_toggle")) {
            var root = mixed("celestigem", "diamond", 1_000, false); JustDireAttributeTestSupport.set(root, flag, false);
            hit(server, flag + " off", root, 800, 0);
        }
        postEvent(server);
        nativeDirectBoundaries(server);
        isolation(server);
        abilities(server);
    }

    private static void nativeDirectBoundaries(MinecraftServer server) {
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE, GameType.SPECTATOR)) {
            var player = NeoForgeJustDireNegateFallTest.player(server, mode);
            var root = boot("celestigem", 1_000); player.setItemSlot(EquipmentSlot.FEET, root); var before = root.copy();
            if (mode != GameType.SURVIVAL) {
                require(!player.hurtServer(server.overworld(), attack(player), 8) && ItemStack.matches(before, root), "Native incoming mode immunity changed");
            }
            root.hurtAndBreak(2, player, EquipmentSlot.FEET);
            require(charge(root) == 998 && root.getDamageValue() == 0, "Native direct request gained armor's x100 or ordinary wear");
            root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE); root.hurtAndBreak(2, player, EquipmentSlot.FEET);
            require(charge(root) == 996 && root.getDamageValue() == 0, "Native direct Unbreakable/mode boundary changed");
            record("native direct " + mode, "incoming immunity unchanged; two direct requests cost 2 FE each, including Unbreakable");
        }
    }

    private static void transactions(MinecraftServer server) {
        for (boolean inverse : new boolean[]{false, true}) {
            for (String fault : List.of("short", "staged", "reenter", "replace", "wallet", "component", "committed")) {
                var root = mixed("celestigem", "diamond", 1_000, inverse); var before = root.copy();
                var player = player(server); player.setItemSlot(EquipmentSlot.FEET, root);
                var replacement = mixed("eclipsealloy", "diamond", 900, false);
                JustDireArmorWearFaults.player = player; JustDireArmorWearFaults.root = root;
                JustDireArmorWearFaults.replacement = replacement; JustDireArmorWearFaults.extracted = 0; JustDireArmorWearFaults.fault = fault;
                boolean failedAfterCommit = false;
                try { CommonHooks.onArmorHurt(attack(player), new EquipmentSlot[]{EquipmentSlot.FEET}, 2, player); }
                catch (JustDireArmorWearFaults.CommittedFailure expected) { failedAfterCommit = true; }
                require(JustDireArmorWearFaults.extracted == 200 && JustDireArmorWearFaults.fault.isEmpty(), "Fault missed real staged extraction: " + fault);
                require(failedAfterCommit == fault.equals("committed"), "Post-commit failure was swallowed/retried");
                int expectedFE = fault.equals("wallet") ? 77 : List.of("reenter", "committed").contains(fault) ? 800 : 1_000;
                int wear = List.of("short", "staged").contains(fault) ? 2 : 0;
                var expected = before.copy(); expected.set(JustDireResourceTestSupport.energyComponent(), expectedFE); expected.setDamageValue(wear);
                if (fault.equals("component")) { expected.set(DataComponents.CUSTOM_NAME, Component.literal("independent edit")); }
                require(ItemStack.matches(expected, root), "Abort overwrote foreign edit or leaked staged payment: " + fault + "; FE=" + charge(root) + "; wear=" + root.getDamageValue());
                if (fault.equals("replace")) { require(player.getItemBySlot(EquipmentSlot.FEET) == replacement && charge(replacement) == 900 && replacement.getDamageValue() == 0, "Stale fallback damaged/retried replacement"); }
                record("real staged " + fault + " inverse=" + inverse, "receipt staged=200; final FE=" + expectedFE + "; wear=" + wear + "; unrelated state preserved");
                // Same player: the finally guard must release after both successful and aborted payments.
                var next = mixed("celestigem", "diamond", 1_000, inverse); player.setItemSlot(EquipmentSlot.FEET, next);
                CommonHooks.onArmorHurt(attack(player), new EquipmentSlot[]{EquipmentSlot.FEET}, 2, player);
                require(charge(next) == 800 && next.getDamageValue() == 0, "Guard leaked after " + fault);
            }
        }
        JustDireArmorWearFaults.player = null; JustDireArmorWearFaults.root = null; JustDireArmorWearFaults.replacement = null;
    }

    private static void postEvent(MinecraftServer server) {
        for (String change : List.of("cancel", "0", "-2", "0.75", "1.75", "3.5")) {
            var root = mixed("celestigem", "diamond", 1_000, false); var player = player(server); player.setItemSlot(EquipmentSlot.FEET, root);
            mutation = change; observed = player;
            CommonHooks.onArmorHurt(attack(player), new EquipmentSlot[]{EquipmentSlot.FEET}, 2, player);
            observed = null; mutation = "none";
            int bill = change.equals("cancel") ? 0 : Math.max(0, (int) Float.parseFloat(change)) * 100;
            require(charge(root) == 1_000 - bill && root.getDamageValue() == 0, "Final event mutation ignored: " + change);
            record("post-event " + change, "FE=" + charge(root) + "; wear=0; NeoForge integer consumer quantization before x100");
        }
        // Both cancellation and a changed final amount also pass through real player combat.
        var root = mixed("celestigem", "diamond", 1_000, false);
        mutation = "cancel"; hit(server, "actual combat cancelled wear", root, 1_000, 0); mutation = "none";
        root = mixed("celestigem", "diamond", 1_000, false);
        mutation = "3.5"; hit(server, "actual combat modified wear", root, 700, 0); mutation = "none";
    }

    private static void isolation(MinecraftServer server) {
        for (GameType mode : List.of(GameType.CREATIVE, GameType.SPECTATOR)) {
            var player = NeoForgeJustDireNegateFallTest.player(server, mode);
            var root = mixed("celestigem", "diamond", 1_000, false); player.setItemSlot(EquipmentSlot.FEET, root); var before = root.copy();
            require(!player.hurtServer(server.overworld(), attack(player), 8) && ItemStack.matches(before, root), "Ordinary mode immunity lost");
            record(mode.toString(), "actual incoming hit rejected; FE/wear unchanged");
        }
        var root = mixed("celestigem", "diamond", 1_000, false); root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        hit(server, "Unbreakable", root, 1_000, 0);
        root = mixed("celestigem", "diamond", 1_000, false); root.remove(DataComponents.MAX_DAMAGE); root.remove(DataComponents.DAMAGE);
        hit(server, "non-damageable root", root, 1_000, 0);
        var player = player(server); var zombie = new Zombie(EntityType.ZOMBIE, server.overworld());
        root = mixed("celestigem", "diamond", 1_000, false); zombie.setItemSlot(EquipmentSlot.FEET, root);
        CommonHooks.onArmorHurt(attack(player), new EquipmentSlot[]{EquipmentSlot.FEET}, 2, zombie);
        require(charge(root) == 1_000 && root.getDamageValue() == 2, "Non-player got conversion");
        record("non-player", "actual CommonHooks armor consumer: FE unchanged, wear=2");
        for (String partner : List.of("diamond", "blazegold")) {
            root = mixed("celestigem", partner, 1_000, false); player = player(server); player.setItemSlot(EquipmentSlot.FEET, root); player.doTick();
            require(player.hurtServer(server.overworld(), player.damageSources().lava(), 8), "Lava hit rejected");
            require(charge(root) == (partner.equals("diamond") ? 800 : 1_000) && root.getDamageValue() == 0 && player.getHealth() < 20, "Item fire protection/wear became wearer immunity");
            record("lava " + partner, "FE=" + charge(root) + "; root wear=0; wearer still hurt");
        }
        root = mixed("celestigem", "blazegold", 1_000, false); player = player(server); player.setItemSlot(EquipmentSlot.FEET, root);
        observed = player; mutation = "3.5";
        CommonHooks.onArmorHurt(player.damageSources().lava(), new EquipmentSlot[]{EquipmentSlot.FEET}, 2, player);
        observed = null; mutation = "none";
        require(charge(root) == 1_000 && root.getDamageValue() == 3, "Revived fire-protected entry invented FE bill or overrode foreign wear");
        record("foreign revived protected entry", "original=0 -> final=3.5: ordinary wear=3; no FE conversion");
        root = mixed("celestigem", "diamond", 1_000, false); root.setDamageValue(root.getMaxDamage() - 1);
        hit(server, "funded final durability survives", root, 800, 0);
        root = mixed("celestigem", "diamond", 199, false); root.setDamageValue(root.getMaxDamage() - 1);
        player = player(server); player.setItemSlot(EquipmentSlot.FEET, root); require(player.hurtServer(server.overworld(), attack(player), 8) && root.isEmpty(), "Unfunded hybrid cannot break");
        record("unfunded final durability", "ordinary fallback destroyed root; no wear FE payment");
        root = mixed("celestigem", "diamond", 1_000, false); player = player(server); player.setItemSlot(EquipmentSlot.FEET, root);
        root.hurtAndBreak(2, player, EquipmentSlot.FEET); require(charge(root) == 1_000 && root.getDamageValue() == 2, "Direct wear converted");
        root.setDamageValue(12); root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.MENDING), 1);
        var orb = new ExperienceOrb(EntityType.EXPERIENCE_ORB, server.overworld()); orb.setValue(5); orb.playerTouch(player);
        require(root.getDamageValue() == 2 && charge(root) == 1_000, "Mending paid/refilled FE");
        record("direct wear / actual Mending", "direct request adds ordinary 2; XP repairs 12->2; FE unchanged");
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        var transformed = MixedBootsRecomposer.recompose(root, data.right().source(), data.left().source());
        require(transformed.getDamageValue() == 2 && charge(transformed) == 1_000, "Recomposition reset live wear/wallet");
        record("recomposition", "inverted sources preserve current wear=2 and FE=1000; no reinitialization");
    }

    private static void abilities(MinecraftServer server) throws Exception {
        for (boolean powered : new boolean[]{false, true}) {
            var root = mixed("celestigem", "blazegold", powered ? 10_000 : 199, false);
            JustDireAttributeTestSupport.set(root, "groundstomp_upgrade_installed", true);
            JustDireAttributeTestSupport.set(root, "groundstomp_bindingmode", 2);
            Object binding = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                    .getConstructor(String.class, int.class, boolean.class, boolean.class).newInstance("groundstomp", 71, false, true);
            JustDireAttributeTestSupport.set(root, "ability_bindings", List.of(binding));
            var player = JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL); player.setItemSlot(EquipmentSlot.FEET, root);
            var reply = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, 71, false));
            require(JustDireActiveNetwork.activate(player, new JustDireActiveNetwork.Activate(1, reply.nonce(), 71, false)), "Production GS activation rejected");
            require(charge(root) == (powered ? 5_000 : 199) && root.getDamageValue() == (powered ? 0 : 25), "GS paid armor-wear price or converted ordinary fallback");
            record("production GS powered=" + powered, "FE=" + charge(root) + "; ordinary wear=" + root.getDamageValue());
        }
        var root = mixed("celestigem", "diamond", 1_000, false); JustDireAttributeTestSupport.set(root, "negatefalldamage_upgrade_installed", true);
        var player = player(server); player.setItemSlot(EquipmentSlot.FEET, root); player.causeFallDamage(9, 1, player.damageSources().fall());
        require(charge(root) == 550 && root.getDamageValue() == 0 && player.getHealth() == 20, "Negate got combat conversion/double bill");
        record("production Negate", "9-block fall: FE=1000->550; wear=0; health=20");
        root = mixed("ferricore", "diamond", 0, false); JustDireAttributeTestSupport.set(root, "jumpboost_upgrade_installed", true);
        player = player(server); player.setItemSlot(EquipmentSlot.FEET, root); player.setDeltaMovement(Vec3.ZERO); player.jumpFromGround();
        require(player.getDeltaMovement().y > .5 && root.getDamageValue() == 0 && charge(root) == 0, "Jump gained wear/cost or lost boost");
        record("production Jump", "passive boost remains; no FE/wear payment");
    }

    private static void hit(MinecraftServer server, String label, ItemStack root, int expectedFE, int expectedWear) {
        var player = player(server); player.setItemSlot(EquipmentSlot.FEET, root); player.doTick(); var before = root.copy();
        observed = player; require(player.hurtServer(server.overworld(), attack(player), 8), "Actual hit rejected: " + label); observed = null;
        require(charge(root) == expectedFE && root.getDamageValue() - before.getDamageValue() == expectedWear,
                label + " FE=" + charge(root) + " wear delta=" + (root.getDamageValue() - before.getDamageValue()));
        var allowed = before.copy();
        if (before.getDamageValue() != root.getDamageValue()) { allowed.setDamageValue(root.getDamageValue()); }
        if (root.has(JustDireResourceTestSupport.energyComponent())) { allowed.set(JustDireResourceTestSupport.energyComponent(), expectedFE); }
        require(ItemStack.matches(allowed, root), "Wear changed unrelated state/provenance: " + label);
        record(label, "FE=" + charge(root) + "; added wear=" + expectedWear);
    }
    private static ServerPlayer player(MinecraftServer server) { return NeoForgeJustDireNegateFallTest.player(server, GameType.SURVIVAL); }
    private static ItemStack boot(String tier, int charge) { return NeoForgeJustDireResourcesTest.boots(tier, charge); }
    private static ItemStack mixed(String left, String right, int charge, boolean inverse) {
        var pair = MixedBootsFactory.createPair(source(left), source(right)); var root = inverse ? pair.inverse() : pair.primary();
        root.set(JustDireResourceTestSupport.energyComponent(), charge); return root;
    }
    private static ItemStack source(String tier) { return tier.equals("diamond") ? new ItemStack(Items.DIAMOND_BOOTS) : boot(tier, 0); }
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
