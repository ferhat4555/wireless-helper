package com.andrerinas.wirelesshelper.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object Prefs {
    private const val NORMAL_PREFS = "WirelessHelperPrefs"
    private const val ENCRYPTED_PREFS = "WirelessHelperSecurePrefs"

    fun get(context: Context): SharedPreferences {
        return context.getSharedPreferences(NORMAL_PREFS, Context.MODE_PRIVATE)
    }

    fun getSecure(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
