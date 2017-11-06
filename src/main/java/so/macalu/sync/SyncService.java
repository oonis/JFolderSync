package so.macalu.sync;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import so.macalu.sync.ISyncService;

public class SyncService implements ISyncService{
    private ZContext context;

    public SyncService(ZContext context) {
        this.context = context;
    }

    @Override
    public void update(Path fileLocation) {

    }
}