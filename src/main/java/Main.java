import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Main {
    // Port number server listens to
    private static final int PORT = 4221;
    // Default directory to the current working directory
    private static String fileDirectory = System.getProperty("user.dir");

    // Main method
    public static void main(String[] args) {
        // Parse command line arguments
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--directory") && i + 1 < args.length) {
                fileDirectory = args[i + 1];
                break;
            }
        }

        // Ensure directory exists
        Path dirPath = Paths.get(fileDirectory);
        if (!Files.exists(dirPath)) {
            System.err.println("Specified directory does not exist: " + fileDirectory);
            System.err.println("Using current working directory instead.");
            fileDirectory = System.getProperty("user.dir");
        }

        // Log the directory being used
        System.out.println("File directory set to: " + fileDirectory);

        try {
            // Create ServerSocket to listen on specified port
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Server started on port " + PORT);

            // Main server loop
            while (true) {
                // Wait for a client to connect
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                // Create new thread to handle this client
                Thread clientThread = new Thread(() -> handleClient(clientSocket));
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
            System.exit(1);
        }
    }

    // Handle a client connection in separate thread
    private static void handleClient(Socket clientSocket) {
        try {
            // Get InputStreamReader to read requests from client
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));

            // Read first line of request
            String requestLine = in.readLine();
            System.out.println("Request line: " + requestLine);

            // Parse headers
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                int colonIndex = headerLine.indexOf(':');
                if (colonIndex > 0) {
                    String headerName = headerLine.substring(0, colonIndex).trim();
                    String headerValue = headerLine.substring(colonIndex + 1).trim();
                    // Store header name (as lowercase)
                    headers.put(headerName.toLowerCase(), headerValue);
                }
            }

            // Get OutputStream to send data back to client
            OutputStream out = clientSocket.getOutputStream();

            // Extract method and path from request line
            String[] requestParts = requestLine.split(" ");
            String method = requestParts[0]; // GET, POST, etc.
            String path = requestParts.length > 1 ? requestParts[1] : "/";
            System.out.println("Method: " + method + ", Path: " + path);

            // Check for gzip support
            boolean clientSupportsGzip = false;
            String acceptEncoding = headers.get("accept-encoding");
            if (acceptEncoding != null) {
                // Split Accept-Encoding header by commas
                List<String> encodings = Arrays.stream(acceptEncoding.split(",")).map(String::trim).collect(Collectors.toList());

                // Check if gzip is in encodings list
                clientSupportsGzip = encodings.contains("gzip");
                System.out.println("Supported encodings: " + encodings);
                System.out.println("Client supports gzip:" + clientSupportsGzip);
            }

            // Handle different paths
            if (path.equals("/")) {
                // Root path -> respond with 200 OK
                String response = "HTTP/1.1 200 OK\r\n\r\n";
                out.write(response.getBytes());
            } else if (path.startsWith("/echo/")) {
                // Extract string after "/echo/"
                String echoString = path.substring("/echo/".length());
                System.out.println("Echo string: " + echoString);

                // Calculate content length (in bytes)
                int contentLength = echoString.getBytes().length;

                // Form response with headers and body
                StringBuilder responseBuilder = new StringBuilder();
                responseBuilder.append("HTTP/1.1 200 OK\r\n");
                responseBuilder.append("Content-Type: text/plain\r\n");

                // Add Content-Encoding header if client supports gzip
                if (clientSupportsGzip) {
                    responseBuilder.append("Content-Encoding: gzip\r\n");
                    // Note: For this stage, we're not actually compressing the content yet
                }

                responseBuilder.append("Content-Length: " + contentLength + "\r\n");
                responseBuilder.append("\r\n");
                responseBuilder.append(echoString);

                out.write(responseBuilder.toString().getBytes());
            } else if (path.equals("/user-agent")) {
                // Get User-Agent header value
                String userAgent = headers.get("user-agent");
                System.out.println("User-Agent: " + userAgent);

                if (userAgent != null) {
                    // Calculate content length (in bytes)
                    int contentLength = userAgent.getBytes().length;

                    // Form response with headers and body
                    StringBuilder responseBuilder = new StringBuilder();
                    responseBuilder.append("HTTP/1.1 200 OK\r\n");
                    responseBuilder.append("Content-Type: text/plain\r\n");

                    // Add Content-Encoding header if client supports gzip
                    if (clientSupportsGzip) {
                        responseBuilder.append("Content-Encoding: gzip\r\n");
                        // Note: For this stage, we're not actually compressing the content yet
                    }

                    responseBuilder.append("Content-Length: " + contentLength + "\r\n");
                    responseBuilder.append("\r\n");
                    responseBuilder.append(userAgent);

                    out.write(responseBuilder.toString().getBytes());
                } else {
                    // User-Agent missing -> respond with 400 Bad Request
                    String response = "HTTP/1.1 400 Bad Request\r\n\r\n";
                    out.write(response.getBytes());
                }
            } else if (path.startsWith("/files/")) {
                // Handle file operations (both GET and POST)
                String fileName = path.substring("/files/".length());
                Path filePath = Paths.get(fileDirectory, fileName);

                if (method.equals("GET")) {
                    // Handle GET request for files
                    File file = filePath.toFile();

                    if (file.exists() && file.isFile()) {
                        try {
                            byte[] fileContent = Files.readAllBytes(filePath);

                            StringBuilder responseBuilder = new StringBuilder();
                            responseBuilder.append("HTTP/1.1 200 OK\r\n");
                            responseBuilder.append("Content-Type: application/octet-stream\r\n");

                            // Add Content-Encoding header if client supports gzip
                            if (clientSupportsGzip) {
                                responseBuilder.append("Content-Encoding: gzip\r\n");
                                // Note: For this stage, we're not actually compressing the content yet
                            }

                            responseBuilder.append("Content-Length: " + fileContent.length + "\r\n");
                            responseBuilder.append("\r\n");

                            out.write(responseBuilder.toString().getBytes());
                            out.write(fileContent);
                        } catch (IOException e) {
                            String response = "HTTP/1.1 500 Internal Server Error\r\n\r\n";
                            out.write(response.getBytes());
                            System.err.println("File reading error: " + e.getMessage());
                        }
                    } else {
                        String response = "HTTP/1.1 404 Not Found\r\n\r\n";
                        out.write(response.getBytes());
                    }
                } else if (method.equals("POST")) {
                    // Handle POST request to create a new file
                    String contentLengthStr = headers.get("content-length");

                    if (contentLengthStr != null) {
                        int contentLength = Integer.parseInt(contentLengthStr);

                        // Read the request body
                        char[] bodyChars = new char[contentLength];
                        in.read(bodyChars, 0, contentLength);
                        String bodyContent = new String(bodyChars);

                        // Create the file
                        try {
                            // Create parent directories if they don't exist
                            File parentDir = filePath.getParent().toFile();
                            if (!parentDir.exists()) {
                                parentDir.mkdirs();
                            }

                            // Write the file content
                            Files.write(filePath, bodyContent.getBytes());

                            // Send 201 Created response
                            String response = "HTTP/1.1 201 Created\r\n\r\n";
                            out.write(response.getBytes());

                            System.out.println("File created: " + fileName);
                        } catch (IOException e) {
                            String response = "HTTP/1.1 500 Internal Server Error\r\n\r\n";
                            out.write(response.getBytes());
                            System.err.println("File writing error: " + e.getMessage());
                        }
                    } else {
                        // Content-Length header missing
                        String response = "HTTP/1.1 400 Bad Request\r\n\r\n";
                        out.write(response.getBytes());
                    }
                } else {
                    // Method not supported
                    String response = "HTTP/1.1 405 Method Not Allowed\r\n\r\n";
                    out.write(response.getBytes());
                }
            } else {
                // Any other path -> respond with 404 Not Found
                String response = "HTTP/1.1 404 Not Found\r\n\r\n";
                out.write(response.getBytes());
            }

            // Close resources
            out.flush();
            in.close();
            clientSocket.close();

        } catch (IOException e) {
            System.err.println("Error handling client connection: " + e.getMessage());
            try {
                clientSocket.close();
            } catch (IOException ex) {
                System.err.println("Error closing client socket: " + ex.getMessage());
            }
        }
    }
}