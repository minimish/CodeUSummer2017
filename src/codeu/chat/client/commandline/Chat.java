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
import codeu.chat.util.Tokenizer;
import codeu.chat.util.Uuid;

public final class Chat {

  private static File logFile;
  private static PrintWriter pw_log;

  //used to access Chat's users from the user panel for interest system feature
  private Context rootPanelContext;
  //used to access Chat's conversations from outside the user panel
  private UserContext userPanelContext;

  private HashMap<Uuid, Set<Uuid>> userInterestMap = new HashMap<>();
  private HashMap<Uuid, Set<Uuid>> convoInterestMap = new HashMap<>();

  // Map to enable each user's message counts for followed conversations to be independent
  private HashMap<Uuid, HashMap<Uuid, Integer>> convoMessageCountsMap = new HashMap<>();

  // Map holding each user's updated conversations for status update
  private HashMap<Uuid, Set<Uuid>> newConversationsMap = new HashMap<>();

  private HashMap<Uuid, Set<Uuid>> updatedConversationsMap = new HashMap<>();

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
        for (final UserContext user : context.allUsers().values()) {
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
//            Set<Uuid> updatedConversations = new HashSet<>();
//            updatedConversationsMap.put(user.user.id, updatedConversations);
            panels.push(createUserPanel(user));
          }
        } else {
          System.out.println("ERROR: Missing <username>");
        }
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
          System.out.println("Server uptime: " + info.startTime.toString());
        }
      }
    });

    // Now that the panel has all its commands registered, return the panel
    // so that it can be used.
    return panel;
  }

  private Panel createUserPanel(final UserContext user) {

    final Panel panel = new Panel();
    userPanelContext = user;

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
        System.out.println("  c-interest-list");
        System.out.println("    List all conversations that the current user follows.");
        System.out.println("  c-interest-add <title>");
        System.out.println("    Adds the specified conversation to user's interests.");
        System.out.println("  c-interest-remove <title>");
        System.out.println("    Removes the specified conversation from the user's interests.");
        System.out.println("  u-interest-list");
        System.out.println("    List all users that the current user follows.");
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
        for (final ConversationContext conversation : user.conversations().values()) {
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

            // If this user isn't in the new conversations map, add them with a new Set
            Set<Uuid> newConversations = newConversationsMap.computeIfAbsent(user.user.id, id -> new HashSet<>());
            newConversations.add(conversation.conversation.id);
            newConversationsMap.put(user.user.id, newConversations);

            transactionLog.add(String.format("ADD-CONVERSATION %s %s \"%s\" %s",
                    conversation.conversation.id,
                    conversation.conversation.owner,
                    conversation.conversation.title,
                    conversation.conversation.creation.inMs()
            ));

            conversation.conversation.toggleUserToCreator(user.user, true);
            conversation.conversation.toggleUserToOwner(user.user, true);
            conversation.conversation.toggleUserToMember(user.user, true);
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
            conversation.conversation.toggleUserToMember(user.user, true);
          }
        } else {
          System.out.println("ERROR: Missing <title>");
        }
      }

      // Find the first conversation with the given name and return its context.
      // If no conversation has the given name, this will return null.
      private ConversationContext find(String title) {
        for (final ConversationContext conversation : user.conversations().values()) {
          if (title.equals(conversation.conversation.title)) {
            return conversation;
          }
        }
        return null;
      }
    });

    // C-INTEREST-LIST (list conversations)
    //
    // Add a command that will print all conversations the user is interested in when the user enters
    // "c-interest-list" while on the user panel.
    //
    panel.register("c-interest-list", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        Set<Uuid> interestedConvos = convoInterestMap.get(user.user.id);
        if(interestedConvos != null){
          for (final Uuid convoID : interestedConvos) {
            final ConversationContext conversation = findConversation(convoID);
            System.out.format(
                    "CONVERSATION %s (UUID: %s)\n",
                    conversation.conversation.title,
                    conversation.conversation.id
            );
          }
        }
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
              addConvoInterest(user.user.id, conversation.conversation.id);

              transactionLog.add(String.format("ADD-INTEREST-CONVERSATION %s %s",
                      user.user.id,
                      conversation.conversation.id
              ));
          }
        } else {
          System.out.println("ERROR: Missing <title>");
        }
      }

      // Find the first conversation with the given name and return its context.
      // If no conversation has the given name, this will return null.
      private ConversationContext findConversation(String title) {
        for (final ConversationContext conversation : user.conversations().values()) {
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
        final String name = String.join(" ", args);
        if (name.length() > 0) {
          final ConversationContext conversation = findConversation(name);
          if (conversation == null) {
            System.out.format("ERROR: No conversation with name '%s'\n", name);
          } else {
            removeConvoInterest(user.user.id, conversation.conversation.id);

            transactionLog.add(String.format("REMOVE-INTEREST-CONVERSATION %s %s",
                    user.user.id,
                    conversation.conversation.id
            ));
          }
        } else {
          System.out.println("ERROR: Missing <title>");
        }
      }
    });

    // U-INTEREST-LIST (list conversations)
    //
    // Add a command that will print all users the current user is interested in when the user enters
    // "u-interest-list" while on the user panel.
    //
    panel.register("u-interest-list", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        if(userInterestMap.get(user.user.id) != null){
          for (final Uuid userID : userInterestMap.get(user.user.id)) {
            final UserContext user = findUser(userID);
            System.out.format(
                    "USER %s (UUID: %s)\n",
                    user.user.name,
                    user.user.id
            );
          }
        }
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
          if (interestUser == null) {
            System.out.format("ERROR: User '%s' does not exist.\n", name);
          } else {
              addUserInterest(user.user.id, interestUser.user.id);

              transactionLog.add(String.format("ADD-INTEREST-USER %s %s",
                      user.user.id,
                      interestUser.user.id
              ));
          }
        } else {
          System.out.println("ERROR: Missing <username>");
        }
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
          if (interestUser == null) {
            System.out.format("ERROR: User '%s' does not exist.\n", name);
          } else {
            removeUserInterest(user.user.id, interestUser.user.id);

            transactionLog.add(String.format("REMOVE-INTEREST-USER %s %s",
                    user.user.id,
                    interestUser.user.id
            ));
          }
        } else {
          System.out.println("ERROR: Missing <username>");
        }
      }
    });

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
        Set<Uuid> followedUsers = userInterestMap.get(user.user.id);
        Set<Uuid> followedConversations = convoInterestMap.get(user.user.id);

        // If the user follows anyone, print their status
        if(followedUsers != null && followedUsers.size() > 0){
          System.out.println("============= Followed Users: =============");

          // Iterate through the users and print their new and updated conversations
          for(Uuid followedUserID : followedUsers){
            UserContext followedUser = findUser(followedUserID);
            boolean userActivity = false;

            System.out.format("Name: %s (UUID: %s)\n", followedUser.user.name, followedUser.user.id);
            System.out.format("\t%s has added and updated these conversations:\n", followedUser.user.name);

            // Iterate through all the conversations this user has and check if they created/updated any
            for(ConversationContext followedUserConversation : followedUser.conversations().values()) {
              // If the newConversationsMap has this followed user and this specific conversation, print that they created it
              if (newConversationsMap.get(followedUserID) != null && newConversationsMap.get(followedUserID).contains(followedUserConversation.conversation.id)) {
                System.out.format("\t\tCreated: %s (UUID: %s)\n", followedUserConversation.conversation.title, followedUserConversation.conversation.id);
                newConversationsMap.get(followedUserID).remove(followedUserConversation.conversation.id);
                userActivity = true;
              }
              // If this conversation has a count > 0, print that they updated it
              if (updatedConversationsMap.get(followedUserID) != null && updatedConversationsMap.get(followedUserID).contains(followedUserConversation.conversation.id)) {
                System.out.format("\t\tUpdated: %s (UUID: %s)\n", followedUserConversation.conversation.title, followedUserConversation.conversation.id);
                removeUpdatedConversation(followedUserID, followedUserConversation.conversation.id);
                userActivity = true;
              }
            }

            // Specify if the user has no activity
            if(!userActivity)
              System.out.println("\t\tNone.");
          }
        }

        // If the user follows any conversations, print their status
        if(followedConversations != null && followedConversations.size() > 0){
          System.out.println("========= Followed Conversations: =========");

          // Iterate through the followed conversations and print their message counts
          for(Uuid followedConversationID : followedConversations){
            ConversationContext followedConversation = findConversation(followedConversationID);
            System.out.format("Name: %s (UUID: %s)\n", followedConversation.conversation.title, followedConversation.conversation.id);

            // Get the message count contributed by ALL users
            Integer totalMessageCount = (getMessageCount(user.user.id, followedConversationID) == null) ? 0 : getMessageCount(user.user.id, followedConversationID);
            System.out.format("\tMessages added since last update: %d\n", totalMessageCount);

          }
        }

        // Reset message count for a fresh new status update
        HashMap<Uuid, Integer> messageCountsForUser = convoMessageCountsMap.get(user.user.id);
        if(messageCountsForUser != null){
          Set<Uuid> conversationsCounts = messageCountsForUser.keySet();
          for(Uuid convo : conversationsCounts){
            messageCountsForUser.put(convo, 0);
            convoMessageCountsMap.put(user.user.id, messageCountsForUser);
          }
        }

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
        System.out.println("  u-remove-member <username>");
        System.out.println("    Remove a member of the current conversation, can only be done by conversation owners or creator.");
        System.out.println("  u-remove-owner <username>");
        System.out.println("    Remove an owner of the current conversation, can only be done by conversation's creator.");
        System.out.println("  u-add-owner <username>");
        System.out.println("    Add an owner of the current conversation, can only be done by conversation's creator.");
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
        if (!conversation.conversation.isMember(userPanelContext.user)){
          final String message = args.size() > 0 ? String.join(" ", args) : "";
          if (message.length() > 0) {
            MessageContext messageContext = conversation.add(message);

            // Increment the message count for every conversation across the board
            incrementMessageCount(conversation.conversation.id);

            // Only indicate this conversation as updated by THIS user
            updateConversationsMap(userPanelContext.user.id, conversation.conversation.id);

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
        else {
          System.out.println("ERROR: You are not a member of the conversation, and may not add a message.");
        }
      }
    });

    // U-REMOVE-MEMBER (removes member from conversation)
    //
    // A user who's the conversation's owner or creator may use this command
    // to remove a member/user from the conversation.
    //
    panel.register("u-remove-member", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        final String name = args.size() > 0 ? String.join(" ", args) : "";
        ConversationHeader currentConvo = conversation.conversation;

        if (currentConvo.isOwner(userPanelContext.user) || currentConvo.isCreator(userPanelContext.user)){
          if (name.length() > 0) {
            final UserContext removeUser = findUser(name);
            if (removeUser == null) {
              System.out.format("ERROR: User '%s' does not exist.\n", name);
            }
            else if (currentConvo.isCreator(removeUser.user) || currentConvo.isOwner(removeUser.user)){
              System.out.format("ERROR: User '%s' is an owner or creator.\n", name);
            }
            else {
              conversation.conversation.toggleUserToMember(removeUser.user, false);
            }
          } else {
            System.out.println("ERROR: Missing <username>");
          }
        }
        else {
          System.out.println("ERROR: Only users with owner or creator status\n can remove members from a conversation.");
        }
      }
    });

    // U-REMOVE-OWNER (removes owner of conversation)
    //
    // A user who's the conversation's creator may use this command
    // to remove a user's owner status of the conversation.
    //
    panel.register("u-remove-owner", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        final String name = args.size() > 0 ? String.join(" ", args) : "";

        if (conversation.conversation.isCreator(userPanelContext.user)){
          if (name.length() > 0) {
            final UserContext removedOwner = findUser(name);
            if (removedOwner == null) {
              System.out.format("ERROR: User '%s' does not exist.\n", name);
            } else {
              System.out.format("Owner bit - pre: %b\n", conversation.conversation.isOwner(removedOwner.user));
              conversation.conversation.toggleUserToOwner(removedOwner.user, false);
              System.out.format("Owner bit: %b\n", conversation.conversation.isOwner(removedOwner.user));
            }
          } else {
            System.out.println("ERROR: Missing <username>");
          }
        }
        else {
          System.out.println("ERROR: Only creators may remove owners from a conversation.");
        }
      }
    });

    // U-ADD-OWNER (removes owner of conversation)
    //
    // A user who's the conversation's creator may use this command
    // to add owner status of the conversation to a user.
    //
    panel.register("u-add-owner", new Panel.Command() {
      @Override
      public void invoke(List<String> args) {
        final String name = args.size() > 0 ? String.join(" ", args) : "";

        if (conversation.conversation.isCreator(userPanelContext.user)) {
          if (name.length() > 0) {
            final UserContext addedOwner = findUser(name);
            if (addedOwner == null) {
              System.out.format("ERROR: User '%s' does not exist.\n", name);
            } else {
              System.out.format("Owner bit - pre: %b\n", conversation.conversation.isOwner(addedOwner.user));
              conversation.conversation.toggleUserToOwner(addedOwner.user, true);
              System.out.format("Owner bit: %b\n", conversation.conversation.isOwner(addedOwner.user));
            }
          } else {
            System.out.println("ERROR: Missing <username>");
          }
        }
        else {
          System.out.println("ERROR: Only creators may add owners to a conversation.");
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

  private void updateConversationsMap(Uuid user, Uuid convo){
    Set<Uuid> conversations = updatedConversationsMap.computeIfAbsent(user, convoID -> new HashSet<>());

    conversations.add(convo);
    updatedConversationsMap.put(user, conversations);
  }

  private void removeUpdatedConversation(Uuid user, Uuid convo) {
    if(updatedConversationsMap.containsKey(user) && updatedConversationsMap.get(user) != null){
      Set<Uuid> conversations = updatedConversationsMap.get(user);
      conversations.remove(convo);
      updatedConversationsMap.put(user,conversations);
    }

  }

  // Increments the message count of a specified conversation of interest
  // of a specific user.
  private void incrementMessageCount(Uuid convoID){

    for(UserContext user : rootPanelContext.allUsers().values()){
      HashMap<Uuid, Integer> convoCount = (convoMessageCountsMap.get(user.user.id) == null) ? new HashMap<>() : convoMessageCountsMap.get(user.user.id);
      Integer count = (convoCount.get(convoID) == null) ? 0 : convoCount.get(convoID);

      convoCount.put(convoID, count + 1);
      convoMessageCountsMap.put(user.user.id, convoCount);
    }
  }

  // Getter method for conversation message counts in conversations of interest.
  private Integer getMessageCount(Uuid userID, Uuid convoID){
    return (convoMessageCountsMap.get(userID) == null) ? 0 : convoMessageCountsMap.get(userID).get(convoID);
  }

  //methods below are helper methods to get a user or conversation from name or Uuid

  // Find the first user with the given name and return a user context
  // for that user. If no user is found, the function will return null.
  private UserContext findUser(String name) {
    for (final UserContext user : rootPanelContext.allUsers().values()) {
      if (user.user.name.equals(name)) {
        return user;
      }
    }
    return null;
  }

  // Finds the first user with the given Uuid and returns a user context
  // for that user. If no user is found, the function will return null.
  private UserContext findUser(Uuid id) {
    return rootPanelContext.allUsers().get(id);
  }

  // Find the first conversation with the given name and return its context.
  // If no conversation has the given name, this will return null.
  private ConversationContext findConversation(String title) {
    for (final ConversationContext conversation : userPanelContext.conversations().values()) {
      if (title.equals(conversation.conversation.title)) {
        return conversation;
      }
    }
    return null;
  }

  // Finds the first conversation with the given name and returns its context.
  // If no conversation has the given name, this will return null.
  private ConversationContext findConversation(Uuid id) {
    return userPanelContext.conversations().get(id);
   }

  public void addUserInterest(Uuid userID, Uuid followedUserID){
    Set<Uuid> userInterest = userInterestMap.computeIfAbsent(userID, followedUser -> new HashSet<>());

    // Check if the user is trying to follow themselves
    if(userID.id() == followedUserID.id()) {
      System.out.println("ERROR: Cannot add yourself to followed users list!");
      return;
    }

    if(userInterest.contains(followedUserID))
      System.out.println("ERROR: User is already followed!");
    else {
      HashMap<Uuid, Integer> followedUserConversations = convoMessageCountsMap.computeIfAbsent(followedUserID, messageCount -> new HashMap<>());

      // Intialize the message count for all of this users conversations to 0
      for(ConversationContext c : findUser(followedUserID).conversations().values()){
        followedUserConversations.put(c.conversation.id, 0);
      }

      convoMessageCountsMap.put(followedUserID, followedUserConversations);

      userInterest.add(followedUserID);
      userInterestMap.put(userID, userInterest);
    }
  }

  public void addConvoInterest(Uuid userID, Uuid followedConvoID){
    Set<Uuid> convoInterest = convoInterestMap.computeIfAbsent(userID, followedConvo -> new HashSet<>());

    if(convoInterest.contains(followedConvoID))
        System.out.println("ERROR: Conversation is already in interest list!");
    else {
      convoInterest.add(followedConvoID);
      convoInterestMap.put(userID, convoInterest);

      HashMap<Uuid, Integer> followedConversations = convoMessageCountsMap.computeIfAbsent(userID, messageCount -> new HashMap<>());
      followedConversations.put(followedConvoID, 0);
      convoMessageCountsMap.put(userID, followedConversations);
    }
  }

  public void removeUserInterest(Uuid userID, Uuid followedUserID){
    // Only remove the followed user if this user has a Set value mapped to them
    if(userInterestMap.containsKey(userID))
      userInterestMap.get(userID).remove(followedUserID);
  }

  public void removeConvoInterest(Uuid userID, Uuid convoID){
    // Only remove the interested convo if the user has a value mapped to them
    if(convoInterestMap.get(userID) != null && convoMessageCountsMap.get(userID) != null) {
      convoInterestMap.get(userID).remove(convoID);
      //stops keeping track of message count
      convoMessageCountsMap.get(userID).remove(convoID);
    }
    else {
      System.out.println("ERROR: Conversation was not in interests!");
    }
  }
}
