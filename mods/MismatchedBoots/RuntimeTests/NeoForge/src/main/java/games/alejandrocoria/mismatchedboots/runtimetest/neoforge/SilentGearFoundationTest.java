package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearState;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.interaction.MixedBootsSmithingPolicy;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import io.netty.buffer.Unpooled;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Actual creation/recomposition and codec foundation; live behavior is tested in its own phase. */
final class SilentGearFoundationTest {
    private SilentGearFoundationTest() {
    }

    static void absent(MinecraftServer server) {
        require(!ModList.get().isLoaded("silentgear"), "SG absence fixture loaded native mod");
        require(!games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearMagnet.available(), "Absent SG bound magnet API");
        var player = FakePlayerFactory.getMinecraft(server.overworld());
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        for (var root : NeoForgeSilentGearTest.workbench(player, new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.DIAMOND_BOOTS))) {
            require(!root.has(SilentGearCompatibility.state()) && SilentGearCompatibility.effectiveTraits(root).isEmpty(),
                    "Ordinary pair gained SG behavior");
            var data = ModContent.getMixedBootsData(root).orElseThrow();
            require(!MixedBootsRecomposer.recompose(root, data.left().source(), data.right().source()).has(SilentGearCompatibility.state()),
                    "Absent recomposition introduced SG state");
            root.set(SilentGearCompatibility.state(),
                     new SilentGearState(Optional.empty(), Optional.empty(), List.of(), ItemAttributeModifiers.EMPTY, List.of()));
            root.setDamageValue(5);
            root.inventoryTick(player.level(), player, null);
            player.tickCount = 20;
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, root);
            root.inventoryTick(player.level(), player, net.minecraft.world.entity.EquipmentSlot.FEET);
            require(player.getActiveEffects().isEmpty(), "Absent SG published wearer potions");
            require(!root.canWalkOnPowderedSnow(player), "Absent SG published Snow Walker");
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 200));
            root.inventoryTick(player.level(), player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
            require(player.hasEffect(net.minecraft.world.effect.MobEffects.POISON), "Absent SG canceled poison");
            player.removeAllEffects();
            root.hurtAndBreak(3, player, net.minecraft.world.entity.EquipmentSlot.FEET);
            require(root.getDamageValue() == 8, "Absent SG changed ordinary tick/wear callbacks");
            require(!root.getItem().makesPiglinsNeutral(root, player)
                            && games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearRecognition.brilliant(root, player, true),
                    "Absent SG recognition changed ordinary item results");
            var tick = new net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre(player);
            require(!games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearRecognition.voidWard(false, tick, true)
                            && games.alejandrocoria.mismatchedboots.compatibility.silentgear.SilentGearRecognition.voidWard(true, tick, false),
                    "Absent SG changed ordinary Void Ward query results");
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, root);
            var fall = new net.neoforged.neoforge.event.entity.living.LivingFallEvent(player, 10, 1);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(fall);
            require(fall.getDistance() == 10 && !fall.isCanceled(), "Absent SG changed ordinary fall dispatch");
            var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            require(ItemStack.matches(root, ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, root).getOrThrow()).getOrThrow()),
                    "Our registered state did not decode without native classes");
        }
        NetworkAcceptanceFiles.write("silent-gear-stats-report.txt",
                                     "SG absent: two paid ordinary orientations; production recomposition/trait view neutral; ordinary tick/wear/recognition/fall with own state; own state codec independent of SG\n");
    }

    static void run(ServerPlayer player, NeoForgeSilentGearTest.NativeApi api, StringBuilder report) throws Exception {
        var stage = NeoForgeSilentGearTest.NativeApi.component("OXIDATION_STAGE");
        var counter = NeoForgeSilentGearTest.NativeApi.component("OXIDATION_COUNTER");
        ItemStack copper = api.boot("copper");
        ItemStack younger = copper.copy();
        copper.set(counter, 1199);
        younger.set(counter, 10);
        var nativeModifiers = copper.getAttributeModifiers();
        var firstModifier = nativeModifiers.modifiers().getFirst();
        var duplicateModifiers = new ItemAttributeModifiers(List.of(firstModifier, firstModifier));
        require(SilentGearCompatibility.normalize(new ItemStack(Items.DIAMOND_BOOTS), duplicateModifiers).equals(duplicateModifiers),
                "Loaded SG binding normalized another mod's source");
        ItemStack forged = new ItemStack(Items.DIAMOND_BOOTS);
        forged.set(counter, 100);
        for (ItemStack root : NeoForgeSilentGearTest.workbench(player, forged, new ItemStack(Items.IRON_BOOTS))) {
            require(!root.has(SilentGearCompatibility.state()), "Orphan native counter manufactured an SG lane");
        }
        for (ItemStack other : List.of(younger, new ItemStack(Items.DIAMOND_BOOTS))) {
            for (ItemStack root : NeoForgeSilentGearTest.workbench(player, copper, other)) {
                var sources = ModContent.getMixedBootsData(root).orElseThrow();
                SilentGearState initial = root.get(SilentGearCompatibility.state());
                require(initial != null, "Production SG state finalizer did not run");
                assertLane(initial.left(), sources.left().source(), api);
                assertLane(initial.right(), sources.right().source(), api);
                require(initial.overrides().isEmpty() && !initial.rules().isEmpty(), "Initial projection ownership differs");
                ItemStack before = root.copy();
                player.tickCount = 21;
                root.inventoryTick(player.level(), player, null);
                root.getAttributeModifiers();
                require(ItemStack.matches(before, root), "Unchanged off-cadence tick/query wrote state");

                var live = new SilentGearState(initial.left()
                                                      .map(value -> new SilentGearState.Oxidation(value.phase(), value.level(), value.stage(), 77)),
                                               initial.right()
                                                      .map(value -> new SilentGearState.Oxidation(value.phase(), value.level(), value.stage(), 29)),
                                               initial.rules(), initial.projection(), initial.overrides());
                root.set(SilentGearCompatibility.state(), live);
                root.set(DataComponents.CUSTOM_NAME, Component.literal("live SG foundation"));
                root.setDamageValue(9);
                // Removal of a managed group is an explicit live override, including when absent at the next recompose.
                var originalModifiers = root.get(DataComponents.ATTRIBUTE_MODIFIERS);
                var removed = originalModifiers.modifiers().stream()
                                               .filter(entry -> entry.attribute()
                                                                     .unwrapKey()
                                                                     .orElseThrow()
                                                                     .identifier()
                                                                     .equals(Identifier.withDefaultNamespace("armor")))
                                               .map(SilentGearState.Key::of).findFirst().orElseThrow();
                root.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(originalModifiers.modifiers().stream()
                                                                                                         .filter(entry -> !SilentGearState.Key.of(
                                                                                                                 entry).equals(removed)).toList()));
                ItemStack liveBefore = root.copy();
                var trim = MixedBootsSmithingPolicy.evaluate(player.level(), new ItemStack(Items.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE),
                                                             root, new ItemStack(Items.AMETHYST_SHARD))
                                                   .orElseThrow(() -> new AssertionError("Loaded trim recipe did not match"));
                ItemStack result = trim.output();
                var resultState = result.get(SilentGearCompatibility.state());
                require(resultState.left().equals(live.left()) && resultState.right().equals(live.right()), "Trim reset live oxidation");
                require(resultState.overrides().contains(removed)
                                && result.get(DataComponents.ATTRIBUTE_MODIFIERS)
                                         .modifiers()
                                         .stream()
                                         .noneMatch(entry -> SilentGearState.Key.of(entry).equals(removed)),
                        "Trim resurrected a removed managed attribute");
                require(result.getDamageValue() == 9 && result.getHoverName().equals(root.getHoverName()), "Trim changed unrelated live root state");
                require(ItemStack.matches(liveBefore, root), "Trim preview mutated its input");
                var trimmedSources = ModContent.getMixedBootsData(result).orElseThrow();
                require(trimmedSources.left().source().has(DataComponents.TRIM) || trimmedSources.right().source().has(DataComponents.TRIM),
                        "Trim control did not transform a source");
                ItemStack repeat = MixedBootsRecomposer.recompose(result, trimmedSources.left().source(), trimmedSources.right().source());
                require(repeat.get(SilentGearCompatibility.state()).overrides().contains(removed), "Repeat forgot explicit removal");

                var ops = player.level().registryAccess().createSerializationContext(JsonOps.INSTANCE);
                ItemStack decoded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, result).getOrThrow()).getOrThrow();
                require(ItemStack.matches(decoded, result), "Production SG item codec changed state/provenance");
                var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.level().registryAccess());
                try {
                    var codec = SilentGearCompatibility.state().streamCodec();
                    codec.encode(buffer, resultState);
                    require(codec.decode(buffer).equals(resultState) && buffer.readableBytes() == 0, "SG network codec differs");
                } finally {
                    buffer.release();
                }

                root.remove(SilentGearCompatibility.state());
                ItemStack absent = MixedBootsRecomposer.recompose(root, sources.left().source(), sources.right().source());
                require(!absent.has(SilentGearCompatibility.state()), "Recomposition reseeded explicitly absent live state");

                // An explicitly absent lane stays absent even though its frozen source still has an oxidation trait.
                root.set(SilentGearCompatibility.state(), new SilentGearState(Optional.empty(), live.right(),
                                                                              live.rules(), live.projection(), live.overrides()));
                require(MixedBootsRecomposer.recompose(root, sources.left().source(), sources.right().source())
                                            .get(SilentGearCompatibility.state()).left().isEmpty(), "Historical source resurrected a removed lane");
            }
        }

        var strict = new AttributeRuleSet(AttributePolicy.SEPARATE, Map.of(Identifier.withDefaultNamespace("armor"), AttributePolicy.MAXIMUM));
        var composed = MixedBootsFactory.createPair(copper, new ItemStack(Items.DIAMOND_BOOTS), strict,
                                                    games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle.serverRules());
        require(composed.primary().get(SilentGearCompatibility.state()).rules().stream()
                        .anyMatch(rule -> rule.attribute()
                                              .equals(Identifier.withDefaultNamespace("armor")) && rule.policy() == AttributePolicy.MAXIMUM),
                "State did not capture supplied resolved policies");
        var duplicate = NeoForgeSilentGearTest.workbench(player, api.boot("gold"), api.boot("iron")).getFirst();
        require(SilentGearCompatibility.effectiveTraits(duplicate).stream().filter(trait -> trait.id().getPath().equals("malleable"))
                                       .findFirst().orElseThrow().level() == api.traitLevel(api.boot("iron"), "malleable"),
                "Production trait view did not select max native level");

        for (String invalid : List.of("negative", "counter", "stage")) {
            ItemStack malformed = copper.copy();
            if (invalid.equals("negative")) {
                malformed.set(counter, -1);
            }
            if (invalid.equals("counter")) {
                malformed.set(counter, 1201);
            }
            if (invalid.equals("stage")) {
                malformed.set(stage, (byte) 127);
            }

            var menu = new WorkbenchMenu(2, player.getInventory());
            menu.getSlot(0).set(malformed);
            menu.getSlot(1).set(new ItemStack(Items.DIAMOND_BOOTS));
            require(menu.getSlot(2).getItem().isEmpty() && menu.getSlot(3).getItem().isEmpty() && !menu.isCommitted()
                            && ItemStack.matches(menu.getSlot(0).getItem(), malformed), "Malformed SG input produced/payable preview: " + invalid);
        }
        report.append(
                      "PRODUCTION A: paid one/two-sided inverse state; native defaults and unequal counters; actual loaded trim + repeat preserve live clocks/removal; ")
              .append("captured supplied policies; item/network codecs; max-level native view; non-SG duplicates/orphan counter unchanged; explicit state/lane absence; three malformed input refusals; unchanged off-cadence tick/query\n");
    }

    private static void assertLane(Optional<SilentGearState.Oxidation> lane, ItemStack source,
                                   NeoForgeSilentGearTest.NativeApi api) throws Exception {
        if (!api.gear.isInstance(source.getItem())) {
            require(lane.isEmpty(), "Vanilla source gained a lane");
            return;
        }
        require(lane.orElseThrow().equals(new SilentGearState.Oxidation("polished", 1, 0,
                                                                        (int) source.getOrDefault(NeoForgeSilentGearTest.NativeApi.component(
                                                                                "OXIDATION_COUNTER"), 0))),
                "Initial lane differs from its native source");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
