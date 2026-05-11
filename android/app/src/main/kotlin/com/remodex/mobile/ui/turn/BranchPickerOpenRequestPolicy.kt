package com.remodex.mobile.ui.turn

internal enum class BranchPickerCloseCause {
    UserDismissed,
    BranchSelected,
    BranchCreated,
    StateInvalidated,
}

internal fun shouldConsumeBranchPickerOpenRequest(cause: BranchPickerCloseCause): Boolean =
    cause != BranchPickerCloseCause.StateInvalidated
