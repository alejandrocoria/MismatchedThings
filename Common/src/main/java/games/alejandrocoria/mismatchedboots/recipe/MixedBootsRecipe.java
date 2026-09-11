package games.alejandrocoria.mismatchedboots.recipe;

import com.mojang.serialization.MapCodec;
import games.alejandrocoria.mismatchedboots.Constants;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory;
import games.alejandrocoria.mismatchedboots.composition.MixedBootsPair;
import games.alejandrocoria.mismatchedboots.data.BootHalf;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

public final class MixedBootsRecipe extends CustomRecipe {
    private static final MixedBootsRecipe INSTANCE = new MixedBootsRecipe();
    private static final MapCodec<MixedBootsRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    private static final StreamCodec<RegistryFriendlyByteBuf, MixedBootsRecipe> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);
    public static final RecipeSerializer<MixedBootsRecipe> SERIALIZER =
            new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC);

    private final PairFactory pairFactory;

    public MixedBootsRecipe() {
        this(MixedBootsFactory::createPair);
    }

    MixedBootsRecipe(PairFactory pairFactory) {
        this.pairFactory = pairFactory;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return evaluate(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Evaluation evaluation = evaluate(input);
        return evaluation == null ? ItemStack.EMPTY : evaluation.pair().primary();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remainders = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        Evaluation evaluation = evaluate(input);
        if (evaluation != null) {
            remainders.set(evaluation.match().firstIndex(), evaluation.pair().inverse());
        }
        return remainders;
    }

    @Override
    public RecipeSerializer<MixedBootsRecipe> getSerializer() {
        return SERIALIZER;
    }

    private Evaluation evaluate(CraftingInput input) {
        Match match = findInputs(input);
        if (match == null) {
            return null;
        }

        try {
            MixedBootsPair pair = pairFactory.create(match.first(), match.second());
            if (pair.primary().isEmpty() || pair.inverse().isEmpty()) {
                Constants.LOG.debug("Rejected empty mixed boots output for {} and {}",
                        BuiltInRegistries.ITEM.getKey(match.first().getItem()),
                        BuiltInRegistries.ITEM.getKey(match.second().getItem()));
                return null;
            }
            return new Evaluation(match, pair);
        } catch (IllegalArgumentException exception) {
            Constants.LOG.debug("Rejected mixed boots composition for {} and {}: {}",
                    BuiltInRegistries.ITEM.getKey(match.first().getItem()),
                    BuiltInRegistries.ITEM.getKey(match.second().getItem()),
                    exception.getMessage());
            return null;
        }
    }

    private static Match findInputs(CraftingInput input) {
        if (input.ingredientCount() != 2) {
            return null;
        }

        int firstIndex = -1;
        ItemStack first = ItemStack.EMPTY;
        ItemStack second = ItemStack.EMPTY;
        for (int index = 0; index < input.size(); index++) {
            ItemStack stack = input.getItem(index);
            if (stack.isEmpty()) {
                continue;
            }
            if (!BootHalf.isValidSource(stack)) {
                return null;
            }
            if (firstIndex < 0) {
                firstIndex = index;
                first = stack;
            } else {
                second = stack;
            }
        }

        return firstIndex < 0 || second.isEmpty() ? null : new Match(firstIndex, first, second);
    }

    @FunctionalInterface
    interface PairFactory {
        MixedBootsPair create(ItemStack first, ItemStack second);
    }

    private record Match(int firstIndex, ItemStack first, ItemStack second) {
    }

    private record Evaluation(Match match, MixedBootsPair pair) {
    }
}
