package games.alejandrocoria.mismatchedleggings.test.aether;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import games.alejandrocoria.mismatchedleggings.test.SkeletonChecks;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Frozen native-format inputs, decoded only by packaged production; no prototype conversion or gameplay hooks. */
public final class AetherPersistenceChecks {
    private static final String SET = "aether_ii:armor_set";
    private static final String STAGE = System.getProperty("leggings.test.aetherPersistence");
    private static final Path REPORT = Path.of(System.getProperty("leggings.test.report"));
    private static boolean finished;

    private AetherPersistenceChecks() {
    }

    public static void tick(MinecraftServer server) {
        if (finished || server.getTickCount() < 2) {
            return;
        }
        finished = true;
        var report = new StringBuilder("Production Aether persisted data; stage=" + STAGE + "\n");
        try {
            require(ModList.get().isLoaded("mismatchedboots") && ModList.get().isLoaded("mismatchedleggings"), "Both packaged products required");
            var old = frozen();
            boolean absent = STAGE.startsWith("absent");
            require(ModList.get().isLoaded("aether_ii") != absent, "Native mod not actually removed/reinstalled");
            boolean record = !absent && nativeRecord(server);
            require(absent || record, "Pinned native binary did not supply the record codec");
            String input = switch (STAGE) {
                case "absentagain" -> "absent-roots.json";
                case "restore" -> "absentagain-roots.json";
                case "reopened" -> "restore-roots.json";
                default -> "";
            };
            var entries = input.isEmpty() ? old : read(input);
            var output = new JsonArray();
            int index = 0;
            for (var raw : entries) {
                var root = decode(server, raw);
                var encoded = encode(server, root);
                if (absent) {
                    require(inactive(root), "Absent source was activated");
                    if (!input.isEmpty()) {
                        require(encoded.equals(raw), "Second absent save changed complete shell/archive bytes");
                    }
                } else {
                    require(active(root), "Old root did not become fully active " + index);
                    require(encoded.equals(expected(old.get(index), record)), "Complete root/history mismatch " + index + "\n" + encoded);
                    require(encode(server, decode(server, encoded)).equals(encoded), "Current input is not idempotent");
                    var ops = server.registryAccess().createSerializationContext(NbtOps.INSTANCE);
                    var oldNbt = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, old.get(index));
                    var nbtRoot = ItemStack.CODEC.parse(ops, oldNbt).getOrThrow();
                    require(ItemStack.matches(root, nbtRoot), "Native NBT/JSON migration disagreement " + index);
                }
                output.add(encoded);
                index++;
            }
            Files.writeString(REPORT.resolve(STAGE + "-roots.json"), output.toString());
            report.append(index).append(" complete roots: ").append(absent ? "inactive; preserved original data" : "active; exact expected data")
                    .append("; record=").append(record).append('\n');
            if (STAGE.equals("active")) {
                for (int selected : List.of(0, 6)) {
                    negatives(server, old.get(selected).getAsJsonObject(), record, report);
                    archives(server, old.get(selected).getAsJsonObject(), selected, record, report);
                }
            }
            Files.writeString(REPORT.resolve(STAGE + ".txt"), report);
            SkeletonChecks.passed(STAGE);
        } catch (Throwable failure) {
            SkeletonChecks.failed(STAGE, failure);
        } finally {
            server.halt(false);
        }
    }

    private static void negatives(MinecraftServer server, JsonObject original, boolean record, StringBuilder report) throws Exception {
        var booleanInput = original.deepCopy();
        booleanInput.getAsJsonObject("components").addProperty("minecraft:enchantment_glint_override", false);
        require(encode(server, decode(server, booleanInput)).equals(expected(booleanInput, record)), "Strict JSON boolean changed during active migration");

        for (String fault : List.of("count", "source-count", "missing-source", "unknown-component", "unreadable-source")) {
            var raw = original.deepCopy();
            var patch = raw.getAsJsonObject("components");
            var data = patch.getAsJsonObject(history(raw));
            switch (fault) {
                case "count" -> raw.addProperty("count", 2);
                case "source-count" -> data.getAsJsonObject("left").addProperty("count", 2);
                case "missing-source" -> data.getAsJsonObject("left").addProperty("id", "missing_fixture:neptune_boots");
                case "unknown-component" -> patch.addProperty("missing_fixture:unreadable", 42);
                case "unreadable-source" -> data.getAsJsonObject("left").getAsJsonObject("components").add(SET, JsonParser.parseString("{\"armor\":42}"));
                default -> throw new AssertionError(fault);
            }
            var root = decode(server, raw);
            require(inactive(root), "Invalid/partial input became active: " + fault);
            var saved = root.get(component(archive(raw)));
            var parked = (CompoundTag) saved.getClass().getMethod("original").invoke(saved);
            require(parked.getStringOr(SET, "").equals("aether_ii:armor/neptune"), "Partial migration was archived: " + fault);
            require(encode(server, decode(server, encode(server, root))).equals(encode(server, root)), "Refused data changed on second save: " + fault);
        }

        var removed = original.deepCopy();
        removed.getAsJsonObject("components").remove(SET);
        removed.getAsJsonObject("components").add("!" + SET, new JsonObject());
        var root = decode(server, removed);
        require(active(root) && root.get(component(SET)) == null, "Removed set component was recreated");

        for (String id : List.of("aether_ii:neptune_boots", "minecraft:diamond_boots", original.get("id").getAsString())) {
            var raw = new JsonObject();
            raw.addProperty("id", id);
            raw.add("components", JsonParser.parseString("{\"aether_ii:armor_set\":\"aether_ii:armor/neptune\"}"));
            var result = ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), raw);
            require(result.isError() == record, "Native/unrelated/bare item was migrated: " + id);
        }
        report.append("Strict boolean; count/source-count/missing source/unknown component/unreadable source preserve unconverted originals; ")
                .append("removed component stays removed; native/unrelated/bare item codecs untouched\n");
    }

    private static void archives(MinecraftServer server, JsonObject original, int index, boolean record, StringBuilder report) throws Exception {
        var ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        var shell = read("absentagain-roots.json").get(index).getAsJsonObject();
        String key = archive(shell);
        var type = component(key);
        var saved = type.codec().parse(ops, shell.getAsJsonObject("components").get(key)).getOrThrow();
        var parked = (CompoundTag) saved.getClass().getMethod("original").invoke(saved);
        var projection = (CompoundTag) saved.getClass().getMethod("projection").invoke(saved);
        var conflicts = new CompoundTag();
        conflicts.putString(SET, "aether_ii:armor/zanite");

        for (boolean quarantine : List.of(false, true)) {
            Object refused = quarantine
                    ? saved.getClass().getMethod("quarantined", CompoundTag.class, CompoundTag.class).invoke(null, parked, new CompoundTag())
                    : saved.getClass().getMethod("of", CompoundTag.class, CompoundTag.class, CompoundTag.class).invoke(null, parked, projection, conflicts);
            var raw = shell.deepCopy();
            if (quarantine) {
                projection.keySet().forEach(field -> raw.getAsJsonObject("components").remove(field));
            }
            var bytes = type.codec().encodeStart(ops, refused).getOrThrow();
            raw.getAsJsonObject("components").add(key, bytes);
            var root = decode(server, raw);
            require(inactive(root), "Quarantined/conflicted archive became active");
            Object retained = root.get(type);
            require(parked.equals(retained.getClass().getMethod("original").invoke(retained)), "Refused original was migrated");
            require((boolean) retained.getClass().getMethod("quarantined").invoke(retained) == quarantine, "Quarantine decision changed");
            require((quarantine ? new CompoundTag() : conflicts).equals(retained.getClass().getMethod("conflicts").invoke(retained)), "Archive conflicts changed");
        }

        var edited = shell.deepCopy();
        edited.getAsJsonObject("components").addProperty(SET, "aether_ii:armor/zanite");
        var refused = decode(server, edited);
        require(inactive(refused), "Foreign shell edit was ignored during migration");
        Object retained = refused.get(type);
        require(parked.equals(retained.getClass().getMethod("original").invoke(retained)), "Shell conflict migrated the original");
        require(((CompoundTag) retained.getClass().getMethod("conflicts").invoke(retained)).getStringOr(SET, "")
                .equals("aether_ii:armor/zanite"), "Foreign shell conflict was lost");

        var passive = shell.deepCopy();
        passive.getAsJsonObject("components").addProperty("minecraft:custom_name", "Edited passive shell name");
        var expected = expected(original, record).getAsJsonObject();
        expected.getAsJsonObject("components").addProperty("minecraft:custom_name", "Edited passive shell name");
        require(encode(server, decode(server, passive)).equals(expected), "Passive shell edit lost during eligible migration");
        report.append("Conflicted/quarantined archives retain unconverted originals and refusal state; shell edits evaluated before migration; passive name retained\n");
    }

    /** Independent expected field shape, not a call to the production migration helper. */
    static JsonElement expected(JsonElement input, boolean record) {
        var result = input.deepCopy().getAsJsonObject();
        if (!record) { return result; }
        var patch = result.getAsJsonObject("components");
        wrap(patch);
        var data = patch.getAsJsonObject(history(result));
        for (String side : List.of("left", "right")) {
            var source = data.getAsJsonObject(side);
            if (source.has("components")) { wrap(source.getAsJsonObject("components")); }
        }
        return result;
    }

    private static void wrap(JsonObject patch) {
        if (!patch.has(SET) || !patch.get(SET).isJsonPrimitive()) { return; }
        var value = new JsonObject();
        value.add("armor", patch.get(SET));
        patch.add(SET, value);
    }

    private static boolean nativeRecord(MinecraftServer server) {
        var item = new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.parse("aether_ii:neptune_boots")).orElseThrow());
        var type = component(SET);
        return type.codec().encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), item.get(type)).getOrThrow().isJsonObject();
    }

    private static boolean active(ItemStack root) {
        var id = BuiltInRegistries.ITEM.getKey(root.getItem()).toString();
        return ItemStack.validateStrict(root).result().isPresent() && root.getCount() == 1
                && root.has(component(history(id))) && !root.has(component(archive(id)));
    }

    private static boolean inactive(ItemStack root) {
        var id = BuiltInRegistries.ITEM.getKey(root.getItem()).toString();
        var nativeType = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(Identifier.parse(SET));
        return root.has(component(archive(id))) && !root.has(component(history(id))) && nativeType.filter(root::has).isEmpty();
    }

    private static String history(JsonObject root) {
        return history(root.get("id").getAsString());
    }

    private static String archive(JsonObject root) {
        return archive(root.get("id").getAsString());
    }

    private static String history(String id) {
        return id.endsWith("mixed_boots") ? "mismatchedboots:mixed_boots_data" : "mismatchedleggings:mixed_leggings_data";
    }

    private static String archive(String id) {
        return id.endsWith("mixed_boots") ? "mismatchedboots:suspended_boots" : "mismatchedleggings:suspended_leggings";
    }

    @SuppressWarnings("unchecked")
    private static DataComponentType<Object> component(String key) {
        return (DataComponentType<Object>) BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(Identifier.parse(key)).orElseThrow();
    }

    static ItemStack decode(MinecraftServer server, JsonElement raw) {
        return ItemStack.CODEC.parse(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), raw).getOrThrow();
    }

    static JsonElement encode(MinecraftServer server, ItemStack root) {
        return ItemStack.CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE), root).getOrThrow();
    }

    static JsonArray frozen() throws Exception {
        try (var stream = AetherPersistenceChecks.class.getResourceAsStream("/aether-alpha.4.1-mixed.json")) {
            require(stream != null, "Frozen alpha.4.1 fixture missing");
            return JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonArray();
        }
    }

    private static JsonArray read(String name) throws Exception {
        return JsonParser.parseString(Files.readString(REPORT.resolve(name))).getAsJsonArray();
    }
}
