import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class HttpServer {
    //port number server listens to
    private static final int PORT = 4221;

    //main method
    private static void main(String[] args) {
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

                    //get OutputStream to send data back to client
                    OutputStream out = clientSocket.getOutputStream();

                    //send HTTP 200 OK response
                    String response = "HTTP/1.1 200 OK\r\n\r\n";
                    out.write(response.getBytes());

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
}