package com.remodex.mobile.data

internal sealed interface ThreadTurnRecoveryAction {
    data class Running(val turnId: String) : ThreadTurnRecoveryAction
    data object ProtectedFallback : ThreadTurnRecoveryAction
    data object Idle : ThreadTurnRecoveryAction
}

internal object ThreadTurnRecovery {
    fun actionFor(snapshot: ThreadTurnInterruptSnapshot): ThreadTurnRecoveryAction =
        when {
            snapshot.interruptibleTurnId != null -> ThreadTurnRecoveryAction.Running(snapshot.interruptibleTurnId)
            snapshot.hasInterruptibleTurnWithoutId -> ThreadTurnRecoveryAction.ProtectedFallback
            else -> ThreadTurnRecoveryAction.Idle
        }
}
