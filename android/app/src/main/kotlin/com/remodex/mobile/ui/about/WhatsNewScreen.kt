package com.remodex.mobile.ui.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.remodex.mobile.R
import com.remodex.mobile.ui.theme.remodexScreenTopAppBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onNavigateBack)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.whats_new_title)) },
                colors = remodexScreenTopAppBarColors(),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.whats_new_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LocalInfoSection(
                title = stringResource(R.string.whats_new_beta_title),
                body = stringResource(R.string.whats_new_item_beta),
            )
            LocalInfoSection(
                title = stringResource(R.string.whats_new_scroll_title),
                body = stringResource(R.string.whats_new_item_scroll),
            )
            LocalInfoSection(
                title = stringResource(R.string.whats_new_streaming_title),
                body = stringResource(R.string.whats_new_item_streaming),
            )
            LocalInfoSection(
                title = stringResource(R.string.whats_new_repo_title),
                body = stringResource(R.string.whats_new_item_repo),
            )
            LocalInfoSection(
                title = stringResource(R.string.whats_new_bridge_title),
                body = stringResource(R.string.whats_new_item_bridge),
            )
            LocalInfoSection(
                title = stringResource(R.string.whats_new_composer_git_title),
                body = stringResource(R.string.whats_new_composer_git_item),
            )
            LocalInfoSection(
                title = stringResource(R.string.whats_new_smart_scroll_title),
                body = stringResource(R.string.whats_new_smart_scroll_item),
            )
            LocalInfoSection(
                title = stringResource(R.string.whats_new_sidebar_settings_title),
                body = stringResource(R.string.whats_new_sidebar_settings_item),
            )
        }
    }
}
