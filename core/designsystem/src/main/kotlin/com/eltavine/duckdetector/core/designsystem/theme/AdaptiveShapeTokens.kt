package com.eltavine.duckdetector.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Shape
import io.github.xiaotong6666.uihelper.mode.LocalUiMode
import io.github.xiaotong6666.uihelper.mode.UiMode

/** Resolve native per-skin shapes without applying MIUIX continuous corners to Material UI. */
public object AdaptiveShapeTokens {
    public val CornerMedium: Shape
        @Composable @ReadOnlyComposable get() =
            if (LocalUiMode.current == UiMode.Miuix) ShapeTokens.CornerMedium else MaterialTheme.shapes.medium

    public val CornerLarge: Shape
        @Composable @ReadOnlyComposable get() =
            if (LocalUiMode.current == UiMode.Miuix) ShapeTokens.CornerLarge else MaterialTheme.shapes.large

    public val CornerExtraLarge: Shape
        @Composable @ReadOnlyComposable get() =
            if (LocalUiMode.current == UiMode.Miuix) ShapeTokens.CornerExtraLarge else MaterialTheme.shapes.extraLarge
}
