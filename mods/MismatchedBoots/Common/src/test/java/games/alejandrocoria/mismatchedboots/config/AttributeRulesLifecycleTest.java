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
        AttributeRuleState.restoreDefaults();
    }

    @Test
    void serverLifecycleDoesNotOverwriteClientRules() throws IOException {
        AttributeRuleSet client = new AttributeRuleSet(AttributePolicy.MAXIMUM, java.util.Map.of());
        AttributeRuleState.install(client);
        Path path = directory.resolve(AttributeRulesConfig.FILE_NAME);
        Files.writeString(path, """
                [attributes]
                fallback = "separate"
                
                [attributes."minecraft:armor"]
                policy = "combine"
                """);

        AttributeRuleSet loaded = AttributeRulesLifecycle.load(path);

        assertSame(client, AttributeRuleState.current());
        assertSame(loaded, AttributeRulesLifecycle.serverRules());
        assertEquals(AttributePolicy.COMBINE,
                     loaded.policyFor(Identifier.withDefaultNamespace("armor")));

        AttributeRulesLifecycle.onServerStopped();

        assertSame(client, AttributeRuleState.current());
        assertSame(AttributeRuleDefaults.ruleSet(), AttributeRulesLifecycle.serverRules());
    }
}
