package games.alejandrocoria.mismatchedleggings.compatibility.aether;

import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

/** Only the missing LEGS piece is added; native actors still own all set effects. */
public final class AetherSetRecognition {
    private static final Identifier SET = Identifier.parse("aether_ii:armor_set");
    private static volatile boolean disabled;

    private AetherSetRecognition() {}

    public static int count(int original, LivingEntity wearer, TagKey<Item> requested) {
        if (disabled || requested == null) {
            return original;
        }
        try {
            var type = BuiltInRegistries.DATA_COMPONENT_TYPE.getOptional(SET).orElseThrow();
            var root = wearer.getItemBySlot(EquipmentSlot.LEGS);
            // The proven native owner comparison already counted matching roots, including duplicates.
            return root.get(type) != requested && AetherSources.any(root, source -> source.get(type) == requested)
                    ? original + 1 : original;
        } catch (RuntimeException | LinkageError failure) {
            disable(failure);
            return original;
        }
    }

    static synchronized void disable(Throwable failure) {
        if (!disabled) {
            disabled = true;
            if (failure == null) {
                LeggingsContent.LOG.warn("Aether II mixed leggings sets disabled: native additive count unavailable");
            } else {
                LeggingsContent.LOG.warn("Aether II mixed leggings sets disabled: native additive count unavailable", failure);
            }
        }
    }
}
