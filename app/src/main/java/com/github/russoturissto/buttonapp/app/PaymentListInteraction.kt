package com.github.russoturissto.buttonapp.app

/** Stateless transitions for expanding and collapsing the Payments list. */
internal object PaymentListInteraction {
    fun toggleSection(currentlyExpanded: Boolean): Boolean = !currentlyExpanded

    fun toggleRow(currentId: String?, tappedId: String): String? =
        if (currentId == tappedId) null else tappedId
}
