package games.alejandrocoria.mismatchedleggings.compatibility.aquaculture;

import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/** Effective gravity only; never takes ownership of the shared NoGravity flag. */
public final class AquacultureBuoyancy {
    private static final ResourceKey<Item> NEPTUNIUM_LEGGINGS = ResourceKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath("aquaculture", "neptunium_leggings"));

    private AquacultureBuoyancy() {
    }

    @SuppressWarnings("deprecation")
    public static double gravity(Entity entity, double original) {
        if (original == 0 || !(entity instanceof Player player)
                || !player.isEyeInFluid(FluidTags.WATER) || player.isCrouching()
                || player.isJumping() || player.isSwimming() || player.isSpectator()) {
            return original;
        }

        var data = MixedLeggingsStructure.validData(player.getItemBySlot(EquipmentSlot.LEGS)).orElse(null);
        // Mirrors NeptuniumArmor's small 2.9.2 wearer condition on both sides. Calling its tick
        // would compete with other armor ticks for NoGravity and require unsafe flag cleanup.
        return data != null && (data.left().source().is(NEPTUNIUM_LEGGINGS) || data.right().source().is(NEPTUNIUM_LEGGINGS))
                ? 0 : original;
    }
}
