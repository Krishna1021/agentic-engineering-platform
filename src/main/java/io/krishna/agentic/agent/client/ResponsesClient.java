package io.krishna.agentic.agent.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Owns HTTP transport, credentials, deadlines and response size enforcement. */
@Component
@ConditionalOnProperty(name = "platform.model.provider", havingValue = "openai")
public class ResponsesClient {
    private static final int MAX_RESPONSE_BYTES = 1_000_000;
    private final HttpClient client;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    public ResponsesClient(@Value("${platform.model.endpoint}") URI endpoint,
            @Value("${platform.model.name}") String model,
            @Value("${platform.model.api-key}") String apiKey,
            @Value("${platform.model.timeout:PT60S}") Duration timeout) {
        if (model.isBlank() || apiKey.isBlank()) {
            throw new IllegalArgumentException("MODEL_NAME and MODEL_API_KEY are required for openai provider");
        }
        if (!"https".equals(endpoint.getScheme()) && !"localhost".equals(endpoint.getHost())) {
            throw new IllegalArgumentException("Model endpoint must use HTTPS");
        }
        this.endpoint = endpoint;
        this.model = model;
        this.apiKey = apiKey;
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Model timeout must be positive");
        }
        this.timeout = timeout;
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String model() { return model; }

    public byte[] create(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
                .header("Authorization", "Bearer " + apiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var future = client.sendAsync(request, response -> new LimitedBodySubscriber());
        try {
            HttpResponse<byte[]> response = future.get(timeout.plusSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) {
                throw new IOException("Model request failed with HTTP " + response.statusCode());
            }
            return response.body();
        } finally {
            future.cancel(true);
        }
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long received;
        private boolean stopped;

        @Override
        public CompletionStage<byte[]> getBody() { return delegate.getBody(); }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            delegate.onSubscribe(value);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (stopped) { return; }
            received += buffers.stream().mapToLong(ByteBuffer::remaining).sum();
            if (received > MAX_RESPONSE_BYTES) {
                stopped = true;
                subscription.cancel();
                delegate.onError(new IOException("Model response exceeds size limit"));
                return;
            }
            delegate.onNext(buffers);
        }

        @Override
        public void onError(Throwable error) {
            if (!stopped) { delegate.onError(error); }
        }

        @Override
        public void onComplete() {
            if (!stopped) { delegate.onComplete(); }
        }
    }
}
