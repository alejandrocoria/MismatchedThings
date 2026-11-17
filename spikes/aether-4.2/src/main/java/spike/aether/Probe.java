package spike.aether;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.compatibility.MixedBootsSources;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.common.util.AttributeTooltipContext;
import net.neoforged.neoforge.event.AddAttributeTooltipsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;

@Mod("aether42_spike")
public final class Probe {
    public static final String PHASE = System.getProperty("aether.spike.phase");
    public static final Path REPORT = Path.of(System.getProperty("aether.spike.report"));
    private static boolean finished;

    public Probe() {
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            ClientProbe.register();
        } else {
            NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> run(event.getServer()));
        }
    }

    private static void run(MinecraftServer server) {
        if (finished) { return; }
        finished = true;
        var report = new StringBuilder("Aether alpha.4.2 spike; phase=" + PHASE + "\n");
        try {
            switch (PHASE) {
                case "old" -> generate(server, report);
                case "absent" -> absent(server, report);
                case "control" -> control(server, report);
                case "candidate" -> {
                    migrate(server, report, "old.json", "converted.json");
                    migrate(server, report, "absent.json", "restored.json");
                    sets(server, report);
                    serverTooltips(server, report);
                }
                case "restart" -> migrate(server, report, "converted.json", "reopened.json");
                default -> throw new AssertionError("Unexpected server phase " + PHASE);
            }
            Files.writeString(REPORT.resolve(PHASE + ".txt"), report);
            Files.writeString(REPORT.resolve(PHASE + ".ok"), "passed\n");
        } catch (Throwable failure) {
            fail(report, failure);
        } finally {
            server.halt(false);
        }
    }

    private static void generate(MinecraftServer server, StringBuilder report) throws Exception {
        var output = new JsonArray();
        for (boolean boots : List.of(true, false)) {
            String suffix = boots ? "_boots" : "_leggings";
            var source = item("neptune" + suffix);
            source.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved source"));
            source.set(DataComponents.REPAIR_COST, 7);
            source.setDamageValue(13);
            var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            source.set(component("reinforcement_tier"), component("reinforcement_tier").codec()
                    .parse(ops, JsonParser.parseString("\"SECOND\"")).getOrThrow());
            source.set(component("charms"), component("charms").codec().parse(ops, JsonParser.parseString("""
                    {"holders":[{"type":"armor","tier":"two","stack":{"id":"aether_ii:charm_of_health_1","count":1}},
                                {"type":"armor","tier":"two","stack":{}}]}
                    """)).getOrThrow());
            for (int kind = 0; kind < 3; kind++) {
                var first = source.copy();
                if (kind == 1) {
                    // Explicit override inside history must migrate too, without rebuilding that source.
                    first.set(component("armor_set"), item("burrukai_plate" + suffix).get(component("armor_set")));
                }
                var other = kind == 0 ? new ItemStack(boots ? Items.DIAMOND_BOOTS : Items.DIAMOND_LEGGINGS)
                        : kind == 1 ? item("zanite" + suffix) : source.copy();
                for (var root : pair(boots, first, other)) {
                    root.set(DataComponents.CUSTOM_NAME, Component.literal("Preserved root " + boots + "/" + kind));
                    root.setDamageValue(19);
                    var custom = new CompoundTag();
                    custom.putBoolean("flag", true);
                    custom.putString("aether_ii:armor_set", "must remain opaque");
                    root.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
                    var encoded = encode(server, root);
                    var patch = encoded.getAsJsonObject().getAsJsonObject("components");
                    require(patch.has("aether_ii:armor_set") || kind == 0, "Fixture lacks expected owner set");
                    require(LocalConversion.convert(ops, encoded).equals(encoded), "Converter changed alpha.4.1 input");
                    output.add(encoded);
                }
            }
        }
        Files.writeString(REPORT.resolve("old.json"), output.toString());
        report.append("12 real alpha.4.1 roots, both products/orientations: vanilla partner, explicit source set override, duplicate; ")
                .append("native charm, tier, names, repair cost, damage, attributes and opaque custom data\n");
    }

    private static void absent(MinecraftServer server, StringBuilder report) throws Exception {
        var output = new JsonArray();
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        for (var raw : read("old.json")) {
            require(LocalConversion.convert(ops, raw).equals(raw), "Missing native mod changed archived input");
            var stack = ItemStack.CODEC.parse(ops, raw).getOrThrow();
            require(suspended(stack), "Missing source was not suspended");
            var encoded = encode(server, stack);
            require(LocalConversion.convert(ops, encoded).equals(encoded), "Absent archive bytes changed");
            require(encode(server, ItemStack.CODEC.parse(ops, encoded).getOrThrow()).equals(encoded), "Absent second save changed archive");
            output.add(encoded);
        }
        Files.writeString(REPORT.resolve("absent.json"), output.toString());
        report.append("12 missing-source shells survive two saves; conversion leaves archives untouched without Aether\n");
    }

    private static void control(MinecraftServer server, StringBuilder report) throws Exception {
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var old = read("old.json");
        int suspended = 0;
        int active = 0;
        for (var raw : old) {
            var result = ItemStack.CODEC.parse(ops, raw);
            require(result.result().isPresent(), "Old root was lost, rather than preserved or loaded");
            if (suspended(result.getOrThrow())) {
                suspended++;
            } else {
                require(managed(result.getOrThrow()), "Control root neither active nor suspended");
                active++;
            }
        }
        require(suspended > 0 && active > 0, "Expected both explicit old components and absent/default controls");
        var nativeOld = JsonParser.parseString("""
                {"id":"aether_ii:neptune_boots","count":1,"components":{"aether_ii:armor_set":"aether_ii:armor/neptune"}}
                """);
        require(ItemStack.CODEC.parse(ops, nativeOld).isError(), "Native old-string control unexpectedly decoded");
        require(LocalConversion.convert(ops, nativeOld).equals(nativeOld), "Local migration touched native item");
        report.append("Unchanged production: suspended=").append(suspended).append("; active=").append(active)
                .append("; native explicit old component fails decoding, no native codec fallback\n");
    }

    private static void migrate(MinecraftServer server, StringBuilder report, String input, String output) throws Exception {
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var migrated = new JsonArray();
        var originals = read("old.json");
        int index = 0;
        for (var raw : read(input)) {
            var converted = LocalConversion.convert(ops, raw);
            require(LocalConversion.convert(ops, converted).equals(converted), "Conversion is not idempotent");
            var root = ItemStack.CODEC.parse(ops, converted).getOrThrow();
            require(managed(root), "Converted history is not fully active " + index);
            var encoded = encode(server, root);
            var expected = LocalConversion.convert(ops, originals.get(index));
            require(encoded.equals(expected), "Unrelated complete root/history changed " + index + "\n" + encoded + "\n" + expected);
            var nbtOps = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            var oldNbt = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, originals.get(index));
            var nbtRoot = ItemStack.CODEC.parse(nbtOps, LocalConversion.convert(nbtOps, oldNbt)).getOrThrow();
            require(ItemStack.matches(root, nbtRoot), "NBT/JSON migration disagreement " + index);
            migrated.add(encoded);
            index++;
        }
        Files.writeString(REPORT.resolve(output), migrated.toString());
        report.append(input).append(" -> ").append(output).append(": ").append(index)
                .append(" complete roots active; exact expected serialization, both ops, idempotent\n");
    }

    private static void sets(MinecraftServer server, StringBuilder report) throws Exception {
        var subject = EntityType.ZOMBIE.create(server.overworld(), EntitySpawnReason.COMMAND);
        require(subject != null, "No equipment subject");
        var equipment = Class.forName("com.aetherteam.aetherii.item.equipment.EquipmentUtil");
        var count = equipment.getMethod("getArmorCount", LivingEntity.class, TagKey.class);
        var ability = equipment.getMethod("hasArmorAbility", LivingEntity.class, TagKey.class);
        for (String family : List.of("beast_pelt", "burrukai_plate", "zanite", "arkenium", "gravitite", "neptune")) {
            var requested = tag(item(family + "_boots").get(component("armor_set")));
            subject.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            subject.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            for (boolean boots : List.of(true, false)) {
                String suffix = boots ? "_boots" : "_leggings";
                var source = item(family + suffix);
                var other = new ItemStack(boots ? Items.DIAMOND_BOOTS : Items.DIAMOND_LEGGINGS);
                for (var root : pair(boots, source, other)) {
                    clear(subject);
                    subject.setItemSlot(boots ? EquipmentSlot.FEET : EquipmentSlot.LEGS, root);
                    var before = encode(server, root);
                    require((int) count.invoke(null, subject, requested) == 1, "Nullable/owner recognition failed " + boots + "/" + family);
                    require(before.equals(encode(server, root)), "Query mutated history");
                    subject.setItemSlot(EquipmentSlot.HEAD, item(family + "_helmet"));
                    subject.setItemSlot(EquipmentSlot.CHEST, item(family + "_chestplate"));
                    require((int) count.invoke(null, subject, requested) == 3 && (boolean) ability.invoke(null, subject, requested), "Native threshold failed");
                }
                clear(subject);
                subject.setItemSlot(boots ? EquipmentSlot.FEET : EquipmentSlot.LEGS, pair(boots, source, source).getFirst());
                require((int) count.invoke(null, subject, requested) == 1, "Duplicate source counted twice");
            }
        }
        clear(subject);
        var feet = pair(true, item("zanite_boots"), item("neptune_boots"));
        var legs = pair(false, item("neptune_leggings"), item("zanite_leggings"));
        for (int orientation = 0; orientation < 2; orientation++) {
            subject.setItemSlot(EquipmentSlot.FEET, feet.get(orientation));
            subject.setItemSlot(EquipmentSlot.LEGS, legs.get(orientation));
            for (String family : List.of("zanite", "neptune")) {
                require((int) count.invoke(null, subject, tag(item(family + "_boots").get(component("armor_set")))) == 2,
                        "Two products/different sets failed");
            }
        }
        clear(subject);
        subject.setItemSlot(EquipmentSlot.MAINHAND, feet.getFirst());
        require((int) count.invoke(null, subject, tag(item("zanite_boots").get(component("armor_set")))) == 0, "Held item contributes");
        subject.discard();

        var player = FakePlayerFactory.getMinecraft(server.overworld());
        clear(player);
        player.setItemSlot(EquipmentSlot.HEAD, item("zanite_helmet"));
        player.setItemSlot(EquipmentSlot.CHEST, item("zanite_chestplate"));
        player.setItemSlot(EquipmentSlot.FEET, pair(true, item("zanite_boots"), new ItemStack(Items.DIAMOND_BOOTS)).getLast());
        var updater = Class.forName("com.aetherteam.aetherii.item.equipment.armor.abilities.ZaniteArmor")
                .getMethod("updatePlayerAttributes", PlayerTickEvent.Pre.class);
        updater.invoke(null, new PlayerTickEvent.Pre(player));
        var modifier = Identifier.parse("aether_ii:armor_set.ability.zanite.attack_speed");
        require(player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED).hasModifier(modifier), "Native effect did not activate");
        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        updater.invoke(null, new PlayerTickEvent.Pre(player));
        require(!player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED).hasModifier(modifier), "Native effect did not deactivate");
        report.append("Operand-only prototype: six families, owner/null root, duplicate once, both products/different sets; native threshold and Zanite activation/removal\n");
    }

    private static void serverTooltips(MinecraftServer server, StringBuilder report) throws Exception {
        var player = FakePlayerFactory.getMinecraft(server.overworld());
        var nativeEvent = Class.forName("com.aetherteam.aetherii.AetherIIEventListeners")
                .getMethod("onAddAttributeTooltips", AddAttributeTooltipsEvent.class);
        for (var raw : readUnchecked("converted.json")) {
            var stack = ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), raw).getOrThrow();
            for (var flag : List.of(TooltipFlag.NORMAL, TooltipFlag.ADVANCED)) {
                var lines = new ArrayList<Component>();
                var context = AttributeTooltipContext.of(player, Item.TooltipContext.of(server.overworld()), TooltipDisplay.DEFAULT, flag);
                nativeEvent.invoke(null, new AddAttributeTooltipsEvent(stack, lines::add, context));
            }
        }
        var nativeStack = item("neptune_boots");
        var context = AttributeTooltipContext.of(player, Item.TooltipContext.of(server.overworld()), TooltipDisplay.DEFAULT, TooltipFlag.NORMAL);
        nativeEvent.invoke(null, new AddAttributeTooltipsEvent(nativeStack, ignored -> {}, context));
        report.append("Relocated common attribute event is server-safe for converted and native items, normal/advanced\n");

        // Do not turn this into a general tooltip repair: measure the bundled native failure separately.
        for (var stack : List.of(nativeStack, ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),
                read("converted.json").get(0)).getOrThrow())) {
            try {
                stack.getTooltipLines(Item.TooltipContext.of(server.overworld()), player, TooltipFlag.NORMAL);
                throw new AssertionError("Recheck expected upstream Nitrogen control: full tooltip unexpectedly succeeded");
            } catch (NoClassDefFoundError failure) {
                require(failure.getMessage().contains("I18n"), "Unexpected server tooltip linkage failure: " + failure);
                report.append("Known native full-tooltip failure for ").append(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                        .append(": Nitrogen references client I18n (not repaired by this spike)\n");
            }
        }
    }

    public static List<ItemStack> pair(boolean boots, ItemStack first, ItemStack second) {
        if (boots) {
            var pair = MixedBootsFactory.createPair(first, second);
            return List.of(pair.primary(), pair.inverse());
        }
        var pair = MixedLeggingsFactory.createPair(first, second);
        return List.of(pair.primary(), pair.inverse());
    }

    public static boolean managed(ItemStack stack) {
        return MixedBootsSources.anySource(stack, source -> true)
                || MixedLeggingsStructure.validData(stack).isPresent();
    }

    public static List<ItemStack> sources(ItemStack stack) {
        if (MixedBootsSources.anySource(stack, source -> true)) {
            var data = ModContent.getMixedBootsData(stack).orElseThrow();
            return List.of(data.left().source(), data.right().source());
        }
        return MixedLeggingsStructure.validData(stack).map(data -> List.of(data.left().source(), data.right().source())).orElse(List.of());
    }

    private static boolean suspended(ItemStack stack) {
        return ModContent.suspendedBootsData(stack).isPresent() || LeggingsContent.suspendedLeggingsData(stack).isPresent();
    }

    public static Object tag(Object value) throws ReflectiveOperationException {
        return value == null || value instanceof TagKey<?> ? value : value.getClass().getMethod("armor").invoke(value);
    }

    @SuppressWarnings("unchecked")
    public static DataComponentType<Object> component(String name) {
        return (DataComponentType<Object>) BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(Identifier.parse("aether_ii:" + name)).orElseThrow();
    }

    public static ItemStack item(String name) {
        return new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.parse("aether_ii:" + name)).orElseThrow());
    }

    public static void clear(LivingEntity entity) {
        for (var slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND)) {
            entity.setItemSlot(slot, ItemStack.EMPTY);
        }
    }

    private static JsonElement encode(MinecraftServer server, ItemStack stack) {
        return ItemStack.CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), stack).getOrThrow();
    }

    private static JsonArray read(String name) throws Exception {
        return JsonParser.parseString(Files.readString(REPORT.resolve(name))).getAsJsonArray();
    }

    private static JsonArray readUnchecked(String name) {
        try { return read(name); } catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    public static void require(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }

    public static void fail(StringBuilder report, Throwable failure) {
        try {
            var text = new StringWriter();
            failure.printStackTrace(new PrintWriter(text));
            Files.writeString(REPORT.resolve(PHASE + ".failure.txt"), report + "\n" + text);
        } catch (Exception writeFailure) {
            throw new IllegalStateException(writeFailure);
        }
    }
}
