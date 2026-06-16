package dev.g1ax.agent.tool;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.g1ax.agent.MinecraftThread;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Recipe tools backed by the REAL synced {@link RecipeManager}, so the agent reads true vanilla/
 * modpack recipes instead of hallucinating them. {@code lookup_recipe} returns the ingredients
 * for an item; {@code check_craftable} cross-references those against the live inventory and
 * reports exactly what's missing.
 *
 * If the registry can't answer (not connected, or a custom/modded item with no introspectable
 * recipe), the tools return a clear note so the model can fall back to its own knowledge.
 */
public final class RecipeTools {
    private static final Gson GSON = new Gson();
    private static final int MAX_RECIPES = 5;

    private RecipeTools() {}

    public static void register(ToolRegistry reg) {
        reg.register(new SimpleTool(
                "lookup_recipe",
                "Look up how to craft/produce an item using the game's REAL recipe data. Returns the "
                        + "output count and the required ingredients (with alternatives per slot). "
                        + "Prefer this over guessing recipes. Accepts ids like 'hopper' or "
                        + "'minecraft:hopper'.",
                SchemaBuilder.object()
                        .stringProp("item", "Item to look up, e.g. 'hopper' or 'minecraft:redstone_torch'", true)
                        .build(),
                args -> {
                    String item = SimpleTool.str(args, "item", "").trim();
                    if (item.isEmpty()) return "ERROR: 'item' is required.";
                    return MinecraftThread.call(() -> lookupRecipe(item));
                },
                false,
                args -> "lookup_recipe: " + SimpleTool.str(args, "item", "?")));

        reg.register(new SimpleTool(
                "check_craftable",
                "Check whether the player can craft an item right now: compares the item's real "
                        + "recipe against the live inventory and lists exactly which ingredients "
                        + "(and how many) are still missing.",
                SchemaBuilder.object()
                        .stringProp("item", "Item to check, e.g. 'hopper'", true)
                        .build(),
                args -> {
                    String item = SimpleTool.str(args, "item", "").trim();
                    if (item.isEmpty()) return "ERROR: 'item' is required.";
                    return MinecraftThread.call(() -> checkCraftable(item));
                },
                false,
                args -> "check_craftable: " + SimpleTool.str(args, "item", "?")));
    }

    // ---- core, all run on the client thread ----

    private static String lookupRecipe(String itemName) {
        ResolveResult resolved = resolveItem(itemName);
        if (resolved.error != null) return resolved.error;

        List<RecipeView> recipes = findRecipes(resolved.item);
        if (recipes.isEmpty()) {
            return "No registry recipe found for " + resolved.id
                    + " (it may be obtained by mining/smelting/trading, or be a modded item). "
                    + "Use general knowledge to advise.";
        }
        JsonObject out = new JsonObject();
        out.addProperty("item", resolved.id);
        JsonArray arr = new JsonArray();
        for (RecipeView rv : recipes) {
            JsonObject r = new JsonObject();
            r.addProperty("recipe_type", rv.type);
            r.addProperty("output_count", rv.outputCount);
            JsonObject needs = new JsonObject();
            rv.needs.forEach(needs::addProperty);
            r.add("ingredients", needs);
            if (!rv.slotAlternatives.isEmpty()) {
                JsonObject alts = new JsonObject();
                rv.slotAlternatives.forEach((rep, options) -> alts.addProperty(rep, String.join(" | ", options)));
                r.add("alternatives", alts);
            }
            arr.add(r);
        }
        out.add("recipes", arr);
        return out.toString();
    }

    private static String checkCraftable(String itemName) {
        ResolveResult resolved = resolveItem(itemName);
        if (resolved.error != null) return resolved.error;

        List<RecipeView> recipes = findRecipes(resolved.item);
        if (recipes.isEmpty()) {
            return "No registry recipe for " + resolved.id + "; can't compute craftability. "
                    + "Advise how to obtain it from general knowledge.";
        }
        Map<String, Integer> inv = GameStateTools.inventoryCountsOnThread();

        // Use the first recipe as the canonical one for the missing-items report.
        RecipeView rv = recipes.get(0);
        JsonObject missing = new JsonObject();
        boolean craftable = true;
        for (Map.Entry<String, Integer> need : rv.needs.entrySet()) {
            String rep = need.getKey();
            int required = need.getValue();
            int have = countWithAlternatives(inv, rep, rv.slotAlternatives.get(rep));
            if (have < required) {
                craftable = false;
                missing.addProperty(rep, (required - have) + " more (have " + have + "/" + required + ")");
            }
        }
        JsonObject out = new JsonObject();
        out.addProperty("item", resolved.id);
        out.addProperty("craftable_now", craftable);
        out.addProperty("output_count", rv.outputCount);
        JsonObject needsObj = new JsonObject();
        rv.needs.forEach(needsObj::addProperty);
        out.add("requires", needsObj);
        if (!craftable) out.add("missing", missing);
        return out.toString();
    }

    private static int countWithAlternatives(Map<String, Integer> inv, String rep, List<String> alternatives) {
        if (alternatives == null || alternatives.isEmpty()) {
            return inv.getOrDefault(rep, 0);
        }
        int total = 0;
        for (String alt : alternatives) total += inv.getOrDefault(alt, 0);
        return total;
    }

    /** Find recipes whose result is the target item, distilled into a model-friendly view. */
    private static List<RecipeView> findRecipes(Item target) {
        List<RecipeView> views = new ArrayList<>();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null || mc.world == null) return views;
        RecipeManager rm = mc.getNetworkHandler().getRecipeManager();
        var registry = mc.world.getRegistryManager();

        for (RecipeEntry<?> entry : rm.values()) {
            Recipe<?> recipe = entry.value();
            ItemStack result;
            try {
                result = recipe.getResult(registry);
            } catch (Exception e) {
                continue;
            }
            if (result == null || result.isEmpty() || result.getItem() != target) continue;

            RecipeView rv = new RecipeView();
            rv.type = recipe.getClass().getSimpleName();
            rv.outputCount = result.getCount();
            try {
                for (Ingredient ing : recipe.getIngredients()) {
                    if (ing == null || ing.isEmpty()) continue;
                    ItemStack[] matches = ing.getMatchingStacks();
                    if (matches.length == 0) continue;
                    String rep = Registries.ITEM.getId(matches[0].getItem()).toString();
                    rv.needs.merge(rep, 1, Integer::sum);
                    if (matches.length > 1) {
                        List<String> options = new ArrayList<>();
                        for (ItemStack ms : matches) {
                            options.add(Registries.ITEM.getId(ms.getItem()).toString());
                        }
                        rv.slotAlternatives.put(rep, options);
                    }
                }
            } catch (Exception ignored) {
                // Recipe type without introspectable ingredients; still report output.
            }
            views.add(rv);
            if (views.size() >= MAX_RECIPES) break;
        }
        return views;
    }

    private static ResolveResult resolveItem(String name) {
        String normalized = name.toLowerCase().trim().replace(' ', '_');
        Identifier id = Identifier.tryParse(normalized.contains(":") ? normalized : "minecraft:" + normalized);
        ResolveResult r = new ResolveResult();
        if (id == null) {
            r.error = "ERROR: '" + name + "' is not a valid item id.";
            return r;
        }
        Optional<Item> item = Registries.ITEM.getOrEmpty(id);
        if (item.isEmpty() || item.get() == Items.AIR) {
            r.error = "Unknown item '" + name + "'. Use a valid item id like 'hopper' or 'iron_ingot'.";
            return r;
        }
        r.item = item.get();
        r.id = id.toString();
        return r;
    }

    private static final class ResolveResult {
        Item item;
        String id;
        String error;
    }

    /** Distilled, serialization-ready view of one recipe. */
    private static final class RecipeView {
        String type = "recipe";
        int outputCount = 1;
        final Map<String, Integer> needs = new LinkedHashMap<>();
        final Map<String, List<String>> slotAlternatives = new LinkedHashMap<>();
    }
}
