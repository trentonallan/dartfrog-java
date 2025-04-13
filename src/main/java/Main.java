import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.BufferedReader;
import java.io.InputStreamReader;

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
                try {
                    //wait for a client to connect
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Client connected: " + clientSocket.getInetAddress());

                    //get InputStreamReader to read requests from client
                    BufferedReader in = new BufferedReader(
                            new InputStreamReader(clientSocket.getInputStream()));

                    //read first line of request
                    String requestLine = in.readLine();
                    System.out.println("Request line: " + requestLine);

                    //get OutputStream to send data back to client
                    OutputStream out = clientSocket.getOutputStream();

                    //extract path from request line
                    String path = extractPath(requestLine);
                    System.out.println("Path: " + path);

                    //send HTTP response based on path
                    if (path.equals("/")) {
                        //root path -> respond with 200 OK
                        String response = "HTTP/1.1 200 OK\r\n";
                        out.write(response.getBytes());
                    } else {
                        //any other path -> respond with 404 Not Found
                        String response = "HTTP/1.1 404 Not Found\r\n\r\n";
                        out.write(response.getBytes());
                    }

                    //close resources
                    out.flush();
                    clientSocket.close();

                } catch (IOException e) {
                    System.err.println("Error handling client connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
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