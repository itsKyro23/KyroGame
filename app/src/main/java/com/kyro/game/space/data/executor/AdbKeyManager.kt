package com.kyro.game.space.data.executor

import android.content.Context
import android.util.Base64
import java.io.File
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.PKCS8EncodedKeySpec

/**
 * Manages persistent ADB authentication keys and custom client device name (e.g. "KYRO", "ZAYON").
 *
 * Ensures:
 * 1. A single permanent RSA-2048 key pair is used across connections and app updates.
 * 2. Android Wireless Debugging remembers the connection under the clean custom device name
 *    instead of generating random Linux process UIDs (e.g. `u0_a503@localhost`, `u0_a512@localhost`).
 * 3. Connecting to the same paired device reuses the identical RSA key, avoiding duplicate entries.
 */
object AdbKeyManager {

    private const val PREFS_NAME = "kyro_game_space_prefs"
    private const val KEY_ADB_CLIENT_NAME = "adb_custom_client_name"
    const val DEFAULT_CLIENT_NAME = "KYRO"

    fun getClientName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ADB_CLIENT_NAME, DEFAULT_CLIENT_NAME)
            ?.trim()
            ?.ifEmpty { DEFAULT_CLIENT_NAME }
            ?: DEFAULT_CLIENT_NAME
    }

    fun setClientName(context: Context, name: String): String {
        val clean = name.trim().replace(Regex("[^a-zA-Z0-9_-]"), "").ifEmpty { DEFAULT_CLIENT_NAME }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ADB_CLIENT_NAME, clean).apply()
        return clean
    }

    /**
     * Ensures persistent `adbkey` and `adbkey.pub` are synchronized to [adbHomeDir]/.android
     * with the specified [clientName].
     */
    fun ensurePersistentAdbKey(context: Context, adbHomeDir: File): Boolean {
        return try {
            val permanentDir = File(context.filesDir, "adb_keys").apply { mkdirs() }
            val permanentPrivKey = File(permanentDir, "adbkey")
            val permanentPubKey = File(permanentDir, "adbkey.pub")

            val targetAndroidDir = File(adbHomeDir, ".android").apply { mkdirs() }
            val targetPrivKey = File(targetAndroidDir, "adbkey")
            val targetPubKey = File(targetAndroidDir, "adbkey.pub")

            val clientName = getClientName(context)

            // Case 1: Check if an existing key pair already exists in targetAndroidDir or permanentDir
            var existingPriv = if (permanentPrivKey.exists() && permanentPrivKey.length() > 0) {
                permanentPrivKey
            } else if (targetPrivKey.exists() && targetPrivKey.length() > 0) {
                targetPrivKey
            } else {
                null
            }

            var existingPub = if (permanentPubKey.exists() && permanentPubKey.length() > 0) {
                permanentPubKey
            } else if (targetPubKey.exists() && targetPubKey.length() > 0) {
                targetPubKey
            } else {
                null
            }

            if (existingPriv != null && existingPub != null) {
                // We have an existing persistent key! Preserve it so we never generate duplicates.
                val pubText = existingPub.readText().trim()
                val base64Key = pubText.substringBefore(" ").trim()
                val updatedPubContent = "$base64Key $clientName\n"

                // Write/update both locations with the identical RSA key and custom client name
                val privBytes = existingPriv.readBytes()
                permanentPrivKey.writeBytes(privBytes)
                permanentPubKey.writeText(updatedPubContent)

                targetPrivKey.writeBytes(privBytes)
                targetPubKey.writeText(updatedPubContent)

                targetPrivKey.setReadable(true, true)
                targetPrivKey.setWritable(true, true)
                targetPubKey.setReadable(true, false)
                return true
            }

            // Case 2: No existing key exists anywhere — generate a new permanent RSA-2048 key pair
            val keyPair = generateRsaKeyPair()
            val rsaPriv = keyPair.private as RSAPrivateKey
            val rsaPub = keyPair.public as RSAPublicKey

            val privPem = encodePrivateKeyToPem(rsaPriv)
            val pubText = encodeAndroidPubkeyToString(rsaPub, clientName)

            permanentPrivKey.writeText(privPem)
            permanentPubKey.writeText(pubText)

            targetPrivKey.writeText(privPem)
            targetPubKey.writeText(pubText)

            targetPrivKey.setReadable(true, true)
            targetPrivKey.setWritable(true, true)
            targetPubKey.setReadable(true, false)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun generateRsaKeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        return kpg.generateKeyPair()
    }

    private fun encodePrivateKeyToPem(privateKey: RSAPrivateKey): String {
        val base64 = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)
        return buildString {
            appendLine("-----BEGIN PRIVATE KEY-----")
            var i = 0
            while (i < base64.length) {
                val end = (i + 64).coerceAtMost(base64.length)
                appendLine(base64.substring(i, end))
                i = end
            }
            appendLine("-----END PRIVATE KEY-----")
        }
    }

    /**
     * Encodes [publicKey] into standard AOSP Android `adbkey.pub` format:
     * `<base64_android_pubkey> <clientName>\n`
     */
    fun encodeAndroidPubkeyToString(publicKey: RSAPublicKey, clientName: String): String {
        val androidBytes = encodeAndroidPublicKey(publicKey)
        val b64 = Base64.encodeToString(androidBytes, Base64.NO_WRAP)
        return "$b64 $clientName\n"
    }

    /**
     * Encodes an [RSAPublicKey] into the 524-byte AOSP `RSAPublicKey` binary structure:
     * - int len (64 words = 2048 bits / 32)
     * - uint32_t n0inv
     * - uint8_t n[256] (little-endian modulus)
     * - uint8_t rr[256] (little-endian (2^4096) mod modulus)
     * - int exponent (65537)
     */
    fun encodeAndroidPublicKey(key: RSAPublicKey): ByteArray {
        val modulus = key.modulus
        val r = BigInteger.ONE.shiftLeft(32) // 2^32
        val n0inv = r.subtract(modulus.mod(r).modInverse(r)).toInt()

        val r4096 = BigInteger.ONE.shiftLeft(4096) // 2^4096
        val rr = r4096.mod(modulus)

        val buffer = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(64) // 2048 bits = 64 * 32-bit words
        buffer.putInt(n0inv)
        buffer.put(bigIntegerToLittleEndianPadded(modulus, 256))
        buffer.put(bigIntegerToLittleEndianPadded(rr, 256))
        buffer.putInt(key.publicExponent.toInt())

        return buffer.array()
    }

    private fun bigIntegerToLittleEndianPadded(bigInt: BigInteger, size: Int): ByteArray {
        val bigEndian = bigInt.toByteArray()
        val result = ByteArray(size)
        val copyLen = bigEndian.size.coerceAtMost(size)
        // Reverse into result for little-endian byte order
        for (i in 0 until copyLen) {
            val srcIdx = bigEndian.size - 1 - i
            if (srcIdx >= 0) {
                result[i] = bigEndian[srcIdx]
            }
        }
        return result
    }
}
