package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.inventory.ResultContainer;
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

/** Bounded real-mod proof, not production or ordinary-client acceptance of the proposed model. */
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
            smithing(server, player);
            lifecycle(server, player);
            attributes(server, player);
            gameplay(server);
            NetworkAcceptanceFiles.write("just-dire-root-upgrades-report.txt", groups + " root-upgrade proof groups\n" + report);
            NetworkAcceptanceFiles.write("just-dire-root-upgrades-success.txt", "neoforge\n");
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure("just-dire-root-upgrades", failure); }
        finally { server.halt(false); }
    }

    private static void smithing(MinecraftServer server, ServerPlayer player) throws ReflectiveOperationException {
        int nativeRecipes = 0;
        for (String tier : List.of("ferricore", "blazegold", "celestigem", "eclipsealloy")) {
            for (String ability : JustDireRootUpgradePrototype.ABILITIES) {
                ItemStack source = boots(tier, 2_000);
                ItemStack addition = item("upgrade_" + ability);
                var nativeMenu = menu(server, player, ItemStack.EMPTY, source.copy(), addition.copy());
                var nativeResult = output(nativeMenu).copy();
                boolean eligible = !nativeResult.isEmpty();
                if (eligible) { nativeRecipes++; }
                var pair = MixedBootsFactory.createPair(source, new ItemStack(Items.DIAMOND_BOOTS));
                for (ItemStack root : List.of(pair.primary(), pair.inverse())) {
                    root.setDamageValue(31);
                    root.set(DataComponents.CUSTOM_NAME, Component.literal("Root upgrade proof"));
                    root.set(DataComponents.REPAIR_COST, 11);
                    ItemStack before = root.copy();
                    var smith = menu(server, player, ItemStack.EMPTY, root, addition.copy());
                    var result = output(smith);
                    require(!result.isEmpty() == eligible, "Root/native eligibility differs: " + tier + "/" + ability);
                    require(ItemStack.matches(root, before), "Root installation preview mutated input");
                    if (eligible) {
                        require(JustDireRootUpgradePrototype.installed(result, ability), "Upgrade not installed on root");
                        require(data(result).equals(data(before)), "Root installation changed source snapshots");
                        require(result.getDamageValue() == 31 && result.get(DataComponents.REPAIR_COST) == 11
                                && result.getHoverName().equals(before.getHoverName()) && charge(result) == charge(before), "Installation reset live state");
                        require(((ResultContainer) smith.getSlot(SmithingMenu.RESULT_SLOT).container).getRecipeUsed().id()
                                .equals(((ResultContainer) nativeMenu.getSlot(SmithingMenu.RESULT_SLOT).container).getRecipeUsed().id()), "Root installation credited a fabricated recipe");
                        require(output(menu(server, player, ItemStack.EMPTY, result.copy(), addition.copy())).isEmpty(), "Duplicate fell back to source installation");
                    }
                    require(output(menu(server, player, new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), root.copy(), addition.copy())).isEmpty(), "Wrong template bypassed native recipe");
                }
            }
        }
        require(nativeRecipes == 14, "Pinned native eligibility matrix changed");
        record("14 native recipes / 6 ineligible combinations; both orientations; native recipe identity; read-only preview; unchanged sources/FE/wear; duplicate and wrong-template rejection");

        for (boolean shift : List.of(false, true)) {
            ItemStack root = MixedBootsFactory.createPair(boots("eclipsealloy", 2_000), new ItemStack(Items.DIAMOND_BOOTS)).primary();
            player.getInventory().clearContent();
            var smith = menu(server, player, ItemStack.EMPTY, root, item("upgrade_stepheight"));
            var preview = output(smith).copy();
            if (shift) {
                for (int slot = SmithingMenu.RESULT_SLOT + 1; slot < smith.slots.size(); slot++) {
                    smith.getSlot(slot).set(new ItemStack(Items.STONE, 64));
                }
                require(smith.quickMoveStack(player, SmithingMenu.RESULT_SLOT).isEmpty()
                        && ItemStack.matches(preview, output(smith)) && !smith.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty(), "Blocked take lost root result/input");
                player.getInventory().clearContent();
            }
            var taken = shift ? smith.quickMoveStack(player, SmithingMenu.RESULT_SLOT) : take(smith, player);
            require(ItemStack.matches(taken, preview) && output(smith).isEmpty()
                    && smith.getSlot(SmithingMenu.BASE_SLOT).getItem().isEmpty()
                    && smith.getSlot(SmithingMenu.ADDITIONAL_SLOT).getItem().isEmpty(), "Root-only take did not consume one base/addition");
        }
        record("ordinary/shift-click root-only takes; full-inventory rejected take; one addition payment");

        Map<?, ?> flags = (Map<?, ?>) Class.forName("com.direwolf20.justdirethings.setup.Config").getField("AVAILABLE_ABILITY_MAP").get(null);
        var flag = (ModConfigSpec.BooleanValue) flags.entrySet().stream()
                .filter(entry -> ((Enum<?>) entry.getKey()).name().equals("JUMPBOOST")).findFirst().orElseThrow().getValue();
        boolean previous = flag.get();
        ItemStack configured;
        try {
            flag.set(false);
            var root = MixedBootsFactory.createPair(boots("ferricore", 0), new ItemStack(Items.DIAMOND_BOOTS)).primary();
            require(output(menu(server, player, ItemStack.EMPTY, root, item("upgrade_jumpboost"))).isEmpty(), "Disabled native ability installed globally");
            var preinstalled = boots("ferricore", 0);
            preinstalled.set(JustDireRootUpgradePrototype.flag("jumpboost"), true);
            configured = MixedBootsFactory.createPair(preinstalled, new ItemStack(Items.DIAMOND_BOOTS)).primary();
            require(JustDireRootUpgradePrototype.installed(configured, "jumpboost"), "Disabled config destroyed input installation");
            jump(server, configured, 0);
        } finally { flag.set(previous); }
        jump(server, configured, 1);
        var bare = new ItemStack(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID));
        require(output(menu(server, player, ItemStack.EMPTY, bare, item("upgrade_stepheight"))).isEmpty(), "Bare root installed upgrade");
        record("disabled native config rejects installation/use, retains imported flags, restores Jump when enabled; malformed-root rejection");
    }

    private static void lifecycle(MinecraftServer server, ServerPlayer player) {
        var ferricore = boots("ferricore", 0);
        ferricore.set(JustDireRootUpgradePrototype.flag("stepheight"), true);
        ferricore.set(JustDireRootUpgradePrototype.flag("negatefalldamage"), true); // Ineligible forged provenance.
        var eclipse = boots("eclipsealloy", 2_000);
        eclipse.set(JustDireRootUpgradePrototype.flag("jumpboost"), true);
        var inputs = List.of(ferricore.copy(), eclipse.copy());
        var pair = MixedBootsFactory.createPair(ferricore, eclipse);
        for (var root : List.of(pair.primary(), pair.inverse())) {
            require(JustDireRootUpgradePrototype.installed(root, "stepheight") && JustDireRootUpgradePrototype.installed(root, "jumpboost")
                    && !JustDireRootUpgradePrototype.installed(root, "negatefalldamage") && charge(root) == 1_000, "Creation union imported ineligible flag or changed FE policy");
        }
        require(ItemStack.matches(inputs.getFirst(), ferricore) && ItemStack.matches(inputs.getLast(), eclipse), "Creation changed native inputs");
        record("eligible creation union in both inverse outputs; disjoint upgrades; forged ineligible flag ignored; conservative FE unchanged");

        var celestigem = boots("celestigem", 2_000);
        celestigem.set(JustDireRootUpgradePrototype.flag("stepheight"), true);
        var root = MixedBootsFactory.createPair(celestigem, new ItemStack(Items.DIAMOND_BOOTS)).primary();
        root.set(JustDireResourceTestSupport.energyComponent(), 123);
        root.setDamageValue(31);
        for (Boolean installed : new Boolean[]{true, false, null}) {
            root.remove(JustDireRootUpgradePrototype.flag("stepheight"));
            if (installed != null) { root.set(JustDireRootUpgradePrototype.flag("stepheight"), installed); }
            var smith = menu(server, player, item("template_eclipsealloy"), root.copy(), item("eclipsealloy_ingot"));
            var transformed = output(smith).copy();
            require(!transformed.isEmpty() && data(transformed).left().source().is(item("eclipsealloy_boots").getItem()), "Actual native tier transformation failed");
            require(java.util.Objects.equals(installed, transformed.get(JustDireRootUpgradePrototype.flag("stepheight")))
                    && charge(transformed) == 123 && transformed.getDamageValue() == 31, "Tier transformation reimported root flag or reset FE/wear");
            require(ItemStack.matches(transformed, take(smith, player)), "Tier take differs from root-preserving preview");
        }
        record("real native tier recipe/take; equality-to-old-projection true plus explicit false/absence survive; FE/wear preserved");

        root.set(JustDireRootUpgradePrototype.flag("jumpboost"), true);
        var dormant = MixedBootsRecomposer.recompose(root, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS));
        require(JustDireRootUpgradePrototype.installed(dormant, "jumpboost"), "Dormant root installation was destroyed");
        var restored = MixedBootsRecomposer.recompose(dormant, boots("ferricore", 0), new ItemStack(Items.DIAMOND_BOOTS));
        require(JustDireRootUpgradePrototype.installed(restored, "jumpboost"), "Returning tier lost root installation");
        jump(server, dormant, 0); jump(server, restored, 1);
        record("dormant installed flag retained through loss/return of eligible tier; gameplay eligibility still enforced");
    }

    private static void attributes(MinecraftServer server, ServerPlayer player) {
        for (String tier : List.of("ferricore", "celestigem")) {
            var pair = MixedBootsFactory.createPair(boots(tier, 0), new ItemStack(Items.DIAMOND_BOOTS));
            for (var root : List.of(pair.primary(), pair.inverse())) {
                var smith = menu(server, player, ItemStack.EMPTY, root, item("upgrade_stepheight"));
                var installed = take(smith, player);
                require(!installed.isEmpty() && !JustDireRootUpgradePrototype.installed(data(installed).left().source(), "stepheight")
                        && !JustDireRootUpgradePrototype.installed(data(installed).right().source(), "stepheight"), "Step installation is not root-only");
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
                installed.remove(JustDireRootUpgradePrototype.flag("stepheight"));
                installed = MixedBootsRecomposer.recompose(installed, provenance.left().source(), provenance.right().source());
                mob.setItemSlot(EquipmentSlot.FEET, installed);
                mob.tick(); near(.6, mob.getAttributeValue(Attributes.STEP_HEIGHT), "Metadata rebuild resurrected Step");
                mob.discard();
            }
            record(tier + " root-only Step: both orientations; actual equipped step height; controls; charge thresholds/depletion/recharge; rebuild; read-only state");
        }

        var source = boots("ferricore", 0);
        source.set(JustDireRootUpgradePrototype.flag("stepheight"), true);
        var denied = MixedBootsFactory.createPair(source, new ItemStack(Items.DIAMOND_BOOTS)).primary();
        denied.remove(JustDireRootUpgradePrototype.flag("stepheight"));
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
        ferricore.set(JustDireRootUpgradePrototype.flag("jumpboost"), true);
        var pair = MixedBootsFactory.createPair(ferricore, boots("blazegold", 0));
        for (var root : List.of(pair.primary(), pair.inverse())) {
            jump(server, root, 2);
            root.remove(JustDireRootUpgradePrototype.flag("jumpboost")); jump(server, root, 0);
        }
        var eclipse = boots("eclipsealloy", 2_000);
        eclipse.set(JustDireRootUpgradePrototype.flag("jumpboost"), true);
        pair = MixedBootsFactory.createPair(eclipse, boots("ferricore", 0));
        for (var root : List.of(pair.primary(), pair.inverse())) {
            for (int charge : new int[]{1_000, 0, 1_000}) {
                root.set(JustDireResourceTestSupport.energyComponent(), charge); jump(server, root, 1);
            }
        }
        record("actual jump event: Ferricore installation activates stronger Blazegold once; Eclipse installation activates Ferricore passive with/without FE; root absence denies historical flags");

        var player = NeoForgeJustDireNegateFallTest.player(server, GameType.SURVIVAL);
        require(fall(player) == 6, "Unprotected survival control is immune");
        var nativeBoots = NeoForgeJustDireNegateFallTest.boots("celestigem", 1_000);
        player.setItemSlot(EquipmentSlot.FEET, nativeBoots);
        require(fall(player) == 0 && charge(nativeBoots) == 550, "Native Negate payment control changed");
        pair = MixedBootsFactory.createPair(boots("celestigem", 1_000), boots("eclipsealloy", 1_000));
        for (var root : List.of(pair.primary(), pair.inverse())) {
            root.set(JustDireRootUpgradePrototype.flag("negatefalldamage"), true);
            var before = data(root);
            player.setItemSlot(EquipmentSlot.FEET, root);
            require(fall(player) == 0 && charge(root) == 550 && data(root).equals(before), "Global Negate did not debit exactly once on actual root");
            root.remove(JustDireRootUpgradePrototype.flag("negatefalldamage"));
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
    private static double step(ItemStack stack) {
        return stack.getAttributeModifiers().modifiers().stream().filter(entry -> entry.attribute().is(Attributes.STEP_HEIGHT.unwrapKey().orElseThrow()))
                .mapToDouble(entry -> entry.modifier().amount()).sum();
    }
    private static void record(String description) { groups++; report.append(description).append('\n'); }
    private static void near(double expected, double actual, String message) { require(Math.abs(expected - actual) < 1e-7, message + ": " + actual + " != " + expected); }
    private static void require(boolean condition, String message) { if (!condition) { throw new AssertionError(message); } }
}
