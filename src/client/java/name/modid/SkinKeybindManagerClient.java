package name.modid;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.option.ControlsOptionsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.session.Session;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import okhttp3.*;
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
			{16, 36, 40, 48},  // body overlay 1
			{44, 32, 52, 36},  // right arm overlay 1
			{40, 36, 56, 48},  // right arm overlay 2
			{52, 48, 60, 52},  // left arm overlay 1
			{48, 52, 64, 64},  // left arm overlay 2
			{4, 32, 12, 36},    // right leg overlay 1
			{0, 36, 16, 48},    // right leg overlay 2
			{4, 48, 12, 52},    // left leg overlay 1
			{0, 52, 16, 54}    // left leg overlay 2
	};

	private static final int[][] OVERLAY_RECTS_SLIM = {
			{40, 0, 56, 8},   // head overlay 1
			{32, 8, 64, 16},   // head overlay 2
			{20, 32, 36, 36},  // body overlay 1
			{16, 36, 40, 48},  // body overlay 1
			{44, 32, 52, 36},  // right arm overlay 1
			{40, 36, 56, 48},  // right arm overlay 2
			{52, 48, 60, 52},  // left arm overlay 1
			{48, 52, 64, 64},  // left arm overlay 2
			{4, 32, 12, 36},    // right leg overlay 1
			{0, 36, 16, 48},    // right leg overlay 2
			{4, 48, 12, 52},    // left leg overlay 1
			{0, 52, 16, 54}    // left leg overlay 2
	};

	private static int[][] getOverlayRects(String variant) {
		return variant.equalsIgnoreCase("slim") ? OVERLAY_RECTS_SLIM : OVERLAY_RECTS_CLASSIC;
	}

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (ran) return; // run only once

			session = mc.getSession();
			ran = true;
		});

		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof ControlsOptionsScreen)) return;
			System.out.println("ControlOptionsScreen");

			ButtonWidget myButton = ButtonWidget.builder(
							Text.literal("Skin Keybind Manager"),
							b -> client.setScreen(new SkinKeybindManagerScreen(
									screen))
					)
					.dimensions(5, 5, 150, 20)
					.build();

			Screens.getButtons(screen).add(myButton);
		});
	}

	public static BufferedImage encodePlayerSkin(List<KeyBinding> keybindings, BufferedImage skin, String variant) throws Exception {
		if (skin == null) return null;

		// Step 1: Build keybind string
		StringBuilder sb = new StringBuilder();
		sb.append("#SKINKEYBINDS_START\n");
		if (keybindings != null && !keybindings.isEmpty()) {
			for (KeyBinding kb : keybindings) {
				String id = kb.getBoundKeyTranslationKey();
				String type = kb.getDefaultKey().getCategory().name();
				int code = kb.getDefaultKey().getCode();
				System.out.println("[SkinKeybindManager] Encoding keybind -> ID: " + id + ", Type: " + type + ", Code: " + code);
				sb.append(id).append(":").append(type).append(":").append(code).append(";");
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

		// New Step 2.5: Prepare data with a 4-byte length prefix
		// The length includes the XZ stream and its footer.
		ByteArrayOutputStream finalDataOut = new ByteArrayOutputStream();

		// Write the length as a 4-byte integer (Big-Endian)
		finalDataOut.write((compressed.length >> 24) & 0xFF);
		finalDataOut.write((compressed.length >> 16) & 0xFF);
		finalDataOut.write((compressed.length >> 8) & 0xFF);
		finalDataOut.write(compressed.length & 0xFF);

		// Write the actual XZ data
		finalDataOut.write(compressed);
		byte[] prefixedCompressed = finalDataOut.toByteArray();

// Step 3: Write bytes into overlay pixels (use prefixedCompressed)
		int[][] overlayRects = getOverlayRects(variant);
		int idx = 0;
		outer:
		for (int[] rect : overlayRects) {
			for (int y = rect[1]; y < rect[3]; y++) {
				for (int x = rect[0]; x < rect[2]; x++) {
					// Use prefixedCompressed here
					int r = idx < prefixedCompressed.length ? prefixedCompressed[idx++] & 0xFF : 0;
					int g = idx < prefixedCompressed.length ? prefixedCompressed[idx++] & 0xFF : 0;
					int b = idx < prefixedCompressed.length ? prefixedCompressed[idx++] & 0xFF : 0;
					int a = idx < prefixedCompressed.length ? prefixedCompressed[idx++] & 0xFF : 0;
					int rgba = (r << 24) | (g << 16) | (b << 8) | a;
					skin.setRGB(x, y, rgba);
					if (idx >= prefixedCompressed.length) break outer;
				}
			}
		}
		return skin;
	}



	/**
	 * Merge existing skin keybinds with current Fabric keybinds.
	 * Existing skin keybinds take precedence if present, otherwise use current in-game bindings.
	 */
	public static List<KeyBinding> getMergedKeybinds(List<KeyBinding> skinKeybinds) {
		Map<String, KeyBinding> mergedMap = new LinkedHashMap<>();

		// Step 1: Start with skin keybinds
		if (skinKeybinds != null) {
			for (KeyBinding kb : skinKeybinds) {
				String keyId = kb.getBoundKeyTranslationKey();
				mergedMap.put(keyId, kb);
				System.out.println("[SkinKeybindManager] Skin keybind -> ID: " + keyId +
						", Type: " + kb.getDefaultKey().getTranslationKey() +
						", Code: " + kb.getDefaultKey().getCode());
			}
		}

		// Step 2: Merge in current live Fabric keybinds
		MinecraftClient mc = MinecraftClient.getInstance();
		for (KeyBinding kb : mc.options.allKeys) {
			String keyId = kb.getBoundKeyTranslationKey();
			System.out.println("1" + kb.getDefaultKey());
			System.out.println("2" + kb.getBoundKeyLocalizedText());
			System.out.println("3" + kb.getCategory());
			System.out.println("4" + kb.getId());
			if (!mergedMap.containsKey(keyId)) {
				mergedMap.put(keyId, kb);
				System.out.println("[SkinKeybindManager] Live keybind -> ID: " + keyId +
						", Type: " + kb.getDefaultKey().getTranslationKey() +
						", Code: " + kb.getDefaultKey().getCode());
			}
		}

		List<KeyBinding> mergedList = new ArrayList<>(mergedMap.values());
		System.out.println("[SkinKeybindManager] Total merged keybinds: " + mergedList.size());
		return mergedList;
	}


	/**
	 * Decode keybinds from a skin image overlay.
	 * Returns a list of KeyBinding objects with ID, binding, and category.
	 */
	public static List<KeyBinding> decodePlayerSkin(BufferedImage skin, String variant) {
		List<KeyBinding> keybindings = new ArrayList<>();
		if (skin == null) return keybindings;

		int[][] overlayRects = getOverlayRects(variant);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		// Step 1: Extract overlay bytes
		for (int[] rect : overlayRects) {
			for (int y = rect[1]; y < rect[3]; y++) {
				for (int x = rect[0]; x < rect[2]; x++) {
					int rgba = skin.getRGB(x, y);
					baos.write((rgba >> 24) & 0xFF);
					baos.write((rgba >> 16) & 0xFF);
					baos.write((rgba >> 8) & 0xFF);
					baos.write(rgba & 0xFF);
				}
			}
		}

		byte[] bytes = baos.toByteArray();
		// Check if there's even space for the 4-byte prefix
		if (bytes.length < 4) return keybindings;

		// Step 2: Read the 4-byte length prefix
		int dataLength = (bytes[0] & 0xFF) << 24 |
				(bytes[1] & 0xFF) << 16 |
				(bytes[2] & 0xFF) << 8  |
				(bytes[3] & 0xFF);

		// Check if the stated length fits within the extracted bytes
		if (dataLength <= 0 || dataLength > bytes.length - 4) {
			System.err.println("[SkinKeybindManager] Invalid or corrupt length prefix.");
			return keybindings;
		}

		// The actual XZ data starts at index 4 and is dataLength long
		byte[] xzData = Arrays.copyOfRange(bytes, 4, 4 + dataLength);

		// Step 3: Decompress XZ safely
		try (XZInputStream xzIn = new XZInputStream(new ByteArrayInputStream(xzData))) {
			ByteArrayOutputStream decoded = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int n;
			while ((n = xzIn.read(buffer)) != -1) decoded.write(buffer, 0, n);

			String result = decoded.toString();
			int start = result.indexOf("#SKINKEYBINDS_START");
			int end = result.indexOf("#SKINKEYBINDS_END");
			if (start == -1 || end == -1 || start >= end) return keybindings;

			String data = result.substring(start + "#SKINKEYBINDS_START".length(), end).trim();
			if (!data.isEmpty()) {
				String[] entries = data.split(";");
				for (String entry : entries) {
					if (entry.isEmpty()) continue;
					String[] parts = entry.split(":");
					if (parts.length >= 3) {
						String id = parts[0];
						InputUtil.Type type = InputUtil.Type.valueOf(parts[1]);
						int code = Integer.parseInt(parts[2]);
						InputUtil.Key boundKey = type.createFromCode(code);
						keybindings.add(new KeyBinding(id, type, code, KeyBinding.Category.MISC));
					}
				}
			}
		} catch (Exception e) {
			System.out.println("[SkinKeybindManager] No keybinds found or decompression failed: " + e);
			// Return empty list if nothing encoded yet
		}

		return keybindings;
	}


	public static int applyKeybinds(List<KeyBinding> fromSkinBindings) {
		if (fromSkinBindings == null) fromSkinBindings = List.of();

		MinecraftClient mc = MinecraftClient.getInstance();
		KeyBinding[] allKeys = mc.options.allKeys;
		int applied = 0;

		for (KeyBinding kb : allKeys) {
			// Find matching key in fromSkinBindings
			KeyBinding match = fromSkinBindings.stream()
					.filter(f -> f.getBoundKeyTranslationKey().equals(kb.getBoundKeyTranslationKey()))
					.findFirst()
					.orElse(null);

			if (match != null) {
				kb.setBoundKey(match.getDefaultKey());
				applied++;
			} else {
				// Unbind the key
				kb.setBoundKey(InputUtil.UNKNOWN_KEY);
			}
		}

		KeyBinding.updateKeysByCode();
		System.out.println("[SkinKeybindManager] Applied " + applied + " keybinds. Others unbound.");
		return applied;
	}





	public static void saveSkinToDisk(BufferedImage skin) throws IOException {
		File out = new File(FabricLoader.getInstance().getGameDir() + "/" + "skin_encoded.png");
		ImageIO.write(skin, "PNG", out);
		System.out.println("[SkinKeybindManager] Encoded skin saved: " + out.getAbsolutePath());
	}


	public static BufferedImage loadSkinFromDisk() {
		try {
			// Build the file path inside the game directory
			File skinFile = new File(FabricLoader.getInstance().getGameDir() + "/" + "skin_encoded.png");

			if (!skinFile.exists()) {
				System.err.println("[SkinKeybindManager] Skin file not found: " + skinFile.getAbsolutePath());
				return null;
			}

			// Read PNG into BufferedImage
			BufferedImage skin = ImageIO.read(skinFile);
			System.out.println("[SkinKeybindManager] Loaded skin from: " + skinFile.getAbsolutePath());
			return skin;

		} catch (IOException e) {
			System.err.println("[SkinKeybindManager] Failed to load skin: " + e.getMessage());
			return null;
		}
	}

	public static BufferedImage downloadSkin(String username) throws Exception {

		// 1. Fetch UUID from Mojang API
		String uuid = getProfileUUID(username); // your existing method
		if (uuid == null) {
			System.err.println("[SkinKeybindManager] Could not fetch UUID for username: " + username);
			uuid = "fcab5be823974c298ff904911c72e294";
			//return null;
		}

		// 2. Fetch session server profile to get skin URL
		String accessToken = session.getAccessToken(); // your session object
		String response = getSessionServerProfile(uuid, accessToken); // your existing method
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

		// 3. Decode base64 JSON to get skin URL
		String decodedJson = new String(java.util.Base64.getDecoder().decode(base64Value));
		JsonObject texturesJson = JsonParser.parseString(decodedJson).getAsJsonObject().getAsJsonObject("textures");
		if (!texturesJson.has("SKIN")) return null;

		String skinUrl = texturesJson.getAsJsonObject("SKIN").get("url").getAsString();

		// 4. Download skin image
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

	public static String getVariant(BufferedImage skin) {
		if (skin == null) return "classic";

		// Coordinates for the left arm overlay pixel in 64x64 skin
		int x = 54;
		int y = 20;

		// Make sure the skin is at least 64x64
		if (skin.getWidth() < 64 || skin.getHeight() < 64) return "classic";

		int rgba = skin.getRGB(x, y);
		int alpha = (rgba >> 24) & 0xFF;

		return (alpha != 0) ? "slim" : "classic";
	}

	/**
	 * Uploads a skin file to Mojang using the current player's session.
	 *
	 * @param skinFile PNG file with your overlay
	 * @param slim     true for slim (Alex) model, false for classic
	 * @return true if upload succeeded
	 */
	public boolean uploadSkin(File skinFile, boolean slim) throws IOException {
		MinecraftClient mc = MinecraftClient.getInstance();
		Session session = mc.getSession();
		String username = session.getUsername();
		String accessToken = session.getAccessToken();
		String uuid = getProfileUUID(username);

		String url = "https://api.minecraft.net/session/minecraft/profile/" + uuid + "/skin";

		MultipartBody.Builder bodyBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);
		bodyBuilder.addFormDataPart("file", skinFile.getName(),
				RequestBody.create(skinFile, MediaType.parse("image/png")));
		if (slim) {
			bodyBuilder.addFormDataPart("model", "slim");
		}

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
		System.out.println("URL: " + url);
		Request request = new Request.Builder().url(url).build();
		try (Response resp = client.newCall(request).execute()) {
			if (!resp.isSuccessful()) return null;
			String body = resp.body().string();
			System.out.println("BODY: " + body);
			JsonObject json = JsonParser.parseString(body).getAsJsonObject();
			System.out.println("JSON: " + json);
			System.out.println("ID: " + json.get("id").getAsString());
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
