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

package com.eltavine.duckdetector.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import io.github.xiaotong6666.uihelper.adaptive.AdaptiveIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.eltavine.duckdetector.core.designsystem.theme.DuckTheme
import com.eltavine.duckdetector.core.designsystem.components.DuckPanel
import com.eltavine.duckdetector.core.designsystem.theme.DuckTypography
import com.eltavine.duckdetector.core.designsystem.theme.miuixInsetSurfaceColor
import com.eltavine.duckdetector.core.designsystem.theme.MotionTokens
import com.eltavine.duckdetector.core.evidence.DetectorStatus
import com.eltavine.duckdetector.core.ui.R
import com.eltavine.duckdetector.core.ui.presentation.StatusAppearance
import com.eltavine.duckdetector.core.ui.presentation.rememberStatusAppearance
import io.github.xiaotong6666.uihelper.adaptive.AdaptiveContent
import io.github.xiaotong6666.uihelper.adaptive.AdaptiveExpandIcon
import io.github.xiaotong6666.uihelper.adaptive.adaptiveSecondaryTextColor
import io.github.xiaotong6666.uihelper.adaptive.adaptiveSurfaceBackground
import io.github.xiaotong6666.uihelper.adaptive.adaptiveSurfaceClip
import io.github.xiaotong6666.uihelper.adaptive.adaptiveValue
import top.yukonga.miuix.kmp.anim.folmeSpring
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A detector's card. Collapsed, it shows only what a reader scanning the dashboard needs: the
 * detector, its status and the verdict. Expanding it adds the [subtitle] describing what was
 * checked, the [headerFacts], the [summary], the [content] and the [footerActions].
 */
@Composable
public fun DetectorCardFrame(
    title: String,
    subtitle: String,
    status: DetectorStatus,
    verdict: String,
    summary: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    miuixLeadingIcon: ImageVector? = null,
    miuixLeadingPainter: Painter? = null,
    leadingBadgeIcon: ImageVector? = null,
    leadingBadgeStatus: DetectorStatus? = null,
    leadingBadgeContentDescription: String? = null,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    headerFacts: @Composable ColumnScope.() -> Unit = {},
    collapsedOverview: @Composable ColumnScope.() -> Unit = {},
    footerActions: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val appearance = rememberStatusAppearance(status)
    val leadingBadgeAppearance = rememberStatusAppearance(leadingBadgeStatus ?: status)
    var internalExpanded by rememberSaveable(title) { mutableStateOf(false) }
    val isExpanded = expanded ?: internalExpanded
    val toggleDescription = stringResource(
        if (isExpanded) R.string.card_collapse else R.string.card_expand,
    )
    val haptics = LocalHapticFeedback.current
    val headerInteraction = remember { MutableInteractionSource() }
    val useMiuixToggleHaptics = adaptiveValue(material = false, miuix = true)
    val toggleExpandedAction: () -> Unit = {
        val next = !isExpanded
        if (expanded == null) internalExpanded = next
        onExpandedChange?.invoke(next)
    }
    val toggleExpanded: () -> Unit = {
        if (useMiuixToggleHaptics) {
            haptics.performHapticFeedback(
                if (!isExpanded) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
            )
        }
        toggleExpandedAction()
    }

    val cardContent: @Composable ColumnScope.() -> Unit = {
        AdaptiveContent(
            miuix = {
                val verdictColor by animateColorAsState(
                    targetValue = if (isExpanded) {
                        MiuixTheme.colorScheme.onSurface
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                    animationSpec = tween(durationMillis = 260),
                    label = "miuixDetectorVerdictColor",
                )
                BasicComponent(
                    startAction = {
                        MiuixCardGlyph(
                            icon = miuixLeadingIcon ?: leadingIcon,
                            painter = miuixLeadingPainter,
                            badgeIcon = leadingBadgeIcon,
                            badgeAppearance = leadingBadgeAppearance,
                            badgeContentDescription = leadingBadgeContentDescription,
                        )
                    },
                    endActions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CompactStatusBadge(status = status)
                            AdaptiveExpandIcon(expanded = isExpanded, miuixSize = 16.dp)
                        }
                    },
                    // The outer card handles taps while collapsed; only the header collapses it.
                    onClick = if (isExpanded) toggleExpanded else null,
                    onClickLabel = toggleDescription,
                    role = Role.Button,
                    interactionSource = headerInteraction,
                ) {
                    WrapSafeText(
                        text = title,
                        style = MiuixTheme.textStyles.headline1.copy(fontWeight = FontWeight.Medium),
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    if (verdict.isNotBlank()) {
                        AnimatedVisibility(
                            visible = !isExpanded,
                            enter = expandVertically(
                                animationSpec = tween(durationMillis = 280, delayMillis = 90),
                                expandFrom = Alignment.Top,
                            ) + fadeIn(
                                animationSpec = tween(durationMillis = 150, delayMillis = 90),
                            ),
                            exit = shrinkVertically(
                                animationSpec = tween(durationMillis = 280),
                                shrinkTowards = Alignment.Top,
                            ) + fadeOut(animationSpec = tween(durationMillis = 150)),
                        ) {
                            WrapSafeText(
                                text = verdict,
                                style = MiuixTheme.textStyles.body2,
                                color = verdictColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                if (!isExpanded) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        content = collapsedOverview,
                    )
                }
            },
            material = {
                // Collapsed: the native Card owns the entire hit area and indication, including its
                // padding and collapsed overview. Expanded: only the flush-to-edge header can collapse.
                // Do not clip the inset glyph row: its small badge deliberately extends past the tile.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .indication(headerInteraction, LocalIndication.current)
                        .then(
                            if (!isExpanded) Modifier else Modifier
                                .clickable(
                                    interactionSource = headerInteraction,
                                    indication = null,
                                    role = Role.Button,
                                    onClickLabel = toggleDescription,
                                    onClick = toggleExpanded,
                                ),
                        )
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = adaptiveValue(
                                material = if (isExpanded) 12.dp else 28.dp,
                                miuix = 18.dp,
                            ),
                        ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CardGlyph(
                            icon = leadingIcon,
                            appearance = appearance,
                            badgeIcon = leadingBadgeIcon,
                            badgeAppearance = leadingBadgeAppearance,
                            badgeContentDescription = leadingBadgeContentDescription,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            WrapSafeText(
                                text = title,
                                style = DuckTypography.PanelTitle,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (verdict.isNotBlank()) {
                                AnimatedVisibility(
                                    visible = !isExpanded,
                                    enter = expandVertically(
                                        animationSpec = MotionTokens.smoothSpring(IntSize.VisibilityThreshold),
                                        expandFrom = Alignment.Top,
                                    ) + fadeIn(
                                        animationSpec = tween(durationMillis = 150, delayMillis = 120),
                                    ),
                                    exit = shrinkVertically(
                                        animationSpec = MotionTokens.smoothSpring(IntSize.VisibilityThreshold),
                                        shrinkTowards = Alignment.Top,
                                    ) + fadeOut(animationSpec = tween(durationMillis = 120)),
                                ) {
                                    WrapSafeText(
                                        text = verdict,
                                        style = DuckTypography.PanelSupporting,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        CompactStatusBadge(status = status)
                        AdaptiveExpandIcon(expanded = isExpanded)
                    }
                }
            },
        )

        AdaptiveContent(
            miuix = {
                AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(
                animationSpec = folmeSpring(
                    damping = 1.0f,
                    response = 0.36f,
                    visibilityThreshold = IntSize.VisibilityThreshold,
                ),
                expandFrom = Alignment.Top,
            ) + fadeIn(animationSpec = folmeSpring(damping = 1.0f, response = 0.30f)),
            exit = shrinkVertically(
                animationSpec = folmeSpring(
                    damping = 1.0f,
                    response = 0.30f,
                    visibilityThreshold = IntSize.VisibilityThreshold,
                ),
                shrinkTowards = Alignment.Top,
            ) + fadeOut(animationSpec = folmeSpring(damping = 1.0f, response = 0.23f)),
        ) {
            // Section rows stay folded by default, so the outer height transition measures
            // short summaries, not all of TEE's attestation values at once.
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (verdict.isNotBlank()) {
                        WrapSafeText(
                            text = verdict,
                            modifier = Modifier.fillMaxWidth(),
                            style = DuckTypography.PanelBody.copy(fontWeight = FontWeight.Medium),
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                    if (subtitle.isNotBlank()) {
                        WrapSafeText(
                            text = subtitle,
                            modifier = Modifier.fillMaxWidth(),
                            style = DuckTypography.PanelSupporting,
                            color = adaptiveSecondaryTextColor(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    headerFacts()
                    if (summary.isNotBlank()) {
                        WrapSafeText(
                            text = summary,
                            modifier = Modifier.fillMaxWidth(),
                            style = DuckTypography.PanelSupporting,
                            color = adaptiveSecondaryTextColor(),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .adaptiveSurfaceBackground(
                            materialColor = Color.Transparent,
                            materialShape = MaterialTheme.shapes.large,
                            miuixColor = miuixInsetSurfaceColor(),
                            miuixCornerRadius = 16.dp,
                        )
                        .adaptiveSurfaceClip(
                            materialShape = MaterialTheme.shapes.large,
                            miuixCornerRadius = 16.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    content = content,
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    content = footerActions,
                )
            }
        }

            },
            material = {
                // One state/size transition for the mutually exclusive Material overview and details.
                // Two independent AnimatedVisibility size animations cause the overview to release its
                // height while the details are still growing, producing a visible two-stage jump.
                AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
                (fadeIn(MotionTokens.FadeInOut) togetherWith fadeOut(MotionTokens.FadeInOut))
                    .using(
                        SizeTransform(clip = true) { _, _ ->
                            MotionTokens.smoothSpring(IntSize.VisibilityThreshold)
                        },
                    )
            },
            label = "materialDetectorBody",
        ) {
            if (it) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (verdict.isNotBlank()) {
                            WrapSafeText(
                                text = verdict,
                                modifier = Modifier.fillMaxWidth(),
                                style = DuckTypography.PanelBody.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        if (subtitle.isNotBlank()) {
                            WrapSafeText(
                                text = subtitle,
                                modifier = Modifier.fillMaxWidth(),
                                style = DuckTypography.PanelSupporting,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        headerFacts()
                        if (summary.isNotBlank()) {
                            WrapSafeText(
                                text = summary,
                                modifier = Modifier.fillMaxWidth(),
                                style = DuckTypography.PanelSupporting,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        content = content,
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        content = footerActions,
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    content = collapsedOverview,
                )
            }
                }
            },
        )

    }

    DuckPanel(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        onClick = if (isExpanded) null else toggleExpanded,
        materialShape = MaterialTheme.shapes.large,
        content = cardContent,
    )
}

@Composable
private fun MiuixCardGlyph(
    icon: ImageVector,
    painter: Painter?,
    badgeIcon: ImageVector?,
    badgeAppearance: StatusAppearance,
    badgeContentDescription: String?,
) {
    Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
        if (painter != null) {
            AdaptiveIcon(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MiuixTheme.colorScheme.onSurface,
            )
        } else {
            AdaptiveIcon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
        if (badgeIcon != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .size(14.dp)
                    .background(MiuixTheme.colorScheme.surfaceContainer, CircleShape)
                    .padding(1.dp),
                contentAlignment = Alignment.Center,
            ) {
                AdaptiveIcon(
                    imageVector = badgeIcon,
                    contentDescription = badgeContentDescription,
                    modifier = Modifier.size(12.dp),
                    tint = badgeAppearance.iconTint,
                )
            }
        }
    }
}

@Composable
private fun CardGlyph(
    icon: ImageVector,
    appearance: StatusAppearance,
    badgeIcon: ImageVector?,
    badgeAppearance: StatusAppearance,
    badgeContentDescription: String?,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .adaptiveSurfaceBackground(
                materialColor = MaterialTheme.colorScheme.secondaryContainer,
                materialShape = MaterialTheme.shapes.medium,
                miuixColor = miuixInsetSurfaceColor(),
                miuixCornerRadius = 14.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AdaptiveIcon(imageVector = icon, contentDescription = null, tint = appearance.iconTint)
        if (badgeIcon != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 5.dp, y = 5.dp)
                    .size(20.dp)
                    .background(color = DuckTheme.palette.groupedSurface, shape = CircleShape)
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                AdaptiveIcon(
                    imageVector = badgeIcon,
                    contentDescription = badgeContentDescription,
                    tint = badgeAppearance.iconTint,
                )
            }
        }
    }
}
