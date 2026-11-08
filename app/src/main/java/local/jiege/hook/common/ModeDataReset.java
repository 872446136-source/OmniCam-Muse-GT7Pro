package local.jiege.hook.common;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Deletes the camera's mode database once per module version.
 *
 * <p>The camera does not build its mode bar from the config on every start: it seeds
 * {@code databases/mode_data.db} once (ModeDataSQLiteOpenHelper) and then only queries it. Each row is
 * written under a support flag — {@code gr} needs {@code com.oplus.gr.mode.support}, {@code retroCamera}
 * needs {@code com.oplus.feature.retro.camera.support} — so a row is missing for good on a phone whose
 * config never had those flags on, no matter what the config says later. That is why the profile alone
 * made {@code hasMode()} pass while the camera still showed no GR entry.
 *
 * <p>Dropping the file makes the helper run its "db is not exist" path, which re-inserts every mode the
 * (now overridden) flags allow. The cost is the user's own mode order and headline/panel split, which
 * is why this runs once per version instead of on every start.
 *
 * <p>The camera process does the delete itself: a root shell writing into the app's private directory
 * cannot keep the app's SELinux categories, and before the first unlock it cannot write there at all.
 */
public final class ModeDataReset {
    private static final String MARKER = "mode_db_reset";
    private static final String DATABASES = "databases";
    /** Bump when a new mode has to be re-seeded. */
    private static final String VERSION = "499";

    /**
     * The marker carries the camera build as well as this module's version, because the table is
     * as much the camera's as ours. The camera reads it back with its own columns and types:
     * {@code DataBaseOperator.<init>} walks every row through {@code (String) value}, so a camera
     * update that changes one column from text to integer turns the next start into a
     * {@code ClassCastException: Integer cannot be cast to String} inside {@code Camera.onCreate},
     * before any of this module's hooks run. 7.013.51 is exactly that: the table seeded under
     * 7.013.40 is still on the phone, and the camera crashes on it every time it starts.
     *
     * <p>So the drop is keyed on both. A module update re-seeds once because a new mode was added;
     * a camera update re-seeds once because its schema may have moved. Both cost the same thing,
     * the user's own mode order, which is why it is still once per change and not every start.
     */
    private static final String MARKER_SEPARATOR = ":";
    private static final String[] FILES = {
        "mode_data.db", "mode_data.db-journal", "mode_data.db-wal", "mode_data.db-shm",
    };

    private ModeDataReset() {}

    /**
     * Drops the mode database when this module version has not done so yet.
     *
     * @param dataDir the camera's data directory, from the package info of the hooked process
     * @param cameraApk the camera APK this process was loaded from; its identity changes on every
     *     update, which is what a camera update looks like from inside the camera process
     * @return whether the database was dropped on this start
     */
    public static boolean ensure(String dataDir, String cameraApk) {
        if (dataDir == null) return false;
        File dir = new File(Capabilities.PROFILE_DIR);
        File marker = new File(dir, MARKER);
        String wanted = VERSION + MARKER_SEPARATOR + identity(cameraApk);
        if (marker.isFile() && wanted.equals(read(marker))) return false;

        File databases = new File(dataDir, DATABASES);
        boolean dropped = false;
        boolean stuck = false;
        for (String name : FILES) {
            File file = new File(databases, name);
            if (!file.isFile()) continue;
            if (file.delete()) dropped = true;
            else {
                stuck = true;
                Log.e("ModeData", "cannot delete " + file);
            }
        }
        if (stuck) {
            // Something still has the file open. Writing the marker now would make this the last
            // attempt there ever is, and the camera would keep crashing on the same rows; leaving
            // it unwritten means the next start tries again.
            Log.e("ModeData", "the mode database is still on disk, the next start will retry");
            return dropped;
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.e("ModeData", "cannot create " + dir);
            return dropped;
        }
        try (OutputStream out = new FileOutputStream(marker)) {
            out.write(wanted.getBytes("UTF-8"));
        } catch (Throwable error) {
            Log.e("ModeData", "cannot write " + marker, error);
        }
        Log.i("ModeData", (dropped ? "mode database dropped, the camera will re-seed it"
            : "no mode database to drop") + " (marker " + wanted + ")");
        return dropped;
    }

    /**
     * A stable name for the camera package as it is installed now. The version name would be the
     * obvious choice, but {@code ApplicationInfo} does not carry one and the application object
     * does not exist yet at this point; the APK does. Its length and timestamp change whenever
     * the package is replaced, which is exactly the event this has to notice, and they stay put
     * across ordinary starts.
     */
    private static String identity(String cameraApk) {
        if (cameraApk == null || cameraApk.length() == 0) return "?";
        try {
            File apk = new File(cameraApk);
            return apk.length() + "-" + apk.lastModified();
        } catch (Throwable error) {
            return "?";
        }
    }

    private static String read(File file) {
        try (FileInputStream in = new FileInputStream(file)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[64];
            int read;
            while ((read = in.read(buffer)) > 0) out.write(buffer, 0, read);
            return new String(out.toByteArray(), "UTF-8").trim();
        } catch (Throwable error) {
            return null;
        }
    }
}
