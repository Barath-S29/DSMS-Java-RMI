package server;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ShareMarketServerImpl extends UnicastRemoteObject implements ShareMarketServer {

    private final String city;
    private final int udpPort;
    private static final int DEFAULT_PORT = 5000;
    private final Map<String, Map<String, Share>> shareDatabase = new HashMap<>();
    private final Map<String, Integer> remoteServers = new HashMap<>();

    private final Map<String, Map<String, Integer>> buyerHoldings = new HashMap<>();

    public ShareMarketServerImpl(String city, int udpPort) throws RemoteException {
        super();
        this.city = city;
        this.udpPort = udpPort;
        initializeShareTypes();
    }

    private void initializeShareTypes() {
        shareDatabase.put("Equity", new HashMap<>());
        shareDatabase.put("Bonus", new HashMap<>());
        shareDatabase.put("Dividend", new HashMap<>());
    }

    public void addRemoteServer(String city, int port) {
        remoteServers.put(city, port);
    }

    public Map<String, Map<String, Share>> getShareDatabase() {
        return this.shareDatabase;
    }

    private void logAction(String requestType, String requestParams, boolean success) {
        try {
            FileWriter writer = new FileWriter("logs/" + city + "_Server.log", true);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            writer.write("[" + timestamp + "] " + requestType + " | Params: " + requestParams + " | Status: " + (success ? "Successfully Completed" : "Failed") + "\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public synchronized String addShare(String shareID, String shareType, int availableCapacity) {
        shareDatabase.putIfAbsent(shareType, new HashMap<>());

        if (shareDatabase.get(shareType).containsKey(shareID)) {
            logAction("Add Share", "ShareID: " + shareID + ", ShareType: " + shareType, false);
            return "Share already exists with ID " + shareID + " and Type " + shareType;
        }

        shareDatabase.get(shareType).put(shareID, new Share(shareID, shareType, availableCapacity));
        logAction("Add Share", "ShareID: " + shareID + ", ShareType: " + shareType + ", Capacity: " + availableCapacity, true);
        return "Share added successfully: " + shareType + "-" + shareID;
    }

    @Override
    public synchronized String getShares(String buyerID) {
        // Strip depth marker if present for cleaner processing
        String cleanBuyerID = buyerID.contains("::DEPTH::") ? 
            buyerID.split("::DEPTH::")[0] : buyerID;
        
        StringBuilder result = new StringBuilder("Your Shares:\n");
        boolean hasShares = false;
        
        // Check for local shares
        if (buyerHoldings.containsKey(cleanBuyerID) && !buyerHoldings.get(cleanBuyerID).isEmpty()) {
            result.append(this.city).append(" Market Shares:\n");
            for (Map.Entry<String, Integer> entry : buyerHoldings.get(cleanBuyerID).entrySet()) {
                result.append("[Share: ").append(entry.getKey())
                      .append(", Owned: ").append(entry.getValue()).append("]\n");
            }
            hasShares = true;
        }
        
        // Only check remote markets if this isn't already a recursive call
        if (!buyerID.contains("::DEPTH::")) {
            // Add depth marker to prevent infinite recursion
            String depthMarkedID = cleanBuyerID + "::DEPTH::1";
            
            // Try to get shares from each remote market
            for (String remoteName : remoteServers.keySet()) {
                if (!remoteName.equalsIgnoreCase(this.city)) {
                    try {
                        // Get port for the remote market
                        int targetPort = getPortForMarket(remoteName);
                        if (targetPort == -1) {
                            System.out.println("Invalid port for market: " + remoteName);
                            continue;
                        }
                        
                        // Connect to the remote server
                        Registry registry = LocateRegistry.getRegistry("localhost", targetPort);
                        ShareMarketServer remoteServer = (ShareMarketServer) registry.lookup(remoteName);
                        
                        // Get shares from remote server
                        String remoteSharesResponse = remoteServer.getShares(depthMarkedID);
                        System.out.println("DEBUG - Remote response from " + remoteName + ": " + remoteSharesResponse);
                        
                        // Only process if the buyer has shares in this remote market
                        if (!remoteSharesResponse.equals("You do not own any shares in any market.")) {
                            // Extract just the shares part, removing the header
                            String[] lines = remoteSharesResponse.split("\n");
                            StringBuilder remoteShares = new StringBuilder();
                            
                            boolean foundShareLines = false;
                            for (int i = 1; i < lines.length; i++) { // Skip the header line
                                if (lines[i].trim().startsWith("[Share:")) {
                                    remoteShares.append(lines[i]).append("\n");
                                    foundShareLines = true;
                                }
                            }
                            
                            if (foundShareLines) {
                                if (hasShares) {
                                    result.append("\n"); // Add spacing between markets
                                }
                                result.append(remoteName).append(" Market Shares:\n");
                                result.append(remoteShares);
                                hasShares = true;
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Error fetching shares from " + remoteName + ": " + e.getMessage());
                        e.printStackTrace(); // More detailed error info
                    }
                }
            }
        }
        
        logAction("Get Shares", "BuyerID: " + cleanBuyerID, true);
        
        if (!hasShares) {
            return "You do not own any shares in any market.";
        }
        
        return result.toString();
    }

    @Override
    public synchronized String removeShare(String shareID, String shareType) {
        if (!shareDatabase.containsKey(shareType) || !shareDatabase.get(shareType).containsKey(shareID)) {
            logAction("Remove Share", "ShareID: " + shareID + ", ShareType: " + shareType, false);
            return "Share not found.";
        }

        shareDatabase.get(shareType).remove(shareID);
        logAction("Remove Share", "ShareID: " + shareID + ", ShareType: " + shareType, true);
        return "Share removed successfully.";
    }


    @Override
    public synchronized String purchaseShare(String buyerID, String shareID, String shareType, int shareCount) {
        String uniqueKey = shareType + "-" + shareID;

        // if (!shareDatabase.containsKey(shareType) || !shareDatabase.get(shareType).containsKey(shareID)) {
        //     logAction("Purchase Share", "BuyerID: " + buyerID + ", ShareID: " + shareID + ", ShareType: " + shareType + ", Quantity: " + shareCount, false);
        //     return "Purchase failed. Share not found.";
        // }

        Share share = shareDatabase.get(shareType).get(shareID);
        if (shareDatabase.containsKey(shareType) && shareDatabase.get(shareType).containsKey(shareID)) {
            if (share.getAvailableCapacity() < shareCount) {
                logAction("Purchase Share", "BuyerID: " + buyerID + ", ShareID: " + shareID + ", ShareType: " + shareType + ", Quantity: " + shareCount, false);
                return "Purchase failed. Not enough shares available.";
            }

            // Update the share's available capacity
            share.reduceCapacity(shareCount);

            // Update buyer's holdings
            buyerHoldings.putIfAbsent(buyerID, new HashMap<>());
            Map<String, Integer> holdings = buyerHoldings.get(buyerID);
            holdings.put(uniqueKey, holdings.getOrDefault(uniqueKey, 0) + shareCount);

            logAction("Purchase Share", "BuyerID: " + buyerID + ", ShareID: " + shareID + ", ShareType: " + shareType + ", Quantity: " + shareCount, true);
            return "Purchase successful. You bought " + shareCount + " of " + uniqueKey;
        }
        else{
            for (Map.Entry<String, Integer> entry : remoteServers.entrySet()) {
                String remoteName = entry.getKey();
                try {
                    return purchaseRemoteShare(buyerID, shareID, shareType, shareCount, remoteName);
                } catch (Exception e) {
                    // If this remote server doesn't have it, continue to the next one
                    continue;
                }
            }
            logAction("Purchase Share", "BuyerID: " + buyerID + ", ShareID: " + shareID, false);
            return "Purchase failed. Share not found in any market.";
        }
    }


    @Override
    public synchronized String purchaseRemoteShare(String buyerID, String shareID, String shareType, 
                                                int shareCount, String targetMarket) throws RemoteException {
        try {
            // Get the RMI port for the target market
            int targetPort = getPortForMarket(targetMarket);
            if (targetPort == -1) {
                logAction("Purchase Remote Share", "BuyerID: " + buyerID + ", ShareID: " + shareID + 
                        ", Target: " + targetMarket, false);
                return "Purchase failed. Invalid target market.";
            }
            
            // Connect to the remote server
            Registry registry = LocateRegistry.getRegistry("localhost", targetPort);
            ShareMarketServer remoteServer = (ShareMarketServer) registry.lookup(targetMarket);
            
            // Execute the purchase on the remote server
            String result = remoteServer.purchaseShare(buyerID, shareID, shareType, shareCount);
            
            // Log the cross-server transaction
            logAction("Purchase Remote Share", "BuyerID: " + buyerID + ", ShareID: " + shareID + 
                    ", Target: " + targetMarket + ", Quantity: " + shareCount, true);
            
            return "Cross-server purchase: " + result;
        } catch (Exception e) {
            logAction("Purchase Remote Share", "BuyerID: " + buyerID + ", ShareID: " + shareID + 
                    ", Target: " + targetMarket, false);
            return "Cross-server purchase failed: " + e.getMessage();
        }
    }


    @Override
    public synchronized String sellShare(String buyerID, String shareID, int quantity) {
        String uniqueKey = null;
        String shareType = null;

        if (buyerHoldings.containsKey(buyerID)) {
            for (String key : buyerHoldings.get(buyerID).keySet()) {
                if (key.endsWith("-" + shareID)) {
                    uniqueKey = key;
                    shareType = key.split("-")[0];
                    break;
                }
            }
        }

        // if (uniqueKey == null) {
        //     logAction("Sell Share", "BuyerID: " + buyerID + ", ShareID: " + shareID + ", Quantity: " + quantity, false);
        //     return "Sell failed. You do not own this share.";
        // }

        if (uniqueKey != null){
            int ownedShares = buyerHoldings.get(buyerID).get(uniqueKey);

            if (quantity > ownedShares) {
                logAction("Sell Share", "BuyerID: " + buyerID + ", ShareID: " + shareID + ", Quantity: " + quantity, false);
                return "Sell failed. You cannot sell more than you own.";
            }

            if (quantity == ownedShares) {
                buyerHoldings.get(buyerID).remove(uniqueKey);
            } else {
                buyerHoldings.get(buyerID).put(uniqueKey, ownedShares - quantity);
            }

            shareType = uniqueKey.split("-")[0];
            Share share = shareDatabase.get(shareType).get(shareID);
            share.increaseCapacity(quantity);

            logAction("Sell Share", "BuyerID: " + buyerID + ", ShareID: " + shareID + ", Quantity: " + quantity, true);
            return "Sell operation successful. Sold " + quantity + " of " + uniqueKey;
        }
        else {
            // If not found locally, try to find it in other markets
            for (Map.Entry<String, Integer> entry : remoteServers.entrySet()) {
                String remoteName = entry.getKey();
                try {
                    return sellRemoteShare(buyerID, shareID, null, quantity, remoteName);
                } catch (Exception e) {
                    // If this remote server doesn't have it, continue to the next one
                    continue;
                }
            }
            logAction("Sell Share", "BuyerID: " + buyerID + ", ShareID: " + shareID, false);
            return "Sell failed. Share not found in any market.";
        }
    }

    @Override
    public synchronized String sellRemoteShare(String buyerID, String shareID, String shareType, 
                                            int shareCount, String targetMarket) throws RemoteException {
        try {
            // Get the RMI port for the target market
            int targetPort = getPortForMarket(targetMarket);
            if (targetPort == -1) {
                logAction("Sell Remote Share", "BuyerID: " + buyerID + ", ShareID: " + shareID + 
                        ", Target: " + targetMarket, false);
                return "Sell failed. Invalid target market.";
            }
            
            // Connect to the remote server
            Registry registry = LocateRegistry.getRegistry("localhost", targetPort);
            ShareMarketServer remoteServer = (ShareMarketServer) registry.lookup(targetMarket);
            
            // Execute the sell on the remote server
            String result = remoteServer.sellShare(buyerID, shareID, shareCount);
            
            // Log the cross-server transaction
            logAction("Sell Remote Share", "BuyerID: " + buyerID + ", ShareID: " + shareID + 
                    ", Target: " + targetMarket + ", Quantity: " + shareCount, true);
            
            return "Cross-server sell: " + result;
        } catch (Exception e) {
            logAction("Sell Remote Share", "BuyerID: " + buyerID + ", ShareID: " + shareID + 
                    ", Target: " + targetMarket, false);
            return "Cross-server sell failed: " + e.getMessage();
        }
    }

    // Helper method to get the port for a market name
    private int getPortForMarket(String marketName) {
        switch (marketName) {
            case "NewYork": return 1099;
            case "London": return 1100;
            case "Tokyo": return 1101;
            default: return -1;
        }
    }


    @Override
    public synchronized String listShareAvailability(String shareType) {
        StringBuilder availability = new StringBuilder();

        synchronized (shareDatabase) {
            if (shareDatabase.containsKey(shareType)) {
                for (Share share : shareDatabase.get(shareType).values()) {
                    availability.append("[Share ID: ").append(share.getShareID())
                            .append(", Type: ").append(share.getShareType())
                            .append(", Available: ").append(share.getAvailableCapacity()).append("]\n");
                }
            }
        }

        for (Map.Entry<String, Integer> entry : remoteServers.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(this.city)) {
                int port = entry.getValue();
                String udpResponse = sendUDPRequest("localhost", port, "LIST_AVAILABILITY " + shareType);
                if (!udpResponse.isEmpty()) {
                    availability.append(udpResponse).append("\n");
                }
            }
        }
        return availability.toString().trim();
    }


    private String sendUDPRequest(String host, int port, String message) {
        try (DatagramSocket socket = new DatagramSocket()) {
            byte[] buffer = message.getBytes();
            InetAddress serverAddress = InetAddress.getByName(host);

            DatagramPacket request = new DatagramPacket(buffer, buffer.length, serverAddress, port);
            socket.send(request);

            byte[] responseBuffer = new byte[4096];
            DatagramPacket response = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(response);

            String responseMessage = new String(response.getData(), 0, response.getLength());
            return responseMessage;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }


    private static void startServer(String city, int udpPort, int rmiPort) throws RemoteException {
        ShareMarketServerImpl server = new ShareMarketServerImpl(city, udpPort);

        // Try to create a new RMI registry, if not already running
        Registry registry;
        try {
            registry = LocateRegistry.createRegistry(rmiPort);
        } catch (RemoteException e) {
            registry = LocateRegistry.getRegistry(rmiPort);
        }

        // Bind the server
        registry.rebind(city, server);

        System.out.println(city + " Server is ready at UDP Port "+udpPort+" and RMI port "+rmiPort+" .");

        // Add remote servers
        if (city.equals("NewYork")) {
            server.addRemoteServer("London", 5001);
            server.addRemoteServer("Tokyo", 5002);
        } else if (city.equals("London")) {
            server.addRemoteServer("NewYork", 5000);
            server.addRemoteServer("Tokyo", 5002);
        } else if (city.equals("Tokyo")) {
            server.addRemoteServer("NewYork", 5000);
            server.addRemoteServer("London", 5001);
        }

        // Start UDP thread
        new Thread(new UDPServerThread(udpPort, server, server.getShareDatabase())).start();
    }




    public static void main(String[] args) {
        try {
            startServer("NewYork", 5000, 1099);
            startServer("London", 5001, 1100);
            startServer("Tokyo", 5002, 1101);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
