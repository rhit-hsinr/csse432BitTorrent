package com.minitorrent;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;

public class addPeer extends Thread {

    private ServerSocket listener = null;
    private LinkedList<Socket> peers = null;
    private boolean stop = false;

    public addPeer(int port, LinkedList<Socket> peers) {
        this.peers = peers;

        // set up general listerner on port
        try {
            listener = new ServerSocket(port);
            listener.setSoTimeout(6000);
            System.out.println("Listing on port " + port);

        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    public void stopCreating() {
        stop = true;
    }

    public void run() // start new thread
    {
        while (!stop) {
            Socket peerSock = null;

            try {
                peerSock = listener.accept();

            } catch (Exception ex) {
                continue;
            }

            if (peerSock != null) {
                synchronized (peers) {
                    peers.offer(peerSock);
                    peers.notifyAll();
                }
            }
        }
    }
}
