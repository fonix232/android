package io.homeassistant.companion.android.common.data.kiosk

import java.security.SecureRandom

/** Fixed user name for the camera stream; only the password is a secret worth generating. */
const val KIOSK_STREAM_USERNAME = "kiosk"

/** Length of a generated password, in bytes before encoding. */
private const val PASSWORD_BYTES = 12

private const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

/** Alphabet without the characters that need escaping in a URL, for the generated password. */
private const val BASE64_URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

private const val BITS_PER_CHAR = 6
private const val BITS_PER_BYTE = 8

/** Masks for one byte and for one Base64 character's worth of bits. */
private const val BYTE_MASK = 0xFF
private const val CHAR_MASK = 0x3F

/**
 * The credentials the kiosk camera stream requires.
 *
 * The password is generated rather than chosen. A stream on a wall tablet is the kind of thing a
 * user would give a password they already use elsewhere, and this one is typed once into Home
 * Assistant and never again.
 */
@JvmInline
value class KioskStreamCredentials(val password: String) {

    /** The `user:password` pair as HTTP Basic encodes it. */
    val encoded: String
        get() = "$KIOSK_STREAM_USERNAME:$password".toByteArray().toBase64(BASE64_ALPHABET)

    companion object {
        /** Returns credentials with a freshly generated password. */
        fun generate(random: SecureRandom = SecureRandom()): KioskStreamCredentials =
            KioskStreamCredentials(ByteArray(PASSWORD_BYTES).also(random::nextBytes).toBase64(BASE64_URL_ALPHABET))
    }
}

/**
 * Encodes these bytes with [alphabet], without padding.
 *
 * Written out rather than taken from `android.util.Base64` so that the type stays usable from a
 * plain unit test, and from anywhere else that has no Android framework behind it.
 */
private fun ByteArray.toBase64(alphabet: String): String {
    val out = StringBuilder()
    var buffer = 0
    var bits = 0

    for (byte in this) {
        buffer = (buffer shl BITS_PER_BYTE) or (byte.toInt() and BYTE_MASK)
        bits += BITS_PER_BYTE
        while (bits >= BITS_PER_CHAR) {
            bits -= BITS_PER_CHAR
            out.append(alphabet[(buffer shr bits) and CHAR_MASK])
        }
    }
    if (bits > 0) {
        out.append(alphabet[(buffer shl (BITS_PER_CHAR - bits)) and CHAR_MASK])
    }
    return out.toString()
}
