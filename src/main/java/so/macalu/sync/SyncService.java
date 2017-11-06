package so.macalu.sync;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import org.json.JSONObject;
import org.json.JSONTokener;

import so.macalu.sync.ISyncService;

/**
 * Service for dealing with uploads to the destinations for a directory
 */
public class SyncService implements ISyncService, Closeable{
    private ZContext context;
    private List<URI> destinations;

    public SyncService(ZContext context) {
        this.context = context;
    }

    @Override
    public void update(Path fileLocation) {
        if(destinations.isEmpty()) {
            return;
        }

        for(URI uri : destinations) {

        }
    }

    @Override
    public void close() throws IOException {

    }
}