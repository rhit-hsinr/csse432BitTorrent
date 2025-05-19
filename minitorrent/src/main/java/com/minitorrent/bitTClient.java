package com.minitorrent;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;

// class PeerSession {
//     // peer data
//     String ip;
//     int port;
//     Socket socket;
//     DataInputStream inputStream;
//     DataOutputStream outputStream;

//     // peer state
//     boolean amChoking = true;
//     boolean amInterested = false;
//     boolean peerChoking = true;
//     boolean peerInterested = false;

//     byte[] peerBitfield;

//     // global access data
//     private final byte[] infoHashGlobal;
//     private final byte[] peerIdGlobal;
//     private final long totalPieces;
//     private final bitTClient client;

//      public PeerSession(String ip, int port, byte[] infoHashGlobal, byte[] peerIdGlobal, bitTClient client) {
//         this.ip = ip;
//         this.port = port;
//         this.infoHashGlobal = infoHashGlobal;
//         this.peerIdGlobal = peerIdGlobal;
//         this.client = client;
//         this.totalPieces = client.getNumPiecesGlobal();
//     }

//     public boolean connectAndHandshake() {
//         try {
//             System.out.println("[" + Thread.currentThread().getName() + "] Connecting to peer: " + ip + ":" + port);
//             socket = new Socket();
//             socket.connect(new InetSocketAddress(ip, port), 10000); // 10s connect timeout
//             socket.setSoTimeout(15000); // 15s read timeout
//             outputStream = new DataOutputStream(socket.getOutputStream());
//             inputStream = new DataInputStream(socket.getInputStream());

//             // 1. Send Handshake
//             ByteArrayOutputStream handshakeMsg = new ByteArrayOutputStream();
//             handshakeMsg.write((byte) 19); // pstrlen
//             handshakeMsg.write("BitTorrent protocol".getBytes(StandardCharsets.ISO_8859_1)); // pstr
//             handshakeMsg.write(new byte[8]); // reserved bytes
//             handshakeMsg.write(infoHashGlobal); // RAW info_hash
//             handshakeMsg.write(peerIdGlobal);   // RAW peer_id

//             outputStream.write(handshakeMsg.toByteArray());
//             outputStream.flush();
//             System.out.println("[" + Thread.currentThread().getName() + "] Sent handshake to " + ip + ":" + port);

//             // 2. Receive Handshake
//             byte[] responseHandshake = new byte[68]; // pstrlen (1) + pstr (19) + reserved (8) + info_hash (20) + peer_id (20)
//             inputStream.readFully(responseHandshake);

//             byte pstrlenReceived = responseHandshake[0];
//             if (pstrlenReceived != 19) {
//                 System.err.println("[" + Thread.currentThread().getName() + "] Peer " + ip + ":" + port + " sent invalid pstrlen: " + pstrlenReceived);
//                 close();
//                 return false;
//             }

//             String pstrReceived = new String(responseHandshake, 1, 19, StandardCharsets.ISO_8859_1);
//             if (!"BitTorrent protocol".equals(pstrReceived)) {
//                 System.err.println("[" + Thread.currentThread().getName() + "] Peer " + ip + ":" + port + " sent invalid pstr: " + pstrReceived);
//                 close();
//                 return false;
//             }

//             // byte[] reservedReceived = Arrays.copyOfRange(responseHandshake, 20, 28);
//             byte[] infoHashReceived = Arrays.copyOfRange(responseHandshake, 28, 48);

//             if (!Arrays.equals(infoHashGlobal, infoHashReceived)) {
//                 System.err.println("[" + Thread.currentThread().getName() + "] Peer " + ip + ":" + port + " sent incorrect info_hash.");
//                 close();
//                 return false;
//             }

//             // byte[] peerIdReceived = Arrays.copyOfRange(responseHandshake, 48, 68);
//             System.out.println("[" + Thread.currentThread().getName() + "] Handshake successful with " + ip + ":" + port);
//             return true;

//         } catch (UnknownHostException e) {
//             System.err.println("[" + Thread.currentThread().getName() + "] Unknown host: " + ip + " - " + e.getMessage());
//             return false;
//         } catch (IOException e) {
//             System.err.println("[" + Thread.currentThread().getName() + "] I/O error connecting or handshaking with " + ip + ":" + port + " - " + e.getMessage());
//             close();
//             return false;
//         }
//     }

//     public void close() {
//         try {
//             if (inputStream != null) inputStream.close();
//             if (outputStream != null) outputStream.close();
//             if (socket != null && !socket.isClosed()) socket.close();
//         } catch (IOException e) {
//             // Log quietly
//         }
//         client.peerDisconnected(this);
//         System.out.println("[" + Thread.currentThread().getName() + "] Closed connection with " + ip + ":" + port);
//     }

// }

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

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;

    private final Bencode bencode = new Bencode(StandardCharsets.US_ASCII);
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
        this.pieceLengthGlobal = (Long) infoDict.get("piece length");
        System.out.println("Piece Length: " + pieceLengthGlobal);

        // extract file length
        this.fileLengthGlobal = (Long) infoDict.get("length");
        System.out.println("File Length: " + fileLengthGlobal);

        if (fileLengthGlobal == 0 || pieceLengthGlobal == 0) {
            System.err.println("You have nothing in your file gng");
            return;
        }

        // create new file with file length
        try (RandomAccessFile raf = new RandomAccessFile(outputFileGlobal, "rw")) {
            raf.setLength(fileLengthGlobal);
        } catch (IOException e) {
            System.err.println("Error with pre-allocation for " + outputFileGlobal);
        }

        this.numPiecesGlobal = (int) ((fileLengthGlobal + pieceLengthGlobal - 1) / pieceLengthGlobal);
        this.pieceDataBuffers = new byte[numPiecesGlobal][];
        this.pieceCompleted = new boolean[numPiecesGlobal];
        this.pieceBlockTracker = new int[numPiecesGlobal];
        System.out.println("Num pieces: " + numPiecesGlobal);

        String piecesObj = (String) infoDict.get("pieces");
        this.piecesHashGlobal = piecesObj.getBytes(StandardCharsets.US_ASCII);

        // compute SHA-1 info hash
        // get raw infodict in bytes
        // hash the raw bytes
        byte[] infoBytes = bencode.encode(infoDict);
        this.infoHashGlobal = MessageDigest.getInstance("SHA-1").digest(infoBytes);

        // creating peer ID
        byte[] peerId = new byte[20];
        this.peerIdGlobal = peerId;
        SecureRandom ran = new SecureRandom();
        ran.nextBytes(peerId);

        // tracker communication
        List<Peer> peers = null;

        int retryCount = 0;
        while (retryCount < 3) {
            try {
                peers = connectToTracker(announceUrl);
                break;
            } catch (SocketTimeoutException e) {
                retryCount++;
                System.err.println("Tracker timeout, retrying (" + retryCount + "/3)...");
            } catch (IOException e) {
                System.err.println("Tracker announce failed: " + e.getMessage());
                return;
            }
        }

        if (peers == null || peers.isEmpty()) {
            System.out.println("No peers found... bye bye");
            return;
        }
        System.out.println("Found " + peers.size() + " peers from tracker");

        // peer connection and communication
        int maxConcurrent = 20;
        int connectionAttemptCount = 0;

        for (Peer peer : peers) {
            System.out.println("Peer info: " + peer.getHost() + ":" + peer.getPort());
            try {
                // 1) Open the TCP connection with timeouts
                peer.connect();

                // 2) Send our 68‑byte handshake
                peer.sendHandshake(infoHashGlobal, peerIdGlobal);

                // 3) Read & verify their handshake (throws if infoHash mismatches)
                peer.receiveHandshake(infoHashGlobal);

                System.out.println("Handshake OK with " + peer.getHost() + ":" + peer.getPort());

                // TODO: from here you can send an “interested” message, read their bitfield,
                // etc.
                // e.g. peer.sendMessage(torrentMsg.genInterested());

                peer.close();
            } catch (IOException e) {
                System.err.println("Handshake failed with "
                        + peer.getHost() + ":" + peer.getPort() + " → " + e.getMessage());
            }
        }
        */

    }

    private List<Peer> connectToTracker(String announceUrl) throws IOException {
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
        con.setConnectTimeout(CONNECT_TIMEOUT_MS);
        con.setReadTimeout(READ_TIMEOUT_MS);

        List<Peer> peerList = new ArrayList<>();
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
                System.out.println("Tracker response: " + trackerResponse);

                if (trackerResponse.containsKey("failure reason")) {
                    System.err.println("Tracker failure: " + trackerResponse.get("failure reason"));
                    return peerList;
                }

                String peersObj = (String) trackerResponse.get("peers");
                peerParse(peersObj, peerList);
            }
        } else {
            System.err.println("Tracker request failed. HTTP Error Code: " + responseCode);
        }
        con.disconnect();
        return peerList;
    }

    private void peerParse(String peersObj, List<Peer> peerList) {
        byte[] peers = peersObj.getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < peers.length; i += 6) {
            String ip = String.format("%d.%d.%d.%d",
                    peers[i] & 0xff,
                    peers[i + 1] & 0xff,
                    peers[i + 2] & 0xff,
                    peers[i + 3] & 0xff);
            int port = (((peers[i + 4] & 0xff) << 8) | peers[i + 5] & 0xff);
            if (port > 0) {
                peerList.add(new Peer(ip, port));
            }
        }
        if (peersObj.isEmpty()) {
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

    public int getNumPiecesGlobal() { return numPiecesGlobal; }

}