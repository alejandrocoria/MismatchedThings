package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireUpgradesCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Map;

/** Production root-ownership acceptance against real recipes and native gameplay consumers. */
public final class NeoForgeJustDireRootUpgradeTest {
    private static boolean finished;
    private static int groups;
    private static final StringBuilder report = new StringBuilder();
    private NeoForgeJustDireRootUpgradeTest() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void tick(MinecraftServer server) {
        if (finished) { return; }
        finished = true;
        try {
            var player = NeoForgeJustDireNegateFallTest.player(server, GameType.SURVIVAL);
            String guard = System.getProperty("mismatchedboots.runtimetest.justDireRootUpgradeGuard", "");
            if (guard.isEmpty()) {
                configuredCreation(server);
                lifecycle(server, player);
                attributes(server, player);
                gameplay(server);
            } else { guard(server, player, guard); }
            NetworkAcceptanceFiles.write("just-dire-root-upgrades-report.txt", groups + " root-upgrade production groups\n" + report);
            NetworkAcceptanceFiles.write("just-dire-root-upgrades-success.txt", "neoforge\n");
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure("just-dire-root-upgrades", failure); }
        finally { server.halt(false); }
    }

    private static void configuredCreation(MinecraftServer server) throws ReflectiveOperationException {
        Map<?, ?> flags = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config").getField("AVAILABLE_ABILITY_MAP").get(null);
        var flag = (ModConfigSpec.BooleanValue) flags.entrySet().stream()
                .filter(entry -> ((Enum<?>) entry.getKey()).name().equals("JUMPBOOST")).findFirst().orElseThrow().getValue();
        boolean previous = flag.get();
        ItemStack configured;
        try {
            flag.set(false);
            var preinstalled = boots("ferricore", 0);
            preinstalled.set(flag("jumpboost"), true);
            configured = MixedBootsFactory.createPair(preinstalled, new ItemStack(Items.DIAMOND_BOOTS)).primary();
            require(installed(configured, "jumpboost"), "Disabled config destroyed input installation");
            jump(server, configured, 0);
        } finally { flag.set(previous); }
        jump(server, configured, 1);
        record("disabled native config retains imported flags without enabling Jump; reenabling restores Jump");
    }

    private static void guard(MinecraftServer server, ServerPlayer player, String variant) {
        boolean missing = variant.equals("apiMissing");
        require(JustDireUpgradesCompatibility.available() != missing, "Root API fixture did not select its intended binding state");
        var powered = boots("eclipsealloy", 2_000);
        var passive = boots("ferricore", 0);
        passive.set(flag("jumpboost"), true);
        var pair = MixedBootsFactory.createPair(powered, passive);
        for (var root : List.of(pair.primary(), pair.inverse())) {
            root.set(flag("jumpboost"), true);
            root.set(flag("negatefalldamage"), true);
            root.setDamageValue(31);
            var before = root.copy();
            for (int repeat = 0; repeat < 3; repeat++) {
                var addition = item("upgrade_stepheight");
                var smith = menu(server, player, ItemStack.EMPTY, root.copy(), addition);
                require(output(smith).isEmpty() && ItemStack.matches(before, smith.getSlot(SmithingMenu.BASE_SLOT).getItem())
                        && addition.getCount() == 1, "Failed root installation fell through to source assembly or spent inputs");
                smith.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
                require(smith.getCarried().isEmpty() && !smith.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty()
                        && smith.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().getCount() == 1, "Rejected take consumed inputs");
            }
            require(ItemStack.matches(before, root) && JustDireResourceTestSupport.handler(root).getAmountAsInt() == 1_000,
                    "Upgrade failure modified live FE/root state");
            var nativeResult = output(menu(server, player, ItemStack.EMPTY, powered.copy(), item("upgrade_stepheight")));
            require(!nativeResult.isEmpty() && installed(nativeResult, "stepheight"), "Root failure disabled native installation");
            require(nativeResult.has(DataComponents.CUSTOM_NAME) != missing, "Recipe shape fixture did not change its native assembly");
            var rebuilt = MixedBootsRecomposer.recompose(root, data(root).left().source(), data(root).right().source());
            require(installed(rebuilt, "jumpboost") && installed(rebuilt, "negatefalldamage")
                    && charge(rebuilt) == 1_000 && rebuilt.getDamageValue() == 31 && data(rebuilt).equals(data(root)),
                    "Optional upgrade failure destroyed stored root state on rebuild");
            jump(server, rebuilt, missing ? 0 : 5);
            player.setItemSlot(EquipmentSlot.FEET, rebuilt);
            require(fall(player) == (missing ? 6 : 0) && charge(rebuilt) == (missing ? 1_000 : 550),
                    "Upgrade failure bypassed native-query permission or broke independent Negate payment");
        }
        record(variant + ": both orientations; repeated rejected preview/take; native assembly control; stored flags/FE/wear/provenance preserved; safe Jump/Negate permission; one bounded warning");
    }

    private static void lifecycle(MinecraftServer server, ServerPlayer player) {
        var ferricore = boots("ferricore", 0);
        ferricore.set(flag("stepheight"), true);
        ferricore.set(flag("negatefalldamage"), true); // Ineligible forged provenance.
        var eclipse = boots("eclipsealloy", 2_000);
        eclipse.set(flag("jumpboost"), true);
        var inputs = List.of(ferricore.copy(), eclipse.copy());
        var pair = MixedBootsFactory.createPair(ferricore, eclipse);
        for (var root : List.of(pair.primary(), pair.inverse())) {
            require(installed(root, "stepheight") && installed(root, "jumpboost")
                    && !installed(root, "negatefalldamage") && charge(root) == 1_000, "Creation union imported ineligible flag or changed FE policy");
        }
        require(ItemStack.matches(inputs.getFirst(), ferricore) && ItemStack.matches(inputs.getLast(), eclipse), "Creation changed native inputs");
        record("eligible creation union in both inverse outputs; disjoint upgrades; forged ineligible flag ignored; conservative FE unchanged");

        var celestigem = boots("celestigem", 2_000);
        celestigem.set(flag("stepheight"), true);
        var root = MixedBootsFactory.createPair(celestigem, new ItemStack(Items.DIAMOND_BOOTS)).primary();
        root.set(JustDireResourceTestSupport.energyComponent(), 123);
        root.setDamageValue(31);
        for (Boolean installed : new Boolean[]{true, false, null}) {
            root.remove(flag("stepheight"));
            if (installed != null) { root.set(flag("stepheight"), installed); }
            var smith = menu(server, player, item("template_eclipsealloy"), root.copy(), item("eclipsealloy_ingot"));
            var transformed = output(smith).copy();
            require(!transformed.isEmpty() && data(transformed).left().source().is(item("eclipsealloy_boots").getItem()), "Actual native tier transformation failed");
            require(java.util.Objects.equals(installed, transformed.get(flag("stepheight")))
                    && charge(transformed) == 123 && transformed.getDamageValue() == 31, "Tier transformation reimported root flag or reset FE/wear");
            require(ItemStack.matches(transformed, take(smith, player)), "Tier take differs from root-preserving preview");
        }
        record("real native tier recipe/take; equality-to-old-projection true plus explicit false/absence survive; FE/wear preserved");

        root.set(flag("jumpboost"), true);
        var dormant = MixedBootsRecomposer.recompose(root, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
        require(installed(dormant, "jumpboost"), "Dormant root installation was destroyed");
        var restored = MixedBootsRecomposer.recompose(dormant, boots("ferricore", 0), new ItemStack(Items.DIAMOND_BOOTS));
        require(installed(restored, "jumpboost"), "Returning tier lost root installation");
        jump(server, dormant, 0); jump(server, restored, 1);
        record("dormant installed flag retained through loss/return of eligible tier; gameplay eligibility still enforced");

        var a = boots("ferricore", 0);
        var b = boots("blazegold", 0);
        for (ItemStack source : List.of(a, b)) { source.set(flag("jumpboost"), true); }
        JustDireAttributeTestSupport.set(a, "jumpboost_toggle", true);
        JustDireAttributeTestSupport.set(a, "jumpboost_value", 1);
        JustDireAttributeTestSupport.set(b, "jumpboost_toggle", false);
        JustDireAttributeTestSupport.set(b, "jumpboost_value", 2);
        var owners = MixedBootsFactory.createPair(a, b);
        var toggle = JustDireAttributeTestSupport.<Boolean>component("jumpboost_toggle");
        var value = JustDireAttributeTestSupport.<Integer>component("jumpboost_value");
        require(owners.primary().get(toggle) && owners.primary().get(value) == 1
                && !owners.inverse().get(toggle) && owners.inverse().get(value) == 2, "Conflicting initial settings do not follow owner");
        for (ItemStack output : List.of(owners.primary(), owners.inverse())) {
            var before = output.copy();
            var swapped = MixedBootsRecomposer.recompose(output, b, a);
            require(java.util.Objects.equals(before.get(toggle), swapped.get(toggle))
                    && java.util.Objects.equals(before.get(value), swapped.get(value)), "Root settings equal to old projection were replaced");
            output.remove(toggle); output.remove(value);
            var missing = MixedBootsRecomposer.recompose(output, b, a);
            require(!missing.has(toggle) && !missing.has(value), "Root setting absence reimported historical controls");
            jump(server, missing, 2); // Missing slider uses Blazegold default, not source A's historical 1.
        }
        record("conflicting switches/parameters use initial owner; equality-to-old-projection and explicit absence survive; missing numeric setting uses native tier default");
    }

    private static void attributes(MinecraftServer server, ServerPlayer player) {
        for (String tier : List.of("ferricore", "celestigem")) {
            var pair = MixedBootsFactory.createPair(boots(tier, 0), new ItemStack(Items.DIAMOND_BOOTS));
            for (var root : List.of(pair.primary(), pair.inverse())) {
                var smith = menu(server, player, ItemStack.EMPTY, root, item("upgrade_stepheight"));
                var installed = take(smith, player);
                require(!installed.isEmpty() && !installed(data(installed).left().source(), "stepheight")
                        && !installed(data(installed).right().source(), "stepheight"), "Step installation is not root-only");
                var mob = EntityType.ZOMBIE.create(server.overworld(), EntitySpawnReason.COMMAND);
                require(mob != null, "Could not create equipped Step control");
                mob.setItemSlot(EquipmentSlot.FEET, installed);
                boolean powered = tier.equals("celestigem");
                for (int charge : new int[]{0, 49, 50, 100, 0, 100}) {
                    if (powered) { installed.set(JustDireResourceTestSupport.energyComponent(), charge); }
                    var before = installed.copy();
                    mob.tick();
                    near(.6 + (!powered || charge >= 50 ? 1 : 0), mob.getAttributeValue(Attributes.STEP_HEIGHT), "Root-only Step equipment transition " + tier + "/" + charge);
                    require(ItemStack.matches(before, installed), "Step query/tick changed FE or provenance");
                }
                for (String control : List.of("tool_enabled", "stepheight_toggle")) {
                    JustDireAttributeTestSupport.set(installed, control, false);
                    mob.tick(); near(.6, mob.getAttributeValue(Attributes.STEP_HEIGHT), "Root Step control ignored " + tier + "/" + control);
                    JustDireAttributeTestSupport.set(installed, control, true);
                    mob.tick(); near(1.6, mob.getAttributeValue(Attributes.STEP_HEIGHT), "Root Step control not restored");
                }
                var provenance = data(installed);
                installed.remove(flag("stepheight"));
                installed = MixedBootsRecomposer.recompose(installed, provenance.left().source(), provenance.right().source());
                mob.setItemSlot(EquipmentSlot.FEET, installed);
                mob.tick(); near(.6, mob.getAttributeValue(Attributes.STEP_HEIGHT), "Metadata rebuild resurrected Step");
                mob.discard();
            }
            record(tier + " root-only Step: both orientations; actual equipped step height; controls; charge thresholds/depletion/recharge; rebuild; read-only state");
        }

        var source = boots("ferricore", 0);
        source.set(flag("stepheight"), true);
        var denied = MixedBootsFactory.createPair(source, new ItemStack(Items.DIAMOND_BOOTS)).primary();
        denied.remove(flag("stepheight"));
        require(step(denied) == 0, "Historical source flag bypassed root permission");
        var both = MixedBootsFactory.createPair(source, boots("blazegold", 0),
                new AttributeRuleSet(AttributePolicy.COMBINE, Map.of()), ComponentRulesLifecycle.serverRules()).primary();
        near(2, step(both), "Global installation did not reach both eligible Step tiers under frozen combine");
        var rebuilt = MixedBootsRecomposer.recompose(denied, data(denied).left().source(), data(denied).right().source());
        near(0, step(rebuilt), "Source flag reappeared after metadata rebuild");
        record("historical source Step denied; existing nine-state codec supports non-powered tiers and frozen combine");

        var explicit = both.copy();
        var modifier = new ItemAttributeModifiers.Entry(Attributes.STEP_HEIGHT,
                new net.minecraft.world.entity.ai.attributes.AttributeModifier(Identifier.fromNamespaceAndPath("justdirethings", "justdirestepassist"),
                        17, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.FEET, ItemAttributeModifiers.Display.hidden());
        var entries = new java.util.ArrayList<>(explicit.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers());
        entries.add(modifier);
        explicit.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(List.copyOf(entries)));
        var explicitRebuilt = MixedBootsRecomposer.recompose(explicit, data(explicit).left().source(), data(explicit).right().source());
        near(17, step(explicitRebuilt), "Root-owned upgrade rebuild overwrote explicit managed modifier");
        var removed = both.copy();
        // Armor is present in the persisted baseline, so absence represents a real explicit removal.
        removed.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(removed.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers()
                .stream().filter(entry -> !entry.attribute().is(Attributes.ARMOR.unwrapKey().orElseThrow())).toList()));
        var removalRebuilt = MixedBootsRecomposer.recompose(removed, data(removed).left().source(), data(removed).right().source());
        require(removalRebuilt.getAttributeModifiers().modifiers().stream().noneMatch(entry -> entry.attribute().is(Attributes.ARMOR.unwrapKey().orElseThrow())), "Metadata rebuild resurrected removed armor");
        record("explicit managed root Step edit and armor removal preserved through metadata rebuild/live projection");

        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var loaded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, both).getOrThrow()).getOrThrow();
        require(ItemStack.matches(both, loaded), "Persistent codec lost root flags/projections");
        near(2, step(loaded), "Persisted projection lost frozen rules");
        var packet = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(packet, loaded);
            var transferred = ItemStack.STREAM_CODEC.decode(packet);
            require(ItemStack.matches(loaded, transferred), "Packet lost root flags/projections");
            near(2, step(transferred), "Packet projection differs");
        } finally { packet.release(); }
        record("persistent and network stack codecs; root-installed flags and nine-state frozen metadata retained");
    }

    private static void gameplay(MinecraftServer server) {
        var ferricore = boots("ferricore", 0);
        ferricore.set(flag("jumpboost"), true);
        var pair = MixedBootsFactory.createPair(ferricore, boots("blazegold", 0));
        for (var root : List.of(pair.primary(), pair.inverse())) {
            jump(server, root, 2);
            root.remove(flag("jumpboost")); jump(server, root, 0);
        }
        var eclipse = boots("eclipsealloy", 2_000);
        eclipse.set(flag("jumpboost"), true);
        pair = MixedBootsFactory.createPair(eclipse, boots("ferricore", 0));
        for (var root : List.of(pair.primary(), pair.inverse())) {
            for (int charge : new int[]{1_000, 0, 1_000}) {
                root.set(JustDireResourceTestSupport.energyComponent(), charge); jump(server, root, charge >= 5 ? 5 : 1);
            }
        }
        record("actual jump event: Ferricore installation activates stronger Blazegold once; Eclipse/Ferricore uses charged maximum and independent passive fallback/recharge; root absence denies historical flags");

        var player = NeoForgeJustDireNegateFallTest.player(server, GameType.SURVIVAL);
        require(fall(player) == 6, "Unprotected survival control is immune");
        var nativeBoots = NeoForgeJustDireNegateFallTest.boots("celestigem", 1_000);
        player.setItemSlot(EquipmentSlot.FEET, nativeBoots);
        require(fall(player) == 0 && charge(nativeBoots) == 550, "Native Negate payment control changed");
        pair = MixedBootsFactory.createPair(boots("celestigem", 1_000), boots("eclipsealloy", 1_000));
        for (var root : List.of(pair.primary(), pair.inverse())) {
            root.set(flag("negatefalldamage"), true);
            var before = data(root);
            player.setItemSlot(EquipmentSlot.FEET, root);
            require(fall(player) == 0 && charge(root) == 550 && data(root).equals(before), "Global Negate did not debit exactly once on actual root");
            root.remove(flag("negatefalldamage"));
            require(fall(player) == 6 && charge(root) == 550, "Absent root Negate still spent/protected");
        }
        record("native and actual survival mixed Negate falls: two eligible tiers, one 450-FE root debit, unchanged sources; root removal denies protection/payment");
    }

    private static void jump(MinecraftServer server, ItemStack root, int value) {
        var player = NeoForgeJustDireNegateFallTest.player(server, GameType.SURVIVAL);
        player.jumpFromGround();
        double baseline = player.getDeltaMovement().y;
        player.setDeltaMovement(Vec3.ZERO);
        var before = root.copy();
        player.setItemSlot(EquipmentSlot.FEET, root);
        player.jumpFromGround();
        near(baseline + value / 7.5f, player.getDeltaMovement().y, "Wrong cross-tier passive Jump " + value);
        require(ItemStack.matches(before, root), "Passive query mutated root/provenance");
    }

    private static float fall(ServerPlayer player) {
        player.setHealth(player.getMaxHealth()); player.invulnerableTime = 0;
        player.causeFallDamage(9, 1, player.damageSources().fall());
        return player.getMaxHealth() - player.getHealth();
    }

    private static ItemStack take(SmithingMenu menu, ServerPlayer player) {
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(SmithingMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
        return menu.getCarried().copy();
    }

    private static SmithingMenu menu(MinecraftServer server, ServerPlayer player, ItemStack template, ItemStack root, ItemStack addition) {
        var menu = new SmithingMenu(1, player.getInventory(), ContainerLevelAccess.create(server.overworld(), BlockPos.ZERO));
        menu.getSlot(SmithingMenu.TEMPLATE_SLOT).set(template);
        menu.getSlot(SmithingMenu.BASE_SLOT).set(root);
        menu.getSlot(SmithingMenu.ADDITIONAL_SLOT).set(addition);
        return menu;
    }

    private static ItemStack output(SmithingMenu menu) { return menu.getSlot(SmithingMenu.RESULT_SLOT).getItem(); }
    private static ItemStack boots(String tier, int energy) { return NeoForgeJustDireResourcesTest.boots(tier, energy); }
    private static ItemStack item(String path) { return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", path)).orElseThrow()); }
    private static MixedBootsData data(ItemStack stack) { return ModContent.getMixedBootsData(stack).orElseThrow(); }
    private static int charge(ItemStack stack) { return stack.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0); }
    @SuppressWarnings("unchecked") private static DataComponentType<Boolean> flag(String ability) {
        return (DataComponentType<Boolean>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(
                Identifier.fromNamespaceAndPath("justdirethings", ability + "_upgrade_installed")).orElseThrow().value();
    }
    private static boolean installed(ItemStack stack, String ability) { return stack.getOrDefault(flag(ability), false); }
    private static double step(ItemStack stack) {
        return stack.getAttributeModifiers().modifiers().stream().filter(entry -> entry.attribute().is(Attributes.STEP_HEIGHT.unwrapKey().orElseThrow()))
                .mapToDouble(entry -> entry.modifier().amount()).sum();
    }
    private static void record(String description) { groups++; report.append(description).append('\n'); }
    private static void near(double expected, double actual, String message) { require(Math.abs(expected - actual) < 1e-7, message + ": " + actual + " != " + expected); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
