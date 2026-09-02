import java.io.*;
import java.net.*;

public class server{

    public static void main(String[] args){
        try{
            ServerSocket serverSocket = new ServerSocket(5000);
            System.out.println("server started");
            System.out.println("waiting or the client...");
            Socket socket = serverSocket.accept();
            System.out.println("client connected");
            
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            PrintWriter output = new PrintWriter(socket.getOutputStream(),true);
            String message = input.readLine();
            System.out.println("client says:"+ message);
            output.println("Hello client");
            socket.close();
            serverSocket.close();
            System.out.println("Connection closed");

        }
        catch (IOException e) {
            System.out.println(e);
        }
    }
}