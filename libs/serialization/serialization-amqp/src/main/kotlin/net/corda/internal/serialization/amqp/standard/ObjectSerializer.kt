package net.corda.internal.serialization.amqp.standard

import net.corda.internal.serialization.amqp.AMQPSerializer
import net.corda.internal.serialization.amqp.ComposableTypePropertySerializer
import net.corda.internal.serialization.amqp.CompositeType
import net.corda.internal.serialization.amqp.DeserializationInput
import net.corda.internal.serialization.amqp.EvolutionObjectBuilder
import net.corda.internal.serialization.amqp.Field
import net.corda.internal.serialization.amqp.LocalSerializerFactory
import net.corda.internal.serialization.amqp.Metadata
import net.corda.internal.serialization.amqp.ObjectBuilder
import net.corda.internal.serialization.amqp.PropertySerializer
import net.corda.internal.serialization.amqp.SerializationOutput
import net.corda.internal.serialization.amqp.SerializationSchemas
import net.corda.internal.serialization.amqp.TypeNotation
import net.corda.internal.serialization.amqp.TypeNotationGenerator
import net.corda.internal.serialization.amqp.ifThrowsAppend
import net.corda.internal.serialization.amqp.withDescribed
import net.corda.internal.serialization.amqp.withList
import net.corda.internal.serialization.model.LocalConstructorInformation
import net.corda.internal.serialization.model.LocalPropertyInformation
import net.corda.internal.serialization.model.LocalTypeInformation
import net.corda.internal.serialization.model.PropertyName
import net.corda.internal.serialization.model.RemotePropertyInformation
import net.corda.internal.serialization.model.RemoteTypeInformation
import net.corda.internal.serialization.model.TypeIdentifier
import net.corda.sandbox.SandboxGroup
import net.corda.serialization.SerializationContext
import net.corda.v5.serialization.MissingSerializerException
import org.apache.qpid.proton.amqp.Symbol
import org.apache.qpid.proton.codec.Data
import java.io.NotSerializableException
import java.lang.reflect.Type

interface ObjectSerializer : AMQPSerializer<Any> {

    val propertySerializers: Map<String, PropertySerializer>
    val fields: List<Field>

    companion object {
        fun make(typeInformation: LocalTypeInformation, factory: LocalSerializerFactory): ObjectSerializer {
            if (typeInformation is LocalTypeInformation.NonComposable) {
                val typeNames = typeInformation.nonComposableTypes.map { it.observedType.typeName }
                throw MissingSerializerException(
                    nonComposableExceptionMessage(typeInformation, factory),
                    typeNames
                )
            }

            val typeDescriptor = factory.createDescriptor(typeInformation)
            val typeNotation = TypeNotationGenerator.getTypeNotation(typeInformation, typeDescriptor)

            return when (typeInformation) {
                is LocalTypeInformation.Composable ->
                    makeForComposable(typeInformation, typeNotation, typeDescriptor, factory)
                is LocalTypeInformation.AnInterface,
                is LocalTypeInformation.Abstract ->
                    makeForAbstract(typeNotation, typeInformation, typeDescriptor, factory)
                else -> throw NotSerializableException("Cannot build object serializer for $typeInformation")
            }
        }

        private fun nonComposableExceptionMessage(
            typeInformation: LocalTypeInformation.NonComposable,
            factory: LocalSerializerFactory
        ): String {
            val serializerInformation = factory.customSerializerNames.map { "Serializer: $it" }.let { serializers ->
                when {
                    serializers.isNotEmpty() -> "Registered custom serializers:\n    ${serializers.joinToString("\n    ")}"
                    else -> "No custom serializers registered."
                }
            }
            return "Unable to create an object serializer for type ${typeInformation.observedType}:\n" +
                    "${typeInformation.reason}\n\n" +
                    "${typeInformation.remedy}\n\n" +
                    "$serializerInformation\n"
        }

        private fun makeForAbstract(typeNotation: CompositeType,
                                    typeInformation: LocalTypeInformation,
                                    typeDescriptor: Symbol,
                                    factory: LocalSerializerFactory): AbstractObjectSerializer {
            val propertySerializers = makePropertySerializers(typeInformation.propertiesOrEmptyMap, factory)
            val writer = ComposableObjectWriter(typeNotation, typeInformation.interfacesOrEmptyList, propertySerializers)
            return AbstractObjectSerializer(typeInformation.observedType, typeDescriptor, propertySerializers,
                    typeNotation.fields, writer)
        }

        private fun makeForComposable(typeInformation: LocalTypeInformation.Composable,
                                      typeNotation: CompositeType,
                                      typeDescriptor: Symbol,
                                      factory: LocalSerializerFactory): ComposableObjectSerializer {
            val propertySerializers = makePropertySerializers(typeInformation.properties, factory)
            val reader = ComposableObjectReader(
                    typeInformation.typeIdentifier,
                    propertySerializers,
                    ObjectBuilder.makeProvider(typeInformation))

            val writer = ComposableObjectWriter(
                    typeNotation,
                    typeInformation.interfaces,
                    propertySerializers)

            return ComposableObjectSerializer(
                    typeInformation.observedType,
                    typeDescriptor,
                    propertySerializers,
                    typeNotation.fields,
                    reader,
                    writer)
        }

        private fun makePropertySerializers(properties: Map<PropertyName, LocalPropertyInformation>,
                                            factory: LocalSerializerFactory): Map<String, PropertySerializer> =
                properties.mapValues { (name, property) ->
                    ComposableTypePropertySerializer.make(name, property, factory)
                }
    }
}

@Suppress("LongParameterList")
class ComposableObjectSerializer(
        override val type: Type,
        override val typeDescriptor: Symbol,
        override val propertySerializers: Map<PropertyName, PropertySerializer>,
        override val fields: List<Field>,
        private val reader: ComposableObjectReader,
        private val writer: ComposableObjectWriter): ObjectSerializer {

    override fun writeClassInfo(output: SerializationOutput, context: SerializationContext) = writer.writeClassInfo(output, context)

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext, debugIndent: Int) =
            writer.writeObject(obj, data, type, output, context, debugIndent)

    override fun readObject(obj: Any, serializationSchemas: SerializationSchemas, metadata: Metadata,
                            input: DeserializationInput, context: SerializationContext): Any =
            reader.readObject(obj, serializationSchemas, metadata, input, context)
}

class ComposableObjectWriter(
        private val typeNotation: TypeNotation,
        private val interfaces: List<LocalTypeInformation>,
        private val propertySerializers: Map<PropertyName, PropertySerializer>
) {
    fun writeClassInfo(output: SerializationOutput, context: SerializationContext) {
        if (output.writeTypeNotations(typeNotation)) {
            for (iface in interfaces) {
                output.requireSerializer(iface.observedType, context)
            }

            propertySerializers.values.forEach { serializer ->
                serializer.writeClassInfo(output, context)
            }
        }
    }

    fun writeObject(
            obj: Any, data: Data,
            @Suppress("UNUSED_PARAMETER") type: Type,
            output: SerializationOutput,
            context: SerializationContext,
            debugIndent: Int
    ) {
        data.withDescribed(typeNotation.descriptor) {
            withList {
                propertySerializers.values.forEach { propertySerializer ->
                    propertySerializer.writeProperty(obj, this, output, context, debugIndent + 1)
                }
            }
        }
    }
}

class ComposableObjectReader(
        val typeIdentifier: TypeIdentifier,
        private val propertySerializers: Map<PropertyName, PropertySerializer>,
        private val objectBuilderProvider: () -> ObjectBuilder
) {

    fun readObject(obj: Any, serializationSchemas: SerializationSchemas, metadata: Metadata,
                   input: DeserializationInput, context: SerializationContext): Any =
            ifThrowsAppend({ typeIdentifier.prettyPrint(false) }) {
                if (obj !is List<*>) throw NotSerializableException("Body of described type is unexpected $obj")
                if (obj.size < propertySerializers.size) {
                    throw NotSerializableException("${obj.size} objects to deserialize, but " +
                            "${propertySerializers.size} properties in described type ${typeIdentifier.prettyPrint(false)}")
                }

                val builder = objectBuilderProvider()
                builder.initialize()
                obj.asSequence().zip(propertySerializers.values.asSequence())
                        // Read _all_ properties from the stream
                        .map { (item, property) -> property.readProperty(item, serializationSchemas, metadata, input, context) }
                        // Write them into the builder (computed properties will be thrown away)
                        .forEachIndexed { slot, propertyValue -> builder.populate(slot, propertyValue) }
                return builder.build()
            }
}

class AbstractObjectSerializer(
        override val type: Type,
        override val typeDescriptor: Symbol,
        override val propertySerializers: Map<PropertyName, PropertySerializer>,
        override val fields: List<Field>,
        private val writer: ComposableObjectWriter): ObjectSerializer {
    override fun writeClassInfo(output: SerializationOutput, context: SerializationContext) =
        writer.writeClassInfo(output, context)

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext, debugIndent: Int) =
        writer.writeObject(obj, data, type, output, context, debugIndent)

    override fun readObject(obj: Any, serializationSchemas: SerializationSchemas, metadata: Metadata,
                            input: DeserializationInput, context: SerializationContext): Any =
        throw UnsupportedOperationException("Cannot deserialize abstract type ${type.typeName}")
}

class EvolutionObjectSerializer(
        override val type: Type,
        override val typeDescriptor: Symbol,
        override val propertySerializers: Map<PropertyName, PropertySerializer>,
        private val reader: ComposableObjectReader): ObjectSerializer {

    companion object {
        fun make(localTypeInformation: LocalTypeInformation.Composable,
                 remoteTypeInformation: RemoteTypeInformation.Composable,
                 constructor: LocalConstructorInformation,
                 properties: Map<String, LocalPropertyInformation>,
                 mustPreserveData: Boolean,
                 sandboxGroup: SandboxGroup): EvolutionObjectSerializer {
            val propertySerializers = makePropertySerializers(properties, remoteTypeInformation.properties, sandboxGroup)
            val reader = ComposableObjectReader(
                    localTypeInformation.typeIdentifier,
                    propertySerializers,
                    EvolutionObjectBuilder.makeProvider(
                            localTypeInformation.typeIdentifier,
                            constructor,
                            properties,
                            remoteTypeInformation,
                            mustPreserveData))

            return EvolutionObjectSerializer(
                    localTypeInformation.observedType,
                    Symbol.valueOf(remoteTypeInformation.typeDescriptor),
                    propertySerializers,
                    reader)
        }

        private fun makePropertySerializers(localProperties: Map<String, LocalPropertyInformation>,
                                            remoteProperties: Map<String, RemotePropertyInformation>,
                                            sandboxGroup: SandboxGroup): Map<String, PropertySerializer> =
                remoteProperties.mapValues { (name, property) ->
                    val localProperty = localProperties[name]
                    val isCalculated = localProperty?.isCalculated ?: false
                    val type = localProperty?.type?.observedType ?: property.type.typeIdentifier.getLocalType(sandboxGroup)
                    ComposableTypePropertySerializer.makeForEvolution(name, isCalculated, property.type.typeIdentifier, type)
                }
    }

    override val fields: List<Field> get() = emptyList()

    override fun writeClassInfo(output: SerializationOutput, context: SerializationContext) =
            throw UnsupportedOperationException("Evolved types cannot be written")

    override fun writeObject(obj: Any, data: Data, type: Type, output: SerializationOutput, context: SerializationContext, debugIndent: Int) =
            throw UnsupportedOperationException("Evolved types cannot be written")

    override fun readObject(obj: Any, serializationSchemas: SerializationSchemas, metadata: Metadata, input: DeserializationInput, context: SerializationContext): Any =
            reader.readObject(obj, serializationSchemas, metadata, input, context)

}