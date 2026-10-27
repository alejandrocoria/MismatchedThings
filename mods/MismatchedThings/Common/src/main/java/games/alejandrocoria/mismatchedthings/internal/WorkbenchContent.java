package games.alejandrocoria.mismatchedthings.internal;

import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class WorkbenchContent {
    private WorkbenchContent() {
    }

    public static WorkbenchBlock createBlock() {
        return new WorkbenchBlock(BlockBehaviour.Properties.of()
                                                           .strength(2.5F)
                                                           .sound(SoundType.WOOD)
                                                           .setId(ResourceKey.create(Registries.BLOCK, MismatchedWorkbench.ID)));
    }

    public static Item.Properties itemProperties() {
        return new Item.Properties().useBlockDescriptionPrefix()
                                    .setId(ResourceKey.create(Registries.ITEM, MismatchedWorkbench.ID));
    }

    public static MenuType<WorkbenchMenu> createMenu() {
        return new MenuType<>(WorkbenchMenu::new, FeatureFlags.VANILLA_SET);
    }

    @SuppressWarnings("unchecked")
    public static MenuType<WorkbenchMenu> menuType() {
        return (MenuType<WorkbenchMenu>) MismatchedWorkbench.menuType();
    }
}
