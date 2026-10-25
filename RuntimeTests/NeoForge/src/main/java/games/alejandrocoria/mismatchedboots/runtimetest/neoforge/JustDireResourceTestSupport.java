package games.alejandrocoria.mismatchedboots.runtimetest.neoforge;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;

/** Native test lookups only: no fixture capability, composition wrapper or resource implementation. */
final class JustDireResourceTestSupport {
    private JustDireResourceTestSupport() {
    }

    static EnergyHandler handler(ItemStack stack) {
        return ItemAccess.forStack(stack).getCapability(Capabilities.Energy.ITEM);
    }

    @SuppressWarnings("unchecked")
    static DataComponentType<Integer> energyComponent() {
        return (DataComponentType<Integer>) BuiltInRegistries.DATA_COMPONENT_TYPE
                .get(Identifier.fromNamespaceAndPath("justdirethings", "forge_energy")).orElseThrow().value();
    }
}
