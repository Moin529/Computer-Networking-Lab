// ip : 10.33.3.18

import java.io.*;
import java.net.*;

public class _30_36Client {
    private static final String SOCKET_SERVER_IP = "localhost";
    private static final int SOCKET_SERVER_PORT = 5000;
    private static final String HTTP_SERVER_IP = "localhost";
    private static final int HTTP_SERVER_PORT = 8080;

    public static void main(String[] args) {
        try (BufferedReader console = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.println("\n===== Client Menu =====");
                System.out.println("1. Socket: Download file");
                System.out.println("2. HTTP: Download file (GET)");
                System.out.println("3. HTTP: Upload file (POST)");
                System.out.println("4. Exit");
                System.out.print("Choice: ");

                String choice = console.readLine();
                switch (choice) {
                    case "1": socketDownload(console); break;
                    case "2": httpDownload(console); break;
                    case "3": httpUpload(console); break;
                    case "4": return;
                    default: System.out.println("Invalid choice");
                }
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private static void showFileListFromSocket() {
        try (Socket socket = new Socket(SOCKET_SERVER_IP, SOCKET_SERVER_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (line.equals("Enter the file name you want to download:")) break;
            }
        } catch (IOException e) {
            System.out.println("Could not fetch file list from socket server.");
        }
    }

    private static void socketDownload(BufferedReader console) {
        try (Socket socket = new Socket(SOCKET_SERVER_IP, SOCKET_SERVER_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
             DataInputStream dataIn = new DataInputStream(socket.getInputStream())) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (line.equals("Enter the file name you want to download:")) break;
            }

            System.out.print("Enter filename: ");
            String fileName = console.readLine();
            writer.write(fileName + "\n");
            writer.flush();

            String response = reader.readLine();
            if ("FOUND".equalsIgnoreCase(response)) {
                long fileSize = dataIn.readLong();
                File outFile = new File("Downloaded_" + fileName);
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[4096];
                    long remaining = fileSize;
                    int bytesRead;
                    while (remaining > 0 &&
                           (bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        remaining -= bytesRead;
                    }
                }
                System.out.println("Socket download OK: " + outFile.getName());
            } else {
                System.out.println("Socket: File not found on server");
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    // HTTP GET download
    private static void httpDownload(BufferedReader console) {
        try {
            System.out.println("\nAvailable files (via socket server):");
            showFileListFromSocket();

            System.out.print("Enter filename to download (HTTP): ");
            String filename = console.readLine();

            String urlStr = "http://" + HTTP_SERVER_IP + ":" + HTTP_SERVER_PORT + "/download?filename=" +
                            URLEncoder.encode(filename, "UTF-8");
            HttpURLConnection conn = (HttpURLConnection) new URI(urlStr).toURL().openConnection();
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            System.out.println("HTTP GET Response: " + code + " " + conn.getResponseMessage());

            if (code == 200) {
                File outFile = new File("Downloaded_" + filename);
                try (InputStream is = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = is.read(buffer)) != -1) fos.write(buffer, 0, read);
                }
                System.out.println("File downloaded via HTTP: " + outFile.getName());
            } else if (code == 404) {
                System.out.println("HTTP 404: File not found");
            } else if (code == 405) {
                System.out.println("HTTP 405: Method Not Allowed");
            }
            conn.disconnect();
        } catch (Exception e) { e.printStackTrace(); }
    }

    // HTTP POST upload
    private static void httpUpload(BufferedReader console) {
        try {
            File uploadsDir = new File("Uploads");
            if (!uploadsDir.exists() || !uploadsDir.isDirectory()) {
                System.out.println("'Uploads' folder not found on client side. Please create it.");
                return;
            }

            File[] files = uploadsDir.listFiles();
            if (files == null || files.length == 0) {
                System.out.println("No files available in 'Uploads' folder.");
                return;
            }

            System.out.println("\nFiles available in 'Uploads':");
            for (File f : files) {
                if (f.isFile()) {
                    System.out.println(" - " + f.getName());
                }
            }

            System.out.print("Enter filename to upload: ");
            String fileName = console.readLine();
            File file = new File(uploadsDir, fileName);

            if (!file.exists() || !file.isFile()) {
                System.out.println("File not found in 'Uploads': " + fileName);
                return;
            }

            String urlStr = "http://" + HTTP_SERVER_IP + ":" + HTTP_SERVER_PORT +
                            "/upload?filename=" + URLEncoder.encode(file.getName(), "UTF-8");
            HttpURLConnection conn = (HttpURLConnection) new URI(urlStr).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream(); FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = fis.read(buffer)) != -1) os.write(buffer, 0, read);
            }

            int code = conn.getResponseCode();
            System.out.println("HTTP POST Response: " + code + " " + conn.getResponseMessage());

            if (code == 200) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line; while ((line = br.readLine()) != null) System.out.println(line);
                }
            }
            conn.disconnect();
        } catch (Exception e) { e.printStackTrace(); }
    }
}
