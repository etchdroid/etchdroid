package eu.depau.etchdroid

import eu.depau.etchdroid.utils.exception.InitException
import eu.depau.etchdroid.utils.exception.UsbCommunicationException
import eu.depau.etchdroid.utils.exception.base.isEnvironmentalFault
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class EnvironmentalFaultTest {
    @Test
    fun classifiesCauseChain() {
        assertTrue(UsbCommunicationException(IOException("io")).isEnvironmentalFault())
        assertTrue(InitException("init").isEnvironmentalFault())
        assertFalse(InitException("init", NullPointerException()).isEnvironmentalFault())
        assertFalse(UsbCommunicationException(IllegalArgumentException()).isEnvironmentalFault())
        assertFalse(UsbCommunicationException(CancellationException("x")).isEnvironmentalFault())
        assertFalse(IOException("plain").isEnvironmentalFault())
        assertFalse(null.isEnvironmentalFault())
    }
}
