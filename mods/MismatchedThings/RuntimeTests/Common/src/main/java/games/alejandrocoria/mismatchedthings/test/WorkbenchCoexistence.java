package games.alejandrocoria.mismatchedthings.test;

import com.google.gson.JsonParser;
import games.alejandrocoria.mismatchedthings.api.MismatchedWorkbench;
import games.alejandrocoria.mismatchedthings.api.WorkbenchInput;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchContributors;
import games.alejandrocoria.mismatchedthings.internal.WorkbenchMenu;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class WorkbenchCoexistence {
    public static final String CASE = System.getProperty("workbench.test.case", "");
    public static final boolean ACTIVE = !CASE.isEmpty();
    public static final boolean UPGRADE = Boolean.getBoolean("workbench.test.upgrade");
    public static String selectedVersion;
    public static int coreContainers;
    public static final AtomicInteger REGISTRATIONS = new AtomicInteger();
    public static boolean boots;
    public static boolean baseline;
    public static boolean sibling;

    private WorkbenchCoexistence() {
    }

    public static List<WorkbenchRoute> routes() {
        if (!ACTIVE) {
            return WorkbenchChecks.RECIPES ? List.of(WorkbenchRoute.FIXTURE) : List.of();
        }
        var routes = new ArrayList<WorkbenchRoute>();
        if (boots) {
            routes.add(WorkbenchRoute.BOOTS);
        }
        if (baseline) {
            routes.add(WorkbenchRoute.BASELINE);
        }
        if (sibling) {
            routes.add(WorkbenchRoute.SIBLING);
        }
        return List.copyOf(routes);
    }

    public static void verifyEnvironment() throws Exception {
        if (!ACTIVE) {
            return;
        }
        WorkbenchChecks.require(coreContainers == 1, "Loader did not resolve exactly one core container");
        WorkbenchChecks.require(REGISTRATIONS.get() == 1, "Core registration did not initialize exactly once: " + REGISTRATIONS.get());
        WorkbenchChecks.require(boots == (CASE.equals("boots") || CASE.equals("both")), "Wrong Boots presence");
        WorkbenchChecks.require(baseline == (CASE.equals("baseline") || CASE.equals("both")), "Wrong frozen consumer presence");
        WorkbenchChecks.require(sibling == (CASE.equals("sibling") || CASE.equals("both")), "Wrong sibling presence");
        WorkbenchChecks.require(selectedVersion.equals(System.getProperty("workbench.test.expectedVersion")),
                "Wrong selected core version: " + selectedVersion);
        boolean extended;
        try {
            Class.forName("games.alejandrocoria.mismatchedthings.api.WorkbenchExtensions");
            extended = true;
        } catch (ClassNotFoundException expected) {
            extended = false;
        }
        WorkbenchChecks.require(extended == sibling, "New API is not exclusive to the selected candidate core");
        WorkbenchChecks.require(BuiltInRegistries.ITEM.containsKey(Identifier.parse("mismatchedboots:mixed_boots")) == boots,
                "Boots item graph is not isolated");
        if (!boots) {
            try {
                Class.forName("games.alejandrocoria.mismatchedboots.composition.MixedBootsFactory");
                throw new AssertionError("Boots classes leaked into a core/sibling/baseline-only graph");
            } catch (ClassNotFoundException expected) {
                // A sibling must not need Boots installed, not merely avoid using its item registry key.
            }
        }
        var field = WorkbenchContributors.class.getDeclaredField("INPUTS");
        field.setAccessible(true);
        WorkbenchChecks.require(((java.util.Map<?, ?>) field.get(null)).size() == routes().size(), "Lost or extra contributor registration");
    }

    public static void verifyRoutes(ServerLevel level, Player player) {
        var menu = new WorkbenchMenu(50, player.getInventory());
        for (WorkbenchRoute route : List.of(WorkbenchRoute.BOOTS, WorkbenchRoute.BASELINE, WorkbenchRoute.SIBLING)) {
            boolean installed = routes().contains(route);
            WorkbenchChecks.require(menu.getSlot(0).mayPlace(route.input().left()) == installed, "Wrong admission: " + route.name());
            var recipe = level.recipeAccess().getRecipeFor(MismatchedWorkbench.recipeType(), route.input(), level);
            WorkbenchChecks.require(recipe.isPresent() == installed, "Wrong loaded recipe: " + route.name());
            if (installed) {
                WorkbenchChecks.require(route.assemble(level).isComplete(), "Incomplete result: " + route.name());
                menu.getSlot(0).set(route.input().left());
                WorkbenchChecks.require(!menu.getSlot(2).hasItem(), "Partial input produced a preview");
                menu.getSlot(1).set(route.input().right());
                WorkbenchChecks.require(menu.getSlot(2).hasItem() && menu.getSlot(3).hasItem(), "Pair preview absent");
                menu.getSlot(0).set(ItemStack.EMPTY);
                menu.getSlot(1).set(ItemStack.EMPTY);
            }
        }
        WorkbenchChecks.require(!menu.getSlot(0).mayPlace(new ItemStack(Items.STONE)), "Unrelated input admitted");
        for (WorkbenchRoute first : routes()) {
            for (WorkbenchRoute second : routes()) {
                if (first == second) {
                    continue;
                }
                var mixed = new WorkbenchInput(first.input().left(), second.input().right());
                WorkbenchChecks.require(level.recipeAccess().getRecipeFor(MismatchedWorkbench.recipeType(), mixed, level).isEmpty(),
                        "Cross-domain pair matched: " + first.name() + "/" + second.name());
            }
        }
    }

    public static void saveRoot(ServerLevel level, Container barrel) {
        if (boots) {
            ItemStack root = WorkbenchRoute.BOOTS.assemble(level).primary();
            root.set(DataComponents.CUSTOM_NAME, Component.literal("Saved mixed boots"));
            root.set(DataComponents.REPAIR_COST, 7);
            barrel.setItem(1, root);
            WorkbenchChecks.write("saved-boots.json", WorkbenchChecks.encode(root, level));
        }
    }

    public static void verifySavedRoot(ServerLevel level, Container barrel) throws Exception {
        if (!UPGRADE) {
            return;
        }
        WorkbenchChecks.require(boots && sibling && baseline, "Upgrade did not install all independent consumers");
        String expected = Files.readString(Path.of(System.getProperty("workbench.test.seedReport"), "saved-boots.json"));
        WorkbenchChecks.require(JsonParser.parseString(expected).equals(JsonParser.parseString(WorkbenchChecks.encode(barrel.getItem(1), level))),
                "Complete saved Boots root changed during a core upgrade");
    }
}
