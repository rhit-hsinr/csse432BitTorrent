import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class bitTClient {

    public static void main(String[] args) {
        // should be filename torrent toPutIn

        // Printing the first argument
        System.out.println(args[0]); // torrent
        System.out.println(args[1]); // where to go

        // open tor file and decode

        // use size from decoded file to allocate space for file
        File file = new File(args[1]);
        try {
            if (file.createNewFile()) {
                System.out.println("File created: " + file.getName());
            } else {
                System.out.println("File already exists.");
            }
        } catch (IOException e) {
            System.err.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        }

        // start tcp connection with tracker to get list of peers
        // get host and port from torrent file
        String host = "";
        int port = 0;
        Socket socket;
        String urlString = "get from tracker";
        URL url = new URL(urlString);

        try {
            socket = new Socket(host, port);

            // for sending a recv
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

            // get key-value pairs
            Map<String, String> params = new HashMap<>();
            params.put("info_hash", "IDK&");
            params.put("peer_id", "len20Random&"); // generate random id at start of every new download
            params.put("port", Integer.toString(port) + "&");
            params.put("uploaded", "0&");
            params.put("downloaded", "0&");
            params.put("left", Integer.toString(totalfromtracker));

            // make get request
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            con.setDoOutput(true);
            DataOutputStream outURL = new DataOutputStream(con.getOutputStream());
            outURL.writeBytes(mapToStringBuilder(params));

            outURL.flush();
            outURL.close();

            // send get request

            String request = "GET /announce?" + keyStuff + " HTTP/1.1\r\n" +
                    "Host: " + host + "\r\n" +
                    "Connection: close\r\n" +
                    "\r\n";

            // Send the request
            out.print(request);
            out.flush();

            // get encoded response

            // close everything
            in.close();
            out.close();
            socket.close();

        } catch (UnknownHostException e) {

            e.printStackTrace();
        } catch (IOException e) {

            e.printStackTrace();
        }

    }

    public static String mapToStringBuilder(Map<String, String> params)
    {
        StringBuilder finalString = new StringBuilder();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            finalString.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            finalString.append("=");
            finalString.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            finalString.append("&");
        }

        String resultString = finalString.toString();
        return resultString.length() > 0
          ? resultString.substring(0, resultString.length() - 1)
          : resultString;

        return finalString.toString();

}
