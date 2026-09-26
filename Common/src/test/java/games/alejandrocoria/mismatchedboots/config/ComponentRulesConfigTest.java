package games.alejandrocoria.mismatchedboots.config;

import games.alejandrocoria.mismatchedboots.component.ComponentNumericKind;
import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentRule;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleDefaults;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.component.DurabilityPolicy;
import games.alejandrocoria.mismatchedboots.component.RepairCostPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentRulesConfigTest {
    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void firstLoadCreatesCanonicalDefaultsAndCleanRoundTripPreservesTheFile() throws IOException {
        Path path = configPath();

        ComponentRules first = new ComponentRulesConfig(path).load();
        String generated = Files.readString(path);
        ComponentRules second = new ComponentRulesConfig(path).load();

        assertSame(ComponentRuleDefaults.state(), first);
        assertEquals(first, second);
        assertEquals(generated, Files.readString(path));
        assertOrdered(generated, "[durability]", "[repair_cost]", "[components]",
                "[components.\"minecraft:damage_resistant\"]",
                "[components.\"minecraft:custom_model_data\"]");
        assertFalse(generated.toLowerCase().contains("version"));
        assertFalse(generated.toLowerCase().contains("migration"));
        assertThrows(UnsupportedOperationException.class,
                () -> first.declarations().put(id("example:mutate"), ComponentRule.of(ComponentPolicy.OWNER)));
    }

    @Test
    void loadsDedicatedOverridesRegisteredRulesAndDormantDeclarations() throws IOException {
        Path path = configPath();
        String dottedId = "example.mod:heat/resistance-test_value";
        Files.writeString(path, """
                [durability]
                policy = "separate"

                [repair_cost]
                policy = "owner"

                [components."minecraft:rarity"]
                policy = "owner"

                [components."minecraft:custom_data"]
                policy = "average"
                number = "int"

                [components."absentmod:dormant_energy"]
                policy = "maximum"
                number = "long"

                [components."%s"]
                policy = "share"
                """.formatted(dottedId));

        ComponentRules rules = new ComponentRulesConfig(path).load();
        String saved = Files.readString(path);

        assertEquals(DurabilityPolicy.SEPARATE, rules.durability());
        assertEquals(RepairCostPolicy.OWNER, rules.repairCost());
        assertEquals(ComponentRule.of(ComponentPolicy.OWNER), rules.declarations().get(id("minecraft:rarity")));
        assertEquals(ComponentRule.numeric(ComponentPolicy.AVERAGE, ComponentNumericKind.INT),
                rules.components().ruleFor(id("minecraft:custom_data")));
        assertTrue(rules.declarations().containsKey(id("absentmod:dormant_energy")));
        assertFalse(rules.components().rules().containsKey(id("absentmod:dormant_energy")));
        assertTrue(saved.contains("[components.\"" + dottedId + "\"]"));
        assertOrdered(saved,
                "[components.\"minecraft:custom_model_data\"]",
                "[components.\"absentmod:dormant_energy\"]",
                "[components.\"example.mod:heat/resistance-test_value\"]",
                "[components.\"minecraft:custom_data\"]");
    }

    @Test
    void missingDefaultsAndExplicitOwnerHaveDifferentCanonicalResults() throws IOException {
        Path path = configPath();
        Files.writeString(path, """
                [durability]
                policy = "average"

                [repair_cost]
                policy = "maximum"

                [components."minecraft:rarity"]
                policy = "owner"
                """);

        ComponentRules rules = new ComponentRulesConfig(path).load();

        assertEquals(ComponentRule.of(ComponentPolicy.OWNER),
                rules.declarations().get(id("minecraft:rarity")));
        assertEquals(ComponentRule.of(ComponentPolicy.INTERSECTION),
                rules.declarations().get(id("minecraft:damage_resistant")));
        String saved = Files.readString(path);
        assertTrue(saved.contains("[components.\"minecraft:damage_resistant\"]"));
        int rarity = saved.indexOf("[components.\"minecraft:rarity\"]");
        assertTrue(rarity >= 0 && saved.indexOf("policy = \"owner\"", rarity) > rarity);
    }

    @Test
    void missingDedicatedTablesRestoreTheirDefaults() throws IOException {
        Path path = configPath();
        Files.writeString(path, """
                [components."minecraft:rarity"]
                policy = "owner"
                """);

        ComponentRules rules = new ComponentRulesConfig(path).load();

        assertEquals(DurabilityPolicy.AVERAGE, rules.durability());
        assertEquals(RepairCostPolicy.MAXIMUM, rules.repairCost());
        String saved = Files.readString(path);
        assertOrdered(saved, "[durability]", "[repair_cost]", "[components]");
    }

    @Test
    void invalidEntriesNormalizeToDefaultsOrOwnerAndFixedFieldsAreDiscarded() throws IOException {
        Path path = configPath();
        Files.writeString(path, """
                root_unknown = true

                [durability]
                policy = "Average"

                [repair_cost]
                policy = "unknown"

                [components."minecraft:enchantable"]
                policy = "average"
                number = "int"

                [components."minecraft:rarity"]
                policy = "average"
                number = "int"

                [components."minecraft:repair_cost"]
                policy = "owner"

                [components."example:invalid"]
                policy = "average"

                [components."example:unknown_field"]
                policy = "same"
                extra = true

                [components."missing_namespace"]
                policy = "same"
                """);

        ComponentRules rules = new ComponentRulesConfig(path).load();
        String saved = Files.readString(path);

        assertEquals(DurabilityPolicy.AVERAGE, rules.durability());
        assertEquals(RepairCostPolicy.MAXIMUM, rules.repairCost());
        assertEquals(ComponentRule.of(ComponentPolicy.MINIMUM),
                rules.declarations().get(id("minecraft:enchantable")));
        assertEquals(ComponentRule.of(ComponentPolicy.SAME),
                rules.declarations().get(id("minecraft:rarity")));
        assertEquals(ComponentRule.of(ComponentPolicy.OWNER),
                rules.declarations().get(id("example:invalid")));
        assertEquals(ComponentRule.of(ComponentPolicy.OWNER),
                rules.declarations().get(id("example:unknown_field")));
        assertFalse(rules.declarations().containsKey(id("minecraft:repair_cost")));
        assertFalse(saved.contains("root_unknown"));
        assertFalse(saved.contains("missing_namespace"));
        assertFalse(saved.contains("extra ="));
        assertFalse(saved.contains("[components.\"minecraft:repair_cost\"]"));
    }

    @Test
    void malformedAndDuplicateTablesAreBackedUpAndReset() throws IOException {
        assertMalformedRecovery("[components\n");
        assertMalformedRecovery("""
                [components."minecraft:rarity"]
                policy = "same"

                [components."minecraft:rarity"]
                policy = "owner"
                """);
    }

    @Test
    void cleanLoadPreservesTimestampAndUserComments() throws IOException {
        Path path = configPath();
        new ComponentRulesConfig(path).load();
        String withComment = "# user comment\n" + Files.readString(path);
        Files.writeString(path, withComment);
        FileTime timestamp = FileTime.fromMillis(1_700_000_000_000L);
        Files.setLastModifiedTime(path, timestamp);

        new ComponentRulesConfig(path).load();

        assertEquals(withComment, Files.readString(path));
        assertEquals(timestamp, Files.getLastModifiedTime(path));
    }

    @Test
    void builtInRegistryResolutionUsesTheActualRegisteredComponentType() throws IOException {
        Path path = configPath();
        Identifier customData = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(DataComponents.CUSTOM_DATA);
        Files.writeString(path, """
                [durability]
                policy = "average"

                [repair_cost]
                policy = "maximum"

                [components."%s"]
                policy = "maximum"
                number = "long"
                """.formatted(customData));

        ComponentRules rules = new ComponentRulesConfig(path).load();

        assertEquals(ComponentRule.numeric(ComponentPolicy.MAXIMUM, ComponentNumericKind.LONG),
                rules.components().ruleFor(customData));
    }

    private void assertMalformedRecovery(String malformed) throws IOException {
        Path directory = temporaryDirectory.resolve("malformed-" + Math.abs(malformed.hashCode()));
        Files.createDirectories(directory);
        Path path = directory.resolve(ComponentRulesConfig.FILE_NAME);
        Files.writeString(path, malformed);

        ComponentRules rules = new ComponentRulesConfig(path).load();

        assertSame(ComponentRuleDefaults.state(), rules);
        List<Path> backups;
        try (var paths = Files.list(directory)) {
            backups = paths.filter(candidate -> candidate.getFileName().toString().endsWith(".bak")).toList();
        }
        assertEquals(1, backups.size());
        assertEquals(malformed, Files.readString(backups.getFirst()));
        assertTrue(Files.readString(path).contains("[durability]"));
    }

    private Path configPath() throws IOException {
        Path directory = temporaryDirectory.resolve("config");
        Files.createDirectories(directory);
        return directory.resolve(ComponentRulesConfig.FILE_NAME);
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
}
