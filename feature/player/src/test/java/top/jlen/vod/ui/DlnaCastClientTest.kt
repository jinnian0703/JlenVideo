package top.jlen.vod.ui

import java.net.InetAddress
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class DlnaCastClientTest {
    @Test
    fun validatesLocationAgainstSenderAndRejectsNonLanTargets() {
        val sender = InetAddress.getByName("192.168.1.2")
        assertTrue(DlnaCastClient.isTrustedLocation("http://192.168.1.2:8080/device.xml", sender))
        assertFalse(DlnaCastClient.isTrustedLocation("http://192.168.1.3/device.xml", sender))
        assertFalse(DlnaCastClient.isTrustedLocation("http://localhost/device.xml", sender))
        assertFalse(DlnaCastClient.isTrustedLocation("file:///device.xml", sender))
        assertFalse(DlnaCastClient.isTrustedLocation("http://user@192.168.1.2/device.xml", sender))
        assertFalse(DlnaCastClient.isTrustedLocation("http://127.0.0.1/device.xml", InetAddress.getByName("127.0.0.1")))
        assertFalse(DlnaCastClient.isTrustedLocation("http://8.8.8.8/device.xml", InetAddress.getByName("8.8.8.8")))
    }

    @Test
    fun readsEmbeddedDeviceNameAndUsesAdvertisedServiceVersion() {
        val device = DlnaCastClient.parseDeviceDescription("http://192.168.1.2/device.xml", description().toByteArray())
        assertEquals("客厅电视", device?.name)
        assertEquals("http://192.168.1.2/control", device?.controlUrl)
        assertEquals("urn:schemas-upnp-org:service:AVTransport:2", device?.serviceType)
    }

    @Test
    fun rejectsControlUrlPointingAtAnotherHost() {
        assertNull(DlnaCastClient.parseDeviceDescription(
            "http://192.168.1.2/device.xml",
            description().replace("<controlURL>/control</controlURL>", "<controlURL>http://192.168.1.3/control</controlURL>").toByteArray()
        ))
    }

    @Test
    fun rejectsDoctypeAndOversizedDescription() {
        val xml = "<!DOCTYPE root [<!ENTITY message 'invalid'>]>" + description()
        assertTrue(runCatching { DlnaCastClient.parseDeviceDescription("http://192.168.1.2/device.xml", xml.toByteArray()) }.isFailure)
        assertNull(DlnaCastClient.parseDeviceDescription("http://192.168.1.2/device.xml", ByteArray(256 * 1024 + 1)))
    }

    @Test
    fun timeParsingRejectsOverflowNegativeAndOutOfRangeFields() {
        assertEquals(3_723_000L, DlnaCastClient.parseTime("01:02:03.123"))
        assertEquals(0L, DlnaCastClient.parseTime("00:00:00"))
        listOf("NOT_IMPLEMENTED", "01:60:00", "-1:00:00", "99999999999999999:00:00", "00:-1:00", "1:2").forEach {
            assertNull(it, DlnaCastClient.parseTime(it))
        }
    }

    @Test
    fun seekTimeUsesAsciiDigitsRegardlessOfPhoneLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("ar"))
            assertEquals("01:02:03", DlnaCastClient.formatTime(3_723_000L))
        } finally {
            Locale.setDefault(original)
        }
    }

    private fun description() = """
        <root xmlns="urn:schemas-upnp-org:device-1-0">
            <device><friendlyName>根设备</friendlyName><deviceList>
                <device><friendlyName>客厅电视</friendlyName><serviceList><service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:2</serviceType>
                    <controlURL>/control</controlURL>
                </service></serviceList></device>
            </deviceList></device>
        </root>
    """.trimIndent()
}
