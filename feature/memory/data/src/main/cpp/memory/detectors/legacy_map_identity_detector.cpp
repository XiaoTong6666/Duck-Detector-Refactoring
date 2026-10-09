#include "memory/detectors/legacy_map_identity_detector.h"
#include "memory/common/maps_reader.h"

#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <fcntl.h>
#include <unistd.h>
#include <array>
#include <algorithm>
#include <cerrno>
#include <map>
#include <set>
#include <sstream>
#include <string_view>
#include <utility>

namespace duckdetector::memory {
namespace {

enum class CacheGroup { kNone, kApp, kZygote };

CacheGroup classify_cache(std::string_view path) {
    // Preserve the names used by Android ART on Android 10–16. A name alone
    // is never evidence of a root framework: ART legitimately maps both a
    // writable/non-executable view and an executable view of a memfd.
    constexpr std::array<std::string_view, 5> kAppNames{
            "/memfd:jit-cache", "/memfd:/jit-cache",
            "/dev/ashmem/jit-cache", "/dev/ashmem//jit-cache",
            "/dev/ashmem/dalvik-jit-code-cache"};
    constexpr std::array<std::string_view, 4> kZygoteNames{
            "/memfd:jit-zygote-cache", "/memfd:/jit-zygote-cache",
            "/dev/ashmem/jit-zygote-cache", "/dev/ashmem//jit-zygote-cache"};
    for (auto prefix : kZygoteNames) if (path.starts_with(prefix)) return CacheGroup::kZygote;
    for (auto prefix : kAppNames) if (path.starts_with(prefix)) return CacheGroup::kApp;
    return CacheGroup::kNone;
}

Finding evidence(std::string title, std::string detail, FindingSeverity severity) {
    return Finding{.section = "MAPS", .category = "SMAPS",
                   .label = std::move(title), .detail = std::move(detail),
                   .severity = severity};
}

bool named_framework_jar(const std::string &path) {
    // AOSP system/framework/framework.jar path only; maps with a deleted
    // suffix cannot be stat'ed by that name, and other @ suffixes are not
    // necessarily this jar's backing file.
    return path == "/system/framework/framework.jar";
}

struct ObservedCache {
    // AOSP ART legitimately uses multiple memfds with the same label across
    // generations. Do not compare unrelated inode identities as a verdict.
    std::set<std::uint64_t> executable_inodes;
    std::set<std::uint64_t> writable_inodes;
    std::set<std::uint64_t> readonly_inodes;
};

bool system_executable(const MapEntry &entry) {
    if (!entry.executable || entry.inode == 0 || entry.path.empty() ||
        entry.path.find(" (deleted)") != std::string::npos ||
        entry.path.find('!') != std::string::npos) return false;
    return is_system_path(entry.path);
}

bool own_odex_path(const std::string &path) {
    return (path.starts_with("/data/app/") || path.starts_with("/mnt/expand/")) &&
           path.ends_with(".odex") &&
           path.find(" (deleted)") == std::string::npos;
}

// NativeTest scanned a mapped ODEX VMA for this option with an unchecked
// pointer. Here scan bounded, explicit file bytes so unmapped/PROT_NONE data
// cannot cause a segmentation fault. Marker presence alone is not a root verdict.
bool odex_has_inline_limit(const std::string &path) {
    constexpr std::string_view marker = "inline-max-code-units=";
    constexpr std::size_t budget = 4 * 1024 * 1024;
    const int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return false;
    std::array<char, 4096> bytes{};
    std::string overlap;
    std::size_t offset = 0;
    bool detected = false;
    while (offset < budget) {
        const auto n = pread(fd, bytes.data(), bytes.size(), static_cast<off_t>(offset));
        if (n <= 0) break;
        std::string chunk = overlap + std::string(bytes.data(), static_cast<std::size_t>(n));
        if (chunk.find(marker) != std::string::npos) {
            detected = true;
            break;
        }
        overlap = chunk.substr(chunk.size() > marker.size()
                                       ? chunk.size() - marker.size() : 0);
        offset += static_cast<std::size_t>(n);
    }
    close(fd);
    return detected;
}

} // namespace

std::vector<Finding> inspect_legacy_map_identities(const std::vector<MapEntry> &maps) {
    std::vector<Finding> findings;
    std::map<CacheGroup, ObservedCache> caches;
    std::set<std::string> statted_jars;
    std::set<std::string> checked_executables;
    std::set<std::string> inspected_odex;
    constexpr std::size_t kMaxExecutableFindings = 12;
    std::size_t executableFindings = 0;
    for (const auto &entry : maps) {
        if (own_odex_path(entry.path) && inspected_odex.size() < 8 &&
            inspected_odex.insert(entry.path).second &&
            odex_has_inline_limit(entry.path)) {
            findings.push_back(evidence(
                "ART ODEX inlining option observed",
                entry.path + ": dex2oat inline-max-code-units option string is present. "
                "This can be legitimate compiler metadata; do not infer Zygisk without controls.",
                FindingSeverity::kLow));
        }
        if (entry.inode == 0) continue;
        const auto group = classify_cache(entry.path);
        if (group != CacheGroup::kNone) {
            auto &state = caches[group];
            if (entry.executable) state.executable_inodes.insert(entry.inode);
            else if (entry.writable) state.writable_inodes.insert(entry.inode);
            else if (entry.readable) state.readonly_inodes.insert(entry.inode);
        }
        if (system_executable(entry) &&
            checked_executables.insert(entry.path + ":" + std::to_string(entry.inode)).second &&
            executableFindings < kMaxExecutableFindings) {
            struct stat mappedFile{};
            if (stat(entry.path.c_str(), &mappedFile) != 0) {
                ++executableFindings;
                findings.push_back(evidence(
                    "Executable system mapping backing unavailable",
                    entry.path + ": path stat unavailable; mapping identity cannot be verified",
                    FindingSeverity::kLow));
            } else if (major(mappedFile.st_dev) == entry.dev_major &&
                       minor(mappedFile.st_dev) == entry.dev_minor &&
                       static_cast<std::uint64_t>(mappedFile.st_ino) != entry.inode) {
                ++executableFindings;
                findings.push_back(evidence(
                    "Executable system mapping inode differs",
                    entry.path + ": mapped and current path inodes differ on same device. "
                    "This may be a replaced file, update, or namespace change.",
                    FindingSeverity::kMedium));
            }
        }
        if (!named_framework_jar(entry.path)) continue;
        const std::string identity = entry.path + ":" +
                                     std::to_string(entry.dev_major) + ":" +
                                     std::to_string(entry.dev_minor) + ":" +
                                     std::to_string(entry.inode);
        if (!statted_jars.insert(identity).second) continue;
        struct stat st{};
        if (stat(entry.path.c_str(), &st) != 0) {
            findings.push_back(evidence(
                    "Framework JAR backing identity unavailable",
                    entry.path + ": the current backing file could not be stat()ed; "
                    "mapping identity is unverified, not clean",
                    FindingSeverity::kLow));
            continue;
        }
        const bool sameDevice =
                major(st.st_dev) == entry.dev_major && minor(st.st_dev) == entry.dev_minor;
        if (!sameDevice) {
            findings.push_back(evidence(
                    "Framework JAR mapped device differs",
                    entry.path + ": mapped device differs from stat(path). "
                    "Mount namespaces, overlayfs and bind mounts may explain this",
                    FindingSeverity::kLow));
        } else if (static_cast<std::uint64_t>(st.st_ino) != entry.inode) {
            std::ostringstream out;
            out << entry.path << ": /proc/self/maps inode=" << entry.inode
                << " but stat(path) inode=" << st.st_ino
                << " on the same device; compare with a clean control image";
            findings.push_back(evidence(
                    "Framework JAR mapped inode differs",
                    out.str(), FindingSeverity::kMedium));
        }
    }
    for (const auto &[group, state] : caches) {
        const std::string name = group == CacheGroup::kApp ? "ART app JIT" : "ART zygote JIT";
        if (state.executable_inodes.size() > 1 ||
            state.writable_inodes.size() > 1 || state.readonly_inodes.size() > 1) {
            findings.push_back(evidence(
                "Multiple " + name + " cache backing inodes",
                "Multiple " + name + " cache instances are present. This is often "
                "legitimate during ART runtime cache changes; no root verdict.",
                FindingSeverity::kLow));
        }
        // AOSP ART's dual views can be RX+R or RX+RW, according to release
        // and runtime phase. Compare only singleton pairs; multiple cache
        // instances cannot be paired from name alone.
        const auto &nonexec = !state.writable_inodes.empty()
                                  ? state.writable_inodes : state.readonly_inodes;
        if (nonexec.size() == 1 && state.executable_inodes.size() == 1 &&
            *nonexec.begin() != *state.executable_inodes.begin()) {
            findings.push_back(evidence(
                name + " cache view identity differs",
                name + ": singleton non-executable and executable views have different "
                "inodes. Independent JIT caches can explain this; inspect paired offsets.",
                FindingSeverity::kLow));
        }
    }
    return findings;
}

} // namespace duckdetector::memory
