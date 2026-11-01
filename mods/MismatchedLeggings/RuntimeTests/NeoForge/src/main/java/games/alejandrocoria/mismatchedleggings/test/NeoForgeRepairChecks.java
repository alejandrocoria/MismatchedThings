package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedleggings.composition.MixedLeggingsFactory;
import games.alejandrocoria.mismatchedleggings.config.CompositionRules;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import net.minecraft.world.item.equipment.Equippable;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

final class NeoForgeRepairChecks {
    private NeoForgeRepairChecks() {
    }

    static void register(IEventBus bus) {
        var items = DeferredRegister.createItems("leggings_skeleton_test");
        var blocked = items.registerItem("no_combine_leggings", properties -> new Item(properties.durability(150)
                .component(DataComponents.EQUIPPABLE, Equippable.builder(EquipmentSlot.LEGS).build())) {
            @Override
            public boolean isCombineRepairable(ItemStack stack) {
                return false;
            }
        });
        items.register(bus);
        CraftingRepairChecks.nativeEligibilityCheck = server -> {
            var level = server.overworld();
            var donor = new ItemStack(blocked.get());
            var pair = MixedLeggingsFactory.createPair(donor, new ItemStack(Items.IRON_LEGGINGS), CompositionRules.defaults());
            for (var root : List.of(pair.primary(), pair.inverse())) {
                var input = CraftingInput.of(2, 1, List.of(root, donor));
                require(level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level).isEmpty(),
                        "Dedicated recipe ignored the NeoForge donor's combine-repair veto");
                var allowed = CraftingInput.of(2, 1, List.of(root, new ItemStack(Items.IRON_LEGGINGS)));
                require(level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, allowed, level).isPresent(),
                        "A source's donor veto incorrectly disabled every root repair");
            }
            require(!RepairItemRecipe.INSTANCE.matches(CraftingInput.of(2, 1, List.of(donor, donor.copy())), level),
                    "Native combine-veto control unexpectedly accepts repair");
        };
    }
}
