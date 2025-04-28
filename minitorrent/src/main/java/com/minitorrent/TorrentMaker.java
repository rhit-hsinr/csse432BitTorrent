package com.minitorrent;

import com.github.cdefgah.bencoder4j.*;
import com.github.cdefgah.bencoder4j.model.BencodedByteSequence;
import com.github.cdefgah.bencoder4j.model.BencodedDictionary;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class TorrentMaker {
    public void createTorrent(TorrentMetadata metadata, String outputFileName, String announceURL) throws IOException {
        BencodedDictionary torrentDict = new BencodedDictionary();
        String trackerURL = announceURL;
        torrentDict.put(new BencodedByteSequence("announce"), new BencodedByteSequence(trackerURL));
    }
}
