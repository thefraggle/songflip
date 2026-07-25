package com.songflip.data

import android.content.Context
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build

object DomainVerificationUtils {

    fun checkLinksEnabled(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return try {
                val manager = context.getSystemService(DomainVerificationManager::class.java)
                    ?: return false
                val userState = manager.getDomainVerificationUserState(context.packageName)
                    ?: return false

                val hostMap = userState.hostToStateMap

                hostMap.any { (_, state) ->
                    state == DomainVerificationUserState.DOMAIN_STATE_SELECTED ||
                    state == DomainVerificationUserState.DOMAIN_STATE_VERIFIED
                }
            } catch (e: Exception) {
                null
            }
        }
        return null
    }
}
