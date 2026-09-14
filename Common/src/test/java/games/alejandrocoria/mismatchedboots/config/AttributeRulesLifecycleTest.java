package games.alejandrocoria.mismatchedboots.config;

import games.alejandrocoria.mismatchedboots.attribute.AttributePolicy;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleDefaults;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleState;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AttributeRulesLifecycleTest {
    @TempDir
    Path directory;

    @AfterEach
    void restoreDefaults() {
        AttributeRulesLifecycle.onServerStopped();
    }

    @Test
    void serverLifecycleInstallsTheDiskSnapshotAndRestoresDefaults() throws IOException {
        Path path = directory.resolve(AttributeRulesConfig.FILE_NAME);
        Files.writeString(path, """
                [attributes]
                fallback = "separate"

                [attributes."minecraft:armor"]
                policy = "combine"
                """);

        AttributeRuleSet loaded = AttributeRulesLifecycle.load(path);

        assertSame(loaded, AttributeRuleState.current());
        assertSame(loaded, AttributeRulesLifecycle.serverRules());
        assertEquals(AttributePolicy.COMBINE,
                AttributeRuleState.current().policyFor(Identifier.withDefaultNamespace("armor")));

        AttributeRuleState.restoreDefaults();

        assertSame(loaded, AttributeRulesLifecycle.serverRules());
        AttributeRulesLifecycle.onServerStopped();

        assertSame(AttributeRuleDefaults.ruleSet(), AttributeRuleState.current());
        assertSame(AttributeRuleDefaults.ruleSet(), AttributeRulesLifecycle.serverRules());
    }
}
