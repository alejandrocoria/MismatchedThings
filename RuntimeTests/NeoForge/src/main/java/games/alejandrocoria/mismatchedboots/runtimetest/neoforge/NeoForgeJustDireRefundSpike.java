package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireActiveNetwork;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Opt-in feasibility only. Lifecycle credit helpers are explicit fixture calls, not production hooks. */
final class NeoForgeJustDireRefundSpike {
    private static final String MODE = System.getProperty("mismatchedboots.runtimetest.justDireRefundSpike");
    private static final String PREFIX = "just-dire-refund-spike-" + MODE;
    private static final BlockPos STORE = new BlockPos(0, 80, 0);
    private static final List<String> drops = new ArrayList<>();
    private static final StringBuilder report = new StringBuilder();
    private static ServerPlayer observed;
    private static ItemStack reentrant;
    private static boolean cancel, finished;
    private static int groups;

    private NeoForgeJustDireRefundSpike() {}

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (EntityJoinLevelEvent event) -> {
            if (observed != null && event.getEntity() instanceof ItemEntity item && !event.getLevel().isClientSide()) {
                var id = BuiltInRegistries.ITEM.getKey(item.getItem().getItem());
                if (id.getNamespace().equals("justdirethings") && id.getPath().startsWith("upgrade_")) {
                    require(item.getItem().getCount() == 1, "Not a one-item refund"); drops.add(id.getPath().substring("upgrade_".length()));
                    if (reentrant != null) { var empty = reentrant; reentrant = null; empty.hurtAndBreak(1, observed, EquipmentSlot.FEET); }
                }
            }
        });
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ArmorHurtEvent event) -> {
            if (cancel && event.getEntity() == observed) { event.setCanceled(true); }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (finished) { return; } finished = true;
            try {
                var server = event.getServer(); server.setDifficulty(Difficulty.NORMAL, true);
                if (MODE.equals("restart")) { restart(server); }
                else if (MODE.equals("missing")) { missing(server); }
                else { live(server); }
                NetworkAcceptanceFiles.write(PREFIX + "-report.txt", groups + " feasibility groups\n" + report);
                NetworkAcceptanceFiles.write(PREFIX + "-success.txt", "neoforge\n");
            } catch (Throwable failure) { NetworkAcceptanceFiles.failure(PREFIX, failure); }
            finally { observed = null; event.getServer().halt(false); }
        });
    }

    private static void live(MinecraftServer server) throws Exception {
        var a = boot("ferricore", "stepheight"); var b = boot("ferricore", "jumpboost");
        var beforeA = a.copy(); var beforeB = b.copy(); var pair = MixedBootsFactory.createPair(a, b);
        var first = JustDireRefundSpikeCandidate.creation(pair.primary()); var inverse = JustDireRefundSpikeCandidate.creation(pair.inverse());
        require(credits(first).equals(List.of("stepheight")) && credits(inverse).equals(List.of("jumpboost"))
                && JustDireRefundSpikeCandidate.installed(first, "jumpboost") && JustDireRefundSpikeCandidate.installed(inverse, "stepheight")
                && ItemStack.matches(a, beforeA) && ItemStack.matches(b, beforeB), "Owner allocation changed union/inputs");
        direct(server, first, List.of("stepheight")); direct(server, inverse, List.of("jumpboost"));
        pair = MixedBootsFactory.createPair(a, a.copy());
        direct(server, JustDireRefundSpikeCandidate.creation(pair.primary()), List.of("stepheight"));
        direct(server, JustDireRefundSpikeCandidate.creation(pair.inverse()), List.of("stepheight"));
        record("owner allocation: disjoint and shared native purchases; both inverse outputs refund conserved totals after actual direct breaks");

        var root = credited("ferricore", "stepheight", "jumpboost"); codec(server, root);
        last(root); var player = equip(server, root); reentrant = root; hit(player); refunds("stepheight", "jumpboost");
        require(root.isEmpty() && reentrant == null, "Real combat/reentry control did not consume the original");
        record("actual survival combat returns two credited items once; drop-event reentry into the empty root cannot refund again");

        root = credited("ferricore", "stepheight"); last(root); player = equip(server, root); var before = root.copy(); cancel = true;
        try { hit(player); } finally { cancel = false; }
        require(ItemStack.matches(root, before) && drops.isEmpty(), "Cancelled wear refunded or changed state");
        boolean survived = false; server.overworld().getRandom().setSeed(1_234_567);
        for (int attempt = 0; attempt < 32 && !survived; attempt++) {
            root = credited("ferricore", "stepheight"); last(root);
            root.enchant(server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING), 10);
            player = equip(server, root); hit(player); survived = !root.isEmpty();
            refunds(survived ? new String[0] : new String[]{"stepheight"});
        }
        require(survived, "No actual Unbreaking survivor"); record("cancelled and Unbreaking-surviving actual hits refund nothing; real enchanted breaks still refund");

        for (GameType mode : List.of(GameType.CREATIVE, GameType.SPECTATOR)) {
            root = credited("ferricore", "stepheight"); last(root); player = equip(server, root, mode);
            root.hurtAndBreak(2, player, EquipmentSlot.FEET); refunds();
        }
        root = credited("ferricore", "stepheight"); last(root); root.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        player = equip(server, root); before = root.copy(); root.hurtAndBreak(2, player, EquipmentSlot.FEET);
        require(ItemStack.matches(before, root) && drops.isEmpty(), "Unbreakable changed/refunded");
        record("creative/spectator direct requests and Unbreakable do not authorize refunds");

        for (String fault : List.of("replace", "edit", "refill")) {
            root = credited("ferricore", "stepheight"); last(root); player = equip(server, root);
            JustDireRefundSpikeCandidate.faultTarget = root; JustDireRefundSpikeCandidate.fault = fault; JustDireRefundSpikeCandidate.trace.clear();
            root.hurtAndBreak(2, player, EquipmentSlot.FEET); refunds();
            require(JustDireRefundSpikeCandidate.trace.equals(List.of("capture", "foreign-before", "foreign-after", "complete")), "Foreign fault was not inside the capture/completion window: " + JustDireRefundSpikeCandidate.trace);
            if (fault.equals("replace")) { require(player.getItemBySlot(EquipmentSlot.FEET).is(Items.DIAMOND_BOOTS)
                    && player.getItemBySlot(EquipmentSlot.FEET).getDamageValue() == 0, "Stale root changed replacement"); }
            JustDireRefundSpikeCandidate.faultTarget = null; JustDireRefundSpikeCandidate.fault = "none";
            if (fault.equals("refill")) { root.hurtAndBreak(1, player, EquipmentSlot.FEET); refunds("stepheight"); }
            record("real foreign shrink wrapper " + fault + ": stale/edited/unconsumed capture refused; refill retry releases normally");
        }
        for (var invalid : List.of(List.of("stepheight", "stepheight"), List.of("unknown"), List.of("earthquake"))) {
            root = credited("ferricore", "stepheight"); root.set(JustDireRefundSpikeCandidate.CREDITS.get(), invalid);
            last(root); player = equip(server, root); root.hurtAndBreak(2, player, EquipmentSlot.FEET); refunds();
        }
        root = credited("ferricore", "stepheight"); root.remove(JustDireRefundSpikeCandidate.CREDITS.get()); direct(server, root, List.of());
        root = credited("ferricore", "stepheight"); last(root); player = equip(server, ItemStack.EMPTY); root.hurtAndBreak(2, player, EquipmentSlot.FEET); refunds();
        root = credited("ferricore", "stepheight"); last(root); root.setCount(2); player = equip(server, root); root.hurtAndBreak(2, player, EquipmentSlot.FEET); refunds();
        require(root.getCount() == 1, "Invalid count control did not run actual shrink");
        record("unknown/duplicate/uninstalled credit, absent credit, unequipped roots and invalid count never grant a refund");

        root = credited("blazegold", "groundstomp"); root.setDamageValue(root.getMaxDamage() - 25); player = equip(server, root);
        JustDireAttributeTestSupport.set(root, "groundstomp_bindingmode", 2);
        var binding = Class.forName("com.direwolf20.justdirethings.common.items.interfaces.ToolRecords$AbilityBinding")
                .getConstructor(String.class, int.class, boolean.class, boolean.class).newInstance("groundstomp", 71, false, true);
        JustDireAttributeTestSupport.set(root, "ability_bindings", List.of(binding));
        var reply = JustDireActiveNetwork.prepare(player, new JustDireActiveNetwork.Prepare(1, 71, false));
        require(JustDireActiveNetwork.activate(player, new JustDireActiveNetwork.Activate(1, reply.nonce(), 71, false)) && root.isEmpty(), "Production GS did not really break");
        refunds("groundstomp"); record("production GS permit/action breaks at 25 remaining durability and candidate returns its one credit");

        var purchased = installation(server);
        root = credited("celestigem", "stepheight"); root.set(JustDireResourceTestSupport.energyComponent(), 123); root.setDamageValue(31);
        player = equip(server, ItemStack.EMPTY); var smith = menu(server, player, item("template_eclipsealloy"), root.copy(), item("eclipsealloy_ingot"));
        var preview = smith.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
        require(!preview.isEmpty(), "No actual tier preview"); smith.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
        var tier = smith.getCarried().copy(); require(ItemStack.matches(preview, tier) && credits(tier).equals(credits(root))
                && tier.getDamageValue() == 31 && tier.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0) == 123, "Tier take reset credits/wear/FE");
        var dormant = MixedBootsRecomposer.recompose(purchased, new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.IRON_BOOTS));
        require(credits(dormant).equals(List.of("jumpboost")), "Source changes erased root credit"); direct(server, dormant, List.of("jumpboost"));
        record("actual native Celes-to-Eclipse preview/take preserves spent FE/wear/credits; dormant source recomposition retains and refunds its credit");
        require(JustDireRefundSpikeCandidate.captures > 0 && JustDireRefundSpikeCandidate.completions > 0 && JustDireRefundSpikeCandidate.wrappers > 0, "Candidate/competing wrapper was not active");
        persist(server, tier, purchased);
    }

    private static ItemStack installation(MinecraftServer server) throws Exception {
        var root = credited("ferricore"); var player = equip(server, ItemStack.EMPTY); var before = root.copy();
        var smith = menu(server, player, ItemStack.EMPTY, root.copy(), item("upgrade_jumpboost"));
        var preview = JustDireRefundSpikeCandidate.installation(root, smith.getSlot(SmithingMenu.RESULT_SLOT).getItem(), "jumpboost");
        require(ItemStack.matches(before, root) && credits(root).isEmpty(), "Preview spent or credited original");
        smith.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
        require(smith.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty() && smith.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().isEmpty(), "Actual root take did not pay once");
        var purchased = JustDireRefundSpikeCandidate.installation(root, smith.getCarried(), "jumpboost");
        require(ItemStack.matches(preview, purchased), "Candidate native preview/take differs");
        var inherited = JustDireRefundSpikeCandidate.creation(MixedBootsFactory.createPair(new ItemStack(Items.DIAMOND_BOOTS), boot("ferricore", "jumpboost")).primary());
        require(credits(inherited).isEmpty() && JustDireRefundSpikeCandidate.installed(inherited, "jumpboost"), "Wrong inverse owner control");
        var duplicate = menu(server, player, ItemStack.EMPTY, inherited.copy(), item("upgrade_jumpboost"));
        require(duplicate.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty()
                && duplicate.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().getCount() == 1, "Inherited capability allowed a second purchase");
        record("actual native root installation pays one addition; explicit candidate preview/take helper adds one root credit; inherited duplicate stays rejected");
        return purchased;
    }

    private static void codec(MinecraftServer server, ItemStack root) {
        var codec = JustDireRefundSpikeCandidate.CODEC;
        require(codec.parse(JsonOps.INSTANCE, JsonParser.parseString("[\"jumpboost\",\"stepheight\"]")).getOrThrow().equals(List.of("stepheight", "jumpboost")), "Unstable order");
        for (String invalid : List.of("[\"stepheight\",\"stepheight\"]", "[\"unknown\"]", "[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]")) {
            require(codec.parse(JsonOps.INSTANCE, JsonParser.parseString(invalid)).error().isPresent(), "Invalid credit codec accepted");
        }
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        require(ItemStack.matches(root, ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, root).getOrThrow()).getOrThrow()), "Full-stack persistent roundtrip lost credit");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try { ItemStack.STREAM_CODEC.encode(buffer, root); require(ItemStack.matches(root, ItemStack.STREAM_CODEC.decode(buffer)), "Full-stack network codec lost credit"); }
        finally { buffer.release(); }
        record("bounded immutable canonical name list; duplicate/unknown/oversize rejection; complete stack persistent/network codec roundtrips");
    }

    private static void persist(MinecraftServer server, ItemStack tier, ItemStack purchased) {
        var level = server.overworld(); level.setBlock(STORE, Blocks.BARREL.defaultBlockState(), 3);
        var barrel = (BarrelBlockEntity) level.getBlockEntity(STORE); require(barrel != null, "No actual barrel");
        var expected = new JsonArray(); var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        int slot = 0;
        for (var root : List.of(tier, purchased)) { last(root); barrel.setItem(slot++, root.copy()); expected.add(ItemStack.CODEC.encodeStart(ops, root).getOrThrow()); }
        barrel.setChanged(); NetworkAcceptanceFiles.write("just-dire-refund-spike-saved.json", expected.toString());
        server.saveEverything(true, true, true); record("persist two independently encoded credited roots in a real world barrel for a separate JVM");
    }
    private static void restart(MinecraftServer server) throws Exception {
        server.overworld().getChunkAt(STORE); var barrel = (BarrelBlockEntity) server.overworld().getBlockEntity(STORE);
        require(barrel != null, "Actual world store missing");
        var expected = JsonParser.parseString(Files.readString(Path.of(System.getProperty("mismatchedboots.runtimetest.markers"), "just-dire-refund-spike-saved.json"))).getAsJsonArray();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (int slot = 0; slot < 2; slot++) {
            var root = barrel.removeItemNoUpdate(slot); var snapshot = ItemStack.CODEC.parse(ops, expected.get(slot)).getOrThrow();
            require(ItemStack.matches(snapshot, root) && credits(root).equals(List.of(slot == 0 ? "stepheight" : "jumpboost")), "Disk state differs");
            if (slot == 1) { require(!JustDireRefundSpikeCandidate.installed(ModContent.getMixedBootsData(root).orElseThrow().left().source(), "jumpboost"), "Purchase control unexpectedly came from history"); }
            var player = equip(server, root); hit(player); require(root.isEmpty(), "Loaded spent root did not break"); refunds(slot == 0 ? "stepheight" : "jumpboost");
        }
        record("actual second JVM retains complete root FE/wear/history and credits; fresh breaks use root credits, not historical installation");
    }
    private static void missing(MinecraftServer server) throws Exception {
        direct(server, credited("ferricore", "stepheight"), List.of());
        require(JustDireRefundSpikeCandidate.captures == 0 && JustDireRefundSpikeCandidate.completions == 0
                && JustDireRefundSpikeCandidate.wrappers == 0, "Missing hook fixture applied candidate mixins");
        record("candidate hook absent: ordinary actual break remains, zero speculative refund, no hard injection failure");
    }

    private static ItemStack boot(String tier, String... installed) {
        var source = NeoForgeJustDireResourcesTest.boots(tier, 0);
        for (String name : installed) { JustDireAttributeTestSupport.set(source, name + "_upgrade_installed", true); } return source;
    }
    private static ItemStack credited(String tier, String... installed) throws Exception { return JustDireRefundSpikeCandidate.creation(MixedBootsFactory.createPair(boot(tier, installed), new ItemStack(Items.DIAMOND_BOOTS)).primary()); }
    private static List<String> credits(ItemStack root) { return JustDireRefundSpikeCandidate.credits(root); }
    private static void last(ItemStack root) { root.setDamageValue(root.getMaxDamage() - 1); }
    private static ServerPlayer equip(MinecraftServer server, ItemStack root) { return equip(server, root, GameType.SURVIVAL); }
    private static ServerPlayer equip(MinecraftServer server, ItemStack root, GameType mode) { drops.clear(); observed = JustDireGroundStompTestSupport.player(server, mode); observed.setItemSlot(EquipmentSlot.FEET, root); return observed; }
    private static void hit(ServerPlayer player) { require(player.hurtServer(player.level(), player.damageSources().mobAttack(new Zombie(EntityType.ZOMBIE, player.level())), 8), "Real hit rejected"); }
    private static void direct(MinecraftServer server, ItemStack root, List<String> expected) { last(root); var player = equip(server, root); root.hurtAndBreak(2, player, EquipmentSlot.FEET); require(root.isEmpty(), "Actual direct break did not consume root"); refunds(expected.toArray(String[]::new)); }
    private static void refunds(String... expected) { require(drops.stream().sorted().toList().equals(List.of(expected).stream().sorted().toList()), "Wrong drops: " + drops + "; expected=" + List.of(expected)); }
    private static ItemStack item(String path) { return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow()); }
    private static SmithingMenu menu(MinecraftServer server, ServerPlayer player, ItemStack template, ItemStack root, ItemStack addition) { var menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.create(server.overworld(), BlockPos.ZERO)); menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(template); menu.getSlot(SmithingMenu.BASE_SLOT).set(root); menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(addition); return menu; }
    private static void record(String text) { groups++; report.append(text).append('\n'); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
