package theduckman64.skinkeybindmanager;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

@Mod("skinkeybindmanager")
@EventBusSubscriber(modid = "skinkeybindmanager")
public class SkinKeybindManagerClient {

    public static User user;

    public SkinKeybindManagerClient(IEventBus modBus) {
        modBus.addListener(this::onClientSetup);
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        // Ensure this runs on the client thread where the Minecraft instance is guaranteed to be available
        event.enqueueWork(() -> {
            // Get the Minecraft instance
            Minecraft minecraft = Minecraft.getInstance();
            user = minecraft.getUser();
        });
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Minecraft mc = Minecraft.getInstance();

        // Add button to controls/options screen
        if (event.getScreen() instanceof net.minecraft.client.gui.screens.options.controls.ControlsScreen) {
            event.addListener(Button.builder(
                    Component.literal("Skin Keybind Manager"),
                    b -> mc.setScreen(new SkinKeybindManagerScreen(event.getScreen()))
            ).bounds(5, 5, 150, 20).build());
        }
    }

    /**
     * Merge existing skin keybinds with current NeoForge keybinds.
     * (Minecraft-specific logic)
     */
    public static Map<String, SkinKeybindUtils.KeyData> getMergedKeybinds(Map<String, SkinKeybindUtils.KeyData> skinKeybindMap) {
        Minecraft mc = Minecraft.getInstance();
        Map<String, SkinKeybindUtils.KeyData> mergedMap = new HashMap<>();

        Map<String, SkinKeybindUtils.KeyData> remainingSkinKeybinds = skinKeybindMap != null
                ? new HashMap<>(skinKeybindMap)
                : new HashMap<>();

        for (KeyMapping kb : mc.options.keyMappings) {
            String id = kb.getName();
            InputConstants.Key boundKey = kb.getKey();

            if (boundKey != InputConstants.UNKNOWN && boundKey.getValue() != -1) {
                String translationKey = boundKey.getName();
                mergedMap.put(id, new SkinKeybindUtils.KeyData(translationKey, id));
                remainingSkinKeybinds.remove(id);
            } else {
                remainingSkinKeybinds.remove(id);
            }
        }

        mergedMap.putAll(remainingSkinKeybinds);
        return mergedMap;
    }

    /**
     * Apply keybinds from a decoded map.
     * (Minecraft-specific logic)
     */
    public static int applyKeybinds(Map<String, SkinKeybindUtils.KeyData> skinKeybindMap) {
        if (skinKeybindMap == null || skinKeybindMap.isEmpty()) return 0;

        Minecraft mc = Minecraft.getInstance();
        KeyMapping[] allKeys = mc.options.keyMappings;
        int applied = 0;

        for (KeyMapping km : allKeys) {
            String name = km.getName();

            if (skinKeybindMap.containsKey(name)) {
                SkinKeybindUtils.KeyData keyData = skinKeybindMap.get(name);
                InputConstants.Key newKey = InputConstants.getKey(keyData.translationKey());
                km.setKey(newKey);
                applied++;
            }
        }

        KeyMapping.resetMapping();
        return applied;
    }

    // --- Wrapper Methods ---
    // These methods provide the Minecraft-specific context (game dir, auth token)
    // to the agnostic utility methods in SkinKeybindUtils.

    private static File getSkinFile() {
        return new File(FMLPaths.GAMEDIR.get().toFile(), "skin_encoded.png");
    }

    public static File saveSkinToDisk(BufferedImage skin) throws IOException {
        return SkinKeybindUtils.saveSkinToDisk(skin, getSkinFile());
    }

    public static BufferedImage loadSkinFromDisk() throws IOException {
        return SkinKeybindUtils.loadSkinFromDisk(getSkinFile());
    }

    public static BufferedImage downloadSkin() throws IOException {
        if (user == null) throw new IOException("User session not initialized.");
        return SkinKeybindUtils.downloadSkin(user.getProfileId().toString(), user.getAccessToken());
    }

    public static String getVariant() throws IOException {
        if (user == null) throw new IOException("User session not initialized.");
        return SkinKeybindUtils.getVariant(user.getProfileId().toString(), user.getAccessToken());
    }

    public static boolean uploadSkin(File skinFile, boolean slim) throws IOException {
        if (user == null) throw new IOException("User session not initialized.");
        String variant = slim ? "slim" : "classic";
        return SkinKeybindUtils.uploadSkin(skinFile, variant, user.getAccessToken());
    }
}