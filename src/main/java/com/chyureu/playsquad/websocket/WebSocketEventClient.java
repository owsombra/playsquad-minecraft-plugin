package com.chyureu.playsquad.websocket;

import com.chyureu.playsquad.PlaySquad;
import com.chyureu.playsquad.config.SettingManager;
import com.chyureu.playsquad.events.PlaySquadDonationEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringEscapeUtils;
import org.bukkit.Bukkit;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;

public class WebSocketEventClient {
    // Initialize SettingManager and obtain token
    private SettingManager settingManager = SettingManager.getInstance(PlaySquad.getPlugin());
    private String token = settingManager.getToken();
    private WebSocketClient webSocketClient;
    private final String WEBSOCKET_BASE_URI = "wss://playsquad.gg/ws/event/?token=";

    private Timer heartbeatTimer;

    private Timer reconnectTimer;
    private boolean isReconnecting = false;

    // Constructor to establish WebSocket connection
    public WebSocketEventClient() {
        try {
            // Create URI for WebSocket connection with token
            URI uri = new URI(WEBSOCKET_BASE_URI + token);

            // Create WebSocket client and define its behavior
            webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake serverHandshake) {
                    // WebSocket connection established
                    System.out.println("WebSocket connection established.");

                    if (heartbeatTimer != null) {
                        heartbeatTimer.cancel();
                    }

                    // Set up a timer to send heartbeat messages periodically  
                    heartbeatTimer = new Timer();
                    heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            sendHeartbeat();
                        }
                    }, 0, 10000); // Send heartbeat every 10 seconds
                }

                @Override
                public void onMessage(String message) {
                    // Handle incoming WebSocket message
                    handleWebSocketMessage(message);
                }

                @Override
                public void onClose(int code, String s, boolean b) {
                    System.out.println("WebSocket connection closed.");

                    // Reconnect to the WebSocket server
                    reconnectWebSocket(code);
                }

                @Override
                public void onError(Exception e) {
                    System.out.println("WebSocket connection error.");

                    // Reconnect to the WebSocket server with code -1 (unclear status)
                    reconnectWebSocket(-1);
                    e.printStackTrace();
                }
            };

            // Connect to the WebSocket server
            webSocketClient.connect();
        } catch (Exception e) {
            // Handle exceptions
            e.printStackTrace();
        }
    }

    // Reconnect to the WebSocket server
    // code => -1: Status Code is unclear
    // code => 1000 : Normal closure
    private void reconnectWebSocket(int code) {
        if (isReconnecting) {
            System.out.println("Already reconnecting, skipping reconnect attempt.");
            return;
        }

        if (code == 1000) {
            System.out.println("WebSocket closed normally.");
        }
        
        reconnectTimer = new Timer();
        reconnectTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!webSocketClient.isOpen()) {
                    System.out.println("Attempting to reconnect...");
                    isReconnecting = true;
                    webSocketClient.reconnect();
                } else {
                    reconnectTimer.cancel();
                    isReconnecting = false; // Reset reconnecting flag when connection is reestablished
                    System.out.println("WebSocket reconnected.");
                }
            }
        }, 0, 5000);
    }

    // Send heartbeat message to the server
    private void sendHeartbeat() {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            // Prepare and send the heartbeat message
            String heartbeatMessage = "{\"eventType\":\"HEARTBEAT\",\"eventId\":\"" + token + "\"}";
            webSocketClient.send(heartbeatMessage);
            return;
        }
        // Close the WebSocket connection if it's not open
        closeWebSocket();
    }

    // Close the WebSocket connection
    public void closeWebSocket() {
        if (webSocketClient != null) {
            webSocketClient.close();
        }
    }

    // Handle incoming WebSocket messages
    private void handleWebSocketMessage(String message) {
        try {
            // Parse the JSON message
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(message);

            // Extract relevant fields from the message
            String eventType = jsonNode.get("eventType").asText();
            if (!eventType.equals("DONATE")) {
                // If eventType is not "DONATE", return without further processing
                return;
            }
            String amount = jsonNode.has("amount") ? jsonNode.get("amount").asText() : null;
            String eventId = jsonNode.has("eventId") ? jsonNode.get("eventId").asText() : null;
            String guestName = jsonNode.has("guestNameText") ? jsonNode.get("guestNameText").asText() : null;
            String squadName = jsonNode.has("squadNameText") ? jsonNode.get("squadNameText").asText() : null;
            String clientMessage = jsonNode.has("clientMessageText") ? jsonNode.get("clientMessageText").asText() : null;

            // Dispatch a custom event (PlaySquadDonationEvent) with the extracted data
            Bukkit.getScheduler().runTask(PlaySquad.getPlugin(), () -> {
                PlaySquad.getPlugin().getServer().getPluginManager().callEvent(new PlaySquadDonationEvent(amount, eventId, StringEscapeUtils.unescapeJava(guestName), StringEscapeUtils.unescapeJava(squadName), StringEscapeUtils.unescapeJava(clientMessage)));
            });
        } catch (IOException e) {
            // Handle IO exceptions
            e.printStackTrace();
        }
    }
}