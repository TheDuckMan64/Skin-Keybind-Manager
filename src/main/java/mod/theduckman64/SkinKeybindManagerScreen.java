package mod.theduckman64;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.toasts.SystemToast;
import com.mojang.authlib.GameProfile;

import java.awt.image.BufferedImage;
import java.util.List;
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
                                // Download current skin
                                BufferedImage skin = SkinKeybindManagerClient.downloadSkin(
                                        SkinKeybindManagerClient.user.getName()
                                );
                                String variant = SkinKeybindManagerClient.getVariant(
                                        SkinKeybindManagerClient.user.getName()
                                );

                                // Decode existing skin keybinds
                                Map<String, SkinKeybindManagerClient.KeyData> skinKeybinds =
                                        SkinKeybindManagerClient.decodePlayerSkin(skin, variant);

                                // Merge with current client bindings (client takes priority)
                                Map<String, SkinKeybindManagerClient.KeyData> mergedBindings =
                                        SkinKeybindManagerClient.getMergedKeybinds(skinKeybinds);

                                // Encode merged bindings into skin
                                BufferedImage encodedSkin = SkinKeybindManagerClient.encodePlayerSkin(
                                        mergedBindings,
                                        skin,
                                        variant);

                                boolean isSlim = variant.equalsIgnoreCase("slim");
                                boolean success = SkinKeybindManagerClient.uploadSkin(
                                        SkinKeybindManagerClient.saveSkinToDisk(encodedSkin),
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
                                BufferedImage skin = SkinKeybindManagerClient.downloadSkin(
                                        SkinKeybindManagerClient.user.getName()
                                );

                                Map<String, SkinKeybindManagerClient.KeyData> keybinds =
                                        SkinKeybindManagerClient.decodePlayerSkin(
                                                skin,
                                                SkinKeybindManagerClient.getVariant(
                                                        SkinKeybindManagerClient.user.getName()
                                                )
                                        );

                                int applied = SkinKeybindManagerClient.applyKeybinds(keybinds);

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
                                BufferedImage skin = SkinKeybindManagerClient.downloadSkin(
                                        SkinKeybindManagerClient.user.getName()
                                );
                                String variant = SkinKeybindManagerClient.getVariant(
                                        SkinKeybindManagerClient.user.getName()
                                );

                                // Decode skin keybinds and merge with client
                                Map<String, SkinKeybindManagerClient.KeyData> skinKeybinds =
                                        SkinKeybindManagerClient.decodePlayerSkin(skin, variant);
                                Map<String, SkinKeybindManagerClient.KeyData> mergedBindings =
                                        SkinKeybindManagerClient.getMergedKeybinds(skinKeybinds);

                                // Encode and save
                                BufferedImage encodedSkin = SkinKeybindManagerClient.encodePlayerSkin(
                                        mergedBindings,
                                        skin,
                                        variant
                                );
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
                                BufferedImage skin = SkinKeybindManagerClient.loadSkinFromDisk();
                                if (skin == null) {
                                    showToast("Error", "No skin file found on disk");
                                    return;
                                }

                                Map<String, SkinKeybindManagerClient.KeyData> decoded =
                                        SkinKeybindManagerClient.decodePlayerSkin(
                                                skin,
                                                SkinKeybindManagerClient.getVariant(
                                                        SkinKeybindManagerClient.user.getName()
                                                )
                                        );
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
        Minecraft.getInstance().getToastManager().addToast(
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