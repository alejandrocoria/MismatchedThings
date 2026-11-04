package games.alejandrocoria.mismatchedboots.config;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleDefaults;
import games.alejandrocoria.mismatchedboots.component.ComponentPolicy;
import games.alejandrocoria.mismatchedboots.component.ComponentRule;
import games.alejandrocoria.mismatchedboots.component.ComponentRuleDefaults;
import games.alejandrocoria.mismatchedboots.component.ComponentRules;
import games.alejandrocoria.mismatchedboots.network.CompositionRules;
import games.alejandrocoria.mismatchedboots.network.CompositionRulesClientLifecycle;
import games.alejandrocoria.mismatchedthings.api.composition.DurabilityPolicy;
import games.alejandrocoria.mismatchedthings.api.composition.RepairCostPolicy;
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
        CompositionRulesClientLifecycle.onDisconnected();
    }

    @Test
    void serverLifecycleDoesNotOverwriteClientRules() throws IOException {
        ComponentRules client = new ComponentRules(DurabilityPolicy.SEPARATE,
                                                   RepairCostPolicy.AVERAGE, ComponentRuleDefaults.ruleSet());
        CompositionRulesClientLifecycle.install(new CompositionRules(AttributeRuleDefaults.ruleSet(), client));
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

        assertSame(client, CompositionRulesClientLifecycle.synchronizedRules().components());
        assertSame(loaded, ComponentRulesLifecycle.serverRules());
        assertEquals(DurabilityPolicy.SEPARATE, loaded.durability());
        assertEquals(RepairCostPolicy.OWNER, loaded.repairCost());
        assertEquals(ComponentRule.of(ComponentPolicy.OWNER),
                     loaded.declarations().get(Identifier.withDefaultNamespace("rarity")));
        assertEquals(ComponentRule.of(ComponentPolicy.SHARE),
                     loaded.declarations().get(Identifier.parse("futuremod:dormant")));

        ComponentRulesLifecycle.onServerStopped();
        assertSame(client, CompositionRulesClientLifecycle.synchronizedRules().components());
        assertSame(ComponentRuleDefaults.state(), ComponentRulesLifecycle.serverRules());
    }
}
