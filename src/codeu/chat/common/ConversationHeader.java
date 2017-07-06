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

package codeu.chat.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

import codeu.chat.util.Serializer;
import codeu.chat.util.Serializers;
import codeu.chat.util.Time;
import codeu.chat.util.Uuid;

public final class ConversationHeader {

  public static final Serializer<ConversationHeader> SERIALIZER = new Serializer<ConversationHeader>() {

    @Override
    public void write(OutputStream out, ConversationHeader value) throws IOException {

      Uuid.SERIALIZER.write(out, value.id);
      Uuid.SERIALIZER.write(out, value.owner);
      Time.SERIALIZER.write(out, value.creation);
      Serializers.STRING.write(out, value.title);
    }

    @Override
    public ConversationHeader read(InputStream in) throws IOException {

      return new ConversationHeader(
          Uuid.SERIALIZER.read(in),
          Uuid.SERIALIZER.read(in),
          Time.SERIALIZER.read(in),
          Serializers.STRING.read(in)
      );

    }
  };

  public final Uuid id;
  public final Uuid owner;
  public final Time creation;
  public final String title;

  public final HashMap<Uuid, Integer> accessControl;

  public static final int MEMBER = 0x0001;
  public static final int OWNER = 0x0002;
  public static final int CREATOR = 0x0004;

  public ConversationHeader(Uuid id, Uuid owner, Time creation, String title) {

    this.id = id;
    this.owner = owner;
    this.creation = creation;
    this.title = title;

    accessControl = new HashMap<>();
  }

  // Flag is set to true if user is to be set to new status, else it's bit is 'turned off'
  private void toggleUserToMember(User u, boolean flag){
    Integer access = accessControl.computeIfAbsent(u.id, newAccess -> 0);
    Integer newAccess;

    if(flag)
      newAccess = access | MEMBER;
    else {
      newAccess = access & MEMBER;
      toggleUserToOwner(u, false);
      toggleUserToCreator(u, false);
    }

    accessControl.put(u.id, newAccess);
  }

  private void toggleUserToOwner(User u, boolean flag){
    Integer access = accessControl.computeIfAbsent(u.id, newAccess -> 0);
    Integer newAccess;

    if(flag){
      newAccess = access | OWNER;
      toggleUserToMember(u, true);
    }
    else {
      newAccess = access & OWNER;
      toggleUserToCreator(u, false);
    }

    accessControl.put(u.id, newAccess);
  }

  private void toggleUserToCreator(User u, boolean flag){
    Integer access = accessControl.computeIfAbsent(u.id, newAccess -> 0);
    Integer newAccess;

    if(flag) {
      newAccess = access | CREATOR;
      toggleUserToOwner(u, true);
      toggleUserToMember(u, true);
    }
    else
      newAccess = access & CREATOR;

    accessControl.put(u.id, newAccess);
  }
}
