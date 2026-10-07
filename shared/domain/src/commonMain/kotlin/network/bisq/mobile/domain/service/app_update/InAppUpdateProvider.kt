package network.bisq.mobile.domain.service.app_update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Store-driven update of the installed app, without leaving it. Only Google Play installs of the
 * google flavor have a real implementation; every other build or install binds
 * [UnsupportedInAppUpdateProvider] and updates through an external page instead.
 *
 * The operations run on the provider's own process-lifetime scope, because a download outlives the
 * screen that started it.
 */
interface InAppUpdateProvider {
    val state: StateFlow<InAppUpdateState>

    /**
     * Asks the store whether it can update this install in-app. This contacts the store, so call it
     * only once an update is known to exist.
     */
    fun refresh()

    /** Starts, or after a failure restarts, the background download. */
    fun startUpdate()

    /** Installs a downloaded update, which restarts the app. */
    fun completeUpdate()
}

sealed interface InAppUpdateState {
    /** No in-app update can be offered: unsupported build or install, or the store has none yet. */
    data object Unavailable : InAppUpdateState

    data object Available : InAppUpdateState

    /** @param progress fraction downloaded, from 0 to 1 */
    data class Downloading(
        val progress: Float,
    ) : InAppUpdateState

    data object Failed : InAppUpdateState

    data object ReadyToInstall : InAppUpdateState
}

class UnsupportedInAppUpdateProvider : InAppUpdateProvider {
    override val state: StateFlow<InAppUpdateState> = MutableStateFlow(InAppUpdateState.Unavailable).asStateFlow()

    override fun refresh() = Unit

    override fun startUpdate() = Unit

    override fun completeUpdate() = Unit
}
