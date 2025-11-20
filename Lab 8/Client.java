import java.io.*;
import java.net.*;
import java.util.*;

//EstimatedRTT=(1−α)EstimatedRTT+αSampleRTT
//DevRTT=(1−β)DevRTT+β∣SampleRTT−EstimatedRTT∣


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
        int ssthresh = 8;
        int cwnd = 1;
        int totalRounds = 30;

        Scanner sc = new Scanner(System.in);
        Random rand = new Random();

        System.out.print("Select TCP Mode (TAHOE/RENO): ");
        mode = sc.nextLine().trim().toUpperCase();

        System.out.print("Enable formula-based RANDOM timeout simulation? (Y/N): ");
        boolean simulateTimeout = sc.nextLine().trim().toUpperCase().startsWith("Y");

        double alpha = 0.05 + rand.nextDouble() * 0.35;
        double beta  = 0.10 + rand.nextDouble() * 0.40;
        double estimatedRTT = 20 + rand.nextInt(61); 
        double devRTT       = 5  + rand.nextInt(26);
        double timeoutInterval = estimatedRTT + 4 * devRTT;

        System.out.println("\n== Initial RTT parameters ==");
        System.out.printf("alpha = %.3f, beta = %.3f%n", alpha, beta);
        System.out.println("EstimatedRTT (init) = " + estimatedRTT + " ms");
        System.out.println("DevRTT (init)       = " + devRTT + " ms");
        System.out.println("TimeoutInterval     = " + timeoutInterval + " ms\n");

        String filename = mode.equals("TAHOE") ? "tahoe_log.csv" : "reno_log.csv";
        CWNDLogger logger = new CWNDLogger(filename);

        try (Socket socket = new Socket("localhost", port)) {

            socket.setSoTimeout(0);

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            System.out.println("== TCP " + mode + " Mode ==");
            System.out.println("Formula-based random timeout simulation: " + simulateTimeout + "\n");

            for (int round = 1; round <= totalRounds; round++) {
                System.out.println("==================================================");
                System.out.println("Round " + round + ": cwnd = " + cwnd + ", ssthresh = " + ssthresh);

                List<String> packets = new ArrayList<>();
                for (int i = 0; i < cwnd; i++) {
                    String pkt = "pkt" + round + "_" + i;
                    packets.add(pkt);
                }

                String batch = String.join(",", packets);
                System.out.println("Sending window: " + batch);

                long sendTime = System.currentTimeMillis();
                for (String pkt : packets) {
                    System.out.println("[TIMER] Started timer for " + pkt +
                                       " at " + sendTime + " ms");
                }

                out.println(batch);

                boolean lossThisRound = false;
                String lossReason = null;

                int expectedAcks = cwnd;
                int dupACKcount = 0;
                String lastAck = null;

                for (int i = 0; i < expectedAcks; i++) {
                    String ack = in.readLine();

                    if (ack == null) {
                        System.out.println("[INFO] Server closed connection.");
                        lossThisRound = true;
                        lossReason = "CONNECTION CLOSED";
                        break;
                    }

                    long recvTime = System.currentTimeMillis();
                    System.out.println("Received: " + ack +
                                       "   (elapsed real time since send ≈ " +
                                       (recvTime - sendTime) + " ms)");

                    if (lastAck != null && ack.equals(lastAck)) {
                        dupACKcount++;
                    } else {
                        dupACKcount = 1;
                        lastAck = ack;
                    }

                    if (dupACKcount == 3) {
                        System.out.println("=== FAST RETRANSMIT EVENT ===");
                        System.out.println("[DUP ACK] 3 Duplicate ACKs for " + ack);

                        int newSsthresh = Math.max(cwnd / 2, 1);
                        ssthresh = newSsthresh;

                        if (mode.equals("RENO")) {
                            cwnd = ssthresh;
                            System.out.println("[RENO] Fast recovery: cwnd = " + cwnd +
                                               ", ssthresh = " + ssthresh);
                            lossReason = "FAST_RETX_RENO";
                        } else {
                            cwnd = 1;
                            System.out.println("[TAHOE] cwnd reset to 1, ssthresh = " + ssthresh);
                            lossReason = "FAST_RETX_TAHOE";
                        }

                        lossThisRound = true;
                        break;
                    }
                }

                if (simulateTimeout && !lossThisRound) {
                    int sampleRTT = 50 + rand.nextInt(251); 
                    int actualRTT = 50 + rand.nextInt(351); 

                    estimatedRTT = (1 - alpha) * estimatedRTT + alpha * sampleRTT;
                    devRTT = (1 - beta) * devRTT + beta * Math.abs(sampleRTT - estimatedRTT);
                    timeoutInterval = estimatedRTT + 4 * devRTT;

                    System.out.println("[FORMULA] SampleRTT_sim = " + sampleRTT + " ms");
                    System.out.println("[FORMULA] EstimatedRTT  = " +
                                       String.format("%.2f", estimatedRTT) + " ms");
                    System.out.println("[FORMULA] DevRTT        = " +
                                       String.format("%.2f", devRTT) + " ms");
                    System.out.println("[FORMULA] TimeoutIntvl  = " +
                                       String.format("%.2f", timeoutInterval) + " ms");
                    System.out.println("[FORMULA] ActualRTT_sim = " + actualRTT + " ms");

                    if (actualRTT > timeoutInterval) {
                        System.out.println("=== TIMEOUT (FORMULA) ===");
                        System.out.println("[TIMEOUT] actualRTT_sim > TimeoutInterval");

                        int newSsthresh = Math.max(cwnd / 2, 1);
                        ssthresh = newSsthresh;
                        cwnd = 1;

                        System.out.println("[TIMEOUT] Window reduced: ssthresh = " +
                                           ssthresh + ", cwnd = " + cwnd);

                        lossThisRound = true;
                        lossReason = "FORMULA_TIMEOUT";
                    }
                }

                if (lossThisRound) {
                    System.out.println("=== LOSS DETECTED: " + lossReason + " ===");
                    System.out.println("[RETX] Retransmitting window: " + batch);
                    out.println(batch);
                } else {
                    if (cwnd < ssthresh) {
                        cwnd *= 2;
                        System.out.println("[NO LOSS] Slow Start: cwnd = " + cwnd);
                    } else {
                        cwnd += 1;
                        System.out.println("[NO LOSS] Congestion Avoidance: cwnd = " + cwnd);
                    }
                }

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
