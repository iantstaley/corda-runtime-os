package net.corda.internal.serialization.amqp;

import net.corda.internal.serialization.amqp.helper.TestSerializationContext;
import net.corda.v5.base.annotations.CordaSerializable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.NotSerializableException;
import java.util.concurrent.TimeUnit;

import static net.corda.internal.serialization.amqp.testutils.AMQPTestUtilsKt.testDefaultFactory;

@Timeout(value = 30, unit = TimeUnit.SECONDS)
public class JavaSerialiseEnumTests {

    @CordaSerializable
    public enum Bras {
        TSHIRT, UNDERWIRE, PUSHUP, BRALETTE, STRAPLESS, SPORTS, BACKLESS, PADDED
    }

    @CordaSerializable
    private static class Bra {
        private final Bras bra;

        private Bra(Bras bra) {
            this.bra = bra;
        }

        public Bras getBra() {
            return this.bra;
        }
    }

    @Test
    public void testJavaConstructorAnnotations() throws NotSerializableException {
        Bra bra = new Bra(Bras.UNDERWIRE);

        SerializationOutput ser = new SerializationOutput(testDefaultFactory());
        ser.serialize(bra, TestSerializationContext.testSerializationContext);
    }
}
