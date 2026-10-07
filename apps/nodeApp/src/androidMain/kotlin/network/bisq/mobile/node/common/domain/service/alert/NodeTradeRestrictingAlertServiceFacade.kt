package network.bisq.mobile.node.common.domain.service.alert

import bisq.bonded_roles.security_manager.alert.AuthorizedAlertDataUtils
import bisq.common.application.ApplicationVersion
import bisq.common.observable.Pin
import bisq.common.platform.Version
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import network.bisq.mobile.data.service.alert.TradeRestrictingAlertServiceFacade
import network.bisq.mobile.domain.model.alert.AuthorizedAlertData
import network.bisq.mobile.node.common.domain.mapping.alert.toDomainOrNull
import network.bisq.mobile.node.common.domain.service.AndroidApplicationService
import bisq.bonded_roles.security_manager.alert.AuthorizedAlertData as AuthorizedAlertDataBisq2

/**
 * Lets the UI refuse a trade action up front, with the reason, instead of the user meeting the
 * trade service's exception. So both properties mirror the checks BisqEasyTradeService runs before
 * every trade action (verifyTradingNotOnHalt, verifyMinVersionForTrading), with the same AppType.
 */
class NodeTradeRestrictingAlertServiceFacade(
    private val provider: AndroidApplicationService.Provider,
) : TradeRestrictingAlertServiceFacade() {
    private val alertService by lazy { provider.alertService.get() }

    private val _alert = MutableStateFlow<AuthorizedAlertData?>(null)
    override val alert: StateFlow<AuthorizedAlertData?> = _alert.asStateFlow()

    private val _minVersionRequiredForTrading = MutableStateFlow<String?>(null)
    override val minVersionRequiredForTrading: StateFlow<String?> = _minVersionRequiredForTrading.asStateFlow()

    private var unconsumedAlertsPin: Pin? = null

    override suspend fun activate() {
        super.activate()

        // The Runnable observer fires once at subscription, covering the initial refresh
        unconsumedAlertsPin = alertService.authorizedAlertDataSet.addObserver(Runnable { refreshAlert() })
    }

    override suspend fun deactivate() {
        // Tear down our own state before delegating to the base (mirror of activate()).
        unconsumedAlertsPin?.unbind()
        unconsumedAlertsPin = null
        _alert.value = null
        _minVersionRequiredForTrading.value = null
        super.deactivate()
    }

    private fun refreshAlert() {
        _alert.value = findAlertRestrictingTrading()?.toDomainOrNull()
        _minVersionRequiredForTrading.value = findMinVersionBlockingTrading()
    }

    // A halt always restricts. A version requirement restricts only while the embedded core is
    // older, so a node that already updated is not held back by an alert that is still published.
    private fun findAlertRestrictingTrading(): AuthorizedAlertDataBisq2? =
        AuthorizedAlertDataUtils
            .findMostRecentTradeRestrictingAlert(alertService.authorizedAlertDataSet.stream(), AndroidApplicationService.APP_TYPE)
            .filter { it.isHaltTrading || it.minVersion.map(::isCoreBelow).orElse(false) }
            .orElse(null)

    private fun findMinVersionBlockingTrading(): String? =
        AuthorizedAlertDataUtils
            .findMinRequiredVersionForTrading(alertService.authorizedAlertDataSet.stream(), AndroidApplicationService.APP_TYPE)
            .filter(::isCoreBelow)
            .orElse(null)

    private fun isCoreBelow(minVersion: String): Boolean = Version.isValid(minVersion) && ApplicationVersion.getVersion().below(minVersion)
}
