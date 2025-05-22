package com.minitorrent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Queue;

//always accept peer communication
public class peerReader implements Runnable {

    private int TOTALHELD = 10; // only keep ten messages
    private InputStream peerInput = null;
    private Queue<TorrentMsg> allMgs = null;
    private boolean stopped = false;

    public peerReader(InputStream is, Queue<TorrentMsg> allMsgs) {
        this.peerInput = is;
        this.allMgs = allMsgs;
    }

    public void run() {
        byte[] lenStorage = new byte[4];
        int read = 0;

        while (!stopped) {
            // getting len of message
            try {
                read = peerInput.read(lenStorage, 0, 4);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            System.out.println(read);
            // if (read != 4) {
            //     System.out.println("This shi is not 4: " + read);
            //     throw new RuntimeException("size was not 4 when reading from peer");
            // }

            // pulling out total length
            ByteBuffer buf = ByteBuffer.wrap(lenStorage);
            int len = buf.getInt();

            // making buffer for message
            byte[] rest = new byte[4 + len];

            // copy length
            for (int i = 0; i < 4; i++) {
                rest[i] = lenStorage[i];
            }

            // read rest of msg
            for (read = 0; read < len;) {
                try {
                    read += peerInput.read(rest, 4 + read, len - read);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            if (read != len) {
                System.out.println("read less than expected from peer");
            }

            // turn into readble form
            TorrentMsg toHandle = TorrentMsg.turnIntoMsg(rest);

            // add to queue
            synchronized (allMgs) { // bc of threads. Like a lock
                while (allMgs.size() >= TOTALHELD) { // check there is room
                    try {
                        allMgs.wait();
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                allMgs.offer(toHandle); // add to queue
            }
        }
    }

    public void endThread() {
        this.stopped = true;
    }
}
