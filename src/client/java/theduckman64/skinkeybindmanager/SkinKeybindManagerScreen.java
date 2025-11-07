package theduckman64.skinkeybindmanager;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.client.toast.SystemToast;

import java.awt.image.BufferedImage;
import java.util.Map;

public class SkinKeybindManagerScreen extends Screen {

    private final Screen parent;
    private static long lastUploadTime = 0;
    private static final long UPLOAD_COOLDOWN_MS = 10000; // 10 seconds

    public SkinKeybindManagerScreen(Screen parent) {
        super(Text.of("Skin Keybind Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Back button
        ButtonWidget backButton = ButtonWidget.builder(
                        Text.of("Back"),
                        b -> client.setScreen(parent))
                .dimensions(5, 5, 150, 20)
                .build();
        Screens.getButtons(this).add(backButton);

        ButtonWidget uploadButton = ButtonWidget.builder(
                        Text.of("Upload keybinds to skin"),
                        b -> {
                            try {
                                long currentTime = System.currentTimeMillis();
                                long timeSinceLastUpload = currentTime - lastUploadTime;

                                if (timeSinceLastUpload < UPLOAD_COOLDOWN_MS) {
                                    long remainingMs = UPLOAD_COOLDOWN_MS - timeSinceLastUpload;
                                    long remainingSeconds = (remainingMs + 999) / 1000; // Round up
                                    showToast("Please wait " + remainingSeconds + " seconds (API Limit reached)");
                                    return;
                                }
                                // Download current skin
                                BufferedImage skin = SkinKeybindManagerClient.downloadSkin(
                                        SkinKeybindManagerClient.session.getUsername()
                                );
                                String variant = SkinKeybindManagerClient.getVariant(
                                        SkinKeybindManagerClient.session.getUsername()
                                );

                                // Decode existing skin keybinds
                                // --- Use SkinKeybindUtils.KeyData ---
                                Map<String, SkinKeybindUtils.KeyData> skinKeybinds =
                                        SkinKeybindManagerClient.decodePlayerSkin(skin, variant);

                                // Merge with current client bindings (client takes priority)
                                // --- Use SkinKeybindUtils.KeyData ---
                                Map<String, SkinKeybindUtils.KeyData> mergedBindings = SkinKeybindManagerClient.getMergedKeybinds(skinKeybinds);

                                // Encode merged bindings into skin
                                BufferedImage encodedSkin = SkinKeybindUtils.encodePlayerSkin( // This can call SkinKeybindUtils directly
                                        mergedBindings,
                                        skin,
                                        variant);

                                boolean success = SkinKeybindManagerClient.uploadSkin(
                                        SkinKeybindManagerClient.saveSkinToDisk(encodedSkin),
                                        variant);

                                if (success) {
                                    showToast("Successfully uploaded keybinds to skin");
                                    lastUploadTime = System.currentTimeMillis();
                                } else {
                                    showToast("Failed to upload skin");
                                }
                            } catch (Exception e) {
                                showToast("Error: " + e.getMessage());
                                e.printStackTrace();
                            }
                        })
                .dimensions(5, 30, 150, 20)
                .build();
        Screens.getButtons(this).add(uploadButton);

        ButtonWidget downloadButton = ButtonWidget.builder(
                        Text.of("Download keybinds from skin"),
                        b -> {
                            try {
                                long currentTime = System.currentTimeMillis();
                                long timeSinceLastUpload = currentTime - lastUploadTime;

                                if (timeSinceLastUpload < UPLOAD_COOLDOWN_MS) {
                                    long remainingMs = UPLOAD_COOLDOWN_MS - timeSinceLastUpload;
                                    long remainingSeconds = (remainingMs + 999) / 1000; // Round up
                                    showToast("Please wait " + remainingSeconds + " seconds (API Limit reached)");
                                    return;
                                }

                                BufferedImage skin = SkinKeybindManagerClient.downloadSkin(
                                        SkinKeybindManagerClient.session.getUsername()
                                );

                                // --- Use SkinKeybindUtils.KeyData ---
                                Map<String, SkinKeybindUtils.KeyData> keybinds =
                                        SkinKeybindManagerClient.decodePlayerSkin(
                                                skin,
                                                SkinKeybindManagerClient.getVariant(
                                                        SkinKeybindManagerClient.session.getUsername()
                                                )
                                        );

                                int applied = SkinKeybindManagerClient.applyKeybinds(keybinds);
                                lastUploadTime = System.currentTimeMillis();
                                showToast("Applied " + applied + " keybinds from skin.");
                            } catch (Exception e) {
                                showToast("Error: " + e.getMessage());
                                e.printStackTrace();
                            }
                        })
                .dimensions(160, 30, 150, 20)
                .build();
        Screens.getButtons(this).add(downloadButton);


        // Save PNG to disk (encode online skin with merged keybinds and save)
        ButtonWidget toDiskButton = ButtonWidget.builder(
                        Text.of("Save skin with keybinds to disk"),
                        b -> {
                            try {
                                BufferedImage skin = SkinKeybindManagerClient.downloadSkin(
                                        SkinKeybindManagerClient.session.getUsername()
                                );
                                String variant = SkinKeybindManagerClient.getVariant(
                                        SkinKeybindManagerClient.session.getUsername()
                                );

                                // Decode skin keybinds and merge with client
                                // --- Use SkinKeybindUtils.KeyData ---
                                Map<String, SkinKeybindUtils.KeyData> skinKeybinds =
                                        SkinKeybindManagerClient.decodePlayerSkin(skin, variant);
                                // --- Use SkinKeybindUtils.KeyData ---
                                Map<String, SkinKeybindUtils.KeyData> mergedBindings = SkinKeybindManagerClient.getMergedKeybinds(skinKeybinds);

                                // Encode and save
                                BufferedImage encodedSkin = SkinKeybindUtils.encodePlayerSkin( // This can call SkinKeybindUtils directly
                                        mergedBindings,
                                        skin,
                                        variant
                                );
                                SkinKeybindManagerClient.saveSkinToDisk(encodedSkin);
                                showToast("Saved skin_encoded.png to disk.");
                            } catch (Exception e) {
                                showToast("Error: " + e.getMessage());
                                e.printStackTrace();
                            }
                        })
                .dimensions(5, 55, 150, 20)
                .build();
        Screens.getButtons(this).add(toDiskButton);

        // Load keybinds from disk
        ButtonWidget fromDiskButton = ButtonWidget.builder(
                        Text.of("Load skin with keybinds from disk"),
                        b -> {
                            try {
                                BufferedImage skin = SkinKeybindManagerClient.loadSkinFromDisk();
                                if (skin == null) {
                                    System.err.println("[SkinKeybindManagerScreen] No skin file found on disk.");
                                    return;
                                }

                                // --- Use SkinKeybindUtils.KeyData ---
                                Map<String, SkinKeybindUtils.KeyData> decoded =
                                        SkinKeybindManagerClient.decodePlayerSkin(
                                                skin,
                                                SkinKeybindManagerClient.getVariant(
                                                        SkinKeybindManagerClient.session.getUsername()
                                                )
                                        );
                                int applied = SkinKeybindManagerClient.applyKeybinds(decoded);
                                showToast("[SkinKeybindManagerScreen] Applied " + applied + " keybinds from disk skin.");
                            } catch (Exception e) {
                                showToast("Error: " + e.getMessage());
                                e.printStackTrace();
                            }
                        })
                .dimensions(160, 55, 150, 20)
                .build();
        Screens.getButtons(this).add(fromDiskButton);

    }

    public void showToast(String message) {
        MinecraftClient.getInstance().getToastManager().add(
                SystemToast.create(
                        MinecraftClient.getInstance(),
                        SystemToast.Type.NARRATOR_TOGGLE,
                        Text.literal("Skin Keybind Manager"),
                        Text.literal(message)
                )
        );
    }


    @Override
    public void close() {
        client.setScreen(parent);
    }
}