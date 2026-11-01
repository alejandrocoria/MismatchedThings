package games.alejandrocoria.mismatchedleggings.compatibility.rei;

import games.alejandrocoria.mismatchedleggings.compatibility.MismatchedWorkbenchClientRecipes;
import games.alejandrocoria.mismatchedleggings.compatibility.MismatchedWorkbenchExampleCycle;
import games.alejandrocoria.mismatchedleggings.config.CompositionRuleState;
import games.alejandrocoria.mismatchedleggings.content.MixedLeggingsStructure;
import games.alejandrocoria.mismatchedleggings.data.LeggingHalf;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.api.client.viewer.rei.WorkbenchReiDisplay;
import me.shedaniel.rei.api.client.registry.entry.EntryRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.entry.type.VanillaEntryTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class MismatchedWorkbenchReiExamples {
    private MismatchedWorkbenchReiExamples() {
    }

    static boolean requestsDisplay(
            boolean requestsCategory,
            List<ItemStack> recipesFor,
            List<ItemStack> usagesFor,
            Item workstation) {
        return requestsCategory
                || recipesFor.stream().anyMatch(stack -> MixedLeggingsStructure.validData(stack).isPresent())
                || usagesFor.stream().anyMatch(stack -> stack.is(workstation) || LeggingHalf.isValidSource(stack));
    }

    static Optional<WorkbenchReiDisplay> create() {
        if (CompositionRuleState.synchronizedPreview() == null) {
            return Optional.empty();
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return Optional.empty();
        }

        return MismatchedWorkbenchExampleCycle.create(collectCandidates(), (left, right) -> {
            return MismatchedWorkbenchClientRecipes.assemblePair(
                    new WorkbenchInput(left, right), level);
        }).map(example -> WorkbenchReiDisplay.create(
                example.leftInputs(),
                example.rightInputs(),
                example.primaryOutputs(),
                example.inverseOutputs()));
    }

    private static List<ItemStack> collectCandidates() {
        return collectCandidates(EntryRegistry.getInstance().getPreFilteredList().stream()
                                              .filter(entry -> entry.getType() == VanillaEntryTypes.ITEM)
                                              .map(MismatchedWorkbenchReiExamples::itemStack)
                                              .toList());
    }

    static List<ItemStack> collectCandidates(Iterable<ItemStack> entries) {
        Map<LeggingHalf, ItemStack> uniqueCandidates = new LinkedHashMap<>();
        for (ItemStack stack : entries) {
            ItemStack candidate = stack.copyWithCount(1);
            if (LeggingHalf.isValidSource(candidate)) {
                uniqueCandidates.putIfAbsent(new LeggingHalf(candidate), candidate);
            }
        }

        return uniqueCandidates.values().stream()
                               .sorted(Comparator.comparing(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))
                               .toList();
    }

    private static ItemStack itemStack(EntryStack<?> entry) {
        ItemStack stack = entry.castValue();
        return stack.copyWithCount(1);
    }
}
