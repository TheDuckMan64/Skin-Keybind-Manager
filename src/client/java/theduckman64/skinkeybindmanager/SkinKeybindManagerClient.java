package theduckman64.skinkeybindmanager;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.session.Session;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SkinKeybindManagerClient implements ClientModInitializer {

    private static boolean ran = false;
    public static Session session;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (ran) return;
            session = mc.getSession();
            ran = true;
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof ControlsOptionsScreen)) return;

            ButtonWidget myButton = ButtonWidget.builder(
                            Text.literal("Skin Keybind Manager"),
                            b -> client.setScreen(new SkinKeybindManagerScreen(screen)) // Assumes this screen exists
                    )
                    .dimensions(5, 5, 150, 20)
                    .build();

            Screens.getButtons(screen).add(myButton);
        });
    }

    /**
     * Merge existing skin keybinds with current Fabric keybinds.
     * Returns a map of keybind ID -> KeyData with the following logic:
     * 1) If the key exists (is bound) in the client, add it to the output
     * 2) If the key exists but is unbound in the client, remove it from skinKeybindMap
     * 3) Add all leftover items in skinKeybindMap to the output
     */
    public static Map<String, SkinKeybindUtils.KeyData> getMergedKeybinds(Map<String, SkinKeybindUtils.KeyData> skinKeybindMap) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Map<String, SkinKeybindUtils.KeyData> mergedMap = new HashMap<>();

        // Create a copy of skinKeybindMap to track leftovers
        Map<String, SkinKeybindUtils.KeyData> remainingSkinKeybinds = skinKeybindMap != null
                ? new HashMap<>(skinKeybindMap)
                : new HashMap<>();

        for (KeyBinding kb : mc.options.allKeys) {
            String id = kb.getTranslationKey();
            InputUtil.Key boundKey = KeyBindingHelper.getBoundKeyOf(kb);

            // Check if key is bound (not UNKNOWN_KEY and code != -1)
            if (boundKey != InputUtil.UNKNOWN_KEY && boundKey.getCode() != -1) {
                // 1) Key exists in client - add it to output
                String translationKey = boundKey.getTranslationKey();
                mergedMap.put(id, new SkinKeybindUtils.KeyData(translationKey, id));

                // Remove from remaining skin keybinds since we found it in client
                remainingSkinKeybinds.remove(id);
            } else {
                // 2) Key exists but is unbound - remove it from skin keybinds
                remainingSkinKeybinds.remove(id);
            }
        }

        // 3) Add all leftover items from skinKeybindMap to output
        mergedMap.putAll(remainingSkinKeybinds);

        return mergedMap;
    }

    /**
     * Apply keybinds from a decoded map (translationKey -> KeyData).
     * Matches keybinds by translation key with existing client keybinds.
     * Always overwrites existing bindings with the skin's bindings.
     */
    public static int applyKeybinds(Map<String, SkinKeybindUtils.KeyData> skinKeybindMap) {
        if (skinKeybindMap == null || skinKeybindMap.isEmpty()) return 0;

        MinecraftClient mc = MinecraftClient.getInstance();
        KeyBinding[] allKeys = mc.options.allKeys;
        int applied = 0;

        for (KeyBinding kb : allKeys) {
            String id = kb.getTranslationKey();

            if (skinKeybindMap.containsKey(id)) {
                SkinKeybindUtils.KeyData keyData = skinKeybindMap.get(id);

                // Parse the key from its translation key
                InputUtil.Key newKey = InputUtil.fromTranslationKey(keyData.translationKey());

                kb.setBoundKey(newKey);
                applied++;
            }
        }

        KeyBinding.updateKeysByCode();
        return applied;
    }

    public static void showToast(String message) {
        MinecraftClient.getInstance().getToastManager().add(
                SystemToast.create(
                        MinecraftClient.getInstance(),
                        SystemToast.Type.NARRATOR_TOGGLE,
                        Text.literal("Skin Keybind Manager"),
                        Text.literal(message)
                )
        );
    }

    // --- Wrapper Methods ---

    private static File getSkinFile() {
        return new File(FabricLoader.getInstance().getGameDir() + "/" + "skin_encoded.png");
    }

    public static @NotNull File saveSkinToDisk(BufferedImage skin) throws IOException {
        return SkinKeybindUtils.saveSkinToDisk(skin, getSkinFile());
    }

    public static BufferedImage loadSkinFromDisk() {
        try {
            File skinFile = getSkinFile();
            if (!skinFile.exists()) {
                showToast("No skin on disk");
                return null;
            }
            return SkinKeybindUtils.loadSkinFromDisk(skinFile);
        } catch (Exception e) {
            showToast("Error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static BufferedImage downloadSkin(String username) {
        if (session == null) {
            showToast("Session not initialized!");
            return null;
        }
        try {
            return SkinKeybindUtils.downloadSkin(username, session.getAccessToken());
        } catch (Exception e) {
            showToast("Failed to download skin: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static String getVariant(String username) {
        if (session == null) {
            showToast("Session not initialized!");
            return "classic";
        }
        try {
            return SkinKeybindUtils.getVariant(username, session.getAccessToken());
        } catch (Exception e) {
            showToast("Failed to get variant: " + e.getMessage());
            e.printStackTrace();
            return "classic";
        }
    }

    public static boolean uploadSkin(File skinFile, String slim) {
        if (session == null) {
            showToast("Session not initialized!");
            return false;
        }
        try {
            return SkinKeybindUtils.uploadSkin(skinFile, slim, session.getAccessToken());
        } catch (IOException e) {
            showToast("Failed to upload skin: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static Map<String, SkinKeybindUtils.KeyData> decodePlayerSkin(BufferedImage skin, String variant) {
        Map<String, SkinKeybindUtils.KeyData> map = SkinKeybindUtils.decodePlayerSkin(skin, variant);
        if (map.isEmpty()) {
            showToast("No prior skin data found");
        }
        return map;
    }
}