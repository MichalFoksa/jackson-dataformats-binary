package tools.jackson.dataformat.cbor.fuzz;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import tools.jackson.databind.ObjectMapper;

import tools.jackson.dataformat.cbor.CBORTestBase;

public class CBORFuzz458_65768_NPETest extends CBORTestBase
{
    private final ObjectMapper MAPPER = cborMapper();

    public void testInvalidText() throws Exception
    {
        final byte[] input = readResource("/data/clusterfuzz-cbor-65768.cbor");
        try (JsonParser p = MAPPER.createParser(input)) {
            try {
                assertNull(p.nextTextValue());
                assertToken(JsonToken.VALUE_NUMBER_FLOAT, p.currentToken());
                assertEquals(0, p.getIntValue());
                assertNull(p.nextTextValue());
                assertNull(p.nextTextValue());
                assertNull(p.nextTextValue());
                assertNull(p.nextTextValue());
                assertNull(p.nextTextValue());
                assertNull(p.nextTextValue());
                assertNull(p.nextTextValue());
                assertNull(p.nextTextValue());
                assertNull(p.nextTextValue());
                assertToken(JsonToken.VALUE_EMBEDDED_OBJECT, p.currentToken());
                p.getFloatValue();
                p.getDecimalValue();
                fail("Should not reach here (invalid input)");
            } catch (StreamReadException e) {
                verifyException(e, "Current token (VALUE_EMBEDDED_OBJECT) not numeric");
            }
        }
    }
}
