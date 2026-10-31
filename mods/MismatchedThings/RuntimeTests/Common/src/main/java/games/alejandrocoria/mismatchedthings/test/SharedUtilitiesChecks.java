package games.alejandrocoria.mismatchedthings.test;

import com.electronwill.nightconfig.core.CommentedConfig;
import games.alejandrocoria.mismatchedthings.api.composition.DurabilityMerger;
import games.alejandrocoria.mismatchedthings.api.composition.DurabilityPolicy;
import games.alejandrocoria.mismatchedthings.api.composition.RepairCostMerger;
import games.alejandrocoria.mismatchedthings.api.composition.RepairCostPolicy;
import games.alejandrocoria.mismatchedthings.api.config.DynamicConfigPart;
import games.alejandrocoria.mismatchedthings.api.config.EnumConfigEntry;
import games.alejandrocoria.mismatchedthings.api.config.TomlConfigIO;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

final class SharedUtilitiesChecks {
    private SharedUtilitiesChecks() {
    }

    static void verify() throws Exception {
        Path path = Path.of("config", "shared-utilities-probe.toml");
        Files.deleteIfExists(path);
        var part = new TestPart();
        var files = new TomlConfigIO(LoggerFactory.getLogger("workbench-test"));
        files.load(path, part);
        WorkbenchChecks.require(Files.readString(path).contains("average"), "Standalone config default was not saved");
        Files.writeString(path, "policy = \"separate\"\n");
        files.load(path, part);
        WorkbenchChecks.require(part.entry.get() == DurabilityPolicy.SEPARATE, "Standalone enum config did not load");

        var first = new ItemStack(Items.IRON_HELMET);
        var second = new ItemStack(Items.GOLDEN_HELMET);
        first.set(DataComponents.MAX_DAMAGE, 101);
        first.set(DataComponents.DAMAGE, 50);
        second.set(DataComponents.MAX_DAMAGE, 101);
        second.set(DataComponents.DAMAGE, 51);
        var result = DurabilityMerger.merge(first, second, DurabilityPolicy.AVERAGE);
        WorkbenchChecks.require(result.primary().equals(new DurabilityMerger.Result(101, 51))
                && result.primary().equals(result.inverse()), "Standalone durability composition changed");
        first.set(DataComponents.REPAIR_COST, 2);
        second.set(DataComponents.REPAIR_COST, 5);
        WorkbenchChecks.require(RepairCostMerger.merge(first, second, RepairCostPolicy.AVERAGE)
                .equals(new RepairCostMerger.Results(4, 4)), "Standalone repair-cost composition changed");
    }

    private static final class TestPart implements DynamicConfigPart {
        private final EnumConfigEntry<DurabilityPolicy> entry = new EnumConfigEntry<>(
                "policy", DurabilityPolicy.AVERAGE, "Fixture only",
                DurabilityPolicy::fromSerializedName, DurabilityPolicy::serializedName);

        @Override
        public boolean load(CommentedConfig config) {
            return entry.load(config);
        }

        @Override
        public void save(CommentedConfig config) {
            entry.save(config);
        }

        @Override
        public void reset() {
            entry.reset();
        }
    }
}
