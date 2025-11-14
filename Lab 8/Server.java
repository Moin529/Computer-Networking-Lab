import java.io.*;
import java.net.*;
import java.util.*;

public class Server {

    private static final double LOSS_PROBABILITY = 0.10;  // 10% chance per round

    public static void main(String[] args) {
        int port = 5000;
        System.out.println("The server started on port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected: " + clientSocket.getInetAddress());

            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            Random rand = new Random();
            String received;

            while ((received = in.readLine()) != null) {
                if (received.equalsIgnoreCase("exit"))
                    break;

                String[] packets = received.split(",");
                int packetCount = packets.length;

                System.out.println("\nReceived packets: " + received);

                // Decide whether packet loss occurs this round
                boolean lossHappens = rand.nextDouble() < LOSS_PROBABILITY;

                int lossIndex = -1;
                if (lossHappens) {
                    lossIndex = rand.nextInt(packetCount);
                    System.out.println("Simulating PACKET LOSS at index " + lossIndex +
                                       " -> " + packets[lossIndex]);
                } else {
                    System.out.println("NO LOSS this round.");
                }

                // Send ACKs according to loss rules
                for (int i = 0; i < packetCount; i++) {
                    if (!lossHappens || i != lossIndex) {
                        // Normal ACK
                        out.println("ACK:" + packets[i]);
                        System.out.println("Sent ACK:" + packets[i]);
                    } else {
                        // Loss happened
                        if (i == 0) {
                            // if first packet lost → send 3 ACK:NA
                            for (int j = 0; j < 3; j++) {
                                out.println("ACK:NA");
                                System.out.println("Sent ACK:NA (duplicate)");
                            }
                        } else {
                            // Otherwise → 3 duplicate ACKs for previous packet
                            String prev = packets[i - 1];
                            for (int j = 0; j < 3; j++) {
                                out.println("ACK:" + prev);
                                System.out.println("Sent DUP ACK:" + prev);
                            }
                        }

                        // After sending duplicate ACKs for the lost packet,
                        // server does NOT send normal ACK for that lost packet.
                    }
                }
            }

            System.out.println("\nClient disconnected.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
