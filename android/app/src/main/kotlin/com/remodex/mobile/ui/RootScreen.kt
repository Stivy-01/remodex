package com.remodex.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.remodex.mobile.ui.home.RootPhase
import com.remodex.mobile.ui.home.RootViewModel
import com.remodex.mobile.ui.onboarding.OnboardingScreen
import com.remodex.mobile.ui.onboarding.QrScannerScreen
import com.remodex.mobile.ui.shell.MainShell

/**
 * App root: onboarding → QR pairing → main shell ([MainShell]).
 * Parity with [ContentView.swift](CodexMobile/CodexMobile/ContentView.swift) branching.
 */
@Composable
fun RootScreen(modifier: Modifier = Modifier) {
    val repository = LocalCodexRepository.current
    val viewModel: RootViewModel = viewModel(factory = RootViewModel.factory())
    val phase by viewModel.phase.collectAsStateWithLifecycle()

    when (phase) {
        RootPhase.Onboarding ->
            OnboardingScreen(
                onContinue = viewModel::finishOnboarding,
                modifier = modifier,
            )
        RootPhase.PairingScan ->
            QrScannerScreen(
                repository = repository,
                onBack = viewModel::closePairingScanner,
                onPairingComplete = viewModel::notifyPairingSuccess,
                modifier = modifier,
            )
        RootPhase.Main ->
            MainShell(
                viewModel = viewModel,
                onOpenPairingScanner = viewModel::openPairingScanner,
                modifier = modifier,
            )
    }
}
