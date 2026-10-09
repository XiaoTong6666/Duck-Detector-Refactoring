/*
 * Copyright 2026 Duck Apps Contributor
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.eltavine.duckdetector.capability.selinuxpolicy.data

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.eltavine.duckdetector.core.native.DuckDetectorNativeLibrary
import java.io.ByteArrayOutputStream

/** A bounded, fail-local observation taken by the app_zygote *before* it forks isolated children. */
internal class AppZygoteMountPermissionProbe {
    private external fun nativeFirstDirectoryEntry(path: String): Int

    fun inspect(): AppZygoteMountSnapshot {
        val nowNs = System.currentTimeMillis() * 1_000_000L
        val attr = runCatching { Os.lstat("/proc/self/attr/current").st_ctim }
        val elapsed = attr.getOrNull()?.let {
            it.tv_sec * 1_000_000_000L + it.tv_nsec - nowNs
        }
        val table = runCatching {
            val fd = Os.open(
                "/proc/self/mountinfo",
                OsConstants.O_RDONLY or OsConstants.O_CLOEXEC,
                0,
            )
            try {
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var complete = false
                while (output.size() < 256 * 1024) {
                    val count = Os.read(fd, chunk, 0, minOf(chunk.size, 256 * 1024 - output.size()))
                    if (count <= 0) {
                        complete = count == 0
                        break
                    }
                    output.write(chunk, 0, count)
                }
                if (!complete) {
                    // A full buffer might coincide with EOF: a single read disambiguates
                    // without accepting a partial mount table as authoritative.
                    complete = Os.read(fd, chunk, 0, 1) == 0
                }
                output.toString("UTF-8") to complete
            } finally {
                Os.close(fd)
            }
        }
        val mountText = table.getOrNull()?.first
        val mountComplete = table.getOrNull()?.second == true
        val mounts = mountText?.let(AppZygoteMountInfo::parse).orEmpty()
        val paths = listOf("/mnt", "/mnt/obb", "/mnt/asec", "/data", "/apex/com.android.art")
            .map { path ->
                val mount = mounts[path]
                if (path !in listOf("/mnt", "/mnt/obb", "/mnt/asec")) {
                    return@map AppZygoteMountPath(
                        path = path, attempted = true, available = mount != null,
                        mountId = mount?.id, mountFs = mount?.fs, mountSource = mount?.source,
                    )
                }
                val context = runCatching {
                    Os.getxattr(path, "security.selinux")
                        .toString(Charsets.UTF_8).trimEnd('\u0000')
                }
                val label = context.getOrNull()
                // NativeTest's control requires the precise tmpfs label. For other
                // labels, do NOT imply a successful directory-permission test.
                val dirCode = if (label == "u:object_r:tmpfs:s0" &&
                    DuckDetectorNativeLibrary.isLoaded
                ) {
                    runCatching { nativeFirstDirectoryEntry(path) }.getOrNull()
                } else null
                val directoryAttempted = label == "u:object_r:tmpfs:s0" &&
                    DuckDetectorNativeLibrary.isLoaded
                AppZygoteMountPath(
                    path = path,
                    attempted = true,
                    available = label != null || mount != null,
                    errno = (context.exceptionOrNull() as? ErrnoException)?.errno,
                    label = label,
                    directoryAttempted = directoryAttempted,
                    directoryResultAvailable = dirCode != null,
                    directoryFirstEntry = dirCode?.takeIf { it >= 0 }?.let { it > 0 },
                    directoryErrno = dirCode?.takeIf { it < 0 }?.let { -it },
                    mountId = mount?.id, mountFs = mount?.fs, mountSource = mount?.source,
                )
            }
        return AppZygoteMountSnapshot(
            attempted = true,
            available = attr.isSuccess || mounts.isNotEmpty() || paths.any { it.available },
            attrAttempted = true,
            attrCtimeDeltaNs = elapsed,
            attrErrno = (attr.exceptionOrNull() as? ErrnoException)?.errno,
            mountInfoAvailable = mountComplete && mounts.isNotEmpty(),
            mountInfoErrno = (table.exceptionOrNull() as? ErrnoException)?.errno,
            paths = paths,
            mountGapMarkers = mountText?.takeIf { mountComplete }
                ?.let(AppZygoteMountInfo::gapMarkers).orEmpty(),
            failureReason = when {
                table.isSuccess && !mountComplete -> "App Zygote mountinfo truncated; mount IDs are unverified"
                attr.isFailure && table.isFailure && paths.none { it.available } ->
                    "App Zygote procfs, mountinfo and xattrs unavailable"
                else -> null
            },
        )
    }
}
