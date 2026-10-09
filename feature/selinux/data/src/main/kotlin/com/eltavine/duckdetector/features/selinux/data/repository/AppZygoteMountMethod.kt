/*
 * Copyright 2026 Duck Apps Contributor
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.eltavine.duckdetector.features.selinux.data.repository

import com.eltavine.duckdetector.capability.selinuxpolicy.data.AppZygoteMountInfo
import com.eltavine.duckdetector.capability.selinuxpolicy.data.AppZygoteMountSnapshot
import com.eltavine.duckdetector.features.selinux.domain.SelinuxCheckResult
import java.io.File

internal fun buildAppZygoteMountMethod(
    snapshot: AppZygoteMountSnapshot,
    mainMountinfo: String? = runCatching {
        File("/proc/self/mountinfo").bufferedReader().use { reader ->
            val buffer = CharArray(8192)
            val contents = StringBuilder()
            while (true) {
                val count = reader.read(buffer)
                if (count == -1) break
                if (contents.length + count > 256 * 1024) return@use null
                contents.append(buffer, 0, count)
            }
            contents.toString()
        }
    }.getOrNull(),
): SelinuxCheckResult {
    val current = mainMountinfo?.let(AppZygoteMountInfo::parse).orEmpty()
    val comparisons = if (snapshot.mountInfoAvailable && mainMountinfo != null) {
        AppZygoteMountInfo.compare(snapshot, current)
    } else emptyList()
    val details = buildList {
        add("App zygote preload captured=${snapshot.attempted}")
        add("Carrier verified=${snapshot.carrierVerified}")
        add("Available=${snapshot.available}")
        add("attr/current ctime delta ns=${snapshot.attrCtimeDeltaNs ?: "unavailable"}; errno=${snapshot.attrErrno ?: "-"}")
        add("App zygote mountinfo available=${snapshot.mountInfoAvailable}; errno=${snapshot.mountInfoErrno ?: "-"}")
        snapshot.paths.forEach { path ->
            add(
                "${path.path}: label=${path.label ?: "unavailable"}; " +
                    "errno=${path.errno ?: "-"}; " +
                    "directory attempted=${path.directoryAttempted}; " +
                    "directory result available=${path.directoryResultAvailable}; " +
                    "first readdir entry=${path.directoryFirstEntry ?: "unavailable"}; " +
                    "dir errno=${path.directoryErrno ?: "-"}; " +
                    "app_zygote mount id=${path.mountId ?: "-"}; " +
                    "fs=${path.mountFs ?: "-"}; source=${path.mountSource ?: "-"}",
            )
        }
        comparisons.forEach {
            add("Cross-process mount ID ${it.path}: app_zygote=${it.appZygoteId ?: "-"}, " +
                "app=${it.mainId ?: "-"}, equal=${it.matches ?: "unknown"}")
        }
        add("App zygote mount ID gaps=${snapshot.mountGapMarkers.ifEmpty { listOf("none observed or unavailable") }}")
        add("Main process mount ID gaps=${mainMountinfo?.let(AppZygoteMountInfo::gapMarkers) ?: listOf("unavailable")}")
        add("Cross-namespace mount IDs may legitimately differ; no root verdict without stock controls.")
        snapshot.failureReason?.let(::add)
    }.joinToString("\n")
    return SelinuxCheckResult(
        method = "App zygote mount permissions and ID comparison",
        status = if (snapshot.attempted && snapshot.available && snapshot.carrierVerified) {
            "Info"
        } else {
            "Unavailable"
        },
        isSecure = null,
        permissionDenied = false,
        details = details,
    )
}
