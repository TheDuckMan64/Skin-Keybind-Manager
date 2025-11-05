package mod.theduckman64;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

public class SkinKeybindManagerClient implements ClientModInitializer {

	private static final OkHttpClient client = new OkHttpClient();
	private static boolean ran = false;
	public static Session session;

	private static final int[][] OVERLAY_RECTS_CLASSIC = {
			{40, 0, 56, 8},   // head overlay 1
			{32, 8, 64, 16},   // head overlay 2
			{20, 32, 36, 36},  // body overlay 1
			{16, 36, 40, 48},  // body overlay 2
			{44, 32, 52, 36},  // right arm overlay 1
			{40, 36, 56, 48},  // right arm overlay 2
			{52, 48, 60, 52},  // left arm overlay 1
			{48, 52, 64, 64},  // left arm overlay 2
			{4, 32, 12, 36},    // right leg overlay 1
			{0, 36, 16, 48},    // right leg overlay 2
			{4, 48, 12, 52},    // left leg overlay 1
			{0, 52, 16, 64}    // left leg overlay 2
	};

	private static final int[][] OVERLAY_RECTS_SLIM = {
			{40, 0, 56, 8},   // head overlay 1
			{32, 8, 64, 16},   // head overlay 2
			{20, 32, 36, 36},  // body overlay 1
			{16, 36, 40, 48},  // body overlay 2
			{44, 32, 50, 36},  // right arm overlay 1 (slim is 3px wide)
			{40, 36, 54, 48},  // right arm overlay 2
			{52, 48, 58, 52},  // left arm overlay 1 (slim is 3px wide)
			{48, 52, 62, 64},  // left arm overlay 2
			{4, 32, 12, 36},    // right leg overlay 1
			{0, 36, 16, 48},    // right leg overlay 2
			{4, 48, 12, 52},    // left leg overlay 1
			{0, 52, 16, 64}    // left leg overlay 2
	};

	private static int[][] getOverlayRects(String variant) {
		return variant.equalsIgnoreCase("slim") ? OVERLAY_RECTS_SLIM : OVERLAY_RECTS_CLASSIC;
	}

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (ran) return;
			session = mc.getSession();
			ran = true;
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof ControlsOptionsScreen)) return;
			System.out.println("ControlOptionsScreen");

			ButtonWidget myButton = ButtonWidget.builder(
							Text.literal("Skin Keybind Manager"),
							b -> client.setScreen(new SkinKeybindManagerScreen(screen))
					)
					.dimensions(5, 5, 150, 20)
					.build();

			Screens.getButtons(screen).add(myButton);
		});
	}

	public static BufferedImage encodePlayerSkin(List<KeyBinding> keybindings, BufferedImage skin, String variant) throws Exception {
		if (skin == null) return null;

		// Step 1: Build keybind string (ID:KEY:TYPE format where TYPE is K or M)
		StringBuilder sb = new StringBuilder();
		sb.append("#SKINKEYBINDS_START\n");
		if (keybindings != null && !keybindings.isEmpty()) {
			for (KeyBinding kb : keybindings) {
				String id = kb.getId();
				InputUtil.Key boundKey = KeyBindingHelper.getBoundKeyOf(kb);

				// Skip unbound keys (UNKNOWN_KEY has code -1)
				if (boundKey == InputUtil.UNKNOWN_KEY || boundKey.getCode() == -1) {
					continue;
				}

				String translationKey = boundKey.getTranslationKey();
				sb.append(id).append(":").append(translationKey).append(";");
			}
		}
		sb.append("\n#SKINKEYBINDS_END\n");

		// Step 2: Compress with LZMA/XZ
		byte[] compressed;
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
			 XZOutputStream xzOut = new XZOutputStream(baos, new LZMA2Options())) {
			xzOut.write(sb.toString().getBytes());
			xzOut.finish();
			compressed = baos.toByteArray();
		}

		// Step 2.5: Prepare data with a 4-byte length prefix
		ByteArrayOutputStream finalDataOut = new ByteArrayOutputStream();
		finalDataOut.write((compressed.length >> 24) & 0xFF);
		finalDataOut.write((compressed.length >> 16) & 0xFF);
		finalDataOut.write((compressed.length >> 8) & 0xFF);
		finalDataOut.write(compressed.length & 0xFF);
		finalDataOut.write(compressed);
		byte[] prefixedCompressed = finalDataOut.toByteArray();

		// Step 3: Write bytes into overlay pixels (ARGB format)
		int[][] overlayRects = getOverlayRects(variant);
		int idx = 0;
		for (int[] rect : overlayRects) {
			for (int y = rect[1]; y < rect[3]; y++) {
				for (int x = rect[0]; x < rect[2]; x++) {
					if (idx + 3 < prefixedCompressed.length) {
						int a = prefixedCompressed[idx++] & 0xFF;
						int r = prefixedCompressed[idx++] & 0xFF;
						int g = prefixedCompressed[idx++] & 0xFF;
						int b = prefixedCompressed[idx++] & 0xFF;
						int argb = (a << 24) | (r << 16) | (g << 8) | b;
						skin.setRGB(x, y, argb);
					} else {
						skin.setRGB(x, y, 0x00000000);
					}
				}
			}
		}

		return skin;
	}

	/**
	 * Merge existing skin keybinds with current Fabric keybinds.
	 * Current client keybinds take precedence, skin keybinds are used as fallback.
	 */
	public static List<KeyBinding> getMergedKeybinds(Map<String, KeyData> skinKeybindMap) {
		MinecraftClient mc = MinecraftClient.getInstance();
		List<KeyBinding> mergedList = new ArrayList<>();

		for (KeyBinding kb : mc.options.allKeys) {
			String translationKey = kb.getBoundKeyTranslationKey();
			InputUtil.Key boundKey = KeyBindingHelper.getBoundKeyOf(kb);

			// Always use current client binding if it's set (not UNKNOWN_KEY)
			if (boundKey != InputUtil.UNKNOWN_KEY && boundKey.getCode() != -1) {
				mergedList.add(kb);
			} else if (skinKeybindMap != null && skinKeybindMap.containsKey(translationKey)) {
				// Fall back to skin keybind only if client binding is unset
				KeyData keyData = skinKeybindMap.get(translationKey);
				InputUtil.Key skinKey = InputUtil.fromTranslationKey(keyData.translationKey);
				kb.setBoundKey(skinKey);
			}
		}
		return mergedList;
	}

	/**
	 * Decode keybinds from a skin image overlay.
	 * Returns a map of keybind ID -> KeyData (key translation + type) for later application.
	 */
	public static Map<String, KeyData> decodePlayerSkin(BufferedImage skin, String variant) {
		Map<String, KeyData> keybindMap = new HashMap<>();
		if (skin == null) return keybindMap;

		int[][] overlayRects = getOverlayRects(variant);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		// Step 1: Extract overlay bytes (ARGB format)
		for (int[] rect : overlayRects) {
			for (int y = rect[1]; y < rect[3]; y++) {
				for (int x = rect[0]; x < rect[2]; x++) {
					int argb = skin.getRGB(x, y);
					baos.write((argb >> 24) & 0xFF); // Alpha
					baos.write((argb >> 16) & 0xFF); // Red
					baos.write((argb >> 8) & 0xFF);  // Green
					baos.write(argb & 0xFF);         // Blue
				}
			}
		}

		byte[] bytes = baos.toByteArray();
		if (bytes.length < 4) return keybindMap;

		// Step 2: Read the 4-byte length prefix
		int dataLength = ((bytes[0] & 0xFF) << 24) |
				((bytes[1] & 0xFF) << 16) |
				((bytes[2] & 0xFF) << 8) |
				(bytes[3] & 0xFF);

		if (dataLength <= 0 || dataLength > bytes.length - 4) {
			showToast("No prior skin data found");
			return keybindMap;
		}

		byte[] xzData = Arrays.copyOfRange(bytes, 4, 4 + dataLength);

		// Step 3: Decompress XZ
		try (XZInputStream xzIn = new XZInputStream(new ByteArrayInputStream(xzData))) {
			ByteArrayOutputStream decoded = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int n;
			while ((n = xzIn.read(buffer)) != -1) decoded.write(buffer, 0, n);

			String result = decoded.toString();
			int start = result.indexOf("#SKINKEYBINDS_START");
			int end = result.indexOf("#SKINKEYBINDS_END");
			if (start == -1 || end == -1 || start >= end) return keybindMap;

			String data = result.substring(start + "#SKINKEYBINDS_START".length(), end).trim();
			if (!data.isEmpty()) {
				String[] entries = data.split(";");
				for (String entry : entries) {
					if (entry.isEmpty()) continue;
					String[] parts = entry.split(":");
					if (parts.length == 2) {
						String id = parts[0];
						String translationKey = parts[1];
						keybindMap.put(id, new KeyData(translationKey, id));
					}
				}
			}
		} catch (Exception ignored) {}
		return keybindMap;
	}

	/**
	 * Simple data class to hold key translation and type
	 */
	public static class KeyData {
		public final String translationKey;
		public final String id;

		public KeyData(String keyTranslationKey, String id) {
			this.translationKey = keyTranslationKey;
			this.id = id;
		}
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

	/**
	 * Apply keybinds from a decoded map (translationKey -> KeyData).
	 * Matches keybinds by translation key with existing client keybinds.
	 * Always overwrites existing bindings with the skin's bindings.
	 */
	public static int applyKeybinds(Map<String, KeyData> skinKeybindMap) {
		if (skinKeybindMap == null || skinKeybindMap.isEmpty()) return 0;

		MinecraftClient mc = MinecraftClient.getInstance();
		KeyBinding[] allKeys = mc.options.allKeys;
		int applied = 0;

		for (KeyBinding kb : allKeys) {
			String id = kb.getId();

			if (skinKeybindMap.containsKey(id)) {
				KeyData keyData = skinKeybindMap.get(id);

				// Parse the key from its translation key
				InputUtil.Key newKey = InputUtil.fromTranslationKey(keyData.translationKey);

				kb.setBoundKey(newKey);
				applied++;
			}
		}

		KeyBinding.updateKeysByCode();
		showToast("[SkinKeybindManager] Applied " + applied + " keybinds from skin.");
		return applied;
	}

	public static @NotNull File saveSkinToDisk(BufferedImage skin) throws IOException {
		File out = new File(FabricLoader.getInstance().getGameDir() + "/" + "skin_encoded.png");
		ImageIO.write(skin, "PNG", out);
		return out;
	}

	public static BufferedImage loadSkinFromDisk() {
		try {
			File skinFile = new File(FabricLoader.getInstance().getGameDir() + "/" + "skin_encoded.png");
			if (!skinFile.exists()) {
				showToast("No skin on disk");
				return null;
			}
			return ImageIO.read(skinFile);
		} catch (Exception e) {
			showToast("Error: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	public static BufferedImage downloadSkin(String username) throws Exception {
		String uuid = getProfileUUID(username);
		if (uuid == null) {
			showToast("Error: UUID not found");
		}

		String accessToken = session.getAccessToken();
		String response = getSessionServerProfile(uuid, accessToken);
		JsonObject profileJson = JsonParser.parseString(response).getAsJsonObject();
		JsonArray properties = profileJson.getAsJsonArray("properties");

		String base64Value = null;
		for (JsonElement e : properties) {
			JsonObject prop = e.getAsJsonObject();
			if ("textures".equals(prop.get("name").getAsString())) {
				base64Value = prop.get("value").getAsString();
				break;
			}
		}

		if (base64Value == null) {
			return null;
		}

		String decodedJson = new String(java.util.Base64.getDecoder().decode(base64Value));
		JsonObject texturesJson = JsonParser.parseString(decodedJson).getAsJsonObject().getAsJsonObject("textures");
		if (!texturesJson.has("SKIN")) return null;

		String skinUrl = texturesJson.getAsJsonObject("SKIN").get("url").getAsString();

		Request request = new Request.Builder().url(skinUrl).build();
		try (Response resp = client.newCall(request).execute()) {
			if (!resp.isSuccessful()) {
				showToast("Failed to download skin: " + resp.code());
				return null;
			}
			byte[] imageBytes;
            assert resp.body() != null;
            imageBytes = resp.body().bytes();
            return ImageIO.read(new ByteArrayInputStream(imageBytes));
		}
	}

	public static String getVariant(BufferedImage skin) {
		if (skin == null) return "classic";
		int x = 54;
		int y = 20;
		if (skin.getWidth() < 64 || skin.getHeight() < 64) return "classic";
		int rgba = skin.getRGB(x, y);
		int alpha = (rgba >> 24) & 0xFF;
		return (alpha != 0) ? "slim" : "classic";
	}

	public static boolean uploadSkin(File skinFile, String slim) throws IOException {
		MinecraftClient mc = MinecraftClient.getInstance();
		Session session = mc.getSession();
		String accessToken = session.getAccessToken();

		String url = "https://api.minecraftservices.com/minecraft/profile/skins";

		MultipartBody.Builder bodyBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);
		bodyBuilder.addFormDataPart("file", skinFile.getName(),
				RequestBody.create(skinFile, MediaType.parse("image/png")));
		bodyBuilder.addFormDataPart("variant", slim);

		Request request = new Request.Builder()
				.url(url)
				.post(bodyBuilder.build())
				.addHeader("Authorization", "Bearer " + accessToken)
				.build();

		try (Response resp = client.newCall(request).execute()) {
			if (!resp.isSuccessful()) {
				showToast("Failed to upload skin: " + resp.code() + " " + resp.message());
				return false;
			}
			return true;
		}
	}

	private static String getProfileUUID(String username) throws IOException {
		String url = "https://api.mojang.com/users/profiles/minecraft/" + username;
		Request request = new Request.Builder().url(url).build();
		try (Response resp = client.newCall(request).execute()) {
			if (!resp.isSuccessful()) return null;
            assert resp.body() != null;
            String body = resp.body().string();
			JsonObject json = JsonParser.parseString(body).getAsJsonObject();
			return json.get("id").getAsString();
		}
	}

	private static String getSessionServerProfile(String uuid, String accessToken) throws IOException {
		Request request = new Request.Builder()
				.url("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid)
				.addHeader("Authorization", "Bearer " + accessToken)
				.build();

		try (Response resp = client.newCall(request).execute()) {
			if (!resp.isSuccessful())
				throw new IOException("[SkinKeybindManager] Session server HTTP error: " + resp);
            assert resp.body() != null;
            return resp.body().string();
		}
	}
}