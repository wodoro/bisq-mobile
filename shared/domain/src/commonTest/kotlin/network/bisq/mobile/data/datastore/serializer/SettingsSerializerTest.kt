package network.bisq.mobile.data.datastore.serializer

import kotlinx.coroutines.test.runTest
import network.bisq.mobile.data.model.CommunityNotificationLevel
import network.bisq.mobile.data.model.Settings
import network.bisq.mobile.data.model.notificationLevelFor
import network.bisq.mobile.data.replicated.chat.ChatChannelDomainEnum
import network.bisq.mobile.test.datastore.jsonDataStoreSerializerTestSupport
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsSerializerTest {
    private val support =
        jsonDataStoreSerializerTestSupport(
            serializer = SettingsSerializer,
            defaultValue = Settings(),
            sampleValue = ::sampleSettings,
            typeName = "Settings",
            kSerializer = Settings.serializer(),
        )

    @Test
    fun `defaultValue returns empty Settings`() {
        support.assertDefaultValue()
    }

    @Test
    fun `readFrom returns default when source is exhausted`() =
        runTest {
            support.assertExhaustedReturnsDefault()
        }

    @Test
    fun `readFrom deserializes valid JSON`() =
        runTest {
            support.assertDeserializesValidJson()
        }

    @Test
    fun `readFrom wraps SerializationException in CorruptionException`() =
        runTest {
            support.assertWrapsSerializationExceptionInCorruptionException()
        }

    @Test
    fun `writeTo round trips Settings`() =
        runTest {
            support.assertRoundTrip()
        }

    @Test
    fun `readFrom applies a legacy community level to both channels`() =
        runTest {
            val settings = SettingsSerializer.readFrom(Buffer().writeUtf8("{\"communityNotificationLevel\":\"OFF\"}"))

            assertEquals(CommunityNotificationLevel.OFF, settings.notificationLevelFor(ChatChannelDomainEnum.DISCUSSION))
            assertEquals(CommunityNotificationLevel.OFF, settings.notificationLevelFor(ChatChannelDomainEnum.SUPPORT))
        }

    @Test
    fun `readFrom gives ALL on both channels when no level was stored`() =
        runTest {
            val settings = SettingsSerializer.readFrom(Buffer().writeUtf8("{}"))

            assertEquals(CommunityNotificationLevel.ALL, settings.notificationLevelFor(ChatChannelDomainEnum.DISCUSSION))
            assertEquals(CommunityNotificationLevel.ALL, settings.notificationLevelFor(ChatChannelDomainEnum.SUPPORT))
        }

    @Test
    fun `readFrom gives no ignored release versions to settings stored before the field existed`() =
        runTest {
            val settings = SettingsSerializer.readFrom(Buffer().writeUtf8("{\"firstLaunch\":false}"))

            assertEquals(emptySet(), settings.ignoredReleaseVersions)
        }

    private fun sampleSettings() =
        Settings(
            firstLaunch = false,
            selectedMarketCode = "BTC/EUR",
            discussionsNotificationLevel = CommunityNotificationLevel.OFF,
            supportNotificationLevel = CommunityNotificationLevel.MENTIONS_AND_REPLIES,
            rememberOfferbookFilterPreferences = false,
            ignoredReleaseVersions = setOf("0.15.0"),
        )
}
