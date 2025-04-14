import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * A robust and efficient HTTP/1.1 server designed for performance and clarity.
 *
 * Features:
 * - Asynchronous request handling via a thread pool.
 * - Implements core HTTP/1.1 with persistent connections.
 * - Efficient content compression (gzip) with content negotiation.
 * - Comprehensive file serving (GET) with content type detection.
 * - File creation/overwrite (POST) with proper handling of request bodies.
 * - Robust error handling and logging.
 * - Clean, modular design promoting maintainability and extensibility.
 */
public class Main {

    private static final int DEFAULT_PORT = 4221;
    private static final String DEFAULT_FILE_DIRECTORY = System.getProperty("user.dir");
    private static final int SOCKET_TIMEOUT_MS = 30000;
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final ExecutorService threadPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    private static String fileDirectory;

    /**
     * Entry point for the HTTP server.
     *
     * Parses command-line arguments, configures the server, and starts the main loop
     * for accepting client connections. Utilizes a thread pool for efficient handling
     * of concurrent requests.
     *
     * @param args Command-line arguments (supports --directory and --port)
     */
    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        fileDirectory = DEFAULT_FILE_DIRECTORY;

        // Parse command-line arguments with enhanced clarity
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--directory":
                    if (i + 1 < args.length) {
                        fileDirectory = args[++i];
                    } else {
                        System.err.println("Error: --directory option requires a path.");
                        System.exit(1);
                    }
                    break;
                case "--port":
                    if (i + 1 < args.length) {
                        try {
                            port = Integer.parseInt(args[++i]);
                            if (port < 1 || port > 65535) {
                                throw new NumberFormatException("Port number out of range.");
                            }
                        } catch (NumberFormatException e) {
                            System.err.println("Error: Invalid port number: " + args[i]);
                            System.exit(1);
                        }
                    } else {
                        System.err.println("Error: --port option requires a number.");
                        System.exit(1);
                    }
                    break;
                default:
                    System.err.println("Usage: java Main [--directory <path>] [--port <number>]");
                    System.exit(1);
            }
        }

        // Validate and log the configured file directory
        Path dirPath = Paths.get(fileDirectory);
        if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
            System.err.println("Error: Specified directory does not exist or is not a directory: " + fileDirectory);
            System.err.println("Using default directory: " + DEFAULT_FILE_DIRECTORY);
            fileDirectory = DEFAULT_FILE_DIRECTORY;
        }
        System.out.println("Serving files from: " + fileDirectory);
        System.out.println("Server listening on port: " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from: " + clientSocket.getInetAddress());
                clientSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
                threadPool.submit(() -> handleClient(clientSocket)); // Delegate to thread pool
            }
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
            threadPool.shutdownNow();
            System.exit(1);
        }
    }

    /**
     * Handles a single client connection.
     *
     * Reads and processes multiple HTTP requests over a persistent connection until
     * the client closes the connection or a timeout occurs. Employs a structured
     * approach for request parsing, routing, and response generation.
     *
     * @param clientSocket The socket connected to the client.
     */
    private static void handleClient(Socket clientSocket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                OutputStream out = new BufferedOutputStream(clientSocket.getOutputStream())
        ) {
            boolean keepAlive = true;
            while (keepAlive) {
                try {
                    HttpRequest request = parseRequest(in);
                    if (request == null) {
                        keepAlive = false;
                        break;
                    }
                    HttpResponse response = routeRequest(request);
                    sendResponse(response, out, request.headers().get("accept-encoding"));
                    keepAlive = request.headers().getOrDefault("connection", "keep-alive")
                            .equalsIgnoreCase("keep-alive") && response.statusCode() != 400 && response.statusCode() != 404 && response.statusCode() != 500;
                } catch (SocketTimeoutException e) {
                    System.out.println("Connection timed out.");
                    keepAlive = false;
                } catch (SocketException e) {
                    System.out.println("Client disconnected unexpectedly.");
                    keepAlive = false;
                } catch (IOException e) {
                    System.err.println("Error processing request: " + e.getMessage());
                    HttpResponse errorResponse = new HttpResponse.Builder(500)
                            .withBody("Internal Server Error".getBytes())
                            .withHeader("Content-Type", "text/plain")
                            .build();
                    sendResponse(errorResponse, out, null);
                    keepAlive = false;
                }
            }
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
                System.out.println("Closed connection with: " + clientSocket.getInetAddress());
            } catch (IOException e) {
                System.err.println("Error closing socket: " + e.getMessage());
            }
        }
    }

    /**
     * Parses an HTTP request from the input stream.
     *
     * Reads the request line and subsequent headers, storing them in a structured
     * HttpRequest object. Handles potential EOF and malformed requests.
     *
     * @param in The BufferedReader associated with the client socket.
     * @return An HttpRequest object if parsing is successful, null if the connection is closed.
     * @throws IOException If an I/O error occurs during reading.
     */
    private static HttpRequest parseRequest(BufferedReader in) throws IOException {
        String requestLine = in.readLine();
        if (requestLine == null) {
            return null; // Client closed connection
        }
        System.out.println("Request Line: " + requestLine);

        String[] parts = requestLine.split(" ");
        if (parts.length != 3) {
            return null; // Malformed request line
        }
        String method = parts[0].toUpperCase(Locale.ROOT);
        String path = parts[1];

        Map<String, String> headers = new HashMap<>();
        String headerLine;
        while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
            int colonIndex = headerLine.indexOf(':');
            if (colonIndex > 0) {
                String name = headerLine.substring(0, colonIndex).trim().toLowerCase(Locale.ROOT);
                String value = headerLine.substring(colonIndex + 1).trim();
                headers.put(name, value);
            }
        }

        // Handle request body for POST requests
        byte[] body = null;
        if (method.equals("POST")) {
            String contentLengthStr = headers.get("content-length");
            if (contentLengthStr != null) {
                try {
                    int contentLength = Integer.parseInt(contentLengthStr);
                    char[] bodyChars = new char[contentLength];
                    int bytesRead = in.read(bodyChars, 0, contentLength);
                    if (bytesRead == contentLength) {
                        body = new String(bodyChars).getBytes();
                    } else {
                        System.err.println("Error reading full request body.");
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Invalid Content-Length: " + contentLengthStr);
                }
            }
        }

        return new HttpRequest(method, path, headers, body);
    }

    /**
     * Routes the incoming HTTP request to the appropriate handler.
     *
     * Based on the request path, this method dispatches the request to specific
     * handler functions for different endpoints.
     *
     * @param request The HttpRequest object containing the parsed request.
     * @return An HttpResponse object generated by the handler.
     * @throws IOException If an I/O error occurs during handling.
     */
    private static HttpResponse routeRequest(HttpRequest request) throws IOException {
        String path = request.path();
        String method = request.method();

        System.out.println("Routing: " + method + " " + path);

        if (path.equals("/")) {
            return new HttpResponse.Builder(200).build();
        } else if (path.startsWith("/echo/")) {
            return handleEcho(path);
        } else if (path.equals("/user-agent")) {
            return handleUserAgent(request.headers().get("user-agent"));
        } else if (path.startsWith("/files/")) {
            return handleFiles(path.substring("/files/".length()), method, request.headers(), request.body());
        } else {
            return new HttpResponse.Builder(404).build();
        }
    }

    /**
     * Handles the /echo/ endpoint.
     *
     * Extracts the text after /echo/ and returns it as a plain text response.
     *
     * @param path The request path.
     * @return An HttpResponse object containing the echoed text.
     */
    private static HttpResponse handleEcho(String path) {
        String echoText = path.substring("/echo/".length());
        return new HttpResponse.Builder(200)
                .withBody(echoText.getBytes())
                .withHeader("Content-Type", "text/plain")
                .build();
    }

    /**
     * Handles the /user-agent endpoint.
     *
     * Returns the value of the User-Agent header in the request.
     *
     * @param userAgent The User-Agent header value.
     * @return An HttpResponse object containing the User-Agent string.
     */
    private static HttpResponse handleUserAgent(String userAgent) {
        if (userAgent != null) {
            return new HttpResponse.Builder(200)
                    .withBody(userAgent.getBytes())
                    .withHeader("Content-Type", "text/plain")
                    .build();
        } else {
            return new HttpResponse.Builder(400)
                    .withBody("User-Agent header required.".getBytes())
                    .withHeader("Content-Type", "text/plain")
                    .build();
        }
    }

    /**
     * Handles the /files/ endpoint for GET and POST requests.
     *
     * For GET requests, serves the content of the specified file. Supports gzip
     * compression if the client accepts it. For POST requests, creates or overwrites
     * the file with the request body.
     *
     * @param fileName The name of the file to handle.
     * @param method   The HTTP method (GET or POST).
     * @param headers  The request headers.
     * @param body     The request body (for POST).
     * @return An HttpResponse object containing the file content or status.
     * @throws IOException If an I/O error occurs during file operations.
     */
    private static HttpResponse handleFiles(String fileName, String method, Map<String, String> headers, byte[] body) throws IOException {
        Path filePath = Paths.get(fileDirectory, fileName);
        File file = filePath.toFile();

        switch (method) {
            case "GET":
                if (file.exists() && file.isFile()) {
                    byte[] fileContent = Files.readAllBytes(filePath);
                    String contentType = Files.probeContentType(filePath);
                    if (contentType == null) {
                        contentType = "application/octet-stream";
                    }
                    HttpResponse.Builder builder = new HttpResponse.Builder(200)
                            .withBody(fileContent)
                            .withHeader("Content-Type", contentType);
                    if (shouldCompress(headers.get("accept-encoding"))) {
                        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                             GZIPOutputStream gzipOS = new GZIPOutputStream(bos)) {
                            gzipOS.write(fileContent);
                            builder.withBody(bos.toByteArray())
                                    .withHeader("Content-Encoding", "gzip");
                        }
                    }
                    return builder.build();
                } else {
                    return new HttpResponse.Builder(404).build();
                }
            case "POST":
                if (body != null) {
                    Files.createDirectories(filePath.getParent());
                    Files.write(filePath, body, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    return new HttpResponse.Builder(201).build();
                } else {
                    return new HttpResponse.Builder(400).build();
                }
            default:
                return new HttpResponse.Builder(405).build();
        }
    }

    /**
     * Sends the HTTP response to the client.
     *
     * Writes the status line, headers, and body of the HttpResponse to the output stream.
     * Handles content compression based on the Accept-Encoding header.
     *
     * @param response        The HttpResponse object to send.
     * @param out             The OutputStream associated with the client socket.
     * @param acceptEncoding  The Accept-Encoding header from the client request.
     * @throws IOException If an I/O error occurs during writing.
     */
    private static void sendResponse(HttpResponse response, OutputStream out, String acceptEncoding) throws IOException {
        StringBuilder responseHeader = new StringBuilder();
        responseHeader.append("HTTP/1.1 ").append(response.statusCode()).append(" ").append(getStatusMessage(response.statusCode())).append("\r\n");
        for (Map.Entry<String, String> header : response.headers().entrySet()) {
            responseHeader.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        responseHeader.append("Content-Length: ").append(response.body() != null ? response.body().length : 0).append("\r\n");
        if (acceptEncoding != null && acceptEncoding.contains("gzip") && response.headers().containsKey("Content-Encoding") && response.headers().get("Content-Encoding").equals("gzip")) {
            // No need to add Content-Length again if it's already set for compressed data
        }
        responseHeader.append("\r\n");

        out.write(responseHeader.toString().getBytes());
        if (response.body() != null) {
            out.write(response.body());
        }
        out.flush();
    }

    /**
     * Determines if the server should compress the response based on the Accept-Encoding header.
     *
     * @param acceptEncoding The Accept-Encoding header value.
     * @return True if gzip compression is acceptable by the client, false otherwise.
     */
    private static boolean shouldCompress(String acceptEncoding) {
        return acceptEncoding != null && acceptEncoding.contains("gzip");
    }

    /**
     * Returns the standard HTTP status message for a given status code.
     *
     * @param statusCode The HTTP status code.
     * @return The corresponding status message.
     */
    private static String getStatusMessage(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            default -> "Unknown Status";
        };
    }

    /**
     * Represents an HTTP request.
     */
    private record HttpRequest(String method, String path, Map<String, String> headers, byte[] body) {
    }

    /**
     * Represents an HTTP response.
     */
    private record HttpResponse(int statusCode, Map<String, String> headers, byte[] body) {
        /**
         * Builder class for constructing HttpResponse objects with a fluent API.
         */
        public static class Builder {
            private final int statusCode;
            private final Map<String, String> headers = new HashMap<>();
            private byte[] body;

            public Builder(int statusCode) {
                this.statusCode = statusCode;
            }

            public Builder withHeader(String name, String value) {
                this.headers.put(name, value);
                return this;
            }

            public Builder withBody(byte[] body) {
                this.body = body;
                return this;
            }

            public HttpResponse build() {
                return new HttpResponse(statusCode, Map.copyOf(headers), body);
            }
        }
    }
}
