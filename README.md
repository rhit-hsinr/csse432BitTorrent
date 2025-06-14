# csse432BitTorrent
## A mini-version of a BitClient 
This is a "mini-version" as we didn't implement the formal way to get pieces from a peer (the rarest first) and some small logistical stuff was left out.

### Accomplishments
1. Created a torrent maker which takes a file and tracker and creates a torrent file with them
2. Implemented a BitClient
   * Implemented torrent and tracker parsing to connect to tracker and get a list of peers
   * Implemented a process which loops through all peers and pulls one of their messages which is then classfied and handle in the correct way
   * Implemented a process to request pieces from a peer and also send pieces to a peer
3. Implemented a peer class which handles the handshake, sending messages, and storing information for each peer
4. Implemented threading and a peer reader class which reads and stores messages from all peers at the same time
5. Implemented a torrent message decoder/encoder which formats messages to send to the peers and also formats messages sent by the peers
6. Successfully got a text file from multiple peers

### How to run the code (in theory)
1. You need to have Java and Maven installed and configured correctly
2. Locate the run.sh file located csse432BitTorrent\minitorrent
3. Make sure you have execute permissions -- chmod +x run.sh
4. Type ./run.sh sample.torrent output.txt
5. This might work or it might open up Wireshark
6. The final file should be in output.txt

### Video of the program running
https://app.screencastify.com/v2/watch/Xpf24sUKJ5bCQiaO9m16
