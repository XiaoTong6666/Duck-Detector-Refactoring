/*
 * Copyright 2026 Duck Apps Contributor
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package com.eltavine.duckdetector.capability.selinuxpolicy.data

import com.eltavine.duckdetector.core.native.NativePayloadCodec

/** Data captured before an isolated service forks; not a live scan of the service. */
public data class AppZygoteMountSnapshot(
    val attempted: Boolean = false,
    val available: Boolean = false,
    val carrierVerified: Boolean = false,
    val attrAttempted: Boolean = false,
    val attrCtimeDeltaNs: Long? = null,
    val attrErrno: Int? = null,
    val mountInfoAvailable: Boolean = false,
    val mountInfoErrno: Int? = null,
    val paths: List<AppZygoteMountPath> = emptyList(),
    val mountGapMarkers: List<String> = emptyList(),
    val failureReason: String? = null,
)

public data class AppZygoteMountPath(
    val path: String,
    val attempted: Boolean,
    val available: Boolean,
    val errno: Int? = null,
    val label: String? = null,
    val directoryAttempted: Boolean = false,
    val directoryResultAvailable: Boolean = false,
    val directoryFirstEntry: Boolean? = null,
    val directoryErrno: Int? = null,
    val mountId: Int? = null,
    val mountFs: String? = null,
    val mountSource: String? = null,
)

/** Comparison across namespaces is supporting evidence, not proof that mount IDs must match. */
public data class AppZygoteMountIdComparison(
    val path: String,
    val appZygoteId: Int?,
    val mainId: Int?,
    val matches: Boolean?,
)

public object AppZygoteMountInfo {
    public data class Entry(val id: Int, val path: String, val fs: String, val source: String)

    public fun parse(contents: String): Map<String, Entry> = buildMap {
        for (line in contents.lineSequence()) {
            if (line.length > 8192) continue
            val chunks = line.split(" - ", limit = 2)
            if (chunks.size != 2) continue
            val prefix = chunks[0].split(' ').filter(String::isNotBlank)
            val suffix = chunks[1].split(' ').filter(String::isNotBlank)
            if (prefix.size < 5 || suffix.size < 2) continue
            val id = prefix[0].toIntOrNull() ?: continue
            // Keep exact mount points and names. No substring matching of mountinfo.
            val path = prefix[4]
            if (path in setOf("/mnt", "/mnt/obb", "/mnt/asec", "/data", "/apex/com.android.art")) {
                put(path, Entry(id, path, suffix[0], suffix[1]))
            }
        }
    }

    public fun compare(
        snapshot: AppZygoteMountSnapshot,
        current: Map<String, Entry>,
    ): List<AppZygoteMountIdComparison> = snapshot.paths.map { old ->
        val now = current[old.path]
        // A missing path / missing ID is unknown, not an ID mismatch.
        AppZygoteMountIdComparison(
            old.path, old.mountId, now?.id,
            if (old.mountId != null && now != null) old.mountId == now.id else null,
        )
    }

    /** The same two narrow mount-ID windows as NativeTest, for cross-process diagnostics only.
     * Missing IDs can be caused by ordinary namespace/mount churn, so callers must not convert
     * these markers into a root verdict without device-specific normal controls.
     */
    public fun gapMarkers(contents: String): List<String> {
        val records = contents.lineSequence().mapNotNull { line ->
            val words = line.substringBefore(" - ").split(' ').filter(String::isNotBlank)
            if (words.size >= 5) words[0].toIntOrNull()?.let { it to words[4] } else null
        }.toList()
        return buildList {
            records.forEachIndexed { index, (id, path) ->
                if (path == "/apex/com.android.art" && index + 1 < records.size &&
                    records[index + 1].first > id + 1
                ) {
                    add("apex_gap:${records[index + 1].first - id - 1}")
                }
                if (path == "/data_mirror") {
                    var previousId = id
                    var missing = 0
                    var reachedUserBoundary = false
                    for (candidate in records.subList(0, index).asReversed()) {
                        if (id - candidate.first >= 10) break
                        if (candidate.first < previousId) missing += previousId - candidate.first - 1
                        previousId = candidate.first
                        if (candidate.second.startsWith("/data/user")) {
                            reachedUserBoundary = true
                            break
                        }
                    }
                    if (reachedUserBoundary && missing >= 3) add("data_mirror_gap:$missing")
                }
            }
        }
    }
}

internal object AppZygoteMountPayloadCodec {
    private fun e(v: String?): String = NativePayloadCodec.encodeValue(v.orEmpty())
    private fun d(v: String): String = NativePayloadCodec.decodeValue(v)
    private fun b(v: Boolean): String = if (v) "1" else "0"
    private fun bn(v: Boolean?): String = when (v) { true -> "1"; false -> "0"; null -> "-" }
    private fun n(v: Int?): String = v?.toString() ?: "-"
    private fun flag(v: String?): Boolean = v == "1"
    private fun nullableFlag(v: String?): Boolean? = when (v) { "1" -> true; "0" -> false; else -> null }
    private fun number(v: String?): Int? = v?.toIntOrNull()

    fun encode(snapshot: AppZygoteMountSnapshot): String = buildString {
        append("VERSION=1\n")
        append("ATTEMPTED=").append(b(snapshot.attempted)).append('\n')
        append("AVAILABLE=").append(b(snapshot.available)).append('\n')
        append("CARRIER_VERIFIED=").append(b(snapshot.carrierVerified)).append('\n')
        append("ATTR_ATTEMPTED=").append(b(snapshot.attrAttempted)).append('\n')
        append("ATTR_CTIME_DELTA_NS=").append(snapshot.attrCtimeDeltaNs ?: "-").append('\n')
        append("ATTR_ERRNO=").append(n(snapshot.attrErrno)).append('\n')
        append("MOUNTINFO_AVAILABLE=").append(b(snapshot.mountInfoAvailable)).append('\n')
        append("MOUNTINFO_ERRNO=").append(n(snapshot.mountInfoErrno)).append('\n')
        append("FAILURE_REASON=").append(e(snapshot.failureReason)).append('\n')
        snapshot.paths.forEach { item ->
            append("PATH=")
            append(listOf(
                e(item.path), b(item.attempted), b(item.available), n(item.errno),
                e(item.label), bn(item.directoryFirstEntry), n(item.directoryErrno),
                n(item.mountId), e(item.mountFs), e(item.mountSource),
                b(item.directoryAttempted),
                b(item.directoryResultAvailable),
            ).joinToString("\t"))
            append('\n')
        }
        snapshot.mountGapMarkers.forEach { append("GAP=").append(e(it)).append('\n') }
    }

    fun decode(raw: String): AppZygoteMountSnapshot {
        if (raw.isBlank()) return AppZygoteMountSnapshot()
        val values = mutableMapOf<String, String>()
        val paths = mutableListOf<AppZygoteMountPath>()
        val gaps = mutableListOf<String>()
        for (line in raw.lineSequence()) {
            val key = line.substringBefore('=')
            val value = line.substringAfter('=', "")
            if (key == "GAP") {
                gaps += d(value)
            } else if (key == "PATH") {
                val p = value.split('\t')
                if (p.size !in 10..12) continue
                paths += AppZygoteMountPath(
                    path = d(p[0]), attempted = flag(p[1]), available = flag(p[2]),
                    errno = number(p[3]), label = d(p[4]).ifEmpty { null },
                    directoryAttempted = p.size >= 11 && flag(p[10]),
                    directoryResultAvailable = p.size == 12 && flag(p[11]),
                    directoryFirstEntry = nullableFlag(p[5]), directoryErrno = number(p[6]),
                    mountId = number(p[7]), mountFs = d(p[8]).ifEmpty { null },
                    mountSource = d(p[9]).ifEmpty { null },
                )
            } else {
                values[key] = value
            }
        }
        if (values["VERSION"] != "1") return AppZygoteMountSnapshot(
            failureReason = "Unsupported App Zygote mount payload version.",
        )
        return AppZygoteMountSnapshot(
            attempted = flag(values["ATTEMPTED"]),
            available = flag(values["AVAILABLE"]),
            carrierVerified = flag(values["CARRIER_VERIFIED"]),
            attrAttempted = flag(values["ATTR_ATTEMPTED"]),
            attrCtimeDeltaNs = values["ATTR_CTIME_DELTA_NS"]?.toLongOrNull(),
            attrErrno = number(values["ATTR_ERRNO"]),
            mountInfoAvailable = flag(values["MOUNTINFO_AVAILABLE"]),
            mountInfoErrno = number(values["MOUNTINFO_ERRNO"]),
            paths = paths,
            mountGapMarkers = gaps,
            failureReason = d(values["FAILURE_REASON"].orEmpty()).ifEmpty { null },
        )
    }
}
