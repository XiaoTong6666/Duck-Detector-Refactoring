/*
 * Copyright 2026 Duck Apps Contributor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.eltavine.duckdetector.features.update.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Source
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.eltavine.duckdetector.features.update.domain.AvailableNightlyUpdate
import io.github.xiaotong6666.uihelper.dialog.UpdatePromptChange
import io.github.xiaotong6666.uihelper.dialog.UpdatePromptDialog
import io.github.xiaotong6666.uihelper.dialog.UpdatePromptMetadata

@Composable
fun NightlyUpdateDialog(
    show: Boolean,
    currentVersionName: String,
    update: AvailableNightlyUpdate,
    downloadEnabled: Boolean,
    onDismiss: () -> Unit,
    onViewChanges: () -> Unit,
    onDownload: () -> Unit,
    onDismissFinished: () -> Unit = {},
) {
    UpdatePromptDialog(
        show = show,
        title = stringResource(R.string.update_dialog_title),
        summary = stringResource(
            R.string.update_version_change,
            currentVersionName,
            update.manifest.versionName,
        ),
        metadata = listOf(
            UpdatePromptMetadata(
                label = stringResource(R.string.update_branch_hash_label),
                value = "${update.manifest.branch} · ${update.manifest.commit.sha.take(8)}",
                icon = Icons.Rounded.Source,
                monospace = true,
            ),
            UpdatePromptMetadata(
                label = stringResource(R.string.update_author_label),
                value = update.manifest.commit.authorName,
                icon = Icons.Rounded.AccountCircle,
            ),
            UpdatePromptMetadata(
                label = stringResource(R.string.update_time_label),
                value = formatUpdateTime(update.manifest.builtAtUtc),
                icon = Icons.Rounded.Schedule,
            ),
        ),
        changesTitle = stringResource(R.string.update_changelog_title),
        changes = update.changelog.map { commit ->
            UpdatePromptChange(
                title = commit.subject,
                reference = commit.sha.take(8),
            )
        },
        moreChangesLabel = when (val count = update.remainingCommitCount) {
            null -> stringResource(R.string.update_remaining_commits_unknown)
            in 1..Int.MAX_VALUE -> pluralStringResource(R.plurals.update_remaining_commits, count, count)
            else -> null
        },
        viewAllLabel = stringResource(R.string.update_view_changes),
        onViewAll = onViewChanges,
        dismissLabel = stringResource(R.string.update_later),
        confirmLabel = stringResource(R.string.update_download),
        confirmEnabled = downloadEnabled,
        onDismiss = onDismiss,
        onConfirm = onDownload,
        onDismissFinished = onDismissFinished,
    )
}
