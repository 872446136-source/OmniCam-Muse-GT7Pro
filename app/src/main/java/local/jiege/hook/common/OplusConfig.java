package local.jiege.hook.common;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * The XPAN half of {@code oplus_camera_config}, staged for the camera process.
 *
 * <p><b>Why the switch flags alone are not enough.</b> {@code ModeGates} answers the config keys
 * through {@code CameraConfig}, which is the app's own reader and runs in this process. XPAN's frame
 * is not decided there: the capture size and the four crop regions are vendor tags, read by
 * {@code CameraUnit} / the IPU out of {@code /odm/etc/camera/config/oplus_camera_config} while the
 * camera unit initialises. A hook cannot reach that copy, and 501 proved the difference between the
 * two readers — the app's reader answered {@code 7872x2912} for both the {@code Size} and the string
 * getter, while the shot still came out 3072x4096, the ordinary 4:3 frame.
 *
 * <p><b>What this device ships.</b> The stock table declares XPAN's canvas as the picture size:
 * {@code com.oplus.xpan.main.picturesize = 8000x3636} — 64+7872+64 by 360+2912+360, i.e. the region
 * plus its margins. The four crop regions are already correct. With the canvas handed out as the
 * output size there is nothing to cut, and the mode falls back to the ordinary photo pipeline. The
 * 4.4.4 port for PMA110, the Aura port for PLK110 and the earlier GT7 Pro port all answer
 * {@code 7872x2912} for the three size keys instead, and add the two keys this firmware does not
 * list at all ({@code xpan.all.camera.support}, {@code xpan.mode.version}).
 *
 * <p><b>How it is staged.</b> The stock file is an AES-128-ECB envelope whose key is derived from
 * the device's own {@code libOplusSecurity.so} (32 bytes at 0x7AB XOR 0x3F, every second byte kept);
 * {@code tools/oplus_config.py} is the same algorithm and decrypt-then-repack reproduces the stock
 * file byte for byte. So nothing is shipped: the live file is decrypted here, six values are
 * written over it, and the result is wrapped in the envelope the live file came in. A config from a
 * different build, a different key, or a file that no longer has the shape this expects all end the
 * same way — the file is not staged and the stock config is served.
 *
 * <p>Only the XPAN keys are touched. Everything else keeps the value the device has, so a system
 * update that moves a size or a table still carries through, which is the same rule
 * {@link ConfigProfile} follows for {@code camera_unit_config}.
 *
 * <p>Escape hatch: {@code touch /data/adb/omnicam_xpanconf_off} withdraws the staged copy.
 */
public final class OplusConfig {
    private static final String TAG = "OplusConfig";

    /** The name as the camera asks for it; the native redirect serves this from the profile dir. */
    private static final String NAME = "oplus_camera_config";

    /** The device's own copy, read past the redirect this module installs. */
    private static final String LIVE = "/proc/self/root/odm/etc/camera/config/" + NAME;

    /** The library the config key is derived from; the first one that reads wins. */
    private static final String[] SECURITY = {
        "/proc/self/root/odm/lib64/libOplusSecurity.so",
        "/odm/lib64/libOplusSecurity.so",
        "/proc/self/root/system/lib64/libOplusSecurity.so",
    };

    private static final String OFF = "/data/adb/omnicam_xpanconf_off";

    /** Key material: 32 bytes at this offset, XOR 0x3F, every second byte — 16 bytes of AES-128. */
    private static final int KEY_OFFSET = 0x7AB;
    private static final int KEY_XOR = 0x3F;
    private static final int KEY_BYTES = 32;

    /**
     * The values written over the stock table. Sizes are the crop region, not the canvas: XPAN's
     * output is 7872x2912 and the 8000x3636 frame it is cut from is internal.
     */
    private static final String[][] SET = {
        {"com.oplus.xpan.main.picturesize", "7872x2912"},
        {"com.oplus.xpan.wide.picturesize", "7872x2912"},
        {"com.oplus.xpan.tele.picturesize", "7872x2912"},
        // The mode flag ships as 0 on this device. ModeGates answers it for the app; the camera
        // unit reads the tag, and a mode the unit does not know is a mode without a stream.
        {"com.oplus.feature.xpan.mode.support", "1"},
    };

    /** Keys this firmware does not list at all: {key, Type, Count, Value}. */
    private static final String[][] ADD = {
        {"com.oplus.xpan.all.camera.support", "Byte", "1", "1"},
        {"com.oplus.xpan.mode.version", "Int32", "1", "3"},
    };

    private OplusConfig() {}

    /**
     * Writes the patched config into {@code dir}; returns true when the file is in place.
     *
     * <p>False means "serve the stock file": either the escape hatch is set, or the live config
     * could not be read, decrypted or edited. A staged copy from an earlier run is removed in that
     * case, so a device that used to work and no longer does falls back instead of keeping a stale
     * table.
     */
    public static boolean stage(File dir) {
        File target = new File(dir, NAME);
        if (new File(OFF).exists()) {
            if (target.isFile() && target.delete()) Log.i(TAG, "staged config withdrawn (" + OFF + ")");
            return false;
        }
        byte[] live = read(new File(LIVE));
        if (live == null || live.length < 64) {
            Log.i(TAG, "live config unreadable, the stock table is served");
            return false;
        }
        byte[] staged;
        try {
            staged = patch(live);
        } catch (Throwable error) {
            Log.e(TAG, "config patch failed, the stock table is served", error);
            staged = null;
        }
        if (staged == null) {
            if (target.isFile() && target.delete()) Log.i(TAG, "staged config removed, stock served");
            return false;
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.e(TAG, "cannot create " + dir);
            return false;
        }
        if (target.length() == staged.length && sameStart(target, staged)) {
            Log.i(TAG, "staged config already current");
            return true;
        }
        File temporary = new File(dir, NAME + ".tmp");
        try (OutputStream out = new FileOutputStream(temporary)) {
            out.write(staged);
        } catch (Throwable error) {
            Log.e(TAG, "cannot write " + temporary, error);
            temporary.delete();
            return false;
        }
        if (!temporary.renameTo(target)) {
            Log.e(TAG, "cannot publish " + target);
            temporary.delete();
            return false;
        }
        Log.i(TAG, "staged config written: " + staged.length + " bytes");
        return true;
    }

    /** Plaintext out of the live file, patched, and wrapped again the way it came in. */
    private static byte[] patch(byte[] live) throws Exception {
        boolean envelope = live.length > 12 && live[0] == 0x01 && live[1] == 0x01
            && live[live.length - 1] == (byte) 0xFF;
        byte[] plain = envelope ? decrypt(live) : live;
        if (plain == null) {
            Log.i(TAG, "config is an envelope this key does not open, the stock table is served");
            return null;
        }
        String text = new String(plain, "UTF-8");
        int changed = 0;
        for (String[] entry : SET) {
            String patched = setValue(text, entry[0], entry[1]);
            if (patched != null) {
                text = patched;
                changed++;
            }
        }
        for (String[] entry : ADD) {
            if (text.contains("\"" + entry[0] + "\"")) continue;
            String patched = addEntry(text, entry[0], entry[1], entry[2], entry[3]);
            if (patched != null) {
                text = patched;
                changed++;
            }
        }
        if (changed == 0) {
            Log.i(TAG, "config has no XPAN keys to write, the stock table is served");
            return null;
        }
        byte[] patched = text.getBytes("UTF-8");
        byte[] packed = envelope ? encrypt(patched, live) : patched;
        // The camera has to read back exactly what was written, or the mode reads half a table.
        byte[] check = envelope ? decrypt(packed) : packed;
        if (check == null || !java.util.Arrays.equals(check, patched)) {
            Log.e(TAG, "round trip mismatch, the stock table is served", null);
            return null;
        }
        Log.i(TAG, "XPAN config patched: " + changed + " values, " + packed.length + " bytes");
        return packed;
    }

    /** Rewrites the {@code Value} of the entry that carries {@code key}; null when it is absent. */
    private static String setValue(String json, String key, String value) {
        int at = json.indexOf("\"" + key + "\"");
        if (at < 0) return null;
        int atValue = json.indexOf("\"Value\"", at);
        // A "Value" further down belongs to the next entry: every entry here is four short lines.
        if (atValue < 0 || atValue - at > 400) return null;
        int colon = json.indexOf(':', atValue);
        if (colon < 0) return null;
        int open = json.indexOf('"', colon);
        if (open < 0) return null;
        int close = json.indexOf('"', open + 1);
        if (close < 0) return null;
        if (json.substring(open + 1, close).equals(value)) return null;
        return json.substring(0, open + 1) + value + json.substring(close);
    }

    /** Appends an entry the table does not have; null when the array cannot be found. */
    private static String addEntry(String json, String key, String type, String count, String value) {
        int end = json.lastIndexOf(']');
        if (end < 0) return null;
        int cut = end;
        while (cut > 0 && Character.isWhitespace(json.charAt(cut - 1))) cut--;
        String head = json.substring(0, cut);
        String tail = json.substring(cut);
        StringBuilder entry = new StringBuilder();
        if (!head.endsWith(",")) entry.append(',');
        entry.append("\n\n{\n  \"VendorTag\": \"").append(key)
            .append("\",\n  \"Type\": \"").append(type)
            .append("\",\n  \"Count\": \"").append(count)
            .append("\",\n  \"Value\": \"").append(value)
            .append("\"\n}\n");
        return head + entry + tail;
    }

    private static byte[] decrypt(byte[] envelope) throws Exception {
        int length = envelope.length - 8;
        if (length <= 0 || length % 16 != 0) return null;
        byte[] key = key();
        if (key == null) return null;
        byte[] plain = aes(key, java.util.Arrays.copyOfRange(envelope, 4, envelope.length - 4), false);
        if (plain == null || plain.length == 0) return null;
        int pad = plain[plain.length - 1] & 0xFF;
        if (pad < 1 || pad > 16 || pad > plain.length) return null;
        return java.util.Arrays.copyOf(plain, plain.length - pad);
    }

    private static byte[] encrypt(byte[] plain, byte[] frame) throws Exception {
        byte[] key = key();
        if (key == null) return null;
        int pad = 16 - plain.length % 16;
        byte[] padded = java.util.Arrays.copyOf(plain, plain.length + pad);
        for (int i = plain.length; i < padded.length; i++) padded[i] = (byte) pad;
        byte[] cipher = aes(key, padded, true);
        if (cipher == null) return null;
        ByteArrayOutputStream out = new ByteArrayOutputStream(cipher.length + 8);
        out.write(frame, 0, 4);
        out.write(cipher, 0, cipher.length);
        out.write(frame, frame.length - 4, 4);
        return out.toByteArray();
    }

    /** AES-128-ECB without padding — the same call {@code tools/oplus_config.py} makes. */
    private static byte[] aes(byte[] key, byte[] data, boolean encrypt) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/NoPadding");
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(data);
        } catch (Throwable error) {
            Log.e(TAG, "AES failed", error);
            return null;
        }
    }

    /** The 16 bytes derived from this device's own security library. */
    private static byte[] key() {
        for (String path : SECURITY) {
            byte[] library = read(new File(path));
            if (library == null || library.length < KEY_OFFSET + KEY_BYTES) continue;
            byte[] key = new byte[KEY_BYTES / 2];
            for (int i = 0; i < KEY_BYTES; i += 2) {
                key[i / 2] = (byte) (library[KEY_OFFSET + i] ^ KEY_XOR);
            }
            return key;
        }
        Log.i(TAG, "libOplusSecurity.so not readable, the stock table is served");
        return null;
    }

    private static byte[] read(File file) {
        if (!file.isFile() || file.length() <= 0) return null;
        try (InputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) file.length());
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return out.toByteArray();
        } catch (Throwable error) {
            return null;
        }
    }

    private static boolean sameStart(File file, byte[] expected) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[expected.length];
            int read = 0;
            while (read < buffer.length) {
                int step = in.read(buffer, read, buffer.length - read);
                if (step <= 0) return false;
                read += step;
            }
            return java.util.Arrays.equals(buffer, expected);
        } catch (Throwable error) {
            return false;
        }
    }
}
