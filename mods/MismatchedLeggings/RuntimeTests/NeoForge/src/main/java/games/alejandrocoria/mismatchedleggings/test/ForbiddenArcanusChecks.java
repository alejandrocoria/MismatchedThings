package games.alejandrocoria.mismatchedleggings.test;

import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsRecomposer;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.test.mixin.EquipmentUpdatesInvoker;
import games.alejandrocoria.mismatchedleggings.test.render.BootsCoexistence;
import games.alejandrocoria.mismatchedthings.api.composition.AttributePolicy;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Native attribute events and resource consumer; no test replacement for either mechanism. */
final class ForbiddenArcanusChecks {
    static final Identifier ATTRIBUTE = Identifier.parse("forbidden_arcanus:generic.aureal_regeneration");
    private static final List<Actor> actors = new ArrayList<>();
    private static boolean finished;
    private static int ticks;
    private static int periods;

    private ForbiddenArcanusChecks() {}

    static void tick(MinecraftServer server) {
        if (!server.isDedicatedServer() || finished || server.getTickCount() < 20) {
            return;
        }
        try {
            if (actors.isEmpty()) {
                prepare(server);
            }
            boolean regenerationTick = server.overworld().getGameTime() % 100 == 0;
            if (regenerationTick) {
                periods++;
            }
            for (var actor : actors) {
                int before = aureal(actor.player());
                // Disconnected actors are not in PlayerList. Dispatch exactly one real native Pre
                // event per actual world tick; never call the handler or reproduce its resource write.
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(actor.player()));
                require(aureal(actor.player()) == before + (regenerationTick ? (int) actor.rate() : 0),
                        "Native Aureal amount/cadence differs for " + actor.name() + " at " + server.overworld().getGameTime());
                require(actor.player().getAttributeValue(attribute()) == actor.rate(), "Equipment attribute drift: " + actor.name());
            }
            if (++ticks >= 205) {
                require(periods >= 2, "Native resource cadence was not exercised twice");
                String coverage = ForbiddenBootsChecks.only()
                        ? "Standalone Boots: native Draco/Tyr and both mixed orientations, wear/death, smithing, JDT and legacy storage passed;\n"
                        : "Native/Draco/Tyr/plain, both orientations and duplicates passed;\n"
                        + "effective canonical attributes, immutable inputs, codec/stream/hash, recomposition,\n"
                        + "unequip/inventory and fractional-policy controls passed;\n";
                Files.writeString(report("server"), coverage
                        + "native-only entity attribute save failure reproduced; fresh mixed-only entity save clean;\n"
                        + "native PlayerTickEvent.Pre consumer: " + actors.size() + " actors, " + ticks + " real world ticks, "
                        + periods + " regeneration periods; current Boots=" + Boolean.getBoolean("leggings.test.boots") + "\n");
                SkeletonChecks.passed("server");
                finished = true;
                server.halt(false);
            }
        } catch (Throwable failure) {
            finished = true;
            SkeletonChecks.failed("server", failure);
            server.halt(false);
        }
    }

    private static void prepare(MinecraftServer server) throws Exception {
        if (!ForbiddenBootsChecks.only()) {
            SkeletonChecks.verify();
            ForbiddenModifierChecks.server(server);
            ForbiddenModifierSmithingChecks.server(server);
        }
        ForbiddenBootsChecks.server(server);
        nativeSaveControl(server);
        for (var sample : samples(server)) {
            if (ForbiddenBootsChecks.only()) {
                ForbiddenModifierChecks.codecs(server, sample.stack());
            } else {
                codecs(server, sample.stack());
            }
            var player = player(server);
            equip(player, sample.stack());
            require(player.getAttributeValue(attribute()) == sample.rate(), "Wrong equipped Aureal: " + sample.name());
            require(aureal(player) == 0, "Fresh native attachment is not empty");
            actors.add(new Actor(sample.name(), player, sample.rate()));
        }
        var player = player(server);
        var root = samples(server).stream().filter(sample -> sample.name().equals(ForbiddenBootsChecks.only()
                ? "tyr_boots/diamond primary" : "tyr_leggings/diamond primary")).findFirst().orElseThrow().stack();
        equip(player, root);
        require(player.getAttributeValue(attribute()) == 2, "Unequip positive control failed");
        equip(player, ItemStack.EMPTY);
        player.getInventory().add(root.copy());
        require(player.getAttributeValue(attribute()) == 0, "Inventory/removed leggings retain Aureal");
        actors.add(new Actor("unequipped root in inventory", player, 0));

        if (Boolean.getBoolean("leggings.test.boots") && !ForbiddenBootsChecks.only()) {
            player = player(server);
            equip(player, root.copy());
            player.setItemSlot(EquipmentSlot.FEET,
                    BootsCoexistence.pair(nativeItem("draco_arcanus_boots"), new ItemStack(Items.DIAMOND_BOOTS)).getFirst());
            ((EquipmentUpdatesInvoker) player).leggings$detectEquipmentUpdates();
            require(player.getAttributeValue(attribute()) == 3, "Mixed Boots/Leggings did not add independent slot contributions");
            actors.add(new Actor("mixed Boots plus mixed Leggings", player, 3));
        }
    }

    private static void nativeSaveControl(MinecraftServer server) throws Exception {
        var nativePlayer = player(server);
        equip(nativePlayer, nativeItem(ForbiddenBootsChecks.only() ? "tyr_boots" : "tyr_leggings"));
        var nativeProblems = saveProblems(nativePlayer);
        require(nativeProblems.contains("Unregistered holder") && nativeProblems.contains("RegistryEntry")
                        && nativeProblems.contains("attributes"),
                "Pinned native-only Valhelsia entity-save control changed: " + nativeProblems);

        var mixedPlayer = player(server);
        var root = ForbiddenBootsChecks.only()
                ? ForbiddenBootsChecks.pair(server, nativeItem("tyr_boots"), new ItemStack(Items.DIAMOND_BOOTS)).getFirst()
                : MixedLeggingsFactory.createPair(nativeItem("tyr_leggings"), new ItemStack(Items.DIAMOND_LEGGINGS), CompositionRules.defaults()).primary();
        equip(mixedPlayer, root);
        require(saveProblems(mixedPlayer).isEmpty(), "Fresh mixed-only equipment produced entity serialization errors");
        equip(nativePlayer, root);
        var warmedProblems = saveProblems(nativePlayer);
        require(warmedProblems.contains("Unregistered holder") && warmedProblems.contains("RegistryEntry"),
                "Native-to-mixed history no longer retains the native AttributeInstance wrapper");
        actors.add(new Actor("native Tyr then mixed Tyr", nativePlayer, 2));
        actors.add(new Actor("fresh mixed Tyr save control", mixedPlayer, 2));
        Files.writeString(report("native-save"), "Native Tyr only, no mixed equipped:\n" + nativeProblems
                + "\nFresh mixed Tyr only: clean entity save\nNative then mixed:\n" + warmedProblems + "\n");
    }

    private static String saveProblems(ServerPlayer player) {
        var problems = new ProblemReporter.Collector();
        var output = TagValueOutput.createWithContext(problems, player.registryAccess());
        player.saveWithoutId(output);
        output.buildResult();
        return problems.getReport();
    }

    static List<Sample> samples(MinecraftServer server) {
        if (ForbiddenBootsChecks.only()) {
            try {
                return ForbiddenBootsChecks.aurealSamples(server);
            } catch (Exception failure) {
                throw new AssertionError("Could not create standalone Boots Aureal controls", failure);
            }
        }
        var result = new ArrayList<Sample>();
        result.add(new Sample("plain", new ItemStack(Items.DIAMOND_LEGGINGS), 0));
        for (String tier : List.of("draco_arcanus", "tyr")) {
            var source = nativeItem(tier + "_leggings");
            double rate = tier.equals("tyr") ? 2 : 1;
            require(source.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()
                            .stream().noneMatch(entry -> entry.attribute().equals(attribute())),
                    "Fixture no longer distinguishes event-provided attributes from raw components");
            result.add(new Sample(tier + " native", source.copy(), rate));
            addPair(result, tier + "_leggings/diamond", source, new ItemStack(Items.DIAMOND_LEGGINGS), rate, 0);
            addPair(result, tier + " duplicate", source, source.copy(), rate, rate);
        }
        addPair(result, "Draco/Tyr", nativeItem("draco_arcanus_leggings"), nativeItem("tyr_leggings"), 1, 2);

        var rules = new CompositionRules(new AttributeRuleSet(AttributePolicy.SEPARATE,
                Map.of(ATTRIBUTE, AttributePolicy.AVERAGE)), CompositionRules.defaults().components());
        var fractional = MixedLeggingsFactory.createPair(nativeItem("draco_arcanus_leggings"), new ItemStack(Items.DIAMOND_LEGGINGS), rules);
        result.add(new Sample("explicit average half", fractional.primary(), .5));
        fractional = MixedLeggingsFactory.createPair(nativeItem("draco_arcanus_leggings"), nativeItem("tyr_leggings"), rules);
        result.add(new Sample("explicit average one and half", fractional.inverse(), 1.5));
        return result;
    }

    private static void addPair(List<Sample> result, String name, ItemStack first, ItemStack second, double primary, double inverse) {
        var firstBefore = first.copy();
        var secondBefore = second.copy();
        var pair = MixedLeggingsFactory.createPair(first, second, CompositionRules.defaults());
        require(ItemStack.matches(first, firstBefore) && ItemStack.matches(second, secondBefore), "Effective query mutated inputs");
        result.add(new Sample(name + " primary", pair.primary(), primary));
        result.add(new Sample(name + " inverse", pair.inverse(), inverse));
    }

    private static void codecs(MinecraftServer server, ItemStack root) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        var loaded = ItemStack.CODEC.parse(ops, ItemStack.CODEC.encodeStart(ops, root).getOrThrow()).getOrThrow();
        require(ItemStack.matches(root, loaded), "Native fixture lost state in persistent codec");
        ItemStack.hashItemAndComponents(root);
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buffer, root);
            require(ItemStack.matches(root, ItemStack.STREAM_CODEC.decode(buffer)), "Native fixture lost state in network codec");
        } finally {
            buffer.release();
        }
        var data = MixedLeggingsStructure.validData(root).orElse(null);
        if (data != null) {
            require(root.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
                            .allMatch(entry -> entry.attribute() == BuiltInRegistries.ATTRIBUTE.get(entry.attribute().unwrapKey().orElseThrow()).orElseThrow()),
                    "Persisted root retained an event wrapper holder");
            // Fractional samples intentionally use explicit rules, unlike default recomposition.
            double amount = root.get(DataComponents.ATTRIBUTE_MODIFIERS).modifiers().stream()
                    .filter(entry -> entry.attribute().equals(attribute())).mapToDouble(entry -> entry.modifier().amount()).sum();
            if (amount == Math.floor(amount)) {
                var recomposed = MixedLeggingsRecomposer.recompose(root, data.left().source(), data.right().source());
                require(ItemStack.matches(root, recomposed), "Unchanged source recomposition lost native effective attributes");
            }
        }
    }

    static void equip(ServerPlayer player, ItemStack root) {
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        player.setItemSlot(slot(root), root.copy());
        ((EquipmentUpdatesInvoker) player).leggings$detectEquipmentUpdates();
    }

    static EquipmentSlot slot(ItemStack stack) {
        var equipment = stack.get(DataComponents.EQUIPPABLE);
        // Existing bare/malformed Leggings controls intentionally have no equipment component.
        return equipment == null ? EquipmentSlot.LEGS : equipment.slot();
    }

    private static ServerPlayer player(MinecraftServer server) {
        var player = RepairFixtures.player(server);
        // Native attachment sync queries connection attributes even for disconnected actors.
        // An unnegotiated in-memory channel supplies them without pretending a client received data.
        new EmbeddedChannel(player.connection.getConnection());
        return player;
    }

    static Holder<Attribute> attribute() {
        return BuiltInRegistries.ATTRIBUTE.get(ATTRIBUTE).orElseThrow();
    }

    static ItemStack nativeItem(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.get(Identifier.fromNamespaceAndPath("forbidden_arcanus", name)).orElseThrow().value());
    }

    @SuppressWarnings("unchecked")
    private static int aureal(ServerPlayer player) throws ReflectiveOperationException {
        // Only the observation bridge is reflective; the native event owns all resource writes.
        var type = (Supplier<AttachmentType<Object>>) Class.forName("com.stal111.forbidden_arcanus.core.init.other.ModAttachmentTypes")
                .getField("AUREAL").get(null);
        var storage = player.getData(type.get());
        return (int) storage.getClass().getMethod("amount").invoke(storage);
    }

    static Path report(String phase) {
        return Path.of(System.getProperty("leggings.test.report"), "forbidden-" + phase + ".txt");
    }

    record Sample(String name, ItemStack stack, double rate) {}
    private record Actor(String name, ServerPlayer player, double rate) {}
}
