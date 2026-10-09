package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
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

/** Real equipped transitions and competing transformations, not production acceptance. */
public final class NeoForgeJustDireAttributeSpikeTest {
    private static boolean finished;
    private static boolean later;
    private static int laterReads;
    private static int cases;
    private static final Identifier ARMOR_ID = Identifier.withDefaultNamespace("armor.boots");
    private static final Identifier EXTRA_ID = Identifier.fromNamespaceAndPath("fixture", "extra");

    private NeoForgeJustDireAttributeSpikeTest() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, NeoForgeJustDireAttributeSpikeTest::later);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> tick(event.getServer()));
    }

    private static void later(ItemAttributeModifierEvent event) {
        if (!later || !event.getItemStack().has(JustDireAttributeSpike.projections())) { return; }
        laterReads++;
        event.replaceModifier(Attributes.ARMOR, new AttributeModifier(ARMOR_ID, 23, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.FEET);
        event.removeModifier(Attributes.ARMOR_TOUGHNESS, ARMOR_ID);
        event.addModifier(Attributes.MOVEMENT_SPEED, new AttributeModifier(Identifier.fromNamespaceAndPath("fixture", "late"),
                        .07, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.FEET);
    }

    private static void tick(MinecraftServer server) {
        if (finished) { return; }
        finished = true;
        boolean absent = Boolean.getBoolean("mismatchedboots.runtimetest.justDireAttributeHookAbsent");
        try {
            JustDireAttributeSpike.bind();
            ItemStack control = root(boots("celestigem", 0, true), new ItemStack(Items.IRON_BOOTS));
            var defaults = control.getAttributeModifiers();
            if (absent) {
                require(!JustDireAttributeSpike.hookSeen && !JustDireAttributeSpike.prepare(control, rules()), "Absent hook published metadata");
                control.set(JustDireResourceSpike.energyComponent(), 100);
                require(control.getAttributeModifiers().equals(defaults), "Absent hook altered generic fallback");
                cases++;
                var saved = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(
                        System.getProperty("mismatchedboots.runtimetest.justDireAttributeSavedRoot"))));
                ItemStack loaded = ItemStack.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()), saved).getOrThrow();
                require(loaded.has(JustDireAttributeSpike.projections())
                        && loaded.getOrDefault(JustDireResourceSpike.energyComponent(), 0) == 100,
                        "Previous process fixture lost its projections/live FE");
                require(loaded.getAttributeModifiers().equals(loaded.get(DataComponents.ATTRIBUTE_MODIFIERS)),
                        "Missing hook did not use serialized fallback");
                near(2, amount(loaded.getAttributeModifiers(), "armor"), "Missing hook resurrected powered saved armor");
                near(0, amount(loaded.getAttributeModifiers(), "step_height"), "Missing hook resurrected powered saved Step Assist");
                cases++;
            } else {
                require(JustDireAttributeSpike.hookSeen, "Pre-event hook not reached");
                matrix(server);
            }
            NetworkAcceptanceFiles.write("just-dire-attributes-report.txt", cases + " attribute spike scenario groups; "
                    + (absent ? "real mixins disabled; no new projections published; generic fresh-root fallback; prior-process saved-root safe baseline" :
                    "both source orders/inverse outputs; empty creation/recharge/depletion; native threshold and live root toggles; "
                    + "installed provenance; frozen policies; pre-event identity guards; real earlier competing mixin/later event; "
                    + "custom display; bounded persistent/network metadata; actual equipment ticks/damage mitigation/player durability")
                    + "; test-only, no production support\n");
            NetworkAcceptanceFiles.write("just-dire-attributes-success.txt", (absent ? "Absent" : "Live") + "\n");
        } catch (Throwable failure) { NetworkAcceptanceFiles.failure("just-dire-attributes", failure); }
        finally { server.halt(false); }
    }

    private static void matrix(MinecraftServer server) throws ReflectiveOperationException {
        ItemStack iron = new ItemStack(Items.IRON_BOOTS);
        for (boolean reverse : List.of(false, true)) {
            ItemStack powered = boots("celestigem", 0, true);
            var pair = JustDireResourceSpike.createPair(reverse ? iron : powered, reverse ? powered : iron);
            for (ItemStack output : List.of(pair.primary(), pair.inverse())) {
                require(JustDireAttributeSpike.prepare(output, rules()), "Projection preparation failed");
                var provenance = output.get(ModContent.mixedBootsDataType().orElseThrow());
                Zombie mob = mob(server);
                mob.setItemSlot(EquipmentSlot.FEET, output);
                for (int charge : new int[]{0, 49, 50, 51, 0, 100}) {
                    output.set(JustDireResourceSpike.energyComponent(), charge);
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
                    JustDireAttributeSpike.set(output, toggle, false);
                    mob.tick();
                    near(.6, mob.getAttributeValue(Attributes.STEP_HEIGHT), "Live root toggle failed: " + toggle);
                    require(amount(output.getAttributeModifiers(), "armor") > 1, "Step toggle incorrectly disabled armor");
                    JustDireAttributeSpike.set(output, toggle, true);
                    mob.tick();
                    near(1.6, mob.getAttributeValue(Attributes.STEP_HEIGHT), "Step toggle did not restore installed contribution");
                }
                mob.discard(); cases++;
            }
        }

        for (AttributePolicy policy : List.of(AttributePolicy.AVERAGE, AttributePolicy.COMBINE,
                AttributePolicy.MINIMUM, AttributePolicy.MAXIMUM, AttributePolicy.SEPARATE)) {
            var frozen = new AttributeRuleSet(policy, Map.of());
            var pair = JustDireResourceSpike.createPair(boots("celestigem", 0, true), boots("eclipsealloy", 0, true));
            for (ItemStack output : List.of(pair.primary(), pair.inverse())) {
                JustDireAttributeSpike.prepare(output, frozen);
                for (int charge : new int[]{0, 50, 50_000, 0}) {
                    output.set(JustDireResourceSpike.energyComponent(), charge);
                    same(expected(output, frozen), output.getAttributeModifiers(), "Frozen policy " + policy);
                }
            }
            cases++;
        }
        ItemStack rejected = root(boots("celestigem", 0, false), iron);
        ItemStack rejectedBefore = rejected.copy();
        boolean rejectedPreparation = false;
        try { JustDireAttributeSpike.prepare(rejected, new AttributeRuleSet(AttributePolicy.REJECT, Map.of())); }
        catch (java.lang.reflect.InvocationTargetException failure) {
            rejectedPreparation = failure.getCause() instanceof IllegalArgumentException;
        }
        require(rejectedPreparation && ItemStack.matches(rejectedBefore, rejected), "Reject policy partially published projections");
        cases++;

        ItemStack customIron = iron.copy();
        customIron.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(List.of(
                new ItemAttributeModifiers.Entry(Attributes.ARMOR,
                        new AttributeModifier(ARMOR_ID, .2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
                        EquipmentSlotGroup.FEET, ItemAttributeModifiers.Display.override(Component.literal("managed custom armor"))))));
        var ambiguous = JustDireResourceSpike.createPair(boots("celestigem", 0, false), customIron);
        for (ItemStack output : List.of(ambiguous.primary(), ambiguous.inverse())) {
            JustDireAttributeSpike.prepare(output, rules());
            for (int charge : new int[]{0, 100, 0}) {
                output.set(JustDireResourceSpike.energyComponent(), charge);
                same(expected(output, rules()), output.getAttributeModifiers(), "Managed operation/display ambiguity");
            }
        }
        cases++;

        ItemStack forged = root(boots("celestigem", 0, false), iron);
        JustDireAttributeSpike.prepare(forged, rules());
        forged.set(JustDireResourceSpike.energyComponent(), 100);
        JustDireAttributeSpike.set(forged, "stepheight_upgrade_installed", true);
        require(amount(forged.getAttributeModifiers(), "step_height") == 0, "Forged root installed an upgrade");
        var data = forged.get(ModContent.mixedBootsDataType().orElseThrow());
        ItemStack installed = data.left().source();
        JustDireAttributeSpike.set(installed, "stepheight_upgrade_installed", true);
        ItemStack recomposed = JustDireResourceSpike.recompose(forged, installed, data.right().source());
        JustDireAttributeSpike.prepare(recomposed, rules());
        near(1, amount(recomposed.getAttributeModifiers(), "step_height"), "Source-wise installation failed to rebuild metadata");
        require(recomposed.getOrDefault(JustDireResourceSpike.energyComponent(), 0) == 100, "Preparation refilled FE");
        cases++;

        ItemStack nonPowered = JustDireResourceSpike.createPair(boots("ferricore", 0, true), iron).primary();
        var nonPoweredBefore = nonPowered.getAttributeModifiers();
        require(!JustDireAttributeSpike.prepare(nonPowered, rules()), "Unpowered root acquired projection state");
        JustDireAttributeSpike.set(nonPowered, "tool_enabled", false);
        same(nonPoweredBefore, nonPowered.getAttributeModifiers(), "Unpowered Step Assist behavior changed");
        cases++;

        ItemStack editable = root(boots("celestigem", 0, true), iron);
        JustDireAttributeSpike.prepare(editable, rules());
        var extra = new ItemAttributeModifiers.Entry(Attributes.MOVEMENT_SPEED,
                new AttributeModifier(EXTRA_ID, .03, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.FEET,
                ItemAttributeModifiers.Display.override(Component.literal("fixture custom display")));
        List<ItemAttributeModifiers.Entry> entries = new ArrayList<>(editable.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers());
        entries.add(extra);
        editable.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(List.copyOf(entries)));
        require(editable.getAttributeModifiers().modifiers().contains(extra), "Lost unrelated custom display entry");
        editable.set(JustDireResourceSpike.energyComponent(), 100);
        JustDireAttributeSpike.earlierOverride = true;
        try {
            near(17, amount(editable.getAttributeModifiers(), "armor"), "Earlier real mixin identity override lost");
            near(1, amount(editable.getAttributeModifiers(), "step_height"), "Earlier edit disabled unrelated live routing");
        } finally { JustDireAttributeSpike.earlierOverride = false; }
        later = true;
        try {
            var finalAttributes = editable.getAttributeModifiers();
            require(laterReads > 0 && finalAttributes.modifiers().contains(extra), "Late event/custom display not preserved");
            near(23, amount(finalAttributes, "armor"), "Late event managed replacement overwritten");
            near(0, amount(finalAttributes, "armor_toughness"), "Late event managed removal overwritten");
            near(.10, amount(finalAttributes, "movement_speed"), "Late event unrelated addition lost");
        } finally { later = false; }
        entries.removeIf(entry -> entry.attribute().equals(Attributes.ARMOR));
        entries.add(new ItemAttributeModifiers.Entry(Attributes.ARMOR,
                new AttributeModifier(ARMOR_ID, 11, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.FEET,
                ItemAttributeModifiers.Display.hidden()));
        editable.set(DataComponents.ATTRIBUTE_MODIFIERS, new ItemAttributeModifiers(List.copyOf(entries)));
        near(11, amount(editable.getAttributeModifiers(), "armor"), "Explicit default identity/display edit overwritten");
        require(editable.getAttributeModifiers().modifiers().stream().anyMatch(entry -> entry.attribute().equals(Attributes.ARMOR)
                && entry.display().equals(ItemAttributeModifiers.Display.hidden())), "Managed edit display lost");
        cases++;

        ItemStack persisted = root(boots("celestigem", 0, true), iron);
        var combined = new AttributeRuleSet(AttributePolicy.COMBINE, Map.of());
        JustDireAttributeSpike.prepare(persisted, combined);
        persisted.set(JustDireResourceSpike.energyComponent(), 100);
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var json = ItemStack.CODEC.encodeStart(ops, persisted).getOrThrow();
        NetworkAcceptanceFiles.write("just-dire-attributes-root.json", json.toString());
        ItemStack decoded = ItemStack.CODEC.parse(ops, json).getOrThrow();
        decoded.set(JustDireResourceSpike.energyComponent(), 100);
        same(expected(decoded, combined), decoded.getAttributeModifiers(), "Loaded root lost frozen rules/empty source restoration");
        require(!decoded.getAttributeModifiers().equals(expected(decoded, rules())), "Frozen policy oracle failed to differ from current rules");
        var packet = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), server.registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(packet, decoded);
            ItemStack transferred = ItemStack.STREAM_CODEC.decode(packet);
            require(ItemStack.matches(decoded, transferred), "Network codec lost live charge/projections");
            same(decoded.getAttributeModifiers(), transferred.getAttributeModifiers(), "Network projection dispatch differs");
        } finally { packet.release(); }
        require(JustDireAttributeSpike.CODEC.encodeStart(ops, List.of()).error().isPresent(), "Unbounded/missing state table accepted");
        cases++;

        Zombie dynamic = mob(server);
        ItemStack armor = root(boots("celestigem", 0, false), iron);
        JustDireAttributeSpike.prepare(armor, rules());
        dynamic.setItemSlot(EquipmentSlot.FEET, armor);
        float drainedDamage = damage(dynamic);
        armor.set(JustDireResourceSpike.energyComponent(), 100);
        float chargedDamage = damage(dynamic);
        require(chargedDamage < drainedDamage,
                "Actual charged armor mitigation failed: " + chargedDamage + "/" + drainedDamage);
        armor.set(JustDireResourceSpike.energyComponent(), 0);
        near(drainedDamage, damage(dynamic), "Actual depletion did not restore normal-only mitigation");
        Zombie nativeControl = mob(server);
        ItemStack nativeArmor = boots("celestigem", 0, false);
        nativeControl.setItemSlot(EquipmentSlot.FEET, nativeArmor);
        float emptyNative = damage(nativeControl);
        nativeArmor.set(JustDireResourceSpike.energyComponent(), 100);
        require(damage(nativeControl) < emptyNative, "Native equipment/damage control did not respond to FE");
        dynamic.discard(); nativeControl.discard(); cases++;

        var player = new net.minecraft.server.level.ServerPlayer(server, server.overworld(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "attribute-spike"),
                net.minecraft.server.level.ClientInformation.createDefault()) {
            @Override public net.minecraft.world.level.GameType gameMode() { return net.minecraft.world.level.GameType.SURVIVAL; }
        };
        new net.minecraft.server.network.ServerGamePacketListenerImpl(server,
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(), false)) {
            // Detached player has no real client to complete the loaded handshake.
            @Override public boolean hasClientLoaded() { return true; }
        };
        ItemStack worn = root(boots("celestigem", 0, false), iron);
        JustDireAttributeSpike.prepare(worn, rules());
        worn.set(JustDireResourceSpike.energyComponent(), 100);
        player.setItemSlot(EquipmentSlot.FEET, worn);
        player.doTick();
        require(player.hurtServer(server.overworld(), player.damageSources().mobAttack(mob(server)), 10)
                && worn.getDamageValue() > 0 && worn.getOrDefault(JustDireResourceSpike.energyComponent(), 0) == 100,
                "Actual player damage did not retain ordinary wear/free attribute availability boundary");
        cases++;
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
            if (JustDireResourceSpike.handler(source) != null) {
                source.set(JustDireResourceSpike.energyComponent(), Math.min(JustDireResourceSpike.handler(source).getCapacityAsInt(),
                        root.getOrDefault(JustDireResourceSpike.energyComponent(), 0)));
            }
        }
        return JustDireAttributeSpike.composed(left, right, rules);
    }

    private static ItemStack root(ItemStack left, ItemStack right) {
        return JustDireResourceSpike.createPair(left, right).primary();
    }

    private static ItemStack boots(String tier, int charge, boolean step) {
        ItemStack boots = new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("justdirethings", tier + "_boots"))
                .orElseThrow().value());
        if (tier.equals("celestigem") || tier.equals("eclipsealloy")) { boots.set(JustDireResourceSpike.energyComponent(), charge); }
        if (step) { JustDireAttributeSpike.set(boots, "stepheight_upgrade_installed", true); }
        return boots;
    }

    private static AttributeRuleSet rules() { return AttributeRulesLifecycle.serverRules(); }

    private static double amount(ItemAttributeModifiers input, String attribute) {
        return input.modifiers().stream().filter(entry -> BuiltInRegistries.ATTRIBUTE.getKey(entry.attribute().value())
                .equals(Identifier.withDefaultNamespace(attribute))).mapToDouble(entry -> entry.modifier().amount()).sum();
    }

    private static void same(ItemAttributeModifiers expected, ItemAttributeModifiers actual, String label) {
        List<ItemAttributeModifiers.Entry> remaining = new ArrayList<>(actual.modifiers());
        for (var entry : expected.modifiers()) { require(remaining.remove(entry), label + ": missing " + entry + " in " + actual); }
        require(remaining.isEmpty(), label + ": extra " + remaining);
    }

    private static void near(double expected, double actual, String label) {
        require(Math.abs(expected - actual) < .00001, label + ": " + expected + " != " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) { throw new IllegalStateException(message); }
    }
}
