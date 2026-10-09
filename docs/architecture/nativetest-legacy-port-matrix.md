# NativeTest legacy detector parity with Duck

This matrix compares `/home/xiaotong/NativeTest-publish` to Duck HEAD
(`b6c7c42a`) plus the current uncommitted changes. NativeTest's single
`checker.cpp` does **not** define an Android compatibility contract. Do not
copy its verdict bits without checking AOSP, availability and normal controls.

## Migration status

| NativeTest method / observable | Duck owner | Status | Reason |
| --- | --- | --- | --- |
| `FindMntStrings`: stale Bionic TLS and current mountinfo | `capability/earlypreload` | **Ported, experimental** | `mnt_strings_probe.cpp`: checked parent TLS read, bounded fork, parser, missing-current-record evaluation. Fails unavailable; device controls outstanding |
| `FindFutileHide`: ns/mnt / mountinfo ctime | `capability/earlypreload` | Already covered | Early ctime checks with conservative threshold |
| `FindMountInfoLoophole`: mount ID, tmpfs minor, peers | `capability/earlypreload` + `feature/mount` | Already covered | Version and normal mount churn matter |
| `FindInconsistentMount`: statfs /proc/self/exe vs path | `feature/mount` | Already covered | Compare filesystem and visible mount table |
| `FindMemory`: executable mappings, ART JIT mappings | `feature/memory` | Already covered for executable-map baseline | Detailed /proc/self/maps + smaps analysis |
| `FindMemory`: JIT readable/writable and executable mapping inode consistency | `feature/memory` | **Ported as low-severity supporting evidence** | ART dual-view mappings can be RX+R or RX+RW; distinct instances are not identity-paired from names alone |
| `FindMemory`: mapped `framework.jar` vs stat inode | `feature/memory` | **Ported as supporting evidence** | Same device + differing inode = review; device mismatch/unavailable = info |
| `FindMemory`: all executable system file path/inode consistency | `feature/memory` | **Ported, conservative** | Compares mapped inode and stat(path) only when device matches; bounded report count; normal system update/namespace churn still possible |
| `FindMemory`: old Zygisk argv-7 bytes | `feature/zygisk` | Not ported | Private argv-adjacent stack layout with no AOSP ABI guarantee; raw dereference unsafe |
| `FindMemory`: `inline-max-code-units=` in own ODEX | `feature/memory` | **Ported as diagnostic** | Bounded `pread` of own app ODEX instead of unsafe mapping dereference; marker is compiler metadata and not a root verdict |
| `FindUnload`: linker soinfo allocator page layout | `feature/memory` / `feature/zygisk` | Existing different method only | Duck checks maps vs `dl_iterate_phdr`; Android linker `soinfo` allocator is private and changes across versions |
| `FindNativeBridge`: NativeBridgeError / NativeBridgeAvailable / NativeBridgeInitialized | `feature/memory` | **Ported as diagnostic** | `RTLD_NOLOAD` on an already-loaded `libnativebridge.so`, then exact available C++ ABI exports; `had_error` does not mean injection |
| `FindNativeBridge`: Runtime VM option-pointer delta | `feature/virtualization` | Not ported | `AndroidRuntime` private C++ ABI and fixed 0x70 delta not version-stable |
| `FindSystemServer`: PackageManager shell error stack | `feature/lsposed` | **Not actively ported** | Requires deliberately malformed server-side transaction; unbounded pipe read in old version and nontrivial server failure/permission path |
| `FindTransactions`: Binder transaction timing ratios | `feature/lsposed` | Direct bridge probes already; timing omitted | 100x calls per arm + ~20 sets produce unstable ratio without per-device baselines |
| `FindPtyPermissionLoophole`: send PTY FD to PackageManager shell | `feature/nativeroot` | Local devpts checks already; cross-process probe omitted | Sending special FDs to `system_server` has service-specific semantics; DeadObjectException does not prove expected denial |
| `FindPermissionLoophole`: RTM_GETLINK netlink boundary | `feature/nativeroot` | Already covered | API and target SDK gate |
| `FindAPatch`: syscall-45 mincore/superkey/latency | `feature/nativeroot` | Already covered | More controlled probes, old key-length side channel is version-limited |
| `FindDelayedSyscall`: statx vs newfstatat ratio | `feature/nativeroot` | **Ported as child-isolated timing diagnostic** | Android 11+/supported syscall ABI; interleaved probes on same missing procfs path, no hardcoded threshold or root bit |
| `FindSulist`: fork children and compare procfs atime | `feature/nativeroot` | Not ported | Many child processes + access time timing; filesystems/kernel policies do not guarantee stable procfs atime |
| `FindModification`: loaded libc/libart bytes vs files | `feature/memory` | Already covered | smaps COW/private copied executable system pages with byte comparison |
| `AntiDebug`: self tracer attachment | `feature/memory` | **Ported as diagnostic** | Reads `TracerPid` from /proc/self/status; no ptrace or child suspension |
| `AntiDebug`: breakpoint regsets, inotify self monitor | `feature/memory` | Not ported as Root evidence | NativeTest self anti-analysis, not a generally valid environment integrity verdict |
| `FindResetProp`: property area and value integrity | `capability/systemproperties`, `feature/customrom` | Already covered | Area format depends on API and Bionic backing |
| `KeyAttestation`: keystore Binder response, SPKI/signing | `feature/tee` + `capability/attestation` | Already covered in part | Legacy Keystore1 Android 10/11 needs explicit end-to-end equivalent coverage |
| App Zygote permission/mount variant | `capability/selinuxpolicy` | **Ported as structured diagnostic** | Existing app_zygote carrier reused; SELinux permission controls need device matrix |

## Platform source references

- Bionic linker: https://android.googlesource.com/platform/bionic/+/refs/heads/main/linker/
- Bionic mount parser and static TLS: https://android.googlesource.com/platform/bionic/+/refs/heads/main/libc/bionic/mntent.cpp and `libc/private/bionic_tls.h`
- ART JIT dual-view cache: https://android.googlesource.com/platform/art/+/5725e7c/runtime/jit/jit_code_cache.cc
- Kernel mappings semantics: https://www.kernel.org/doc/html/latest/filesystems/proc.html
- PackageManager ShellCommand: https://android.googlesource.com/platform/frameworks/base/+/android11-release/services/core/java/com/android/server/pm/PackageManagerShellCommand.java
- App zygote: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/com/android/internal/os/AppZygoteInit.java
- SELinux policy: https://android.googlesource.com/platform/system/sepolicy/

Android 10–16 tagged **Bionic TLS**, `Binder.onShellCommand` and
`AppZygoteInit` implementation checks are recorded in
`docs/architecture/nativetest-p1-android10-17.md`. Their behavior must not
be extrapolated to Android 17, for which exact tagged files remain unchecked.
The ART JIT source above verifies one dual-view implementation; **do not claim
all 10–17 ART tags are identical**. Verified framework semantics do not
prove a detector's false-positive rate on a vendor kernel/device.

## Interpretation and release gate

1. New identity findings are **informational / review**, never independent
   `root=true` or `kernel_su=true` flags. Existing specialized probes must
   supply those claims.
2. `process_vm_readv`, `clone`, SELinux xattr and mountinfo are individually
   allowed to fail on vendor kernels. Failure is `unavailable`, not `clean`.
3. Before stronger verdicts, capture stock controls on Android 10 through 17,
   including vendor variants, both app zygote and main process, and cases of
   normal ART JIT cache transitions, mount churn and stale file-backed maps.
4. Do **not** add NativeTest's exact time thresholds, fixed linker offsets or
   arbitrary pointer reads to Duck's normal scan without source- and
   device-specific evidence.
