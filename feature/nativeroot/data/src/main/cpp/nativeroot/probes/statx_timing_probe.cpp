#include "nativeroot/probes/statx_timing_probe.h"
#include "common/seccomp_child.h"
#include <android/api-level.h>
#include <algorithm>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <csignal>
#include <fcntl.h>
#include <linux/stat.h>
#include <poll.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>
#include <utility>

namespace duckdetector::nativeroot {
namespace {

#if defined(__NR_statx) && defined(__NR_newfstatat)
constexpr int kPairs = 24;
constexpr char kMissingPath[] = "/proc/self/__duck_detector_missing_stat_file__";

std::uint64_t monotonic_ns() {
    timespec value{};
    if (clock_gettime(CLOCK_MONOTONIC, &value) != 0) return 0;
    return static_cast<std::uint64_t>(value.tv_sec) * 1000000000ULL +
           static_cast<std::uint64_t>(value.tv_nsec);
}

struct TimingSample {
    std::uint64_t statx_ns = 0;
    std::uint64_t fstatat_ns = 0;
    int first_error = 0;
    int second_error = 0;
    int valid = 0;
};

// Interleave order to reduce drift, as NativeTest's FindDelayedSyscall did.
// The original 1.1 ratio is not an AOSP contract; return measured evidence only.
TimingSample sample_child() {
    TimingSample result{};
    struct statx stx{};
    struct stat st{};
    auto call_statx = [&]() {
        const auto start = monotonic_ns();
        const long rv = syscall(__NR_statx, AT_FDCWD, kMissingPath,
                                AT_SYMLINK_NOFOLLOW, STATX_BASIC_STATS, &stx);
        const int err = rv < 0 ? errno : 0;
        return std::pair<std::uint64_t, int>{monotonic_ns() - start, err};
    };
    auto call_fstatat = [&]() {
        const auto start = monotonic_ns();
        const long rv = syscall(__NR_newfstatat, AT_FDCWD, kMissingPath,
                                &st, AT_SYMLINK_NOFOLLOW);
        const int err = rv < 0 ? errno : 0;
        return std::pair<std::uint64_t, int>{monotonic_ns() - start, err};
    };
    // Both must access the same nonexistent procfs path, otherwise the
    // comparison mixes different kernel work.
    auto warmup_a = call_statx();
    auto warmup_b = call_fstatat();
    if (warmup_a.second != ENOENT || warmup_b.second != ENOENT) {
        result.first_error = warmup_a.second;
        result.second_error = warmup_b.second;
        return result;
    }
    for (int i = 0; i < kPairs; ++i) {
        const auto first = (i & 1) ? call_statx() : call_fstatat();
        const auto second = (i & 1) ? call_fstatat() : call_statx();
        if (first.second != ENOENT || second.second != ENOENT) return result;
        result.statx_ns += (i & 1) ? first.first : second.first;
        result.fstatat_ns += (i & 1) ? second.first : first.first;
    }
    result.valid = result.statx_ns > 0 && result.fstatat_ns > 0;
    return result;
}

bool collect_sample(TimingSample &out, int &failure) {
    int fds[2]{-1, -1};
    if (pipe2(fds, O_CLOEXEC) != 0) { failure = errno; return false; }
    const pid_t pid = fork();
    if (pid == 0) {
        close(fds[0]);
        if (!common::install_seccomp_trap_exit()) _exit(3);
        const auto result = sample_child();
        const bool success = write(fds[1], &result, sizeof(result)) ==
                             static_cast<ssize_t>(sizeof(result));
        close(fds[1]);
        _exit(success ? 0 : 4);
    }
    close(fds[1]);
    if (pid < 0) { failure = errno; close(fds[0]); return false; }
    pollfd pfd{fds[0], POLLIN, 0};
    int poll_result;
    do { poll_result = poll(&pfd, 1, 350); } while (poll_result < 0 && errno == EINTR);
    if (poll_result != 1 || !(pfd.revents & POLLIN)) {
        failure = poll_result == 0 ? ETIMEDOUT : (errno ? errno : EIO);
        (void) kill(pid, SIGKILL);
        close(fds[0]);
        (void) waitpid(pid, nullptr, 0);
        return false;
    }
    const auto n = read(fds[0], &out, sizeof(out));
    close(fds[0]);
    int status{};
    if (waitpid(pid, &status, 0) < 0 || !WIFEXITED(status) ||
        WEXITSTATUS(status) != 0 || n != static_cast<ssize_t>(sizeof(out))) {
        failure = common::seccomp_trapped(status) ? EPERM : EPROTO;
        return false;
    }
    return true;
}
#endif

} // namespace

ProbeResult run_statx_fstatat_timing_observation() {
    ProbeResult result;
#if defined(__NR_statx) && defined(__NR_newfstatat)
    if (android_get_device_api_level() < 30) return result;
    TimingSample sample{};
    int failure{};
    if (!collect_sample(sample, failure)) {
        result.findings.push_back(Finding{
                .group = "SYSCALL", .label = "statx/fstatat latency unavailable",
                .value = "Unavailable",
                .detail = "Child probe was refused, interrupted or timed out (errno=" +
                          std::to_string(failure) + "). No root inference.",
                .severity = Severity::kInfo});
        return result;
    }
    if (!sample.valid) return result;
    char description[180]{};
    const double ratio = static_cast<double>(sample.fstatat_ns) /
                         static_cast<double>(sample.statx_ns);
    std::snprintf(description, sizeof(description),
                  "interleaved pairs=%d, statx avg=%.1f ns, fstatat avg=%.1f ns, ratio=%.3f. "
                  "No fixed AOSP latency threshold.",
                  kPairs, static_cast<double>(sample.statx_ns) / kPairs,
                  static_cast<double>(sample.fstatat_ns) / kPairs, ratio);
    result.findings.push_back(Finding{
            .group = "SYSCALL", .label = "statx/fstatat timing observation",
            .value = "Measured", .detail = description,
            .severity = Severity::kInfo});
#else
    // Kernel and Android ABI do not provide both syscall numbers.
#endif
    return result;
}
} // namespace duckdetector::nativeroot
