package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

internal data class OnlineAiNetworkState(
    val active: Boolean,
    val internetCapable: Boolean,
    val metered: Boolean,
)

internal object OnlineAiNetworkPolicy {
    fun requireAccess(state: OnlineAiNetworkState, allowMeteredNetwork: Boolean) {
        if (!state.active || !state.internetCapable) {
            throw OnlineAiFailureException(OnlineAiFailureReason.NETWORK_UNAVAILABLE)
        }
        if (state.metered && !allowMeteredNetwork) {
            throw OnlineAiFailureException(OnlineAiFailureReason.METERED_NETWORK_DISALLOWED)
        }
    }
}

internal fun interface OnlineAiNetworkAccess {
    fun requireAccess()

    companion object {
        val UNRESTRICTED = OnlineAiNetworkAccess { Unit }
    }
}

/** Applies the persisted user policy to the active Android network immediately before a request. */
internal class AndroidOnlineAiNetworkAccess(
    context: Context,
    private val allowMeteredNetwork: () -> Boolean,
) : OnlineAiNetworkAccess {
    private val connectivityManager = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    override fun requireAccess() {
        val policyAllowsMetered = try {
            allowMeteredNetwork()
        } catch (_: Exception) {
            throw OnlineAiFailureException(OnlineAiFailureReason.NETWORK_UNAVAILABLE)
        }
        val state = try {
            val manager = connectivityManager
                ?: throw OnlineAiFailureException(OnlineAiFailureReason.NETWORK_UNAVAILABLE)
            val activeNetwork = manager.activeNetwork
            val capabilities = activeNetwork?.let(manager::getNetworkCapabilities)
            OnlineAiNetworkState(
                active = activeNetwork != null,
                internetCapable = capabilities?.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET,
                ) == true,
                metered = manager.isActiveNetworkMetered,
            )
        } catch (_: SecurityException) {
            throw OnlineAiFailureException(OnlineAiFailureReason.NETWORK_UNAVAILABLE)
        }
        OnlineAiNetworkPolicy.requireAccess(state, policyAllowsMetered)
    }
}
