package games.alejandrocoria.mismatchedboots.compatibility.rei;

import games.alejandrocoria.mismatchedboots.content.ModContent;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.client.registry.display.DynamicDisplayGenerator;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.client.view.ViewSearchBuilder;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MismatchedBootsReiClientPlugin implements REIClientPlugin {
    @Override
    @SuppressWarnings("removal")
    public void registerCategories(CategoryRegistry registry) {
        registry.add(new MismatchedWorkbenchReiCategory());
        registry.addWorkstations(
                MismatchedWorkbenchReiDisplay.CATEGORY,
                EntryStacks.of(ModContent.mismatchedWorkbenchItem().orElseThrow()));
        registry.removePlusButton(MismatchedWorkbenchReiDisplay.CATEGORY);
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        registry.registerDisplayGenerator(
                MismatchedWorkbenchReiDisplay.CATEGORY,
                new DynamicDisplayGenerator<MismatchedWorkbenchReiDisplay>() {
                    @Override
                    public Optional<List<MismatchedWorkbenchReiDisplay>> getRecipeFor(EntryStack<?> entry) {
                        return itemStack(entry)
                                .filter(stack -> ModContent.getMixedBootsData(stack).isPresent())
                                .flatMap(stack -> createDisplay());
                    }

                    @Override
                    public Optional<List<MismatchedWorkbenchReiDisplay>> getUsageFor(EntryStack<?> entry) {
                        return itemStack(entry)
                                .filter(stack -> stack.is(ModContent.mismatchedWorkbenchItem().orElseThrow())
                                        || BootHalf.isValidSource(stack))
                                .flatMap(stack -> createDisplay());
                    }

                    @Override
                    public Optional<List<MismatchedWorkbenchReiDisplay>> generate(ViewSearchBuilder builder) {
                        boolean requestsCategory = builder.getCategories().contains(MismatchedWorkbenchReiDisplay.CATEGORY)
                                || builder.getFilteringCategories().contains(MismatchedWorkbenchReiDisplay.CATEGORY)
                                || Objects.equals(
                                builder.getPreferredOpenedCategory(),
                                MismatchedWorkbenchReiDisplay.CATEGORY);
                        return requestsCategory ? createDisplay() : Optional.empty();
                    }

                    private Optional<List<MismatchedWorkbenchReiDisplay>> createDisplay() {
                        return MismatchedWorkbenchReiExamples.create().map(List::of);
                    }

                    private Optional<ItemStack> itemStack(EntryStack<?> entry) {
                        if (entry.getType() != VanillaEntryTypes.ITEM) {
                            return Optional.empty();
                        }
                        return Optional.of(entry.<ItemStack>castValue().copyWithCount(1));
                    }
                });
    }

    @Override
    public void registerEntries(EntryRegistry registry) {
        Item mixedBoots = BuiltInRegistries.ITEM.get(ModContent.MIXED_BOOTS_ID)
                .orElseThrow(() -> new IllegalStateException("MismatchedBoots item is not registered"))
                .value();
        registry.removeEntry(EntryStacks.of(new ItemStack(mixedBoots)));
    }
}
