package games.alejandrocoria.mismatchedboots.composition;

import com.mojang.serialization.Codec;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OwnerComponentMergerTest {
    private static DataComponentType<String> inheritedType;
    private static DataComponentType<String> divergedType;
    private static DataComponentType<String> removedType;
    private static DataComponentType<String> introducedType;
    private static DataComponentType<String> rootOnlyType;
    private static DataComponentType<String> managedType;
    private static DataComponentType<String> decisionType;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        inheritedType = stringComponent();
        divergedType = stringComponent();
        removedType = stringComponent();
        introducedType = stringComponent();
        rootOnlyType = stringComponent();
        managedType = stringComponent();
        decisionType = stringComponent();
    }

    @Test
    void updatesOnlyOwnerProjectedComponents() {
        ItemStack previousOwner = stack(Items.IRON_BOOTS, DataComponentMap.builder()
                .set(inheritedType, "old inherited")
                .set(divergedType, "old owner")
                .set(removedType, "old removable")
                .set(managedType, "old managed")
                .build());
        ItemStack newOwner = stack(Items.DIAMOND_BOOTS, DataComponentMap.builder()
                .set(inheritedType, "new inherited")
                .set(divergedType, "new owner")
                .set(introducedType, "newly introduced")
                .set(managedType, "new managed")
                .build());
        ItemStack currentRoot = stack(Items.CHAINMAIL_BOOTS, DataComponentMap.builder()
                .set(inheritedType, "old inherited")
                .set(divergedType, "old owner")
                .set(removedType, "old removable")
                .set(managedType, "old managed")
                .build());
        currentRoot.set(divergedType, "root mutation");
        currentRoot.set(rootOnlyType, "root only");
        currentRoot.set(managedType, "old managed");
        ItemStack rootBefore = currentRoot.copy();
        ItemStack previousBefore = previousOwner.copy();
        ItemStack newBefore = newOwner.copy();

        ItemStack result = OwnerComponentMerger.merge(
                currentRoot, previousOwner, newOwner, Set.of(managedType));

        assertEquals(Items.CHAINMAIL_BOOTS, result.getItem());
        assertEquals("new inherited", result.get(inheritedType));
        assertEquals("root mutation", result.get(divergedType));
        assertNull(result.get(removedType));
        assertEquals("newly introduced", result.get(introducedType));
        assertEquals("root only", result.get(rootOnlyType));
        assertEquals("old managed", result.get(managedType));
        assertTrue(ItemStack.matches(rootBefore, currentRoot));
        assertTrue(ItemStack.matches(previousBefore, previousOwner));
        assertTrue(ItemStack.matches(newBefore, newOwner));
    }

    @Test
    void preservesAnExplicitRootRemoval() {
        ItemStack previousOwner = stack(Items.IRON_BOOTS, DataComponentMap.builder()
                .set(divergedType, "old owner")
                .build());
        ItemStack newOwner = stack(Items.DIAMOND_BOOTS, DataComponentMap.builder()
                .set(divergedType, "new owner")
                .build());
        ItemStack currentRoot = stack(Items.CHAINMAIL_BOOTS, DataComponentMap.builder()
                .set(divergedType, "old owner")
                .build());
        currentRoot.remove(divergedType);

        ItemStack result = OwnerComponentMerger.merge(
                currentRoot, previousOwner, newOwner, Set.of());

        assertNull(result.get(divergedType));
    }

    @Test
    void followsTheThreeWayDecisionForEveryPresenceCombination() {
        for (String previous : Arrays.asList(null, "alpha", "beta")) {
            for (String current : Arrays.asList(null, "alpha", "beta")) {
                for (String replacement : Arrays.asList(null, "alpha", "beta")) {
                    for (boolean managed : new boolean[]{false, true}) {
                        ItemStack previousOwner = stackWithDecisionValue(Items.IRON_BOOTS, previous);
                        ItemStack currentRoot = stackWithDecisionValue(Items.CHAINMAIL_BOOTS, current);
                        ItemStack newOwner = stackWithDecisionValue(Items.DIAMOND_BOOTS, replacement);

                        ItemStack result = OwnerComponentMerger.merge(
                                currentRoot,
                                previousOwner,
                                newOwner,
                                managed ? Set.of(decisionType) : Set.of());

                        String expected = !managed && Objects.equals(current, previous)
                                ? replacement
                                : current;
                        assertEquals(expected, result.get(decisionType), () ->
                                "previous=" + previous
                                        + ", current=" + current
                                        + ", replacement=" + replacement
                                        + ", managed=" + managed);
                    }
                }
            }
        }
    }

    private static DataComponentType<String> stringComponent() {
        return DataComponentType.<String>builder().persistent(Codec.STRING).build();
    }

    private static ItemStack stack(Item item, DataComponentMap components) {
        return new ItemStack(Holder.direct(item, components), 1);
    }

    private static ItemStack stackWithDecisionValue(Item item, String value) {
        DataComponentMap.Builder components = DataComponentMap.builder();
        if (value != null) {
            components.set(decisionType, value);
        }
        return stack(item, components.build());
    }
}
