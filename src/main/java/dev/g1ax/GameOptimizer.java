package dev.g1ax;

import net.minecraft.client.MinecraftClient;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class GameOptimizer {
    private static final Path CONFIG_DIR = Paths.get("config");
    private static final Path BACKUP_DIR = Paths.get("config/g1ax_backups");
    
    public static String getSystemInfo() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            Runtime rt = Runtime.getRuntime();
            
            StringBuilder sb = new StringBuilder();
            sb.append("FPS: ").append(mc.getCurrentFps()).append("\n");
            sb.append("RAM Used: ").append((rt.totalMemory() - rt.freeMemory()) / 1024 / 1024).append("MB\n");
            sb.append("RAM Total: ").append(rt.maxMemory() / 1024 / 1024).append("MB\n");
            sb.append("CPU Cores: ").append(Runtime.getRuntime().availableProcessors()).append("\n");
            sb.append("Render Distance: ").append(mc.options.getViewDistance().getValue()).append("\n");
            sb.append("Graphics: ").append(mc.options.getGraphicsMode().getValue()).append("\n");
            
            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    public static String listAllConfigs() {
        try {
            StringBuilder sb = new StringBuilder("Available configs:\n");
            Files.walk(CONFIG_DIR, 2)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json") || 
                           p.toString().endsWith(".properties") || 
                           p.toString().endsWith(".toml"))
                .forEach(p -> sb.append("- ").append(CONFIG_DIR.relativize(p)).append("\n"));
            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    public static String readConfig(String configPath) {
        try {
            Path file = CONFIG_DIR.resolve(configPath);
            if (!Files.exists(file)) return "Not found: " + configPath;
            return Files.readString(file);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    public static String writeConfig(String configPath, String content) {
        try {
            Path file = CONFIG_DIR.resolve(configPath);
            if (!Files.exists(file)) return "Not found: " + configPath;
            
            // Create backup folder if not exists
            Files.createDirectories(BACKUP_DIR);
            
            // Auto backup with timestamp
            String backupName = configPath.replace("/", "_") + ".backup." + System.currentTimeMillis();
            Path backup = BACKUP_DIR.resolve(backupName);
            Files.copy(file, backup);
            
            Files.writeString(file, content);
            return "Updated " + configPath + " (backup: " + backupName + ")";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    public static String getAllModConfigs() {
        try {
            StringBuilder sb = new StringBuilder();
            Files.walk(CONFIG_DIR, 2)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".json") || 
                           p.toString().endsWith(".toml") || 
                           p.toString().endsWith(".properties"))
                .limit(10)
                .forEach(p -> {
                    try {
                        sb.append("\n=== ").append(CONFIG_DIR.relativize(p)).append(" ===\n");
                        sb.append(Files.readString(p)).append("\n");
                    } catch (Exception e) {
                        sb.append("Error reading\n");
                    }
                });
            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    public static String getInstalledMods() {
        try {
            StringBuilder sb = new StringBuilder();
            net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()
                .forEach(mod -> sb.append(mod.getMetadata().getName())
                    .append(" v").append(mod.getMetadata().getVersion()).append("\n"));
            return sb.toString();
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
