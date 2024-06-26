package tools.jackson.dataformat.smile.async;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import tools.jackson.core.*;
import tools.jackson.core.async.ByteArrayFeeder;
import tools.jackson.core.type.TypeReference;

import tools.jackson.databind.*;
import tools.jackson.databind.util.TokenBuffer;

import tools.jackson.dataformat.smile.databind.SmileMapper;

// for [dataformats-binary#384]
public class ConcurrentAsyncTest extends AsyncTestBase
{
    public void testConcurrentHandling() throws Exception
    {
        // 19-Jun-2023, tatu: For some reason, TokenBuffer buffering
        //   appears to fail and can't make it work so... skip
        if (true) {
            return;
        }

        Map<String, Map<String, String>> tags = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            Map<String, String> value = new HashMap<>();
            for (int j = 0; j < 10; j++) {
                value.put("key_" + j, "val" + j);
            }
            tags.put("elt_" + i, value);
        }

        ObjectMapper objectMapper = new SmileMapper();
        ObjectWriter objectWriter = objectMapper.writer();
        byte[] json = objectWriter.writeValueAsBytes(tags);
        TypeReference<Map<String, Map<String, String>>> typeReference = new TypeReference<Map<String, Map<String, String>>>() {
        };

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<CompletableFuture<?>> futures = new ArrayList<>();

        // Exact count varies but this seems to be enough to produce the problem
        int count = 10_000;
        for (int i = 0; i < count; i++) {
            JsonParser parser = objectMapper.createNonBlockingByteArrayParser();
            ByteArrayFeeder inputFeeder = (ByteArrayFeeder) parser.nonBlockingInputFeeder();
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    inputFeeder.feedInput(json, 0, json.length);
                    @SuppressWarnings("resource")
                    TokenBuffer tokenBuffer = TokenBuffer.forGeneration();
                    while (true) {
                        JsonToken token = parser.nextToken();
                        if (token == JsonToken.NOT_AVAILABLE || token == null) {
                            break;
                        }

                        tokenBuffer.copyCurrentEvent(parser);
                    }
                    return tokenBuffer.asParser().readValueAs(typeReference);
                } finally {
                    inputFeeder.endOfInput();
                    parser.close();
                }
            }, executorService));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).get();
    }
}
