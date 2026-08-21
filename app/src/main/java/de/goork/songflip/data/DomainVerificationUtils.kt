package de.goork.songflip.data

import android.content.Context
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.os.Build

data class DomainStatusInfo(
    val totalHosts: Int,
    val enabledHosts: Int,
    val unverifiedHosts: List<String>,
    val isFullyEnabled: Boolean,
    val isPartiallyEnabled: Boolean
)

object DomainVerificationUtils {

    fun getDomainStatus(context: Context): DomainStatusInfo? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return try {
                val manager = context.getSystemService(DomainVerificationManager::class.java)
                    ?: return null
                val userState = manager.getDomainVerificationUserState(context.packageName)
                    ?: return null

                val hostMap = userState.hostToStateMap
                val total = hostMap.size
                if (total == 0) return null

                val unverified = hostMap.filter { (_, state) ->
                    state != DomainVerificationUserState.DOMAIN_STATE_SELECTED &&
                    state != DomainVerificationUserState.DOMAIN_STATE_VERIFIED
                }.keys.toList()

                val enabled = total - unverified.size
                val isFully = unverified.isEmpty()
                val isPartially = enabled > 0 && unverified.isNotEmpty()

                DomainStatusInfo(
                    totalHosts = total,
                    enabledHosts = enabled,
                    unverifiedHosts = unverified,
                    isFullyEnabled = isFully,
                    isPartiallyEnabled = isPartially
                )
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    fun checkLinksEnabled(context: Context): Boolean? {
        val status = getDomainStatus(context) ?: return null
        return status.enabledHosts > 0
    }
}
