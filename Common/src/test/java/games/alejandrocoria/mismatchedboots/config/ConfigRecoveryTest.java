package games.alejandrocoria.mismatchedboots.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleDefaults;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleDefaults;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigRecoveryTest {
    @TempDir
    Path directory;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @ParameterizedTest
    @CsvSource({"true,true,false", "true,false,false", "false,true,false", "false,false,false",
            "true,true,true", "true,false,true", "false,true,true", "false,false,true"})
    void readOrBackupFailureUsesMemoryDefaultsAndPreservesOriginal(
            boolean attributes, boolean unreadable, boolean securityFailure) throws IOException {
        Path path = directory.resolve(attributes ? AttributeRulesConfig.FILE_NAME : ComponentRulesConfig.FILE_NAME);
        byte[] original = (unreadable ? "# user rules\n[attributes]\nfallback = \"maximum\"\n" : "[broken\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(path, original);
        int[] backupAttempts = {0};
        TomlConfigIO files = new TomlConfigIO() {
            @Override
            CommentedConfig read(Path target) throws IOException {
                if (unreadable) {
                    fail();
                }
                return super.read(target);
            }

            @Override
            Path backup(Path target) throws IOException {
                backupAttempts[0]++;
                fail();
                throw new AssertionError("Failure injection returned");
            }

            @Override
            CommentedFileConfig open(Path target) {
                throw new AssertionError("Recovery attempted to write without a successful read/backup");
            }

            private void fail() throws IOException {
                if (securityFailure) {
                    throw new SecurityException("Injected filesystem denial");
                }
                throw new IOException("Injected filesystem failure");
            }
        };

        assertEquals(attributes ? AttributeRuleDefaults.ruleSet() : ComponentRuleDefaults.state(),
                attributes ? new AttributeRulesConfig(path, files).load() : new ComponentRulesConfig(path, files).load());
        assertArrayEquals(original, Files.readAllBytes(path));
        assertEquals(unreadable ? 0 : 1, backupAttempts[0]);
        try (var contents = Files.list(directory)) {
            assertEquals(1, contents.count());
        }
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    void invalidUtf8IsUnreadableNotMalformedAndIsNotRewritten(boolean attributes) throws IOException {
        Path path = directory.resolve(attributes ? AttributeRulesConfig.FILE_NAME : ComponentRulesConfig.FILE_NAME);
        byte[] original = {(byte) 0xC3, (byte) 0x28};
        Files.write(path, original);
        assertEquals(attributes ? AttributeRuleDefaults.ruleSet() : ComponentRuleDefaults.state(),
                attributes ? new AttributeRulesConfig(path).load() : new ComponentRulesConfig(path).load());
        assertArrayEquals(original, Files.readAllBytes(path));
        try (var contents = Files.list(directory)) {
            assertEquals(1, contents.count());
        }
    }
}
