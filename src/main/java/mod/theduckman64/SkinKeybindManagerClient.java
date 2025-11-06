package mod.theduckman64;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import okhttp3.*;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;

@Mod("skinkeybindmanager")
@EventBusSubscriber(modid = "skinkeybindmanager")
public class SkinKeybindManagerClient {

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

	private static final OkHttpClient client = new OkHttpClient();
	public static User user;


	private static final int[][] OVERLAY_RECTS_CLASSIC = {
			{0, 0, 8, 8},
			{24, 0, 40, 8},
			{56, 0, 64, 8},
			{0, 16, 4, 20},
			{12, 16, 20, 20},
			{36, 16, 44, 20},
			{52, 16, 56, 20},
			{0, 32, 4, 36},
			{12, 32, 20, 36},
			{36, 32, 44, 36},
			{52, 32, 56, 36},
			{0, 48, 4, 52},
			{12, 48, 20, 52},
			{28, 48, 36, 52},
			{44, 48, 52, 52},
			{60, 48, 64, 52},
			{56, 16, 64, 48}
	};

	private static final int[][] OVERLAY_RECTS_SLIM = {
			{0, 0, 8, 8},
			{24, 0, 40, 8},
			{56, 0, 64, 8},
			{0, 16, 4, 20},
			{12, 16, 20, 20},
			{36, 16, 44, 20},
			{50, 16, 54, 20},
			{0, 32, 4, 36},
			{12, 32, 20, 36},
			{36, 32, 44, 36},
			{50, 32, 54, 36},
			{0, 48, 4, 52},
			{12, 48, 20, 52},
			{28, 48, 36, 52},
			{42, 48, 52, 52},
			{46, 52, 48, 64},
			{58, 48, 64, 52},
			{54, 16, 64, 48},
			{62, 52, 64, 64}
	};

	private static int[][] getOverlayRects(String variant) {
		return variant.equalsIgnoreCase("slim") ? OVERLAY_RECTS_SLIM : OVERLAY_RECTS_CLASSIC;
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

	public static BufferedImage encodePlayerSkin(List<KeyMapping> keybindings, BufferedImage skin, String variant) throws Exception {
		if (skin == null) return null;

		// Step 1: Build keybind string (ID:KEY format)
		StringBuilder sb = new StringBuilder();
		sb.append("#SKINKEYBINDS_START\n");
		if (keybindings != null && !keybindings.isEmpty()) {
			for (KeyMapping km : keybindings) {
				String id = km.getName();
				InputConstants.Key boundKey = km.getKey();

				// Skip unbound keys (UNKNOWN has value -1)
				if (boundKey.getValue() == -1 || boundKey == InputConstants.UNKNOWN) {
					continue;
				}

				String translationKey = boundKey.getName();
				System.out.println("[SkinKeybindManager] Encoding keybind -> ID: " + id + ", Key: " + translationKey);
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

					// Check if there are enough bytes remaining to read a full ARGB pixel (4 bytes)
					if (idx + 4 <= prefixedCompressed.length) {
						// Since we've confirmed there are 4 bytes, we can read them without the ternary checks.
						int a = prefixedCompressed[idx++] & 0xFF;
						int r = prefixedCompressed[idx++] & 0xFF;
						int g = prefixedCompressed[idx++] & 0xFF;
						int b = prefixedCompressed[idx++] & 0xFF;

						int argb = (a << 24) | (r << 16) | (g << 8) | b;
						skin.setRGB(x, y, argb);
					} else {
						skin.setRGB(x, y, 0xdeadbeef);
					}
				}
			}
		}
		return skin;
	}

	/**
	 * Merge existing skin keybinds with current keybinds.
	 * Current client keybinds take precedence, skin keybinds are used as fallback.
	 */
	public static List<KeyMapping> getMergedKeybinds(Map<String, KeyData> skinKeybindMap) {
		Minecraft mc = Minecraft.getInstance();
		List<KeyMapping> mergedList = new ArrayList<>();

		for (KeyMapping km : mc.options.keyMappings) {
			String name = km.getName();
			InputConstants.Key boundKey = km.getKey();

			// Always use current client binding if it's set (not UNKNOWN)
			if (boundKey != InputConstants.UNKNOWN && boundKey.getValue() != -1) {
				System.out.println("[SkinKeybindManager] Using client keybind -> ID: " + name +
						", Key: " + boundKey.getName());
			} else if (skinKeybindMap != null && skinKeybindMap.containsKey(name)) {
				// Fall back to skin keybind only if client binding is unset
				KeyData keyData = skinKeybindMap.get(name);
				InputConstants.Key skinKey = InputConstants.getKey(keyData.translationKey);
				km.setKey(skinKey);
				System.out.println("[SkinKeybindManager] Using skin keybind (fallback) -> ID: " + name +
						", Key: " + keyData.translationKey);
			} else {
				System.out.println("[SkinKeybindManager] Skipping unbound key -> ID: " + name);
			}

			mergedList.add(km);
		}

		System.out.println("[SkinKeybindManager] Total merged keybinds: " + mergedList.size());
		return mergedList;
	}

	/**
	 * Decode keybinds from a skin image overlay.
	 * Returns a map of keybind ID -> KeyData (key translation) for later application.
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
			System.err.println("[SkinKeybindManager] Invalid or corrupt length prefix.");
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
		} catch (Exception e) {
			System.out.println("[SkinKeybindManager] No keybinds found or decompression failed: " + e);
		}

		return keybindMap;
	}

	/**
	 * Simple data class to hold key translation
	 */
	public static class KeyData {
		public final String translationKey;
		public final String id;

		public KeyData(String keyTranslationKey, String id) {
			this.translationKey = keyTranslationKey;
			this.id = id;
		}
	}

	/**
	 * Apply keybinds from a decoded map.
	 * Matches keybinds by name with existing client keybinds.
	 * Always overwrites existing bindings with the skin's bindings.
	 */
	public static int applyKeybinds(Map<String, KeyData> skinKeybindMap) {
		if (skinKeybindMap == null || skinKeybindMap.isEmpty()) return 0;

		Minecraft mc = Minecraft.getInstance();
		KeyMapping[] allKeys = mc.options.keyMappings;
		int applied = 0;

		for (KeyMapping km : allKeys) {
			String name = km.getName();

			if (skinKeybindMap.containsKey(name)) {
				KeyData keyData = skinKeybindMap.get(name);

				// Parse the key from its translation key
				InputConstants.Key newKey = InputConstants.getKey(keyData.translationKey);

				km.setKey(newKey);
				applied++;
			}
		}

		KeyMapping.resetMapping();
		System.out.println("[SkinKeybindManager] Applied " + applied + " keybinds from skin.");
		return applied;
	}

	public static File saveSkinToDisk(BufferedImage skin) throws IOException {
		File out = new File(FMLPaths.GAMEDIR.get().toFile(), "skin_encoded.png");
		ImageIO.write(skin, "PNG", out);
		return out;
	}

	public static BufferedImage loadSkinFromDisk() {
		try {
			File skinFile = new File(FMLPaths.GAMEDIR.get().toFile(), "skin_encoded.png");
			if (!skinFile.exists()) {
				System.err.println("[SkinKeybindManager] Skin file not found: " + skinFile.getAbsolutePath());
				return null;
			}
			BufferedImage skin = ImageIO.read(skinFile);
			return skin;
		} catch (IOException e) {
			System.err.println("[SkinKeybindManager] Failed to load skin: " + e.getMessage());
			return null;
		}
	}

	public static BufferedImage downloadSkin(String username) throws Exception {
		String uuid = user.getProfileId().toString();

		String accessToken = user.getAccessToken();
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
			System.err.println("[SkinKeybindManager] No 'textures' property found for user: " + username);
			return null;
		}

		String decodedJson = new String(java.util.Base64.getDecoder().decode(base64Value));
		JsonObject texturesJson = JsonParser.parseString(decodedJson).getAsJsonObject().getAsJsonObject("textures");
		if (!texturesJson.has("SKIN")) return null;

		String skinUrl = texturesJson.getAsJsonObject("SKIN").get("url").getAsString();

		Request request = new Request.Builder().url(skinUrl).build();
		try (Response resp = client.newCall(request).execute()) {
			if (!resp.isSuccessful()) {
				System.err.println("[SkinKeybindManager] Failed to download skin: " + resp.code());
				return null;
			}
			byte[] imageBytes = resp.body().bytes();
			return ImageIO.read(new ByteArrayInputStream(imageBytes));
		}
	}

	public static String getVariant(String username) throws IOException {
		String uuid = user.getProfileId().toString();
		String accessToken = user.getAccessToken();

		// Get the profile JSON from Mojang session server
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
			System.err.println("[SkinFetcher] No 'textures' property found for user: " + username);
			return "classic"; // default fallback
		}

		// Decode the Base64 value
		String decodedJson = new String(Base64.getDecoder().decode(base64Value));
		JsonObject texturesJson = JsonParser.parseString(decodedJson)
				.getAsJsonObject()
				.getAsJsonObject("textures");

		if (!texturesJson.has("SKIN")) return "classic";

		JsonObject skinObject = texturesJson.getAsJsonObject("SKIN");

		// Return the model variant if present, otherwise "classic"
		return skinObject.has("metadata") && skinObject.getAsJsonObject("metadata").has("model")
				? skinObject.getAsJsonObject("metadata").get("model").getAsString()
				: "classic";
		}

	public static boolean uploadSkin(File skinFile, boolean slim) throws IOException {
		Minecraft mc = Minecraft.getInstance();
		User user = mc.getUser();
		String accessToken = user.getAccessToken();
		String url = "https://api.minecraftservices.com/minecraft/profile/skins";

		MultipartBody.Builder bodyBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);
		bodyBuilder.addFormDataPart("file", skinFile.getName(),
				RequestBody.create(skinFile, MediaType.parse("image/png")));
		bodyBuilder.addFormDataPart("variant", slim ? "slim" : "classic");

		Request request = new Request.Builder()
				.url(url)
				.post(bodyBuilder.build())
				.addHeader("Authorization", "Bearer " + accessToken)
				.build();

		try (Response resp = client.newCall(request).execute()) {
			if (!resp.isSuccessful()) {
				System.err.println("[SkinKeybindManager] Failed to upload skin: " + resp.code() + " " + resp.message());
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
			return resp.body().string();
		}
	}
}