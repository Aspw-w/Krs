package com.instrumentalist.krs.utils.network;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class NetUtil {

    private static final String API_KEY = "3456281102d8eff00d17218a49c2a48ffb0c9f461ef6dea6a13bc28089e658d0";
    private static final String BASE_URL = "https://xn--3-kq6aqru49drkut90b5up.online/infinity";
    private static final int MAX_RESPONSE_BYTES = 1_000_000;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public static String sendRequest(String endpoint, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + endpoint))
                .timeout(Duration.ofSeconds(10))
                .header("X-API-Key", API_KEY)
                .header("Content-Type", "application/json")
                .method("GET", HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.limiting(
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8), MAX_RESPONSE_BYTES));
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IOException("Request failed with HTTP status " + response.statusCode());

        String body = response.body();
        if (body == null)
            throw new IOException("Response body exceeded the supported size");

        return body;
    }

    public static String parseJsonValue(String json, String key) {
        if (json == null || key == null) {
            return null;
        }

        String searchKey = "\"" + key + "\":\"";
        int startIndex = json.indexOf(searchKey);
        if (startIndex == -1) {
            return null;
        }
        startIndex += searchKey.length();
        int endIndex = json.indexOf("\"", startIndex);
        if (endIndex == -1) {
            return null;
        }
        return json.substring(startIndex, endIndex);
    }
}
