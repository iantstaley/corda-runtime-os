package net.corda.messagebus.db.producer

import javax.persistence.RollbackException
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.producer.CordaProducer
import net.corda.messagebus.api.producer.CordaProducerRecord
import net.corda.messagebus.db.datamodel.TopicRecordEntry
import net.corda.messagebus.db.persistence.DBAccess
import net.corda.messagebus.db.persistence.DBAccess.Companion.ATOMIC_TRANSACTION
import net.corda.messagebus.db.util.WriteOffsets
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

internal class CordaAtomicDBProducerImplTest {

    private val topic = "topic"
    private val key = "key"
    private val value = "value"
    private val serializedKey = key.toByteArray()
    private val serializedValue = value.toByteArray()

    @Test
    fun `atomic producer is okay when db already has atomic transaction`() {
        val dbAccess: DBAccess = mock()
        whenever(dbAccess.writeTransactionRecord(any())).thenAnswer {
            throw RollbackException("I already have this!")
        }
        CordaAtomicDBProducerImpl(mock(), dbAccess, mock(), mock())
    }

    @Test
    fun `atomic producer sends correct entry to database and topic`() {
        val dbAccess = mock<DBAccess>().apply {
            whenever(getTopicPartitionMapFor(any())).thenReturn(setOf(CordaTopicPartition(topic, 1)))
        }
        val writeOffsets = mock<WriteOffsets>().apply {
            whenever(getNextOffsetFor(CordaTopicPartition(topic, 0))).thenReturn(6)
        }
        val serializer = mock<CordaAvroSerializer<Any>>().apply {
            whenever(serialize(eq(key))).thenReturn(serializedKey)
            whenever(serialize(eq(value))).thenReturn(serializedValue)
        }
        val callback: CordaProducer.Callback = mock()

        val producer = CordaAtomicDBProducerImpl(
            serializer,
            dbAccess,
            writeOffsets,
            mock()
        )
        val cordaRecord = CordaProducerRecord(topic, key, value)

        producer.send(cordaRecord, callback)

        val dbRecordList = argumentCaptor<List<TopicRecordEntry>>()
        verify(dbAccess).writeRecords(dbRecordList.capture())
        verify(callback).onCompletion(null)
        val record = dbRecordList.firstValue.single()
        assertThat(record.topic).isEqualTo(topic)
        assertThat(record.key).isEqualTo(serializedKey)
        assertThat(record.value).isEqualTo(serializedValue)
        assertThat(record.recordOffset).isEqualTo(6)
        assertThat(record.partition).isEqualTo(0)
        assertThat(record.transactionId).isEqualTo(ATOMIC_TRANSACTION)
    }

    @Test
    fun `atomic producer sends correct entry to database when partition is specified`() {
        val dbAccess = mock<DBAccess>()
        val writeOffsets = mock<WriteOffsets>().apply {
            whenever(getNextOffsetFor(eq(CordaTopicPartition(topic, 0)))).thenReturn(3)
        }
        val serializer = mock<CordaAvroSerializer<Any>>()
        whenever(serializer.serialize(eq(key))).thenReturn(serializedKey)
        whenever(serializer.serialize(eq(value))).thenReturn(serializedValue)
        val callback: CordaProducer.Callback = mock()

        val producer = CordaAtomicDBProducerImpl(
            serializer,
            dbAccess,
            writeOffsets,
            mock()
        )
        val cordaRecord = CordaProducerRecord(topic, key, value)

        producer.send(cordaRecord, 0, callback)

        val dbRecordList = argumentCaptor<List<TopicRecordEntry>>()
        verify(dbAccess).writeRecords(dbRecordList.capture())
        verify(callback).onCompletion(null)
        val record = dbRecordList.firstValue.first()
        assertThat(record.topic).isEqualTo(topic)
        assertThat(record.key).isEqualTo(serializedKey)
        assertThat(record.value).isEqualTo(serializedValue)
        assertThat(record.recordOffset).isEqualTo(3)
        assertThat(record.partition).isEqualTo(0)
        assertThat(record.transactionId).isEqualTo(ATOMIC_TRANSACTION)
    }

    @Test
    fun `atomic producer throws exception for serialization error`() {
        val dbAccess = mock<DBAccess>()
        val writeOffsets = mock<WriteOffsets>().apply {
            whenever(getNextOffsetFor(eq(CordaTopicPartition(topic, 0)))).thenReturn(3)
        }
        val serializer = mock<CordaAvroSerializer<Any>>()
        whenever(serializer.serialize(eq(key))).doThrow(CordaRuntimeException("error"))
        whenever(serializer.serialize(eq(value))).thenReturn(serializedValue)
        val callback: CordaProducer.Callback = mock()

        val producer = CordaAtomicDBProducerImpl(
            serializer,
            dbAccess,
            writeOffsets,
            mock()
        )
        val cordaRecord = CordaProducerRecord(topic, key, value)

        assertThrows<CordaRuntimeException> {
            producer.send(cordaRecord, 0, callback)
        }
    }

    @Test
    fun `atomic producer does not throw exception for serialization error when flag set to false`() {
        val dbAccess = mock<DBAccess>()
        val writeOffsets = mock<WriteOffsets>().apply {
            whenever(getNextOffsetFor(eq(CordaTopicPartition(topic, 0)))).thenReturn(3)
        }
        val serializer = mock<CordaAvroSerializer<Any>>()
        doThrow(CordaRuntimeException("error")).whenever(serializer).serialize(eq(key))
        whenever(serializer.serialize(eq(value))).thenReturn(serializedValue)
        val callback: CordaProducer.Callback = mock()

        val producer = CordaAtomicDBProducerImpl(
            serializer,
            dbAccess,
            writeOffsets,
            mock(),
            false
        )
        val cordaRecord = CordaProducerRecord(topic, key, value)

        assertDoesNotThrow {
            producer.send(cordaRecord, 0, callback)
        }
    }

    @Test
    fun `atomic producer does not allow transactional calls`() {
        val dbAccess: DBAccess = mock()

        val producer = CordaAtomicDBProducerImpl(mock(), dbAccess, mock(), mock())

        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            producer.beginTransaction()
        }
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            producer.abortTransaction()
        }
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            producer.commitTransaction()
        }
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            producer.sendAllOffsetsToTransaction(mock())
        }
        assertThatExceptionOfType(CordaMessageAPIFatalException::class.java).isThrownBy {
            producer.sendRecordOffsetsToTransaction(mock(), mock())
        }
        verifyNoMoreInteractions(dbAccess)
    }

    @Test
    fun `producer correctly closes down dbAccess when closed`() {
        val dbAccess: DBAccess = mock()
        val producer = CordaAtomicDBProducerImpl(mock(), dbAccess, mock(), mock())
        producer.close()
        verify(dbAccess).close()
    }
}
