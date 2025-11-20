import java.io.*;
import java.net.*;
import java.util.*;

public class Server {

    private static final double LOSS_PROBABILITY = 0.20;  // tune as you like

    public static void main(String[] args) {
        int port = 5000;
        System.out.println("Server started on port " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {

            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected: " + clientSocket.getInetAddress());

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            Random rand = new Random();
            String received;

            while ((received = in.readLine()) != null) {
                if (received.equalsIgnoreCase("exit")) {
                    System.out.println("Received 'exit' from client. Closing.");
                    break;
                }

                String[] packets = received.split(",");
                int packetCount = packets.length;

                System.out.println();
                System.out.println("Received packets: " + received);

                boolean lossHappens = rand.nextDouble() < LOSS_PROBABILITY;
                int lossIndex = -1;

                if (lossHappens && packetCount > 0) {
                    lossIndex = rand.nextInt(packetCount);
                    System.out.println("Simulating PACKET LOSS at index " + lossIndex +
                                       " -> " + packets[lossIndex]);
                } else {
                    System.out.println("NO LOSS this round.");
                }

                for (int i = 0; i < packetCount; i++) {
                    if (!lossHappens || i != lossIndex) {
                        // Normal ACK
                        out.println("ACK:" + packets[i]);
                        System.out.println("Sent ACK:" + packets[i]);
                    } else {
                        // Simulate 3 duplicate ACKs for previous packet (or NA if first)
                        if (i == 0) {
                            for (int j = 0; j < 3; j++) {
                                out.println("ACK:NA");
                                System.out.println("Sent DUP ACK:NA");
                            }
                        } else {
                            String prev = packets[i - 1];
                            for (int j = 0; j < 3; j++) {
                                out.println("ACK:" + prev);
                                System.out.println("Sent DUP ACK:" + prev);
                            }
                        }
                    }
                }
            }

            System.out.println();
            System.out.println("Client disconnected. Server shutting down.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}