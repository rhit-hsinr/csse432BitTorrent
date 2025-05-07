import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;


import com.github.cdefgah.bencoder4j.*;

public class bitTClient {

    public static void main(String[] args) {
        // should be filename torrent toPutIn

        // Printing the first argument
        System.out.println(args[0]); // torrent
        System.out.println(args[1]); // where to go

        // open tor file and decode

        // use size from decoded file to allocate space for file
        File torrentFile = new File(args[1]); // torrent file
        try {
            if (torrentFile.createNewFile()) {
                System.out.println("File created: " + torrentFile.getName());
            } else {
                System.out.println("File already exists.");
            }
        } catch (IOException e) {
            System.err.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        }

        // read file raw
        InputStream input = new FileInputStream(torrentFile);
        // parse the torrent file
        BencodeStreamReader reader = new BencodeStreamReader(input);
        BencodedDictionary torrentDict = new BencodedDictionary(reader);

        // get values
        // Extract 'announce' URL
        BencodedByteSequence announce = (BencodedByteSequence) torrentDict.get("announce");
        System.out.println("Announce URL: " + announce.getValueAsString());

        // Extract 'info' dictionary
        BencodedDictionary infoDict = (BencodedDictionary) torrentDict.get("info");

        // Extract 'name' from 'info'
        BencodedByteSequence name = (BencodedByteSequence) infoDict.get("name");
        System.out.println("Torrent Name: " + name.getValueAsString());

        // Extract 'piece length' from 'info'
        BencodedInteger pieceLength = (BencodedInteger) infoDict.get("piece length");
        System.out.println("Piece Length: " + pieceLength.getValue());

        BencodedInteger length = (BencodedInteger) infoDict.get("length");
        System.out.println("File Length: " + length.getValue());
        // stuff for get request
        String trackerURL;
        String infoHash;

        //creating peer ID -- 20 bytes random and new for each download
        byte peerId[] = new byte[20];
        SecureRandom ran = new SecureRandom();
        ran.nextBytes(peerId);

        int port = 6881; // common port
        long uploaded = 0;
        long downloaded = 0;
        long left = 0; // from the torrent file
        String event = "started";

        // added the raw encoding for bittorrent
        String encodedInfoHash = percentEncode(infoHash.getBytes(StandardCharsets.ISO_8859_1));
        String encodedPeerID = percentEncode(peerId);
        // make request
        String urlS = String.format("%s?info_hash=%s&peer_id=%s&port=%d&uploaded=%d&downloaded=%d&left=%d&event=%s",
                trackerURL, encodedInfoHash, encodedPeerID, port, uploaded, downloaded, left, event);

        // make connection
        URL url;
        try {
            url = new URL(urlS);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");

            try (InputStream responseStream = connection.getInputStream()) {
                BencodedDictionary trackerResponse = new BencodedDictionary(new BencodeStreamReader(responseStream));
                System.out.println("\nTracker response:");

                for (String key : trackerResponse.keySet()) {
                    BencodedObject value = trackerResponse.get(key);
                    System.out.println("- " + key + ": " + value);
                }
            }
            // Peer logic
            BencodedObject peersObj = trackerResponse.get("peers");
            if (peersObj instanceof BencodedByteSequence) {
                byte[] peersBytes = ((BencodedByteSequence) peersObj).getValue();

                System.out.println("\nParsed Peers:");
                for (int i = 0; i < peersBytes.length; i += 6) {
                    String ip = (peersBytes[i] & 0xFF) + "." +
                                (peersBytes[i + 1] & 0xFF) + "." +
                                (peersBytes[i + 2] & 0xFF) + "." +
                                (peersBytes[i + 3] & 0xFF);
                    int port = ((peersBytes[i + 4] & 0xFF) << 8) | (peersBytes[i + 5] & 0xFF);
                    System.out.println("- " + ip + ":" + port);

                    try {
                        //connect peer
                    }
                }
            }
            // read response to get peer list and start connection with peers
            BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("line: " + line);
            }
            // pull out ip addrs and ports of peers from tracker (decode bencode)
            //list of peers and their coressponding ports
            //send 19 byte hearder with bittorrent protocol
            //20 byte SHA with info hash
            //20 byte peer id generated by us
            //call thread's run to start a tcp connection with given info
            //go through each peer in the list
            Threads s[];
            int i=0;
            for(each peer)
            {
                s[i] = new Threads(peerIP, port, peerId, infoHash);
                s.start();
                i++;

            }

            i=0;
            for(each peer)
            {
                //join all
                s[i].join();
                i++;
            }


        


            // make connections to them with threads

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // the peers need to be bittorrent encoded, which is raw percent encoding...
    private static String percentEncode(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%%%02X", b));
        }
        return sb.toString();
    }

}

// public static String mapToStringBuilder(Map<String, String> params)
// {
// StringBuilder finalString = new StringBuilder();

// for (Map.Entry<String, String> entry : params.entrySet()) {
// finalString.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
// finalString.append("=");
// finalString.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
// finalString.append("&");
// }

// String resultString = finalString.toString();
// return resultString.length() > 0
// ? resultString.substring(0, resultString.length() - 1)
// : resultString;

// return finalString.toString();

// }

// Map<String, String> params = new HashMap<>();
// params.put("info_hash", "IDK&");
// params.put("peer_id", "len20Random&"); // generate random id at start of
// every new download
// params.put("port", Integer.toString(port) + "&");
// params.put("uploaded", "0&");
// params.put("downloaded", "0&");
// params.put("left", Integer.toString(totalfromtracker));

// // make get request
// HttpURLConnection con = (HttpURLConnection) url.openConnection();
// con.setRequestMethod("GET");

// con.setDoOutput(true);
// DataOutputStream outURL = new DataOutputStream(con.getOutputStream());
// outURL.writeBytes(mapToStringBuilder(params));

// outURL.flush();
// outURL.close();

// // send get request

// String request = "GET /announce?" + keyStuff + " HTTP/1.1\r\n" +
// "Host: " + host + "\r\n" +
// "Connection: close\r\n" +
// "\r\n";

// // Send the request
// out.print(request);
// out.flush();

// Socket socket;
// String urlString = "get from tracker";
// URL url = new URL(urlString);
// try {
// socket = new Socket(host, port);

// // for sending a recv
// BufferedReader in = new BufferedReader(new
// InputStreamReader(socket.getInputStream()));
// PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

// //need to send an http get to get peer list from tracker

// // get encoded response

// // close everything
// in.close();
// out.close();
// socket.close();

// } catch (UnknownHostException e) {

// e.printStackTrace();
// } catch (IOException e) {

// e.printStackTrace();
// }