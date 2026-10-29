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
    private static final String VERSION = "488";
    private static final String[] FILES = {
        "mode_data.db", "mode_data.db-journal", "mode_data.db-wal", "mode_data.db-shm",
    };

    private ModeDataReset() {}

    /**
     * Drops the mode database when this module version has not done so yet.
     *
     * @param dataDir the camera's data directory, from the package info of the hooked process
     * @return whether the database was dropped on this start
     */
    public static boolean ensure(String dataDir) {
        if (dataDir == null) return false;
        File dir = new File(Capabilities.PROFILE_DIR);
        File marker = new File(dir, MARKER);
        if (marker.isFile() && VERSION.equals(read(marker))) return false;

        File databases = new File(dataDir, DATABASES);
        boolean dropped = false;
        for (String name : FILES) {
            File file = new File(databases, name);
            if (!file.isFile()) continue;
            if (file.delete()) dropped = true;
            else Log.e("ModeData", "cannot delete " + file);
        }
        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.e("ModeData", "cannot create " + dir);
            return dropped;
        }
        try (OutputStream out = new FileOutputStream(marker)) {
            out.write(VERSION.getBytes("UTF-8"));
        } catch (Throwable error) {
            Log.e("ModeData", "cannot write " + marker, error);
        }
        Log.i("ModeData", dropped ? "mode database dropped, the camera will re-seed it" : "no mode database to drop");
        return dropped;
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
