package codeu.chat.client.commandline;

import static org.junit.Assert.*;

import codeu.chat.client.core.*;
import codeu.chat.common.*;
import codeu.chat.util.Time;
import codeu.chat.util.Uuid;
import codeu.chat.util.connections.ClientConnectionSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.model.TestClass;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Created by dita on 6/17/17.
 */
public final class ChatTest {

    private ServerInfo serverInfo;
    private User user;
    private UserContext userContext;
    private ConversationHeader convo;
    private ConversationContext convoContext;
    private Message message;
    private MessageContext messageContext;

    private TemporaryFolder folder = new TemporaryFolder();

    private class TestController extends Controller {

        public TestController(){
            super(new ClientConnectionSource("localhost", 1025));
        }

        @Override
        public Message newMessage(Uuid author, Uuid conversation, String body) {
            return message;
        }

        @Override
        public User newUser(String name) {
            return user;
        }

        @Override
        public ConversationHeader newConversation(String title, Uuid owner) {
            return convo;
        }
    }

    private class TestView extends codeu.chat.client.core.View {

        public TestView() {
            super(new ClientConnectionSource("localhost", 1025));
        }

        @Override
        public Collection<User> getUsers() {
            Collection<User> users = new ArrayList<>();
            users.add(user);

            return users;
        }

        @Override
        public Collection<ConversationHeader> getConversations() {
            Collection<ConversationHeader> conversations = new ArrayList<>();
            conversations.add(convo);

            return conversations;
        }

        @Override
        public Collection<ConversationPayload> getConversationPayloads(Collection<Uuid> ids) {
            return super.getConversationPayloads(ids);
        }

        @Override
        public Collection<Message> getMessages(Collection<Uuid> ids) {
            Collection<Message> messages = new ArrayList<>();
            messages.add(message);

            return messages;
        }

        @Override
        public ServerInfo getInfo() {
            return serverInfo;
        }
    }

    private class TestContext extends Context{

        private final UserContext userContext = new UserContext(user, new TestView(), new TestController());
        private final TestView view;
        private final TestController controller;

        public TestContext(TestView view, TestController controller) {
            super(new ClientConnectionSource("localhost", 1025));

            this.view = view;
            this.controller = controller;
        }

        @Override
        public UserContext create(String name) {
            return userContext;
        }

        @Override
        public Iterable<UserContext> allUsers() {
            final Collection<UserContext> users = new ArrayList<>();
            users.add(userContext);

            return users;
        }

        @Override
        public ServerInfo getInfo() {
            return serverInfo;
        }
    }

    private Chat chat;

    @Before
    public void doBefore() throws IOException{
        TestView view = new TestView();
        TestController controller = new TestController();

        serverInfo = new ServerInfo();
        user = new User(Uuid.NULL, "username", Time.now());
        convo = new ConversationHeader(Uuid.NULL, Uuid.NULL, Time.now(), "convo");
        message = new Message(Uuid.NULL, Uuid.NULL, Uuid.NULL, Time.now(), Uuid.NULL, "message");

        userContext = new UserContext(user, view, controller);
        convoContext = new ConversationContext(user, convo, view, controller);
        messageContext = new MessageContext(message, view);

        chat = new Chat(new TestContext(view, controller));
    }

    @Test
    public void addUserTest() throws Exception {
        boolean addUser = chat.handleCommand("u-sign-in username");
        assertEquals(true, addUser);
    }

    @Test
    public void addConvoTest() throws Exception {
        chat.handleCommand("u-sign-in username");

        boolean addConvo = chat.handleCommand("c-join convo");
        assertEquals(true, addConvo);
    }

    @Test
    public void addMessageTest() throws Exception {
        chat.handleCommand("u-sign-in username");
        chat.handleCommand("c-join convo");
        boolean addMessage = chat.handleCommand("m-add message");
        assertEquals(true, addMessage);
    }
}