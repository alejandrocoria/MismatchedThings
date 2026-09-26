package games.alejandrocoria.mismatchedboots.config;

import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentRule;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleDefaults;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleState;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.component.DurabilityPolicy;
import games.alejandrocoria.mismatchedboots.component.RepairCostPolicy;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ComponentRulesLifecycleTest {
    @TempDir
    Path directory;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restoreDefaults() {
        ComponentRulesLifecycle.onServerStopped();
    }

    @Test
    void serverLifecycleAtomicallyInstallsAndRestoresTheCompleteSnapshot() throws IOException {
        Path path = directory.resolve(ComponentRulesConfig.FILE_NAME);
        Files.writeString(path, """
                [durability]
                policy = "separate"

                [repair_cost]
                policy = "owner"

                [components."minecraft:rarity"]
                policy = "owner"

                [components."futuremod:dormant"]
                policy = "share"
                """);

        ComponentRules loaded = ComponentRulesLifecycle.load(path);

        assertSame(loaded, ComponentRuleState.current());
        assertSame(loaded, ComponentRulesLifecycle.serverRules());
        assertEquals(DurabilityPolicy.SEPARATE, loaded.durability());
        assertEquals(RepairCostPolicy.OWNER, loaded.repairCost());
        assertEquals(ComponentRule.of(ComponentPolicy.OWNER),
                loaded.declarations().get(Identifier.withDefaultNamespace("rarity")));
        assertEquals(ComponentRule.of(ComponentPolicy.SHARE),
                loaded.declarations().get(Identifier.parse("futuremod:dormant")));

        ComponentRuleState.restoreDefaults();
        assertSame(loaded, ComponentRulesLifecycle.serverRules());

        ComponentRulesLifecycle.onServerStopped();
        assertSame(ComponentRuleDefaults.state(), ComponentRuleState.current());
        assertSame(ComponentRuleDefaults.state(), ComponentRulesLifecycle.serverRules());
    }
}
