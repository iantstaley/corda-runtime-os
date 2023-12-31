package net.corda.cpk.write.impl.services.kafka

import net.corda.lifecycle.Resource
import net.corda.v5.crypto.SecureHash

// TODO Maybe we need to allow cache entries invalidation to allow re-writing a Kafka record?
/**
 * Cache for CPK checksums.
 */
interface CpkChecksumsCache : Resource {
    fun start()

    fun getCachedCpkIds(): List<SecureHash>

    fun add(cpkChecksum: SecureHash)
}
