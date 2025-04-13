import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * HTTP/1.1 Server Implementation
 * 
 * A lightweight, multi-threaded HTTP server with support for:
 * - Persistent connections
 * - Content compression (gzip)
 * - File operations (GET/POST)
 * - Multiple concurrent client connections
 */
public class Main {
    // Port number server listens to
    private static final int PORT = 4221;
    // Default directory to the current working directory
    private static String fileDirectory = System.getProperty("user.dir");
    // Socket timeout in milliseconds (30 seconds)
    private static final int SOCKET_TIMEOUT = 30000;

    /**
     * Entry point for the HTTP server
     * 
     * Initializes the server, parses command-line arguments,
     * and enters the main connection acceptance loop.
     * 
     * @param args Command line arguments (supports --directory)
     */
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

            // Main server loop - continuously accept new client connections
            while (true) {
                // Wait for a client to connect (blocks until connection received)
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                // Set socket timeout to prevent resource leaks from abandoned connections
                clientSocket.setSoTimeout(SOCKET_TIMEOUT);

                // Create new thread to handle this client - enables concurrent connections
                Thread clientThread = new Thread(() -> handleClient(clientSocket));
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Compresses data using the GZIP algorithm
     * 
     * @param data The byte array to compress
     * @return A new byte array containing the compressed data
     * @throws IOException If compression fails
     */
    private static byte[] compressData(byte[] data) throws IOException {
        // Use ByteArrayOutputStream to capture compressed output
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream(data.length);
        
        // GZIPOutputStream automatically handles compression, try-with-resources ensures proper closure
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteStream)) {
            gzipOutputStream.write(data);
            // Stream is automatically flushed and closed by try-with-resources
        }
        return byteStream.toByteArray();
    }

    /**
     * Handles a client connection in a separate thread
     * 
     * This method processes multiple HTTP requests over a persistent connection.
     * It parses requests, routes them to appropriate handlers, and sends responses.
     * The connection remains open until explicitly closed or a timeout/error occurs.
     * 
     * @param clientSocket The socket connected to the client
     */
    private static void handleClient(Socket clientSocket) {
        try {
            // Set up input/output streams for the socket
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
            OutputStream out = clientSocket.getOutputStream();

            // HTTP/1.1 connections are persistent by default
            boolean keepConnectionOpen = true;

            // Process requests in a loop for persistent connections
            while (keepConnectionOpen) {
                try {
                    // Read first line of request
                    String requestLine = in.readLine();

                    // If request line is null, client closed the connection
                    if (requestLine == null) {
                        System.out.println("Client closed the connection");
                        break;
                    }

                    System.out.println("Request line: " + requestLine);

                    // Parse headers into a map of header name -> header value
                    Map<String, String> headers = new HashMap<>();
                    String headerLine;
                    // Headers end with an empty line (CRLF)
                    while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                        int colonIndex = headerLine.indexOf(':');
                        if (colonIndex > 0) {
                            String headerName = headerLine.substring(0, colonIndex).trim();
                            String headerValue = headerLine.substring(colonIndex + 1).trim();
                            // Store header names as lowercase for case-insensitive matching
                            headers.put(headerName.toLowerCase(), headerValue);
                        }
                    }

                    // Check if the client wants to close the connection
                    String connectionHeader = headers.get("connection");
                    if (connectionHeader != null && connectionHeader.equalsIgnoreCase("close")) {
                        keepConnectionOpen = false;
                        System.out.println("Client requested connection close");
                    }

                    // Extract method and path from request line
                    String[] requestParts = requestLine.split(" ");
                    String method = requestParts[0]; // GET, POST, etc.
                    String path = requestParts.length > 1 ? requestParts[1] : "/";
                    System.out.println("Method: " + method + ", Path: " + path);

                    // Content negotiation: check for gzip support via Accept-Encoding header
                    boolean clientSupportsGzip = false;
                    String acceptEncoding = headers.get("accept-encoding");
                    if (acceptEncoding != null) {
                        // Split the Accept-Encoding header by commas and trim each value
                        List<String> encodings = Arrays.stream(acceptEncoding.split(","))
                                .map(String::trim)
                                .collect(Collectors.toList());

                        // Check if gzip is in the list of supported encodings
                        clientSupportsGzip = encodings.contains("gzip");
                        System.out.println("Supported encodings: " + encodings);
                        System.out.println("Client supports gzip: " + clientSupportsGzip);
                    }

                    // Route the request to the appropriate handler based on path
                    if (path.equals("/")) {
                        // Root path -> respond with 200 OK
                        String response = "HTTP/1.1 200 OK\r\n\r\n";
                        out.write(response.getBytes());
                    } else if (path.startsWith("/echo/")) {
                        // Handle /echo/ endpoint - returns the path segment as plain text
                        handleEchoRequest(path, clientSupportsGzip, keepConnectionOpen, out);
                    } else if (path.equals("/user-agent")) {
                        // Handle /user-agent endpoint - returns the User-Agent header value
                        handleUserAgentRequest(headers, clientSupportsGzip, keepConnectionOpen, out);
                    } else if (path.startsWith("/files/")) {
                        // Handle /files/ endpoint - file operations (GET/POST)
                        handleFileRequest(path, method, headers, clientSupportsGzip, keepConnectionOpen, in, out);
                    } else {
                        // Any other path -> respond with 404 Not Found
                        String response = "HTTP/1.1 404 Not Found\r\n\r\n";
                        out.write(response.getBytes());
                    }

                    // Flush the output to ensure the response is sent immediately
                    out.flush();

                    // If the connection is not to be kept open, break the loop
                    if (!keepConnectionOpen) {
                        break;
                    }

                } catch (SocketTimeoutException e) {
                    // Socket timeout occurred, close the connection
                    System.out.println("Socket timeout, closing connection");
                    break;
                } catch (SocketException e) {
                    // Socket error (likely client disconnected unexpectedly)
                    System.out.println("Socket error: " + e.getMessage());
                    break;
                } catch (IOException e) {
                    // Other IO errors
                    System.err.println("Error handling request: " + e.getMessage());
                    break;
                }
            }

            // Clean up resources when connection ends
            System.out.println("Closing connection with client");
            in.close();
            out.close();
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

    /**
     * Handles requests to the /echo/ endpoint
     * 
     * Extracts the string after "/echo/" and returns it as plain text.
     * Applies gzip compression if the client supports it.
     * 
     * @param path The request path
     * @param clientSupportsGzip Whether the client supports gzip compression
     * @param keepConnectionOpen Whether to keep the connection open after this request
     * @param out The output stream to write the response to
     * @throws IOException If an I/O error occurs
     */
    private static void handleEchoRequest(String path, boolean clientSupportsGzip, 
                                         boolean keepConnectionOpen, OutputStream out) throws IOException {
        // Extract string after "/echo/"
        String echoString = path.substring("/echo/".length());
        System.out.println("Echo string: " + echoString);

        // Convert string to bytes using default charset
        byte[] responseBody = echoString.getBytes();
        byte[] compressedResponseBody = null;

        if (clientSupportsGzip) {
            try {
                // Compress the response body if client supports gzip
                compressedResponseBody = compressData(responseBody);
                System.out.println("Original body size: " + responseBody.length + " bytes");
                System.out.println("Compressed body size: " + compressedResponseBody.length + " bytes");
            } catch (IOException e) {
                System.err.println("Compression error: " + e.getMessage());
                // Fall back to uncompressed if compression fails
                clientSupportsGzip = false;
            }
        }

        // Choose the appropriate response body based on compression support
        byte[] finalResponseBody = clientSupportsGzip ? compressedResponseBody : responseBody;
        int contentLength = finalResponseBody.length;

        // Build HTTP response with appropriate headers
        StringBuilder responseBuilder = new StringBuilder();
        responseBuilder.append("HTTP/1.1 200 OK\r\n");
        responseBuilder.append("Content-Type: text/plain\r\n");

        // Add Content-Encoding header if using gzip
        if (clientSupportsGzip) {
            responseBuilder.append("Content-Encoding: gzip\r\n");
        }

        // Content-Length is required for persistent connections
        responseBuilder.append("Content-Length: " + contentLength + "\r\n");

        // Add connection header if client requested connection close
        if (!keepConnectionOpen) {
            responseBuilder.append("Connection: close\r\n");
        }

        // Empty line signifies end of headers
        responseBuilder.append("\r\n");

        // Send headers and body separately (since body might be binary data)
        out.write(responseBuilder.toString().getBytes());
        out.write(finalResponseBody);
    }

    /**
     * Handles requests to the /user-agent endpoint
     * 
     * Returns the User-Agent header value as plain text.
     * Applies gzip compression if the client supports it.
     * 
     * @param headers The request headers
     * @param clientSupportsGzip Whether the client supports gzip compression
     * @param keepConnectionOpen Whether to keep the connection open after this request
     * @param out The output stream to write the response to
     * @throws IOException If an I/O error occurs
     */
    private static void handleUserAgentRequest(Map<String, String> headers, boolean clientSupportsGzip,
                                               boolean keepConnectionOpen, OutputStream out) throws IOException {
        // Get User-Agent header value
        String userAgent = headers.get("user-agent");
        System.out.println("User-Agent: " + userAgent);

        if (userAgent != null) {
            // Convert user agent string to bytes
            byte[] responseBody = userAgent.getBytes();
            byte[] compressedResponseBody = null;

            // Compress if supported and enabled
            if (clientSupportsGzip) {
                try {
                    compressedResponseBody = compressData(responseBody);
                } catch (IOException e) {
                    System.err.println("Compression error: " + e.getMessage());
                    // Fall back to uncompressed if compression fails
                    clientSupportsGzip = false;
                }
            }

            // Select appropriate body based on compression setting
            byte[] finalResponseBody = clientSupportsGzip ? compressedResponseBody : responseBody;
            int contentLength = finalResponseBody.length;

            // Build HTTP response
            StringBuilder responseBuilder = new StringBuilder();
            responseBuilder.append("HTTP/1.1 200 OK\r\n");
            responseBuilder.append("Content-Type: text/plain\r\n");

            if (clientSupportsGzip) {
                responseBuilder.append("Content-Encoding: gzip\r\n");
            }

            responseBuilder.append("Content-Length: " + contentLength + "\r\n");

            if (!keepConnectionOpen) {
                responseBuilder.append("Connection: close\r\n");
            }

            responseBuilder.append("\r\n");

            // Send response
            out.write(responseBuilder.toString().getBytes());
            out.write(finalResponseBody);
        } else {
            // User-Agent header is required for this endpoint
            String response = "HTTP/1.1 400 Bad Request\r\n\r\n";
            out.write(response.getBytes());
        }
    }

    /**
     * Handles requests to the /files/ endpoint
     * 
     * For GET requests: Returns the contents of the specified file.
     * For POST requests: Creates or overwrites a file with the request body.
     * Applies gzip compression for GET responses if the client supports it.
     * 
     * @param path The request path
     * @param method The HTTP method (GET or POST)
     * @param headers The request headers
     * @param clientSupportsGzip Whether the client supports gzip compression
     * @param keepConnectionOpen Whether to keep the connection open after this request
     * @param in The input stream to read the request body from (for POST)
     * @param out The output stream to write the response to
     * @throws IOException If an I/O error occurs
     */
    private static void handleFileRequest(String path, String method, Map<String, String> headers,
                                          boolean clientSupportsGzip, boolean keepConnectionOpen,
                                          BufferedReader in, OutputStream out) throws IOException {
        // Extract filename from path, removing /files/ prefix
        String fileName = path.substring("/files/".length());
        Path filePath = Paths.get(fileDirectory, fileName);

        if (method.equals("GET")) {
            // Handle GET request - serve file content
            File file = filePath.toFile();

            if (file.exists() && file.isFile()) {
                try {
                    // Read entire file into memory - suitable for small files
                    // For large files, streaming would be more efficient
                    byte[] fileContent = Files.readAllBytes(filePath);
                    byte[] compressedFileContent = null;

                    // Compress content if supported
                    if (clientSupportsGzip) {
                        try {
                            compressedFileContent = compressData(fileContent);
                        } catch (IOException e) {
                            System.err.println("Compression error: " + e.getMessage());
                            clientSupportsGzip = false;
                        }
                    }

                    // Select final content version based on compression setting
                    byte[] finalContent = clientSupportsGzip ? compressedFileContent : fileContent;
                    int contentLength = finalContent.length;

                    // Build response headers
                    StringBuilder responseBuilder = new StringBuilder();
                    responseBuilder.append("HTTP/1.1 200 OK\r\n");
                    // application/octet-stream is a generic binary type
                    // In a production server, we might detect content type based on extension
                    responseBuilder.append("Content-Type: application/octet-stream\r\n");

                    if (clientSupportsGzip) {
                        responseBuilder.append("Content-Encoding: gzip\r\n");
                    }

                    responseBuilder.append("Content-Length: " + contentLength + "\r\n");

                    if (!keepConnectionOpen) {
                        responseBuilder.append("Connection: close\r\n");
                    }

                    responseBuilder.append("\r\n");

                    // Send response
                    out.write(responseBuilder.toString().getBytes());
                    out.write(finalContent);
                } catch (IOException e) {
                    // Internal server error for file reading issues
                    String response = "HTTP/1.1 500 Internal Server Error\r\n\r\n";
                    out.write(response.getBytes());
                    System.err.println("File reading error: " + e.getMessage());
                }
            } else {
                // File not found
                String response = "HTTP/1.1 404 Not Found\r\n\r\n";
                out.write(response.getBytes());
            }
        } else if (method.equals("POST")) {
            // Handle POST request - create or update file
            String contentLengthStr = headers.get("content-length");

            if (contentLengthStr != null) {
                // Parse content length as integer
                int contentLength = Integer.parseInt(contentLengthStr);

                // Read exact number of characters as specified by Content-Length
                char[] bodyChars = new char[contentLength];
                in.read(bodyChars, 0, contentLength);
                String bodyContent = new String(bodyChars);

                // Create the file
                try {
                    // Ensure parent directories exist (recursive directory creation)
                    File parentDir = filePath.getParent().toFile();
                    if (!parentDir.exists()) {
                        parentDir.mkdirs();
                    }

                    // Write file content to disk
                    Files.write(filePath, bodyContent.getBytes());

                    // Send 201 Created response (standard for resource creation)
                    StringBuilder responseBuilder = new StringBuilder();
                    responseBuilder.append("HTTP/1.1 201 Created\r\n");

                    if (!keepConnectionOpen) {
                        responseBuilder.append("Connection: close\r\n");
                    }

                    responseBuilder.append("\r\n");

                    out.write(responseBuilder.toString().getBytes());

                    System.out.println("File created: " + fileName);
                } catch (IOException e) {
                    // Internal server error for file writing issues
                    String response = "HTTP/1.1 500 Internal Server Error\r\n\r\n";
                    out.write(response.getBytes());
                    System.err.println("File writing error: " + e.getMessage());
                }
            } else {
                // Content-Length header is required for POST requests
                String response = "HTTP/1.1 400 Bad Request\r\n\r\n";
                out.write(response.getBytes());
            }
        } else {
            // Only GET and POST methods are supported for this endpoint
            String response = "HTTP/1.1 405 Method Not Allowed\r\n\r\n";
            out.write(response.getBytes());
        }
    }
}
