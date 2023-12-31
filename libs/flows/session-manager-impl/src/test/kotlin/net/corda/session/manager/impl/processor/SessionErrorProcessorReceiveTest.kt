package net.corda.session.manager.impl.processor

import net.corda.data.ExceptionEnvelope
import net.corda.data.flow.event.MessageDirection
import net.corda.data.flow.event.session.SessionError
import net.corda.data.flow.state.session.SessionStateType
import net.corda.flow.utils.emptyKeyValuePairList
import net.corda.test.flow.util.buildSessionEvent
import net.corda.test.flow.util.buildSessionState
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

class SessionErrorProcessorReceiveTest {

    @Test
    fun testNullState() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.INBOUND,
            "sessionId",
            null,
            SessionError(ExceptionEnvelope()),
            contextSessionProps = emptyKeyValuePairList()
        )
        val sessionState = SessionErrorProcessorReceive("Key", null, sessionEvent, ExceptionEnvelope(), Instant.now()).execute()
        Assertions.assertThat(sessionState).isNotNull
        Assertions.assertThat(sessionState.sendEventsState.undeliveredMessages.first().payload::class.java)
            .isEqualTo(SessionError::class.java)
    }

    @Test
    fun testErrorMessage() {
        val sessionEvent = buildSessionEvent(
            MessageDirection.INBOUND,
            "sessionId",
            null,
            SessionError(ExceptionEnvelope()),
            contextSessionProps = emptyKeyValuePairList()
        )

        val inputState = buildSessionState(
            SessionStateType.CONFIRMED, 0, mutableListOf(), 0, mutableListOf()
        )

        val sessionState = SessionErrorProcessorReceive("Key", inputState, sessionEvent, ExceptionEnvelope(), Instant.now())
            .execute()
        Assertions.assertThat(sessionState).isNotNull
        Assertions.assertThat(sessionState.status).isEqualTo(SessionStateType.ERROR)
    }
}
