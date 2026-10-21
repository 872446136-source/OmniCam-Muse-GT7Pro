// Device config profile, native half.
//
// Some features are shipped in the firmware but never offered, because the mode list does not name
// them: on 真我 GT7 Pro the tilt-shift and XPAN operation codes, use cases, decision-tree nodes and
// crop parameters are all present, while mode_type_list has neither mode and one support flag is 0.
// The profile under module/profiles/<model>/config is that device's own config with those entries
// added, staged into the app's private files by service.sh.
//
// While the marker exists, reads of the six ODM config paths are served from the staged copies. Only
// this process is redirected: nothing on /odm changes, no system file is replaced, and deleting the
// staged directory restores stock behaviour after a camera restart. The configs are read when the
// camera algorithms initialise, so a change applies on the next camera process.
//
// Runs inside the path hooks: no libc stat/access/open here (the hooks would call back in), only raw
// syscalls.
#include <cstddef>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <sys/syscall.h>
#include <unistd.h>

#include "native_hooks.h"

namespace {

constexpr char kOdmConfig[] = "/odm/etc/camera/config/";
constexpr char kRoot[] = "/data/user/0/com.oplus.camera/files/jiege/profile/";
constexpr char kMarker[] = "/data/user/0/com.oplus.camera/files/jiege/profile/enabled";

// The five files a profile may replace, plus the feature config. A profile only ships the ones it
// actually patches; the rest fall through to stock.
constexpr const char *kNames[] = {
    "camera_unit_config",
    "camera_unit_feature_config.protobuf",
    "oplus_camera_config",
    "oplus_camera_algo_switch_config",
    "oplus_camera_aps_config",
    "oplus_camera_preview_decision_config.json",
};

bool readable(const char *path) {
    return syscall(SYS_faccessat, AT_FDCWD, path, R_OK, 0) == 0;
}

}  // namespace

const char *profileRemapPath(const char *path, char *buf, size_t n) {
    constexpr size_t prefix = sizeof(kOdmConfig) - 1;
    if (std::strncmp(path, kOdmConfig, prefix) != 0) return nullptr;
    const char *name = path + prefix;
    bool known = false;
    for (const char *candidate : kNames) {
        if (std::strcmp(name, candidate) == 0) {
            known = true;
            break;
        }
    }
    if (!known || !readable(kMarker)) return nullptr;
    std::snprintf(buf, n, "%s%s", kRoot, name);
    return readable(buf) ? buf : nullptr;
}
