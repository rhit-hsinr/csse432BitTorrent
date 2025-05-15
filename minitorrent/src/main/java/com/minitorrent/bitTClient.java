package com.minitorrent;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import com.github.cdefgah.bencoder4j.io.*;
import com.github.cdefgah.bencoder4j.model.*;

import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;

public class bitTClient {
    // global access data
    private File outputFileGlobal;
    private long pieceLengthGlobal;
    private long fileLengthGlobal;
    private int numPiecesGlobal;

    // global access info bytes
    private byte[] infoHashGlobal;
    private byte[] peerIdGlobal;
    private byte[] piecesHashGlobal;

    // piece management
    private byte[][] pieceDataBuffers;
    private boolean[] pieceCompleted; 
    private int[] pieceBlockTracker;  
    private final Object pieceLock = new Object(); 

    // private final List<PeerSession> activePeerSessions = new ArrayList<>();
    private final List<Thread> peerThreads = new ArrayList<>();

    private final Bencode bencode = new Bencode(StandardCharsets.UTF_8);
    private static final int BLOCK_SIZE = 16 * 1024;

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: <torrent-file> <output-file>");
            return;
        }
        bitTClient client = new bitTClient();
        try {
            client.start(args[0], args[1]);
        } catch (IOException | NoSuchAlgorithmException e) {
            System.err.println("Client failed to start: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void start(String torrentFilePath, String outputFilePath) throws IOException, NoSuchAlgorithmException {
        System.out.println("Torrent file: " + torrentFilePath);
        System.out.println("Output file: " + outputFilePath);

        File torrentFile = new File(torrentFilePath);
        this.outputFileGlobal = new File(outputFilePath);

        if (!torrentFile.exists()) {
            System.err.println("Torrent file not found: " + torrentFilePath);
            return;
        }

        byte[] torrentBytes = Files.readAllBytes(torrentFile.toPath());

        if (!outputFileGlobal.exists()) {
            if (!outputFileGlobal.createNewFile()) {
                System.err.println("Could not create output file: " + outputFileGlobal.getAbsolutePath());
                return;
            }
        }

        Map<String, Object> torrentDict = bencode.decode(torrentBytes, Type.DICTIONARY);

        // extract announce URL
        String announceUrl = (String) torrentDict.get("announce");
        System.out.println("Announce URL: " + announceUrl);

        // extract info dictionary
        @SuppressWarnings("unchecked")
        Map<String, Object> infoDict = (Map<String, Object>) torrentDict.get("info");
        if (infoDict == null) {
            System.err.println("Info dictionary cannot be found in the torrent file");
            return;
        }

        // extract name
        String name = (String) infoDict.get("name");
        System.out.println("Torrent Name: " + name);

        // extract piece length
        Long pieceLength = (Long) infoDict.get("piece length");
        this.pieceLengthGlobal = pieceLength;
        System.out.println("Piece Length: " + pieceLengthGlobal);

        // extract file length
        Long length = (Long) infoDict.get("length");
        this.fileLengthGlobal = length;
        System.out.println("File Length: " + fileLengthGlobal);

        if (fileLengthGlobal == 0 || pieceLengthGlobal == 0) {
            System.err.println("You have nothing in your file gng");
            return;
        }

        // create new file with file length
        try (RandomAccessFile raf = new RandomAccessFile(outputFileGlobal, "rw")) {
            raf.setLength(fileLengthGlobal);
        }   catch (IOException e) {
            System.err.println("Error with pre-allocation for " + outputFileGlobal);
        }

        this.numPiecesGlobal = (int) ((fileLengthGlobal + pieceLengthGlobal - 1)/pieceLengthGlobal);
        this.pieceDataBuffers = new byte[numPiecesGlobal][];
        this.pieceCompleted = new boolean[numPiecesGlobal];
        this.pieceBlockTracker = new int [numPiecesGlobal];
        System.out.println("Num pieces: " + numPiecesGlobal);

        Object piecesObj = infoDict.get("pieces");
        this.piecesHashGlobal = (byte[]) piecesObj;

        // compute SHA-1 info hash
        // get raw infodict in bytes
        // hash the raw bytes
        byte[] infoBytes = bencode.encode(infoDict);
        byte[] infoHash = MessageDigest.getInstance("SHA-1").digest(infoBytes);
        this.infoHashGlobal = infoHash;

        // creating peer ID
        byte peerId[] = new byte[20];
        this.peerIdGlobal = peerId;
        SecureRandom ran = new SecureRandom();
        ran.nextBytes(peerId);

        // tracker communication
        List<bitTClient.Peer> peers = connectToTracker(announceUrl);
        if (peers.isEmpty()) {
            System.out.println("No peers found... bye bye");
            return;
        }
        System.out.println("Found " + peers.size() + " peers from tracker");
        
        // peer connection and communication
        int maxConcurrent = 20;
        int connectionAttemptCount = 0;

        for (bitTClient.Peer peerInfo : peers) {
            // need to add the bitPeers in here
        }
        try {
            // Connect to the peers (maybe use threads?)
            // for now, just try to connect to one peer

            // unsure abt
            LinkedList<Socket> peers = new LinkedList<Socket>();
            addPeer toRunPeers = new addPeer(portURL, peers);
            toRunPeers.start();

            // //send 19 byte hearder with bittorrent protocol
            // //20 byte SHA with info hash
            // //20 byte peer id generated by us
            // //call thread's run to start a tcp connection with given info
            // //go through each peer in the list

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private List<bitTClient.Peer> connectToTracker(String announceUrl) throws IOException {
        int portURL = 6881;
        long uploaded = 0;
        long downloaded = 0;
        long left = fileLengthGlobal;
        String event = "started";

        String encodedInfoHash = percentEncode(infoHashGlobal);
        String encodedPeerID = percentEncode(peerIdGlobal);

        String trackerUrlString = String.format(
                "%s%sinfo_hash=%s&peer_id=%s&port=%d&uploaded=%d&downloaded=%d&left=%d&compact=1&event=%s",
                announceUrl,
                (announceUrl.contains("?") ? "&" : "?"),
                encodedInfoHash,
                encodedPeerID,
                portURL,
                uploaded,
                downloaded,
                left,
                event);

        System.out.println("Announcing to tracker:\n" + trackerUrlString);
        URL url = new URL(trackerUrlString);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setConnectTimeout(10000);
        con.setReadTimeout(15000);

        List<bitTClient.Peer> peerList = new ArrayList<>();
        int responseCode = con.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            try (InputStream responseStream = con.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[1024];
                int bytesRead;
                while ((bytesRead = responseStream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, bytesRead);
                }
                byte[] responseBytes = buffer.toByteArray();
                Map<String, Object> trackerResponse = bencode.decode(responseBytes, Type.DICTIONARY);

                if (trackerResponse.containsKey("failure reason")) {
                    System.err.println("Tracker failure: " + trackerResponse.get("failure reason"));
                    return peerList;
                }

                Object peersObj = trackerResponse.get("peers");
                peerParse(peersObj, peerList);
            }
        } else {
            System.err.println("Tracker request failed. HTTP Error Code: " + responseCode);
        }
        con.disconnect();
        return peerList;
    }

    private void peerParse(Object peersObj, List<bitTClient.Peer> peerList) {
        if (peersObj instanceof byte[]) { // Compact peer list
            byte[] peerBytes = (byte[]) peersObj;
            if (peerBytes.length % 6 != 0) {
                System.err.println("Malformed compact peer data from tracker. Length: " + peerBytes.length);
            } else {
                for (int i = 0; i < peerBytes.length; i += 6) {
                    String ip = String.format("%d.%d.%d.%d",
                            peerBytes[i] & 0xFF,
                            peerBytes[i + 1] & 0xFF,
                            peerBytes[i + 2] & 0xFF,
                            peerBytes[i + 3] & 0xFF);
                    int port = ((peerBytes[i + 4] & 0xFF) << 8) | (peerBytes[i + 5] & 0xFF);
                    if (port > 0 && port <= 65535) {
                        peerList.add(new bitTClient.Peer(ip, port));
                    }
                }
            }
        } else if (peersObj instanceof List) { // Non-compact peer list
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> peerDictList = (List<Map<String, Object>>) peersObj;
            for (Map<String, Object> peerDict : peerDictList) {
                String ip = (String) peerDict.get("ip");
                Long portLong = (Long) peerDict.get("port");
                // String peerId = (String) peerDict.get("peer id");
                if (ip != null && portLong != null) {
                    int port = portLong.intValue();
                     if (port > 0 && port <= 65535) {
                        peerList.add(new bitTClient.Peer(ip, port));
                    }
                }
            }
        } else if (peersObj != null) {
            System.err.println("Peers data from tracker ain't real " + peersObj.getClass().getName());
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
        private Socket peerSock = null;
        private BufferedOutputStream outPeer = null;
        private BufferedInputStream inPeer = null;
        private readFromPeer reader = null; // for reading from peer
        private Queue<readFromTor> forTorFile = null; // for talking to tor.. needed?

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

        // connecting to a peer
        public int connect() {
            try {
                peerSock = new Socket(getIP(), getPort());
                outPeer = new BufferedOutputStream(new DataOutputStream(peerSock.getOutputStream()));
                inPeer = new BufferedInputStream(new DataInputStream(peerSock.getInputStream()));

            } catch (IOException ex) {
                System.out.println("Couldn't connect to peer " + getIP());
                return -1;
            }
            return 0;
        }

        public int sendFirstMsg(String info, byte[] peerId) { // to send first msg
            try {
                byte[] msg = genMsg(info, peerId);
                outPeer.write(msg, 0, msg.length);
                outPeer.flush();
            } catch (IOException ex) {
                System.out.println("Could not make connection");
                return -1;
            }
            return 0;
        }

        public byte[] genMsg(String info, byte[] peerId) {
            ByteBuffer msg = ByteBuffer.allocate(68); // size of handshake = 68 bytes
            byte pLen = 19;
            msg.put(pLen);

            try {
                msg.put("BitTorrent protocol".getBytes("US-ASCII"));
            } catch (UnsupportedEncodingException e) {

                e.printStackTrace();
            }

            byte[] empty = new byte[8];
            Arrays.fill(empty, (byte) 0); // set to 0

            msg.put(empty);

            MessageDigest md;
            try {
                md = MessageDigest.getInstance("SHA-1");
                msg.put(md.digest(info.getBytes())); // need to encode
                msg.put(peerId);
                msg.flip();
            } catch (NoSuchAlgorithmException e) {

                e.printStackTrace();
            }
            return msg.array();

        }

        public int receivefirstMsg(String info, byte[] peerId) {
            byte[] recvPeer = new byte[68];
            try {
                // read msg
                int read = 0;
                while (read < 68) {
                    read += inPeer.read(recvPeer, read, 68 - read);
                }
            } catch (IOException ex) {
                System.out.println("couldn't read full message");
                return -1;
            }

            byte[] toTestMsg = genMsg(info, peerId);
            if (toTestMsg.length != recvPeer.length) {
                return -1;
            }
            // compare the two msgs minus the peer id
            for (int i = 0; i < toTestMsg.length - 20; i++) {
                if (toTestMsg[i] != recvPeer[i]) {
                    System.out.println("Problem with comparing the first message for peer " + getIP());
                    return -1;
                }
            }
            // msg good
            // start actual communication
            this.msgQ = new LinkedList<readFromTor>(); // for communicating with tor file
            this.reader = new readFromPeer(); // for communicating with peer
            Thread t = new Thread(reader);
            t.start();
            return 0;
        }

        @Override
        public String toString() {
            return "IP: " + ip + ", Port: " + port;
        }
    }

}