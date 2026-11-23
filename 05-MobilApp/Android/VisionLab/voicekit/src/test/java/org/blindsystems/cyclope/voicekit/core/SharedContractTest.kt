package org.blindsystems.cyclope.voicekit.core

import org.junit.Assert.*
import org.junit.Test

class SharedContractTest {
    /** Public STT contracts must link and expose their errors without Android or a vision host. */
    @Test fun reservedSpeechInputApiIsPlatformAndDomainIndependent() {
        val contracts = listOf(SpeechInput::class.java, MicrophonePermissionHost::class.java, SpeechInputListener::class.java, SpeechInputError::class.java)
        for (contract in contracts) {
            val exposed = contract.declaredMethods.flatMap { listOf(it.returnType) + it.parameterTypes.toList() }
            for (type in exposed) {
                val actual = if (type.isArray) type.componentType else type
                assertFalse(actual.name.startsWith("android."))
                assertFalse(actual.name.startsWith("org.blindsystems.cyclope.vision"))
            }
            contract.enumConstants?.let { constants ->
                assertTrue(constants.isNotEmpty())
                assertTrue(constants.all { it.javaClass == contract })
            }
        }
    }
}
