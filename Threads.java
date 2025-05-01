import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class Threads extends Thread {
    String toConnectIP;
    int port;
    String myID;
    String infoHash;

    public Threads(String toConnectIP, int port, String myID, String infoHash) {
        this.toConnectIP = toConnectIP;
        this.port = port;
        this.myID = myID;
        this.infoHash = infoHash;
    }

    // Overriding the run method
    @Override
    public void run() {
        // will go to start then here
        Socket socket;

        try {
            socket = new Socket(toConnectIP, port);

            // for sending a recv
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

        } catch (UnknownHostException e) {

            e.printStackTrace();
        } catch (IOException e) {

            e.printStackTrace();
        }

    }
}
