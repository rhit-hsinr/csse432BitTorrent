package com.minitorrent;

public class soItDoesntGetDeleted {

    // this goes at bottom of bitclient
    public static void sendMsg(Peer peer, torrentMsg msg) {
        byte[] packedMsg = msg.turnIntoBytes();
        peer.write(packedMsg);
    }

    // done = true if we have the whole file

    if(!done) // requesting pieces from non-choked peers

    {
        for (Peer peer : peerList) {
            int i;
            if (!peer.isChoked && peer.isInterested // check it is not choked and is interested
                    && (i = peer.getRarePiece(bitfield)) > -1 // make sure peer has piece we don't and get index
                    && !peer.requests.contains(i)) // check that we haven't asked for this index already
            {

                int iLen = pieceLengthGlobal; // length of peice
                if (i = numPiecesGlobal - 1 && fileLengthGlobal % pieceLengthGlobal > 0) // is last piece and does not
                                                                                         // divide evenly
                {
                    iLen = fileLengthGlobal % pieceLengthGlobal; // set to the rest of the file
                    // since a file won't always be split evenly at the end
                }

                // creating request message
                torrentMsg req = new torrentMsg(torrentMsg.MsgType.REQUEST, i, i * pieceLengthGlobal, iLen);
                peer.request.add(i); // adding to the list of pieces we've asked for
                sendMessage(peer, req); // sending msg to peer
            }
        }
    }

}
