import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class Main {
    //port number server listens to
    private static final int PORT = 4221;

    //main method
    public static void main(String[] args) {
        try {
            //create ServerSocket to listen on specified port
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Server started on port " + PORT);

            //main server loop
            while (true) {

                //wait for a client to connect
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());

                //create new thread to handle this client
                Thread clientThread = new Thread(() -> handleClient(clientSocket));
                clientThread.start();
            }
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
        }
    }

    //handle a client connection in separate thread
    private static void handleClient(Socket clientSocket) {
        try {
            //get InputStreamReader to read requests from client
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));

            //read first line of request
            String requestLine = in.readLine();
            System.out.println("Request line: " + requestLine);

            //parse headers
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                int colonIndex = headerLine.indexOf(':');
                if (colonIndex > 0) {
                    String headerName = headerLine.substring(0, colonIndex).trim();
                    String headerValue = headerLine.substring(colonIndex + 1).trim();
                    //store header name (as lowercase)
                    headers.put(headerName.toLowerCase(), headerValue);
                }
            }

            //get OutputStream to send data back to client
            OutputStream out = clientSocket.getOutputStream();

            //extract path from request line
            String path = extractPath(requestLine);
            System.out.println("Path: " + path);

            //send HTTP response based on path
            if (path.equals("/")) {
                //root path -> respond with 200 OK
                String response = "HTTP/1.1 200 OK\r\n\r\n";
                out.write(response.getBytes());
            } else if (path.startsWith("/echo/")) {
                //extract string after "/echo/"
                String echoString = path.substring("/echo/".length());
                System.out.println("Echo string: " + echoString);

                //calculate content length (in bytes)
                int contentLength = echoString.getBytes().length;

                //form response with headers and body
                String response =
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/plain\r\n" +
                                "Content-Length: " + contentLength + "\r\n" +
                                "\r\n" +
                                echoString;

                out.write(response.getBytes());
            } else if (path.equals("/user-agent")) {
                //get User-Agent header value
                String userAgent = headers.get("user-agent");
                System.out.println("User-Agent: " + userAgent);

                if (userAgent != null) {
                    //calculate content length (in bytes)
                    int contentLength = userAgent.getBytes().length;

                    //form response with headers and body
                    String response =
                            "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/plain\r\n" +
                                    "Content-Length: " + contentLength + "\r\n" +
                                    "\r\n" +
                                    userAgent;

                    out.write(response.getBytes());
                } else {
                    //User-Agent missing -> respond with 400 Bad Request
                    String response = "HTTP/1.1 400 Bad Request\r\n\r\n";
                    out.write(response.getBytes());
                }
            } else {
                //any other path -> respond with 404 Not Found
                String response = "HTTP/1.1 404 Not Found\r\n\r\n";
                out.write(response.getBytes());
            }

            //close resources
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


    //extractPath method
    private static String extractPath(String requestLine) {
        if (requestLine == null) {
            //requestLine is null -> default to root path
            return null;
        }

        //split requestLine by spaces
        String[] parts = requestLine.split(" ");

        //check format
        if (parts.length < 2) {
            //invalid format -> default to root path
            return "/";
        }

        //return path
        return parts[1];
    }

}