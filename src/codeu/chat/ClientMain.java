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

package codeu.chat;

import java.io.*;

import codeu.chat.client.commandline.Chat;
import codeu.chat.client.core.Context;
import codeu.chat.util.*;
import codeu.chat.util.connections.ClientConnectionSource;
import codeu.chat.util.connections.ConnectionSource;

final class ClientMain {

  private static final Logger.Log LOG = Logger.newLog(ClientMain.class);

  private static Time lastLogBackup;
  private static final long BACKUP_RATE_IN_MS = 30000;

  private static Chat chat;

  private static void reloadOldInterests() throws IOException {
    // Open the transaction log file for reading
    BufferedReader bufferedReader = new BufferedReader(new FileReader("data/transaction_log.txt"));

    // Read the header lines of each transaction log
    String line;

    System.out.println("Loading interest system...");

    while((line = bufferedReader.readLine()) != null) {

      // Instantiate a Tokenizer to parse through log's data
      Tokenizer logInfo = new Tokenizer(line);

      String commandType = logInfo.next();

      if(commandType.equals("ADD-INTEREST-USER")){
        Uuid owner = Uuid.parse(logInfo.next());
        Uuid follow = Uuid.parse(logInfo.next());

        chat.addUserInterest(owner, follow);
      }
      else if(commandType.equals("REMOVE-INTEREST-USER")){
        Uuid owner = Uuid.parse(logInfo.next());
        Uuid follow = Uuid.parse(logInfo.next());

        chat.removeUserInterest(owner, follow);
      }
      else if(commandType.equals("ADD-INTEREST-CONVERSATION")){
        Uuid owner = Uuid.parse(logInfo.next());
        Uuid follow = Uuid.parse(logInfo.next());

        chat.addConvoInterest(owner, follow);
      }
      else if(commandType.equals("REMOVE-INTEREST-CONVERSATION")){
        Uuid owner = Uuid.parse(logInfo.next());
        Uuid follow = Uuid.parse(logInfo.next());

        chat.removeConvoInterest(owner, follow);
      }

      line = bufferedReader.readLine();
    }

    LOG.info("Successfully restored last logged interest system state.");

    System.out.println("Successfully loaded interest system!");

    bufferedReader.close();
  }

  public static void main(String [] args) {

    try {
      Logger.enableFileOutput("data/chat_client_log.log");
    } catch (IOException ex) {
      LOG.error(ex, "Failed to set logger to write to file");
    }

    LOG.info("============================= START OF LOG =============================");

    LOG.info("Starting chat client...");

    final RemoteAddress address = RemoteAddress.parse(args[0]);

    final ConnectionSource source = new ClientConnectionSource(address.host, address.port);

    LOG.info("Creating client...");

    chat = new Chat(new Context(source));

    LOG.info("Created client");

    // Reload old interests
    try {
      reloadOldInterests();
    } catch (Exception e) {
      LOG.info("Could not reload last logged interest system.");
    }

    boolean keepRunning = true;

    lastLogBackup = Time.now();

    try (final BufferedReader input = new BufferedReader(new InputStreamReader(System.in))) {
      while (keepRunning) {
        System.out.print(">>> ");

        // Evaluate if it is time to transfer data from queue to disk, then call the transferQueueToLog() method defined
        // above and update the last backup time
        Time currentTime = Time.now();
        if(currentTime.inMs() - lastLogBackup.inMs() >= BACKUP_RATE_IN_MS){
          chat.transferQueueToLog();
          lastLogBackup = currentTime;
        }

        keepRunning = chat.handleCommand(input.readLine().trim());
      }
    } catch (IOException ex) {
      LOG.error("Failed to read from input");
    }

    LOG.info("chat client has exited.");
  }
}
