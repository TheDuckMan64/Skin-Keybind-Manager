package name.modid;

import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

import java.awt.image.BufferedImage;
import java.util.List;

public class SkinKeybindManagerScreen extends Screen {

    private final Screen parent;

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

        // Upload keybinds to skin (download live)
        ButtonWidget encodeButton = ButtonWidget.builder(
                        Text.of("Upload keybinds to skin"),
                        b -> {
                            try {
                                BufferedImage skin = SkinKeybindManagerClient.downloadSkin(
                                        SkinKeybindManagerClient.session.getUsername()
                                );
                                String variant = SkinKeybindManagerClient.getVariant(skin);

                                List<KeyBinding> bindings = SkinKeybindManagerClient.decodePlayerSkin(
                                        skin,
                                        variant
                                );

                                BufferedImage encodedSkin = SkinKeybindManagerClient.encodePlayerSkin(
                                        bindings,
                                        skin,
                                        variant);
                                SkinKeybindManagerClient.saveSkinToDisk(encodedSkin);

                                System.out.println("[SkinKeybindManagerScreen] Skin encoded and saved successfully.");
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .dimensions(5, 30, 150, 20)
                .build();
        Screens.getButtons(this).add(encodeButton);

        // Download keybinds from skin (download live)
        ButtonWidget decodeButton = ButtonWidget.builder(
                        Text.of("Download keybinds from skin"),
                        b -> {
                            try {
                                BufferedImage skin = SkinKeybindManagerClient.downloadSkin(
                                        SkinKeybindManagerClient.session.getUsername()
                                );

                                List<KeyBinding> bindings = SkinKeybindManagerClient.decodePlayerSkin(
                                        skin,
                                        SkinKeybindManagerClient.getVariant(skin)
                                );

                                SkinKeybindManagerClient.applyKeybinds(bindings);

                                System.out.println("[SkinKeybindManagerScreen] Keybinds decoded and applied.");
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        })
                .dimensions(160, 30, 150, 20)
                .build();
        Screens.getButtons(this).add(decodeButton);


        // Save PNG to disk (encode online skin and save)
        ButtonWidget toDiskButton = ButtonWidget.builder(
                        Text.of("Save PNG to disk"),
                        b -> {
                            try {
                                BufferedImage skin = SkinKeybindManagerClient.downloadSkin(
                                        SkinKeybindManagerClient.session.getUsername()
                                );
                                List<KeyBinding> currentBindings = SkinKeybindManagerClient.getMergedKeybinds(
                                        SkinKeybindManagerClient.decodePlayerSkin(
                                                skin,
                                                SkinKeybindManagerClient.getVariant(skin)
                                        )
                                );
                                BufferedImage encoded = SkinKeybindManagerClient.encodePlayerSkin(
                                        currentBindings,
                                        skin,
                                        SkinKeybindManagerClient.getVariant(skin)
                                );
                                SkinKeybindManagerClient.saveSkinToDisk(encoded);
                                System.out.println("[SkinKeybindManagerScreen] Skin with keybinds saved to disk.");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        })
                .dimensions(320, 30, 150, 20)
                .build();
        Screens.getButtons(this).add(toDiskButton);

        // Load keybinds from disk
        ButtonWidget fromDiskButton = ButtonWidget.builder(
                        Text.of("Load keybinds from disk"),
                        b -> {
                            try {
                                BufferedImage skin = SkinKeybindManagerClient.loadSkinFromDisk();
                                if (skin == null) return;
                                List<KeyBinding> decoded = SkinKeybindManagerClient.decodePlayerSkin(
                                        skin,
                                        SkinKeybindManagerClient.getVariant(skin)
                                );
                                int applied = SkinKeybindManagerClient.applyKeybinds(decoded);
                                System.out.println("[SkinKeybindManagerScreen] Applied " + applied + " keybinds from disk skin.");
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        })
                .dimensions(5, 60, 150, 20)
                .build();
        Screens.getButtons(this).add(fromDiskButton);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
