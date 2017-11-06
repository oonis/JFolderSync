package so.macalu.sync;

public interface ISyncService {
    /**
     * Tell the implementation that a file has been updated.
     * 
     * @param fileLocation <code>Path</code> The file path which has been modified.
     */
    public void update(Path fileLocation);
}