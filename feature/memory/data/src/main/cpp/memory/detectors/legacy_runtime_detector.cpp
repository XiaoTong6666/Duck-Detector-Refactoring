#include "memory/detectors/legacy_runtime_detector.h"
#include <dlfcn.h>
#include <cerrno>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>

namespace duckdetector::memory {
namespace {
Finding note(const char *label, std::string detail) {
    return Finding{.section = "LINKER", .category = "NATIVE_BRIDGE",
                   .label = label, .detail = std::move(detail),
                   .severity = FindingSeverity::kLow};
}
using BridgeFlag = bool (*)();

std::vector<Finding> native_bridge_state() {
    std::vector<Finding> result;
    // NativeTest FindNativeBridge calls NativeBridgeError() without interpreting
    // its precise meaning: AOSP defines it as had_error, *not* bridge activity.
    // RTLD_NOLOAD avoids importing a new bridge into the calling process.
    void *handle = dlopen("libnativebridge.so", RTLD_NOW | RTLD_NOLOAD);
    if (handle == nullptr) return result;
    auto symbol = [handle](const char *name) {
        return reinterpret_cast<BridgeFlag>(dlsym(handle, name));
    };
    const auto error = symbol("_ZN7android17NativeBridgeErrorEv");
    const auto available = symbol("_ZN7android21NativeBridgeAvailableEv");
    const auto initialized = symbol("_ZN7android23NativeBridgeInitializedEv");
    if (error != nullptr && available != nullptr && initialized != nullptr) {
        const bool hasError = error();
        const bool isAvailable = available();
        const bool isInitialized = initialized();
        if (hasError || isAvailable || isInitialized) {
            result.push_back(note("Android NativeBridge runtime state",
                    std::string("NativeBridgeError=") + (hasError ? "true" : "false") +
                    "; Available=" + (isAvailable ? "true" : "false") +
                    "; Initialized=" + (isInitialized ? "true" : "false") +
                    ". AOSP states these are runtime translation and error flags, "
                    "not injection or root proof."));
        }
    }
    dlclose(handle);
    return result;
}

std::vector<Finding> debugger_state() {
    std::vector<Finding> result;
    // NativeTest's ptrace hardware breakpoint experiment intentionally stops
    // a child. Here observe the calling process without spawning/tracing one.
    FILE *fp = fopen("/proc/self/status", "re");
    if (fp == nullptr) return result;
    char line[256] = {};
    while (fgets(line, sizeof(line), fp)) {
        if (std::strncmp(line, "TracerPid:", 10) != 0) continue;
        char *end = nullptr;
        const long tracer = strtol(line + 10, &end, 10);
        if (end != line + 10 && tracer > 0 && tracer <= INT32_MAX) {
            result.push_back(note("Process is being traced",
                    "TracerPid=" + std::to_string(tracer) +
                    ". A debugger, profiling session, or instrumentation may be attached; "
                    "this is not a root verdict."));
        }
        break;
    }
    fclose(fp);
    return result;
}
} // namespace

std::vector<Finding> inspect_legacy_runtime_state() {
    auto results = native_bridge_state();
    auto debugger = debugger_state();
    results.insert(results.end(), debugger.begin(), debugger.end());
    return results;
}
} // namespace duckdetector::memory
