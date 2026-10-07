package network.bisq.mobile.data.service.alert

import kotlinx.coroutines.flow.StateFlow
import network.bisq.mobile.data.service.ServiceFacade
import network.bisq.mobile.domain.model.alert.AuthorizedAlertData

abstract class TradeRestrictingAlertServiceFacade : ServiceFacade() {
    abstract val alert: StateFlow<AuthorizedAlertData?>

    /**
     * The bisq2 version a security-manager alert requires for trading, while this app's own trades
     * are blocked because the bisq2 core it runs is older. Updating the app is then the only remedy.
     * Null when no such block applies to this app.
     */
    abstract val minVersionRequiredForTrading: StateFlow<String?>
}
