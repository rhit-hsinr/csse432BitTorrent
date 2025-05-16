package com.minitorrent;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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

    // timeouts in milliseconds
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS    = 15_000;

    /**
     * Construct a peer for the given host and port.
     */
    public Peer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Establishes a TCP connection to the peer, with timeouts.
     */
    public void connect() throws IOException {
        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        socket.setSoTimeout(READ_TIMEOUT_MS);
        in  = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
    }

    /**
     * Sends the BitTorrent handshake to this peer.
     */
    public void sendHandshake(byte[] infoHash, byte[] peerId) throws IOException {
        System.out.println("Sending handshake to " + host + ":" + port);
        byte[] msg = genHandshake(infoHash, peerId);
        out.write(msg);
        out.flush();
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
        byte[] expectedPstr = "BitTorrent protocol".getBytes(StandardCharsets.US_ASCII);
        if (!Arrays.equals(pstr, expectedPstr)) {
            throw new IOException("Unexpected protocol: " +
                    new String(pstr, StandardCharsets.US_ASCII));
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
        buf.put((byte)19);
        buf.put("BitTorrent protocol".getBytes(StandardCharsets.US_ASCII));
        buf.put(new byte[8]);     // reserved
        buf.put(infoHash);        // 20-byte info_hash
        buf.put(peerId);          // 20-byte peer_id
        return buf.array();
    }

    /**
     * Sends an arbitrary protocol message to this peer.
     */
    public void sendMessage(byte[] message) throws IOException {
        out.write(message);
        out.flush();
    }

    /**
     * Reads the next protocol message from this peer.
     * Returns a byte[] where index 0 is the msg ID (if length>0),
     * or an empty array for a keep-alive (length=0).
     */
    public byte[] readMessage() throws IOException {
        int length = in.readInt();       // length prefix
        if (length == 0) {
            // keep-alive has no ID nor payload
            return new byte[0];
        }
        byte msgId = in.readByte();      // message ID
        byte[] payload = new byte[length - 1];
        in.readFully(payload);

        // combine ID + payload for return
        byte[] result = new byte[length];
        result[0] = msgId;
        System.arraycopy(payload, 0, result, 1, payload.length);
        return result;
    }

    /**
     * Closes the connection and associated streams.
     */
    public void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }

    public String getHost() { return host; }
    public int    getPort() { return port; }
}
