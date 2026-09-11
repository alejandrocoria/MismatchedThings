package games.alejandrocoria.mismatchedboots.composition;

import games.alejandrocoria.mismatchedboots.Constants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

final class AttributeMerger {
    private AttributeMerger() {
    }

    static ItemAttributeModifiers merge(ItemStack left, ItemStack right) {
        List<Contribution> contributions = new ArrayList<>();
        collect(contributions, left, Side.LEFT);
        collect(contributions, right, Side.RIGHT);
        contributions.sort(Contribution.ORDER);

        ItemAttributeModifiers.Builder result = ItemAttributeModifiers.builder();
        for (Contribution contribution : contributions) {
            ItemAttributeModifiers.Entry entry = contribution.entry;
            AttributeModifier modifier = new AttributeModifier(
                    stableId(contribution), entry.modifier().amount() * 0.5D, entry.modifier().operation());
            result.add(entry.attribute(), modifier, entry.slot(), entry.display());
        }
        return result.build();
    }

    private static void collect(List<Contribution> result, ItemStack source, Side side) {
        ItemAttributeModifiers modifiers = source.getOrDefault(
                DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        Identifier itemId = registeredItemId(source);

        for (int index = 0; index < modifiers.modifiers().size(); index++) {
            ItemAttributeModifiers.Entry entry = modifiers.modifiers().get(index);
            double amount = entry.modifier().amount();
            if (!Double.isFinite(amount)) {
                throw new IllegalArgumentException("Non-finite attribute modifier at " + side.serializedName + " index " + index);
            }
            Identifier attributeId = entry.attribute()
                    .unwrapKey()
                    .map(key -> key.identifier())
                    .orElseThrow(() -> new IllegalArgumentException("Attribute modifier uses an unregistered attribute"));
            result.add(new Contribution(side, itemId, attributeId, entry, index));
        }
    }

    private static Identifier registeredItemId(ItemStack source) {
        Identifier itemId = source.typeHolder().unwrapKey().map(key -> key.identifier()).orElse(null);
        if (itemId == null) {
            itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(source.getItem());
        }
        if (itemId == null) {
            throw new IllegalArgumentException("Attribute source item is not registered");
        }
        return itemId;
    }

    private static Identifier stableId(Contribution contribution) {
        ItemAttributeModifiers.Entry entry = contribution.entry;
        MessageDigest digest = sha256();

        update(digest, contribution.side.serializedName);
        update(digest, contribution.itemId.toString());
        update(digest, contribution.attributeId.toString());
        update(digest, entry.modifier().id().toString());
        update(digest, entry.modifier().operation().getSerializedName());
        update(digest, entry.slot().getSerializedName());
        update(digest, Integer.toString(contribution.index));

        return Identifier.fromNamespaceAndPath(
                Constants.MOD_ID, "attribute/" + HexFormat.of().formatHex(digest.digest()));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private record Contribution(
            Side side,
            Identifier itemId,
            Identifier attributeId,
            ItemAttributeModifiers.Entry entry,
            int index) {
        private static final Comparator<Contribution> ORDER = Comparator
                .comparing(Contribution::itemId)
                .thenComparing(Contribution::attributeId)
                .thenComparing(contribution -> contribution.entry.modifier().id())
                .thenComparingInt(contribution -> contribution.entry.modifier().operation().id())
                .thenComparing(contribution -> contribution.entry.slot().getSerializedName())
                .thenComparingInt(Contribution::index)
                .thenComparingDouble(contribution -> contribution.entry.modifier().amount());
    }

    private enum Side {
        LEFT("left"),
        RIGHT("right");

        private final String serializedName;

        Side(String serializedName) {
            this.serializedName = serializedName;
        }
    }
}
