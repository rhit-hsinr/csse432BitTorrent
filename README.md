# csse432BitTorrent
## A mini-version of a BitClient 
This is a "mini-version" as we didn't implement the formal way to get pieces from a peer (the rarest first) and some small logistical stuff was left out.

### Accomplishments
1. Created a torrent maker which takes a file and tracker and creates a torrent file with them
2. Implemented a BitClient
  1. Implemented torrent and tracker parsing to connect to tracker and get a list of peers
  2. Implemented a process which loops through all peers and pulls one of their messages which is then classfied and handle in the correct way
  3. Implemented a process to request pieces from a peer and also send pieces to a peer
6. Implemented a peer class which handles the handshake, sending messages, and storing information for each peer
7. Implemented threading and a peer reader class which reads and stores messages from all peers at the same time
8. Implemented a torrent message decoder/encoder which formats messages to send to the peers and also formats messages sent by the peers

### How to run the code (in theory)


### Video of the program running

