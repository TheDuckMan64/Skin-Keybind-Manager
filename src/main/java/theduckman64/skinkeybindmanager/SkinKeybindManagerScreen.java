package theduckman64.skinkeybindmanager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.toasts.SystemToast;

import java.awt.image.BufferedImage;
import java.util.Map;

public class SkinKeybindManagerScreen extends Screen {

    private final Screen parent;
    private static long lastUploadTime = 0;
    private static final long UPLOAD_COOLDOWN_MS = 60000; // 60 seconds

    public SkinKeybindManagerScreen(Screen parent) {
        super(Component.literal("Skin Keybind Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Back button
        this.addRenderableWidget(Button.builder(
                        Component.literal("Back"),
                        b -> minecraft.setScreen(parent))
                .bounds(5, 5, 150, 20)
                .build());

        // Upload keybinds to skin
        this.addRenderableWidget(Button.builder(
                        Component.literal("Upload keybinds to skin"),
                        b -> {
                            // Check cooldown
                            long currentTime = System.currentTimeMillis();
                            long timeSinceLastUpload = currentTime - lastUploadTime;

                            if (timeSinceLastUpload < UPLOAD_COOLDOWN_MS) {
                                long remainingMs = UPLOAD_COOLDOWN_MS - timeSinceLastUpload;
                                long remainingSeconds = (remainingMs + 999) / 1000; // Round up
                                showToast("Upload Cooldown", "Please wait " + remainingSeconds + " seconds");
                                return;
                            }

                            try {
                                // Download current skin (using wrapper)
                                BufferedImage skin = SkinKeybindManagerClient.downloadSkin();
                                String variant = SkinKeybindManagerClient.getVariant();

                                // Decode existing skin keybinds (using util)
                                Map<String, SkinKeybindUtils.KeyData> skinKeybinds =
                                        SkinKeybindUtils.decodePlayerSkin(skin, variant);

                                // Merge with current client bindings (using client method)
                                Map<String, SkinKeybindUtils.KeyData> mergedBindings =
                                        SkinKeybindManagerClient.getMergedKeybinds(skinKeybinds);

                                // Encode merged bindings into skin (using util)
                                BufferedImage encodedSkin = SkinKeybindUtils.encodePlayerSkin(
                                        mergedBindings,
                                        skin,
                                        variant);

                                boolean isSlim = variant.equalsIgnoreCase("slim");
                                // Upload (using wrapper)
                                boolean success = SkinKeybindManagerClient.uploadSkin(
                                        SkinKeybindManagerClient.saveSkinToDisk(encodedSkin), // save wrapper
                                        isSlim);

                                if (success) {
                                    lastUploadTime = currentTime; // Update cooldown timer
                                    showToast("Skin Keybinds", "Successfully uploaded keybinds to skin");
                                } else {
                                    showToast("Skin Keybinds", "Failed to upload skin");
                                }
                            } catch (Exception e) {
                                showToast("Error", "Failed to upload: " + e.getMessage());
                                e.printStackTrace();
                            }
                        })
                .bounds(5, 30, 150, 20)
                .build());

        // Download keybinds from skin
        this.addRenderableWidget(Button.builder(
                        Component.literal("Download keybinds from skin"),
                        b -> {
                            try {
                                long currentTime = System.currentTimeMillis();
                                long timeSinceLastUpload = currentTime - lastUploadTime;

                                if (timeSinceLastUpload < UPLOAD_COOLDOWN_MS) {
                                    long remainingMs = UPLOAD_COOLDOWN_MS - timeSinceLastUpload;
                                    long remainingSeconds = (remainingMs + 999) / 1000; // Round up
                                    showToast("Upload Cooldown", "Please wait " + remainingSeconds + " seconds");
                                    return;
                                }
                                // Download (using wrapper)
                                BufferedImage skin = SkinKeybindManagerClient.downloadSkin();
                                String variant = SkinKeybindManagerClient.getVariant();

                                // Decode (using util)
                                Map<String, SkinKeybindUtils.KeyData> keybinds =
                                        SkinKeybindUtils.decodePlayerSkin(skin, variant);

                                // Apply (using client method)
                                int applied = SkinKeybindManagerClient.applyKeybinds(keybinds);
                                lastUploadTime = currentTime;
                                showToast("Skin Keybinds", "Applied " + applied + " keybinds from skin");
                            } catch (Exception e) {
                                showToast("Error", "Failed to download: " + e.getMessage());
                                e.printStackTrace();
                            }
                        })
                .bounds(160, 30, 150, 20)
                .build());

        // Save PNG to disk
        this.addRenderableWidget(Button.builder(
                        Component.literal("Save PNG to disk"),
                        b -> {
                            try {
                                // Download (using wrapper)
                                BufferedImage skin = SkinKeybindManagerClient.downloadSkin();
                                String variant = SkinKeybindManagerClient.getVariant();

                                // Decode (using util)
                                Map<String, SkinKeybindUtils.KeyData> skinKeybinds =
                                        SkinKeybindUtils.decodePlayerSkin(skin, variant);
                                // Merge (using client method)
                                Map<String, SkinKeybindUtils.KeyData> mergedBindings =
                                        SkinKeybindManagerClient.getMergedKeybinds(skinKeybinds);

                                // Encode (using util)
                                BufferedImage encodedSkin = SkinKeybindUtils.encodePlayerSkin(
                                        mergedBindings,
                                        skin,
                                        variant
                                );
                                // Save (using wrapper)
                                SkinKeybindManagerClient.saveSkinToDisk(encodedSkin);

                                showToast("Skin Keybinds", "Skin saved to disk successfully");
                            } catch (Exception e) {
                                showToast("Error", "Failed to save: " + e.getMessage());
                                e.printStackTrace();
                            }
                        })
                .bounds(5, 55, 150, 20)
                .build());

        // Load keybinds from disk
        this.addRenderableWidget(Button.builder(
                        Component.literal("Load keybinds from disk"),
                        b -> {
                            try {
                                // Load (using wrapper)
                                BufferedImage skin = SkinKeybindManagerClient.loadSkinFromDisk();
                                // Get variant (using wrapper)
                                String variant = SkinKeybindManagerClient.getVariant();

                                // Decode (using util)
                                Map<String, SkinKeybindUtils.KeyData> decoded =
                                        SkinKeybindUtils.decodePlayerSkin(skin, variant);

                                // Apply (using client method)
                                int applied = SkinKeybindManagerClient.applyKeybinds(decoded);

                                showToast("Skin Keybinds", "Applied " + applied + " keybinds from disk");
                            } catch (Exception e) {
                                showToast("Error", "Failed to load: " + e.getMessage());
                                e.printStackTrace();
                            }
                        })
                .bounds(160, 55, 150, 20)
                .build());
    }

    private void showToast(String title, String message) {
        Minecraft.getInstance().getToasts().addToast(
                new SystemToast(
                        SystemToast.SystemToastId.NARRATOR_TOGGLE,
                        Component.literal(title),
                        Component.literal(message)
                )
        );
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}