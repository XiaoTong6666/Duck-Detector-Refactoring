#include "preload/mnt_strings_probe.h"

#include <algorithm>
#include <array>
#include <cerrno>
#include <csignal>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <mntent.h>
#include <poll.h>
#include <string>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <sys/wait.h>
#include <unistd.h>
#include <ctime>

namespace duckdetector::preload::mnt_strings {
namespace {
constexpr std::size_t kBufferSize = BUFSIZ;
constexpr std::size_t kMaxTlsDistance = 128 * 1024;
constexpr int kForkTimeoutMs = 250;

std::uint64_t monotonic_millis() {
    timespec now{};
    if (clock_gettime(CLOCK_MONOTONIC, &now) != 0) return 0;
    return static_cast<std::uint64_t>(now.tv_sec) * 1000ULL +
           static_cast<std::uint64_t>(now.tv_nsec) / 1000000ULL;
}

bool is_token_char(unsigned char c) {
    return c >= 33 && c <= 126;
}

bool take_token(const char *p, std::size_t len, std::size_t &offset,
                std::string &token, std::size_t maxLen) {
    while (offset < len && (p[offset] == '\0' || p[offset] == ' ' || p[offset] == '\t')) {
        ++offset;
    }
    const std::size_t start = offset;
    while (offset < len && is_token_char(static_cast<unsigned char>(p[offset]))) {
        ++offset;
    }
    if (offset == start || offset - start > maxLen || offset == len) return false;
    if (p[offset] != '\0' && p[offset] != ' ' && p[offset] != '\t') return false;
    token.assign(p + start, offset - start);
    return true;
}

bool write_full(int fd, const void *data, std::size_t len) {
    const auto *p = static_cast<const char *>(data);
    while (len) {
        const ssize_t n = write(fd, p, len);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) return false;
        p += n;
        len -= static_cast<std::size_t>(n);
    }
    return true;
}

struct AddressMessage {
    uintptr_t address = 0;
    uintptr_t threadPointer = 0;
    int error = 0;
};

bool read_mnt_tls_address(AddressMessage &msg) {
    int fds[2];
    if (pipe2(fds, O_CLOEXEC) != 0) {
        msg.error = errno;
        return false;
    }
    // Raw clone avoids the ART and pthread_atfork callback machinery, while the
    // child must never return into Android/Java. It uses only getmntent and raw I/O.
    const pid_t pid = static_cast<pid_t>(syscall(SYS_clone, SIGCHLD, 0, nullptr, nullptr, 0));
    if (pid < 0) {
        msg.error = errno;
        close(fds[0]);
        close(fds[1]);
        return false;
    }
    if (pid == 0) {
        close(fds[0]);
        AddressMessage child{};
        child.threadPointer = reinterpret_cast<uintptr_t>(__builtin_thread_pointer());
        FILE *fp = setmntent("/proc/self/mounts", "r");
        if (fp != nullptr) {
            mntent *entry = getmntent(fp);
            if (entry != nullptr) {
                // Verified for Android 10–16 Bionic private bionic_tls.h:
                // mntent_buf immediately precedes mntent_strings[BUFSIZ].
                const uintptr_t address = reinterpret_cast<uintptr_t>(entry + 1);
                const uintptr_t field = reinterpret_cast<uintptr_t>(entry->mnt_fsname);
                if (field >= address && field - address < kBufferSize) child.address = address;
            } else {
                child.error = EPROTO;
            }
            endmntent(fp);
        } else {
            child.error = errno;
        }
        (void) write_full(fds[1], &child, sizeof(child));
        close(fds[1]);
        _exit(0);
    }
    close(fds[1]);
    const std::uint64_t deadline = monotonic_millis() + kForkTimeoutMs;
    pollfd item{fds[0], POLLIN, 0};
    int ready;
    do {
        const auto now = monotonic_millis();
        if (now >= deadline) {
            ready = 0;
            break;
        }
        ready = poll(&item, 1, static_cast<int>(deadline - now));
    } while (ready < 0 && errno == EINTR);
    if (ready <= 0 || !(item.revents & POLLIN)) {
        msg.error = ready == 0 ? ETIMEDOUT : (errno == 0 ? EPROTO : errno);
        kill(pid, SIGKILL);
        close(fds[0]);
        (void) waitpid(pid, nullptr, 0);
        return false;
    }
    const ssize_t n = read(fds[0], &msg, sizeof(msg));
    close(fds[0]);
    int status = 0;
    pid_t reaped = 0;
    do {
        reaped = waitpid(pid, &status, WNOHANG);
        if (reaped == pid || reaped < 0) break;
        if (monotonic_millis() >= deadline) {
            msg.error = ETIMEDOUT;
            (void) kill(pid, SIGKILL);
            (void) waitpid(pid, nullptr, 0);
            return false;
        }
        timespec delay{0, 1000000};
        (void) nanosleep(&delay, nullptr);
    } while (true);
    if (reaped != pid || !WIFEXITED(status) ||
        WEXITSTATUS(status) != 0 || n != static_cast<ssize_t>(sizeof(msg))) {
        msg.error = EPROTO;
        return false;
    }
    return msg.address != 0 && msg.error == 0;
}

}  // namespace

bool parse_bionic_buffer(const char *bytes, std::size_t size, Record &out) {
    if (bytes == nullptr || size < 16) return false;
    std::size_t offset = 0;
    Record rec;
    if (!take_token(bytes, size, offset, rec.source, 255) ||
        !take_token(bytes, size, offset, rec.target, 511) ||
        !take_token(bytes, size, offset, rec.filesystem, 63) ||
        !take_token(bytes, size, offset, rec.options, 512)) return false;
    if (rec.target.empty() || rec.target.front() != '/' ||
        rec.filesystem.empty() ||
        (rec.options.find(',') == std::string::npos &&
         rec.options != "ro" && rec.options != "rw")) return false;
    out = std::move(rec);
    return true;
}

bool suspicious(const Record &record) {
    return record.source == "KSU" || record.source == "magisk" ||
           record.source == "APatch" || record.target == "/data/adb" ||
           record.target.starts_with("/data/adb/");
}

bool is_sensitive_partition(std::string_view path) {
    for (auto prefix : {"/system/", "/vendor/", "/product/", "/system_ext/"}) {
        if (path.starts_with(prefix)) return true;
    }
    return false;
}

bool present_in_mountinfo(const Record &record, std::string_view mountinfo) {
    std::size_t start = 0;
    while (start < mountinfo.size()) {
        const std::size_t end = mountinfo.find('\n', start);
        const std::string_view line = mountinfo.substr(start, end == std::string_view::npos
                                                              ? end : end - start);
        // Exact source/fs/target token comparison, never substring search. The
        // mountinfo format separates the target at index 4 from fs/source after " - ".
        std::array<std::string_view, 5> before{};
        std::size_t p = 0;
        bool ok = true;
        for (auto &field : before) {
            while (p < line.size() && line[p] == ' ') ++p;
            const auto sep = line.find(' ', p);
            if (sep == std::string_view::npos) { ok = false; break; }
            field = line.substr(p, sep - p);
            p = sep + 1;
        }
        const std::size_t dash = line.find(" - ", p);
        if (ok && dash != std::string_view::npos &&
            before[4] == record.target) {
            const auto after = line.substr(dash + 3);
            const auto first = after.find(' ');
            if (first != std::string_view::npos) {
                const auto second = after.find(' ', first + 1);
                const auto fs = after.substr(0, first);
                const auto src = after.substr(first + 1,
                                               second == std::string_view::npos ? second : second - first - 1);
                if (fs == record.filesystem && src == record.source) return true;
            }
        }
        if (end == std::string_view::npos) break;
        start = end + 1;
    }
    return false;
}

HistoricalSample sample_parent_tls() {
    HistoricalSample sample;
    AddressMessage address{};
    if (!read_mnt_tls_address(address)) {
        sample.status = "unavailable_address";
        sample.error = address.error == 0 ? EPROTO : address.error;
        return sample;
    }
    const uintptr_t tp = reinterpret_cast<uintptr_t>(__builtin_thread_pointer());
    if (address.threadPointer != tp ||
        address.address < 4096 ||
        (address.address > tp ? address.address - tp : tp - address.address) > kMaxTlsDistance ||
        address.address > UINTPTR_MAX - kBufferSize) {
        sample.status = "unavailable_tls_validation";
        sample.error = EINVAL;
        return sample;
    }
    std::array<char, kBufferSize> bytes{};
    iovec local{bytes.data(), bytes.size()};
    iovec remote{reinterpret_cast<void *>(address.address), bytes.size()};
    // Linux copies from the *parent* address space without dereferencing a
    // potentially stale pointer in user code. EFAULT/EPERM are not a clean verdict.
    const ssize_t n = static_cast<ssize_t>(
            syscall(SYS_process_vm_readv, getpid(), &local, 1, &remote, 1, 0));
    if (n != static_cast<ssize_t>(bytes.size())) {
        sample.status = "unavailable_read";
        sample.error = n < 0 ? errno : EIO;
        return sample;
    }
    if (!parse_bionic_buffer(bytes.data(), bytes.size(), sample.record)) {
        sample.status = "empty_or_invalid";
        return sample;
    }
    sample.status = "observed";
    return sample;
}
}  // namespace duckdetector::preload::mnt_strings
