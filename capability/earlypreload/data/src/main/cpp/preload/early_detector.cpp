/*
 * Copyright 2026 Duck Apps Contributor
 * If you have any questions, suggestions, or other inquiries, please email Eltavine <me@eltavine.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "preload/early_detector.h"
#include "preload/mnt_strings_probe.h"

#include <android/log.h>

#include <cerrno>
#include <cstring>
#include <fcntl.h>
#include <mntent.h>
#include <string>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>
#include <utility>

#define LOG_TAG "EarlyMountPreload"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace duckdetector::preload {

    namespace {

        constexpr std::int64_t kSecToNs = 1000000000LL;
        constexpr std::int64_t kValidContextThresholdNs = 5LL * kSecToNs;

        EarlyMountPreloadResult g_storedResult;
        bool g_hasRun = false;
        std::int64_t g_preloadTimestampNs = 0;

        std::int64_t realtime_coarse_now_ns() {
            struct timespec now{};
            clock_gettime(CLOCK_REALTIME_COARSE, &now);
            return (static_cast<std::int64_t>(now.tv_sec) * kSecToNs) + now.tv_nsec;
        }

        bool get_live_mount_evidence(std::string &outSource, std::string &outTarget, std::string &outFs) {
            FILE *file = setmntent("/proc/self/mounts", "r");
            if (file == nullptr) {
                LOGE("setmntent() failed: %s", strerror(errno));
                return false;
            }

            std::string source;
            std::string target;
            std::string filesystem;
            bool observed = false;
            bool suspiciousCaptured = false;
            struct mntent *entry = nullptr;
            while ((entry = getmntent(file)) != nullptr) {
                // Copy before calling getmntent() again: the next iteration overwrites its
                // thread-local buffer, and EOF returns nullptr.
                const std::string currentSource = entry->mnt_fsname ? entry->mnt_fsname : "";
                const std::string currentTarget = entry->mnt_dir ? entry->mnt_dir : "";
                const bool suspicious = currentSource == "KSU" ||
                                        currentSource == "magisk" ||
                                        currentSource == "APatch" ||
                                        (currentTarget == "/data/adb" ||
                                         currentTarget.starts_with("/data/adb/"));
                // Preserve the first suspicious *live* entry, otherwise the last parsed
                // entry for display. A subsequent clean entry must not erase a hit.
                if (!suspiciousCaptured) {
                    source = currentSource;
                    target = currentTarget;
                    filesystem = entry->mnt_type ? entry->mnt_type : "";
                    suspiciousCaptured = suspicious;
                }
                observed = true;
            }

            endmntent(file);
            if (observed) {
                outSource = std::move(source);
                outTarget = std::move(target);
                outFs = std::move(filesystem);
            }
            return observed;
        }

    }  // namespace

    bool detect_futile_hide(EarlyMountPreloadResult &result) {
        struct timespec now{};
        clock_gettime(CLOCK_REALTIME_COARSE, &now);
        usleep(100);

        bool detected = false;

        struct stat nsStat{};
        if (fstatat(AT_FDCWD, "/proc/self/ns/mnt", &nsStat, AT_SYMLINK_NOFOLLOW) == 0) {
            const std::int64_t delta =
                    ((static_cast<std::int64_t>(nsStat.st_ctim.tv_sec) - now.tv_sec) * kSecToNs) +
                    (nsStat.st_ctim.tv_nsec - now.tv_nsec);
            result.nsMntCtimeDeltaNs = delta;
            if (delta < -kSecToNs) {
                detected = true;
                result.futileHideDetected = true;
                result.findings.push_back(
                        "FUTILE_HIDE|ns/mnt ctime anomaly: delta=" + std::to_string(delta) +
                        " ns|DANGER"
                );
            }
        }

        struct stat mountInfoStat{};
        if (stat("/proc/self/mountinfo", &mountInfoStat) == 0) {
            const std::int64_t delta =
                    ((static_cast<std::int64_t>(mountInfoStat.st_ctim.tv_sec) - now.tv_sec) *
                     kSecToNs) +
                    (mountInfoStat.st_ctim.tv_nsec - now.tv_nsec);
            result.mountInfoCtimeDeltaNs = delta;
            if (delta < -kSecToNs) {
                detected = true;
                result.futileHideDetected = true;
                result.findings.push_back(
                        "FUTILE_HIDE|mountinfo ctime anomaly: delta=" + std::to_string(delta) +
                        " ns|DANGER"
                );
            }
        }

        return detected;
    }

    bool detect_mnt_strings_anomaly(EarlyMountPreloadResult &result) {
        // Always snapshot parent TLS *before* this process calls getmntent(),
        // because doing so irreversibly overwrites the very historical bytes of interest.
        const auto historical = mnt_strings::sample_parent_tls();
        result.mntStringsStatus = historical.status;
        result.mntStringsErrno = historical.error;
        result.findings.push_back(
                "MNT_STRINGS_STATUS|Historical Bionic TLS: " + historical.status +
                " (errno=" + std::to_string(historical.error) + ")|SUPPORT");

        std::string mountinfo;
        bool mountinfoComplete = false;
        {
            FILE *fp = fopen("/proc/self/mountinfo", "r");
            if (fp != nullptr) {
                char line[8192];
                bool truncated = false;
                while (fgets(line, sizeof(line), fp) != nullptr) {
                    if (mountinfo.size() + strlen(line) > 512 * 1024) {
                        truncated = true;
                        break;
                    }
                    mountinfo += line;
                }
                mountinfoComplete = !truncated && !ferror(fp) && feof(fp);
                fclose(fp);
            }
        }

        std::string source;
        std::string target;
        std::string fs;
        const bool liveAvailable = get_live_mount_evidence(source, target, fs);
        bool detected = false;
        if (liveAvailable && (source == "KSU" || source == "magisk" || source == "APatch" ||
                              target == "/data/adb" || target.starts_with("/data/adb/"))) {
            result.liveMountDetected = true;
            result.liveMountSource = source;
            result.liveMountTarget = target;
            result.liveMountFs = fs;
            detected = true;
            result.findings.push_back("LIVE_MOUNT|Visible early mount: " + source + " -> " +
                                      target + " (" + fs + ")|DANGER");
        }

        if (historical.status == "observed") {
            const auto &rec = historical.record;
            result.mntStringsSource = rec.source;
            result.mntStringsTarget = rec.target;
            result.mntStringsFs = rec.filesystem;
            if (!mountinfoComplete) {
                result.mntStringsStatus = "unavailable_mountinfo";
                result.findings.push_back(
                    "MNT_STRINGS_STATUS|Current mountinfo is incomplete or unreadable; "
                    "historical absence cannot be established|SUPPORT");
            } else {
                const bool stillPresent = mnt_strings::present_in_mountinfo(rec, mountinfo);
                if (stillPresent) {
                    result.mntStringsStatus = "present_in_current_mounts";
                } else if (mnt_strings::suspicious(rec)) {
                    detected = true;
                    result.mntStringsDetected = true;
                    result.mntStringsStatus = "hidden_suspicious_record";
                    result.findings.push_back("MNT_STRINGS|Historical TLS mount absent from current mountinfo: " +
                                              rec.source + " -> " + rec.target + " (" + rec.filesystem + ")|DANGER");
                } else if (mnt_strings::is_sensitive_partition(rec.target)) {
                    result.mntStringsStatus = "historical_partition_absent";
                    result.findings.push_back("MNT_STRINGS_HISTORY|Historical partition mount absent from current mountinfo: " +
                                              rec.target + " (can reflect normal mount churn)|WARNING");
                } else {
                    result.mntStringsStatus = "historical_other_record";
                }
            }
        } else if (liveAvailable) {
            // Display only: this live observation is not a TLS-residue detection.
            result.mntStringsSource = source;
            result.mntStringsTarget = target;
            result.mntStringsFs = fs;
        }

        return detected;
    }

    bool is_preload_context_valid() {
        if (!g_hasRun) {
            return false;
        }
        return (realtime_coarse_now_ns() - g_preloadTimestampNs) < kValidContextThresholdNs;
    }

    EarlyMountPreloadResult run_early_detection() {
        EarlyMountPreloadResult result;
        g_preloadTimestampNs = realtime_coarse_now_ns();

        bool detected = false;
        detected |= detect_futile_hide(result);
        detected |= detect_mnt_strings_anomaly(result);
        detected |= detect_mount_id_loophole(result);
        detected |= detect_minor_dev_gap(result);
        detected |= detect_peer_group_gap(result);
        result.detected = detected;

        std::string methods;
        if (result.futileHideDetected) {
            methods += methods.empty() ? "FutileHide" : ", FutileHide";
        }
        if (result.mntStringsDetected) {
            methods += methods.empty() ? "MntStrings" : ", MntStrings";
        }
        if (result.liveMountDetected) {
            methods += methods.empty() ? "LiveMount" : ", LiveMount";
        }
        if (result.mountIdGapDetected) {
            methods += methods.empty() ? "MountIdGap" : ", MountIdGap";
        }
        if (result.minorDevGapDetected) {
            methods += methods.empty() ? "MinorDevGap" : ", MinorDevGap";
        }
        if (result.peerGroupGapDetected) {
            methods += methods.empty() ? "PeerGroupGap" : ", PeerGroupGap";
        }

        result.detectionMethod = methods.empty() ? "None" : methods;
        result.details = detected
                         ? "Early mount preload detected: " + result.detectionMethod
                         : "No startup preload anomaly found";

        g_storedResult = result;
        g_hasRun = true;
        return result;
    }

    const EarlyMountPreloadResult *get_stored_result() {
        return g_hasRun ? &g_storedResult : nullptr;
    }

    bool has_early_detection_run() {
        return g_hasRun;
    }

    void reset_early_detection() {
        g_storedResult = EarlyMountPreloadResult{};
        g_hasRun = false;
        g_preloadTimestampNs = 0;
    }

}  // namespace duckdetector::preload
