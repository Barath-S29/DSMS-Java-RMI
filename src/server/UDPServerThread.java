package server;

import java.net.*;
import java.util.Map;

public class UDPServerThread extends Thread {
    
    ////

    private final int udpPort;
    private final ShareMarketServerImpl serverImpl;
    private final Map<String, Map<String, Share>> shareDatabase;
    
    public UDPServerThread(int udpPort, ShareMarketServerImpl serverImpl, Map<String, Map<String, Share>> shareDatabase) {
        this.udpPort = udpPort;
        this.serverImpl = serverImpl;  // Initialize final field
        this.shareDatabase = shareDatabase;
    }
    


    public void run() {
        try (DatagramSocket socket = new DatagramSocket(udpPort)) {
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                socket.receive(request);

                String receivedMessage = new String(request.getData(), 0, request.getLength());

                // Process request and generate response
                String responseMessage = processUDPRequest(receivedMessage);

                byte[] responseBytes = responseMessage.getBytes();
                DatagramPacket response = new DatagramPacket(responseBytes, responseBytes.length,
                        request.getAddress(), request.getPort());
                socket.send(response);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String processUDPRequest(String request) {
        if (request.startsWith("LIST_AVAILABILITY")) {
            String shareType = request.split(" ")[1];
            return getLocalShareAvailability(shareType);
        }
        return "INVALID_REQUEST";
    }
    
    private String getLocalShareAvailability(String shareType) {
        StringBuilder result = new StringBuilder();
        synchronized (serverImpl.getShareDatabase()) {
            for (Map<String, Share> shareMap : shareDatabase.values()) {
                for (Share share : shareMap.values()) {  // Iterate over the actual Share objects
                    if (share.getShareType().equalsIgnoreCase(shareType)) {
                        result.append("Share: ").append(share.getShareID())
                              .append(", Type: ").append(share.getShareType())
                              .append(", Available: ").append(share.getAvailableCapacity())
                              .append("\n");
                    }
                }
            }

        }
        return result.toString().trim();
    }
    
}



    //private final ShareMarketServerImpl serverImpl;
    //private final int port;
    // public UDPServerThread(int udpPort, ShareMarketServerImpl serverImpl) {
    //     this.udpPort = udpPort;
    //     // this.server = server;
    //     this.serverImpl = serverImpl;
    // }