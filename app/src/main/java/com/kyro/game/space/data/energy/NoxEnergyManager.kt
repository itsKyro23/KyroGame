package com.kyro.game.space.data.energy

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import java.security.MessageDigest

/**
 * Manages NOX Game Space Energy System.
 *
 * Requirements:
 * - Initial Energy: 60
 * - Max Energy: 120
 * - Ad Reward: +40 Energy (capped at 120)
 * - Energy Costs:
 *   - Resolution Changer: 15⚡
 *   - Lightweight Texture Tuner: 20⚡
 *   - Game Tune: 10⚡
 *
 * Anti-Hack / Anti-Mod Protection:
 * - Employs dual memory bit-masking and cryptographic SHA-256 signature verification tied to device hardware ID.
 * - If SharedPreferences, memory, or transaction nonces are modified externally, tamper is immediately detected.
 * - When tamper is detected, energy is forcefully locked and penalized to -120.
 */
object NoxEnergyManager {

    const val INITIAL_ENERGY = 40
    const val MAX_ENERGY = 120
    const val AD_REWARD_ENERGY = 40
    const val PENALTY_ENERGY = -120

    // Feature Energy Costs
    const val COST_RESOLUTION_CHANGER = 15
    const val COST_LIGHTWEIGHT_TEXTURE = 20
    const val COST_LIGHTWEIGHT_TEXTURE_SINGLE = 20
    const val COST_LIGHTWEIGHT_TEXTURE_ALL = 40
    const val COST_GAME_TUNE = 10
    const val COST_AOT_DEX_SINGLE = 15
    const val COST_AOT_DEX_ALL = 30

    private const val PREFS_NAME = "nox_secure_vault"
    private const val KEY_OBFUSCATED_VAL = "nox_k7_val"
    private const val KEY_SIG = "nox_k7_sig"
    private const val KEY_NONCE = "nox_k7_nonce"
    private const val KEY_INITIALIZED = "nox_k7_init"
    private const val SALT = "NOX_CYBER_SHIELD_ENERGY_v1_99x"

    // Memory obfuscation masks
    private const val MASK_A = 0x5A5AA5A5
    private const val MASK_B = 0x3C3CC3C3

    private var memoryA: Int = 0
    private var memoryB: Int = 0

    private val _energyState = mutableIntStateOf(INITIAL_ENERGY)
    val energyState: State<Int> = _energyState

    private val _isTampered = mutableStateOf(false)
    val isTampered: State<Boolean> = _isTampered

    private var isLoaded = false

    fun init(context: Context) {
        if (isLoaded) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val deviceId = getDeviceFingerprint(context)

        val isInit = prefs.getBoolean(KEY_INITIALIZED, false)
        if (!isInit) {
            // First time initialization: grant initial 60 energy
            saveSecureEnergy(context, INITIAL_ENERGY, 1, deviceId)
            prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
            setMemoryEnergy(INITIAL_ENERGY)
            _isTampered.value = false
        } else {
            // Load and verify cryptographic signature
            val rawObfuscated = prefs.getInt(KEY_OBFUSCATED_VAL, 0)
            val storedNonce = prefs.getInt(KEY_NONCE, 0)
            val storedSig = prefs.getString(KEY_SIG, "") ?: ""

            val decodedVal = rawObfuscated xor (deviceId.hashCode() xor storedNonce)
            val expectedSig = computeSignature(decodedVal, storedNonce, deviceId)

            if (storedSig.isEmpty() || storedSig != expectedSig) {
                // External tamper detected! Penalize to -120
                applyPenalty(context, deviceId)
            } else {
                if (decodedVal == PENALTY_ENERGY) {
                    _isTampered.value = true
                    setMemoryEnergy(PENALTY_ENERGY)
                } else if (decodedVal > MAX_ENERGY || decodedVal < 0) {
                    // Out-of-bounds illegal energy
                    applyPenalty(context, deviceId)
                } else {
                    _isTampered.value = false
                    setMemoryEnergy(decodedVal)
                }
            }
        }
        isLoaded = true
    }

    private fun getMemoryEnergy(): Int {
        val valA = memoryA xor MASK_A
        val valB = memoryB xor MASK_B
        if (valA != valB) {
            // In-memory manipulation detected!
            return PENALTY_ENERGY
        }
        return valA
    }

    private fun setMemoryEnergy(value: Int) {
        memoryA = value xor MASK_A
        memoryB = value xor MASK_B
        _energyState.intValue = value
    }

    private fun applyPenalty(context: Context, deviceId: String) {
        _isTampered.value = true
        setMemoryEnergy(PENALTY_ENERGY)
        saveSecureEnergy(context, PENALTY_ENERGY, System.currentTimeMillis().toInt(), deviceId)
    }

    fun getEnergy(context: Context): Int {
        init(context)
        return getMemoryEnergy()
    }

    fun resetForTesting() {
        isLoaded = false
        memoryA = 0
        memoryB = 0
        _energyState.intValue = INITIAL_ENERGY
        _isTampered.value = false
    }

    fun hasEnergy(context: Context, cost: Int): Boolean {
        init(context)
        val current = getMemoryEnergy()
        if (current == PENALTY_ENERGY || _isTampered.value) return false
        return current >= cost
    }

    fun consumeEnergy(context: Context, cost: Int): Boolean {
        init(context)
        val deviceId = getDeviceFingerprint(context)
        val current = getMemoryEnergy()

        if (current == PENALTY_ENERGY || _isTampered.value) return false
        if (current < cost) return false

        val newEnergy = current - cost
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentNonce = prefs.getInt(KEY_NONCE, 0) + 1

        setMemoryEnergy(newEnergy)
        saveSecureEnergy(context, newEnergy, currentNonce, deviceId)
        return true
    }

    fun addEnergyFromAd(context: Context): Int {
        init(context)
        val deviceId = getDeviceFingerprint(context)
        val current = getMemoryEnergy()

        if (current == PENALTY_ENERGY || _isTampered.value) {
            // If penalized due to tampering, refuse reward
            return PENALTY_ENERGY
        }

        val newEnergy = (current + AD_REWARD_ENERGY).coerceAtMost(MAX_ENERGY)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentNonce = prefs.getInt(KEY_NONCE, 0) + 1

        setMemoryEnergy(newEnergy)
        saveSecureEnergy(context, newEnergy, currentNonce, deviceId)
        return newEnergy
    }

    private fun saveSecureEnergy(context: Context, energy: Int, nonce: Int, deviceId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obfuscated = energy xor (deviceId.hashCode() xor nonce)
        val sig = computeSignature(energy, nonce, deviceId)

        prefs.edit()
            .putInt(KEY_OBFUSCATED_VAL, obfuscated)
            .putInt(KEY_NONCE, nonce)
            .putString(KEY_SIG, sig)
            .apply()
    }

    private fun computeSignature(energy: Int, nonce: Int, deviceId: String): String {
        val payload = "$SALT:$energy:$nonce:$deviceId"
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(payload.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun getDeviceFingerprint(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "nox_hardware_node"
        } catch (_: Throwable) {
            "nox_hardware_node"
        }
    }
}
