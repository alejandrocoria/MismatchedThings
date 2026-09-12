package games.alejandrocoria.mismatchedboots.gametest;

import games.alejandrocoria.mismatchedboots.content.MixedBootsItem;
import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.recipe.MixedBootsRecipe;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;

public final class MismatchedBootsGameTests {
    @GameTest
    public void registeredContentIsAvailable(GameTestHelper helper) {
        helper.assertTrue(BuiltInRegistries.ITEM.getValue(ModContent.MIXED_BOOTS_ID) instanceof MixedBootsItem,
                "Registered mixed boots item has the wrong implementation");
        var registeredDataType = BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(ModContent.MIXED_BOOTS_DATA_ID);
        helper.assertTrue(registeredDataType != null, "Mixed boots data component is not registered");
        helper.assertTrue(ModContent.mixedBootsDataType().orElseThrow() == registeredDataType,
                "Registered mixed boots data component is unavailable through ModContent");
        helper.assertTrue(BuiltInRegistries.RECIPE_SERIALIZER.getValue(ModContent.MIXED_BOOTS_RECIPE_ID)
                        == MixedBootsRecipe.SERIALIZER,
                "Registered mixed boots recipe serializer has the wrong implementation");
        helper.succeed();
    }
}
