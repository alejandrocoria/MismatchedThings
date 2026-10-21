package games.alejandrocoria.mismatchedboots.config;

import com.mojang.serialization.Lifecycle;
import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleDefaults;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttributeRulesConfigTest {
    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void firstLoadCreatesCanonicalConfigAndCleanRoundTripDoesNotChangeIt() throws IOException {
        Path path = configPath();
        AttributeRuleSet first = new AttributeRulesConfig(path).load();
        String generated = Files.readString(path);

        assertEquals(AttributeRuleDefaults.ruleSet(), first);
        assertEquals(expectedGeneratedConfig(), generated);
        assertFalse(generated.contains("configVersion"));
        assertFalse(generated.contains("migration"));

        AttributeRuleSet second = new AttributeRulesConfig(path).load();
        assertEquals(first, second);
        assertEquals(generated, Files.readString(path));
    }

    @Test
    void literalQuotedIdentifierKeepsDotsSlashesHyphensAndUnderscores() throws IOException {
        Path path = configPath();
        String id = "example.mod:heat/resistance-test_value";
        Files.writeString(path, """
                [attributes]
                fallback = "separate"

                [attributes."%s"]
                policy = "maximum"
                """.formatted(id));

        AttributeRuleSet rules = new AttributeRulesConfig(path).load();

        assertEquals(AttributePolicy.MAXIMUM, rules.policyFor(Identifier.parse(id)));
        assertTrue(Files.readString(path).contains("[attributes.\"" + id + "\"]"));
    }

    @Test
    void saveUsesBuiltInOrderThenSortedCustomOrder() throws IOException {
        Path path = configPath();
        Files.writeString(path, """
                [attributes]
                fallback = "separate"
                unknown = true

                [attributes."zeta:last"]
                policy = "combine"

                [attributes."alpha:first"]
                policy = "minimum"
                """);

        new AttributeRulesConfig(path).load();
        String saved = Files.readString(path);

        assertOrdered(saved,
                "[attributes.\"minecraft:armor\"]",
                "[attributes.\"minecraft:waypoint_transmit_range\"]",
                "[attributes.\"neoforge:swim_speed\"]",
                "[attributes.\"neoforge:nametag_distance\"]",
                "[attributes.\"neoforge:creative_flight\"]",
                "[attributes.\"silentgear:magic_armor\"]",
                "[attributes.\"alpha:first\"]",
                "[attributes.\"zeta:last\"]");
        assertFalse(saved.contains("unknown"));
    }

    @Test
    void existingConfigsInheritOptionalModDefaultsWithoutRewritingAndCanOverrideThem() throws IOException {
        Path path = configPath();
        String existing = """
                [attributes]
                fallback = "maximum"
                """;
        Files.writeString(path, existing);

        AttributeRuleSet inherited = new AttributeRulesConfig(path).load();

        assertEquals(AttributePolicy.AVERAGE, inherited.policyFor(id("neoforge:swim_speed")));
        assertEquals(AttributePolicy.AVERAGE, inherited.policyFor(id("neoforge:nametag_distance")));
        assertEquals(AttributePolicy.SEPARATE, inherited.policyFor(id("neoforge:creative_flight")));
        assertEquals(AttributePolicy.AVERAGE, inherited.policyFor(id("silentgear:magic_armor")));
        assertEquals(existing, Files.readString(path));

        String overridden = existing + """

                [attributes."neoforge:swim_speed"]
                policy = "separate"

                [attributes."silentgear:magic_armor"]
                policy = "separate"
                """;
        Files.writeString(path, overridden);

        AttributeRuleSet rules = new AttributeRulesConfig(path).load();

        assertEquals(AttributePolicy.SEPARATE, rules.policyFor(id("neoforge:swim_speed")));
        assertEquals(AttributePolicy.AVERAGE, rules.policyFor(id("neoforge:nametag_distance")));
        assertEquals(AttributePolicy.SEPARATE, rules.policyFor(id("neoforge:creative_flight")));
        assertEquals(AttributePolicy.SEPARATE, rules.policyFor(id("silentgear:magic_armor")));
        assertEquals(overridden, Files.readString(path));
    }

    @Test
    void stepHeightInheritsMaximumButPreservesAnExplicitAverageWithoutRewriting() throws IOException {
        Path path = configPath();
        String existing = """
                [attributes]
                fallback = "separate"
                """;
        Files.writeString(path, existing);
        assertEquals(AttributePolicy.MAXIMUM,
                new AttributeRulesConfig(path).load().policyFor(id("minecraft:step_height")));
        assertEquals(existing, Files.readString(path));

        String overridden = existing + """

                [attributes."minecraft:step_height"]
                policy = "average"
                """;
        Files.writeString(path, overridden);
        assertEquals(AttributePolicy.AVERAGE,
                new AttributeRulesConfig(path).load().policyFor(id("minecraft:step_height")));
        assertEquals(overridden, Files.readString(path));
    }

    @Test
    void missingAndInvalidValuesUseDocumentedInheritance() throws IOException {
        Path path = configPath();
        Files.writeString(path, """
                [attributes]
                fallback = "maximum"

                [attributes."minecraft:armor"]

                [attributes."example:missing"]

                [attributes."example:invalid"]
                policy = "Average"
                """);

        AttributeRuleSet rules = new AttributeRulesConfig(path).load();

        assertEquals(AttributePolicy.MAXIMUM, rules.fallback());
        assertEquals(AttributePolicy.AVERAGE, rules.policyFor(id("minecraft:armor")));
        assertEquals(AttributePolicy.MAXIMUM, rules.policyFor(id("example:missing")));
        assertEquals(AttributePolicy.MAXIMUM, rules.policyFor(id("example:invalid")));
        assertTrue(Files.readString(path).contains("policy = \"maximum\""));
    }

    @Test
    void malformedAndDuplicateTablesAreBackedUpAndReset() throws IOException {
        assertMalformedRecovery("[attributes\n", "syntactically malformed");
        assertMalformedRecovery("""
                [attributes."minecraft:armor"]
                policy = "average"

                [attributes."minecraft:armor"]
                policy = "maximum"
                """, "duplicate table");
    }

    @Test
    void invalidIdsShapesAndUnknownFieldsAreRemoved() throws IOException {
        Path path = configPath();
        Files.writeString(path, """
                root_unknown = true

                [attributes]
                fallback = 12
                "minecraft:luck" = "average"

                [attributes."missing_namespace"]
                policy = "combine"

                [attributes."Minecraft:armor"]
                policy = "maximum"

                [attributes."example:valid"]
                policy = "minimum"
                unknown = "remove me"
                """);

        AttributeRuleSet rules = new AttributeRulesConfig(path).load();
        String saved = Files.readString(path);

        assertEquals(AttributePolicy.SEPARATE, rules.fallback());
        assertEquals(AttributePolicy.MINIMUM, rules.policyFor(id("example:valid")));
        assertFalse(saved.contains("root_unknown"));
        assertFalse(saved.contains("missing_namespace"));
        assertFalse(saved.contains("Minecraft:armor"));
        assertFalse(saved.contains("remove me"));
        assertFalse(saved.contains("\"minecraft:luck\" ="));
    }

    @Test
    void registeredAndDormantCustomIdsAreBothRetained() throws IOException {
        Identifier registeredId = id("test:registered_heat");
        MappedRegistry<Attribute> registry = new MappedRegistry<>(Registries.ATTRIBUTE, Lifecycle.stable());
        Holder<Attribute> registered = registry.register(
                ResourceKey.create(Registries.ATTRIBUTE, registeredId),
                new RangedAttribute("attribute.test.registered_heat", 0.0, -100.0, 100.0),
                RegistrationInfo.BUILT_IN);
        registry.freeze();
        assertEquals(registeredId, registered.unwrapKey().orElseThrow().identifier());

        Path path = configPath();
        Files.writeString(path, """
                [attributes]
                fallback = "separate"

                [attributes."test:registered_heat"]
                policy = "maximum"

                [attributes."absentmod:dormant_heat"]
                policy = "combine"
                """);

        AttributeRuleSet rules = new AttributeRulesConfig(path).load();

        assertEquals(AttributePolicy.MAXIMUM, rules.policyFor(registeredId));
        assertEquals(AttributePolicy.COMBINE, rules.policyFor(id("absentmod:dormant_heat")));
        assertTrue(rules.rules().containsKey(id("absentmod:dormant_heat")));
    }

    @Test
    void cleanLoadPreservesContentTimestampAndArbitraryComments() throws IOException {
        Path path = configPath();
        new AttributeRulesConfig(path).load();
        String withUserComment = "# user's arbitrary comment\n" + Files.readString(path);
        Files.writeString(path, withUserComment);
        FileTime preservedTime = FileTime.fromMillis(1_700_000_000_000L);
        Files.setLastModifiedTime(path, preservedTime);

        new AttributeRulesConfig(path).load();

        assertEquals(withUserComment, Files.readString(path));
        assertEquals(preservedTime, Files.getLastModifiedTime(path));
    }

    @Test
    void dirtyRebuildIsCanonicalAndMayDiscardArbitraryComments() throws IOException {
        Path path = configPath();
        new AttributeRulesConfig(path).load();
        Files.writeString(path, "# user's arbitrary comment\n" + Files.readString(path)
                + "\n[attributes.\"example:dirty\"]\nunknown = true\n");

        new AttributeRulesConfig(path).load();
        String rebuilt = Files.readString(path);

        assertFalse(rebuilt.contains("user's arbitrary comment"));
        assertFalse(rebuilt.contains("unknown ="));
        assertTrue(rebuilt.contains("Attribute modifier composition rules."), rebuilt);
        assertTrue(rebuilt.contains("Policy used by valid attribute IDs"), rebuilt);
    }

    @Test
    void returnedSnapshotsAreImmutableAndLoadsAreDefensive() throws IOException {
        Path path = configPath();
        AttributeRuleSet first = new AttributeRulesConfig(path).load();

        assertThrows(UnsupportedOperationException.class,
                () -> first.rules().put(id("example:mutate"), AttributePolicy.REJECT));
        String generated = Files.readString(path);
        int armorCategory = generated.indexOf("[attributes.\"minecraft:armor\"]");
        int armorPolicy = generated.indexOf("policy = \"average\"", armorCategory);
        assertTrue(armorCategory >= 0 && armorPolicy >= 0, generated);
        Files.writeString(path, generated.substring(0, armorPolicy)
                + "policy = \"maximum\""
                + generated.substring(armorPolicy + "policy = \"average\"".length()));
        AttributeRuleSet second = new AttributeRulesConfig(path).load();

        assertEquals(AttributePolicy.AVERAGE, first.policyFor(id("minecraft:armor")));
        assertEquals(AttributePolicy.MAXIMUM, second.policyFor(id("minecraft:armor")));
    }

    @Test
    void generatedConfigContainsNoVersionOrMigrationField() throws IOException {
        Path path = configPath();
        new AttributeRulesConfig(path).load();
        String generated = Files.readString(path);

        assertFalse(generated.toLowerCase().contains("version"));
        assertFalse(generated.toLowerCase().contains("migration"));
    }

    private void assertMalformedRecovery(String malformed, String description) throws IOException {
        Path directory = temporaryDirectory.resolve(description.replace(' ', '-'));
        Files.createDirectories(directory);
        Path path = directory.resolve(AttributeRulesConfig.FILE_NAME);
        Files.writeString(path, malformed);

        AttributeRuleSet rules = new AttributeRulesConfig(path).load();

        assertEquals(AttributeRuleDefaults.ruleSet(), rules);
        assertTrue(Files.readString(path).contains("fallback = \"separate\""));
        List<Path> backups;
        try (var paths = Files.list(directory)) {
            backups = paths.filter(candidate -> candidate.getFileName().toString().endsWith(".bak")).toList();
        }
        assertEquals(1, backups.size());
        assertEquals(malformed, Files.readString(backups.getFirst()));
    }

    private Path configPath() throws IOException {
        Path directory = temporaryDirectory.resolve("config");
        Files.createDirectories(directory);
        return directory.resolve(AttributeRulesConfig.FILE_NAME);
    }

    private static Identifier id(String value) {
        return Identifier.parse(value);
    }

    private static void assertOrdered(String text, String... fragments) {
        int previous = -1;
        List<String> missing = new ArrayList<>();
        for (String fragment : fragments) {
            int current = text.indexOf(fragment);
            if (current < 0) {
                missing.add(fragment);
            }
            assertTrue(current > previous, () -> "Expected canonical order; missing=" + missing + "\n" + text);
            previous = current;
        }
    }

    private static String expectedGeneratedConfig() {
        String newline = System.lineSeparator();
        StringBuilder expected = new StringBuilder()
                .append("#Attribute modifier composition rules. Available policies: ")
                .append("average, combine, minimum, maximum, separate, reject.").append(newline)
                .append("[attributes]").append(newline)
                .append("\t#Policy used by valid attribute IDs without an explicit rule.").append(newline)
                .append("\tfallback = \"separate\"").append(newline);
        AttributeRuleDefaults.rules().forEach((attributeId, policy) -> expected
                .append(newline).append("\t[attributes.\"")
                .append(attributeId)
                .append("\"]").append(newline).append("\t\tpolicy = \"")
                .append(policy.serializedName())
                .append("\"").append(newline));
        return expected.toString();
    }
}
