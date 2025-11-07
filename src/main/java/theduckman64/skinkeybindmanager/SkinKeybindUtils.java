package theduckman64.skinkeybindmanager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZInputStream;
import org.tukaani.xz.XZOutputStream;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Contains all Minecraft-version-agnostic logic for handling skin keybinds.
 * This class does not depend on any Minecraft, NeoForge, or Fabric APIs.
 */
public class SkinKeybindUtils {

    public static final OkHttpClient client = new OkHttpClient();

    private static final int[][] OVERLAY_RECTS_CLASSIC = {
            {0, 0, 8, 8}, {24, 0, 40, 8}, {56, 0, 64, 8},
            {0, 16, 4, 20}, {12, 16, 20, 20}, {36, 16, 44, 20},
            {52, 16, 56, 20}, {0, 32, 4, 36}, {12, 32, 20, 36},
            {36, 32, 44, 36}, {52, 32, 56, 36}, {0, 48, 4, 52},
            {12, 48, 20, 52}, {28, 48, 36, 52}, {44, 48, 52, 52},
            {60, 48, 64, 52}, {56, 16, 64, 48}
    };

    private static final int[][] OVERLAY_RECTS_SLIM = {
            {0, 0, 8, 8}, {24, 0, 40, 8}, {56, 0, 64, 8},
            {0, 16, 4, 20}, {12, 16, 20, 20}, {36, 16, 44, 20},
            {50, 16, 54, 20}, {0, 32, 4, 36}, {12, 32, 20, 36},
            {36, 32, 44, 36}, {50, 32, 54, 36}, {0, 48, 4, 52},
            {12, 48, 20, 52}, {28, 48, 36, 52}, {42, 48, 52, 52},
            {46, 52, 48, 64}, {58, 48, 64, 52}, {54, 16, 64, 48},
            {62, 52, 64, 64}
    };

    /**
         * Simple data class to hold key translation
         */
        public record KeyData(String translationKey, String id) {
    }

    private static int[][] getOverlayRects(String variant) {
        return variant.equalsIgnoreCase("slim") ? OVERLAY_RECTS_SLIM : OVERLAY_RECTS_CLASSIC;
    }

    public static BufferedImage encodePlayerSkin(Map<String, KeyData> keybindMap, BufferedImage skin, String variant) throws IOException {
        if (skin == null) return null;

        // Step 1: Build keybind string
        StringBuilder sb = new StringBuilder();
        sb.append("#SKINKEYBINDS_START\n");
        if (keybindMap != null && !keybindMap.isEmpty()) {
            for (Map.Entry<String, KeyData> entry : keybindMap.entrySet()) {
                String id = entry.getKey();
                KeyData keyData = entry.getValue();
                sb.append(id).append(":").append(keyData.translationKey).append(";");
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
                    if (idx + 3 < prefixedCompressed.length) { // Check for 4 bytes (a,r,g,b)
                        int a = prefixedCompressed[idx++] & 0xFF;
                        int r = prefixedCompressed[idx++] & 0xFF;
                        int g = prefixedCompressed[idx++] & 0xFF;
                        int b = prefixedCompressed[idx++] & 0xFF;
                        int argb = (a << 24) | (r << 16) | (g << 8) | b;
                        skin.setRGB(x, y, argb);
                    } else {
                        skin.setRGB(x, y, 0x00000000); // Clear remaining pixels
                    }
                }
            }
        }

        return skin;
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
        } catch (Exception ignored) {
            // Decompression failed, return empty map
        }
        return keybindMap;
    }

    public static @NotNull File saveSkinToDisk(BufferedImage skin, File outFile) throws IOException {
        ImageIO.write(skin, "PNG", outFile);
        return outFile;
    }

    public static BufferedImage loadSkinFromDisk(File skinFile) throws IOException {
        if (!skinFile.exists()) {
            throw new FileNotFoundException("Skin file not found: " + skinFile.getAbsolutePath());
        }
        return ImageIO.read(skinFile);
    }

    public static BufferedImage downloadSkin(String uuid, String accessToken) throws IOException {
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
            throw new IOException("No 'textures' property found for user.");
        }

        String decodedJson = new String(Base64.getDecoder().decode(base64Value));
        JsonObject texturesJson = JsonParser.parseString(decodedJson).getAsJsonObject().getAsJsonObject("textures");
        if (!texturesJson.has("SKIN")) {
            throw new IOException("Profile has no skin texture.");
        }

        String skinUrl = texturesJson.getAsJsonObject("SKIN").get("url").getAsString();

        Request request = new Request.Builder().url(skinUrl).build();
        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IOException("Failed to download skin: " + resp.code() + " " + resp.message());
            }
            byte[] imageBytes = resp.body().bytes();
            return ImageIO.read(new ByteArrayInputStream(imageBytes));
        }
    }

    public static String getVariant(String uuid, String accessToken) throws IOException {
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
            return "classic"; // default fallback
        }

        String decodedJson = new String(Base64.getDecoder().decode(base64Value));
        JsonObject texturesJson = JsonParser.parseString(decodedJson)
                .getAsJsonObject()
                .getAsJsonObject("textures");

        if (!texturesJson.has("SKIN")) return "classic";

        JsonObject skinObject = texturesJson.getAsJsonObject("SKIN");

        return skinObject.has("metadata") && skinObject.getAsJsonObject("metadata").has("model")
                ? skinObject.getAsJsonObject("metadata").get("model").getAsString()
                : "classic";
    }

    public static boolean uploadSkin(File skinFile, String variant, String accessToken) throws IOException {
        String url = "https://api.minecraftservices.com/minecraft/profile/skins";

        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder().setType(MultipartBody.FORM);
        bodyBuilder.addFormDataPart("file", skinFile.getName(),
                RequestBody.create(skinFile, MediaType.parse("image/png")));
        bodyBuilder.addFormDataPart("variant", variant);

        Request request = new Request.Builder()
                .url(url)
                .post(bodyBuilder.build())
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Failed to upload skin: " + resp.code() + " " + resp.message());
            }
            return true;
        }
    }

    private static String getSessionServerProfile(String uuid, String accessToken) throws IOException {
        Request request = new Request.Builder()
                .url("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        try (Response resp = client.newCall(request).execute()) {
            if (!resp.isSuccessful() || resp.body() == null)
                throw new IOException("[SkinKeybindManager] Session server HTTP error: " + resp);
            return resp.body().string();
        }
    }
}