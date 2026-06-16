package dev.g1ax.agent.tool;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.g1ax.agent.MinecraftThread;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-only tools that let the agent observe live game state (inventory, player, world, nearby
 * entities, the block under the crosshair). Every body runs on the client thread via
 * {@link MinecraftThread#call}. These ground the model in reality instead of guesswork.
 */
public final class GameStateTools {
    private static final Gson GSON = new Gson();

    private GameStateTools() {}

    public static void register(ToolRegistry reg) {
        reg.register(new SimpleTool(
                "get_inventory",
                "Get the player's current inventory as item id -> total count (includes hotbar, "
                        + "main inventory, armor, and offhand). Call this before answering anything "
                        + "about what the player has or can craft.",
                SchemaBuilder.empty(),
                args -> MinecraftThread.call(GameStateTools::readInventory)));

        reg.register(new SimpleTool(
                "get_player_state",
                "Get the player's live status: position (x,y,z), dimension, biome, health, food, "
                        + "xp level, game mode, facing direction, and light level at their feet.",
                SchemaBuilder.empty(),
                args -> MinecraftThread.call(GameStateTools::readPlayerState)));

        reg.register(new SimpleTool(
                "get_world_info",
                "Get world conditions: time of day (and whether it is day/night), weather (rain/"
                        + "thunder), and difficulty.",
                SchemaBuilder.empty(),
                args -> MinecraftThread.call(GameStateTools::readWorldInfo)));

        reg.register(new SimpleTool(
                "get_nearby_entities",
                "List entities (mobs, players, items, etc.) near the player within a radius, sorted "
                        + "by distance. Useful for 'what's around me' or threat/spawn questions.",
                SchemaBuilder.object()
                        .intProp("radius", "Search radius in blocks (default 16, max 64)", false)
                        .build(),
                args -> {
                    int radius = Math.min(64, Math.max(1, SimpleTool.integer(args, "radius", 16)));
                    return MinecraftThread.call(() -> readNearbyEntities(radius));
                },
                false,
                args -> "get_nearby_entities r=" + SimpleTool.integer(args, "radius", 16)));

        reg.register(new SimpleTool(
                "look_at_block",
                "Get the block the player is currently looking at (crosshair target): its id and "
                        + "position, or a note that nothing is targeted.",
                SchemaBuilder.empty(),
                args -> MinecraftThread.call(GameStateTools::readLookedAtBlock)));
    }

    private static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }

    static String readInventory() {
        ClientPlayerEntity player = mc().player;
        if (player == null) return "No player (not in a world).";
        Map<String, Integer> counts = inventoryCountsOnThread();
        if (counts.isEmpty()) return "Inventory is empty.";
        JsonObject out = new JsonObject();
        counts.forEach(out::addProperty);
        return out.toString();
    }

    /**
     * Item id -> total count across the whole inventory. Caller must already be on the client
     * thread (used directly by recipe-craftability checks that run inside one client-thread call).
     */
    public static Map<String, Integer> inventoryCountsOnThread() {
        Map<String, Integer> counts = new TreeMap<>();
        ClientPlayerEntity player = mc().player;
        if (player == null) return counts;
        var inv = player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            String id = Registries.ITEM.getId(stack.getItem()).toString();
            counts.merge(id, stack.getCount(), Integer::sum);
        }
        return counts;
    }

    static String readPlayerState() {
        ClientPlayerEntity player = mc().player;
        ClientWorld world = mc().world;
        if (player == null || world == null) return "No player (not in a world).";
        BlockPos pos = player.getBlockPos();
        JsonObject o = new JsonObject();
        o.addProperty("x", Math.round(player.getX()));
        o.addProperty("y", Math.round(player.getY()));
        o.addProperty("z", Math.round(player.getZ()));
        o.addProperty("dimension", world.getRegistryKey().getValue().toString());
        world.getBiome(pos).getKey().ifPresent(k -> o.addProperty("biome", k.getValue().toString()));
        o.addProperty("health", round1(player.getHealth()) + "/" + round1(player.getMaxHealth()));
        o.addProperty("food", player.getHungerManager().getFoodLevel() + "/20");
        o.addProperty("xp_level", player.experienceLevel);
        try {
            o.addProperty("game_mode", mc().interactionManager.getCurrentGameMode().asString());
        } catch (Exception ignored) { /* interactionManager can be null mid-load */ }
        o.addProperty("facing", player.getHorizontalFacing().asString());
        o.addProperty("light_level", world.getLightLevel(pos));
        return o.toString();
    }

    static String readWorldInfo() {
        ClientWorld world = mc().world;
        if (world == null) return "No world loaded.";
        long timeOfDay = world.getTimeOfDay() % 24000L;
        boolean night = timeOfDay >= 13000 && timeOfDay <= 23000;
        JsonObject o = new JsonObject();
        o.addProperty("time_of_day_ticks", timeOfDay);
        o.addProperty("phase", night ? "night" : "day");
        o.addProperty("weather", world.isThundering() ? "thunderstorm" : world.isRaining() ? "rain" : "clear");
        try {
            o.addProperty("difficulty", world.getLevelProperties().getDifficulty().getName());
        } catch (Exception ignored) { /* properties may be unavailable */ }
        return o.toString();
    }

    static String readNearbyEntities(int radius) {
        ClientPlayerEntity player = mc().player;
        ClientWorld world = mc().world;
        if (player == null || world == null) return "No player (not in a world).";
        Box box = new Box(
                player.getX() - radius, player.getY() - radius, player.getZ() - radius,
                player.getX() + radius, player.getY() + radius, player.getZ() + radius);
        List<Entity> entities = world.getOtherEntities(player, box, e -> true);
        entities.sort((a, b) -> Float.compare(player.distanceTo(a), player.distanceTo(b)));
        JsonArray arr = new JsonArray();
        int limit = Math.min(25, entities.size());
        for (int i = 0; i < limit; i++) {
            Entity e = entities.get(i);
            JsonObject o = new JsonObject();
            o.addProperty("type", EntityType.getId(e.getType()).toString());
            o.addProperty("name", e.getName().getString());
            o.addProperty("distance", Math.round(player.distanceTo(e) * 10) / 10.0);
            arr.add(o);
        }
        JsonObject out = new JsonObject();
        out.addProperty("count", entities.size());
        out.add("nearest", arr);
        return out.toString();
    }

    static String readLookedAtBlock() {
        MinecraftClient client = mc();
        ClientWorld world = client.world;
        HitResult hit = client.crosshairTarget;
        if (world == null) return "No world loaded.";
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return "Not looking at any block (target type: " + (hit != null ? hit.getType() : "none") + ").";
        }
        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        var state = world.getBlockState(pos);
        JsonObject o = new JsonObject();
        o.addProperty("block", Registries.BLOCK.getId(state.getBlock()).toString());
        o.addProperty("x", pos.getX());
        o.addProperty("y", pos.getY());
        o.addProperty("z", pos.getZ());
        return o.toString();
    }

    private static String round1(float v) {
        return String.valueOf(Math.round(v * 10) / 10.0);
    }
}
