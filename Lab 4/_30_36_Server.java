import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import com.sun.net.httpserver.*;

public class _30_36_Server {
    private static final int SOCKET_PORT = 5000;
    private static final int HTTP_PORT = 8080;
    private static final String FOLDER_NAME = "Dictionary";

    public static void main(String[] args) throws Exception {
        File dir = new File(FOLDER_NAME);
        if (!dir.exists() || !dir.isDirectory()) {
            System.out.println("ERROR: Dictionary folder not found on server side.");
            return;
        }

        // Start socket file server in its own thread
        new Thread(() -> {
            try {
                startSocketFileServer(SOCKET_PORT, dir);
            } catch (IOException e) {
                System.err.println("Socket server error:");
                e.printStackTrace();
            }
        }, "SocketServerThread").start();

        // Start HTTP file server
        startHttpFileServer(HTTP_PORT, dir);
    }

    // ---------- Lab Task 1 (Socket Server) ----------
    private static void startSocketFileServer(int port, File directory) throws IOException {
        System.out.println("Socket file server started on port " + port);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Socket client connected: " + clientSocket.getInetAddress());
                new Thread(new SocketClientHandler(clientSocket, directory)).start();
            }
        }
    }

    static class SocketClientHandler implements Runnable {
        private Socket clientSocket;
        private File directory;

        public SocketClientHandler(Socket socket, File directory) {
            this.clientSocket = socket;
            this.directory = directory;
        }

        @Override
        public void run() {
            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
                DataOutputStream dataOut = new DataOutputStream(clientSocket.getOutputStream())
            ) {
                // Send available files list
                File[] files = directory.listFiles();
                if (files != null && files.length > 0) {
                    writer.write("Available files on server:\n");
                    for (File f : files) {
                        if (f.isFile()) {
                            writer.write(f.getName() + "\n");
                        }
                    }
                } else {
                    writer.write("No files available in Dictionary.\n");
                }
                writer.write("Enter the file name you want to download:\n");
                writer.flush();

                String fileName = reader.readLine();
                if (fileName == null || fileName.trim().isEmpty()) {
                    System.out.println("Client disconnected without requesting a file.");
                    return;
                }
                System.out.println("Client requested file (socket): " + fileName);

                File file = new File(directory, fileName);
                if (file.exists() && file.isFile()) {
                    writer.write("FOUND\n");
                    writer.flush();

                    long fileSize = file.length();
                    dataOut.writeLong(fileSize);

                    try (FileInputStream fis = new FileInputStream(file)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            dataOut.write(buffer, 0, bytesRead);
                        }
                    }
                    System.out.println("Socket: Sent file successfully: " + file.getName());
                } else {
                    writer.write("NOT_FOUND\n");
                    writer.flush();
                    System.out.println("Socket: File not found: " + fileName);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try { clientSocket.close(); } catch (IOException ignored) {}
                System.out.println("Socket client disconnected.");
            }
        }
    }

    // ---------- HTTP Server ----------
    private static void startHttpFileServer(int port, File directory) throws IOException {
        System.out.println("HTTP file server started on port " + port);
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);

        // GET
        httpServer.createContext("/download", exchange -> {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    System.out.println("HTTP 405: Method Not Allowed (download)");
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                String query = exchange.getRequestURI().getRawQuery();
                String filename = null;
                if (query != null) {
                    for (String part : query.split("&")) {
                        if (part.startsWith("filename=")) {
                            filename = URLDecoder.decode(part.substring("filename=".length()), "UTF-8");
                        }
                    }
                }

                if (filename == null || filename.isEmpty()) {
                    String msg = "HTTP 400: Missing filename parameter";
                    System.out.println(msg);
                    byte[] resp = msg.getBytes();
                    exchange.sendResponseHeaders(400, resp.length);
                    exchange.getResponseBody().write(resp);
                    exchange.close();
                    return;
                }

                File file = new File(directory, filename);
                if (!file.exists() || !file.isFile()) {
                    String msg = "HTTP 404: File Not Found (" + filename + ")";
                    System.out.println(msg);
                    byte[] resp = msg.getBytes();
                    exchange.sendResponseHeaders(404, resp.length);
                    exchange.getResponseBody().write(resp);
                    exchange.close();
                    return;
                }

                System.out.println("HTTP 200: Sending file " + filename);
                exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
                exchange.sendResponseHeaders(200, file.length());

                try (OutputStream os = exchange.getResponseBody(); FileInputStream fis = new FileInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, read);
                    }
                }
            } finally {
                exchange.close();
            }
        });

        // POST
        httpServer.createContext("/upload", exchange -> {
            try {
                if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    System.out.println("HTTP 405: Method Not Allowed (upload)");
                    exchange.sendResponseHeaders(405, -1);
                    return;
                }

                String query = exchange.getRequestURI().getRawQuery();
                String filenameParam = "upload_" + System.currentTimeMillis();
                if (query != null) {
                    for (String part : query.split("&")) {
                        if (part.startsWith("filename=")) {
                            filenameParam = "upload_" + System.currentTimeMillis() + "_" +
                                    URLDecoder.decode(part.substring("filename=".length()), "UTF-8");
                        }
                    }
                }

                File outFile = new File(directory, filenameParam);
                try (InputStream is = exchange.getRequestBody(); FileOutputStream fos = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                    }
                }

                String response = "HTTP 200: File uploaded successfully as " + outFile.getName();
                System.out.println(response);
                byte[] respBytes = response.getBytes();
                exchange.sendResponseHeaders(200, respBytes.length);
                exchange.getResponseBody().write(respBytes);
            } finally {
                exchange.close();
            }
        });

        httpServer.setExecutor(Executors.newFixedThreadPool(10));
        httpServer.start();
        System.out.println("HTTP server ready at http://localhost:" + port + " (/download, /upload)");
    }
}
