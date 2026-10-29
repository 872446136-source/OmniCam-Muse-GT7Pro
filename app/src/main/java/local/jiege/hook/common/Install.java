package local.jiege.hook.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The install registry: one line per feature, either "on" or why it was left off.
 *
 * <p>Replaces the old Lite / full split. There is one build; each feature is attempted only when
 * {@link Capabilities} says this device can run it, and whatever happens is recorded here so the
 * settings app can show it. A feature that fails to resolve a symbol turns itself off instead of
 * taking the camera with it.
 */
public final class Install {
    public static final String ON = "on";
    public static final String OFF = "off";
    public static final String FAILED = "failed";

    private static final Map<String, String[]> LINES = new LinkedHashMap<>();
    private static final List<String> ORDER = new ArrayList<>();

    private Install() {}

    /** Installs the feature if {@code available}; otherwise records {@code reason} and moves on. */
    public static void run(String feature, boolean available, String reason, Log.Installer body) {
        if (!available) {
            skip(feature, reason);
            return;
        }
        try {
            body.install();
            put(feature, ON, "");
        } catch (Throwable error) {
            put(feature, FAILED, String.valueOf(error));
            Log.e("Install", feature + " failed", error);
        }
    }

    public static void skip(String feature, String reason) {
        put(feature, OFF, reason);
        Log.i("Install", feature + " off: " + reason);
    }

    public static void on(String feature) {
        put(feature, ON, "");
        Log.i("Install", feature + " on");
    }

    private static synchronized void put(String feature, String status, String reason) {
        if (!LINES.containsKey(feature)) ORDER.add(feature);
        LINES.put(feature, new String[] {status, reason});
    }

    /** {@code feature -> "on" | "off" | "failed: reason"}; insertion order. */
    public static synchronized Map<String, String> status() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String feature : ORDER) {
            String[] line = LINES.get(feature);
            out.put(feature, ON.equals(line[0]) ? ON : line[0] + (line[1].isEmpty() ? "" : ": " + line[1]));
        }
        return out;
    }

    /** Compact summary for the log: counts plus every feature that is not on. */
    public static synchronized String summary() {
        int on = 0;
        StringBuilder off = new StringBuilder();
        for (String feature : ORDER) {
            String[] line = LINES.get(feature);
            if (ON.equals(line[0])) {
                on++;
            } else {
                off.append("\n  ").append(feature).append(" -> ").append(line[0]);
                if (!line[1].isEmpty()) off.append(" (").append(line[1]).append(')');
            }
        }
        return on + "/" + ORDER.size() + " features on" + (off.length() == 0 ? "" : ", off:" + off);
    }

    /**
     * Newline-separated {@code status\tfeature\treason}, for the settings app. The camera process
     * answers with its own registry, which is the only one that has run the installs.
     */
    public static synchronized String report() {
        StringBuilder out = new StringBuilder();
        for (String feature : ORDER) {
            String[] line = LINES.get(feature);
            out.append(line[0]).append('\t').append(feature).append('\t').append(line[1]).append('\n');
        }
        return out.toString();
    }
}
