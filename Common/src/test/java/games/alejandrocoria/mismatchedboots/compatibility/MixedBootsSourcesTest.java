package games.alejandrocoria.mismatchedboots.compatibility;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import games.alejandrocoria.mismatchedboots.data.MixedBootsData;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.Equippable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({"deprecation", "removal"})
class MixedBootsSourcesTest {
    private static DataComponentType<MixedBootsData> dataType;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        bindBoot(Items.IRON_BOOTS);
        bindBoot(Items.GOLDEN_BOOTS);
        bindBoot(Items.DIAMOND_BOOTS);
        bindBoot(Items.CHAINMAIL_BOOTS);
        dataType = ModContent.createMixedBootsDataType();
    }

    @Test
    void rejectsOrdinaryEmptyBareAndInvalidStacks() {
        assertFalse(MixedBootsSources.anySource(ItemStack.EMPTY, source -> true));
        assertFalse(MixedBootsSources.anySource(new ItemStack(Items.IRON_BOOTS), source -> true));
        assertFalse(MixedBootsSources.anySource(new ItemStack(Items.CHAINMAIL_BOOTS), source -> true));
        assertFalse(MixedBootsSources.anySource((MixedBootsData) null, source -> true));

        ItemStack invalid = new ItemStack(Items.CHAINMAIL_BOOTS);
        invalid.set(dataType, data(Items.IRON_BOOTS, Items.GOLDEN_BOOTS));
        invalid.setCount(2);
        assertFalse(MixedBootsSources.anySource(invalid, source -> true));
    }

    @Test
    void acceptsEitherSourceAndRejectsWhenNeitherMatches() {
        MixedBootsData data = data(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);

        assertTrue(MixedBootsSources.anySource(data, source -> source.is(Items.IRON_BOOTS)));
        assertTrue(MixedBootsSources.anySource(data, source -> source.is(Items.GOLDEN_BOOTS)));
        assertFalse(MixedBootsSources.anySource(data, source -> source.is(Items.DIAMOND_BOOTS)));
    }

    @Test
    void shortCircuitsAfterMatchingLeftSource() {
        AtomicInteger invocations = new AtomicInteger();

        assertTrue(MixedBootsSources.anySource(data(Items.IRON_BOOTS, Items.GOLDEN_BOOTS), source -> {
            invocations.incrementAndGet();
            return source.is(Items.IRON_BOOTS);
        }));
        assertEquals(1, invocations.get());
    }

    @Test
    void inverseOutputsAgreeForSymmetricPredicates() {
        MixedBootsData primary = data(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);
        MixedBootsData inverse = data(Items.GOLDEN_BOOTS, Items.IRON_BOOTS);

        assertEquals(
                MixedBootsSources.anySource(primary, source -> source.is(Items.GOLDEN_BOOTS)),
                MixedBootsSources.anySource(inverse, source -> source.is(Items.GOLDEN_BOOTS)));
    }

    @Test
    void predicatesCanOnlyMutateDefensiveCopies() {
        MixedBootsData data = data(Items.IRON_BOOTS, Items.GOLDEN_BOOTS);

        assertFalse(MixedBootsSources.anySource(data, source -> {
            source.setCount(2);
            return false;
        }));

        assertEquals(1, data.left().source().getCount());
        assertEquals(1, data.right().source().getCount());
    }

    private static MixedBootsData data(Item left, Item right) {
        return new MixedBootsData(
                new BootHalf(new ItemStack(left)),
                new BootHalf(new ItemStack(right)));
    }

    private static void bindBoot(Item item) {
        item.builtInRegistryHolder().bindComponents(DataComponentMap.builder()
                .set(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.FEET).build())
                .build());
    }
}
