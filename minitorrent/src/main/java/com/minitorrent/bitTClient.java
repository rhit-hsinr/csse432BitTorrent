package com.minitorrent;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import com.github.cdefgah.bencoder4j.io.*;
import com.github.cdefgah.bencoder4j.model.*;

import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;

public class bitTClient {

    public static void main(String[] args) throws IOException, NoSuchAlgorithmException {
        // should be <torrent> <output>
        if (args.length != 2) {
            System.err.println("Usage: java BitClient <torrent-file> <output-file>");
            return;
        }
        // Printing the first argument
        System.out.println(args[0]); // torrent
        System.out.println(args[1]); // output

        // create the respective files
        File torrentFile = new File(args[0]);
        File outputFile = new File(args[1]);

        byte[] torrentBytes = Files.readAllBytes(torrentFile.toPath());

        // use size from decoded file to allocate space for file
        try {
            if (!outputFile.exists() && !outputFile.createNewFile()) {
                System.err.println("Could not create output file");
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        // read file raw
        // InputStream input = new FileInputStream(torrentFile);

        // // parse the torrent file
        // BencodeStreamReader reader = new BencodeStreamReader(input);
        // BencodedDictionary torrentDict = new BencodedDictionary(reader);

        // // extracting Information
        // // extract announce URL from torrent
        // BencodedByteSequence announce = (BencodedByteSequence)
        // torrentDict.get("announce");
        // System.out.println("Announce URL: " + announce.toString());

        // // extract info dictionary
        // BencodedDictionary infoDict = (BencodedDictionary) torrentDict.get("info");

        // // extract name from info
        // BencodedByteSequence name = (BencodedByteSequence) infoDict.get("name");
        // System.out.println("Torrent Name: " + name.toString());

        // // extract pieceLength from info
        // BencodedInteger pieceLength = (BencodedInteger) infoDict.get("piece length");
        // System.out.println("Piece Length: " + pieceLength.getValue());

        // // extract length from info
        // BencodedInteger length = (BencodedInteger) infoDict.get("length");
        // System.out.println("File Length: " + length.getValue());

        Bencode bencode = new Bencode(StandardCharsets.UTF_8);

        Map<String, Object> torrentDict = bencode.decode(torrentBytes, Type.DICTIONARY);

        // extract announce URL
        String announce = (String) torrentDict.get("announce");
        System.out.println("Announce URL: " + announce);

        // extract info dictionary
        @SuppressWarnings("unchecked")
        Map<String, Object> infoDict = (Map<String, Object>) torrentDict.get("info");

        // extract name
        String name = (String) infoDict.get("name");
        System.out.println("Torrent Name: " + name);

        // extract piece length
        Long pieceLength = (Long) infoDict.get("piece length");
        System.out.println("Piece Length: " + pieceLength);

        // extract file length
        Long length = (Long) infoDict.get("length");
        System.out.println("File Length: " + length);

        // compute SHA-1 info hash
        // get raw infodict in bytes
        // hash the raw bytes
        byte[] infoBytes = bencode.encode(infoDict);
        byte[] infoHash = MessageDigest.getInstance("SHA-1").digest(infoBytes);

        // creating peer ID
        byte peerId[] = new byte[20];
        SecureRandom ran = new SecureRandom();
        ran.nextBytes(peerId);

        int portURL = 6881; // common port
        long uploaded = 0;
        long downloaded = 0;
        long left = length; // from the torrent file
        String event = "started";

        // added the raw encoding for bittorrent
        String encodedInfoHash = percentEncode(infoHash);
        String encodedPeerID = percentEncode(peerId);

        // concat the URL
        String urlString = String.format(
                "%s?info_hash=%s&peer_id=%s&port=%d&uploaded=%d&downloaded=%d&left=%d&event=%s",
                announce.toString(),
                encodedInfoHash,
                encodedPeerID,
                portURL,
                uploaded,
                downloaded,
                left,
                event);

        // contacting the peers
        URL url;
        try {

            // announce to the tracker with the url
            System.out.println("Announce to tracker with URL:\n" + urlString);
            url = new URL(urlString);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            Map<String, Object> trackerResponse;
            // digests output and print out tracker responses (- peers: <data>) etc

            con.setConnectTimeout(10000);
            con.setReadTimeout(5000);
            try (InputStream responseStream = con.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                System.out.println("responseStream correct");
                byte[] data = new byte[1024];
                int bytesRead;
                while ((bytesRead = responseStream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, bytesRead);
                }

                byte[] responseBytes = buffer.toByteArray();
                trackerResponse = bencode.decode(responseBytes, Type.DICTIONARY);
                System.out.println("\nTracker response: " + trackerResponse.keySet());
            }

            // get the peers from tracker response
            Object peersObj = trackerResponse.get("peers");
            byte[] peerBytes = null;
            List<Peer> peerList = new ArrayList<>();

            // iterate through each peer and construct the IPv4 address
            // increment by 6 since it's 4 bytes IP and 2 bytes port
            for (int i = 0; i < peerBytes.length; i += 10) {

                // first 4 bytes for IP
                String ip = String.format("%d.%d.%d.%d",
                        peerBytes[i] & 0xFF,
                        peerBytes[i + 1] & 0xFF,
                        peerBytes[i + 2] & 0xFF,
                        peerBytes[i + 3] & 0xFF);

                // last 2 bytes for port
                int port = ((peerBytes[i + 4] & 0xFF) << 8) | (peerBytes[i + 5] & 0xFF);

                // add them to the list
                peerList.add(new Peer(ip, port));
            }
            
            System.out.println("Yay, we found " + peerList.size() + " peers!");
            for (Peer peer : peerList) {
                System.out.println(peer);
            }
            // Connect to the peers (maybe use threads?)
            // for now, just try to connect to one peer

            Socket peerSock;

            try {
                System.out.println("Socket stuff happening");
                Peer peer = peerList.get(0);
                System.out.println("Trying to connect to: " + peer.getIP() + ":" + peer.getPort());
                peerSock = new Socket();
                peerSock.connect(new InetSocketAddress(peer.getIP(), peer.getPort()), 5000);
                if (peerSock.isConnected()) {
                    System.out.println("connected");
                }
                System.out.println("peerSock created");

                // for sending and recv
                InputStream in = peerSock.getInputStream();
                OutputStream out = peerSock.getOutputStream();

                // getting stuff for first message
                byte plen = 19;
                String prot = "BitTorrent protocol";
                byte[] res = new byte[8];


                System.out.println("Starting Handshake...");

                ByteArrayOutputStream handShake = new ByteArrayOutputStream();
                handShake.write(plen);
                handShake.write(prot.getBytes("ISO-8859-1"));
                handShake.write(res);
                handShake.write(encodedInfoHash.getBytes("ISO-8859-1"));
                handShake.write(peerId);

                System.out.println("outing it trust");

                out.write(handShake.toByteArray());

                System.out.println("handshake done");

                out.flush();

                byte[] response = new byte[68]; // same size as what we sent
                int read = 0;

                while (read < 68) { // reading response which is mirror of handshake
                    int bytesR = in.read(response, read, 68 - read);
                    if (bytesR == -1) {
                        System.out.println("Something went wrong");
                        return;
                    }
                    read += bytesR;
                }

                // check if protocol is the same
                int responseLen = response[0] & 0xFF;
                String responseProt = new String(response, 1, responseLen, "ISO-8859-1");
                if (!responseProt.equals(prot)) {
                    System.out.println("Protocol recieved not BitTorrent protocol");
                    return;
                }

                // check if hashes are exact match (so it's the same file)
                byte[] hashRecieved = Arrays.copyOfRange(response, 28, 48);
                if (!Arrays.equals(hashRecieved, infoHash)) {
                    System.out.println("The info hashes did not match exactly");
                    return;
                }

                // handshake was good
                System.out.println("Handshake was good, now get data");
                in.close();
                out.close();
                peerSock.close();
            } catch (UnknownHostException e) {

                e.printStackTrace();
            } catch (IOException e) {

                e.printStackTrace();
            }

            // read response to get peer list and start connection with peers
            // BufferedReader reader = new BufferedReader(new
            // InputStreamReader(con.getInputStream()));
            // String line;
            // while ((line = reader.readLine()) != null) {
            // System.out.println("line: " + line);
            // }
            // // pull out ip addrs and ports of peers from tracker (decode bencode)
            // //list of peers and their coressponding ports
            // //send 19 byte hearder with bittorrent protocol
            // //20 byte SHA with info hash
            // //20 byte peer id generated by us
            // //call thread's run to start a tcp connection with given info
            // //go through each peer in the list
            // Threads s[];
            // int i=0;
            // for(each peer)
            // {
            // s[i] = new Threads(peerIP, port, peerId, infoHash);
            // s.start();
            // i++;

            // }

            // i=0;
            // for(each peer)
            // {
            // //join all
            // s[i].join();
            // i++;
            // }
            // // make connections to them with threads

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

    static class Peer {
        private String ip;
        private int port;

        public Peer(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
        
        public String getIP() {
            return ip;
        }

        public int getPort() {
            return port;
        }

        @Override
        public String toString() {
            return "IP: " + ip + ", Port: " + port;
        }
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