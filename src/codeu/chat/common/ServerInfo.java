package codeu.chat.common;

import codeu.chat.server.Server;
import codeu.chat.util.Logger;
import codeu.chat.util.Uuid;
import java.io.IOException;

/**
 * Created by dita on 5/20/17.
 */
public final class ServerInfo {

    private final static String SERVER_VERSION = "1.0.0";

    private static final Logger.Log LOG = Logger.newLog(ServerInfo.class);

    public final Uuid version;
    
    public final Time startTime;
    public ServerInfo(Time startTime) {
       this.startTime = startTime;
    }
    public ServerInfo() {
        this.startTime = Time.now();
        Uuid version;

        try {
            version = Uuid.parse(SERVER_VERSION);
        } catch(Exception ex) {
            version = Uuid.NULL;
            LOG.error(ex, "Server version cannot be parsed. Default Uuid version used.");
        }

        this.version = version;
    }

    public ServerInfo(Uuid version) {
        this.version = version;
    }
}
