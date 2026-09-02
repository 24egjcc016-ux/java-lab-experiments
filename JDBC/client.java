import java.io.InputStreamReader;
import java.net.Socket;

public class client{
    public static void main(String[] args){
        try{
            Socket socket = new Socket("localhost",5000);
            System.out.println("connected to server");
            BufferedReader input = new BufferedReader(new InputStreamReader(socket.getOutputStream(),true));
            


        }catch(Exception e){
            System.out.println(e);
    }
}