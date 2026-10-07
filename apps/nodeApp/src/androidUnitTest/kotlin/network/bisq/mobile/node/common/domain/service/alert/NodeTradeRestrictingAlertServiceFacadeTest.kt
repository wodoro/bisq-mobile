package network.bisq.mobile.node.common.domain.service.alert

import androidx.core.util.Supplier
import bisq.bonded_roles.release.AppType
import bisq.bonded_roles.security_manager.alert.AlertService
import bisq.bonded_roles.security_manager.alert.AlertType
import bisq.common.observable.collection.ObservableSet
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import network.bisq.mobile.node.common.domain.service.AndroidApplicationService
import network.bisq.mobile.node.common.test_utils.NodeKoinIntegrationTestBase
import org.junit.Test
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNull
import bisq.bonded_roles.security_manager.alert.AuthorizedAlertData as AuthorizedAlertDataBisq2

/**
 * Covers [NodeTradeRestrictingAlertServiceFacade.minVersionRequiredForTrading], which must report a
 * block exactly when bisq2's trade service has one: an applicable EMERGENCY alert requiring a
 * version above the embedded core.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NodeTradeRestrictingAlertServiceFacadeTest : NodeKoinIntegrationTestBase() {
    private val alerts = ObservableSet<AuthorizedAlertDataBisq2>()
    private lateinit var facade: NodeTradeRestrictingAlertServiceFacade

    override fun onSetup() {
        val alertService = mockk<AlertService> { every { authorizedAlertDataSet } returns alerts }
        val provider = AndroidApplicationService.Provider().apply { this.alertService = Supplier { alertService } }
        facade = NodeTradeRestrictingAlertServiceFacade(provider)
    }

    @Test
    fun `reports the required version when the embedded core is below it`() =
        runTest {
            facade.activate()

            alerts.add(minVersionAlert(ABOVE_CORE, AppType.MOBILE_NODE))

            assertEquals(ABOVE_CORE, facade.minVersionRequiredForTrading.value)
        }

    @Test
    fun `an alert without an app type applies to the node`() =
        runTest {
            alerts.add(minVersionAlert(ABOVE_CORE, AppType.UNSPECIFIED))

            facade.activate()

            assertEquals(ABOVE_CORE, facade.minVersionRequiredForTrading.value)
        }

    @Test
    fun `reports nothing when the embedded core already meets the requirement`() =
        runTest {
            alerts.add(minVersionAlert(BELOW_CORE, AppType.MOBILE_NODE))

            facade.activate()

            assertNull(facade.minVersionRequiredForTrading.value)
        }

    @Test
    fun `an alert for Connect does not block the node`() =
        runTest {
            alerts.add(minVersionAlert(ABOVE_CORE, AppType.MOBILE_CLIENT))

            facade.activate()

            assertNull(facade.minVersionRequiredForTrading.value)
        }

    @Test
    fun `only an EMERGENCY alert can require a version`() =
        runTest {
            alerts.add(minVersionAlert(ABOVE_CORE, AppType.MOBILE_NODE, AlertType.WARN))

            facade.activate()

            assertNull(facade.minVersionRequiredForTrading.value)
        }

    @Test
    fun `clears when the alert is removed`() =
        runTest {
            val alert = minVersionAlert(ABOVE_CORE, AppType.MOBILE_NODE)
            alerts.add(alert)
            facade.activate()

            alerts.remove(alert)

            assertNull(facade.minVersionRequiredForTrading.value)
        }

    private fun minVersionAlert(
        minVersion: String,
        appType: AppType,
        alertType: AlertType = AlertType.EMERGENCY,
    ) = mockk<AuthorizedAlertDataBisq2> {
        every { id } returns "alert-$minVersion-$appType-$alertType"
        every { this@mockk.alertType } returns alertType
        every { this@mockk.appType } returns appType
        every { isHaltTrading } returns false
        every { isRequireVersionForTrading } returns true
        every { isTradeRestrictingAlert } returns true
        every { this@mockk.minVersion } returns Optional.of(minVersion)
        every { headline } returns Optional.empty()
        every { message } returns Optional.of("Update required")
        every { date } returns System.currentTimeMillis()
    }

    private companion object {
        // Far either side of whatever bisq2 core the node embeds.
        const val ABOVE_CORE = "99.0.0"
        const val BELOW_CORE = "1.0.0"
    }
}
