package com.remodex.mobile.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

/**
 * Matches the main shell top bar: slightly translucent background over content.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun remodexScreenTopAppBarColors() =
    TopAppBarDefaults.topAppBarColors(
        containerColor =
            MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
        scrolledContainerColor =
            MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
    )
