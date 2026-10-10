package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireUpgradesCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Native characterization/access fixtures, with an opt-in phase B server-action handoff; no production hooks. */
public final class NeoForgeJustDireGroundStompSpike {
    private static boolean finished;
    private static int warmup;
    private static int groups;
    private static final StringBuilder report = new StringBuilder();
    private static Class<?> abilityType;
    private static Class<?> toolType;
    private static Object stomp;
    private static Method action;
    private static Method canUse;
    private static Method value;
    private static Method cooldown;
    private static Method tickCooldowns;

    private NeoForgeJustDireGroundStompSpike() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer()));
        if (Boolean.getBoolean("mismatchedboots.runtimetest.justDireGroundStompActionSpike")) {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> NeoForgeJustDireGroundStompActionSpike.tick(event.getServer()));
        }
    }

    private static void run(MinecraftServer server) {
        if (finished) { return; }
        // A generated chunk alone does not expose newly added entities to real world queries.
        // Let normal server ticks finish forced-chunk/entity-manager activation, without blocking.
        if (++warmup == 1) { server.overworld().setChunkForced(0, 0, true); }
        if (warmup < 200 && !server.overworld().areEntitiesActuallyLoadedAndTicking(new net.minecraft.world.level.ChunkPos(0, 0))) { return; }
        finished = true;
        boolean actionSpike = Boolean.getBoolean("mismatchedboots.runtimetest.justDireGroundStompActionSpike");
        try {
            require(warmup < 200, "Fixture chunk/entities never became ticking");
            abilityType = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.Ability");
            toolType = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToggleableTool");
            stomp = abilityType.getField("GROUNDSTOMP").get(null);
            action = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.AbilityMethods")
                    .getMethod("groundstomp", Level.class, net.minecraft.world.entity.player.Player.class, ItemStack.class);
            canUse = toolType.getMethod("canUseAbilityAndDurability", ItemStack.class, abilityType);
            value = toolType.getMethod("getToolValue", ItemStack.class, String.class);
            cooldown = toolType.getMethod("getAnyCooldown", ItemStack.class, abilityType);
            tickCooldowns = toolType.getMethod("tickCooldowns", Level.class, ItemStack.class, net.minecraft.world.entity.player.Player.class);
            require((int) abilityType.getMethod("getFeCost").invoke(stomp) == 5_000
                    && (int) abilityType.getMethod("getDurabilityCost").invoke(stomp) == 25, "Native costs changed");
            if (actionSpike) { NeoForgeJustDireGroundStompActionSpike.start(server); return; }
            nativeControls(server);
            mixedQuotesAndPayments(server);
            stateBoundaries(server);
            nativePacketBoundaries(server);
            NetworkAcceptanceFiles.write("just-dire-ground-stomp-spike-report.txt", groups
                    + " server spike groups; native world effects; test-only mixed read-only quotes/payment; no mixed activation/UI acceptance\n" + report);
            NetworkAcceptanceFiles.write("just-dire-ground-stomp-spike-success.txt", "neoforge\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure(actionSpike ? "just-dire-ground-stomp-action-spike" : "just-dire-ground-stomp-spike", failure);
            if (actionSpike) { NeoForgeJustDireGroundStompActionSpike.abort(server); }
        } finally {
            // Phase B owns the normal-tick lifecycle handoff and releases the same ticket on exit.
            if (!actionSpike) { server.overworld().setChunkForced(0, 0, false); server.halt(false); }
        }
    }

    private static void nativeControls(MinecraftServer server) throws ReflectiveOperationException {
        var player = player(server, GameType.SURVIVAL);
        for (String tier : List.of("celestigem", "eclipsealloy")) {
            for (int initial : new int[]{0, 4_999, 5_000, 6_000}) {
                var boot = boots(tier, initial);
                boolean accepted = initial >= 5_000;
                var result = nativeUse(player, boot, new double[]{2});
                require(!result.returned && (result.motion > 0) == accepted
                        && timer(boot) == (accepted ? 200 : -1)
                        && charge(boot) == (accepted ? initial - 5_000 : initial), "Native FE threshold/effect/payment " + tier + "/" + initial
                        + ": result=" + result + "; timer=" + timer(boot) + "; FE=" + charge(boot));
            }
            record(tier + ": native 0/4999/5000/6000 FE thresholds; full base required; one bill; false even after effect");
        }
        for (int remaining : new int[]{24, 25, 26}) {
            var boot = boots("blazegold", 0);
            boot.setDamageValue(boot.getMaxDamage() - remaining);
            var result = nativeUse(player, boot, new double[]{2});
            require((result.motion > 0) == (remaining >= 25) && !result.returned, "Native durability eligibility " + remaining);
            if (remaining == 25) { require(boot.isEmpty(), "Exact durability payment did not break native boots"); }
            else { require(boot.getMaxDamage() - boot.getDamageValue() == (remaining == 24 ? 24 : 1), "Native durability payment changed"); }
        }
        record("Blazegold: 24 rejects; 25 accepts and breaks; 26 accepts leaving one durability");

        var charged = boots("eclipsealloy", 20_000);
        var empty = nativeUse(player, charged, new double[]{});
        require(!empty.returned && charge(charged) == 15_000 && timer(charged) == 200, "Empty native activation did not cost/cool down");
        var before = charged.copy();
        require(nativeUse(player, charged, new double[]{2}).motion == 0 && ItemStack.matches(before, charged), "Cooldown rejection spent/applied effect");
        var inventoryTick = charged.getItem().getClass().getMethod("inventoryTick", ItemStack.class,
                net.minecraft.server.level.ServerLevel.class, net.minecraft.world.entity.Entity.class, EquipmentSlot.class);
        inventoryTick.invoke(charged.getItem(), charged, server.overworld(), player, EquipmentSlot.MAINHAND);
        require(timer(charged) == 200, "Unequipped native boots ticked cooldown");
        inventoryTick.invoke(charged.getItem(), charged, server.overworld(), player, EquipmentSlot.FEET);
        require(timer(charged) == 199, "Equipped native boots did not tick cooldown");
        for (int tick = 0; tick < 199; tick++) { tickCooldowns.invoke(null, server.overworld(), charged, player); }
        require(timer(charged) == -1 && nativeUse(player, charged, new double[]{2}).motion > 0 && charge(charged) == 10_000,
                "Native cooldown expiry did not permit the next single activation");
        record("empty-target payment; repeat rejected unchanged; native non-FEET pause/FEET tick; expiry permits one new bill");

        var multiple = boots("blazegold", 0);
        var result = nativeUse(player, multiple, new double[]{2, -2, 0, 3.01, 4});
        require(result.motions.get(0) > 0 && result.motions.get(1) > 0 && result.motions.get(2) == 0
                && result.motions.get(3) > 0 && result.motions.get(4) == 0 && multiple.getDamageValue() == 25,
                "Native target/range/coincident or single-bill semantics changed");
        var low = boots("eclipsealloy", 6_000); set(low, "groundstomp_value", 1);
        var high = boots("eclipsealloy", 6_000); set(high, "groundstomp_value", 99);
        double lowMotion = nativeUse(player, low, new double[]{2}).motion;
        double highMotion = nativeUse(player, high, new double[]{2}).motion;
        require(lowMotion > 0 && Math.abs(highMotion / lowMotion - 5) < 1e-7, "Native strength/tier clamp changed");
        record("airborne player; AABB-overlapping mobs knocked away, including center 3.01; coincident/outside targets unaffected; one durability bill; slider 1/99 clamps to 1/5");

        for (String control : List.of("tool_enabled", "groundstomp_toggle", "groundstomp_upgrade_installed")) {
            var disabled = boots("eclipsealloy", 6_000); set(disabled, control, false);
            var snapshot = disabled.copy();
            require(nativeUse(player, disabled, new double[]{2}).motion == 0 && ItemStack.matches(snapshot, disabled), "Native disabled control mutated " + control);
        }
        Map<?, ?> config = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config").getField("AVAILABLE_ABILITY_MAP").get(null);
        var flag = (ModConfigSpec.BooleanValue) config.get(stomp);
        boolean previous = flag.get();
        try {
            flag.set(false);
            var disabled = boots("eclipsealloy", 6_000); var snapshot = disabled.copy();
            require(nativeUse(player, disabled, new double[]{2}).motion == 0 && ItemStack.matches(snapshot, disabled), "Native disabled config activated");
        } finally { flag.set(previous); }
        record("tool/ability/installation false and native config disabled: no effect, charge or cooldown mutation");

        for (int level : new int[]{1, 3, 10}) {
            var enchanted = boots("eclipsealloy", 6_000);
            enchanted.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), level);
            require(nativeUse(player, enchanted, new double[]{2}).motion > 0
                    && charge(enchanted) == 6_000 - discounted(5_000, level), "Native FE Unbreaking bill differs " + level);
        }
        var insufficient = boots("eclipsealloy", 4_999);
        insufficient.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 10);
        require(nativeUse(player, insufficient, new double[]{2}).motion == 0 && charge(insufficient) == 4_999, "Discount lowered base eligibility");
        for (GameType mode : List.of(GameType.CREATIVE, GameType.SPECTATOR)) {
            var exempt = player(server, mode);
            for (int initial : new int[]{4_999, 5_000}) {
                var boot = boots("eclipsealloy", initial);
                require((nativeUse(exempt, boot, new double[]{2}).motion > 0) == (initial == 5_000)
                        && charge(boot) == initial, "Native mode exemption bypassed base eligibility or paid " + mode);
            }
        }
        record("FE Unbreaking 1/3/10 discounts payment only; creative/spectator keep full base eligibility but do not pay");
    }

    private static void mixedQuotesAndPayments(MinecraftServer server) throws ReflectiveOperationException {
        var player = player(server, GameType.SURVIVAL);
        var pair = MixedBootsFactory.createPair(boots("blazegold", 0), boots("eclipsealloy", 12_000));
        for (var root : List.of(pair.primary(), pair.inverse())) {
            var snapshot = root.copy();
            var full = quote(root, false);
            require(full != null && full.powered && full.value == 5 && full.bill == 5_000, "Charged quote did not select powered strength/cost");
            require(quote(root, true).equals(full) && ItemStack.matches(snapshot, root), "Queries differ or mutate mixed root");
            player.setItemSlot(EquipmentSlot.FEET, root);
            require(!pay(player, root, full, 7_000) && ItemStack.matches(snapshot, root), "Incomplete FE extraction did not roll back");
            player.setItemSlot(EquipmentSlot.FEET, root.copy());
            require(!pay(player, root, full, full.bill) && ItemStack.matches(snapshot, root), "Stale equipped identity allowed payment");
            player.setItemSlot(EquipmentSlot.FEET, root);
            root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE), 1);
            require(pay(player, root, full, full.bill) && charge(root) == 1_000 && player.getItemBySlot(EquipmentSlot.FEET) == root,
                    "Direct cursed-root payment failed or replaced equipped item");
            // The curse was added after the original snapshot; compare wear/provenance separately.
            require(root.getDamageValue() == snapshot.getDamageValue()
                    && ModContent.getMixedBootsData(root).equals(ModContent.getMixedBootsData(snapshot)), "FE payment changed wear/provenance");
            var fallback = quote(root, false);
            require(fallback != null && !fallback.powered && fallback.value == 2 && fallback.bill == 25
                    && quote(root, true) == null, "Candidate policies do not expose depleted-FE choice");
            set(root, "groundstomp_upgrade_installed", false);
            require(quote(root, false) == null, "Historical source installation bypassed root flag");
            record("both orientations: 6000 FE -> powered quote 5/5000; failed partial extraction rollback; stale-root rejection; Binding Curse direct payment -> 1000; usable fallback 2/25 vs fixed-provider rejection; root false denies sources");
        }
        var root = MixedBootsFactory.createPair(boots("blazegold", 0), new ItemStack(Items.DIAMOND_BOOTS)).primary();
        root.setDamageValue(root.getMaxDamage() - 25);
        player.setItemSlot(EquipmentSlot.FEET, root);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE));
        var quote = quote(root, false);
        require(quote != null && !quote.powered && pay(player, root, quote, quote.bill)
                && player.getItemBySlot(EquipmentSlot.FEET).isEmpty() && player.getMainHandItem().is(Items.STONE),
                "Prototype durability bill did not break the actual feet root safely");
        record("test-only durability payment consumes exact remaining 25 on actual FEET, breaks mixed root, leaves hand unchanged; no mixed world effects");
    }

    private static void stateBoundaries(MinecraftServer server) throws ReflectiveOperationException {
        var bindingType = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding");
        var binding = bindingType.getConstructor(String.class, int.class, boolean.class, boolean.class)
                .newInstance("groundstomp", 71, false, true);
        var source = boots("blazegold", 0);
        set(source, "ability_bindings", List.of(binding)); set(source, "groundstomp_bindingmode", 2);
        set(source, "groundstomp_custom_setting", 1);
        toolType.getMethod("addCooldown", ItemStack.class, abilityType, int.class, boolean.class).invoke(null, source, stomp, 200, false);
        var other = boots("eclipsealloy", 12_000);
        var pair = MixedBootsFactory.createPair(source, other);
        var player = player(server, GameType.SURVIVAL);
        var clickable = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.LeftClickableTool");
        var bindingsFor = clickable.getMethod("getCustomBindingListFor", ItemStack.class, int.class, boolean.class, net.minecraft.world.entity.player.Player.class);
        for (var root : List.of(pair.primary(), pair.inverse())) {
            // Configure root explicitly: no UI or server packet bridge is claimed by these writes.
            set(root, "ability_bindings", List.of(binding)); set(root, "groundstomp_bindingmode", 2);
            set(root, "groundstomp_custom_setting", 1);
            player.setItemSlot(EquipmentSlot.FEET, root);
            require(((List<?>) bindingsFor.invoke(null, root, 71, false, player)).isEmpty(), "Native binding equipment classification unexpectedly accepts mixed FEET");
            require(bindingMatches(root, player, 71, false), "Local FEET-only matcher did not accept root binding");
            require(((List<?>) bindingsFor.invoke(null, root, 72, false, player)).isEmpty(), "Unassigned key selected ability");
            require(!bindingMatches(root, player, 72, false) && !bindingMatches(root, player, 71, true), "Local matcher accepted wrong key/device");
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            require(((List<?>) bindingsFor.invoke(null, root, 71, false, player)).isEmpty(), "Require-equipped binding bypassed equipment");
            require(!bindingMatches(root, player, 71, false), "Local matcher bypassed actual FEET equipment");
            var before = root.copy();
            var decoded = ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                    ItemStack.CODEC.encodeStart(server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE), root).getOrThrow()).getOrThrow();
            require(ItemStack.matches(before, decoded), "Native binding/cooldown root components do not round-trip");
            var data = ModContent.getMixedBootsData(root).orElseThrow();
            var replaced = MixedBootsRecomposer.recompose(root, other, source);
            List<String> changed = new ArrayList<>();
            for (String path : List.of("ability_bindings", "groundstomp_bindingmode", "groundstomp_custom_setting", "ability_cooldowns")) {
                DataComponentType<Object> type = JustDireAttributeTestSupport.component(path);
                if (!java.util.Objects.equals(root.get(type), replaced.get(type))) { changed.add(path); }
            }
            require(ItemStack.matches(before, root) && data.equals(ModContent.getMixedBootsData(root).orElseThrow()), "Recomposition mutated root or snapshots");
            preserveActiveSettings(root, replaced);
            for (String path : List.of("ability_bindings", "groundstomp_bindingmode", "groundstomp_custom_setting", "ability_cooldowns")) {
                DataComponentType<Object> type = JustDireAttributeTestSupport.component(path);
                require(java.util.Objects.equals(root.get(type), replaced.get(type)), "Test-only preservation failed " + path);
                root.remove(type);
            }
            var absent = MixedBootsRecomposer.recompose(root, other, source);
            preserveActiveSettings(root, absent);
            for (String path : List.of("ability_bindings", "groundstomp_bindingmode", "groundstomp_custom_setting", "ability_cooldowns")) {
                require(!absent.has(JustDireAttributeTestSupport.component(path)), "Test-only preservation reimported absent " + path);
            }
            record("native require-equipped matcher rejects mixed FEET; local root matcher accepts only actual FEET/key/device; codec roundtrip; current generic recomposition changed="
                    + changed + "; test-only override preserves current/equal/absent active data");
        }
        var nativeTimer = boots("blazegold", 0);
        toolType.getMethod("addCooldown", ItemStack.class, abilityType, int.class, boolean.class).invoke(null, nativeTimer, stomp, 2, false);
        var root = MixedBootsFactory.createPair(nativeTimer, new ItemStack(Items.DIAMOND_BOOTS)).primary();
        require(timer(root) == 2, "Creation did not retain owner native timer for characterization");
        var initial = root.copy();
        root.getItem().inventoryTick(root, server.overworld(), player, EquipmentSlot.FEET);
        require(timer(root) == 2 && ItemStack.matches(initial, root), "Production unexpectedly activated/ticked Ground Stomp");
        tickCooldowns.invoke(null, server.overworld(), root, player);
        require(timer(root) == 1 && timer(nativeTimer) == 2, "Simple native timer write mutated immutable source/list");
        tickCooldowns.invoke(null, server.overworld(), root, player);
        require(timer(root) == -1 && timer(nativeTimer) == 2, "Native simple cooldown did not expire independently on actual root");
        record("production root has no active tick integration; controlled native non-active timer writes expire root independently; source timer stays 2; active timer branch remains unproved");
    }

    private static void nativePacketBoundaries(MinecraftServer server) throws ReflectiveOperationException {
        var player = player(server, GameType.SURVIVAL);
        // Actual native handlers, synchronous fixture context only: not a network transport/UI proof.
        var contextType = net.neoforged.neoforge.network.handling.IPayloadContext.class;
        var context = java.lang.reflect.Proxy.newProxyInstance(contextType.getClassLoader(), new Class<?>[]{contextType},
                (proxy, method, args) -> switch (method.getName()) {
                    case "player" -> player;
                    case "enqueueWork" -> { ((Runnable) args[0]).run(); yield java.util.concurrent.CompletableFuture.completedFuture(null); }
                    default -> throw new AssertionError("Unexpected native context call " + method.getName());
                });
        var settingType = Class.forName("com.direwolf20.justdirethings.common.network.data.ToggleToolSlotPayload");
        var setting = settingType.getConstructor(String.class, int.class, int.class, int.class)
                .newInstance("groundstomp", 0, 2, 1);
        var bindingType = Class.forName("com.direwolf20.justdirethings.common.network.data.ToggleToolLeftRightClickPayload");
        var binding = bindingType.getConstructor(int.class, String.class, int.class, int.class, boolean.class, boolean.class)
                .newInstance(0, "groundstomp", 2, 71, false, false);
        var clickType = Class.forName("com.direwolf20.justdirethings.common.network.data.LeftClickPayload");
        var click = clickType.getConstructor(int.class, boolean.class, net.minecraft.core.BlockPos.class, int.class, int.class, int.class, boolean.class)
                .newInstance(0, true, net.minecraft.core.BlockPos.ZERO, -1, 0, 71, false);
        var nativeBoot = boots("eclipsealloy", 6_000);
        var root = MixedBootsFactory.createPair(nativeBoot, new ItemStack(Items.DIAMOND_BOOTS)).primary();
        for (var stack : List.of(nativeBoot, root)) {
            var before = stack.copy();
            player.getInventory().setItem(0, stack);
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
            invokePacket("ToggleToolSlotPacket", settingType, setting, context);
            invokePacket("ToggleToolLeftRightClickPacket", bindingType, binding, context);
            invokePacket("LeftClickPacket", clickType, click, context);
            if (stack == nativeBoot) {
                require((int) value.invoke(null, stack, "groundstomp") == 1 && charge(stack) == 1_000 && timer(stack) == 200,
                        "Native handler control did not assign/configure/activate once");
            } else { require(ItemStack.matches(before, stack), "Native packets unexpectedly write or activate mixed roots"); }
        }
        record("actual native settings/binding/activation handlers: native inventory-enabled control configures and pays once; mixed root untouched; synchronous context, not real packets/UI");
    }

    private static void invokePacket(String name, Class<?> payloadType, Object payload, Object context) throws ReflectiveOperationException {
        var type = Class.forName("com.direwolf20.justdirethings.common.network.handler." + name);
        type.getMethod("handle", payloadType, net.neoforged.neoforge.network.handling.IPayloadContext.class)
                .invoke(type.getField("INSTANCE").get(null), payload, context);
    }

    static boolean bindingMatches(ItemStack root, ServerPlayer player, int key, boolean mouse) throws ReflectiveOperationException {
        if (root.isEmpty() || root.getCount() != 1 || player.getItemBySlot(EquipmentSlot.FEET) != root
                || root.getOrDefault(JustDireAttributeTestSupport.<Integer>component("groundstomp_bindingmode"), 0) != 2) { return false; }
        for (Object binding : root.getOrDefault(JustDireAttributeTestSupport.<List<?>>component("ability_bindings"), List.of())) {
            var type = binding.getClass();
            if (type.getMethod("abilityName").invoke(binding).equals("groundstomp")
                    && (int) type.getMethod("key").invoke(binding) == key
                    && (boolean) type.getMethod("isMouse").invoke(binding) == mouse) { return true; }
        }
        return false;
    }

    static void preserveActiveSettings(ItemStack previous, ItemStack output) {
        for (String path : List.of("ability_bindings", "groundstomp_bindingmode", "groundstomp_custom_setting", "ability_cooldowns")) {
            DataComponentType<Object> type = JustDireAttributeTestSupport.component(path);
            output.remove(type);
            if (previous.has(type)) { output.set(type, previous.get(type)); }
        }
    }

    /** Query-only native views. The two policies are candidates, not production behavior. */
    static Quote quote(ItemStack root, boolean fixedProvider) throws ReflectiveOperationException {
        var data = ModContent.getMixedBootsData(root).orElseThrow();
        Quote selected = null;
        for (String tier : List.of("eclipsealloy", "celestigem", "blazegold")) {
            for (var source : List.of(data.left().source(), data.right().source())) {
                if (!source.is(boots(tier, 0).getItem())) { continue; }
                require(JustDireUpgradesCompatibility.overlayInstallation(source, root), "Root installation query API absent");
                for (String path : List.of("tool_enabled", "groundstomp_toggle", "groundstomp_value")) {
                    DataComponentType<Object> type = JustDireAttributeTestSupport.component(path);
                    source.remove(type);
                    if (root.has(type)) { source.set(type, root.get(type)); }
                }
                boolean powered = !tier.equals("blazegold");
                if (powered) { source.set(JustDireResourceTestSupport.energyComponent(), charge(root)); }
                else {
                    source.set(DataComponents.MAX_DAMAGE, root.getMaxDamage());
                    source.setDamageValue(root.getDamageValue());
                }
                boolean usable = (boolean) canUse.invoke(source.getItem(), source, stomp);
                if (fixedProvider && !usable) { return null; }
                if (!usable) { continue; }
                int strength = (int) value.invoke(null, source, "groundstomp");
                var candidate = new Quote(strength, powered, powered ? 5_000 : 25);
                if (fixedProvider) { return candidate; }
                if (selected == null || strength > selected.value) { selected = candidate; }
            }
        }
        return selected;
    }

    /** Payment access proof only: does not dispatch effects, establish cooldown or accept a client request. */
    private static boolean pay(ServerPlayer player, ItemStack root, Quote quote, int requested) {
        if (root.isEmpty() || root.getCount() != 1 || player.getItemBySlot(EquipmentSlot.FEET) != root) { return false; }
        if (quote.powered) {
            var handler = JustDireResourceTestSupport.handler(root);
            if (handler == null) { return false; }
            try (var transaction = Transaction.openRoot()) {
                if (handler.extract(requested, transaction) != requested || player.getItemBySlot(EquipmentSlot.FEET) != root) { return false; }
                transaction.commit();
            }
        } else { root.hurtAndBreak(requested, player, EquipmentSlot.FEET); }
        return true;
    }

    static NativeResult nativeUse(ServerPlayer player, ItemStack boot, double[] distances) throws ReflectiveOperationException {
        player.setItemSlot(EquipmentSlot.FEET, boot);
        List<Mob> targets = new ArrayList<>();
        try {
            for (double distance : distances) {
                var mob = EntityType.ZOMBIE.create(player.level(), EntitySpawnReason.COMMAND);
                require(mob != null, "Could not create native target");
                mob.setNoAi(true); mob.setNoGravity(true);
                mob.setPos(player.getX() + distance, player.getY(), player.getZ());
                mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
                require(player.level().addFreshEntity(mob), "Native target was not added to world");
                require(player.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox()).contains(mob),
                        "Native target is not visible to actual world query");
                targets.add(mob);
            }
            boolean returned = (boolean) action.invoke(null, player.level(), player, boot);
            List<Double> motions = targets.stream().map(mob -> mob.getDeltaMovement().horizontalDistance()).toList();
            return new NativeResult(returned, motions.isEmpty() ? 0 : motions.getFirst(), motions);
        } finally { targets.forEach(Mob::discard); }
    }

    static ServerPlayer player(MinecraftServer server, GameType mode) {
        var player = NeoForgeJustDireNegateFallTest.player(server, mode);
        server.overworld().getChunk(0, 0);
        player.setPos(8, 100, 8); player.setOnGround(false); player.setDeltaMovement(Vec3.ZERO);
        return player;
    }

    static ItemStack boots(String tier, int charge) {
        var stack = NeoForgeJustDireResourcesTest.boots(tier, charge);
        set(stack, "groundstomp_upgrade_installed", true);
        return stack;
    }

    static int timer(ItemStack stack) throws ReflectiveOperationException { return (int) cooldown.invoke(null, stack, stomp); }
    private static int charge(ItemStack stack) { return stack.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0); }
    private static <T> void set(ItemStack stack, String path, T setting) { JustDireAttributeTestSupport.set(stack, path, setting); }
    private static int discounted(int bill, int level) { return (int) Math.max(0, bill - bill * Math.min(1, level * .1)); }
    private static void record(String text) { groups++; report.append(text).append('\n'); }
    private static void require(boolean condition, String text) { if (!condition) { throw new AssertionError(text); } }
    record Quote(int value, boolean powered, int bill) {}
    record NativeResult(boolean returned, double motion, List<Double> motions) {}
}
