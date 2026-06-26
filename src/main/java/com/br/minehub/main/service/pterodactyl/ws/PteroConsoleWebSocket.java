package com.br.minehub.main.service.pterodactyl.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class PteroConsoleWebSocket extends WebSocketClient {

    private final String token;
    private final TextArea console;
    private final ObjectMapper mapper = new ObjectMapper();

    public PteroConsoleWebSocket(String socketUrl, String token, String panelUrl, TextArea console) {
        super(URI.create(socketUrl), createHeaders(panelUrl));

        this.token = token;
        this.console = console;
    }

    private static Map<String, String> createHeaders(String panelUrl) {
        String origin = normalizeOrigin(panelUrl);

        Map<String, String> headers = new HashMap<>();
        headers.put("Origin", origin);
        headers.put("User-Agent", "Mozilla/5.0 MineHub");
        return headers;
    }

    private static String normalizeOrigin(String panelUrl) {
        if (panelUrl == null || panelUrl.isBlank()) {
            return "";
        }

        String origin = panelUrl.trim();

        if (origin.endsWith("/")) {
            origin = origin.substring(0, origin.length() - 1);
        }

        return origin;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        append("[MineHub] WebSocket conectado.\n");

        sendJson("auth", token);
    }

    @Override
    public void onMessage(String message) {
        try {
            JsonNode root = mapper.readTree(message);
            String event = root.has("event") ? root.get("event").asText() : "";

            switch (event) {
                case "auth success" -> {
                    append("[MineHub] Console autenticado.\n");
                    sendRaw("{\"event\":\"send logs\",\"args\":[null]}");
                    sendRaw("{\"event\":\"send stats\",\"args\":[null]}");
                }

                case "console output" -> {
                    JsonNode args = root.get("args");
                    if (args != null && args.isArray() && !args.isEmpty()) {
                        append(args.get(0).asText() + "\n");
                    }
                }

                case "status" -> {
                    JsonNode args = root.get("args");
                    if (args != null && args.isArray() && !args.isEmpty()) {
                        append("[STATUS] " + args.get(0).asText() + "\n");
                    }
                }

                case "stats" -> {
                    // Depois podemos transformar isso em CPU/RAM bonito.
                }

                case "token expiring" -> {
                    append("[MineHub] Token expirando. Reabra ou reconecte o console.\n");
                }

                case "token expired" -> {
                    append("[MineHub] Token expirado. Reconecte o console.\n");
                }

                default -> {
                    if (!event.isBlank()) {
                        append("[EVENT] " + event + "\n");
                    }
                }
            }

        } catch (Exception e) {
            append("[RAW] " + message + "\n");
        }
    }

    private void sendJson(String event, String arg) {
        try {
            String json = mapper.createObjectNode()
                    .put("event", event)
                    .set("args", mapper.createArrayNode().add(arg))
                    .toString();

            send(json);
        } catch (Exception e) {
            append("[MineHub] Erro ao enviar evento: " + e.getMessage() + "\n");
        }
    }

    private void sendRaw(String json) {
        try {
            send(json);
        } catch (Exception e) {
            append("[MineHub] Erro ao enviar JSON: " + e.getMessage() + "\n");
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        append("[MineHub] WebSocket fechado: " + code + " - " + reason + "\n");
    }

    @Override
    public void onError(Exception ex) {
        append("[MineHub] Erro WebSocket: " + ex.getMessage() + "\n");
    }

    private void append(String text) {
        Platform.runLater(() -> console.appendText(text));
    }
}