// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package codeu.chat.client.commandline;

import java.io.*;
import java.util.*;

import codeu.chat.client.core.Context;
import codeu.chat.client.core.ConversationContext;
import codeu.chat.client.core.MessageContext;
import codeu.chat.client.core.UserContext;
import codeu.chat.common.*;
import codeu.chat.util.Time;
import codeu.chat.util.Tokenizer;

public final class Chat {

  private static File logFile;
  private static PrintWriter pw_log;

  //used to access Chat's users from the user panel for interest system feature
  private Context rootPanelContext;
  private Time lastUpdate;
  private ArrayDeque<String> updates = new ArrayDeque<>();

  /**
   * ArrayDeque is a double-ended, self-resizing queue, used
   * to keep track of commands for the transaction log and chat
   * rebuilding.
   */
  private final ArrayDeque<String> transactionLog = new ArrayDeque<>();

  // PANELS
  //
  // We are going to use a stack of panels to track where in the application
  // we are. The command will always be routed to the panel at the top of the
  // stack. When a command wants to go to another panel, it will add a new
  // panel to the top of the stack. When a command wants to go to the previous
  // panel all it needs to do is pop the top panel.
  private final Stack<Panel> panels = new Stack<>();

  public Chat(Context context){

    this.panels.push(createRootPanel(context));
    logFile = new File("data/transaction_log.txt");

    try {
      // Create a new transaction_log.txt file if needed - if not, this command does nothing
      logFile.createNewFile();

      // Open the file as a PrintWriter and set file writing options to append
      pw_log = new PrintWriter(new BufferedWriter(new FileWriter(logFile, true)));
    }
    catch (Exception ex){
      System.out.println("Unable to load transaction log.");
    }

  }

  public Chat(Context context, StringWriter stringWriter) {
    this.panels.push(createRootPanel(context));
    pw_log = new PrintWriter(stringWriter);
  }

  // Transfers all data in the Queue to write to the log
  public void transferQueueToLog(){
    while(!transactionLog.isEmpty()){
      pw_log.println(transactionLog.pop());
    }
    pw_log.flush();
  }

  // HANDLE COMMAND
  //
  // Take a single line of input and parse a command from it. If the system
  // is willing to take another command, the function will return true. If
  // the system wants to exit, the function will return false.
  //
  public boolean handleCommand(String line) {

    final List<String> args = new ArrayList<>();

    final Tokenizer tokenizer = new Tokenizer(line);

    //tokenizing the input, and adding each token to the ArrayList
    tokenizer.forEachRemaining(args :: add);

    //getting the tokens/Strings as commands
    final String command = args.get(0);
    args.remove(0);

    // Because "exit" and "back" are applicable to every panel, handle
    // those commands here to avoid having to implement them for each
    // panel.

    if ("exit".equals(command)) {
      // The user does not want to process any more commands
      transferQueueToLog();

      // Flush out the buffer contents and close file
      pw_log.close();
      return false;
    }

    // Do not allow the root panel to be removed.
    if ("back".equals(command) && panels.size() > 1) {
      panels.pop();
      return true;
    }

    if (panels.peek().handleCommand(command, args)) {
      // the command was handled
      return true;
    }

    // If we get to here it means that the command was not correctly handled
    // so we should let the user know. Still return true as we want to continue
    // processing future commands.
    System.out.println("ERROR: Unsupported command");
    return true;
  }

  // CREATE ROOT PANEL
  //
  // Create a panel for the root of the application. Root in this context means
  // the first panel and the only panel that should always be at the bottom of
  // the panels stack.
  //
  // The root panel is for commands that require no specific contextual information.
  // This is before a user has signed in. Most commands handled by the root panel
  // will be user selection focused.
  //
  private Panel createRootPanel(final Context context) {

    final Panel panel = new Panel();
    rootPanelContext = context;

    // HELP
    //
    // Add a command to print a list of all commands and their description when
    // the user for "help" while on the root panel.
    //
    panel.register("help", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        System.out.println("ROOT MODE");
        System.out.println("  u-list");
        System.out.println("    List all users.");
        System.out.println("  u-add <name>");
        System.out.println("    Add a new user with the given name.");
        System.out.println("  u-sign-in <name>");
        System.out.println("    Sign in as the user with the given name.");
        System.out.println("  info");
        System.out.println("    Get server version.");
        System.out.println("  exit");
        System.out.println("    Exit the program.");
      }
    });

    // U-LIST (user list)
    //
    // Add a command to print all users registered on the server when the user
    // enters "u-list" while on the root panel.
    //
    panel.register("u-list", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        for (final UserContext user : context.allUsers()) {
          System.out.format(
              "USER %s (UUID: %s)\n",
              user.user.name,
              user.user.id);
        }
      }
    });

    // U-ADD (add user)
    //
    // Add a command to add and sign-in as a new user when the user enters
    // "u-add" while on the root panel.
    //
    panel.register("u-add", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        final String name = args.size() > 0 ? String.join(" ", args) : "";
        final UserContext user = context.create(name);

        if (name.length() > 0) {
          if (user == null) {
            System.out.println("ERROR: Failed to create new user");
          } else {
            //command user-id username creation-time
            transactionLog.add(String.format("ADD-USER %s \"%s\" %s",
                    user.user.id,
                    user.user.name,
                    user.user.creation.inMs()
            ));
          }
        } else {
          System.out.println("ERROR: Missing <username>");
        }


      }
    });

    // U-SIGN-IN (sign in user)
    //
    // Add a command to sign-in as a user when the user enters "u-sign-in"
    // while on the root panel.
    //
    panel.register("u-sign-in", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        final String name = args.size() > 0 ? String.join(" ", args) : "";
        if (name.length() > 0) {
          final UserContext user = findUser(name);
          if (user == null) {
            System.out.format("ERROR: Failed to sign in as '%s'\n", name);
          } else {
            panels.push(createUserPanel(user));
          }
        } else {
          System.out.println("ERROR: Missing <username>");
        }
      }

      // Find the first user with the given name and return a user context
      // for that user. If no user is found, the function will return null.
      private UserContext findUser(String name) {
        for (final UserContext user : context.allUsers()) {
          if (user.user.name.equals(name)) {
            return user;
          }
        }
        return null;
      }
    });

    panel.register("info", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        final ServerInfo info = context.getInfo();
        if (info == null) {
          // Communicate error to user - the server did not send us a valid
          // info object.
          System.out.println("ERROR: Failed to get valid server info.");
        } else {
          // Print the server info to the user in a pretty way
          System.out.println("Server version: " + info.version.toString());
        }
      }
    });

    // Now that the panel has all its commands registered, return the panel
    // so that it can be used.
    return panel;
  }

  private Panel createUserPanel(final UserContext user) {

    final Panel panel = new Panel();

    // HELP
    //
    // Add a command that will print a list of all commands and their
    // descriptions when the user enters "help" while on the user panel.
    //
    panel.register("help", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        System.out.println("USER MODE");
        System.out.println("  c-list");
        System.out.println("    List all conversations that the current user can interact with.");
        System.out.println("  c-add <title>");
        System.out.println("    Add a new conversation with the given title and join it as the current user.");
        System.out.println("  c-join <title>");
        System.out.println("    Join the conversation as the current user.");
        System.out.println("  c-interest-add <title>");
        System.out.println("    Adds the specified conversation to user's interests.");
        System.out.println("  c-interest-remove <title>");
        System.out.println("    Removes the specified conversation from the user's interests.");
        System.out.println("  u-interest-add <username>");
        System.out.println("    Adds the specified user to user's interests.");
        System.out.println("  u-interest-remove <username>");
        System.out.println("    Removes the specified user from the user's interests.");
        System.out.println("  status-update");
        System.out.println("    Lists what interests have been updated.");
        System.out.println("  info");
        System.out.println("    Display all info for the current user");
        System.out.println("  back");
        System.out.println("    Go back to ROOT MODE.");
        System.out.println("  exit");
        System.out.println("    Exit the program.");
      }
    });

    // C-LIST (list conversations)
    //
    // Add a command that will print all conversations when the user enters
    // "c-list" while on the user panel.
    //
    panel.register("c-list", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        for (final ConversationContext conversation : user.conversations()) {
          System.out.format(
              "CONVERSATION %s (UUID: %s)\n",
              conversation.conversation.title,
              conversation.conversation.id);
        }
      }
    });

    // C-ADD (add conversation)
    //
    // Add a command that will create and join a new conversation when the user
    // enters "c-add" while on the user panel.
    //
    panel.register("c-add", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        final String name = args.size() > 0 ? String.join(" ", args) : "";
        if (name.length() > 0) {
          final ConversationContext conversation = user.start(name);
          if (conversation == null) {
            System.out.println("ERROR: Failed to create new conversation");
          } else {
            panels.push(createConversationPanel(conversation));

            //command convo-id (uuid of)convo-owner convo-title creation-time
            transactionLog.add(String.format("ADD-CONVERSATION %s %s \"%s\" %s",
                    conversation.conversation.id,
                    conversation.conversation.owner,
                    conversation.conversation.title,
                    conversation.conversation.creation.inMs()
            ));
          }
        } else {
          System.out.println("ERROR: Missing <title>");
        }
      }
    });

    // C-JOIN (join conversation)
    //
    // Add a command that will join a conversation when the user enters
    // "c-join" while on the user panel.
    //
    panel.register("c-join", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        final String name = args.size() > 0 ? String.join(" ", args) : "";
        if (name.length() > 0) {
          final ConversationContext conversation = find(name);
          if (conversation == null) {
            System.out.format("ERROR: No conversation with name '%s'\n", name);
          } else {
            panels.push(createConversationPanel(conversation));
          }
        } else {
          System.out.println("ERROR: Missing <title>");
        }
      }

      // Find the first conversation with the given name and return its context.
      // If no conversation has the given name, this will return null.
      private ConversationContext find(String title) {
        for (final ConversationContext conversation : user.conversations()) {
          if (title.equals(conversation.conversation.title)) {
            return conversation;
          }
        }
        return null;
      }
    });

    //C-INTEREST ADD (adds conversation to user's interests)
    //
    //"c-interest add <conversation title>" will allow user to add specified
    //conversation to interests and receive status updates on conversation
    //
    panel.register("c-interest-add", new Panel.Command() {
      @Override
      public void invoke(List<String> args){
        final String name = args.size() > 0 ? String.join(" ", args) : "";
        if (name.length() > 0) {
          final ConversationContext conversation = findConversation(name);
          if (conversation == null) {
            System.out.format("ERROR: No conversation with name '%s'\n", name);
          } else {
            //add specified conversation to user's interests
            user.user.convoInterests.add(conversation);
          }
        } else {
          System.out.println("ERROR: Missing <title>");
        }
      }

      // Find the first conversation with the given name and return its context.
      // If no conversation has the given name, this will return null.
      private ConversationContext findConversation(String title) {
        for (final ConversationContext conversation : user.conversations()) {
          if (title.equals(conversation.conversation.title)) {
            return conversation;
          }
        }
        return null;
      }

    });

    //C-INTEREST REMOVES (removes conversation to user's interests)
    //
    //"c-interest add <conversation title>" will allow user to remove specified
    //conversation from interests and stop receiving status updates on conversation
    //
    panel.register("c-interest-remove", new Panel.Command() {
      @Override
      public void invoke(List<String> args){
        final String name = args.size() > 0 ? String.join(" ", args) : "";
        if (name.length() > 0) {
          final ConversationContext conversation = findConversation(name);
          if (conversation == null) {
            System.out.format("ERROR: No conversation with name '%s'\n", name);
          } else {
            //if conversation is in interests it's removed, if not nothing is done
            user.user.convoInterests.remove(conversation.conversation.id);
          }
        } else {
          System.out.println("ERROR: Missing <title>");
        }
      }

      // Find the first conversation with the given name and return its context.
      // If no conversation has the given name, this will return null.
      private ConversationContext findConversation(String title) {
        for (final ConversationContext conversation : user.conversations()) {
          if (title.equals(conversation.conversation.title)) {
            return conversation;
          }
        }
        return null;
      }
    });

    //U-INTEREST ADD (adds user to user's interests)
    //
    //"u-interest add <username>" will allow user to add specified
    //user to interests and receive status updates on conversation.
    //
    panel.register("u-interest-add", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        final String name = args.size() > 0 ? String.join(" ", args) : "";
        if (name.length() > 0) {
          final UserContext interestUser = findUser(name);
          if (user == null) {
            System.out.format("ERROR: User '%s' does not exist.\n", name);
          } else {
            //adding specified user's Uuid to current user's interests
            user.user.userInterests.add(interestUser);
          }
        } else {
          System.out.println("ERROR: Missing <username>");
        }
      }

      // Find the first user with the given name and return a user context
      // for that user. If no user is found, the function will return null.
      private UserContext findUser(String name) {
        for (final UserContext user : rootPanelContext.allUsers()) {
          if (user.user.name.equals(name)) {
            return user;
          }
        }
        return null;
      }
    });

    //U-INTEREST REMOVE (removes user from user's interests)
    //
    //"u-interest remove <username>" will allow user to remove specified
    //user from interests and stop receiving status updates on conversation.
    //
    panel.register("u-interest-remove", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        final String name = args.size() > 0 ? String.join(" ", args) : "";
        if (name.length() > 0) {
          final UserContext interestUser = findUser(name);
          if (user == null) {
            System.out.format("ERROR: User '%s' does not exist.\n", name);
          } else {
            //removing specified user from current user's interests
            user.user.userInterests.remove(interestUser.user.id);
          }
        } else {
          System.out.println("ERROR: Missing <username>");
        }
      }

      // Find the first user with the given name and return a user context
      // for that user. If no user is found, the function will return null.
      private UserContext findUser(String name) {
        for (final UserContext user : rootPanelContext.allUsers()) {
          if (user.user.name.equals(name)) {
            return user;
          }
        }
        return null;
      }
    });

    //TODO status update command
    // STATUS UPDATE
    //
    // Command that prints info about the user's interests when
    // user enters "status update" while on user panel.
    //
    // For users of interest, prints what conversations they've created and
    // what conversations they've messaged to since the last update.
    // For conversations of interest, prints how many messages have been
    // created since last update.
    //
    panel.register("status-update", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        lastUpdate = Time.now();

        // Get the users this user follows
        Set<UserContext> followedUsers = user.user.userInterests;

        // If the user follows anyone, print out their updates
        if(user.user.userInterests.size() > 0){
          System.out.println("Followed Users:");
          System.out.println();

          // Check every user this user follows for their updates
          for(UserContext users : followedUsers){
            System.out.println("USER: " + users.user.name);
            Set<ConversationContext> convoUpdates = getConversationUpdates(users, lastUpdate);

            if(convoUpdates.size() > 0)
              System.out.println(users.user.name + " has created the following conversations:");
            // For every followed user, get their conversation updates
            for(ConversationContext convo : convoUpdates)
              System.out.println(convo.conversation.title);

            // Check this followed user's conversations and see if any of them updated
            Iterable<ConversationContext> convos = user.conversations();
            if(convos.iterator().hasNext())
              System.out.println(user.user.name + " has updated the following conversations:");

            for(ConversationContext c : convos)
              if(isConversationUpdated(c, lastUpdate))
                System.out.println(c.conversation.title);
          }
        }
        else {
          System.out.println("No followed users.");
        }

        System.out.println("Followed Conversations:");
        //every conversation Uuid in interests Set should be printed with all their info

      }
    });

    // INFO
    //
    // Add a command that will print info about the current context when the
    // user enters "info" while on the user panel.
    //
    panel.register("info", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        System.out.println("User Info:");
        System.out.format("  Name : %s\n", user.user.name);
        System.out.format("  Id   : UUID: %s\n", user.user.id);
      }
    });


    // Now that the panel has all its commands registered, return the panel
    // so that it can be used.
    return panel;
  }

  private Panel createConversationPanel(final ConversationContext conversation) {

    final Panel panel = new Panel();

    // HELP
    //
    // Add a command that will print all the commands and their descriptions
    // when the user enters "help" while on the conversation panel.
    //
    panel.register("help", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        System.out.println("USER MODE");
        System.out.println("  m-list");
        System.out.println("    List all messages in the current conversation.");
        System.out.println("  m-add <message>");
        System.out.println("    Add a new message to the current conversation as the current user.");
        System.out.println("  info");
        System.out.println("    Display all info about the current conversation.");
        System.out.println("  back");
        System.out.println("    Go back to USER MODE.");
        System.out.println("  exit");
        System.out.println("    Exit the program.");
      }
    });

    // M-LIST (list messages)
    //
    // Add a command to print all messages in the current conversation when the
    // user enters "m-list" while on the conversation panel.
    //
    panel.register("m-list", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        System.out.println("--- start of conversation ---");
        for (MessageContext message = conversation.firstMessage();
                            message != null;
                            message = message.next()) {
          System.out.println();
          System.out.format("USER : %s\n", message.message.author);
          System.out.format("SENT : %s\n", message.message.creation);
          System.out.println();
          System.out.println(message.message.content);
          System.out.println();
        }
        System.out.println("---  end of conversation  ---");
      }
    });

    // M-ADD (add message)
    //
    // Add a command to add a new message to the current conversation when the
    // user enters "m-add" while on the conversation panel.
    //
    panel.register("m-add", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        final String message = args.size() > 0 ? String.join(" ", args) : "";
        if (message.length() > 0) {
          MessageContext messageContext = conversation.add(message);
          transactionLog.add(String.format("ADD-MESSAGE %s %s %s \"%s\" %s",
                  messageContext.message.id,
                  messageContext.message.author,
                  conversation.conversation.id,
                  messageContext.message.content,
                  messageContext.message.creation.inMs()
          )); //command message-id message-author message-content creation-time
        } else {
          System.out.println("ERROR: Messages must contain text");
        }
      }
    });

    // INFO
    //
    // Add a command to print info about the current conversation when the user
    // enters "info" while on the conversation panel.
    //
    panel.register("info", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        System.out.println("Conversation Info:");
        System.out.format("  Title : %s\n", conversation.conversation.title);
        System.out.format("  Id    : UUID: %s\n", conversation.conversation.id);
        System.out.format("  Owner : %s\n", conversation.conversation.owner);
      }
    });

    // Now that the panel has all its commands registered, return the panel
    // so that it can be used.
    return panel;
  }

  private boolean isConversationUpdated(ConversationContext convo, Time lastUpdate){
    return convo.lastMessage().message.creation.inMs() > lastUpdate.inMs();
  }

  private Set<MessageContext> getMessageUpdates(ConversationContext convo, Time lastUpdate){
    Set<MessageContext> messages = new HashSet<>();

    MessageContext current = convo.lastMessage();
    while(current != null){
      if(current.message.creation.inMs() > lastUpdate.inMs())
        messages.add(current);
      current = current.next();
    }

    return messages;
  }

  private Set<ConversationContext> getConversationUpdates(UserContext user, Time lastUpdate){
    Set<ConversationContext> convo = new HashSet<>();
    Iterator<ConversationContext> convoIterator = user.conversations().iterator();

    ConversationContext currentConvo;

    while((currentConvo = convoIterator.next()) != null){
      if(currentConvo.conversation.creation.inMs() > lastUpdate.inMs())
        convo.add(currentConvo);
    }
    return convo;
  }
}
