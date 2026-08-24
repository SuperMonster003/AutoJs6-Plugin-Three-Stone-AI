package io.github.supermonster003.autojs6.plugin.threestoneai.backend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OnlineAiNetworkPolicyTest {
    @Test
    fun unavailableNetworksFailBeforeMeteringIsConsidered() {
        listOf(
            OnlineAiNetworkState(active = false, internetCapable = false, metered = false),
            OnlineAiNetworkState(active = true, internetCapable = false, metered = true),
        ).forEach { state ->
            val failure = assertThrows(OnlineAiFailureException::class.java) {
                OnlineAiNetworkPolicy.requireAccess(state, allowMeteredNetwork = true)
            }
            assertEquals(OnlineAiFailureReason.NETWORK_UNAVAILABLE, failure.reason)
        }
    }

    @Test
    fun meteredNetworkRequiresTheExplicitUserOptIn() {
        val state = OnlineAiNetworkState(active = true, internetCapable = true, metered = true)

        val failure = assertThrows(OnlineAiFailureException::class.java) {
            OnlineAiNetworkPolicy.requireAccess(state, allowMeteredNetwork = false)
        }
        assertEquals(OnlineAiFailureReason.METERED_NETWORK_DISALLOWED, failure.reason)
        OnlineAiNetworkPolicy.requireAccess(state, allowMeteredNetwork = true)
    }

    @Test
    fun unmeteredInternetIsAllowedWithoutOptIn() {
        OnlineAiNetworkPolicy.requireAccess(
            OnlineAiNetworkState(active = true, internetCapable = true, metered = false),
            allowMeteredNetwork = false,
        )
    }
}
