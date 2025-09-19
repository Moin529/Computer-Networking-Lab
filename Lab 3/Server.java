import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

class Server {
    private static final int PORT = 12345;
    private static String[] usersArray;
    private static Map<String, String> lastWithdrawalMap = new ConcurrentHashMap<>();
    private static List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws IOException {
        loadUsers();
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Bank + Chat Server running on port " + PORT);

        // --- Accept clients ---
        while (true) {
            Socket client = serverSocket.accept();
            ClientHandler handler = new ClientHandler(client);
            clients.add(handler);
            handler.start();
        }
    }

    // --- Load and save users for ATM part ---
    private static void loadUsers() throws IOException {
        List<String> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader("users.txt"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) list.add(line.trim());
            }
        }
        usersArray = list.toArray(new String[0]);
    }

    private static synchronized void saveUsers() throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter("users.txt"))) {
            for (String u : usersArray) {
                bw.write(u);
                bw.newLine();
            }
        }
    }

    private static int findUserIndex(String card, String pin) {
        if (usersArray == null) return -1;
        for (int i = 0; i < usersArray.length; i++) {
            String[] parts = usersArray[i].split(",");
            if (parts.length >= 2) {
                String c = parts[0].replace("\uFEFF", "").trim();
                String p = parts[1].trim();
                if (c.equals(card) && p.equals(pin)) return i;
            }
        }
        return -1;
    }

    private static String getCard(int index) {
        String[] parts = usersArray[index].split(",");
        return parts[0].replace("\uFEFF", "").trim();
    }

    private static double getBalance(int index) {
        String[] parts = usersArray[index].split(",");
        if (parts.length >= 3) {
            try {
                return Double.parseDouble(parts[2].trim());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private static void updateBalance(int index, double newBalance) {
        String[] parts = usersArray[index].split(",");
        String card = parts.length >= 1 ? parts[0].replace("\uFEFF", "").trim() : "";
        String pin = parts.length >= 2 ? parts[1].trim() : "";
        usersArray[index] = card + "," + pin + "," + newBalance;
    }

    // --- Client Handler ---
    static class ClientHandler extends Thread {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private boolean authenticated = false;
        private int userIndex = -1;

        public ClientHandler(Socket socket) {
            this.socket = socket;
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
            } catch (IOException e) {
                System.out.println("Error setting up client handler: " + e.getMessage());
            }
        }

        public void sendMessage(String msg) {
            out.println(msg);
        }

        public void run() {
            try {
                String line;
                Scanner serverInput = new Scanner(System.in);

                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    // --- Client wants to exit chat ---
                    if (line.equalsIgnoreCase("bye")) {
                        out.println("Goodbye! Connection closed.");
                        break;
                    }

                    // --- Handle ATM commands ---
                    if (!authenticated) {
                        if (line.startsWith("AUTH:")) {
                            String[] parts = line.split(":", 3);
                            if (parts.length == 3) {
                                String card = parts[1].replace("\uFEFF", "").trim();
                                String pin = parts[2].trim();
                                int idx = findUserIndex(card, pin);
                                if (idx >= 0) {
                                    authenticated = true;
                                    userIndex = idx;
                                    out.println("AUTH_OK");
                                    continue;
                                }
                            }
                            out.println("AUTH_FAIL");
                            break;
                        } else {
                            out.println("AUTH_FAIL");
                            break;
                        }
                    } else {
                        if (line.startsWith("WITHDRAW:")) {
                            String[] parts = line.split(":", 2);
                            double amount;
                            try {
                                amount = Double.parseDouble(parts[1].trim());
                            } catch (Exception e) {
                                out.println("WITHDRAW_FAIL:Invalid amount");
                                continue;
                            }
                            synchronized (Server.class) {
                                double balance = getBalance(userIndex);
                                if (amount <= 0) {
                                    out.println("WITHDRAW_FAIL:Invalid amount");
                                } else if (balance < amount) {
                                    out.println("WITHDRAW_FAIL:Insufficient funds");
                                } else {
                                    double newBalance = balance - amount;
                                    updateBalance(userIndex, newBalance);
                                    saveUsers();
                                    String record = amount + "@" +
                                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
                                    lastWithdrawalMap.put(getCard(userIndex), record);
                                    out.println("WITHDRAW_SUCCESS:" + newBalance);
                                }
                            }
                        } else if (line.equals("BALANCE_REQ")) {
                            double balance = getBalance(userIndex);
                            out.println("BALANCE_RES:" + balance);
                        } else if (line.equals("EXIT") || line.equalsIgnoreCase("QUIT")) {
                            break;
                        } else {
                            // --- Chat Message ---
                            System.out.println("Client[" + socket.getPort() + "]: " + line);

                            // Ask server operator for reply
                            System.out.print("Reply to Client[" + socket.getPort() + "]: ");
                            String reply = serverInput.nextLine();
                            if (reply.equalsIgnoreCase("shutdown")) {
                                System.out.println("Server shutting down...");
                                out.println("SERVER shutting down...");
                                System.exit(0);
                            }
                            out.println("SERVER_REPLY: " + reply);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println("Client disconnected.");
            } finally {
                try { socket.close(); } catch (IOException e) {}
                clients.remove(this);
            }
        }
    }
}
