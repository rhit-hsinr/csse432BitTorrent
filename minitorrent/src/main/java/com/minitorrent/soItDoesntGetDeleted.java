package com.minitorrent;

public class soItDoesntGetDeleted {

    public static void sendMsg(Peer peer, torrentMsg msg) {
        byte[] packedMsg = msg.turnIntoBytes();
        peer.write(packedMsg);
    }
}
