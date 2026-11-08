package games.alejandrocoria.mismatchedleggings.test;

import games.alejandrocoria.mismatchedthings.api.WorkbenchRecipe;
import games.alejandrocoria.mismatchedthings.api.compatibility.undergarden.RogdoriumInfusionService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static games.alejandrocoria.mismatchedleggings.test.SkeletonChecks.require;

/** Maintained state/Infuser wiring; the prototype's candidate hooks are never enabled. */
public final class RogdoriumStateChecks {
    static final List<String> PRODUCTS = switch (System.getProperty("leggings.test.rogdoriumGraph", "both")) {
        case "boots" -> List.of("Boots");
        case "leggings" -> List.of("Leggings");
        default -> List.of("Boots", "Leggings");
    };
    private static boolean finished;

    private RogdoriumStateChecks() {}

    public static void tick(MinecraftServer server) {
        if (finished || server.getTickCount() < 20) { return; }
        finished = true;
        try {
            var mods = ModList.get();
            require(mods.isLoaded("mismatchedboots") == PRODUCTS.contains("Boots")
                    && mods.isLoaded("mismatchedleggings") == PRODUCTS.contains("Leggings"), "Wrong product graph");
            require(mods.getMods().stream().filter(mod -> mod.getModId().equals("mismatchedthings")).count() == 1, "Wrong selected core count");
            boolean absent = Boolean.getBoolean("leggings.test.rogdoriumAbsent");
            require(mods.isLoaded("undergarden") != absent, "Wrong native presence");
            String report;
            if (absent) {
                for (String product : PRODUCTS) {
                    var first = item("minecraft:iron_" + lower(product));
                    var second = item("minecraft:diamond_" + lower(product));
                    for (var root : pair(product, first, second)) {
                        var history = history(root, product);
                        var sources = sources(root, product);
                        var copy = recompose(product, root, sources.getFirst(), sources.getLast());
                        require(history.equals(history(copy, product)), "Absent adapter changed history");
                        var before = copy.copy();
                        RogdoriumInfusionService.INSTANCE.initializeCreation(copy, first, second);
                        RogdoriumInfusionService.INSTANCE.preserveState(root, copy);
                        require(ItemStack.matches(copy, before), "Absent adapter changed ordinary root");
                    }
                }
                report = "Native absent: each installed product creates/recomposes ordinary roots; no foreign class dependency\n";
            } else {
                report = RogdoriumNativeStateChecks.verify(server);
            }
            Files.writeString(Path.of(System.getProperty("leggings.test.report"), "state.txt"), "products=" + PRODUCTS + "\n" + report);
            SkeletonChecks.passed("server");
        } catch (Throwable failure) {
            SkeletonChecks.failed("server", failure);
        } finally {
            server.halt(false);
        }
    }

    static String lower(String product) { return product.toLowerCase(Locale.ROOT); }

    static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.parse(id)));
    }

    static List<ItemStack> pair(String product, ItemStack first, ItemStack second) throws Exception {
        try {
            var value = Class.forName("games.alejandrocoria.mismatched" + lower(product) + ".composition.Mixed" + product + "Factory")
                    .getMethod("createPair", ItemStack.class, ItemStack.class).invoke(null, first, second);
            return List.of((ItemStack) value.getClass().getMethod("primary").invoke(value),
                    (ItemStack) value.getClass().getMethod("inverse").invoke(value));
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException cause) { throw cause; }
            throw failure;
        }
    }

    static ItemStack recompose(String product, ItemStack root, ItemStack first, ItemStack second) throws Exception {
        return (ItemStack) Class.forName("games.alejandrocoria.mismatched" + lower(product) + ".composition.Mixed" + product + "Recomposer")
                .getMethod("recompose", ItemStack.class, ItemStack.class, ItemStack.class).invoke(null, root, first, second);
    }

    static Object history(ItemStack root, String product) {
        var id = Identifier.parse("mismatched" + lower(product) + ":mixed_" + lower(product) + "_data");
        return root.copy().get(BuiltInRegistries.DATA_COMPONENT_TYPE.getValue(id));
    }

    static List<ItemStack> sources(ItemStack root, String product) throws Exception {
        var data = history(root, product);
        var left = data.getClass().getMethod("left").invoke(data);
        var right = data.getClass().getMethod("right").invoke(data);
        return List.of((ItemStack) left.getClass().getMethod("source").invoke(left), (ItemStack) right.getClass().getMethod("source").invoke(right));
    }

    static WorkbenchRecipe recipe(String product) throws Exception {
        return (WorkbenchRecipe) Class.forName("games.alejandrocoria.mismatched" + lower(product) + ".recipe.Mixed" + product + "WorkbenchRecipe")
                .getConstructor().newInstance();
    }
}
