/*
 * Copyright 2026 Duck Apps Contributor
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

package com.eltavine.duckdetector.features.dashboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eltavine.duckdetector.core.designsystem.components.DuckPanel
import com.eltavine.duckdetector.core.designsystem.theme.DuckTheme
import com.eltavine.duckdetector.core.designsystem.theme.DuckTypography
import com.eltavine.duckdetector.core.evidence.DetectionSeverity
import com.eltavine.duckdetector.core.ui.LocalAppBuildInfo
import com.eltavine.duckdetector.core.ui.components.DetectorHairline
import com.eltavine.duckdetector.core.ui.components.WrapSafeText
import com.eltavine.duckdetector.core.ui.presentation.rememberStatusAppearance
import com.eltavine.duckdetector.core.ui.presentation.formatBuildTimeUtc
import com.eltavine.duckdetector.features.dashboard.presentation.model.DashboardOverviewMetricModel
import com.eltavine.duckdetector.features.dashboard.presentation.model.DashboardOverviewModel
import io.github.xiaotong6666.uihelper.adaptive.AdaptiveContent
import io.github.xiaotong6666.uihelper.adaptive.AdaptiveStatusHeroCard
import io.github.xiaotong6666.uihelper.adaptive.AdaptiveVerticalDivider
import io.github.xiaotong6666.uihelper.adaptive.StatusHeroTone
import io.github.xiaotong6666.uihelper.adaptive.adaptiveValue

@Composable
internal fun DashboardOverviewCard(
    model: DashboardOverviewModel,
    onExportReport: () -> Unit,
) {
    val appearance = rememberStatusAppearance(model.status)
    val buildInfo = LocalAppBuildInfo.current
    val versionLabel = "${buildInfo.versionName} (${buildInfo.versionCode})"
    val buildTimeLabel = stringResource(R.string.dashboard_build_time_utc, formatBuildTimeUtc(buildInfo.buildTimeUtc))
    val tone = model.status.severity
    val heroTone = when (tone) {
        DetectionSeverity.DANGER -> StatusHeroTone.Danger
        DetectionSeverity.WARNING -> StatusHeroTone.Warning
        DetectionSeverity.ALL_CLEAR -> StatusHeroTone.Success
        DetectionSeverity.INFO -> StatusHeroTone.Neutral
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdaptiveStatusHeroCard(
            title = model.headline,
            summary = model.summary,
            icon = when (tone) {
                DetectionSeverity.DANGER -> Icons.Rounded.ErrorOutline
                DetectionSeverity.WARNING -> Icons.Rounded.WarningAmber
                DetectionSeverity.ALL_CLEAR -> Icons.Rounded.CheckCircleOutline
                DetectionSeverity.INFO -> Icons.Rounded.Info
            },
            tone = heroTone,
            accentColor = appearance.iconTint,
            onClick = onExportReport,
            metaContent = { contentColor ->
                ReportHeroMetadata(
                    versionLabel = versionLabel,
                    buildTimeLabel = buildTimeLabel,
                    contentColor = contentColor,
                )
            },
            actionContent = { contentColor ->
                ReportHeroAction(contentColor = contentColor)
            },
        )
        DuckPanel(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OverviewTitle(model = model)
            DetectorHairline()
            OverviewMetrics(metrics = model.metrics)
        }
    }
}

@Composable
private fun ReportHeroMetadata(
    versionLabel: String,
    buildTimeLabel: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        ReportMetaLine(icon = Icons.Rounded.Badge, label = versionLabel, color = contentColor.copy(alpha = 0.82f))
        ReportMetaLine(icon = Icons.Rounded.Schedule, label = buildTimeLabel, color = contentColor.copy(alpha = 0.72f))
    }
}

@Composable
private fun ReportHeroAction(contentColor: Color) {
    val endDividerPadding = adaptiveValue(material = 20.dp, miuix = 108.dp)
    val endContentPadding = adaptiveValue(material = 20.dp, miuix = 106.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            // The lower-right illustration occupies the rest of the MIUIX hero.
            // Do not draw a hairline through the glyph.
            modifier = Modifier.padding(start = 20.dp, end = endDividerPadding),
            color = contentColor.copy(alpha = 0.16f),
            thickness = Dp.Hairline,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(start = 20.dp, end = endContentPadding, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(19.dp), tint = contentColor)
            WrapSafeText(
                text = stringResource(R.string.dashboard_export_report_hint),
                modifier = Modifier.weight(1f),
                style = DuckTypography.ReportAction,
                color = contentColor,
            )
            AdaptiveContent(
                material = {
                    Icon(
                        Icons.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = contentColor.copy(alpha = 0.78f),
                    )
                },
                miuix = {},
            )
        }
    }
}

@Composable
private fun ReportMetaLine(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
        WrapSafeText(
            text = label,
            modifier = Modifier.weight(1f),
            style = DuckTypography.ReportMeta,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The title is one line, or the scan's completion time over its duration. */
@Composable
private fun OverviewTitle(model: DashboardOverviewModel) {
    val lines = model.title.lines().filter { it.isNotBlank() }
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (model.showTitleIcon) {
            Icon(
                imageVector = Icons.Outlined.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(14.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            lines.forEach { line ->
                WrapSafeText(
                    text = line,
                    style = DuckTypography.Footnote,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OverviewMetrics(
    metrics: List<DashboardOverviewMetricModel>,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        metrics.forEachIndexed { index, metric ->
            if (index > 0) {
                AdaptiveVerticalDivider(
                    modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp),
                    thickness = Dp.Hairline,
                    materialColor = DuckTheme.palette.separator,
                )
            }
            OverviewMetric(metric = metric, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun OverviewMetric(
    metric: DashboardOverviewMetricModel,
    modifier: Modifier = Modifier,
) {
    val appearance = rememberStatusAppearance(metric.status)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        WrapSafeText(
            text = metric.value,
            style = DuckTypography.MetricNumeral,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(color = appearance.iconTint, shape = CircleShape),
            )
            WrapSafeText(
                text = metric.label,
                style = DuckTypography.PanelCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
