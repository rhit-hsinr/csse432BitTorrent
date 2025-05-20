package com.minitorrent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Represents a single BitTorrent peer connection.
 * Handles socket setup, handshake, and basic message send/receive.
 */
public class Peer {
    private final String host;
    private final int port;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    // Peer state
    public boolean amChoking = true;
    public boolean amInterested = false;
    public boolean peerChoking = true;
    public boolean peerInterested = false;
    private BitSet availablePieces;
    private long lastMessageTime;
    private int consecutiveFailures = 0;
    public Set<Integer> sentRequests;

    // Constants
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int MAX_FAILURES = 3;
    private static final long KEEP_ALIVE_INTERVAL = 120_000; // 2 minutes

    private Queue<torrentMsg> messageQueue;

    public Peer(String host, int port) {
        this.host = host;
        this.port = port;
        this.messageQueue = new LinkedList<>();
        this.lastMessageTime = System.currentTimeMillis();
        this.sentRequests = new HashSet<>();
    }

    public void connect() throws IOException {
        socket = new Socket();
        socket.setSoTimeout(READ_TIMEOUT_MS);
        socket.setTcpNoDelay(true); // Disable Nagle's algorithm
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
    }

    public void initializeBitfield(int numPieces) {
        this.availablePieces = new BitSet(numPieces);
    }

    public void updatePeerBitfield(byte[] bitfield) {
        for (int i = 0; i < bitfield.length * 8; i++) {
            if ((bitfield[i / 8] & (0x80 >> (i % 8))) != 0) {
                availablePieces.set(i);
            }
        }
    }

    /**
     * Sends the BitTorrent handshake to this peer.
     */
    public void sendHandshake(byte[] infoHash, byte[] peerId) throws IOException {
        System.out.println("Sending handshake to " + host + ":" + port);
        byte[] msg = genHandshake(infoHash, peerId);
        sendMessage(msg);
    }

    /**
     * Reads and validates the BitTorrent handshake from this peer.
     * Verifies the protocol string and the info-hash.
     */
    public void receiveHandshake(byte[] expectedInfoHash) throws IOException {
        byte[] response = new byte[68];
        in.readFully(response);

        ByteBuffer buf = ByteBuffer.wrap(response);
        byte pstrlen = buf.get();
        byte[] pstr = new byte[pstrlen];
        buf.get(pstr);

        // validate protocol string
        byte[] expectedPstr = "BitTorrent protocol".getBytes(StandardCharsets.ISO_8859_1);
        if (!Arrays.equals(pstr, expectedPstr)) {
            throw new IOException("Unexpected protocol: " +
                    new String(pstr, StandardCharsets.ISO_8859_1));
        }

        // skip reserved bytes
        buf.position(1 + pstrlen + 8);

        // read & verify info_hash
        byte[] receivedInfoHash = new byte[20];
        buf.get(receivedInfoHash);
        if (!Arrays.equals(receivedInfoHash, expectedInfoHash)) {
            throw new IOException("Handshake failed: info-hash mismatch");
        }

        System.out.println("Received handshake from " + host + ":" + port);
        // optionally read the remote peer_id: buf.get(new byte[20]);
    }

    /**
     * Generates the 68-byte BitTorrent handshake message.
     */
    private byte[] genHandshake(byte[] infoHash, byte[] peerId) {
        ByteBuffer buf = ByteBuffer.allocate(68);
        buf.put((byte) 19);
        buf.put("BitTorrent protocol".getBytes(StandardCharsets.ISO_8859_1));
        buf.put(new byte[8]); // reserved
        buf.put(infoHash); // 20-byte info_hash
        buf.put(peerId); // 20-byte peer_id
        return buf.array();
    }

    /**
     * Sends an arbitrary protocol message to this peer.
     */
    public void sendMessage(byte[] message) throws IOException {
        out.write(message);
        out.flush();
        lastMessageTime = System.currentTimeMillis();
    }

    public void sendInterested() throws IOException {
        torrentMsg msg = new torrentMsg(torrentMsg.MsgType.INTERESTED);
        sendMessage(msg.turnIntoBytes());
        amInterested = true;
    }

    public void sendNotInterested() throws IOException {
        torrentMsg msg = new torrentMsg(torrentMsg.MsgType.UNINTERESTED);
        sendMessage(msg.turnIntoBytes());
        amInterested = false;
    }

    public void sendHave(int pieceIndex) throws IOException {
        torrentMsg msg = new torrentMsg(torrentMsg.MsgType.HAVE, pieceIndex);
        sendMessage(msg.turnIntoBytes());
    }

    /**
     * Reads the next protocol message from this peer.
     * Returns a byte[] where index 0 is the msg ID (if length>0),
     * or an empty array for a keep-alive (length=0).
     */
    public byte[] readMessage() throws IOException {
        int length = in.readInt(); // length prefix
        if (length == 0) {
            // keep-alive has no ID nor payload
            return new byte[0];
        }
        byte msgId = in.readByte(); // message ID
        byte[] payload = new byte[length - 1];
        in.readFully(payload);

        // combine ID + payload for return
        byte[] result = new byte[length];
        result[0] = msgId;
        System.arraycopy(payload, 0, result, 1, payload.length);
        return result;
    }

    public void handleMessage(torrentMsg msg) {
        lastMessageTime = System.currentTimeMillis();
        queueMessage(msg); // Queue the message first

        switch (msg.getType()) {
            case CHOKE:
                peerChoking = true;
                break;
            case UNCHOKE:
                peerChoking = false;
                break;
            case INTERESTED:
                peerInterested = true;
                break;
            case UNINTERESTED:
                peerInterested = false;
                break;
            case HAVE:
                availablePieces.set(msg.getIndex());
                break;
            case BITFIELD:
                updatePeerBitfield(msg.getField());
                break;
        }
    }

    public void checkKeepAlive() throws IOException {
        if (System.currentTimeMillis() - lastMessageTime > KEEP_ALIVE_INTERVAL) {
            sendKeepAlive();
        }
    }

    private void sendKeepAlive() throws IOException {
        sendMessage(new byte[4]); // 4 bytes of zeros
    }

    public void markFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= MAX_FAILURES) {
            close();
        }
    }

    public void markSuccess() {
        consecutiveFailures = 0;
    }

    public boolean isHealthy() {
        return consecutiveFailures < MAX_FAILURES;
    }

    public boolean hasPiece(int pieceIndex) {
        return availablePieces != null && availablePieces.get(pieceIndex);
    }

    public boolean canRequest() {
        return !peerChoking && amInterested;
    }

    public void sendRequest(int pieceIndex, int begin, int length) throws IOException {
        torrentMsg msg = new torrentMsg(torrentMsg.MsgType.REQUEST, pieceIndex, begin, length);
        sendMessage(msg.turnIntoBytes());
        sentRequests.add(pieceIndex);
    }

    public void removeRequest(int pieceIndex) {
        sentRequests.remove(pieceIndex);
    }

    public boolean hasRequest(int pieceIndex) {
        return sentRequests.contains(pieceIndex);
    }

    public torrentMsg getNextMessage() {
        if (messageQueue == null) {
            return null;
        }
        synchronized (messageQueue) {
            return messageQueue.poll();
        }
    }

    public void queueMessage(torrentMsg msg) {
        if (messageQueue != null) {
            synchronized (messageQueue) {
                messageQueue.offer(msg);
            }
        }
    }

    public void close() {
        try {
            if (socket != null) {
                socket.close();
            }
            sentRequests.clear();
        } catch (IOException ignored) {
        }
    }

    // Getters
    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public BitSet getAvailablePieces() {
        return availablePieces;
    }

    public boolean isPeerChoking() {
        return peerChoking;
    }

    public boolean isAmInterested() {
        return amInterested;
    }

    public long getLastMessageTime() {
        return lastMessageTime;
    }
}
