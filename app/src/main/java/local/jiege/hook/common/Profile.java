package local.jiege.hook.common;

/**
 * Which feature set this build installs.
 *
 * This is the test/full-port branch: LITE is false, so the whole upstream feature set is installed —
 * every camera and gallery hook, the native library (assets/native_init is present), and the
 * bind-mounted /odm payload plus the X9 Ultra libraries under module/system/.
 *
 * Nothing here is adapted to the GT7 Pro: the payload is the Find X9 Ultra original and the native
 * half still carries the 25021 decision-family offsets. The point of the branch is to find out on a
 * real device which parts survive and which ones take the camera down, not to ship anything.
 *
 * A build for a device the module has no payload for (the main branch) sets this to true: no mounts,
 * no native library, app-only hooks.
 *
 * Kill switch, no need to uninstall the module:
 *   adb shell su -c "touch /data/adb/omnicam_fullport_off"  then reboot
 */
public final class Profile {
    /** Full upstream feature set on top of the X9 Ultra payload. */
    public static final boolean LITE = false;

    private Profile() {}
}
