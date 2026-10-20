package local.jiege.hook.common;

/**
 * Which feature set this build installs.
 *
 * LITE is for a device the module carries no /odm payload and no patched native libraries for.
 * Nothing is bind-mounted, libjiege_native is not loaded, and only the hooks that live entirely
 * inside the camera / gallery app are installed — the ones that read the app's own classes and
 * write nothing outside the app's data.
 *
 * A build whose payload matches the device (payload regenerated from that device's own /odm, native
 * libraries re-patched from its own libraries) sets this to false and gets the full feature set.
 * Keep it in sync with the "lite" marker in module/ — the shell scripts skip every mount when the
 * marker is there, and the camera would then run without the files these hooks expect.
 *
 * assets/native_init is removed in this build: LSPosed loads the libraries it lists into every
 * scoped process on its own, before any Java hook runs, and the native half redirects ODM paths and
 * patches the tuning libraries — all of which expect the payload. Restore that file together with
 * LITE = false.
 */
public final class Profile {
    /** No /odm mounts, no native library: app-only hooks. */
    public static final boolean LITE = true;

    private Profile() {}
}
