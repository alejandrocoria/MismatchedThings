package games.alejandrocoria.mismatchedboots.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.Objects;

public record BootHalf(ItemStack source) {
    public static final Codec<BootHalf> CODEC = ItemStack.CODEC.flatXmap(BootHalf::decode, BootHalf::encode);
    public static final StreamCodec<RegistryFriendlyByteBuf, BootHalf> STREAM_CODEC =
            ItemStack.STREAM_CODEC.map(BootHalf::new, BootHalf::source);

    public BootHalf {
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
        return this == object || object instanceof BootHalf other && ItemStack.matches(source, other.source);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(source);
    }

    private static DataResult<BootHalf> decode(ItemStack source) {
        String error = validationError(source);
        return error == null ? DataResult.success(new BootHalf(source)) : DataResult.error(() -> error);
    }

    private static DataResult<ItemStack> encode(BootHalf half) {
        String error = validationError(half.source);
        return error == null ? DataResult.success(half.source()) : DataResult.error(() -> error);
    }

    private static String validationError(ItemStack source) {
        if (source.isEmpty()) {
            return "A boot half cannot be empty";
        }
        if (source.getCount() != 1) {
            return "A boot half must contain exactly one item";
        }
        if (ItemStack.validateStrict(source).error().isPresent()) {
            return "A boot half contains invalid item components";
        }

        Equippable equippable = source.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot() != EquipmentSlot.FEET) {
            return "A boot half must be equippable in the feet slot";
        }
        if (source.getItem() instanceof MixedBootsItem
                || ModContent.MIXED_BOOTS_ID.equals(BuiltInRegistries.ITEM.getKey(source.getItem()))) {
            return "Mixed boots cannot contain another pair of mixed boots";
        }
        for (TypedDataComponent<?> component : source.getComponents()) {
            if (component.value() instanceof MixedBootsData || component.value() instanceof SuspendedBootsData) {
                return "Mixed boots source/recovery data cannot be nested";
            }
        }

        return null;
    }
}
