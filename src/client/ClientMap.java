package client;

import java.util.HashMap;
import java.util.Map;

public class ClientMap {
    // Mapping locations to RMI ports
    private static final Map<String, Integer> SERVER_PORT_MAP = new HashMap<>();
    private static final Map<String, String> SERVER_NAME_MAP = new HashMap<>();

    static {
        SERVER_PORT_MAP.put("NYK", 1099);
        SERVER_PORT_MAP.put("LON", 1100);
        SERVER_PORT_MAP.put("TOK", 1101);

        SERVER_NAME_MAP.put("NYK", "NewYork");
        SERVER_NAME_MAP.put("LON", "London");
        SERVER_NAME_MAP.put("TOK", "Tokyo");
    }

    // Extract location from userID (adminID or buyerID)
    public static String getLocation(String userID) {
        if (userID.length() < 3) {
            return null; // Invalid ID
        }
        String loc = userID.substring(0, 3); // Extract first 3 letters (NYK, LON, TOK)
        return SERVER_NAME_MAP.getOrDefault(loc, null); // Convert to full name
    }

    // Get RMI port based on location
    public static int getRMIPort(String userID) {
        String loc = userID.substring(0, 3);
        return SERVER_PORT_MAP.getOrDefault(loc, -1); // Return -1 if invalid
    }
}
