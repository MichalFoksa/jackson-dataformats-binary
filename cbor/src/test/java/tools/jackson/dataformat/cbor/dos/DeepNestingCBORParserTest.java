package tools.jackson.dataformat.cbor.dos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import tools.jackson.core.*;
import tools.jackson.core.exc.StreamConstraintsException;

import tools.jackson.dataformat.cbor.CBORFactory;
import tools.jackson.dataformat.cbor.CBORTestBase;

/**
 * Unit tests for deeply nested JSON
 */
public class DeepNestingCBORParserTest extends CBORTestBase
{
    public void testDeeplyNestedObjects() throws Exception
    {
        final int depth = 1500;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepDoc(out, depth);
        try (JsonParser p = cborParser(out)) {
            while (p.nextToken() != null) {
                ;
            }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            String exceptionPrefix = String.format("Document nesting depth (%d) exceeds the maximum allowed",
                    StreamReadConstraints.DEFAULT_MAX_DEPTH + 1);
            assertTrue("JsonMappingException message is as expected?",
                    e.getMessage().startsWith(exceptionPrefix));
        }
    }

    public void testDeeplyNestedObjectsWithUnconstrainedMapper() throws Exception
    {
        final int depth = 1500;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepDoc(out, depth);
        CBORFactory cborFactory = CBORFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .build();
        try (JsonParser p = cborParser(cborFactory, out)) {
            while (p.nextToken() != null) {
                ;
            }
        }
    }

    public void testDeeplyNestedArrays() throws Exception
    {
        final int depth = 750;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepArrayDoc(out, depth);
        try (JsonParser p = cborParser(out)) {
            while (p.nextToken() != null) {
                ;
            }
            fail("expected StreamConstraintsException");
        } catch (StreamConstraintsException e) {
            String exceptionPrefix = String.format("Document nesting depth (%d) exceeds the maximum allowed",
                    StreamReadConstraints.DEFAULT_MAX_DEPTH + 1);
            assertTrue("JsonMappingException message is as expected?",
                    e.getMessage().startsWith(exceptionPrefix));
        }
    }

    public void testDeeplyNestedArraysWithUnconstrainedMapper() throws Exception
    {
        final int depth = 750;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        genDeepArrayDoc(out, depth);
        CBORFactory cborFactory = CBORFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .build();
        try (JsonParser p = cborParser(cborFactory, out)) {
            while (p.nextToken() != null) {
                ;
            }
        }
    }

    private void genDeepDoc(final ByteArrayOutputStream out, final int depth) throws IOException {
        CBORFactory cborFactory = cborFactoryBuilder()
                .streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .build();
        try (JsonGenerator gen = cborFactory.createGenerator(ObjectWriteContext.empty(), out)) {
            for (int i = 0; i < depth; i++) {
                gen.writeStartObject();
                gen.writeName("a");
            }
            gen.writeString("val");
            for (int i = 0; i < depth; i++) {
                gen.writeEndObject();
            }
        }
    }

    private void genDeepArrayDoc(final ByteArrayOutputStream out, final int depth) throws IOException {
        CBORFactory cborFactory = cborFactoryBuilder()
                .streamWriteConstraints(StreamWriteConstraints.builder().maxNestingDepth(Integer.MAX_VALUE).build())
                .build();
        try (JsonGenerator gen = cborFactory.createGenerator(ObjectWriteContext.empty(), out)) {
            for (int i = 0; i < depth; i++) {
                gen.writeStartObject();
                gen.writeName("a");
                gen.writeStartArray();
            }
            gen.writeString("val");
            for (int i = 0; i < depth; i++) {
                gen.writeEndArray();
                gen.writeEndObject();
            }
        }
    }
}
