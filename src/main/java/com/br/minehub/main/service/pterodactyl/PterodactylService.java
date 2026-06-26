package com.br.minehub.main.service.pterodactyl;

import com.br.minehub.main.service.pterodactyl.model.PteroServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.util.ArrayList;
import java.util.List;

public class PterodactylService {

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    private String panelUrl;
    private String apiKey;
    private String serverId;

    public void configure(String panelUrl, String apiKey, String serverId) {
        this.panelUrl = panelUrl == null ? "" : panelUrl.trim();

        if (this.panelUrl.endsWith("/")) {
            this.panelUrl = this.panelUrl.substring(0, this.panelUrl.length() - 1);
        }

        this.apiKey = apiKey == null
                ? ""
                : apiKey.trim().replace("\r", "").replace("\n", "");

        this.serverId = serverId == null
                ? ""
                : serverId.trim();
    }

    public void selectServer(String serverId) {
        this.serverId = serverId.trim();
    }

    public boolean isConfigured() {
        return panelUrl != null
                && apiKey != null
                && serverId != null
                && !panelUrl.isBlank()
                && !apiKey.isBlank()
                && !serverId.isBlank();
    }

    public List<PteroServer> listServers() throws IOException, InterruptedException {
        String json = testConnection();

        JsonNode root = mapper.readTree(json);
        JsonNode data = root.get("data");

        List<PteroServer> servers = new ArrayList<>();

        if (data != null && data.isArray()) {
            for (JsonNode server : data) {
                JsonNode attributes = server.get("attributes");

                if (attributes != null) {
                    String identifier = attributes.get("identifier").asText();
                    String name = attributes.get("name").asText();

                    servers.add(new PteroServer(identifier, name));
                }
            }
        }

        return servers;
    }

    public String testConnection() throws IOException, InterruptedException {
        HttpRequest request = baseRequest("/api/client")
                .GET()
                .build();

        return send(request);
    }

    public String getResources() throws IOException, InterruptedException {
        HttpRequest request = baseRequest("/api/client/servers/" + serverId + "/resources")
                .GET()
                .build();

        return send(request);
    }

    public String power(String signal) throws IOException, InterruptedException {
        String json = mapper.createObjectNode()
                .put("signal", signal)
                .toString();

        HttpRequest request = baseRequest("/api/client/servers/" + serverId + "/power")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return send(request);
    }

    public String sendCommand(String command) throws IOException, InterruptedException {
        String json = mapper.createObjectNode()
                .put("command", command)
                .toString();

        HttpRequest request = baseRequest("/api/client/servers/" + serverId + "/command")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return send(request);
    }

    public WebSocketData getWebSocketData() throws IOException, InterruptedException {
        HttpRequest request = baseRequest("/api/client/servers/" + serverId + "/websocket")
                .GET()
                .build();

        String json = send(request);
        JsonNode root = mapper.readTree(json);
        JsonNode data = root.get("data");

        String socket = data.get("socket").asText();
        String token = data.get("token").asText();

        return new WebSocketData(socket, token);
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(panelUrl + path))
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
    }

    private String send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        }

        throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
    }

    public record WebSocketData(String socket, String token) {
    }

    public String getPanelUrl() {
        return panelUrl;
    }

    public String getServerId() {
        return serverId;
    }
}