package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.authlib.GameProfile;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireJumpCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireSettingsCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireUpgradesCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Real production action/permit/settings boundaries, with disposable native controls and mutation faults. */
public final class NeoForgeJustDireGroundStompServerTest {
    private static final String PREFIX = "just-dire-ground-stomp-server";
    private static final StringBuilder report = new StringBuilder();
    private static int warmup, groups, sequence, selection;
    private static boolean finished;
    private static boolean lastPrepared, lastAccepted;
    private NeoForgeJustDireGroundStompServerTest() {}

    public static void register() { NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer())); }
    private static void run(MinecraftServer server) {
        if (finished) { return; }
        if (++warmup == 1) { server.overworld().setChunkForced(0, 0, true); }
        if (warmup < 200 && !server.overworld().areEntitiesActuallyLoadedAndTicking(new net.minecraft.world.level.ChunkPos(0, 0))) { return; }
        finished = true;
        try {
            require(warmup < 200, "Entity chunk never became ticking");
            String variant = System.getProperty("mismatchedboots.runtimetest.justDireGroundStompServerVariant", "live");
            if (!variant.equals("live")) { unavailable(server, variant); }
            else {
                require(JustDireGroundStompCompatibility.available() && JustDireSettingsCompatibility.available(), "Production APIs unavailable");
                JustDireGroundStompTestSupport.initializeApi();
                nativePassiveBindings(server);
                effectsAndProviders(server); paymentAndBreak(server); permits(server); mousePermits(server); settings(server); poweredJumpSettings(server); timers(server); faults(server);
                passiveBindings(server);
            }
            NetworkAcceptanceFiles.write(PREFIX + "-report.txt", groups + " production server groups\n" + report);
            NetworkAcceptanceFiles.write(PREFIX + "-success.txt", "neoforge\n");
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure(PREFIX, failure); }
        finally { server.overworld().setChunkForced(0, 0, false); server.halt(false); }
    }

    private static void nativePassiveBindings(MinecraftServer server) throws Exception {
        var player = player(server, GameType.SURVIVAL);
        var tool = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
        var use = tool.getMethod("useAbility", net.minecraft.world.level.Level.class,
                net.minecraft.world.entity.player.Player.class, ItemStack.class, int.class, boolean.class);
        for (String tier : List.of("blazegold", "eclipsealloy")) {
            var nativeBoot = boots(tier, 6_000);
            var names = tier.equals("blazegold") ? List.of("stepheight", "jumpboost")
                    : List.of("stepheight", "jumpboost", "negatefalldamage");
            var records = new ArrayList<Object>();
            for (String name : names) {
                set(nativeBoot, name + "_upgrade_installed", true); set(nativeBoot, name + "_toggle", false);
                set(nativeBoot, name + "_bindingmode", 2); records.add(binding(name, 72, false));
            }
            set(nativeBoot, "ability_bindings", records); set(nativeBoot, "tool_enabled", false);
            set(nativeBoot, "forge_energy", 0); player.setItemSlot(EquipmentSlot.FEET, nativeBoot);
            var before = nativeBoot.copy();
            use.invoke(nativeBoot.getItem(), player.level(), player, nativeBoot, 72, false);
            for (String name : names) { require(nativeBoot.getOrDefault(NeoForgeJustDireGroundStompServerTest.<Boolean>component(name + "_toggle"), false), "Native disabled/depleted toggle failed: " + name); }
            use.invoke(nativeBoot.getItem(), player.level(), player, nativeBoot, 72, false);
            require(ItemStack.matches(before, nativeBoot), "Native shared passives charged, cycled values or failed to toggle back");
            set(nativeBoot, "tool_enabled", true); set(nativeBoot, "forge_energy", 6_000);
            set(nativeBoot, "groundstomp_toggle", true); set(nativeBoot, "groundstomp_bindingmode", 2);
            records.add(binding("groundstomp", 72, false)); set(nativeBoot, "ability_bindings", records);
            use.invoke(nativeBoot.getItem(), player.level(), player, nativeBoot, 72, false);
            require(timer(nativeBoot) == 200, "Shared native active action did not run");
            for (String name : names) { require(nativeBoot.getOrDefault(NeoForgeJustDireGroundStompServerTest.<Boolean>component(name + "_toggle"), false), "Shared native active suppressed passive " + name); }
        }
        record("native binding audit: all shared Step/Jump/Negate toggle while disabled/depleted, no value/resource changes; shared Ground Stomp still runs before passive dispatch");
    }

    private static Object binding(String name, int key, boolean mouse) throws ReflectiveOperationException {
        return Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                .getConstructor(String.class, int.class, boolean.class, boolean.class).newInstance(name, key, mouse, true);
    }

    private static void passiveBindings(MinecraftServer server) throws Exception {
        var player = player(server, GameType.SURVIVAL);
        var names = List.of("stepheight", "jumpboost", "negatefalldamage");
        var left = boots("blazegold", 0); var right = boots("eclipsealloy", 12_000);
        for (var source : List.of(left, right)) {
            for (String name : names) { set(source, name + "_upgrade_installed", true); }
        }
        var pair = MixedBootsFactory.createPair(left, right);
        for (var initial : List.of(pair.primary(), pair.inverse())) {
            for (boolean mouse : new boolean[]{false, true}) {
                int key = mouse ? 4 : 72;
                for (String name : names) {
                    var root = initial.copy(); player.setItemSlot(EquipmentSlot.FEET, root);
                    set(root, name + "_upgrade_installed", true); set(root, name + "_bindingmode", 2);
                    set(root, name + "_toggle", false); set(root, "tool_enabled", false); set(root, "forge_energy", 0);
                    // Duplicate imported records still toggle once. Unrelated bindings are retained but inert.
                    set(root, "ability_bindings", List.of(binding(name, key, mouse), binding(name, key, mouse), binding("earthquake", key, mouse)));
                    root.set(cooldowns(), List.of(record("groundstomp", 80, false)));
                    var expected = root.copy(); set(expected, name + "_toggle", true);
                    require(dispatch(player, key, mouse) && ItemStack.matches(expected, root), "Off/depleted passive input failed: " + name);
                    require(dispatch(player, key, mouse), "Second passive toggle rejected"); set(expected, name + "_toggle", false);
                    require(ItemStack.matches(expected, root), "Passive input cycled slider or spent resources/timer/source state");
                    var freePermit = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, key, mouse));
                    set(root, "forge_energy", 23); root.setDamageValue(root.getDamageValue() + 1);
                    root.set(cooldowns(), List.of(record("groundstomp", 79, false)));
                    require(JustDireActiveNetwork.activate(player, new JustDireActiveNetwork.Activate(sequence, freePermit.nonce(), key, mouse))
                            && charge(root) == 23 && timer(root) == 79, "Ordinary charging/wear/ticking invalidated or was overwritten by free toggle");
                    var before = root.copy();
                    var permit = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, key, mouse));
                    require(permit.accepted(), "Passive prepare rejected");
                    require(!JustDireActiveNetwork.activate(player, new JustDireActiveNetwork.Activate(sequence, permit.nonce(), key, !mouse))
                            && !JustDireActiveNetwork.activate(player, new JustDireActiveNetwork.Activate(sequence, permit.nonce(), key, mouse))
                            && ItemStack.matches(before, root), "Device confusion/replay changed a passive");
                    permit = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, key, mouse));
                    set(root, name + "_value", 2);
                    require(!JustDireActiveNetwork.activate(player, new JustDireActiveNetwork.Activate(sequence, permit.nonce(), key, mouse)), "Stale passive controls accepted");
                    permit = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, key, mouse));
                    player.setItemSlot(EquipmentSlot.FEET, root.copy());
                    require(!JustDireActiveNetwork.activate(player, new JustDireActiveNetwork.Activate(sequence, permit.nonce(), key, mouse)), "Equal replacement revived passive permission");
                    player.setItemSlot(EquipmentSlot.FEET, root);
                    for (int mode : new int[]{0, 1, 99}) {
                        set(root, name + "_bindingmode", mode);
                        require(!JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, key, mouse)).accepted(), "Unsupported passive mode accepted");
                    }
                    root.remove(component(name + "_bindingmode"));
                    require(!JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, key, mouse)).accepted(), "Missing passive mode accepted");
                    set(root, name + "_bindingmode", 2); set(root, name + "_upgrade_installed", false);
                    require(!JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, key, mouse)).accepted(), "Uninstalled passive accepted");
                }
                var root = initial.copy(); player.setItemSlot(EquipmentSlot.FEET, root);
                var bindings = new ArrayList<Object>();
                for (String name : names) {
                    set(root, name + "_upgrade_installed", true); set(root, name + "_bindingmode", 2); set(root, name + "_toggle", false);
                    bindings.add(binding(name, key, mouse));
                }
                bindings.add(binding("groundstomp", key, mouse)); set(root, "groundstomp_bindingmode", 2);
                set(root, "ability_bindings", bindings); set(root, "forge_energy", 6_000);
                var history = ModContent.getMixedBootsData(root);
                root.set(cooldowns(), List.of(record("groundstomp", 1, false)));
                var freePermit = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, key, mouse));
                root.remove(cooldowns());
                require(JustDireActiveNetwork.activate(player, new JustDireActiveNetwork.Activate(sequence, freePermit.nonce(), key, mouse))
                        && charge(root) == 6_000 && timer(root) == -1, "Passive-only permission unexpectedly upgraded to a paid action after timer expiry");
                for (String name : names) { set(root, name + "_toggle", false); }
                var paidPermit = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, key, mouse));
                set(root, "forge_energy", 6_001);
                require(!JustDireActiveNetwork.activate(player, new JustDireActiveNetwork.Activate(sequence, paidPermit.nonce(), key, mouse))
                        && names.stream().noneMatch(name -> root.getOrDefault(NeoForgeJustDireGroundStompServerTest.<Boolean>component(name + "_toggle"), true)), "Shared paid permission lost strict resource validation");
                set(root, "forge_energy", 6_000);
                require(dispatch(player, key, mouse) && charge(root) == 1_000 && timer(root) == 200, "Shared active/passive input lost single action/bill/timer");
                for (String name : names) { require(root.getOrDefault(NeoForgeJustDireGroundStompServerTest.<Boolean>component(name + "_toggle"), false), "Shared passive not toggled: " + name); }
                require(dispatch(player, key, mouse) && charge(root) == 1_000 && timer(root) == 200, "Cooldown suppressed passive input or caused duplicate payment");
                for (String name : names) { require(!root.getOrDefault(NeoForgeJustDireGroundStompServerTest.<Boolean>component(name + "_toggle"), true), "Shared passive not toggled back: " + name); }
                require(ModContent.getMixedBootsData(root).equals(history), "Passive controls edited provenance");
                var data = ModContent.getMixedBootsData(root).orElseThrow();
                var rebuilt = MixedBootsRecomposer.recompose(root, data.left().source(), data.right().source());
                for (String name : names) { require(rebuilt.get(component(name + "_bindingmode")).equals(2), "Live binding mode reimported from sources"); }
                require(rebuilt.get(component("ability_bindings")).equals(bindings), "Shared records lost through recomposition");
                root.remove(component("jumpboost_bindingmode")); set(root, "stepheight_bindingmode", 0);
                rebuilt = MixedBootsRecomposer.recompose(root, data.left().source(), data.right().source());
                require(!rebuilt.has(component("jumpboost_bindingmode")) && rebuilt.get(component("stepheight_bindingmode")).equals(0), "Explicit mode absence/removal lost");
            }
        }
        var unsupported = MixedBootsFactory.createPair(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)).primary();
        player.setItemSlot(EquipmentSlot.FEET, unsupported);
        set(unsupported, "jumpboost_upgrade_installed", true); set(unsupported, "jumpboost_bindingmode", 2);
        set(unsupported, "ability_bindings", List.of(binding("jumpboost", 72, false), binding("earthquake", 72, false)));
        require(!JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, 72, false)).accepted(), "Unsupported tier acquired Jump/Earthquake input");
        record("passive keyboard/mouse on both orientations: installed/tier admission, one toggle per ability, off/depleted/global-off eligibility, exact whole-root mutation, stale/device/replay/equipment/mode/installation rejection; shared active first/single bill and cooldown-independent toggles; live mode/removal recomposition");
        var root = pair.primary().copy(); player.setItemSlot(EquipmentSlot.FEET, root);
        for (String name : names) { set(root, name + "_upgrade_installed", true); set(root, name + "_bindingmode", 2); }
        set(root, "ability_bindings", List.of(binding("stepheight", 72, false), binding("jumpboost", 73, false), binding("negatefalldamage", 74, false)));
        double step = stepAmount(root);
        require(dispatch(player, 72, false) && stepAmount(root) < step, "Step binding did not change live attribute projection");
        require(dispatch(player, 72, false) && stepAmount(root) == step, "Step binding did not restore live projection");
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); JustDireJumpCompatibility.jump(player, root);
        require(player.getDeltaMovement().y > 0, "Enabled passive Jump had no boost");
        require(dispatch(player, 73, false), "Jump toggle rejected");
        player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); JustDireJumpCompatibility.jump(player, root);
        require(player.getDeltaMovement().y == 0, "Jump boost survived binding disable");
        set(root, "forge_energy", 6_000);
        var fall = new LivingFallEvent(player, 9, 1); NeoForge.EVENT_BUS.post(fall);
        require(fall.getDistance() == 0 && charge(root) < 6_000, "Enabled Negate did not protect/pay through actual native event");
        require(dispatch(player, 74, false), "Negate toggle rejected");
        set(root, "forge_energy", 6_000); fall = new LivingFallEvent(player, 9, 1); NeoForge.EVENT_BUS.post(fall);
        require(fall.getDistance() == 9 && charge(root) == 6_000, "Disabled Negate still protected or paid");
        record("bound root switches immediately affect live Step projection, passive Jump movement and actual Negate fall consumer/payment; toggling itself never pays");
    }

    private static double stepAmount(ItemStack root) {
        return root.getAttributeModifiers().modifiers().stream().filter(entry -> entry.attribute().equals(Attributes.STEP_HEIGHT))
                .mapToDouble(entry -> entry.modifier().amount()).sum();
    }

    private static boolean dispatch(ServerPlayer player, int key, boolean mouse) {
        var prepared = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, key, mouse));
        if (!prepared.accepted()) { return false; }
        var request = new JustDireActiveNetwork.Activate(sequence, prepared.nonce(), key, mouse);
        boolean accepted = JustDireActiveNetwork.activate(player, request);
        require(!JustDireActiveNetwork.activate(player, request), "Consumed passive input replayed");
        return accepted;
    }

    private static void effectsAndProviders(MinecraftServer server) throws Exception {
        ServerPlayer player = player(server, GameType.SURVIVAL);
        double[] distances = {2, -2, 0, 3.01, 4};
        var nativeResult = JustDireGroundStompTestSupport.nativeUse(player, JustDireGroundStompTestSupport.boots("eclipsealloy", 6_000), distances);
        var pair = MixedBootsFactory.createPair(boots("blazegold", 0), boots("eclipsealloy", 12_000));
        for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
            configure(root); set(root, "groundstomp_value", 5);
            var before = root.copy();
            var motion = use(player, root, distances);
            require(motion.equals(nativeResult.motions()) && charge(root) == 1_000 && timer(root) == 200,
                    "Native-equivalent airborne/AABB/multi/coincident effect or single bill failed: native=" + nativeResult.motions()
                            + ", mixed=" + motion + ", FE=" + charge(root) + ", timer=" + timer(root)
                            + ", prepared=" + lastPrepared + ", accepted=" + lastAccepted + ", quote=" + JustDireGroundStompCompatibility.quote(root));
            require(ModContent.getMixedBootsData(root).equals(ModContent.getMixedBootsData(before)), "Sources changed");
            var after = root.copy(); require(use(player, root, new double[]{2}).getFirst() == 0 && ItemStack.matches(root, after), "Duplicate mutated state");
        }
        var empty = root("eclipsealloy", 12_000); use(player, empty, new double[]{});
        require(lastAccepted && charge(empty) == 1_000 && timer(empty) == 200, "Empty targets skipped bill/cooldown");
        var excluded = new MultipartMob(player.level()); excluded.setNoAi(true); excluded.setNoGravity(true);
        excluded.setPos(player.getX() + 2, player.getY(), player.getZ());
        require(player.level().addFreshEntity(excluded), "Multipart filter control not inserted");
        try {
            require(use(player, root("eclipsealloy", 12_000), new double[]{2}).getFirst() > 0
                    && excluded.getDeltaMovement().horizontalDistance() == 0, "Multipart mob was not excluded");
        } finally { excluded.discard(); }
        record("native effects on both orientations: airborne, multiple/coincident/edge-AABB/outside targets, one root bill/timer; duplicate rejected; empty targets still commit");
        for (int charge : new int[]{9_998, 10_000}) {
            var root = MixedBootsFactory.createPair(boots("blazegold", 0), boots("eclipsealloy", charge)).primary();
            configure(root); set(root, "groundstomp_value", 5); var before = root.copy();
            double motion = use(player, root, new double[]{2}).getFirst();
            require(motion == (charge == 10_000 ? 5.0 : 2.0) && timer(root) == 200 && root.getOrDefault(component("groundstomp_value"), 0).equals(5), "Fallback strength/control reset");
            require(charge == 10_000 ? charge(root) == 0 && root.getDamageValue() == before.getDamageValue()
                    : charge(root) == 4_999 && root.getDamageValue() == before.getDamageValue() + 25, "Fallback did not pay exactly one provider");
        }
        for (boolean inverse : new boolean[]{false, true}) {
            var twoPowered = MixedBootsFactory.createPair(boots("celestigem", 10_000), boots("eclipsealloy", 10_000));
            var root = inverse ? twoPowered.inverse() : twoPowered.primary(); configure(root); set(root, "groundstomp_value", 1);
            require(JustDireGroundStompCompatibility.quote(root).provider() == JustDireGroundStompCompatibility.Provider.ECLIPSE,
                    "Tie priority changed");
            require(use(player, root, new double[]{2}).getFirst() == 1.0 && charge(root) == 5_000 && timer(root) == 200, "Tie paid multiple providers");
        }
        record("strongest-usable selection, exact 4999/5000 threshold, ordinary fallback clamp without slider rewrite, deterministic two-powered tie: one effect/bill/shared cooldown");
        Map<?, ?> config = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config").getField("AVAILABLE_ABILITY_MAP").get(null);
        var flag = (ModConfigSpec.BooleanValue) config.get(Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability").getField("GROUNDSTOMP").get(null));
        boolean previous = flag.get();
        try { flag.set(false); var root = root("eclipsealloy", 12_000); player.setItemSlot(EquipmentSlot.FEET, root); require(!prepared(player).accepted(), "Disabled config authorized action"); }
        finally { flag.set(previous); }
        for (String control : List.of("tool_enabled", "groundstomp_toggle", "groundstomp_upgrade_installed")) {
            var root = root("eclipsealloy", 12_000); set(root, control, false); player.setItemSlot(EquipmentSlot.FEET, root);
            var before = root.copy(); require(!prepared(player).accepted() && ItemStack.matches(before, root), "Disabled control mutated root");
        }
        record("disabled installation/tool/toggle/native config reject before bill or cooldown");
    }

    private static void paymentAndBreak(MinecraftServer server) throws Exception {
        var enchantment = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING);
        for (GameType mode : List.of(GameType.SURVIVAL, GameType.CREATIVE, GameType.SPECTATOR)) {
            ServerPlayer player = player(server, mode);
            for (int level : new int[]{0, 3, 10}) {
                var root = root("eclipsealloy", 12_000); if (level > 0) { root.enchant(enchantment, level); }
                root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE), 1);
                int bill = mode == GameType.SURVIVAL ? (int) (5_000 - 5_000 * Math.min(1, level * .1)) : 0;
                require(use(player, root, new double[]{2}).getFirst() > 0 && charge(root) == 6_000 - bill && timer(root) == 200
                        && player.getItemBySlot(EquipmentSlot.FEET) == root, "FE discount/exemption/Binding failure");
                var insufficient = root("eclipsealloy", 9_998); insufficient.enchant(enchantment, 10); player.setItemSlot(EquipmentSlot.FEET, insufficient);
                require(!prepared(player).accepted(), "Payment exemption bypassed base eligibility");
                var ordinary = root("blazegold", 0); var before = ordinary.copy(); use(player, ordinary, new double[]{});
                require(ordinary.getDamageValue() == before.getDamageValue() + (mode == GameType.SURVIVAL ? 25 : 0), "Ordinary mode payment mismatch");
            }
        }
        record("FE discounts 0/III/X, survival/creative/spectator, full base eligibility even exempt, ordinary exemptions, Binding Curse without slot exchange");
        ServerPlayer player = player(server, GameType.SURVIVAL);
        var root = root("blazegold", 0); root.enchant(enchantment, 3); var direct = root.copy();
        player.setItemSlot(EquipmentSlot.FEET, direct); server.overworld().getRandom().setSeed(123456); direct.hurtAndBreak(25, player, EquipmentSlot.FEET);
        server.overworld().getRandom().setSeed(123456); use(player, root, new double[]{});
        require(root.getDamageValue() == direct.getDamageValue() && root.getDamageValue() > 0 && root.getDamageValue() < 25, "Unbreaking wear diverged from actual root");
        var unbreakable = root("blazegold", 0); unbreakable.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        unbreakable.setDamageValue(unbreakable.getMaxDamage() - 24); player.setItemSlot(EquipmentSlot.FEET, unbreakable);
        require(!prepared(player).accepted(), "Unbreakable bypassed budget");
        unbreakable.setDamageValue(unbreakable.getMaxDamage() - 25); int wear = unbreakable.getDamageValue(); use(player, unbreakable, new double[]{});
        require(unbreakable.getDamageValue() == wear && timer(unbreakable) == 200, "Funded Unbreakable action failed");
        var observer = new BreakObserver(server); var breaking = root("blazegold", 0); breaking.setDamageValue(breaking.getMaxDamage() - 25);
        var replacement = root("eclipsealloy", 12_000); observer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE));
        observer.callback = () -> { observer.setItemSlot(EquipmentSlot.FEET, replacement); require(!attempt(observer), "Break callback reentered action"); };
        require(use(observer, breaking, new double[]{2}).getFirst() > 0 && breaking.isEmpty() && observer.callbacks == 1
                && observer.brokenSlot == EquipmentSlot.FEET && observer.getMainHandItem().is(Items.STONE)
                && charge(replacement) == 6_000 && timer(replacement) == -1, "Actual FEET break/reentry failure");
        record("actual armor Unbreaking wear, base-eligible Unbreakable, exact durability FEET break once with unchanged hand and rejected callback reentry");
    }

    private static void permits(MinecraftServer server) throws Exception {
        ServerPlayer player = player(server, GameType.SURVIVAL); var root = root("eclipsealloy", 12_000); player.setItemSlot(EquipmentSlot.FEET, root);
        var ready = root.copy();
        var issued = prepared(player); require(issued.accepted(), "No permit"); var first = activatePacket(issued);
        var newer = prepared(player); require(!JustDireActiveNetwork.activate(player, first) && charge(root) == 6_000, "Superseded nonce accepted");
        var packet = activatePacket(newer); require(JustDireActiveNetwork.activate(player, packet) && !JustDireActiveNetwork.activate(player, packet), "Permit not one-use");
        root.remove(cooldowns()); set(root, "forge_energy", 6_000);
        require(ItemStack.matches(root, ready) && JustDireGroundStompCompatibility.quote(root) != null,
                "Replay control did not restore the original otherwise-ready root state");
        require(!JustDireActiveNetwork.activate(player, packet) && charge(root) == 6_000, "Expired/recharged identical state revived consumed permission");
        for (int fault = 0; fault < 6; fault++) {
            root = root("eclipsealloy", 12_000);
            if (fault == 5) { root.set(NeoForgeJustDireGroundStompServerTest.<List<Object>>component("ability_bindings"),
                    new ArrayList<>(root.getOrDefault(component("ability_bindings"), List.of()))); }
            player.setItemSlot(EquipmentSlot.FEET, root); issued = prepared(player); require(issued.accepted(), "No stale permit");
            packet = activatePacket(issued);
            if (fault == 0) { player.setItemSlot(EquipmentSlot.FEET, root.copy()); }
            if (fault == 1) { set(root, "groundstomp_value", 1); }
            if (fault == 2) { packet = new JustDireActiveNetwork.Activate(issued.sequence(), issued.nonce(), 72, false); }
            if (fault == 3) { packet = new JustDireActiveNetwork.Activate(issued.sequence(), issued.nonce(), 71, true); }
            if (fault == 4) { server.getWorldData().overworldData().setGameTime(server.overworld().getGameTime() + 41); }
            if (fault == 5) { root.get(NeoForgeJustDireGroundStompServerTest.<List<Object>>component("ability_bindings"))
                    .add(Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                            .getConstructor(String.class, int.class, boolean.class, boolean.class).newInstance("groundstomp", 72, false, true)); }
            require(!JustDireActiveNetwork.activate(player, packet) && charge(root) == 6_000 && timer(root) == -1, "Stale/forged permit mutated root: " + fault);
            require(!JustDireActiveNetwork.activate(player, activatePacket(issued)), "Rejected consumed permit revived");
        }
        root = root("eclipsealloy", 12_000); player.setItemSlot(EquipmentSlot.FEET, root);
        require(!JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, 71, true)).accepted(), "Mouse accepted");
        for (int mode : new int[]{0, 1, 3, 99}) { set(root, "groundstomp_bindingmode", mode); require(!prepared(player).accepted(), "Unsupported mode accepted"); }
        set(root, "groundstomp_bindingmode", 2);
        require(!JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, 72, false)).accepted(), "Wrong key accepted");
        var samePlayerId = new ServerPlayer(server, server.overworld(), player.getGameProfile(), ClientInformation.createDefault());
        samePlayerId.setItemSlot(EquipmentSlot.FEET, root); issued = prepared(player);
        require(!JustDireActiveNetwork.activate(samePlayerId, activatePacket(issued)), "Player replacement accepted old permission");
        var removedBinding = root.copy(); set(removedBinding, "ability_bindings", List.of()); player.setItemSlot(EquipmentSlot.FEET, removedBinding);
        require(!prepared(player).accepted(), "Removed binding accepted");
        root = root("eclipsealloy", 12_000); player.setItemSlot(EquipmentSlot.FEET, root); issued = prepared(player);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
        require(!JustDireActiveNetwork.activate(player, activatePacket(issued)) && charge(root) == 6_000, "Logout retained unconsumed permit");
        record("superseded/consumed replay even after lockout removal, equal-component replacement, stale settings, wrong key/device, expired nonce, unsupported mode/unbound and player replacement reject with no bill/timer");
    }

    private static void mousePermits(MinecraftServer server) throws Exception {
        var player = player(server, GameType.SURVIVAL);
        var constructor = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                .getConstructor(String.class, int.class, boolean.class, boolean.class);
        for (int button = 2; button <= 7; button++) {
            var root = root("eclipsealloy", 12_000); player.setItemSlot(EquipmentSlot.FEET, root);
            set(root, "ability_bindings", List.of(constructor.newInstance("groundstomp", button, true, true)));
            var history = ModContent.getMixedBootsData(root);
            var issued = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, button, true));
            require(issued.accepted(), "Additional mouse button rejected: " + button);
            var packet = new JustDireActiveNetwork.Activate(issued.sequence(), issued.nonce(), button, true);
            require(JustDireActiveNetwork.activate(player, packet) && charge(root) == 1_000 && timer(root) == 200
                    && !JustDireActiveNetwork.activate(player, packet) && ModContent.getMixedBootsData(root).equals(history),
                    "Mouse bill/timer/replay/provenance failed: " + button);
        }
        for (int button : new int[]{-1, 0, 1, 8, 71}) {
            var root = root("eclipsealloy", 12_000); player.setItemSlot(EquipmentSlot.FEET, root);
            set(root, "ability_bindings", List.of(constructor.newInstance("groundstomp", button, true, true)));
            var before = root.copy();
            require(!JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, button, true)).accepted()
                    && ItemStack.matches(root, before), "Forged mouse code accepted: " + button);
        }
        for (int fault = 0; fault < 3; fault++) {
            var root = root("eclipsealloy", 12_000); player.setItemSlot(EquipmentSlot.FEET, root);
            set(root, "ability_bindings", List.of(constructor.newInstance("groundstomp", 3, true, true)));
            require(!JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, 3, false)).accepted(), "Mouse code accepted as keyboard");
            var issued = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, 3, true));
            require(issued.accepted(), "Mouse permit not issued");
            if (fault == 1) { player.setItemSlot(EquipmentSlot.FEET, root.copy()); }
            if (fault == 2) { set(root, "groundstomp_value", 1); }
            var before = root.copy();
            var rejected = new JustDireActiveNetwork.Activate(issued.sequence(), issued.nonce(), 3, fault != 0);
            var original = new JustDireActiveNetwork.Activate(issued.sequence(), issued.nonce(), 3, true);
            require(!JustDireActiveNetwork.activate(player, rejected) && !JustDireActiveNetwork.activate(player, original)
                    && ItemStack.matches(root, before), "Mouse device/stale state revived permission: " + fault);
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            require(!JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, 3, true)).accepted(), "Unequipped mouse authorized");
        }
        record("mouse buttons 2-7: one bill/timer, consumed replay and provenance; left/right/out-of-range/keyboard confusion, stale root/settings and unequipped rejection");
    }

    private static void settings(MinecraftServer server) throws Exception {
        ServerPlayer player = player(server, GameType.SURVIVAL); var root = root("eclipsealloy", 0); player.setItemSlot(EquipmentSlot.FEET, root);
        AbstractContainerMenu menu = (AbstractContainerMenu) Class.forName("com.direwolf20.justdirethings.common.containers.ToolSettingContainer")
                .getConstructor(int.class, net.minecraft.world.entity.player.Inventory.class, net.minecraft.world.entity.player.Player.class)
                .newInstance(9, player.getInventory(), player); player.containerMenu = menu;
        var history = ModContent.getMixedBootsData(root); var selected = JustDireActiveNetwork.edit(player, request(menu, ++selection, 1, JustDireActiveNetwork.NONE, 0, "", 0, 71, false, true));
        require(selected.accepted(), "Production selection rejected empty-charge root");
        require(!prepared(player).accepted(), "Menu selection authorized activation");
        var token = selected.token();
        var value = request(menu, selection, 2, token, 2, "groundstomp", 5, 71, false, true);
        require(JustDireActiveNetwork.edit(player, value).accepted(), "Allowed native range edit rejected");
        var snapshot = root.copy(); require(!JustDireActiveNetwork.edit(player, value).accepted() && ItemStack.matches(root, snapshot), "Edit replay accepted");
        int number = 3;
        for (var bad : List.of(request(menu, selection, number++, token, 2, "groundstomp", 6, 71, false, true),
                request(menu, selection, number++, token, 1, "earthquake", 0, 71, false, true),
                request(menu, selection, number++, token, 4, "groundstomp", 1, 71, false, true),
                request(menu, selection, number++, token, 4, "groundstomp", 2, 71, true, true),
                request(menu, selection, number++, token, 4, "groundstomp", 2, 71, false, false),
                request(menu, selection, number++, token, 99, "groundstomp", 0, 71, false, true),
                request(menu, selection, number++, token, 3, "groundstomp", -1, 71, false, true))) {
            require(!JustDireActiveNetwork.edit(player, bad).accepted() && ItemStack.matches(root, snapshot), "Forged edit changed root: " + bad);
        }
        root.set(cooldowns(), List.of(record("groundstomp", 80, false))); set(root, "forge_energy", 123); root.setDamageValue(31);
        require(JustDireActiveNetwork.edit(player, request(menu, selection, number++, token, 3, "groundstomp", 1, 71, false, true)).accepted(), "Natural resources/timer invalidated menu settings");
        require(JustDireActiveNetwork.edit(player, request(menu, selection, number++, token, 4, "groundstomp", 2, 72, false, true)).accepted(), "Keyboard binding edit rejected");
        for (int button : new int[]{0, 1, 8}) {
            require(!JustDireActiveNetwork.edit(player, request(menu, selection, number++, token, 4, "groundstomp", 2, button, true, true)).accepted(), "Unsupported mouse edit accepted");
        }
        require(JustDireActiveNetwork.edit(player, request(menu, selection, number++, token, 4, "groundstomp", 2, 7, true, true)).accepted()
                && JustDireSettingsCompatibility.groundStompBinding(root, 7, true)
                && !JustDireSettingsCompatibility.groundStompBinding(root, 7, false), "Mouse binding/device edit failed");
        require(JustDireActiveNetwork.edit(player, request(menu, selection, number++, token, 4, "groundstomp", 0, -1, false, true)).accepted(), "Binding removal rejected");
        require(charge(root) == 123 && timer(root) == 80 && root.getDamageValue() == 31 && ModContent.getMixedBootsData(root).equals(history), "Settings mutated live wallets/timers/sources");
        var before = root.copy(); player.setItemSlot(EquipmentSlot.FEET, root.copy());
        require(!JustDireActiveNetwork.edit(player, request(menu, selection, number++, token, 1, "groundstomp", 0, 71, false, true)).accepted()
                && ItemStack.matches(before, root), "Equal replacement accepted menu token");
        require(!JustDireActiveNetwork.edit(player, request(menu, selection, 99, JustDireActiveNetwork.NONE, 0, "", 0, 71, false, true)).accepted(), "Selection generation replay accepted");
        player.setItemSlot(EquipmentSlot.FEET, root); selected = JustDireActiveNetwork.edit(player, request(menu, ++selection, 1, JustDireActiveNetwork.NONE, 0, "", 0, 71, false, true));
        require(selected.accepted(), "Fresh selection rejected");
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Close(player, menu));
        require(!JustDireActiveNetwork.edit(player, request(menu, selection, 2, selected.token(), 1, "groundstomp", 0, 71, false, true)).accepted(), "Closed menu revived session");
        player.containerMenu = player.inventoryMenu;
        record("actual native menu: installed root range at zero FE, ordered edits/replay/range/ability/mode/device rejection, live resource/timer coexistence, keyboard capture/removal, identity/generation/close invalidation and unchanged sources");
        require(JustDireSettingsCompatibility.metadata(root("ferricore", 0), "jumpboost") != null
                && JustDireSettingsCompatibility.metadata(root("eclipsealloy", 0), "jumpboost").maximum() == 5
                && JustDireSettingsCompatibility.metadata(root, "earthquake") != null
                && JustDireSettingsCompatibility.metadata(root("celestigem", 0), "earthquake") == null, "Ability metadata lost its tier boundaries");
        record("settings inventory includes powered Jump's permanent tier range, excludes ineligible Earthquake and retains depleted powered Ground Stomp/Eclipse Earthquake ranges");
        var combined = MixedBootsFactory.createPair(boots("blazegold", 0), boots("eclipsealloy", 0)).primary();
        for (String ability : List.of("stepheight", "jumpboost", "negatefalldamage", "groundstomp")) { set(combined, ability + "_upgrade_installed", true); }
        player.setItemSlot(EquipmentSlot.FEET, combined); player.containerMenu = menu;
        selected = JustDireActiveNetwork.edit(player, request(menu, ++selection, 1, JustDireActiveNetwork.NONE, 0, "", 0, 71, false, true));
        require(selected.accepted(), "Mixed passive/active settings selection rejected"); token = selected.token(); number = 2;
        for (String ability : List.of("stepheight", "jumpboost", "negatefalldamage")) {
            require(JustDireActiveNetwork.edit(player, request(menu, selection, number++, token, 1, ability, 0, 71, false, true)).accepted()
                    && !combined.getOrDefault(NeoForgeJustDireGroundStompServerTest.<Boolean>component(ability + "_toggle"), true), "Passive toggle rejected: " + ability);
            require(JustDireActiveNetwork.edit(player, request(menu, selection, number++, token, 4, ability, 2, 4, true, true)).accepted()
                    && JustDireSettingsCompatibility.boundPassives(combined, 4, true).contains(ability), "Passive binding edit rejected");
            for (int button : new int[]{0, 1, 8}) {
                require(!JustDireActiveNetwork.edit(player, request(menu, selection, number++, token, 4, ability, 2, button, true, true)).accepted(), "Invalid passive mouse edit accepted");
            }
            require(!JustDireActiveNetwork.edit(player, request(menu, selection, number++, token, 4, ability, 1, 72, false, true)).accepted()
                    && !JustDireActiveNetwork.edit(player, request(menu, selection, number++, token, 4, ability, 2, 72, false, false)).accepted(), "Passive mode/inventory edit accepted");
            require(JustDireActiveNetwork.edit(player, request(menu, selection, number++, token, 4, ability, 2, -1, false, true)).accepted()
                    && !JustDireSettingsCompatibility.boundPassives(combined, 4, true).contains(ability), "Passive Escape removal rejected");
        }
        require(JustDireActiveNetwork.edit(player, request(menu, selection, number++, token, 2, "jumpboost", 2, 71, false, true)).accepted(), "Passive Jump slider rejected");
        for (String ability : List.of("stepheight", "negatefalldamage")) {
            require(!JustDireActiveNetwork.edit(player, request(menu, selection, number++, token, 2, ability, 1, 71, false, true)).accepted(), "Dead numeric setting accepted: " + ability);
        }
        require(JustDireActiveNetwork.edit(player, request(menu, selection, number++, token, 5, "", 0, 71, false, true)).accepted()
                && !combined.getOrDefault(NeoForgeJustDireGroundStompServerTest.<Boolean>component("tool_enabled"), true), "Tool disable rejected");
        require(JustDireActiveNetwork.edit(player, request(menu, selection, number++, token, 5, "", 0, 71, false, true)).accepted()
                && combined.getOrDefault(NeoForgeJustDireGroundStompServerTest.<Boolean>component("tool_enabled"), true), "Tool re-enable rejected");
        var uninstalled = combined.copy(); set(uninstalled, "jumpboost_upgrade_installed", false);
        player.setItemSlot(EquipmentSlot.FEET, uninstalled);
        selected = JustDireActiveNetwork.edit(player, request(menu, ++selection, 1, JustDireActiveNetwork.NONE, 0, "", 0, 71, false, true));
        require(selected.accepted() && !JustDireActiveNetwork.edit(player, request(menu, selection, 2, selected.token(), 1, "jumpboost", 0, 71, false, true)).accepted(), "Uninstalled passive settings authorized edit");
        player.containerMenu = player.inventoryMenu;
        record("installed supported passive settings: Step/Jump/Negate toggles and Jump slider, tool disable/re-enable, uninstalled rejection; no dead Step/Negate numeric setting");
    }

    private static void poweredJumpSettings(MinecraftServer server) throws Exception {
        var player = player(server, GameType.SURVIVAL);
        var menu = (AbstractContainerMenu) Class.forName("com.direwolf20.justdirethings.common.containers.ToolSettingContainer")
                .getConstructor(int.class, net.minecraft.world.entity.player.Inventory.class, net.minecraft.world.entity.player.Player.class)
                .newInstance(10, player.getInventory(), player);
        for (String tier : List.of("celestigem", "eclipsealloy")) {
            for (boolean passive : tier.equals("eclipsealloy") ? new boolean[]{false, true} : new boolean[]{false}) {
                var pair = MixedBootsFactory.createPair(NeoForgeJustDireJumpTest.poweredBoots(tier, 0), passive
                        ? NeoForgeJustDireJumpTest.boots("ferricore", true) : new ItemStack(Items.DIAMOND_BOOTS));
                for (var root : List.of(pair.primary(), pair.inverse())) {
                    int maximum = tier.equals("celestigem") ? 3 : 5;
                    player.setItemSlot(EquipmentSlot.FEET, root); player.containerMenu = menu;
                    set(root, "tool_enabled", false); set(root, "jumpboost_toggle", false);
                    var selected = JustDireActiveNetwork.edit(player, request(menu, ++selection, 1, JustDireActiveNetwork.NONE,
                            JustDireActiveNetwork.SELECT, "", 0, -1, false, true));
                    require(selected.accepted(), "Powered Jump selection rejected at zero FE");
                    int number = 2;
                    for (int energy : new int[]{0, 4, 5}) {
                        set(root, "forge_energy", energy); set(root, "tool_enabled", false); set(root, "jumpboost_toggle", false);
                        var before = root.copy(); set(before, "jumpboost_value", maximum);
                        require(JustDireSettingsCompatibility.metadata(root, "jumpboost").maximum() == maximum
                                && JustDireActiveNetwork.edit(player, request(menu, selection, number++, selected.token(),
                                    JustDireActiveNetwork.VALUE, "jumpboost", maximum, -1, false, true)).accepted()
                                && ItemStack.matches(before, root), "Live resources/switches changed the permanent range or setting edit");
                        require(!JustDireActiveNetwork.edit(player, request(menu, selection, number++, selected.token(),
                                    JustDireActiveNetwork.VALUE, "jumpboost", maximum + 1, -1, false, true)).accepted()
                                && ItemStack.matches(before, root), "Forged powered Jump range changed root state");
                        set(root, "tool_enabled", true); set(root, "jumpboost_toggle", true);
                        NeoForgeJustDireJumpTest.check(server, root, energy >= 5 ? maximum : passive ? 1 : 0);
                        // Restore the selected controls before another request; only live resources
                        // may drift without a settings edit or reselection.
                        set(root, "tool_enabled", false); set(root, "jumpboost_toggle", false);
                    }
                    var before = root.copy(); root.setDamageValue(root.getMaxDamage());
                    require(JustDireSettingsCompatibility.metadata(root, "jumpboost").maximum() == maximum, "Wear shrank the permanent Jump range");
                    root.setDamageValue(before.getDamageValue());
                    root.remove(component("jumpboost_upgrade_installed")); before = root.copy();
                    selected = JustDireActiveNetwork.edit(player, request(menu, ++selection, 1, JustDireActiveNetwork.NONE,
                            JustDireActiveNetwork.SELECT, "", 0, -1, false, true));
                    require(selected.accepted(), "Uninstalled powered tier lost permanent selection metadata");
                    require(!JustDireActiveNetwork.edit(player, request(menu, selection, number++, selected.token(),
                                JustDireActiveNetwork.VALUE, "jumpboost", maximum, -1, false, true)).accepted()
                            && ItemStack.matches(before, root), "Historical powered installation authorized a setting edit");
                    record(tier + (passive ? "+Ferricore" : "+vanilla") + ": settings bounds at 0/4/5 FE/off/worn; rejected overflow/uninstalled edits; actual movement/fall follows the edited root without payment");
                }
            }
        }
        player.containerMenu = player.inventoryMenu;
    }

    private static void timers(MinecraftServer server) throws Exception {
        ServerPlayer player = player(server, GameType.SURVIVAL); var root = root("eclipsealloy", 0); player.setItemSlot(EquipmentSlot.FEET, root);
        var invalid = record("groundstomp", -1, false); var active = record("groundstomp", 3, true); var other = record("earthquake", 11, true);
        var shared = new ArrayList<>(List.of(record("groundstomp", 2, false), invalid, active, other)); root.set(cooldowns(), shared);
        var historical = root.copy(); set(root, "tool_enabled", false); set(root, "groundstomp_upgrade_installed", false);
        var tick = JustDireGroundStompCompatibility.class.getDeclaredMethod("tickEquipped", ServerPlayer.class); tick.setAccessible(true);
        tick.invoke(null, player); require(shared.size() == 4 && timer(historical) == 2 && timer(root) == 1, "Timer shallow-copy alias");
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); tick.invoke(null, player); require(timer(root) == 1, "Detached timer advanced");
        player.setItemSlot(EquipmentSlot.FEET, root); tick.invoke(null, player);
        require(root.get(cooldowns()).equals(List.of(invalid, active, other)), "Expiry advanced unsupported records");
        root.set(cooldowns(), List.of(record("groundstomp", 0, false), other)); tick.invoke(null, player);
        require(root.get(cooldowns()).equals(List.of(other)), "Zero native timer was not expired");
        var dormant = MixedBootsFactory.createPair(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)).primary();
        dormant.set(cooldowns(), List.of(record("groundstomp", 1, false), other)); player.setItemSlot(EquipmentSlot.FEET, dormant); tick.invoke(null, player);
        require(dormant.get(cooldowns()).equals(List.of(other)), "Dormant provider reset/paused worn timer");
        record("production copy-on-write timer: disabled/depleted/dormant worn expiry, unequipped pause, zero expiry, unrelated and negative/active Ground Stomp retained unchanged");
    }

    private static void faults(MinecraftServer server) throws Exception {
        var player = player(server, GameType.SURVIVAL);
        for (int mode : new int[]{1, 2, 3}) {
            var root = root("eclipsealloy", 12_000); player.setItemSlot(EquipmentSlot.FEET, root); var before = root.copy();
            JustDireGroundStompServerGuardPlugin.player = player; JustDireGroundStompServerGuardPlugin.replacement = before.copy();
            JustDireGroundStompServerGuardPlugin.fault = mode;
            try {
                require(!attempt(player) && charge(root) == 6_000 && timer(root) == -1, "Production failed debit did not rollback: " + mode);
                require(JustDireGroundStompServerGuardPlugin.extracted == 5_000, "Fault never reached real mutation");
            } finally { JustDireGroundStompServerGuardPlugin.fault = 0; }
        }
        var root = root("eclipsealloy", 12_000); player.setItemSlot(EquipmentSlot.FEET, root);
        JustDireGroundStompServerGuardPlugin.fault = 4;
        try { attempt(player); throw new AssertionError("Post-commit exception was swallowed"); }
        catch (JustDireGroundStompServerGuardPlugin.PostCommitFailure expected) {
            require(charge(root) == 1_000 && timer(root) == 200 && !attempt(player), "Post-commit failure refunded or retried");
        } finally { JustDireGroundStompServerGuardPlugin.fault = 0; }
        record("actual live-handler extraction faults: short receipt, equal equipment replacement and changed root controls rollback FE with no timer; post-commit effect failure propagates with paid cooldown/no retry");
    }

    private static void unavailable(MinecraftServer server, String variant) throws Exception {
        var player = player(server, GameType.SURVIVAL);
        var root = variant.equals("absent") ? MixedBootsFactory.createPair(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS)).primary() : root("eclipsealloy", 12_000);
        player.setItemSlot(EquipmentSlot.FEET, root); var before = root.copy();
        require(!prepared(player).accepted() && !prepared(player).accepted() && ItemStack.matches(before, root), "Unavailable new route mutated root");
        if (!variant.equals("absent")) {
            require(JustDireUpgradesCompatibility.available() && JustDireResourceTestSupport.handler(root) != null, "New failure poisoned installation/storage");
            if (variant.equals("settingsMissing")) { require(JustDireGroundStompCompatibility.quote(root) != null, "Settings failure poisoned quotes"); }
            else {
                var source = boots("blazegold", 0); set(source, "stepheight_upgrade_installed", true);
                var passive = MixedBootsFactory.createPair(source, new ItemStack(Items.DIAMOND_BOOTS)).primary();
                set(passive, "stepheight_bindingmode", 2); set(passive, "stepheight_toggle", false);
                set(passive, "ability_bindings", List.of(binding("stepheight", 72, false)));
                player.setItemSlot(EquipmentSlot.FEET, passive);
                require(dispatch(player, 72, false) && passive.getOrDefault(NeoForgeJustDireGroundStompServerTest.<Boolean>component("stepheight_toggle"), false),
                        "Active API failure poisoned an independent supported passive binding");
            }
        }
        record(variant + ": repeated unavailable activation safe/quiet or bounded, independent installation/storage intact");
    }

    private static JustDireActiveNetwork.SettingsRequest request(AbstractContainerMenu menu, int selection, int number, UUID token,
            int operation, String ability, int value, int key, boolean mouse, boolean equipped) {
        return new JustDireActiveNetwork.SettingsRequest(menu.containerId, 36, selection, number, token, operation, ability, value, key, mouse, equipped);
    }
    private static JustDireActiveNetwork.ActivationReply prepared(ServerPlayer player) { return JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(++sequence, 71, false)); }
    private static JustDireActiveNetwork.Activate activatePacket(JustDireActiveNetwork.ActivationReply reply) { return new JustDireActiveNetwork.Activate(reply.sequence(), reply.nonce(), 71, false); }
    private static boolean attempt(ServerPlayer player) {
        var reply = prepared(player); lastPrepared = reply.accepted();
        return lastAccepted = reply.accepted() && JustDireActiveNetwork.activate(player, activatePacket(reply));
    }
    private static List<Double> use(ServerPlayer player, ItemStack root, double[] distances) {
        player.setItemSlot(EquipmentSlot.FEET, root); var mobs = new ArrayList<Mob>();
        try {
            for (double distance : distances) {
                var mob = EntityType.ZOMBIE.create(player.level(), EntitySpawnReason.COMMAND); require(mob != null, "No target");
                mob.setNoAi(true); mob.setNoGravity(true); mob.setPos(player.getX() + distance, player.getY(), player.getZ());
                mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); require(player.level().addFreshEntity(mob), "Target not inserted"); mobs.add(mob);
            }
            attempt(player); return mobs.stream().map(mob -> mob.getDeltaMovement().horizontalDistance()).toList();
        } finally { mobs.forEach(Mob::discard); }
    }
    private static ServerPlayer player(MinecraftServer server, GameType mode) { return JustDireGroundStompTestSupport.player(server, mode); }
    private static ItemStack boots(String tier, int charge) throws Exception { return JustDireGroundStompTestSupport.boots(tier, charge); }
    private static ItemStack root(String tier, int charge) throws Exception {
        var root = MixedBootsFactory.createPair(boots(tier, charge), new ItemStack(Items.DIAMOND_BOOTS)).primary(); configure(root); return root;
    }
    private static void configure(ItemStack root) throws Exception {
        set(root, "groundstomp_upgrade_installed", true); set(root, "groundstomp_bindingmode", 2);
        Object binding = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                .getConstructor(String.class, int.class, boolean.class, boolean.class).newInstance("groundstomp", 71, false, true);
        set(root, "ability_bindings", List.of(binding));
    }
    private static Object record(String name, int ticks, boolean active) throws Exception {
        return Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityCooldown")
                .getConstructor(String.class, int.class, boolean.class).newInstance(name, ticks, active);
    }
    private static DataComponentType<List<Object>> cooldowns() { return component("ability_cooldowns"); }
    private static <T> DataComponentType<T> component(String path) { return JustDireAttributeTestSupport.component(path); }
    private static <T> void set(ItemStack stack, String path, T value) { JustDireAttributeTestSupport.set(stack, path, value); }
    private static int charge(ItemStack root) { return root.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0); }
    private static int timer(ItemStack root) throws Exception { return JustDireGroundStompTestSupport.timer(root); }
    private static void record(String text) { groups++; report.append(text).append('\n'); }
    private static void require(boolean condition, String text) { if (!condition) { throw new AssertionError(text); } }
    private static final class BreakObserver extends ServerPlayer {
        int callbacks; EquipmentSlot brokenSlot; Runnable callback;
        BreakObserver(MinecraftServer server) {
            super(server, server.overworld(), new GameProfile(UUID.randomUUID(), "stomp-break"), ClientInformation.createDefault());
            new ServerGamePacketListenerImpl(server, new net.minecraft.network.Connection(PacketFlow.SERVERBOUND), this, CommonListenerCookie.createInitial(getGameProfile(), false));
            connection.markClientLoaded(); setGameMode(GameType.SURVIVAL); setPos(8, 100, 8); setOnGround(false);
        }
        @Override public void onEquippedItemBroken(Item item, EquipmentSlot slot) {
            callbacks++; brokenSlot = slot; super.onEquippedItemBroken(item, slot); if (callback != null) { callback.run(); }
        }
    }
    private static final class MultipartMob extends Zombie {
        MultipartMob(ServerLevel level) { super(EntityType.ZOMBIE, level); }
        @Override public boolean isMultipartEntity() { return true; }
        @Override public PartEntity<?>[] getParts() { return new PartEntity<?>[0]; }
    }
}
