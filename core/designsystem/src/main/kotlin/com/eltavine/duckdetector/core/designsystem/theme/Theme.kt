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

package com.eltavine.duckdetector.core.designsystem.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.rememberPlatformOverscrollFactory
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import io.github.xiaotong6666.uihelper.mode.AdaptiveTheme
import io.github.xiaotong6666.uihelper.mode.UiMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.MiuixIndication

/** Distinct native MIUIX and dynamic Material Expressive skins over the same UI state. */
@Composable
public fun DuckDetectorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    uiMode: UiMode = UiMode.Miuix,
    content: @Composable () -> Unit,
) {
    val baseScheme = if (darkTheme) DarkMonochromeScheme else LightMonochromeScheme
    val view = LocalView.current
    val context = LocalContext.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars =
                !darkTheme
        }
    }

    val appMaterialScheme = if (uiMode == UiMode.Material) {
        val seed = if (darkTheme) {
            dynamicDarkColorScheme(context).primary
        } else {
            dynamicLightColorScheme(context).primary
        }
        rememberDynamicColorScheme(
            seedColor = seed,
            isDark = darkTheme,
            style = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
        )
    } else null
    // The MIUIX theme remains installed for its native controls even in Material mode.
    // Explicitly restore the Android platform factory there: on Android 12+ this
    // stretches the content, rather than inheriting a MIUIX spring overscroll.
    val platformOverscrollFactory = rememberPlatformOverscrollFactory()
    val inheritedOverscrollFactory = LocalOverscrollFactory.current
    AdaptiveTheme(uiMode = uiMode, darkTheme = darkTheme, materialColorScheme = appMaterialScheme) {
        // Keep MIUIX's own colors. Material uses the M3E dynamic-color pipeline
        // when available and uses the existing palette as the Android 10-11 fallback.
        val colorScheme = if (uiMode == UiMode.Miuix) {
            val miuix = MiuixTheme.colorScheme
            baseScheme.copy(
                primary = miuix.primary,
                onPrimary = miuix.onPrimary,
                primaryContainer = miuix.primaryContainer,
                onPrimaryContainer = miuix.onPrimaryContainer,
                inversePrimary = miuix.primaryVariant,
                secondary = miuix.secondary,
                onSecondary = miuix.onSecondary,
                secondaryContainer = miuix.secondaryContainer,
                onSecondaryContainer = miuix.onSecondaryContainer,
                tertiary = miuix.onTertiaryContainer,
                onTertiary = miuix.tertiaryContainer,
                tertiaryContainer = miuix.tertiaryContainer,
                onTertiaryContainer = miuix.onTertiaryContainer,
                error = miuix.error,
                onError = miuix.onError,
                errorContainer = miuix.errorContainer,
                onErrorContainer = miuix.onErrorContainer,
                background = miuix.background,
                onBackground = miuix.onBackground,
                surface = miuix.surface,
                onSurface = miuix.onSurface,
                surfaceVariant = miuix.surfaceVariant,
                onSurfaceVariant = miuix.onSurfaceVariantSummary,
                surfaceTint = miuix.primary,
                inverseSurface = miuix.onSurface,
                inverseOnSurface = miuix.surface,
                outline = miuix.outline,
                outlineVariant = miuix.dividerLine,
                scrim = miuix.windowDimming,
                surfaceBright = miuix.surfaceContainer,
                surfaceDim = miuix.surface,
                surfaceContainerLowest = miuix.surface,
                surfaceContainerLow = miuix.surfaceContainer,
                surfaceContainer = miuix.surfaceContainer,
                surfaceContainerHigh = miuix.surfaceContainerHigh,
                surfaceContainerHighest = miuix.surfaceContainerHighest,
            )
        } else requireNotNull(appMaterialScheme)
        val palette = remember(colorScheme, darkTheme, uiMode) {
            if (uiMode == UiMode.Miuix) duckPalette(colorScheme, darkTheme)
            else expressiveDuckPalette(colorScheme, darkTheme)
        }

        // A single stable theme subtree is essential: switching between two separate theme
        // branches disposes DuckDetectorApp and can restart scans / reset the selected tab.
        // MIUIX owns its native components, colors and Folme motion; this Material layer is
        // only for the remaining Compose Material components and cross-skin fallbacks.
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            typography = if (uiMode == UiMode.Material) ExpressiveTypography else Typography,
            shapes = if (uiMode == UiMode.Material) ExpressiveShapes else Shapes,
            motionScheme = if (uiMode == UiMode.Material) MotionScheme.expressive() else MotionScheme.standard(),
        ) {
            val materialIndication = LocalIndication.current
            val indicationColor = MiuixTheme.colorScheme.onBackground
            val miuixIndication = remember(indicationColor) {
                MiuixIndication(color = indicationColor)
            }
            CompositionLocalProvider(
                LocalDuckPalette provides palette,
                LocalIndication provides if (uiMode == UiMode.Miuix) miuixIndication else materialIndication,
                LocalOverscrollFactory provides if (uiMode == UiMode.Material) platformOverscrollFactory else inheritedOverscrollFactory,
                content = content,
            )
        }
    }
}
