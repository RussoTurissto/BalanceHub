package com.github.russoturissto.buttonapp.balancehub.domain

/**
 * BalanceHub domain primitives.
 *
 * Amounts are stored in minor currency units (for example, kopecks or cents).
 * Provider integrations are deliberately kept outside the domain model.
 */
@JvmInline
value class MoneyMinor(val value: Long)

enum class AccountKind {
    MOBILE,
    BANK
}

enum class BalanceOrigin {
    PROVIDER,
    MANUAL
}

enum class ProviderId {
    MTS,
    TELE2,
    BANK_OTHER
}

data class AccountBalance(
    val amount: MoneyMinor,
    val currencyCode: String,
    val observedAtEpochMillis: Long,
    val origin: BalanceOrigin
) {
    init {
        require(currencyCode.matches(Regex("[A-Z]{3}"))) {
            "Currency code must be a three-letter ISO 4217 code"
        }
        require(observedAtEpochMillis >= 0L) {
            "Observation timestamp cannot be negative"
        }
    }
}

data class TrackedAccount(
    val id: String,
    val title: String,
    val kind: AccountKind,
    val providerId: ProviderId,
    val maskedIdentifier: String?,
    val balance: AccountBalance?,
    val isActive: Boolean = true
) {
    init {
        require(id.isNotBlank()) { "Account id cannot be blank" }
        require(title.isNotBlank()) { "Account title cannot be blank" }
    }
}

data class RecurringPayment(
    val id: String,
    val title: String,
    val amount: MoneyMinor,
    val currencyCode: String,
    /** Day of month, 1..31. The due date is clamped to the month's last day. */
    val billingDayOfMonth: Int,
    val reminderDaysBefore: Int = 3,
    val linkedAccountId: String? = null,
    val isEnabled: Boolean = true
) {
    init {
        require(id.isNotBlank()) { "Payment id cannot be blank" }
        require(title.isNotBlank()) { "Payment title cannot be blank" }
        require(currencyCode.matches(Regex("[A-Z]{3}"))) {
            "Currency code must be a three-letter ISO 4217 code"
        }
        require(billingDayOfMonth in 1..31) { "Billing day must be between 1 and 31" }
        require(reminderDaysBefore in 0..30) { "Reminder lead time must be between 0 and 30 days" }
    }
}

data class ProviderRefreshState(
    val isSupported: Boolean,
    val isAuthorized: Boolean,
    val lastAttemptAtEpochMillis: Long?,
    val lastSuccessAtEpochMillis: Long?,
    val errorCode: String? = null
)
