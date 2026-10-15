package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireRefundCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireRefundCredits;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireUpgradesCompatibility;
import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentRule;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleSet;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.menu.MismatchedWorkbenchMenu;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Production state/menu/actual-break acceptance. No candidate helpers, component or hook. */
final class NeoForgeJustDireRefundsTest {
    private static final String VARIANT = System.getProperty("mismatchedboots.runtimetest.justDireRefunds");
    private static final List<String> NAMES = List.of("stepheight", "jumpboost", "groundstomp", "negatefalldamage", "earthquake");
    private static final StringBuilder report = new StringBuilder();
    private static boolean finished;
    private static int groups, upgradeDrops;

    private NeoForgeJustDireRefundsTest() {}

    static void register() {
        JustDireRefundBreakTest.register();
        NeoForge.EVENT_BUS.addListener((EntityJoinLevelEvent event) -> {
            if (event.getEntity() instanceof ItemEntity item && BuiltInRegistries.ITEM.getKey(item.getItem().getItem())
                    .toString().startsWith("justdirethings:upgrade_")) { upgradeDrops++; }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (finished) { return; } finished = true;
            try {
                var server = event.getServer();
                if (VARIANT.equals("absent")) { absent(); }
                else if (!VARIANT.equals("live") && !VARIANT.equals("itemsMissing") && !VARIANT.equals("metadataMissing")) {
                    for (String result : JustDireRefundFaults.exercise(server, VARIANT)) { record(result); }
                } else {
                    boolean bindingMissing = VARIANT.equals("itemsMissing") || VARIANT.equals("metadataMissing");
                    require(JustDireUpgradesCompatibility.available() && JustDireRefundCompatibility.available() != bindingMissing, "Unexpected production binding state");
                    var player = JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL);
                    if (bindingMissing) { bindingFailure(server, player); }
                    else { allocation(server, player); installation(server, player); lifecycle(server, player); configured(server); codecs(server); }
                    var root = MixedBootsFactory.createPair(boot("ferricore", "stepheight"), new ItemStack(Items.DIAMOND_BOOTS)).primary();
                    root.setDamageValue(root.getMaxDamage() - 1); player.setItemSlot(EquipmentSlot.FEET, root);
                    root.hurtAndBreak(2, player, EquipmentSlot.FEET);
                    require(root.isEmpty() && upgradeDrops == (bindingMissing ? 0 : 1), "Wrong actual direct-break refund");
                    record("actual credited-root break returns one item only with the production binding/hook");
                    if (VARIANT.equals("live")) { for (String result : JustDireRefundBreakTest.live(server)) { record(result); } }
                }
                NetworkAcceptanceFiles.write("just-dire-refunds-report.txt", groups + " production credit groups\n" + report);
                NetworkAcceptanceFiles.write("just-dire-refunds-success.txt", "neoforge\n");
            } catch (Throwable failure) { NetworkAcceptanceFiles.failure("just-dire-refunds", failure); }
            finally { event.getServer().halt(false); }
        });
    }

    private static void allocation(MinecraftServer server, ServerPlayer player) {
        var vanilla = new ItemStack(Items.DIAMOND_BOOTS);
        var step = boot("ferricore", "stepheight");
        var jump = boot("ferricore", "jumpboost");
        for (var inputs : List.of(List.of(step, vanilla), List.of(step, step.copy()), List.of(step, jump), List.of(vanilla, new ItemStack(Items.IRON_BOOTS)))) {
            for (boolean reverse : List.of(false, true)) {
                var a = inputs.get(reverse ? 1 : 0); var b = inputs.get(reverse ? 0 : 1);
                var menu = new MismatchedWorkbenchMenu(1, player.getInventory(), ContainerLevelAccess.create(server.overworld(), BlockPos.ZERO));
                player.getInventory().clearContent(); var beforeA = a.copy(); var beforeB = b.copy();
                menu.getSlot(0).set(a.copy()); menu.getSlot(1).set(b.copy());
                var first = menu.getSlot(2).getItem().copy(); var second = menu.getSlot(3).getItem().copy();
                require(!first.isEmpty() && !second.isEmpty(), "No real Workbench preview");
                for (int retry = 0; retry < 3; retry++) { menu.slotsChanged(menu.getSlot(0).container); }
                require(ItemStack.matches(first, menu.getSlot(2).getItem()) && ItemStack.matches(second, menu.getSlot(3).getItem()), "Preview regenerated credit");
                require(credits(first).equals(purchases(a)) && credits(second).equals(purchases(b)), "Owner allocation changed");
                for (String name : List.of("stepheight", "jumpboost")) {
                    boolean expected = purchases(a).contains(name) || purchases(b).contains(name);
                    require(installed(first, name) == expected && installed(second, name) == expected, "Credit allocation changed capability union");
                }
                fillInventory(player);
                require(menu.quickMoveStack(player, 2).isEmpty() && !menu.isCommitted()
                        && ItemStack.matches(a, menu.getSlot(0).getItem()) && ItemStack.matches(b, menu.getSlot(1).getItem()), "Failed Workbench take spent inputs");
                player.getInventory().clearContent();
                menu.clicked(2, 0, ContainerInput.PICKUP, player);
                require(ItemStack.matches(first, menu.getCarried()) && menu.getSlot(0).getItem().isEmpty() && menu.getSlot(1).getItem().isEmpty(), "First take did not consume exactly once");
                require(ItemStack.matches(second, menu.quickMoveStack(player, 3)) && menu.getSlot(3).getItem().isEmpty()
                        && player.getInventory().getNonEquipmentItems().stream().anyMatch(stack -> ItemStack.matches(second, stack)), "Inverse shift-click differs");
                require(ItemStack.matches(beforeA, a) && ItemStack.matches(beforeB, b), "Workbench mutated native inputs");
            }
        }
        record("eight actual two-output Workbench transactions: both orders, single/shared/disjoint/no purchases, preview retries, blocked takes and inverse shift-click");
        var forged = boot("ferricore", "stepheight", "earthquake");
        forged.set(JustDireRefundCompatibility.component(), new JustDireRefundCredits(List.of("jumpboost")));
        var root = MixedBootsFactory.createPair(forged, vanilla).primary();
        require(credits(root).equals(List.of("stepheight")) && !installed(root, "earthquake"), "Forged input credit or tier-ineligible flag imported");
        var forgedVanilla = vanilla.copy(); forgedVanilla.set(JustDireRefundCompatibility.component(), new JustDireRefundCredits(List.of("earthquake")));
        require(credits(MixedBootsFactory.createPair(forgedVanilla, new ItemStack(Items.IRON_BOOTS)).primary()).isEmpty(), "Vanilla source forged entitlement");
        record("native forged own credit and permanently ineligible installation do not create entitlement");
    }

    private static void installation(MinecraftServer server, ServerPlayer player) {
        var root = MixedBootsFactory.createPair(boot("eclipsealloy"), new ItemStack(Items.DIAMOND_BOOTS)).primary();
        root.setDamageValue(31); root.set(JustDireResourceTestSupport.energyComponent(), 123);
        for (String name : NAMES) {
            player.getInventory().clearContent(); var before = root.copy(); var addition = item("upgrade_" + name);
            int additionCount = Math.min(2, addition.getMaxStackSize()); addition.setCount(additionCount);
            var menu = menu(server, player, ItemStack.EMPTY, root.copy(), addition);
            var preview = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy();
            require(!preview.isEmpty() && installed(preview, name) && credits(preview).contains(name), "Production recipe did not grant purchased credit " + name);
            for (int retry = 0; retry < 3; retry++) { menu.createResult(); }
            require(ItemStack.matches(preview, menu.getSlot(SmithingMenu.RESULT_SLOT).getItem()) && ItemStack.matches(before, root), "Smithing preview mutated input/credit");
            for (int slot = SmithingMenu.RESULT_SLOT + 1; slot < menu.slots.size(); slot++) { menu.getSlot(slot).set(new ItemStack(Items.STONE, 64)); }
            require(menu.quickMoveStack(player, SmithingMenu.RESULT_SLOT).isEmpty()
                    && ItemStack.matches(before, menu.getSlot(SmithingMenu.BASE_SLOT).getItem())
                    && menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().getCount() == additionCount, "Blocked installation paid");
            player.getInventory().clearContent();
            ItemStack taken;
            if (name.equals("jumpboost")) { taken = menu.quickMoveStack(player, SmithingMenu.RESULT_SLOT); }
            else { menu.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player); taken = menu.getCarried(); }
            require(ItemStack.matches(preview, taken) && menu.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty()
                    && menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().getCount() == additionCount - 1, "Installation did not pay once");
            require(taken.getDamageValue() == 31 && taken.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0) == 123
                    && ModContent.getMixedBootsData(taken).equals(ModContent.getMixedBootsData(before)), "Installation changed FE/wear/history");
            root = taken.copy();
        }
        require(credits(root).equals(NAMES), "Five installations did not retain exactly five canonical credits");
        var inherited = MixedBootsFactory.createPair(new ItemStack(Items.DIAMOND_BOOTS), boot("ferricore", "jumpboost")).primary();
        var duplicate = menu(server, player, ItemStack.EMPTY, inherited.copy(), item("upgrade_jumpboost"));
        duplicate.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
        require(credits(inherited).isEmpty() && installed(inherited, "jumpboost") && duplicate.getCarried().isEmpty()
                && duplicate.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().getCount() == 1, "Uncredited inherited capability sold a duplicate credit");
        record("five actual root installations: production preview/take equality, blocked/repeated previews, one addition per upgrade, shift-click, FE/wear/history and inherited duplicate rejection");
    }

    private static void lifecycle(MinecraftServer server, ServerPlayer player) {
        var root = MixedBootsFactory.createPair(boot("celestigem", "stepheight"), new ItemStack(Items.DIAMOND_BOOTS)).primary();
        root.setDamageValue(31); root.set(JustDireResourceTestSupport.energyComponent(), 123);
        root.set(DataComponents.CUSTOM_NAME, Component.literal("Credit lifecycle")); JustDireAttributeTestSupport.set(root, "tool_enabled", false);
        var smith = menu(server, player, item("template_eclipsealloy"), root.copy(), item("eclipsealloy_ingot"));
        var preview = smith.getSlot(SmithingMenu.RESULT_SLOT).getItem().copy(); smith.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
        require(!preview.isEmpty() && ItemStack.matches(preview, smith.getCarried()) && credits(preview).equals(List.of("stepheight"))
                && preview.getDamageValue() == 31 && preview.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0) == 123
                && preview.get(DataComponents.CUSTOM_NAME).equals(root.get(DataComponents.CUSTOM_NAME))
                && !preview.get(JustDireAttributeTestSupport.<Boolean>component("tool_enabled")), "Actual tier take lost live credit/other state");
        for (var state : new JustDireRefundCredits[]{new JustDireRefundCredits(List.of("stepheight")), new JustDireRefundCredits(List.of()), null}) {
            root.remove(JustDireRefundCompatibility.component()); if (state != null) { root.set(JustDireRefundCompatibility.component(), state); }
            var dormant = MixedBootsRecomposer.recompose(root, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
            var changedOwner = boot("celestigem", "jumpboost");
            changedOwner.set(JustDireRefundCompatibility.component(), new JustDireRefundCredits(List.of("earthquake")));
            var restored = MixedBootsRecomposer.recompose(dormant, changedOwner, new ItemStack(Items.DIAMOND_BOOTS));
            require(java.util.Objects.equals(state, dormant.get(JustDireRefundCompatibility.component()))
                    && java.util.Objects.equals(state, restored.get(JustDireRefundCompatibility.component())), "Dormancy/restoration reimported root credit");
        }
        var defaults = ComponentRulesLifecycle.serverRules(); var declarations = new LinkedHashMap<>(defaults.components().rules());
        declarations.put(Identifier.fromNamespaceAndPath("mismatchedboots", "jdt_refundable_upgrades"), ComponentRule.of(ComponentPolicy.OMIT));
        var omit = new ComponentRules(defaults.durability(), defaults.repairCost(), new ComponentRuleSet(declarations));
        var configured = MixedBootsFactory.createPair(boot("ferricore", "stepheight"), new ItemStack(Items.DIAMOND_BOOTS),
                new AttributeRuleSet(AttributePolicy.COMBINE, Map.of()), omit).primary();
        require(credits(configured).equals(List.of("stepheight")), "Generic OMIT suppressed actual owner entitlement");
        record("real tier take preserves credits/FE/wear/controls/name; dormant, explicit empty and absence survive loss/return of native sources; generic OMIT cannot override creation");
    }

    private static void configured(MinecraftServer server) throws ReflectiveOperationException {
        var flags = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config").getField("AVAILABLE_ABILITY_MAP").get(null);
        var flag = (ModConfigSpec.BooleanValue) flags.entrySet().stream().filter(entry -> ((Enum<?>) entry.getKey()).name().equals("JUMPBOOST")).findFirst().orElseThrow().getValue();
        boolean previous = flag.get();
        try {
            flag.set(false);
            var root = MixedBootsFactory.createPair(boot("ferricore", "jumpboost"), new ItemStack(Items.DIAMOND_BOOTS)).primary();
            require(credits(root).equals(List.of("jumpboost")) && installed(root, "jumpboost"), "Disabled config erased purchase");
            var player = JustDireGroundStompTestSupport.player(server, GameType.SURVIVAL);
            var rejected = menu(server, player, ItemStack.EMPTY, MixedBootsFactory.createPair(boot("ferricore"), new ItemStack(Items.DIAMOND_BOOTS)).primary(), item("upgrade_jumpboost"));
            require(rejected.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty(), "Credit support bypassed native disabled installation");
        } finally { flag.set(previous); }
        record("disabled native ability config retains input purchase but still rejects a new installation");
    }

    private static void codecs(MinecraftServer server) {
        var root = MixedBootsFactory.createPair(boot("eclipsealloy", "earthquake", "stepheight"), new ItemStack(Items.DIAMOND_BOOTS)).primary();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        require(ItemStack.matches(root, ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, root).getOrThrow()).getOrThrow()), "Persistent whole root lost credit");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, root); require(ItemStack.matches(root, ItemStack.STREAM_CODEC.decode(buffer)), "Network whole root lost credit");
            for (String invalid : List.of("[\"stepheight\",\"stepheight\"]", "[\"unknown\"]", "[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]")) {
                require(JustDireRefundCredits.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(invalid)).error().isPresent(), "Invalid persisted credit accepted");
                buffer.clear(); ByteBufCodecs.fromCodec(Codec.STRING.listOf()).encode(buffer, JsonParser.parseString(invalid).getAsJsonArray().asList().stream().map(value -> value.getAsString()).toList());
                boolean rejected = false;
                try { JustDireRefundCompatibility.component().streamCodec().decode(buffer); }
                catch (RuntimeException expected) { rejected = true; }
                require(rejected, "Invalid network credit accepted");
            }
        } finally { buffer.release(); }
        record("complete production-root persistent/network codecs retain credits; invalid names, duplicates and bounds rejected by both credit decoders (not actual packets)");
    }

    private static void bindingFailure(MinecraftServer server, ServerPlayer player) {
        var root = MixedBootsFactory.createPair(boot("blazegold", "groundstomp"), new ItemStack(Items.DIAMOND_BOOTS)).primary();
        require(credits(root).equals(List.of("groundstomp")) && JustDireGroundStompCompatibility.quote(root) != null, "Missing refund item disabled independent creation/GS");
        var smith = menu(server, player, ItemStack.EMPTY, root.copy(), item("upgrade_jumpboost"));
        smith.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player); var purchased = smith.getCarried();
        require(!purchased.isEmpty() && credits(purchased).equals(List.of("jumpboost", "groundstomp")), "Refund binding failure disabled installation");
        var data = ModContent.getMixedBootsData(purchased).orElseThrow();
        require(credits(MixedBootsRecomposer.recompose(purchased, data.right().source(), data.left().source())).equals(credits(purchased)), "Binding failure erased credit");
        record("real missing refund item/metadata lookup: one scoped warning; independent installed binding, owner creation, paid root installation, GS quote and live preservation remain functional");
    }

    private static void absent() {
        require(!JustDireUpgradesCompatibility.available() && !JustDireRefundCompatibility.available(), "Absent JDT bound optional APIs");
        var root = MixedBootsFactory.createPair(new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.IRON_BOOTS)).primary();
        require(!root.has(JustDireRefundCompatibility.component()), "Absent integration imported native state");
        root.set(JustDireRefundCompatibility.component(), new JustDireRefundCredits(List.of("jumpboost")));
        var rebuilt = MixedBootsRecomposer.recompose(root, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
        require(credits(rebuilt).equals(List.of("jumpboost")), "Optional absence erased local stored entitlement");
        record("JDT-absent startup: own component registers without optional classes; vanilla creation and local credit preservation remain functional");
    }

    private static List<String> credits(ItemStack root) { var value = root.get(JustDireRefundCompatibility.component()); return value == null ? List.of() : value.names(); }
    private static List<String> purchases(ItemStack source) { return NAMES.stream().filter(name -> source.getItem() != Items.DIAMOND_BOOTS && source.getItem() != Items.IRON_BOOTS && installed(source, name)).toList(); }
    private static boolean installed(ItemStack root, String name) { return root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component(name + "_upgrade_installed"), false); }
    private static ItemStack boot(String tier, String... names) { var root = NeoForgeJustDireResourcesTest.boots(tier, 0); for (String name : names) { JustDireAttributeTestSupport.set(root, name + "_upgrade_installed", true); } return root; }
    private static ItemStack item(String path) { return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow()); }
    private static SmithingMenu menu(MinecraftServer server, ServerPlayer player, ItemStack template, ItemStack root, ItemStack addition) { var menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.create(server.overworld(), BlockPos.ZERO)); menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(template); menu.getSlot(SmithingMenu.BASE_SLOT).set(root); menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(addition); return menu; }
    private static void fillInventory(ServerPlayer player) { for (int slot = 0; slot < player.getInventory().getNonEquipmentItems().size(); slot++) { player.getInventory().setItem(slot, new ItemStack(Items.STONE, 64)); } }
    private static void record(String text) { groups++; report.append(text).append('\n'); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
