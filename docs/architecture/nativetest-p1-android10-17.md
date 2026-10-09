# NativeTest P1 parity: Android 10–17

This record separates **source-level presence**, **equivalent evidence semantics**,
and **validated execution on a device**. No observation is a device compromise
verdict merely because NativeTest treats it as a root bit.

## Code being compared

- NativeTest-publish: `checker.cpp` `FindMntStrings`, `FindSystemServer`,
  `Java_icu_nullptr_nativetest_NTRZygotePreload_check`.
- Duck: `capability/earlypreload/data/src/main/cpp/preload/mnt_strings_probe.cpp`
  and `early_detector.cpp`,
  `capability/selinuxpolicy/data/.../AppZygoteMountPermissionProbe.kt`,
  `feature/lsposed/data/.../LSPosedBinderProbe.kt`.

## AOSP target tags and compatibility gates

The exact source files to cross-check for **each** row are:
`platform/bionic/libc/bionic/mntent.cpp`,
`platform/frameworks/base/core/java/android/os/Binder.java`,
`platform/frameworks/base/core/java/com/android/internal/os/AppZygoteInit.java`,
`platform/frameworks/base/core/jni/com_android_internal_os_Zygote.cpp`,
`platform/frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java`
(or the version's replacement implementation), and the applicable SELinux policy.

| Android | Reference tag | API | App zygote / Mntent integration | Package Binder shell-output parity |
| --- | --- | ---: | --- | --- |
| 10 | `android-10.0.0_r47` | 29 | Verified `doPreload`, no FD whitelist; getmntent uses Bionic TLS | Verified: default Binder onShellCommand has **no UID guard**, package-service override unverified |
| 11 | `android-11.0.0_r48` | 30 | Verified `doPreload`, no FD whitelist; getmntent uses Bionic TLS | Verified: default Binder onShellCommand denies non-root/shell; package-service override unverified |
| 12 | `android-12.0.0_r34` | 31 | Verified `markOpenedFilesBeforePreload/allowFilesOpenedByPreload`; Bionic TLS | Verified: default Binder UID guard; package-service override unverified |
| 13 | `android-13.0.0_r83` | 33 | Verified preload FD whitelist and Bionic TLS | Verified: default Binder UID guard; package-service override unverified |
| 14 | `android-14.0.0_r75` | 34 | Verified preload FD whitelist and Bionic TLS | Verified: default Binder UID guard; package-service override unverified |
| 15 | `android-15.0.0_r36` | 35 | Verified preload FD whitelist and Bionic TLS | Verified: default Binder UID guard; package-service override unverified |
| 16 | `android-16.0.0_r3` | 36 | Verified preload FD whitelist and Bionic TLS | Verified: default Binder UID guard; package-service override unverified |
| 17 | `android-17.0.0_r1` | 37 | Same public API probe, permission outcome must be observed | **Unverified**: release tag exists, but exact package-service/SELinux paths need per-tag inspection |

**Do not mistake this table for eight completed source audits or device tests.**
GitHub's public `aosp-mirror` tagged raw files were read and compared for
Android 10–16, covering the three named files and their actual function bodies.
Android 17's tag exists on AOSP Gitiles, but its equivalent tagged file content
was not available through the public GitHub mirror or DevSpace's network at
review time. Android 10–16 tagged `PackageManagerShellCommand` paths and the
Android 13–16 service override were inspected; 10–12 service overrides,
device SELinux policy, and device validation remain outstanding. The framework
Binder UID-check transition is **Android 10 without it -> Android 11 with it**
in the inspected tags; an overridden service can behave differently.

Canonical source entry points:

- https://android.googlesource.com/platform/bionic/
- https://android.googlesource.com/platform/frameworks/base/
- https://android.googlesource.com/platform/system/sepolicy/

## MntStrings decision

Bionic's `getmntent` returns a thread-local `mntent` and a separate
thread-local string buffer. The Android 10–16 private `bionic_tls.h`
definition places `mntent_buf` immediately before `mntent_strings[BUFSIZ]`.
After `clone(SIGCHLD)`, the calling thread's TLS has the same virtual address
in child and parent. This **does** give NativeTest's child-derived
buffer address a sound basis on those Bionic versions, provided the child
calls `getmntent` on the same thread and the parent has not overwritten
its earlier TLS contents.

The prior Duck implementation lost `entry` at EOF and then overwrote the
parent's TLS by re-reading `getmntent`. The replacement derives the address
from the child's **first successful** `getmntent`, checks that the returned
source field lies within the corresponding buffer, matches the child's
thread pointer to the parent's, rejects non-near-TLS/out-of-range addresses,
and reads the parent's bytes using `process_vm_readv` (a checked syscall
that returns EFAULT rather than blindly dereferencing a guessed pointer).
The fork IPC has a timeout and reports errno on failure. The parent
snapshot is read **before** any parent `getmntent` call.

The buffer parser checks the expected four NUL-separated Bionic fields.
Exact source/fs/target equality with current `mountinfo` is the control:
the historical record gets a `DANGER` signal only for an explicit
root-related source/target that is **missing from** current `mountinfo`.
Normal system-partition churn is just a warning. The separate early *live*
mount check is now labelled `Live mount evidence` rather than
`mntent strings residue`. Status/errno are retained when history cannot
be recovered, and no failure silently becomes historical-root evidence.

This is a memory-residue heuristic with strict preconditions, **not** proof
of historical root state on every device: if no prior `getmntent` ran on
the parent thread, it correctly yields `empty_or_invalid`. Custom Bionic
TLS layout, nonstandard seccomp, and fork restrictions produce unavailable.
Using `clone` then libc I/O in a multithreaded ART process is inherently
fragile; a bounded parent timeout reduces but does not eliminate
fork-child side effects. Require physical device controls.

Android 10–16 tagged `mntent.cpp` implementations all obtain both buffers
from `__get_bionic_tls()`. Do not infer that an older, unrelated
`ThreadLocalBuffer` implementation or another thread's TLS layout applies.
Android 17's exact implementation remains unverified.

## Binder server-side output decision

NativeTest's `FindSystemServer` builds a `SHELL_COMMAND_TRANSACTION` against
the package service, then searches returned text for `LSPHooker` and
`kr328.magic`. This requires a server-side exception/output path, not just
a binder handle. A default Binder UID check, service-specific override,
parcel schema change, or policy restriction can alter the path. Triggering
an invalid package shell command can also place load and exception handling
into `system_server`, where a diagnostic app has no right to assume safety.

Tagged AOSP 10–16 `PackageManagerShellCommand.java` **does** implement the
`path` command. `runPath()` calls `getNextArgRequired()`; omitting the
package argument can raise an `IllegalArgumentException` in a service
shell-command handler. `ShellCommand.exec()` handles error reporting and
normally writes diagnostic text to its error descriptor. A normal
`path <the calling app's own package>` performs a query, but yields ordinary
package-path output, not the exception stack that NativeTest uses as a hook
fingerprint. For Android 13–16, tagged `PackageManagerService` overrides
`onShellCommand` and calls `PackageManagerShellCommand.exec` directly,
so the base Binder UID restriction **does not by itself decide the outcome**.
On 10–12, the shell-command class is confirmed, but the exact tagged PM
service override still needs verification (the source file was too large
for this environment's public mirror fetch). Also validate caller identity
at the actual service command's permission checks.

**Decision: do not add the active malformed shell-command probe.** Intentionally
throwing into `system_server` is unnecessary work on a production service,
and NativeTest's unbounded blocking pipe read is unsuitable in Duck.
An ordinary, bounded `path <own package>` query is safer but does not
provide the same hook stack. Retain the
existing LSPosed Binder *direct-response* probe. This does not provide full
server-side parity. Before considering it, each AOSP tag must establish the
exact `onTransact -> shellCommand -> permission check -> output` sequence and
an explicit non-mutating, bounded response path. A denied transaction is
`Unavailable`, never `Clean`; arbitrary service output is not reliable
evidence without a validated control.

## App zygote additional permissions

The existing `SelinuxContextValidityPreload` now captures (without spawning
a second app-zygote service):

1. `/proc/self/attr/current` `lstat` change-time delta from wall time,
   preserving nanosecond information from the stat result. **Diagnostic
   only**: inode change time preceding the sample is normal.
2. SELinux label reads on `/mnt`, `/mnt/obb` and `/mnt/asec`. Only a
   `u:object_r:tmpfs:s0` label triggers a single native
   `opendir + readdir + closedir` test, rather than Java `File.list()`.
   Permission denial and native read errno remain explicit.
3. App-zygote mountinfo IDs for `/mnt`, `/mnt/obb`, `/mnt/asec`,
   `/data` and `/apex/com.android.art`, plus narrow mount-ID gap markers;
   the SELinux repository compares these with its own current mountinfo.
   Namespace IDs may legitimately differ, so the comparison is diagnostic.

Observations flow through a **typed** `AppZygoteMountSnapshot` on the existing
carrier payload, with attempted, available, errno, carrierVerified, paths,
labels and first-entry outcomes. The repository emits a **separate**
`App zygote mount permissions and ID comparison` method; it carries
`isSecure = null`, not a root verdict, and cannot
change the KSU policy oracle's `isSecure` result. On Android 10/11, the
existing preload logic still closes its libselinux AVC socket before the
carrier returns. The directory probe closes the DIR regardless of result.
Any later SELinux preload exception preserves the already-collected
App Zygote snapshot in the fallback payload, marked unverified.

**Not yet equivalent** to NativeTest's `check & 2` danger bit: whether a
listable tmpfs target is truly a policy violation needs AOSP and device
version-specific expected-denial controls and verification of current
carrier SELinux identity. A readable or blocked label is not silently
mapped to a clean result.

## Validation still required

- Android 10–17: finish PM per-tag override and SELinux policy audit; inspect
  Android 17 when exact tagged files are accessible.
- Android 10–17: clean AOSP or GSI control and affected modified-system
  control; ensure no false root positives for mount labels or procfs ctime.
- Test App Zygote absent, stopped, restricted, and clean preload flows; the
  carrier must return `Unavailable` rather than `Clean` if it never started.
- Check `clone` and `process_vm_readv` availability in ordinary-app contexts,
  with clean system and modified system samples on each device family.
- Do not elevate App Zygote mount ID differences into a DANGER finding without controls.
