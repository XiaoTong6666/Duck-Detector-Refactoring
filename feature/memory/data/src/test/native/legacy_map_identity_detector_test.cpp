#include "memory/detectors/legacy_map_identity_detector.h"

#include <cassert>
#include <string>
#include <vector>

using duckdetector::memory::MapEntry;
using duckdetector::memory::FindingSeverity;
using duckdetector::memory::inspect_legacy_map_identities;

int main() {
    MapEntry exec{};
    exec.path = "/memfd:jit-cache (deleted)";
    exec.readable = true;
    exec.executable = true;
    exec.inode = 41;
    MapEntry read = exec;
    read.executable = false;
    read.inode = 42;
    auto findings = inspect_legacy_map_identities({exec, read});
    assert(findings.size() == 1);
    assert(findings[0].label == "ART app JIT cache view identity differs");
    assert(findings[0].severity == FindingSeverity::kLow);

    read.inode = exec.inode;
    assert(inspect_legacy_map_identities({exec, read}).empty());
    // ART on newer releases can provide an RW non-executable alias.
    read.readable = true;
    read.writable = true;
    read.inode = 44;
    auto rw_alias = inspect_legacy_map_identities({exec, read});
    assert(rw_alias.size() == 1);
    assert(rw_alias[0].severity == FindingSeverity::kLow);

    // Multiple independent JIT caches are not unambiguously pairable.
    MapEntry exec2 = exec;
    exec2.inode = 45;
    auto multiple = inspect_legacy_map_identities({exec, exec2, read});
    assert(!multiple.empty());
    for (const auto &finding : multiple) {
        assert(finding.severity == FindingSeverity::kLow);
    }

    MapEntry ordinary{};
    ordinary.path = "/system/framework/other.jar";
    ordinary.inode = 321;
    assert(inspect_legacy_map_identities({ordinary}).empty());

    // A file that cannot be stat'ed is not a positive tampering verdict.
    ordinary.path = "/system/framework/framework.jar";
    auto unavailable = inspect_legacy_map_identities({ordinary});
    if (!unavailable.empty()) {
        assert(unavailable[0].severity != FindingSeverity::kHigh);
    }
    return 0;
}
