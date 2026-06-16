package dev.g1ax;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;

public class Config {
    // --- credentials ---
    public String groqApiKey = "";
    public String geminiApiKey = "";

    // --- provider / model selection ---
    public String provider = "auto";                       // auto | groq | gemini
    public String groqModel = "llama-3.3-70b-versatile";
    public String geminiModel = "gemini-2.0-flash";

    // --- agent behavior ---
    public double temperature = 0.4;
    public int maxTokens = 1024;
    public int maxIterations = 8;                           // tool-loop step budget per message

    // --- action safety ---
    public boolean trustActions = false;                   // false = safe mode (preview, don't execute)
    public boolean allowCommands = true;                   // hard switch for run_command
    public boolean allowChat = true;                       // hard switch for send_chat

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Paths.get("config/g1axminecraftassistant.json");

    public static Config load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                Config loaded = GSON.fromJson(Files.readString(CONFIG_PATH), Config.class);
                if (loaded != null) {
                    loaded.normalize();
                    return loaded;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new Config();
    }

    /** Fill in any missing/invalid values so an older or partial config file stays usable. */
    public void normalize() {
        if (groqApiKey == null) groqApiKey = "";
        if (geminiApiKey == null) geminiApiKey = "";
        if (provider == null || provider.isBlank()) provider = "auto";
        if (groqModel == null || groqModel.isBlank()) groqModel = "llama-3.3-70b-versatile";
        if (geminiModel == null || geminiModel.isBlank()) geminiModel = "gemini-2.0-flash";
        if (temperature < 0 || temperature > 2) temperature = 0.4;
        if (maxTokens <= 0) maxTokens = 1024;
        if (maxIterations <= 0) maxIterations = 8;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
