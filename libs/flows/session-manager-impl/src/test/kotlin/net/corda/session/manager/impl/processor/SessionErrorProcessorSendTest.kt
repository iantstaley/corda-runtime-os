package net.corda.session.manager.impl.processor

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.test.flow.util.buildSessionEvent
import net.corda.test.flow.util.buildSessionState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
class SessionErrorProcessorSendTest {

    @Test
    fun testNullState() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            null,
            SessionError(ExceptionEnvelope()),
            contextSessionProps = emptyKeyValuePairList()
        )

        val sessionState = SessionErrorProcessorSend("Key", null, sessionEvent, ExceptionEnvelope(), Instant.now()).execute()
        assertThat(sessionState).isNotNull
        assertThat(sessionState.sendEventsState.undeliveredMessages.first().payload::class.java).isEqualTo(SessionError::class.java)
    }

    @Test
    fun testErrorMessage() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.OUTBOUND,
            "sessionId",
            null,
            SessionError(ExceptionEnvelope()),
            contextSessionProps = emptyKeyValuePairList()
        )
        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 0, mutableListOf(), 0, mutableListOf()
        )

        val sessionState = SessionErrorProcessorSend("Key", inputState, sessionEvent, ExceptionEnvelope(), Instant.now()).execute()
        assertThat(sessionState).isNotNull
        assertThat(sessionState.status).isEqualTo(SessionStateType.ERROR)
    }
}
