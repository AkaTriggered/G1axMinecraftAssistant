package dev.g1ax;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ConfigScreen extends Screen {
    private static final String[] PROVIDERS = {"auto", "groq", "gemini"};

    private final Screen parent;
    private final Config config;
    private TextFieldWidget groqField;
    private TextFieldWidget geminiField;
    private ButtonWidget providerButton;
    private ButtonWidget trustButton;

    public ConfigScreen(Screen parent, Config config) {
        super(Text.literal("G1axAssistant Config"));
        this.parent = parent;
        this.config = config != null ? config : Config.load();
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;

        groqField = new TextFieldWidget(textRenderer, x, 80, 200, 20, Text.literal("Groq"));
        groqField.setMaxLength(256);
        groqField.setText(config.groqApiKey);
        addDrawableChild(groqField);

        geminiField = new TextFieldWidget(textRenderer, x, 120, 200, 20, Text.literal("Gemini"));
        geminiField.setMaxLength(256);
        geminiField.setText(config.geminiApiKey);
        addDrawableChild(geminiField);

        providerButton = ButtonWidget.builder(providerLabel(), btn -> {
            config.provider = nextProvider(config.provider);
            btn.setMessage(providerLabel());
        }).dimensions(x, 150, 200, 20).build();
        addDrawableChild(providerButton);

        trustButton = ButtonWidget.builder(trustLabel(), btn -> {
            config.trustActions = !config.trustActions;
            btn.setMessage(trustLabel());
        }).dimensions(x, 175, 200, 20).build();
        addDrawableChild(trustButton);

        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), btn -> {
            config.groqApiKey = groqField.getText().trim();
            config.geminiApiKey = geminiField.getText().trim();
            config.normalize();
            config.save();
            close();
        }).dimensions(width / 2 - 50, 205, 100, 20).build());

        // Focus the first field so typing works immediately (click a field to switch).
        setInitialFocus(groqField);
    }

    private Text trustLabel() {
        return Text.literal(config.trustActions
            ? "Default: TRUST (actions run)"
            : "Default: SAFE (actions previewed)");
    }

    private Text providerLabel() {
        return Text.literal("Provider: " + config.provider);
    }

    private static String nextProvider(String current) {
        for (int i = 0; i < PROVIDERS.length; i++) {
            if (PROVIDERS[i].equalsIgnoreCase(current)) {
                return PROVIDERS[(i + 1) % PROVIDERS.length];
            }
        }
        return PROVIDERS[0];
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, "G1axAssistant Config", width / 2, 20, 0xFF55FF);
        ctx.drawCenteredTextWithShadow(textRenderer, "By G1ax | github.com/@AkaTriggered", width / 2, 35, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "Groq API Key:", width / 2 - 100, 70, 0xAAAAAA);
        ctx.drawTextWithShadow(textRenderer, "Gemini API Key:", width / 2 - 100, 110, 0xAAAAAA);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (groqField.keyPressed(keyCode, scanCode, modifiers) || groqField.isActive()) {
            return true;
        }
        if (geminiField.keyPressed(keyCode, scanCode, modifiers) || geminiField.isActive()) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (groqField.charTyped(chr, modifiers)) return true;
        if (geminiField.charTyped(chr, modifiers)) return true;
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
