package com.revyu.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.revyu.app.R
import com.revyu.app.core.preferences.AppSettings
import com.revyu.app.core.preferences.CatExpression
import com.revyu.app.core.preferences.ThemeMode
import com.revyu.app.core.preferences.getCatDrawableRes

/**
 * Global Top Header used across main app tabs (Home, Reviewer, Widgets, History, Settings).
 * Displays the screen's brand header image on the left (switching to dark mode variant when dark mode is enabled)
 * and the user's selected laying cat on the right, which clicks through to Settings.
 */
@Composable
fun RevyuTopHeader(
    modifier: Modifier = Modifier,
    @DrawableRes headerDrawableRes: Int = R.drawable.revyu_header,
    @DrawableRes headerDarkDrawableRes: Int? = null,
    onOpenSettings: () -> Unit = {},
    settings: AppSettings = AppSettings()
) {
    val isDark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val darkRes = headerDarkDrawableRes ?: when (headerDrawableRes) {
        R.drawable.revyu_header -> R.drawable.revyu_header_dm
        R.drawable.reviewer_header -> R.drawable.reviewer_header_dm
        R.drawable.widgets_header -> R.drawable.widgets_header_dm
        R.drawable.history_header -> R.drawable.history_header_dm
        R.drawable.settings_header -> R.drawable.settings_header_dm
        else -> headerDrawableRes
    }

    val imageRes = if (isDark) darkRes else headerDrawableRes

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = "REVYU",
            modifier = Modifier.height(38.dp),
            contentScale = ContentScale.Fit
        )
        val catDrawable = settings.accentColor.getCatDrawableRes(CatExpression.LAY)
        Image(
            painter = painterResource(id = catDrawable),
            contentDescription = "Settings",
            modifier = Modifier
                .height(48.dp)
                .clickable { onOpenSettings() },
            contentScale = ContentScale.Fit
        )
    }
}
