package client;

import java.io.FileWriter;
import java.io.IOException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;
import server.ShareMarketServer;

public class AdminClient {
    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter your Admin ID (e.g., NYKAXXXX, LONAXXXX, TOKAXXXX): ");
            String adminID = scanner.next();

            // Determine correct server location and RMI port
            String cityCode = adminID.substring(0, 3);
            int rmiPort = getRMIPort(cityCode);
            String serverName = getFullCityName(cityCode);

            if (rmiPort == -1 || serverName == null) {
                System.out.println("Invalid Admin ID. Exiting.");
                return;
            }

            // Connect to correct RMI server
            Registry registry = LocateRegistry.getRegistry("localhost", rmiPort);
            ShareMarketServer server = (ShareMarketServer) registry.lookup(serverName);

            System.out.println("Connected to " + cityCode + " Server.");
            
            while (true) {
                System.out.println("\nAdmin Menu (" + cityCode + ")");
                System.out.println("1. Add Share");
                System.out.println("2. Remove Share");
                System.out.println("3. List Share Availability");
                System.out.println("4. Purchase Share (Buyer Function)");
                System.out.println("5. View My Shares (Buyer Function)");
                System.out.println("6. Sell Share (Buyer Function)");
                System.out.println("7. Exit");
                System.out.print("Enter your choice: ");
                int choice = scanner.nextInt();

                String response;
                switch (choice) {
                    case 1:
                        System.out.println("Example Share ID: LOCTDDMMYY (LOC: New York/T:Time/DDMMYY)");
                        System.out.print("Enter Share ID: ");
                        String shareID = scanner.next();
                        System.out.print("Enter Share Type (Equity/Bonus/Dividend): ");
                        String shareType = scanner.next();
                        System.out.print("Enter Capacity: ");
                        int capacity = scanner.nextInt();
                        response = server.addShare(shareID, shareType, capacity);
                        logAction(adminID, "addShare", response);
                        System.out.println(response);
                        break;
                    case 2:
                        System.out.print("Enter Share ID to remove: ");
                        shareID = scanner.next();
                        System.out.print("Enter Share Type: ");
                        shareType = scanner.next();
                        response = server.removeShare(shareID, shareType);
                        logAction(adminID, "removeShare", response);
                        System.out.println(response);
                        break;
                    case 3:
                        System.out.print("Enter Share Type to list availability: ");
                        shareType = scanner.next();
                        response = server.listShareAvailability(shareType);
                        logAction(adminID, "listShareAvailability", response);
                        System.out.println(response);
                        break;
                    case 4: // Buy Shares
                        System.out.print("Enter Share ID: ");
                        shareID = scanner.next();
                        System.out.print("Enter Share Type (Equity/Bonus/Dividend): ");
                        shareType = scanner.next();
                        System.out.print("Enter Quantity: ");
                        int quantity = scanner.nextInt();
                        response = server.purchaseShare(adminID, shareID, shareType, quantity);
                        logAction(adminID, "purchaseShare", response);
                        System.out.println(response);
                        break;
                    case 5: // View Shares
                        response = server.getShares(adminID);
                        logAction(adminID, "getShares", response);
                        System.out.println("Your Shares: " + response);
                        break;
                    case 6: // Sell Shares
                        System.out.print("Enter Share ID to sell: ");
                        shareID = scanner.next();
                        System.out.print("Enter Quantity: ");
                        quantity = scanner.nextInt();
                        response = server.sellShare(adminID, shareID, quantity);
                        logAction(adminID, "sellShare", response);
                        System.out.println(response);
                        break;
                    case 7:
                        System.out.println("Exiting Admin System.");
                        scanner.close();
                        return;
                    default:
                        System.out.println("Invalid choice. Try again.");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int getRMIPort(String cityCode) {
        switch (cityCode) {
            case "NYK": return 1099;
            case "LON": return 1100;
            case "TOK": return 1101;
            default: return -1;
        }
    }

    private static String getFullCityName(String cityCode) {
        switch (cityCode) {
            case "NYK": return "NewYork";
            case "LON": return "London";
            case "TOK": return "Tokyo";
            default: return null;
        }
    }

    private static void logAction(String userID, String action, String response) {
        try {
            FileWriter writer = new FileWriter("logs/Admin_" + userID + ".log", true);
            writer.write(action + " → " + response + "\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}