package com.minitorrent;

public class soItDoesntGetDeleted {

    // this goes at bottom of bitclient
    public static void sendMsg(Peer peer, torrentMsg msg) {
        byte[] packedMsg = msg.turnIntoBytes();
        peer.write(packedMsg);
    }
}
