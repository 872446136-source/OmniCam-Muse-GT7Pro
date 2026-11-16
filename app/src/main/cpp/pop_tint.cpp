// GT7 Pro: the POP preview has a green/magenta correction absent from its stock CWCM tables.
// Read the value from each capture's settings and apply it before JPEG encoding, after the
// existing filter. No global slider state is consulted by an in-flight photo.
#include "native_hooks.h"
#include <android/hardware_buffer.h>
#include <android/log.h>
#include <sys/system_properties.h>
#include <algorithm>
#include <cmath>
#include <cstdlib>
#include <cstring>
#include <dlfcn.h>

namespace {
constexpr char TAG[] = "PopTintCapture";
thread_local int captureTint = 0;

std::string setting(void *frame, const char *key) {
    // Verified against RMX5010 17.0.0.112 libAlgoInterface, doFilterProcess +0x68:
    // frame +0x8110 holds the settings object; vtable +0x38 returns std::string by value.
    void *settings = *reinterpret_cast<void **>(static_cast<char *>(frame) + 0x8110);
    if (!settings) return {};
    auto table = *reinterpret_cast<void ***>(settings);
    using Get = std::string (*)(void *, const std::string &);
    return reinterpret_cast<Get>(table[7])(settings, std::string(key));
}

template<int Slot> struct Filter {
    static int (*original)(void *, int);
    static int call(void *frame, int stage) {
        int previous = captureTint;
        auto retro = setting(frame, "retro_camera_mode");
        captureTint = (retro == "true" || retro == "1")
                ? std::clamp(std::atoi(setting(frame, "master_mode_cyan_magenta").c_str()), -100, 100) : 0;
        if (captureTint) __android_log_print(ANDROID_LOG_INFO, TAG, "frame tint=%d", captureTint);
        int result = original(frame, stage);
        captureTint = previous;
        return result;
    }
};
template<int Slot> int (*Filter<Slot>::original)(void *, int) = nullptr;

struct HardwareInfo { AHardwareBuffer *buffer; int32_t primaries, transfer, matrix, range; };
using Render = int (*)(void *, void *, const HardwareInfo *, unsigned, const HardwareInfo *, const void *, const void *, int);
Render originalRender;

void tint(AHardwareBuffer *buffer, int value, int matrix, int range) {
    AHardwareBuffer_Desc desc{};
    AHardwareBuffer_describe(buffer, &desc);
    // Android flexible YUV420 and P010. Other layouts must retain the vendor's output.
    bool ten = desc.format == 0x36;
    if (!ten && desc.format != 0x23) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "unsupported buffer format 0x%x", desc.format);
        return;
    }
    using Lock = int (*)(AHardwareBuffer *, uint64_t, int32_t, const ARect *, AHardwareBuffer_Planes *);
    static auto lock = reinterpret_cast<Lock>(dlsym(RTLD_DEFAULT, "AHardwareBuffer_lockPlanes"));
    AHardwareBuffer_Planes planes{};
    if (!lock || lock(buffer, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN | AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN,
                     -1, nullptr, &planes) != 0) return;
    if (planes.planeCount < 3) { AHardwareBuffer_unlock(buffer, nullptr); return; }
    auto addr = [&](int p, int x, int y) {
        auto &plane = planes.planes[p];
        return static_cast<uint8_t *>(plane.data) + size_t(y) * plane.rowStride + size_t(x) * plane.pixelStride;
    };
    bool msb = ten;
    if (ten) for (uint32_t y = 0; y < desc.height && msb; y += 97)
        for (uint32_t x = 0; x < desc.width && msb; x += 13) msb = (*reinterpret_cast<uint16_t *>(addr(0, x, y)) & 63) == 0;
    auto read = [&](int p, int x, int y) -> float {
        auto ptr = addr(p, x, y);
        return ten ? float(msb ? *reinterpret_cast<uint16_t *>(ptr) >> 6 : *reinterpret_cast<uint16_t *>(ptr) & 1023) : *ptr;
    };
    float max = ten ? 1023.f : 255.f, center = ten ? 512.f : 128.f;
    auto write = [&](int p, int x, int y, float v) {
        int q = int(std::lround(std::clamp(v, 0.f, max)));
        auto ptr = addr(p, x, y);
        if (ten) *reinterpret_cast<uint16_t *>(ptr) = uint16_t(msb ? q << 6 : q); else *ptr = uint8_t(q);
    };
    float kr = matrix == 2 ? .2627f : matrix == 1 ? .2126f : .299f;
    float kb = matrix == 2 ? .0593f : matrix == 1 ? .0722f : .114f;
    float kg = 1 - kr - kb, scale = ten ? 4.f : 1.f;
    float y0 = range == 1 ? 0.f : 16.f * scale;
    float ys = range == 1 ? max : 219.f * scale, cs = range == 1 ? max : 224.f * scale;
    float t = value / 100.f, rb = 1 + .16f * t, green = 1 - .16f * t;
    float lum = .308262f * rb + .691738f * green;
    rb /= lum; green /= lum;
    for (uint32_t y = 0; y + 1 < desc.height; y += 2) for (uint32_t x = 0; x + 1 < desc.width; x += 2) {
        float cb = (read(1, x / 2, y / 2) - center) / cs;
        float cr = (read(2, x / 2, y / 2) - center) / cs;
        float sumU = 0, sumV = 0;
        for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
            float Y = (read(0, x + dx, y + dy) - y0) / ys;
            float r = Y + 2 * (1 - kr) * cr, b = Y + 2 * (1 - kb) * cb;
            float g = (Y - kr * r - kb * b) / kg;
            r = std::clamp(r * rb, 0.f, 1.f); b = std::clamp(b * rb, 0.f, 1.f);
            g = std::clamp(g * green, 0.f, 1.f);
            float adjusted = kr * r + kg * g + kb * b;
            write(0, x + dx, y + dy, y0 + ys * adjusted);
            sumU += (b - adjusted) / (2 * (1 - kb));
            sumV += (r - adjusted) / (2 * (1 - kr));
        }
        write(1, x / 2, y / 2, center + cs * sumU / 4);
        write(2, x / 2, y / 2, center + cs * sumV / 4);
    }
    AHardwareBuffer_unlock(buffer, nullptr);
    __android_log_print(ANDROID_LOG_INFO, TAG, "tint=%d %ux%u format=0x%x", value, desc.width, desc.height, desc.format);
}

int render(void *core, void *effect, const HardwareInfo *in, unsigned count, const HardwareInfo *out,
           const void *param, const void *metadata, int flags) {
    int result = originalRender(core, effect, in, count, out, param, metadata, flags);
    if (captureTint && out) __android_log_print(ANDROID_LOG_INFO, TAG, "render result=%d transfer=%d", result, out->transfer);
    if ((result & 1) && captureTint && out && out->buffer && out->transfer == 0)
        tint(out->buffer, captureTint, out->matrix, out->range);
    return result;
}
}

void onLibraryLoadedForPopTint(HookFunction hook, const char *name, void *handle) {
    static bool enabled = [] { char model[PROP_VALUE_MAX]{}; __system_property_get("ro.product.model", model); return std::strcmp(model, "RMX5010") == 0; }();
    if (!enabled) return;
    if (std::strstr(name, "libAlgoInterface.so") && !Filter<0>::original) {
        hookExport(hook, handle, "_ZN7android15doFilterProcessEPNS_15AlgoProcessDataEi", reinterpret_cast<void *>(Filter<0>::call), reinterpret_cast<void **>(&Filter<0>::original), TAG);
    }
    if (std::strstr(name, "libmsnativefilter.so") && !originalRender)
        hookExport(hook, handle, "MsRenderHardwareBuffer", reinterpret_cast<void *>(render), reinterpret_cast<void **>(&originalRender), TAG);
}
