package games.alejandrocoria.mismatchedboots.platform;

import games.alejandrocoria.mismatchedboots.attribute.AttributeRuleSet;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireAttributesCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireEarthquakeCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireGroundStompCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireLavaRepairCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireResourcesCompatibility;
import games.alejandrocoria.mismatchedboots.compatibility.justdirethings.JustDireUpgradesCompatibility;
import games.alejandrocoria.mismatchedboots.interaction.MixedBootsSmithingPolicy;
import games.alejandrocoria.mismatchedboots.platform.services.IPlatformHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public class NeoForgePlatformHelper implements IPlatformHelper {
    @Override
    public void finalizeMixedBootsCreation(ItemStack output, AttributeRuleSet attributes) {
        JustDireLavaRepairCompatibility.sanitizeProgress(output);
        JustDireUpgradesCompatibility.initializeUpgrades(output);
        JustDireResourcesCompatibility.initializeCharge(output);
        JustDireGroundStompCompatibility.initializeCooldown(output);
        JustDireEarthquakeCompatibility.initializeCooldown(output);
        JustDireAttributesCompatibility.prepare(null, output, attributes);
    }

    @Override
    public void finalizeMixedBootsRecomposition(ItemStack previous, ItemStack output, AttributeRuleSet attributes) {
        JustDireLavaRepairCompatibility.sanitizeProgress(output);
        JustDireUpgradesCompatibility.preserveState(previous, output);
        JustDireResourcesCompatibility.preserveCharge(previous, output);
        JustDireAttributesCompatibility.prepare(previous, output, attributes);
    }

    @Override
    public Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public MixedBootsSmithingPolicy.RootAttempt tryRootSmithing(
            ServerLevel level, ItemStack template, ItemStack root, ItemStack addition) {
        return JustDireUpgradesCompatibility.smithing(level, template, root, addition);
    }

    @Override
    public boolean canCombineRepair(ItemStack stack) {
        return stack.isCombineRepairable();
    }

    @Override
    public ItemAttributeModifiers getCompositionAttributeModifiers(ItemStack source) {
        ItemAttributeModifiers effective = source.copy().getAttributeModifiers();
        ItemAttributeModifiers.Builder modifiers = ItemAttributeModifiers.builder();
        for (ItemAttributeModifiers.Entry entry : effective.modifiers()) {
            var key = entry.attribute().unwrapKey().orElseThrow(() -> new IllegalArgumentException(
                    "Attribute modifier uses an unregistered attribute"));
            // Event-provided holder wrappers can work at runtime but fail the
            // component codec used for persistence and inventory hashes.
            var attribute = BuiltInRegistries.ATTRIBUTE.get(key).orElseThrow(() -> new IllegalArgumentException(
                    "Attribute modifier uses an unregistered attribute: " + key.identifier()));
            modifiers.add(attribute, entry.modifier(), entry.slot(), entry.display());
        }
        return modifiers.build();
    }
}
