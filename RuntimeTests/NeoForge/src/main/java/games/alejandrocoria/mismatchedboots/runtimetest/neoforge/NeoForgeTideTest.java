package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Test coordinator only. Owners never link to this class or to each other. */
final class NeoForgeTideTest {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final String MODE = System.getProperty("mismatchedboots.runtimetest.tideOwners");
    private static final String HEAD = "games.alejandrocoria.mismatchedboots.runtimetest.tidehead.TideHeadFixture";
    private static final String FEET = "games.alejandrocoria.mismatchedboots.runtimetest.tide.TideProductionProbe";
    private static final List<String> TRACE = new ArrayList<>();
    private static ServerLevel level;
    private static ServerPlayer control, mixed, observed;
    private static boolean finished;
    private static boolean raining, veto;
    private static int installed, ready, cases, ticks, attempts;

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, NeoForgeTideTest::applicable);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, NeoForgeTideTest::added);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, NeoForgeTideTest::expired);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer()));
    }

    private static boolean tide(MobEffectInstance effect) {
        return effect.is(MobEffects.WATER_BREATHING) || effect.is(MobEffects.STRENGTH);
    }

    private static void applicable(MobEffectEvent.Applicable event) {
        if (event.getEntity() == observed && tide(event.getEffectInstance())) {
            if (veto && event.getEffectInstance().is(MobEffects.STRENGTH)) {
                event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
            }
            TRACE.add("applicable:" + state(event.getEffectInstance()) + ":" + event.getResult()
                    + ":source=" + (event.getEffectSource() == null));
            attempts++;
        }
    }

    private static void added(MobEffectEvent.Added event) {
        if (event.getEntity() == observed && tide(event.getEffectInstance())) {
            TRACE.add("added:" + state(event.getEffectInstance()) + ":old=" + state(event.getOldEffectInstance())
                    + ":source=" + (event.getEffectSource() == null));
        }
    }

    private static void expired(MobEffectEvent.Expired event) {
        if (event.getEntity() == observed && tide(event.getEffectInstance())) { TRACE.add("expired:" + state(event.getEffectInstance())); }
    }

    private static String state(MobEffectInstance effect) {
        return effect == null ? "none" : MobEffectInstance.CODEC.encodeStart(
                RegistryOps.create(JsonOps.INSTANCE, level.registryAccess()), effect).getOrThrow().toString();
    }

    private static void run(MinecraftServer server) {
        if (finished) { return; }
        boolean damageOwnsShutdown = false;
        try {
            installed = MODE.equals("feet") ? 8 : 9;
            boolean apiUnavailable = MODE.startsWith("api");
            ready = MODE.equals("late") || apiUnavailable || MODE.startsWith("base") || MODE.startsWith("predicate") || MODE.equals("extraWork") ? 0 : MODE.equals("missingHead") || MODE.startsWith("headReceiver") ? 8
                    : MODE.equals("publicationChanged") || MODE.startsWith("feetReceiver") ? 1 : installed;
            require(ModList.get().isLoaded("tide_head_owner") == ((installed & 1) != 0), "HEAD artifact presence");
            require(ModList.get().isLoaded("mismatchedboots_tide_test") == ((installed & 8) != 0), "Production instrumentation presence");
            for (int bit : new int[]{1, 8}) {
                String owner = bit == 1 ? HEAD : FEET;
                if ((installed & bit) == 0) {
                    try { Class.forName(owner, false, NeoForgeTideTest.class.getClassLoader()); throw new AssertionError("Absent owner class available: " + owner); }
                    catch (ClassNotFoundException expected) { /* Physically absent, not just disabled. */ }
                } else {
                    String prefix = bit == 1 ? "tideHead$" : "mismatchedboots$tide";
                    String entryGetter = prefix + (bit == 8 ? "EntryReady" : "entryReady");
                    String baseGetter = prefix + (bit == 8 ? "BaseReady" : "baseReady");
                    String armorGetter = prefix + (bit == 8 ? "ArmorType" : "armorType");
                    require(diagnostic(bit, bit == 1 ? "apiAvailable" : "ownerApiAvailable") == (apiUnavailable ? 0 : 1), "Own early API selection " + owner);
                    if (apiUnavailable) {
                        missingMethod(Item.class, baseGetter);
                        if (!MODE.equals("apiAbsent")) {
                            Class<?> nativeClass = nativeArmor(bit == 1 ? 0 : 3, "blue").getClass();
                            missingMethod(nativeClass, entryGetter);
                            missingMethod(nativeClass, armorGetter);
                        }
                    } else {
                        Item receiver = nativeArmor(bit == 1 ? 0 : 3, "blue");
                        int transformedReady = MODE.contains("Receiver") || MODE.startsWith("base") ? installed : ready;
                        require((boolean) receiver.getClass().getMethod(entryGetter).invoke(receiver) == ((transformedReady & bit) != 0), "Own entry readiness " + owner);
                        require((boolean) receiver.getClass().getMethod(baseGetter).invoke(receiver) == !MODE.startsWith("base"), "Own base readiness " + owner);
                    }
                }
            }
            level = server.overworld();
            level.getChunk(0, 0);
            for (BlockPos pos : BlockPos.betweenClosed(0, -60, 0, 2, -57, 2)) { level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState()); }
            level.getWeatherData().setRaining(false); level.setRainLevel(0);
            control = player(server, "OwnersControl"); mixed = player(server, "OwnersMixed");
            if (MODE.equals("apiAbsent")) { absence(); }
            else {
                if (MODE.equals("both")) {
                    // Full gameplay matrix once. Other processes exercise their distinct transformation/failure.
                    for (int pieces = 1; pieces < 16; pieces++) {
                        for (boolean wet : List.of(false, true)) {
                            for (boolean inverse : List.of(false, true)) {
                                for (boolean both : List.of(false, true)) { scenario(pieces, wet, inverse, both); }
                            }
                        }
                    }
                } else {
                    for (int pieces : new int[]{1, 8, 9, 15}) {
                        for (boolean wet : List.of(false, true)) { scenario(pieces, wet, false, false); }
                    }
                    if (MODE.equals("feet")) {
                        for (boolean inverse : List.of(false, true)) {
                            for (boolean both : List.of(false, true)) { scenario(8, true, inverse, both); }
                        }
                    }
                }
                // Native armor must retain all four contributions even when both owners are rejected.
                for (boolean wet : List.of(false, true)) {
                    reset();
                    for (int i = 0; i < 4; i++) {
                        control.setItemSlot(SLOTS[i], new ItemStack(nativeArmor(i, "blue")));
                        mixed.setItemSlot(SLOTS[i], new ItemStack(nativeArmor(i, "blue")));
                    }
                    compare(wet, 0, "ordinary full set"); cases++;
                    require(mixed.hasEffect(MobEffects.WATER_BREATHING), "Native full set keeps Water Breathing");
                    require(mixed.hasEffect(MobEffects.STRENGTH) == wet, "Native full set wetness gate");
                    if (wet) { require(mixed.getEffect(MobEffects.STRENGTH).getAmplifier() == nativeCount(15) - 1, "Native full set keeps native contributions"); }
                }
                negatives();
                if (MODE.equals("both")) { productionEffects(); }
            }
            int headWarnings = diagnostic(1, "warnings"), feetWarnings = diagnostic(8, "ownerWarnings");
            Class.forName(FEET).getMethod("verifyMutation").invoke(null);
            require(headWarnings == (!MODE.equals("apiAbsent") && (installed & 1) != 0 && (ready & 1) == 0 ? 1 : 0), "Bounded HEAD warning");
            require(feetWarnings == (!MODE.equals("apiAbsent") && (installed & 8) != 0 && (ready & 8) == 0 ? 1 : 0), "Bounded FEET warning");
            int headBindings = diagnostic(1, "bindingAttempts"), feetBindings = diagnostic(8, "ownerBindingAttempts");
            require(headBindings == (!apiUnavailable && (installed & 1) != 0 ? 1 : 0), "HEAD binding cached, not retried after rejection");
            require(feetBindings == (!apiUnavailable && (installed & 8) != 0 ? 1 : 0), "FEET binding cached, not retried after rejection");
            NetworkAcceptanceFiles.write("tide-owners-report.txt", "owners=" + MODE + "; production FEET=true; installed=" + installed + "; ready=" + ready
                    + "; scenarios=" + cases + "; player ticks=" + ticks + "; Applicable attempts=" + attempts
                    + "; HEAD activations=" + diagnostic(1, "activations") + "; FEET activations=" + diagnostic(8, "ownerActivations")
                    + "; HEAD warnings=" + headWarnings + "; FEET warnings=" + feetWarnings
                    + "; HEAD bindings=" + headBindings + "; FEET bindings=" + feetBindings + "\n"
                    + (apiUnavailable ? "early optional API rejection; no installed entry/base getters; no receiver binding\n"
                            : "own entry/base getters independent of receiver binding; exact native traces/states; no original HEAD/FEET anchor when both present\n")
                    + "unchanged roots/source envelopes; cached binding failures never retry or invoke a fallback\n"
                    + "TEST-ONLY: HEAD has its own data, receiver, tick, verifier and constants; coordinator requires production Mismatched Boots but HEAD JAR does not\n");
            NetworkAcceptanceFiles.write("tide-owners-success.txt", "neoforge\n");
            if (Boolean.getBoolean("mismatchedboots.runtimetest.tideDamageCoexistence")) {
                NeoForgeIceAndFireDamageTest.register();
                damageOwnsShutdown = true;
            }
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure("tide-owners", failure); }
        finally { observed = null; finished = true; if (!damageOwnsShutdown) { server.halt(false); } }
    }

    private static void missingMethod(Class<?> type, String name) throws Exception {
        try { type.getMethod(name); throw new AssertionError("Rejected API installed " + name); }
        catch (NoSuchMethodException expected) { /* No readiness/accessor selection, not merely a false getter. */ }
    }

    private static void absence() throws Exception {
        require(!ModList.get().isLoaded("iceandfire"), "Optional Ice and Fire JAR physically absent");
        try {
            Class.forName("com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem", false, NeoForgeTideTest.class.getClassLoader());
            throw new AssertionError("Absent optional native class available");
        } catch (ClassNotFoundException expected) { /* Check only after selection; inspection never loads it. */ }
        for (boolean wet : List.of(false, true)) {
            for (String kind : List.of("equipped", "carried", "wrongSlot", "bare", "count")) {
                reset();
                for (int index : new int[]{0, 3}) {
                    ItemStack root = root(index, false, false);
                    switch (kind) {
                        case "carried" -> mixed.getInventory().add(root);
                        case "wrongSlot" -> mixed.setItemSlot(index == 0 ? EquipmentSlot.FEET : EquipmentSlot.HEAD, root);
                        case "bare" -> mixed.setItemSlot(SLOTS[index], new ItemStack(root.getItem()));
                        case "count" -> { root.setCount(2); mixed.setItemSlot(SLOTS[index], root); }
                        default -> mixed.setItemSlot(SLOTS[index], root);
                    }
                    require(!(boolean) Class.forName(index == 0 ? HEAD : "games.alejandrocoria.mismatchedboots.compatibility.iceandfire.IceAndFireTideCompatibility").getMethod("recognizes",
                            net.minecraft.world.entity.Entity.class, EquipmentSlot.class).invoke(null, mixed, SLOTS[index]), "Absent API recognizes no root");
                }
                compare(wet, 0, "absent API " + kind); cases++;
                require(!mixed.hasEffect(MobEffects.WATER_BREATHING) && !mixed.hasEffect(MobEffects.STRENGTH), "Absent API has no native effects");
            }
        }
    }

    private static void scenario(int pieces, boolean wet, boolean inverse, boolean both) throws Exception {
        reset();
        for (int i = 0; i < 4; i++) {
            int bit = 1 << i;
            if ((pieces & bit) == 0) { continue; }
            if ((installed & bit) != 0) {
                mixed.setItemSlot(SLOTS[i], root(i, inverse, both));
                if ((ready & bit) != 0) { control.setItemSlot(SLOTS[i], new ItemStack(nativeArmor(i, "blue"))); }
            } else {
                control.setItemSlot(SLOTS[i], new ItemStack(nativeArmor(i, "blue")));
                mixed.setItemSlot(SLOTS[i], new ItemStack(nativeArmor(i, "blue")));
            }
        }
        compare(wet, ready & pieces, "matrix pieces=" + pieces + ",inverse=" + inverse + ",both=" + both);
        int count = nativeCount(pieces & ~(installed & ~ready));
        require(mixed.hasEffect(MobEffects.WATER_BREATHING) == (count > 0), "Water contribution count");
        require(mixed.hasEffect(MobEffects.STRENGTH) == (count > 0 && wet), "Strength wetness/count");
        if (wet && count > 0) { require(mixed.getEffect(MobEffects.STRENGTH).getAmplifier() == count - 1, "One contribution per owner, not per source"); }
        cases++;
    }

    private static int nativeCount(int mask) {
        int count = Integer.bitCount(mask);
        if (MODE.equals("predicateReplaced") || MODE.equals("predicateChanged")) {
            if ((mask & 8) != 0) { count--; }
            if (MODE.equals("predicateChanged") && (mask & 4) != 0) { count++; }
        }
        return count;
    }

    private static void productionEffects() throws Exception {
        // Every color/orientation and one/two Tide sources must dispatch the SAME blue receiver.
        for (String color : List.of("blue", "bronze", "deepblue", "green", "purple", "red", "teal")) {
            for (boolean inverse : List.of(false, true)) {
                for (boolean both : List.of(false, true)) {
                    reset();
                    control.setItemSlot(EquipmentSlot.FEET, new ItemStack(nativeArmor(3, color)));
                    ItemStack tide = new ItemStack(nativeArmor(3, color));
                    ItemStack other = new ItemStack(both ? nativeArmor(3, "red") : Items.IRON_BOOTS);
                    mixed.setItemSlot(EquipmentSlot.FEET, MixedBootsFactory.createPair(inverse ? other : tide, inverse ? tide : other).primary());
                    compare(true, 8, "production color " + color); cases++;
                }
            }
        }
        for (int duration : List.of(15, 150, -1)) {
            scenario(15, true, true, false);
            for (ServerPlayer player : List.of(control, mixed)) {
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, duration, 5, true, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, duration, 2, true, true, true));
            }
            for (int step = 0; step < 10; step++) { compare(true, installed, "production stronger/hidden duration=" + duration); }
            require(mixed.getEffect(MobEffects.STRENGTH).getAmplifier() == (duration == 15 ? 3 : 5), "Production preserves native stronger/hidden effects");
            cases++;
        }
        scenario(15, true, false, false);
        for (ServerPlayer player : List.of(control, mixed)) {
            for (int i = 0; i < 3; i++) { player.setItemSlot(SLOTS[i], ItemStack.EMPTY); }
        }
        for (int step = 0; step < 10; step++) { compare(true, 8, "production fewer pieces/hidden weaker tail"); }
        for (int step = 0; step < 20; step++) { compare(false, 8, "production dry Strength expiry"); }
        require(!mixed.hasEffect(MobEffects.STRENGTH), "Dry native Strength expires");
        for (ServerPlayer player : List.of(control, mixed)) { player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY); }
        for (int step = 0; step < 20; step++) { compare(false, 0, "production equipment removal/expiry"); }
        require(!mixed.hasEffect(MobEffects.WATER_BREATHING), "Native Water Breathing expires after removal"); cases++;

        reset();
        control.setItemSlot(EquipmentSlot.FEET, new ItemStack(nativeArmor(3, "blue")));
        mixed.setItemSlot(EquipmentSlot.FEET, root(3, false, false));
        level.getWeatherData().setRaining(true); level.getWeatherData().setRainTime(6000); level.setRainLevel(1);
        raining = true;
        try { compare(true, 8, "production rain without immersion"); require(mixed.hasEffect(MobEffects.STRENGTH), "Rain activates native Strength"); cases++; }
        finally { raining = false; level.getWeatherData().setRaining(false); level.setRainLevel(0); }
        veto = true;
        try {
            reset();
            control.setItemSlot(EquipmentSlot.FEET, new ItemStack(nativeArmor(3, "blue")));
            mixed.setItemSlot(EquipmentSlot.FEET, root(3, false, false));
            compare(true, 8, "production native effect veto");
            require(!mixed.hasEffect(MobEffects.STRENGTH), "Production respects native effect veto"); cases++;
        } finally { veto = false; }
        for (boolean useMixed : List.of(false, true)) {
            var mob = EntityType.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            require(mob != null, "Non-player fixture created");
            ItemStack boots = useMixed ? root(3, false, false) : new ItemStack(nativeArmor(3, "blue"));
            mob.setItemSlot(EquipmentSlot.FEET, boots);
            int before = diagnostic(8, "ownerActivations");
            for (int tick = 0; tick < 3; tick++) { mob.tick(); }
            require(diagnostic(8, "ownerActivations") == before && !mob.hasEffect(MobEffects.WATER_BREATHING)
                    && !mob.hasEffect(MobEffects.STRENGTH) && mob.getItemBySlot(EquipmentSlot.FEET) == boots, "Non-player has no production activation");
            mob.discard(); cases++;
        }
        reset();
        for (ServerPlayer player : List.of(control, mixed)) { player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(nativeArmor(1, "blue"))); }
        ItemStack wrongType = new ItemStack(nativeArmor(0, "blue"));
        wrongType.set(DataComponents.EQUIPPABLE, Items.IRON_BOOTS.components().get(DataComponents.EQUIPPABLE));
        mixed.setItemSlot(EquipmentSlot.FEET, MixedBootsFactory.createPair(wrongType, new ItemStack(Items.IRON_BOOTS)).primary());
        compare(true, 0, "source claims FEET component but native ArmorType is HEAD"); cases++;
        reset();
        for (ServerPlayer player : List.of(control, mixed)) {
            player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(nativeArmor(3, "blue")));
        }
        control.setItemSlot(EquipmentSlot.FEET, new ItemStack(nativeArmor(3, "blue")));
        mixed.setItemSlot(EquipmentSlot.FEET, root(3, false, false));
        compare(true, 8, "native wrong-slot class contribution preserved");
        require(mixed.getEffect(MobEffects.STRENGTH).getAmplifier() == 1, "Native wrong-slot class still counts"); cases++;
    }

    private static void negatives() throws Exception {
        for (int i : new int[]{0, 3}) {
            if ((installed & (1 << i)) == 0) { continue; }
            for (String kind : List.of("count", "bare", "wrongSlot", "carried", "foreign")) {
                reset();
                for (ServerPlayer player : List.of(control, mixed)) { player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(nativeArmor(1, "blue"))); }
                ItemStack root = root(i, false, false);
                switch (kind) {
                    case "count" -> root.setCount(2);
                    case "bare" -> root = new ItemStack(root.getItem());
                    case "wrongSlot" -> mixed.setItemSlot(i == 0 ? EquipmentSlot.FEET : EquipmentSlot.HEAD, root);
                    case "carried" -> mixed.getInventory().add(root);
                    case "foreign" -> { ItemStack spoof = new ItemStack(i == 0 ? Items.IRON_HELMET : Items.IRON_BOOTS); spoof.applyComponents(root.getComponents()); root = spoof; }
                }
                if (!kind.equals("wrongSlot") && !kind.equals("carried")) { mixed.setItemSlot(SLOTS[i], root); }
                compare(true, 0, "invalid own root " + i + "/" + kind); cases++;
            }
        }
    }

    private static ItemStack root(int index, boolean inverse, boolean both) {
        Item first = MODE.equals("apiAbsent") ? (index == 0 ? Items.DIAMOND_HELMET : Items.DIAMOND_BOOTS) : nativeArmor(index, "blue");
        Item second = both ? nativeArmor(index, "red") : index == 0 ? Items.IRON_HELMET : Items.IRON_BOOTS;
        if (inverse) { Item swap = first; first = second; second = swap; }
        if (index == 3) { return MixedBootsFactory.createPair(new ItemStack(first), new ItemStack(second)).primary(); }
        ItemStack root = new ItemStack(item("tide_head_owner:mixed_head"));
        CompoundTag tag = new CompoundTag();
        tag.putString("primary_head", BuiltInRegistries.ITEM.getKey(first).toString());
        tag.putString("secondary_head", BuiltInRegistries.ITEM.getKey(second).toString());
        root.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return root;
    }

    private static Item nativeArmor(int index, String color) {
        return item("iceandfire:tide_" + color + "_" + new String[]{"helmet", "chestplate", "leggings", "boots"}[index]);
    }

    private static Item item(String id) { return BuiltInRegistries.ITEM.get(Identifier.parse(id)).orElseThrow().value(); }

    private static int diagnostic(int bit, String method) throws Exception {
        return (installed & bit) == 0 ? 0 : (int) Class.forName(bit == 1 ? HEAD : FEET).getMethod(method).invoke(null);
    }

    private static ServerPlayer player(MinecraftServer server, String name) {
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault()) {
            @Override public GameType gameMode() { return GameType.SURVIVAL; }
        };
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
        GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
        return player; // Not world-inserted: only doTick below advances these two players.
    }

    private static void reset() {
        observed = null;
        for (ServerPlayer player : List.of(control, mixed)) {
            player.removeAllEffects(); player.getInventory().clearContent();
            for (EquipmentSlot slot : SLOTS) { player.setItemSlot(slot, ItemStack.EMPTY); }
        }
    }

    private static void compare(boolean wet, int activeRoots, String label) throws Exception {
        ItemStack[] roots = new ItemStack[4], snapshots = new ItemStack[4];
        for (int i = 0; i < 4; i++) {
            roots[i] = mixed.getItemBySlot(SLOTS[i]); snapshots[i] = roots[i].copy();
            if (MODE.equals("baseMutate") && !snapshots[i].isEmpty()
                    && snapshots[i].getItem().getClass().getName().equals("com.iafenvoy.iceandfire.item.armor.SeaSerpentArmorItem")) {
                snapshots[i].set(DataComponents.REPAIR_COST, snapshots[i].getOrDefault(DataComponents.REPAIR_COST, 0) + 3);
            }
        }
        for (int tick = 0; tick < 3; tick++) {
            List<String> nativeTrace = step(control, wet, 0);
            String water = state(control.getEffect(MobEffects.WATER_BREATHING)), strength = state(control.getEffect(MobEffects.STRENGTH));
            require(nativeTrace.equals(step(mixed, wet, activeRoots)), label + ": native hook mismatch");
            require(water.equals(state(mixed.getEffect(MobEffects.WATER_BREATHING))) && strength.equals(state(mixed.getEffect(MobEffects.STRENGTH))), label + ": effect state mismatch");
            require(mixed.isInWaterOrRain() == wet && control.isInWaterOrRain() == wet, label + ": actual wetness");
        }
        for (int i = 0; i < 4; i++) { require(mixed.getItemBySlot(SLOTS[i]) == roots[i] && ItemStack.matches(roots[i], snapshots[i]), label + ": equipment/source mutation"); }
    }

    private static List<String> step(ServerPlayer player, boolean wet, int roots) throws Exception {
        player.setPos(raining ? 8.5 : wet ? 1.5 : 8.5, raining ? -55.5 : -59.5, raining ? 8.5 : wet ? 1.5 : 8.5); player.setDeltaMovement(Vec3.ZERO); player.setAirSupply(300);
        int head = diagnostic(1, "activations"), feet = diagnostic(8, "ownerActivations");
        observed = player; TRACE.clear(); player.doTick(); ticks++; observed = null;
        require(diagnostic(1, "activations") - head == ((roots & 1) != 0 ? 1 : 0), "Exactly one own HEAD activation");
        require(diagnostic(8, "ownerActivations") - feet == ((roots & 8) != 0 ? 1 : 0), "Exactly one own FEET activation");
        return List.copyOf(TRACE);
    }

    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
