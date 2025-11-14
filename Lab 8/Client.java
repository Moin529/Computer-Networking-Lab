import java.io.*;
import java.net.*;
import java.util.*;

class CWNDLogger {
    private PrintWriter writer;

    public CWNDLogger(String filename) {
        try {
            writer = new PrintWriter(new FileWriter(filename));
            writer.println("Round,cwnd,ssthresh");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void log(int round, int cwnd, int ssthresh) {
        writer.println(round + "," + cwnd + "," + ssthresh);
    }

    public void close() {
        writer.close();
    }
}

public class Client {

    public static void main(String[] args) {
        String mode;
        int port = 5000;
        int ssthresh = 8, cwnd = 1, dupACKcount = 0;
        String lastACK = "";
        int totalRounds = 30;

        Scanner sc = new Scanner(System.in);
        System.out.print("Select TCP Mode (TAHOE/RENO): ");
        mode = sc.nextLine().trim().toUpperCase();

        // Separate log files
        String filename = mode.equals("TAHOE") ? "tahoe_log.csv" : "reno_log.csv";

        CWNDLogger logger = new CWNDLogger(filename);

        try (Socket socket = new Socket("localhost", port)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            System.out.println("\n== TCP " + mode + " Mode ==\n");

            for (int round = 1; round <= totalRounds; round++) {
                System.out.println("Round " + round + ": cwnd = " + cwnd + ", ssthresh = " + ssthresh);

                // Create packets
                List<String> packets = new ArrayList<>();
                for (int i = 0; i < cwnd; i++) {
                    packets.add("pkt" + round + "_" + i);
                }

                System.out.println("Sent packets: " + String.join(", ", packets));
                out.println(String.join(",", packets));

                // Receive ACKs
                dupACKcount = 0;
                int expectedAcks = cwnd;

                for (int i = 0; i < expectedAcks; i++) {
                    String ack = in.readLine();
                    if (ack == null) break;

                    System.out.println("Received: " + ack);

                    if (ack.equals(lastACK)) {
                        dupACKcount++;
                        if (dupACKcount == 3) {
                            System.out.println("==> 3 Duplicate ACKs: Fast Retransmit!");

                            ssthresh = cwnd / 2;

                            if (mode.equals("RENO")) {
                                cwnd = ssthresh;
                                System.out.println("RENO fast recovery: cwnd=" + cwnd);
                            } else {
                                cwnd = 1;
                                System.out.println("TAHOE reset cwnd to 1");
                            }

                            dupACKcount = 0;
                            break;
                        }
                    } else {
                        dupACKcount = 1;
                        lastACK = ack;
                    }
                }

                // No loss
                if (dupACKcount < 3) {
                    if (cwnd < ssthresh) {
                        cwnd *= 2;
                        System.out.println("Slow Start: cwnd=" + cwnd);
                    } else {
                        cwnd += 1;
                        System.out.println("Congestion Avoidance: cwnd=" + cwnd);
                    }
                }

                // Log this round
                logger.log(round, cwnd, ssthresh);

                System.out.println();
            }

            out.println("exit");
            logger.close();
            System.out.println("Log saved to: " + filename);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
