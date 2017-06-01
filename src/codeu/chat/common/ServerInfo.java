package codeu.chat.common;

import codeu.chat.server.Server;
import codeu.chat.util.Logger;
import codeu.chat.util.Time;
import codeu.chat.util.Uuid;
import java.io.IOException;

/**
 * Created by dita on 5/20/17.
 */
public final class ServerInfo {

    private final static String SERVER_VERSION = "1.0.0";

    private static final Logger.Log LOG = Logger.newLog(ServerInfo.class);
    
    public Time startTime;

    // Removed 'final' because of try/catch error
    public Uuid version;
  
    public ServerInfo() {
        this.startTime = Time.now();

        try {
            this.version = Uuid.parse(SERVER_VERSION);
        } catch(Exception ex) {
            this.version = Uuid.NULL;
            LOG.error(ex, "Server version cannot be parsed. Default Uuid version used.");
        }
    }
  
    public ServerInfo(Time startTime) {
       this.startTime = startTime;
    }

    public ServerInfo(Uuid version) {
        this.version = version;
    }
}
