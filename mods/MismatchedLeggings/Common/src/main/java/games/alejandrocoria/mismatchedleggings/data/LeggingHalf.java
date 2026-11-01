package games.alejandrocoria.mismatchedleggings.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import games.alejandrocoria.mismatchedleggings.content.LeggingsContent;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/** Read-only provenance, not a second live equipment stack. */
public record LeggingHalf(ItemStack source) {
    public static final Codec<LeggingHalf> CODEC = ItemStack.CODEC.flatXmap(LeggingHalf::decode, LeggingHalf::encode);
    public static final StreamCodec<RegistryFriendlyByteBuf, LeggingHalf> STREAM_CODEC =
            ItemStack.STREAM_CODEC.map(LeggingHalf::new, LeggingHalf::source);

    public LeggingHalf {
        Objects.requireNonNull(source, "source");
        String error = validationError(source);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        source = source.copy();
    }

    @Override
    public ItemStack source() {
        return source.copy();
    }

    public static boolean isValidSource(ItemStack source) {
        return source != null && validationError(source) == null;
    }

    @Override
    public boolean equals(Object object) {
        return this == object || object instanceof LeggingHalf other && ItemStack.matches(source, other.source);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(source);
    }

    private static DataResult<LeggingHalf> decode(ItemStack source) {
        String error = validationError(source);
        return error == null ? DataResult.success(new LeggingHalf(source)) : DataResult.error(() -> error);
    }

    private static DataResult<ItemStack> encode(LeggingHalf half) {
        String error = validationError(half.source);
        return error == null ? DataResult.success(half.source()) : DataResult.error(() -> error);
    }

    private static String validationError(ItemStack source) {
        if (source.isEmpty() || source.getCount() != 1) {
            return "A leggings half must contain exactly one nonempty item";
        }
        if (ItemStack.validateStrict(source).isError()) {
            return "A leggings half contains invalid item components";
        }
        var equipment = source.get(DataComponents.EQUIPPABLE);
        if (equipment == null || equipment.slot() != EquipmentSlot.LEGS) {
            return "A leggings half must be equippable in the legs slot";
        }
        if (source.getItem() instanceof MixedLeggingsItem
                || LeggingsContent.MIXED_LEGGINGS_ID.equals(BuiltInRegistries.ITEM.getKey(source.getItem()))) {
            return "Mixed leggings cannot contain another mixed pair";
        }
        for (TypedDataComponent<?> component : source.getComponents()) {
            var id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type());
            if (component.value() instanceof MixedLeggingsData || component.value() instanceof SuspendedLeggingsData
                    || LeggingsContent.MIXED_LEGGINGS_DATA_ID.equals(id) || LeggingsContent.SUSPENDED_LEGGINGS_ID.equals(id)) {
                return "Mixed leggings source/recovery data cannot be nested";
            }
        }
        return null;
    }
}
