package games.alejandrocoria.mismatchedboots.platform;

import games.alejandrocoria.mismatchedboots.platform.services.IPlatformHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

public class NeoForgePlatformHelper implements IPlatformHelper {
    @Override
    public Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.getCurrent().isProduction();
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
