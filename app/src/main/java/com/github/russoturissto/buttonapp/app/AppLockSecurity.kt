package com.github.russoturissto.buttonapp.app

import android.content.Context
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * A private, hardware-backed HMAC verifies the four-digit app PIN.
 * No plaintext PIN, reversible PIN encryption, or offline-brute-forceable SHA hash
 * is stored in SharedPreferences. Android Keystore keys are non-exportable.
 */
internal object AppLockSecurity {
    private const val PREFS = "balancehub_lock"
    private const val PIN_KEY = "balancehub_lock_pin_hmac_v1"
    private const val FINGER_KEY = "balancehub_lock_fingerprint_v1"
    private const val SALT = "pin_salt"
    private const val VERIFIER = "pin_verifier"
    private const val ENABLED = "enabled"
    private const val FINGER_ENABLED = "finger_enabled"
    private const val FAILURES = "failed_attempts"
    private const val BLOCK_UNTIL = "block_until"
    private const val RELOCK = "relock"

    enum class PinResult { SUCCESS, WRONG, BLOCKED, ERROR }

    fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun enabled(context: Context): Boolean =
        supported() && prefs(context).getBoolean(ENABLED, false) && hasPin(context)

    fun hasPin(context: Context): Boolean = prefs(context).getString(SALT, null) != null &&
        prefs(context).getString(VERIFIER, null) != null

    fun relock(context: Context): String =
        prefs(context).getString(RELOCK, AppLockPolicy.IMMEDIATE) ?: AppLockPolicy.IMMEDIATE

    fun setRelock(context: Context, mode: String) {
        require(mode in listOf(AppLockPolicy.IMMEDIATE, AppLockPolicy.ONE_MINUTE,
            AppLockPolicy.FIVE_MINUTES, AppLockPolicy.DEVICE_LOCK))
        prefs(context).edit().putString(RELOCK, mode).apply()
    }

    private fun keystore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun hmacKey(): SecretKey {
        (keystore().getKey(PIN_KEY, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(PIN_KEY, KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build())
        return generator.generateKey()
    }

    private fun verifier(pin: String, salt: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey())
        mac.update(salt)
        return mac.doFinal(pin.toByteArray(Charsets.US_ASCII))
    }

    fun savePin(context: Context, pin: String): Boolean {
        if (!supported() || !AppLockPolicy.validPin(pin)) return false
        return try {
            val salt = ByteArray(24).also { SecureRandom().nextBytes(it) }
            val value = verifier(pin, salt)
            val encoder = android.util.Base64.NO_WRAP
            prefs(context).edit()
                .putString(SALT, android.util.Base64.encodeToString(salt, encoder))
                .putString(VERIFIER, android.util.Base64.encodeToString(value, encoder))
                .putBoolean(ENABLED, true)
                .putInt(FAILURES, 0).putLong(BLOCK_UNTIL, 0L).commit()
        } catch (_: Exception) { false }
    }

    fun remainingLockout(context: Context): Long =
        (prefs(context).getLong(BLOCK_UNTIL, 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    fun verifyPin(context: Context, pin: String): PinResult {
        if (!supported() || !hasPin(context) || !AppLockPolicy.validPin(pin)) return PinResult.ERROR
        if (remainingLockout(context) > 0L) return PinResult.BLOCKED
        return try {
            val encoder = android.util.Base64.NO_WRAP
            val salt = android.util.Base64.decode(prefs(context).getString(SALT, ""), encoder)
            val expected = android.util.Base64.decode(
                prefs(context).getString(VERIFIER, ""), encoder)
            if (MessageDigest.isEqual(expected, verifier(pin, salt))) {
                prefs(context).edit().putInt(FAILURES, 0).putLong(BLOCK_UNTIL, 0L).apply()
                PinResult.SUCCESS
            } else {
                val count = prefs(context).getInt(FAILURES, 0) + 1
                val wait = AppLockPolicy.lockoutMillis(count)
                prefs(context).edit().putInt(FAILURES, count)
                    .putLong(BLOCK_UNTIL, if (wait > 0L) System.currentTimeMillis() + wait
                        else 0L).apply()
                PinResult.WRONG
            }
        } catch (_: Exception) { PinResult.ERROR }
    }

    fun disable(context: Context) {
        disableFingerprint(context)
        prefs(context).edit().remove(SALT).remove(VERIFIER).remove(ENABLED)
            .remove(FAILURES).remove(BLOCK_UNTIL).apply()
        // Deleting a key is deliberately not coupled to existing finance data.
        try { keystore().deleteEntry(PIN_KEY) } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    fun fingerprintAvailable(context: Context): Boolean {
        if (!supported()) return false
        return try {
            val manager = context.getSystemService(Context.FINGERPRINT_SERVICE)
                as? FingerprintManager
            manager != null && manager.isHardwareDetected && manager.hasEnrolledFingerprints()
        } catch (_: Exception) { false }
    }

    fun fingerprintEnabled(context: Context): Boolean =
        enabled(context) && prefs(context).getBoolean(FINGER_ENABLED, false) &&
            fingerprintAvailable(context)

    private fun fingerKey(create: Boolean): SecretKey? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val existing = keystore().getKey(FINGER_KEY, null) as? SecretKey
        if (existing != null || !create) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(FINGER_KEY,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .build())
        return generator.generateKey()
    }

    private fun crypto(create: Boolean): FingerprintManager.CryptoObject? {
        return try {
            val key = fingerKey(create) ?: return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            @Suppress("DEPRECATION")
            FingerprintManager.CryptoObject(cipher)
        } catch (_: Exception) {
            // New or removed enrolled fingerprints invalidate the binding.
            // Never silently rebind to the changed system enrolment.
            null
        }
    }

    fun setFingerprintEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(FINGER_ENABLED, enabled).apply()
        if (!enabled) try { keystore().deleteEntry(FINGER_KEY) } catch (_: Exception) {}
    }

    fun disableFingerprint(context: Context) = setFingerprintEnabled(context, false)

    /**
     * Uses FingerprintManager rather than modality-agnostic BiometricPrompt:
     * recognition by face must NEVER unlock this application.
     * Existing bindings fail closed when system fingerprints are changed.
     */
    @Suppress("DEPRECATION")
    fun authenticateFingerprint(
        context: Context,
        enrolling: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onMismatch: () -> Unit
    ): CancellationSignal? {
        if (!fingerprintAvailable(context)) {
            onError("Отпечаток пальца не настроен или недоступен в Android")
            return null
        }
        if (enrolling) disableFingerprint(context)
        val token = crypto(create = enrolling)
        if (token == null) {
            disableFingerprint(context)
            onError("Отпечатки Android изменились. Подтвердите PIN и включите вход повторно.")
            return null
        }
        return try {
            val signal = CancellationSignal()
            val manager = context.getSystemService(Context.FINGERPRINT_SERVICE)
                as FingerprintManager
            manager.authenticate(token, signal, 0,
                object : FingerprintManager.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: FingerprintManager.AuthenticationResult
                    ) {
                        if (enrolling) prefs(context).edit()
                            .putBoolean(FINGER_ENABLED, true).apply()
                        onSuccess()
                    }

                    override fun onAuthenticationFailed() {
                        onMismatch()
                    }

                    override fun onAuthenticationError(code: Int, text: CharSequence) {
                        onError(text.toString())
                    }
                }, null)
            signal
        } catch (_: Exception) {
            if (enrolling) disableFingerprint(context)
            onError("Сканер отпечатка сейчас недоступен. Используйте код BalanceHub.")
            null
        }
    }
}
