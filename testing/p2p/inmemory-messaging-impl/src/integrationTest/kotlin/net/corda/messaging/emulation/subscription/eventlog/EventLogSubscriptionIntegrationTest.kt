package net.corda.messaging.emulation.subscription.eventlog

import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.processor.EventLogProcessor
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.EventLogRecord
import net.corda.messaging.api.records.Record
import net.corda.messaging.api.subscription.config.SubscriptionConfig
import net.corda.messaging.api.subscription.factory.SubscriptionFactory
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import net.corda.schema.configuration.BootConfig.INSTANCE_ID
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.ExtendWith
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@ExtendWith(ServiceExtension::class)
class EventLogSubscriptionIntegrationTest {

    private data class Event(val name: String, val id: Int)

    private val group = "net.corda.messaging.emulation.subscription.eventlog"
    private val topic = "eventlog.test"
    private val clientId = "testId"

    @InjectService(timeout = 4000)
    lateinit var subscriptionFactory: SubscriptionFactory

    @InjectService(timeout = 4000)
    lateinit var publisherFactory: PublisherFactory

    private val processed = mutableListOf<EventLogRecord<String, Event>>()

    private val waitForProcessed = AtomicReference<CountDownLatch>(null)

    private val assigned = mutableSetOf<Int>()

    private val subscription by lazy {
        val config = SubscriptionConfig(groupName = group, eventTopic = topic)
        val processor = object : EventLogProcessor<String, Event> {
            override fun onNext(events: List<EventLogRecord<String, Event>>): List<Record<*, *>> {
                processed.addAll(events)
                repeat(events.size) {
                    waitForProcessed.get()?.countDown()
                }
                return emptyList()
            }

            override val keyClass = String::class.java
            override val valueClass = Event::class.java
        }
        val partitionAssignmentListener = object : PartitionAssignmentListener {
            override fun onPartitionsUnassigned(topicPartitions: List<Pair<String, Int>>) {
            }

            override fun onPartitionsAssigned(topicPartitions: List<Pair<String, Int>>) {
                assigned += topicPartitions.map { it.second }
            }
        }
        subscriptionFactory.createEventLogSubscription(
            subscriptionConfig = config,
            processor = processor,
            partitionAssignmentListener = partitionAssignmentListener,
            messagingConfig = SmartConfigImpl.empty().withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(1))
        )
    }

    private fun publish(vararg records: Record<Any, Any>) {
        val publisherConfig = PublisherConfig(clientId)
        publisherFactory.createPublisher(publisherConfig, SmartConfigImpl.empty()).use {
            it.publish(records.toList())
        }
    }

    @Test
    fun `test events log subscription`() {
        assertThat(subscription.isRunning).isFalse

        // Start the subscription
        subscription.start()
        assertThat(subscription.isRunning).isTrue
        assertThat(processed).isEmpty()

        // Publish a few events
        waitForProcessed.set(CountDownLatch(3))
        publish(
            Record(topic, "key1", Event("one", 1)),
            Record("another.$topic", "key4", Event("four", 4)),
            Record(topic, "key2", Event("two", 2)),
            Record(topic, "key3", Event("three", 3)),
        )

        // Wait for the events
        waitForProcessed.get().await(1, TimeUnit.SECONDS)
        assertThat(processed).contains(
            EventLogRecord(
                topic = topic,
                key = "key1",
                value = Event("one", 1),
                partition = 9,
                offset = 0,
            ),
            EventLogRecord(
                topic = topic,
                key = "key2",
                value = Event("two", 2),
                partition = 10,
                offset = 0
            ),
            EventLogRecord(
                topic = topic,
                key = "key3",
                value = Event("three", 3),
                partition = 1,
                offset = 0
            ),
        ).hasSize(3)
        assertThat(assigned).contains(9, 10, 1)

        // Stop the subscriber
        subscription.close()
        assertThat(subscription.isRunning).isFalse
    }

    @Test
    @Timeout(10)
    fun `test that events log publish reply`() {
        val configurations = (1..2).map {
            SubscriptionConfig(groupName = "events.group.$it", eventTopic = "events.topic.$it")
        }
        val proccessed = CompletableFuture<Record<String, String>>()
        val subscriptions = listOf<(List<EventLogRecord<String, String>>) -> List<Record<*, *>>>(
            { events ->
                events.map {
                    Record(configurations[1].eventTopic, it.key, it.value)
                }
            }, { events ->
            proccessed.complete(events.map { Record(it.topic, it.key, it.value) }.firstOrNull())
            emptyList()
        }
        ).map { onNext ->
            object : EventLogProcessor<String, String> {
                override fun onNext(events: List<EventLogRecord<String, String>>): List<Record<*, *>> {
                    return onNext.invoke(events)
                }

                override val keyClass = String::class.java
                override val valueClass = String::class.java
            }
        }.zip(configurations).map { (processor, config) ->
            subscriptionFactory.createEventLogSubscription(
                subscriptionConfig = config,
                processor = processor,
                partitionAssignmentListener = null,
                messagingConfig = SmartConfigImpl.empty().withValue(INSTANCE_ID, ConfigValueFactory.fromAnyRef(1))
            )
        }.onEach {
            it.start()
        }

        publish(Record(configurations[0].eventTopic, "keyOne", "valueOne"))

        proccessed.join()
        assertThat(proccessed).isCompletedWithValue(Record(configurations[1].eventTopic, "keyOne", "valueOne"))

        subscriptions.forEach {
            it.close()
        }
    }
}
