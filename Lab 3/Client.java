import java.io.*;
import java.net.*;
import java.util.*;

class Client {
    public static void main(String[] args) throws IOException {
        Socket socket = new Socket("localhost", 12345);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        Scanner sc = new Scanner(System.in);

        // --- Authentication ---
        System.out.print("Enter Card No: ");
        String cardNo = sc.nextLine().trim();
        System.out.print("Enter PIN: ");
        String pin = sc.nextLine().trim();

        out.println("AUTH:" + cardNo + ":" + pin);
        String resp = in.readLine();
        if (resp == null || !resp.trim().equals("AUTH_OK")) {
            System.out.println("Authentication failed. Server said: " + resp);
            socket.close();
            return;
        }
        System.out.println("Authentication successful!");

        // --- Thread to listen for server messages ---
        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    System.out.println("\n[SERVER] " + line);
                    System.out.print("Enter choice/message: ");
                }
            } catch (IOException e) {
                System.out.println("Disconnected from server.");
            }
        }).start();

        // --- Main loop: ATM + Chat ---
        while (true) {
            System.out.println("\nChoose option:");
            System.out.println("1. Withdraw");
            System.out.println("2. Check Balance");
            System.out.println("3. Exit");
            System.out.println("4. Chat");
            System.out.print("Enter choice/message: ");

            String input = sc.nextLine().trim();

            if (input.equals("1")) {
                System.out.print("Enter amount to withdraw: ");
                double amount;
                try {
                    amount = Double.parseDouble(sc.nextLine().trim());
                } catch (NumberFormatException e) {
                    System.out.println("Invalid amount!");
                    continue;
                }
                out.println("WITHDRAW:" + amount);
            } else if (input.equals("2")) {
                out.println("BALANCE_REQ");
            } else if (input.equals("3")) {
                out.println("EXIT");
                System.out.println("Exiting...");
                break;
            } else if (input.equals("4")) {
                System.out.print("Enter chat message (type 'bye' to quit chat): ");
                String msg = sc.nextLine();
                out.println(msg);
                if (msg.equalsIgnoreCase("bye")) {
                    break;
                }
            } else {
                // Directly treat as chat message
                out.println(input);
                if (input.equalsIgnoreCase("bye")) {
                    break;
                }
            }
        }

        socket.close();
        sc.close();
    }
}
