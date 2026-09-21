package com.bedrud.app.core.auth

/**
 * Seals and opens the values kept in an encrypted preferences file.
 *
 * The production implementation keeps its key in the Android Keystore, which no unit test can
 * reach, so the whole of [EncryptedPrefs] and its callers speak to this instead.
 */
interface SecureCipher {

    /** Returns [plaintext] sealed, encoded so it can be stored as a preference value. */
    fun encrypt(plaintext: String): String

    /**
     * Returns what [stored] holds, or null when it cannot be opened.
     *
     * Null is an expected answer, not an error: a backup restored onto another device, a Keystore
     * key invalidated by a lock-screen change, a file written by a key that no longer exists. The
     * caller treats it as a value that is not there.
     */
    fun decrypt(stored: String): String?
}
