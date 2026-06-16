package dev.g1ax;

import net.minecraft.client.MinecraftClient;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import com.google.gson.*;

public class SystemScanner {
    private static final Gson GSON = new Gson();

    public static String scan_mod_configs() {
        try {
            Path configDir = Paths.get("config");
            if (!Files.exists(configDir)) return "No config directory";
            
            StringBuilder sb = new StringBuilder("Configs: ");
            Files.list(configDir)
                .filter(p -> p.toString().endsWith(".json") || p.toString().endsWith(".toml"))
                .limit(20)
                .forEach(p -> sb.append(p.getFileName()).append(", "));
            return sb.toString();
        } catch (Exception e) {
            return "Error scanning configs: " + e.getMessage();
        }
    }

    public static String scan_shader_settings() {
        try {
            Path shaderDir = Paths.get("shaderpacks");
            if (!Files.exists(shaderDir)) return "No shaders installed";
            
            StringBuilder sb = new StringBuilder("Shaders: ");
            Files.list(shaderDir).limit(10)
                .forEach(p -> sb.append(p.getFileName()).append(", "));
            return sb.toString();
        } catch (Exception e) {
            return "No shader info available";
        }
    }

    public static String scan_resource_packs() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.getResourcePackManager() == null) return "No resource packs";
            
            int count = mc.getResourcePackManager().getEnabledProfiles().size();
            return "Resource packs enabled: " + count;
        } catch (Exception e) {
            return "Error scanning resource packs";
        }
    }

    public static String scan_keybinds() {
        try {
            Path optionsFile = Paths.get("options.txt");
            if (!Files.exists(optionsFile)) return "No options.txt";
            
            List<String> keybinds = Files.readAllLines(optionsFile).stream()
                .filter(l -> l.startsWith("key_"))
                .limit(5)
                .toList();
            return "Keybinds configured: " + keybinds.size();
        } catch (Exception e) {
            return "Error reading keybinds";
        }
    }

    public static String scan_crash_reports() {
        try {
            Path crashDir = Paths.get("crash-reports");
            if (!Files.exists(crashDir)) return "No crash reports";
            
            List<Path> crashes = Files.list(crashDir)
                .sorted((p1, p2) -> {
                    try {
                        return Files.getLastModifiedTime(p2).compareTo(Files.getLastModifiedTime(p1));
                    } catch (Exception e) { return 0; }
                })
                .limit(3)
                .toList();
            
            if (crashes.isEmpty()) return "No recent crashes";
            
            Path latest = crashes.get(0);
            List<String> lines = Files.readAllLines(latest);
            String error = lines.stream()
                .filter(l -> l.contains("Exception") || l.contains("Error"))
                .findFirst()
                .orElse("Unknown error");
            return "Latest crash: " + error;
        } catch (Exception e) {
            return "No crash info available";
        }
    }

    public static String scan_latest_log() {
        try {
            Path logFile = Paths.get("logs/latest.log");
            if (!Files.exists(logFile)) return "No log file";
            
            List<String> errors = Files.readAllLines(logFile).stream()
                .filter(l -> l.contains("ERROR") || l.contains("WARN"))
                .limit(5)
                .toList();
            
            return errors.isEmpty() ? "No errors in log" : "Recent errors: " + errors.size();
        } catch (Exception e) {
            return "Error reading log";
        }
    }

    public static String scan_world_performance() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world == null) return "No world loaded";
            
            int entityCount = 0;
            for (var entity : mc.world.getEntities()) {
                entityCount++;
            }
            
            int fps = mc.getCurrentFps();
            long memory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            int memoryMB = (int)(memory / 1024 / 1024);
            
            return String.format("FPS: %d | Entities: %d | RAM: %dMB", fps, entityCount, memoryMB);
        } catch (Exception e) {
            return "Performance data unavailable";
        }
    }
}
