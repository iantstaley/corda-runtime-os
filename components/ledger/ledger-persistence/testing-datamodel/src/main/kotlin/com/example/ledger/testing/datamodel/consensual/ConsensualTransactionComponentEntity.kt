package com.example.ledger.testing.datamodel.consensual

import net.corda.v5.base.annotations.CordaSerializable
import java.io.Serializable
import java.time.Instant
import javax.persistence.Column
import javax.persistence.Embeddable
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.IdClass
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

@CordaSerializable
@Entity
@Table(name = "consensual_transaction_component")
@IdClass(ConsensualTransactionComponentEntityId::class)
data class ConsensualTransactionComponentEntity(
    @get:Id
    @get:ManyToOne
    @get:JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    var transaction: ConsensualTransactionEntity,

    @get:Id
    @get:Column(name = "group_idx", nullable = false)
    var groupIndex: Int,

    @get:Id
    @get:Column(name = "leaf_idx", nullable = false)
    var leafIndex: Int,

    @get:Column(name = "data", nullable = false)
    var data: ByteArray,

    @get:Column(name = "hash", nullable = false)
    var hash: String,

    @get:Column(name = "created", nullable = false)
    var created: Instant
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ConsensualTransactionComponentEntity

        if (transaction != other.transaction) return false
        if (groupIndex != other.groupIndex) return false
        if (leafIndex != other.leafIndex) return false
        if (!data.contentEquals(other.data)) return false
        if (hash != other.hash) return false
        if (created != other.created) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transaction.hashCode()
        result = 31 * result + groupIndex
        result = 31 * result + leafIndex
        result = 31 * result + data.contentHashCode()
        result = 31 * result + hash.hashCode()
        result = 31 * result + created.hashCode()
        return result
    }
}

@Embeddable
data class ConsensualTransactionComponentEntityId(
    var transaction: ConsensualTransactionEntity,
    var groupIndex: Int,
    var leafIndex: Int
) : Serializable
