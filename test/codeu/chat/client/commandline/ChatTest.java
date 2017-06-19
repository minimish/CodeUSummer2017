package codeu.chat.client.commandline;

import static org.junit.Assert.*;
import codeu.chat.client.core.Context;
import codeu.chat.util.RemoteAddress;
import codeu.chat.util.connections.ClientConnectionSource;
import codeu.chat.util.connections.Connection;
import codeu.chat.util.connections.ConnectionSource;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.net.Socket;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by dita on 6/17/17.
 */
public final class ChatTest {

    private Chat chat;
    private ConnectionSource connectionSource;

    private BufferedReader bufferedReader;

    @Before
    public void doBefore() throws IOException{
        String hostName = "localhost";
        int portNumber = 1025;

        connectionSource = new ClientConnectionSource(hostName, portNumber);
        connectionSource.connect();
        chat = new Chat(new Context(connectionSource));

        bufferedReader = new BufferedReader(new FileReader(new File("data/transaction_log.txt")));
    }

    @Test
    public void addUserTest() throws Exception {
        boolean addUser = chat.handleCommand("u-add username");
        assertTrue(addUser);
        TimeUnit.MINUTES.wait(1);
        assertNotNull(bufferedReader.readLine());
    }

    @Test
    public void addConvoTest() throws Exception {
        chat.handleCommand("u-sign-in username");
        boolean addConvo = chat.handleCommand("c-add convo");
        assertTrue(addConvo);
        TimeUnit.MINUTES.wait(1);
        assertNotNull(bufferedReader.readLine());
    }

    @Test
    public void addMessageTest() throws Exception {
        boolean addMessage = chat.handleCommand("m-add message");
        assertTrue(addMessage);
        TimeUnit.MINUTES.wait(1);
        assertNotNull(bufferedReader.readLine());
    }
}