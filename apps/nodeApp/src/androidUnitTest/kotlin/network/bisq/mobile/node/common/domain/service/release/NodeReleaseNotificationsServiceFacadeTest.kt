package network.bisq.mobile.node.common.domain.service.release

import androidx.core.util.Supplier
import bisq.bonded_roles.BondedRolesService
import bisq.bonded_roles.release.AppType
import bisq.bonded_roles.release.ReleaseNotificationsService
import bisq.common.observable.collection.ObservableSet
import bisq.common.validation.NetworkDataValidation
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import network.bisq.mobile.node.common.domain.service.AndroidApplicationService
import network.bisq.mobile.node.common.test_utils.NodeKoinIntegrationTestBase
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import bisq.bonded_roles.release.ReleaseNotification as ReleaseNotificationBisq2

@OptIn(ExperimentalCoroutinesApi::class)
class NodeReleaseNotificationsServiceFacadeTest : NodeKoinIntegrationTestBase() {
    private val notifications = ObservableSet<ReleaseNotificationBisq2>()
    private lateinit var facade: NodeReleaseNotificationsServiceFacade

    override fun onSetup() {
        val releaseNotificationsService =
            mockk<ReleaseNotificationsService> {
                every { releaseNotifications } returns notifications
            }
        val bondedRolesService =
            mockk<BondedRolesService> {
                every { this@mockk.releaseNotificationsService } returns releaseNotificationsService
            }
        val provider =
            AndroidApplicationService.Provider().apply {
                this.bondedRolesService = Supplier { bondedRolesService }
            }
        facade = NodeReleaseNotificationsServiceFacade(provider)
    }

    @Test
    fun `keeps only notifications published for the node app`() =
        runTest {
            notifications.add(notification("node", "0.15.0", AppType.MOBILE_NODE))
            notifications.add(notification("connect", "0.11.0", AppType.MOBILE_CLIENT))
            notifications.add(notification("desktop", "2.1.14", AppType.DESKTOP))
            notifications.add(notification("untyped", "0.16.0", AppType.UNSPECIFIED))

            facade.activate()

            assertEquals(listOf("node"), facade.releaseNotifications.value.map { it.id })
        }

    @Test
    fun `follows notifications added and removed after activation`() =
        runTest {
            facade.activate()
            val notification = notification("node", "0.15.0", AppType.MOBILE_NODE, isPreRelease = true)

            notifications.add(notification)
            val mapped = facade.releaseNotifications.value.single()
            assertEquals("0.15.0", mapped.version.toString())
            assertEquals("notes node", mapped.releaseNotes)
            assertTrue(mapped.isPreRelease)

            notifications.remove(notification)
            assertTrue(facade.releaseNotifications.value.isEmpty())
        }

    @Test
    fun `stops following and clears on deactivate`() =
        runTest {
            facade.activate()
            notifications.add(notification("node", "0.15.0", AppType.MOBILE_NODE))

            facade.deactivate()
            notifications.add(notification("later", "0.16.0", AppType.MOBILE_NODE))

            assertTrue(facade.releaseNotifications.value.isEmpty())
        }

    private fun notification(
        id: String,
        version: String,
        appType: AppType,
        isPreRelease: Boolean = false,
    ) = ReleaseNotificationBisq2(
        id,
        // The constructor validates like network data: a recent date and a hash-length profile id.
        System.currentTimeMillis(),
        isPreRelease,
        false,
        "notes $id",
        version,
        "0".repeat(NetworkDataValidation.PROFILE_ID_LENGTH),
        false,
        appType,
    )
}
