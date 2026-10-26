package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireAttributeProjections;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireAttributesCompatibility;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsRecomposer;
import games.alejandrocoria.mismatchedboots.config.AttributeRulesLifecycle;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.runtimetest.NetworkAcceptanceFiles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Real equipped transitions and competing transformations, production-JAR acceptance. */
public final class NeoForgeJustDireAttributesTest {
    private static boolean finished;
    private static boolean later;
    private static int laterReads;
    private static int cases;
    private static final Identifier ARMOR_ID = Identifier.withDefaultNamespace("armor.boots");
    private static final Identifier EXTRA_ID = Identifier.fromNamespaceAndPath("fixture", "extra");

    private NeoForgeJustDireAttributesTest() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, NeoForgeJustDireAttributesTest::later);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void later(ItemAttributeModifierEvent event) {
        if (!later || !event.getItemStack().has(JustDireAttributesCompatibility.projections())) {
            return;
        }
        laterReads++;
        event.replaceModifier(Attributes.ARMOR, new AttributeModifier(ARMOR_ID, 23, AttributeModifier.Operation.ADD_VALUE),
                              EquipmentSlotGroup.FEET);
        event.removeModifier(Attributes.ARMOR_TOUGHNESS, ARMOR_ID);
        event.addModifier(Attributes.MOVEMENT_SPEED, new AttributeModifier(Identifier.fromNamespaceAndPath("fixture", "late"),
                                                                           .07, AttributeModifier.Operation.ADD_VALUE),
                          EquipmentSlotGroup.FEET);
    }

    private static void tick(MinecraftServer server) {
        if (finished) {
            return;
        }
        finished = true;
        boolean missingApi = Boolean.getBoolean("mismatchedboots.runtimetest.justDireAttributeApiMissing");
        boolean absent = Boolean.getBoolean("mismatchedboots.runtimetest.justDireAttributeHookAbsent") || missingApi;
        try {

            ItemStack control = root(boots("celestigem", 0, true), new ItemStack(Items.IRON_BOOTS));
            var defaults = control.getAttributeModifiers();
            if (absent) {
                require(!control.has(JustDireAttributesCompatibility.projections()), "Absent hook published metadata");
                control.set(JustDireResourceTestSupport.energyComponent(), 100);
                require(control.getAttributeModifiers().equals(defaults), "Absent hook altered generic fallback");
                var passiveSource = boots("ferricore", 0, true);
                var passiveRoot = root(passiveSource, new ItemStack(Items.IRON_BOOTS));
                require(!passiveRoot.has(JustDireAttributesCompatibility.projections()), "Absent hook published non-powered metadata");
                near(0, amount(passiveRoot.getAttributeModifiers(), "step_height"), "Fallback left historical non-powered Step permanently active");
                near(1, amount(passiveSource.getAttributeModifiers(), "step_height"), "Fallback altered native non-powered Step");
                cases++;
                var saved = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(
                        System.getProperty("mismatchedboots.runtimetest.justDireAttributeSavedRoot"))));
                ItemStack loaded = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), saved).getOrThrow();
                require(loaded.has(JustDireAttributesCompatibility.projections())
                                && loaded.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0) == 100,
                        "Previous process fixture lost its projections/live FE");
                require(loaded.getAttributeModifiers().equals(loaded.get(DataComponents.ATTRIBUTE_MODIFIERS)),
                        "Missing hook did not use serialized fallback");
                near(2, amount(loaded.getAttributeModifiers(), "armor"), "Missing hook resurrected powered saved armor");
                near(0, amount(loaded.getAttributeModifiers(), "step_height"), "Missing hook resurrected powered saved Step Assist");
                require(JustDireResourceTestSupport.handler(loaded).getAmountAsInt() == 100,
                        "Attribute fallback disabled independent storage");
                var loadedData = ModContent.getMixedBootsData(loaded).orElseThrow();
                ItemStack fallbackRebuilt = MixedBootsRecomposer.recompose(loaded, boots("eclipsealloy", 400_000, true), loadedData.right().source());
                require(!fallbackRebuilt.has(JustDireAttributesCompatibility.projections()), "Missing permission published replacement metadata");
                near(2, amount(fallbackRebuilt.getAttributeModifiers(), "armor"), "Recomposition without routing resurrected powered source armor");
                near(0, amount(fallbackRebuilt.getAttributeModifiers(), "step_height"), "Recomposition without routing resurrected Step Assist");
                require(JustDireResourceTestSupport.handler(fallbackRebuilt).getAmountAsInt() == 100, "Fallback source upgrade refilled FE");
                ItemStack ordinaryRebuilt = MixedBootsRecomposer.recompose(loaded,
                                                                           new ItemStack(Items.IRON_BOOTS),
                                                                           new ItemStack(Items.GOLDEN_BOOTS));
                same(MixedBootsFactory.createPair(new ItemStack(Items.IRON_BOOTS), new ItemStack(Items.GOLDEN_BOOTS))
                                      .primary()
                                      .getAttributeModifiers(),
                     ordinaryRebuilt.getAttributeModifiers(), "Disabled adapter prevented ordinary replacement-source recomposition");
                cases++;
            } else {
                require(control.has(JustDireAttributesCompatibility.projections()), "Production lifecycle did not publish metadata");
                matrix(server);
            }
            NetworkAcceptanceFiles.write("just-dire-attributes-report.txt", cases + " production attribute scenario groups; "
                    + (absent ? (missingApi ? "native API binding rejected" : "production hook permission denied")
                                + "; no new projections published; generic fresh-root fallback; prior-process saved-root safe baseline" :
                       "both source orders/inverse outputs; empty creation/recharge/depletion; native threshold and live root toggles; "
                       + "root installation; frozen policies; pre-event identity guards; real earlier competing mixin/later event; "
                       + "custom display; bounded persistent/network metadata; explicit managed edits through tier/storage removal; "
                       + "actual native smithing installation/take; actual equipment ticks/damage mitigation/player durability")
                    + "; production attributes; incoming wear follows the separate production armor-wear policy\n");
            NetworkAcceptanceFiles.write("just-dire-attributes-success.txt", (missingApi ? "ApiMissing" : absent ? "Absent" : "Live") + "\n");
        } catch (Throwable failure) {
            NetworkAcceptanceFiles.failure("just-dire-attributes", failure);
        } finally {
            server.halt(false);
        }
    }

    private static void matrix(MinecraftServer server) throws ReflectiveOperationException {
        ItemStack iron = new ItemStack(Items.IRON_BOOTS);
        for (boolean reverse : List.of(false, true)) {
            ItemStack powered = boots("celestigem", 0, true);
            var pair = MixedBootsFactory.createPair(reverse ? iron : powered, reverse ? powered : iron);
            for (ItemStack output : List.of(pair.primary(), pair.inverse())) {
                require(output.has(JustDireAttributesCompatibility.projections()), "Production preparation failed");
                var provenance = output.get(ModContent.mixedBootsDataType().orElseThrow());
                Zombie mob = mob(server);
                mob.setItemSlot(EquipmentSlot.FEET, output);
                for (int charge : new int[]{0, 49, 50, 51, 0, 100}) {
                    output.set(JustDireResourceTestSupport.energyComponent(), charge);
                    ItemStack before = output.copy();
                    var expected = expected(output, rules());
                    same(expected, output.getAttributeModifiers(), "native projected threshold " + charge);
                    mob.tick();
                    near(2 + amount(expected, "armor"), mob.getAttributeValue(Attributes.ARMOR), "Real equipment armor refresh");
                    near(.6 + amount(expected, "step_height"), mob.getAttributeValue(Attributes.STEP_HEIGHT), "Real equipment Step Assist refresh");
                    require(ItemStack.matches(before, output) && provenance.equals(output.get(ModContent.mixedBootsDataType().orElseThrow())),
                            "Attribute query/equipment tick spent FE or changed provenance");
                }
                for (String toggle : List.of("tool_enabled", "stepheight_toggle")) {
                    JustDireAttributeTestSupport.set(output, toggle, false);
                    mob.tick();
                    near(.6, mob.getAttributeValue(Attributes.STEP_HEIGHT), "Live root toggle failed: " + toggle);
                    require(amount(output.getAttributeModifiers(), "armor") > 1, "Step toggle incorrectly disabled armor");
                    JustDireAttributeTestSupport.set(output, toggle, true);
                    mob.tick();
                    near(1.6, mob.getAttributeValue(Attributes.STEP_HEIGHT), "Step toggle did not restore installed contribution");
                }
                mob.discard();
                cases++;
            }
        }

        for (AttributePolicy policy : List.of(AttributePolicy.AVERAGE, AttributePolicy.COMBINE,
                                              AttributePolicy.MINIMUM, AttributePolicy.MAXIMUM, AttributePolicy.SEPARATE)) {
            var frozen = new AttributeRuleSet(policy, Map.of());
            var pair = MixedBootsFactory.createPair(boots("celestigem", 0, true), boots("eclipsealloy", 0, true), frozen,
                                                    games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle.serverRules());
            for (ItemStack output : List.of(pair.primary(), pair.inverse())) {

                for (int charge : new int[]{0, 50, 50_000, 0}) {
                    output.set(JustDireResourceTestSupport.energyComponent(), charge);
                    same(expected(output, frozen), output.getAttributeModifiers(), "Frozen policy " + policy);
                }
            }
            cases++;
        }

        ItemStack rejected = boots("celestigem", 0, false);
        ItemStack rejectedBefore = rejected.copy();
        boolean rejectedPreparation = false;
        try {
            MixedBootsFactory.createPair(rejected, iron,
                                         new AttributeRuleSet(AttributePolicy.REJECT, Map.of()),
                                         games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle.serverRules());
        } catch (IllegalArgumentException failure) {
            rejectedPreparation = true;
        }
        require(rejectedPreparation && ItemStack.matches(rejectedBefore, rejected), "Reject policy partially published projections");
        boolean latentRejected = false;
        try {
            MixedBootsFactory.createPair(rejected, boots("eclipsealloy", 0, false),
                                         new AttributeRuleSet(AttributePolicy.REJECT, Map.of()),
                                         games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle.serverRules());
        } catch (IllegalArgumentException failure) {
            latentRejected = true;
        }
        require(latentRejected && ItemStack.matches(rejectedBefore, rejected), "Empty creation bypassed rejection of its latent powered attributes");
        cases++;

        ItemStack customIron = iron.copy();
        customIron.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(List.of(
                new ItemAttributeModifiers.Entry(Attributes.ARMOR,
                                                 new AttributeModifier(ARMOR_ID, .2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                                                 EquipmentSlotGroup.FEET,
                                                 ItemAttributeModifiers.Display.override(Component.literal("managed custom armor"))))));
        var ambiguous = MixedBootsFactory.createPair(boots("celestigem", 0, false), customIron);
        for (ItemStack output : List.of(ambiguous.primary(), ambiguous.inverse())) {

            for (int charge : new int[]{0, 100, 0}) {
                output.set(JustDireResourceTestSupport.energyComponent(), charge);
                same(expected(output, rules()), output.getAttributeModifiers(), "Managed operation/display ambiguity");
            }
        }
        cases++;

        ItemStack forged = root(boots("celestigem", 0, false), iron);

        forged.set(JustDireResourceTestSupport.energyComponent(), 100);
        JustDireAttributeTestSupport.set(forged, "stepheight_upgrade_installed", true);
        require(amount(forged.getAttributeModifiers(), "step_height") == 0, "Manual flag edit unexpectedly rebuilt frozen metadata");
        var data = forged.get(ModContent.mixedBootsDataType().orElseThrow());
        ItemStack installed = data.left().source();
        ItemStack recomposed = MixedBootsRecomposer.recompose(forged, installed, data.right().source());

        near(1, amount(recomposed.getAttributeModifiers(), "step_height"), "Root-only installation failed to rebuild metadata");
        require(ModContent.getMixedBootsData(recomposed).orElseThrow().equals(data), "Rebuild changed source snapshots");
        require(recomposed.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0) == 100, "Preparation refilled FE");
        var explicit = forged.copy();
        var defaultsWithAddedStep = new ArrayList<>(explicit.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers());
        defaultsWithAddedStep.add(new ItemAttributeModifiers.Entry(Attributes.STEP_HEIGHT,
                                                                   new AttributeModifier(Identifier.fromNamespaceAndPath("justdirethings",
                                                                                                                         "justdirestepassist"), 17,
                                                                                         AttributeModifier.Operation.ADD_VALUE),
                                                                   EquipmentSlotGroup.FEET,
                                                                   ItemAttributeModifiers.Display.hidden()));
        explicit.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(List.copyOf(defaultsWithAddedStep)));
        var explicitRebuilt = MixedBootsRecomposer.recompose(explicit, installed, data.right().source());
        near(17,
             amount(explicitRebuilt.getAttributeModifiers(), "step_height"),
             "Previously absent managed identity add was lost during installation");
        cases++;

        ItemStack nonPowered = MixedBootsFactory.createPair(boots("ferricore", 0, true), iron).primary();
        require(nonPowered.has(JustDireAttributesCompatibility.projections()), "Unpowered Step needs live root control projections");
        near(1, amount(nonPowered.getAttributeModifiers(), "step_height"), "Unpowered Step missing");
        JustDireAttributeTestSupport.set(nonPowered, "tool_enabled", false);
        near(0, amount(nonPowered.getAttributeModifiers(), "step_height"), "Unpowered Step ignored root control");
        cases++;

        ItemStack editable = root(boots("celestigem", 0, true), iron);

        var extra = new ItemAttributeModifiers.Entry(Attributes.MOVEMENT_SPEED,
                                                     new AttributeModifier(EXTRA_ID, .03, AttributeModifier.Operation.ADD_VALUE),
                                                     EquipmentSlotGroup.FEET,
                                                     ItemAttributeModifiers.Display.override(Component.literal("fixture custom display")));
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(editable.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers());
        entries.add(extra);
        editable.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(List.copyOf(entries)));
        require(editable.getAttributeModifiers().modifiers().contains(extra), "Lost unrelated custom display entry");
        editable.set(JustDireResourceTestSupport.energyComponent(), 100);
        JustDireAttributeTestSupport.earlierOverride = true;
        try {
            near(17, amount(editable.getAttributeModifiers(), "armor"), "Earlier real mixin identity override lost");
            near(1, amount(editable.getAttributeModifiers(), "step_height"), "Earlier edit disabled unrelated live routing");
        } finally {
            JustDireAttributeTestSupport.earlierOverride = false;
        }
        later = true;
        try {
            var finalAttributes = editable.getAttributeModifiers();
            require(laterReads > 0 && finalAttributes.modifiers().contains(extra), "Late event/custom display not preserved");
            near(23, amount(finalAttributes, "armor"), "Late event managed replacement overwritten");
            near(0, amount(finalAttributes, "armor_toughness"), "Late event managed removal overwritten");
            near(.10, amount(finalAttributes, "movement_speed"), "Late event unrelated addition lost");
        } finally {
            later = false;
        }
        entries.removeIf(entry -> entry.attribute().equals(Attributes.ARMOR));
        entries.add(new ItemAttributeModifiers.Entry(Attributes.ARMOR,
                                                     new AttributeModifier(ARMOR_ID, 11, AttributeModifier.Operation.ADD_VALUE),
                                                     EquipmentSlotGroup.FEET,
                                                     ItemAttributeModifiers.Display.hidden()));
        editable.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(List.copyOf(entries)));
        near(11, amount(editable.getAttributeModifiers(), "armor"), "Explicit default identity/display edit overwritten");
        require(editable.getAttributeModifiers().modifiers().stream().anyMatch(entry -> entry.attribute().equals(Attributes.ARMOR)
                && entry.display().equals(ItemAttributeModifiers.Display.hidden())), "Managed edit display lost");
        var editableData = ModContent.getMixedBootsData(editable).orElseThrow();
        ItemStack rebuilt = MixedBootsRecomposer.recompose(editable, boots("eclipsealloy", 0, true), editableData.right().source());
        near(11, amount(rebuilt.getAttributeModifiers(), "armor"), "Source-wise tier rebuild lost explicit managed root override");
        require(rebuilt.get(JustDireResourceTestSupport.energyComponent()) == 100, "Tier rebuild refilled FE");
        ItemStack removed = MixedBootsRecomposer.recompose(rebuilt, new ItemStack(Items.GOLDEN_BOOTS), editableData.right().source());
        require(!removed.has(JustDireAttributesCompatibility.projections()), "Removing the last powered source retained stale projections");
        near(11, amount(removed.getAttributeModifiers(), "armor"), "Removing storage lost explicit managed root override");
        cases++;

        ItemStack persisted = root(boots("celestigem", 0, true), iron);
        var combined = new AttributeRuleSet(AttributePolicy.COMBINE, Map.of());
        persisted = MixedBootsFactory.createPair(boots("celestigem", 0, true), iron, combined,
                                                 games.alejandrocoria.mismatchedboots.config.ComponentRulesLifecycle.serverRules()).primary();
        persisted.set(JustDireResourceTestSupport.energyComponent(), 100);
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var json = ItemStack.CODEC.encodeStart(ops, persisted).getOrThrow();
        NetworkAcceptanceFiles.write("just-dire-attributes-root.json", json.toString());
        ItemStack decoded = ItemStack.CODEC.parse(ops, json).getOrThrow();
        decoded.set(JustDireResourceTestSupport.energyComponent(), 100);
        same(expected(decoded, combined), decoded.getAttributeModifiers(), "Loaded root lost frozen rules/empty source restoration");
        require(!decoded.getAttributeModifiers().equals(expected(decoded, rules())), "Frozen policy oracle failed to differ from current rules");
        var packet = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(packet, decoded);
            ItemStack transferred = ItemStack.STREAM_CODEC.decode(packet);
            require(ItemStack.matches(decoded, transferred), "Network codec lost live charge/projections");
            same(decoded.getAttributeModifiers(), transferred.getAttributeModifiers(), "Network projection dispatch differs");
        } finally {
            packet.release();
        }
        require(JustDireAttributeProjections.CODEC.parse(ops, new com.google.gson.JsonArray()).error().isPresent(),
                "Unbounded/missing state table accepted");
        cases++;

        Zombie dynamic = mob(server);
        ItemStack armor = root(boots("celestigem", 0, false), iron);

        dynamic.setItemSlot(EquipmentSlot.FEET, armor);
        float drainedDamage = damage(dynamic);
        armor.set(JustDireResourceTestSupport.energyComponent(), 100);
        float chargedDamage = damage(dynamic);
        require(chargedDamage < drainedDamage,
                "Actual charged armor mitigation failed: " + chargedDamage + "/" + drainedDamage);
        armor.set(JustDireResourceTestSupport.energyComponent(), 0);
        near(drainedDamage, damage(dynamic), "Actual depletion did not restore normal-only mitigation");
        Zombie nativeControl = mob(server);
        ItemStack nativeArmor = boots("celestigem", 0, false);
        nativeControl.setItemSlot(EquipmentSlot.FEET, nativeArmor);
        float emptyNative = damage(nativeControl);
        nativeArmor.set(JustDireResourceTestSupport.energyComponent(), 100);
        require(damage(nativeControl) < emptyNative, "Native equipment/damage control did not respond to FE");
        dynamic.discard();
        nativeControl.discard();
        cases++;

        var player = new net.minecraft.server.level.ServerPlayer(server, server.overworld(),
                                                                 new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),
                                                                                                    "attribute-production"),
                                                                 net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override
            public net.minecraft.world.level.GameType gameMode() {
                return net.minecraft.world.level.GameType.SURVIVAL;
            }
        };
        new net.minecraft.server.network.ServerGamePacketListenerImpl(server,
                                                                      new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),
                                                                      player,
                                                                      net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),
                                                                                                                                      false)) {
            // Detached player has no real client to complete the loaded handshake.
            @Override
            public boolean hasClientLoaded() {
                return true;
            }
        };
        ItemStack worn = root(boots("celestigem", 0, false), iron);

        worn.set(JustDireResourceTestSupport.energyComponent(), 1_000);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.FEET, worn);
        player.doTick();
        require(player.hurtServer(server.overworld(), player.damageSources().mobAttack(mob(server)), 10)
                        && worn.getDamageValue() == 0 && worn.getOrDefault(JustDireResourceTestSupport.energyComponent(), 0) < 1_000,
                "Actual player damage did not retain funded production wear conversion alongside live attributes");
        cases++;
        realSmithing(server);
    }

    private static void realSmithing(MinecraftServer server) {
        var player = new net.minecraft.server.level.ServerPlayer(server, server.overworld(),
                                                                 new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),
                                                                                                    "attribute-smithing"),
                                                                 net.minecraft.server.level.ClientInformation.createDefault());
        new net.minecraft.server.network.ServerGamePacketListenerImpl(server,
                                                                      new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND),
                                                                      player,
                                                                      net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),
                                                                                                                                      false));
        for (boolean inverse : List.of(false, true)) {
            var pair = MixedBootsFactory.createPair(boots("celestigem", 0, false), new ItemStack(Items.IRON_BOOTS));
            var menu = new net.minecraft.world.inventory.SmithingMenu(1, player.getInventory(),
                                                                      net.minecraft.world.inventory.ContainerLevelAccess.create(server.overworld(),
                                                                                                                                net.minecraft.core.BlockPos.ZERO));
            menu.getSlot(net.minecraft.world.inventory.SmithingMenu.BASE_SLOT).set(inverse ? pair.inverse() : pair.primary());
            menu.getSlot(net.minecraft.world.inventory.SmithingMenu.ADDITIONAL_SLOT).set(new ItemStack(
                    BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("justdirethings", "upgrade_stepheight"))));
            var preview = menu.getSlot(net.minecraft.world.inventory.SmithingMenu.RESULT_SLOT).getItem().copy();
            require(!preview.isEmpty() && preview.has(JustDireAttributesCompatibility.projections()),
                    "Actual native smithing did not rebuild projections");
            menu.clicked(net.minecraft.world.inventory.SmithingMenu.RESULT_SLOT, 0, net.minecraft.world.inventory.ContainerInput.PICKUP, player);
            var taken = menu.getCarried();
            require(ItemStack.matches(preview, taken), "Smithing output take differed from the untouched preview");
            taken.set(JustDireResourceTestSupport.energyComponent(), 100);
            near(1, amount(taken.getAttributeModifiers(), "step_height"), "Post-mix native installation could not activate after recharge");
            taken.set(JustDireResourceTestSupport.energyComponent(), 0);
            near(0, amount(taken.getAttributeModifiers(), "step_height"), "Post-mix installed Step Assist remained at empty FE");
            cases++;
        }
    }

    private static float damage(Zombie mob) {
        mob.tick();
        mob.setHealth(mob.getMaxHealth());
        mob.invulnerableTime = 0;
        require(mob.hurtServer((net.minecraft.server.level.ServerLevel) mob.level(), mob.damageSources().mobAttack(mob), 10), "Damage rejected");
        return mob.getMaxHealth() - mob.getHealth();
    }

    private static Zombie mob(MinecraftServer server) {
        Zombie mob = EntityType.ZOMBIE.create(server.overworld(), EntitySpawnReason.COMMAND);
        require(mob != null, "Zombie fixture unavailable");
        return mob;
    }

    private static ItemAttributeModifiers expected(ItemStack root, AttributeRuleSet rules) throws ReflectiveOperationException {
        var data = root.get(ModContent.mixedBootsDataType().orElseThrow());
        ItemStack left = data.left().source();
        ItemStack right = data.right().source();
        for (ItemStack source : List.of(left, right)) {
            var id = BuiltInRegistries.ITEM.getKey(source.getItem());
            if (id.getNamespace().equals("justdirethings")) {
                JustDireAttributeTestSupport.set(source, "stepheight_upgrade_installed",
                                                 root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("stepheight_upgrade_installed"),
                                                                   false));
                JustDireAttributeTestSupport.set(source, "tool_enabled",
                                                 root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("tool_enabled"), true));
                JustDireAttributeTestSupport.set(source, "stepheight_toggle",
                                                 root.getOrDefault(JustDireAttributeTestSupport.<Boolean>component("stepheight_toggle"), true));
            }
            if (JustDireResourceTestSupport.handler(source) != null) {
                source.set(JustDireResourceTestSupport.energyComponent(), Math.min(JustDireResourceTestSupport.handler(source).getCapacityAsInt(),
                                                                                   root.getOrDefault(JustDireResourceTestSupport.energyComponent(),
                                                                                                     0)));
            }
        }

        return JustDireAttributeTestSupport.composed(left, right, rules);
    }

    private static ItemStack root(ItemStack left, ItemStack right) {
        return MixedBootsFactory.createPair(left, right).primary();
    }

    private static ItemStack boots(String tier, int charge, boolean step) {
        ItemStack boots = new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", tier + "_boots"))
                                                              .orElseThrow().value());
        if (tier.equals("celestigem") || tier.equals("eclipsealloy")) {
            boots.set(JustDireResourceTestSupport.energyComponent(), charge);
        }
        if (step) {
            JustDireAttributeTestSupport.set(boots, "stepheight_upgrade_installed", true);
        }
        return boots;
    }

    private static AttributeRuleSet rules() {
        return AttributeRulesLifecycle.serverRules();
    }

    private static double amount(ItemAttributeModifiers input, String attribute) {
        return input.modifiers()
                    .stream()
                    .filter(entry -> BuiltInRegistries.ATTRIBUTE.getKey(entry.attribute().value())
                                                                .equals(Identifier.withDefaultNamespace(attribute)))
                    .mapToDouble(entry -> entry.modifier().amount())
                    .sum();
    }

    private static void same(ItemAttributeModifiers expected, ItemAttributeModifiers actual, String label) {
        List<ItemAttributeModifiers.Entry> remaining = new ArrayList<>(actual.modifiers());
        for (var entry : expected.modifiers()) {
            require(remaining.remove(entry), label + ": missing " + entry + " in " + actual);
        }
        require(remaining.isEmpty(), label + ": extra " + remaining);
    }

    private static void near(double expected, double actual, String label) {
        require(Math.abs(expected - actual) < .00001, label + ": " + expected + " != " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
