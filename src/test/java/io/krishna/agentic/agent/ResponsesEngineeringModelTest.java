package io.krishna.agentic.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.krishna.agentic.agent.client.ResponsesClient;
import io.krishna.agentic.workflow.domain.TaskKind;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ResponsesEngineeringModelTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> captured = new AtomicReference<>();

    @AfterEach
    void stop() { if (server != null) { server.stop(0); } }

    @Test
    void usesStructuredContractAndParsesOutput() throws Exception {
        String payload = mapper.writeValueAsString(Map.of("summary", "Design", "files", List.of(), "questions", List.of()));
        var model = model(200, response("completed", payload));
        assertThat(model.generate(request()).summary()).isEqualTo("Design");
        assertThat(mapper.readTree(captured.get()).at("/text/format/strict").asBoolean()).isTrue();
        assertThat(mapper.readTree(captured.get()).path("store").asBoolean()).isFalse();
    }

    @Test
    void handlesProviderErrorsWithoutLeakingResponseBody() throws Exception {
        var model = model(429, "sensitive upstream body");
        assertThatThrownBy(() -> model.generate(request())).isInstanceOf(IOException.class)
                .hasMessageContaining("429").hasMessageNotContaining("sensitive");
    }

    @Test
    void rejectsIncompleteAndMalformedOutputs() throws Exception {
        var incomplete = model(200, response("incomplete", "{}"));
        assertThatThrownBy(() -> incomplete.generate(request())).isInstanceOf(IOException.class);
        server.stop(0);
        var malformed = model(200, response("completed", "{}"));
        assertThatThrownBy(() -> malformed.generate(request())).isInstanceOf(IOException.class);
    }

    @Test
    void requiresCredentialsAndModel() {
        assertThatThrownBy(() -> new ResponsesClient(URI.create("https://api.openai.com/v1/responses"), "", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ResponsesEngineeringModel model(int status, String body) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/responses", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new ResponsesEngineeringModel(mapper, new ResponsesClient(
                URI.create("http://localhost:" + server.getAddress().getPort() + "/responses"), "test-model", "test-key"));
    }

    private String response(String status, String text) throws IOException {
        return mapper.writeValueAsString(Map.of("status", status, "output", List.of(Map.of("type", "message",
                "content", List.of(Map.of("type", "output_text", "text", text))))));
    }

    private AgentRequest request() { return new AgentRequest(TaskKind.DESIGN, "Build a service", Map.of()); }
}
