package com.plexus.personal.document.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class EmbeddingService {
    private final ObjectMapper mapper;
    private final String provider;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int dimensions;
    private final HttpClient client = HttpClient.newHttpClient();

    public EmbeddingService(
            ObjectMapper mapper,
            @Value("${atlas.embedding.provider:local}") String provider,
            @Value("${DASHSCOPE_API_KEY:}") String apiKey,
            @Value("${atlas.embedding.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}") String baseUrl,
            @Value("${atlas.embedding.model:text-embedding-v4}") String model,
            @Value("${atlas.embedding.dimensions:1024}") int dimensions
    ) {
        this.mapper = mapper;
        this.provider = provider;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.dimensions = dimensions;
    }

    public String embed(String text) {
        if ("dashscope".equalsIgnoreCase(provider) && !apiKey.isBlank()) {
            try {
                return remote(text);
            } catch (Exception exception) {
                throw new IllegalStateException("DashScope embedding failed: " + exception.getMessage(), exception);
            }
        }
        return local(text, dimensions);
    }

    private String remote(String text) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("input", text);
        payload.put("dimensions", dimensions);
        payload.put("encoding_format", "float");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl.replaceAll("/$", "") + "/embeddings"))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + response.statusCode());
        JsonNode root = mapper.readTree(response.body());
        JsonNode embedding = root.path("data").path(0).path("embedding");
        if (!embedding.isArray()) throw new IllegalStateException("embedding missing in response");
        return embedding.toString();
    }

    private String local(String text, int size) {
        double[] vector = new double[size];
        for (String word : tokens(text)) {
            if (!word.isBlank()) vector[(word.hashCode() & 0x7fffffff) % size] += 1;
        }
        double norm = 0;
        for (double value : vector) norm += value * value;
        norm = Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < vector.length; i++) vector[i] /= norm;
        return Arrays.toString(vector);
    }

    private List<String> tokens(String text) {
        List<String> result = new ArrayList<>();
        for (String part : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (part.isBlank()) continue;
            boolean cjk = part.codePoints().anyMatch(c -> c >= 0x4e00 && c <= 0x9fff);
            if (!cjk) { result.add(part); continue; }
            int[] chars = part.codePoints().toArray();
            for (int i = 0; i < chars.length; i++) {
                result.add(new String(Character.toChars(chars[i])));
                if (i + 1 < chars.length) result.add(new String(Character.toChars(chars[i])) + new String(Character.toChars(chars[i + 1])));
            }
        }
        return result;
    }
}
