package games.alejandrocoria.mismatchedboots.config;

import games.alejandrocoria.mismatchedboots.component.ComponentRuleDefaults;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HellforgedDefaultsTest {
    @TempDir
    Path directory;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void installsMissingDefaultButKeepsExplicitPolicies() throws Exception {
        var id = Identifier.parse("neovitae:hellforged");
        assertEquals("SHARE", ComponentRuleDefaults.rules().get(id).policy().name());
        var missing = directory.resolve("missing.toml");
        Files.writeString(missing, "[components]\n");
        var loaded = new ComponentRulesConfig(missing).load();
        assertEquals("SHARE", loaded.declarations().get(id).policy().name());
        assertTrue(Files.readString(missing).contains("neovitae:hellforged"));
        for (String policy : new String[]{"owner", "omit", "share"}) {
            var explicit = directory.resolve(policy + ".toml");
            Files.writeString(explicit, """
                    [components."neovitae:hellforged"]
                    policy = "%s"
                    """.formatted(policy));
            var rules = new ComponentRulesConfig(explicit).load();
            assertEquals(policy.toUpperCase(java.util.Locale.ROOT), rules.declarations().get(id).policy().name());
        }
    }
}
