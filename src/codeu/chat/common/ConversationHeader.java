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
import java.io.Serializable;
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

  public HashMap<Uuid, Integer> accessControls;

  private static final int MEMBER = 0x0001;
  private static final int OWNER = 0x0002;
  private static final int CREATOR = 0x0004;
  private static final int REMOVED = 0x0008;

  public ConversationHeader(Uuid id, Uuid owner, Time creation, String title) {

    this.id = id;
    this.owner = owner;
    this.creation = creation;
    this.title = title;

    accessControls = new HashMap<>();
  }

  // Flag is set to true if user is to be set to new status, else it's bit is 'turned off'
  public void toggleUserToMember(User u, boolean flag){
    Integer access = accessControls.computeIfAbsent(u.id, newAccess -> 0);
    Integer newAccess;

    if(flag)
      newAccess = access | MEMBER;
    // If member bit is to be 'turned off', also turn off it's parent controls
    else {
      newAccess = access & ~MEMBER;
      toggleUserToOwner(u, false);
      toggleUserToCreator(u, false);
    }
    accessControls.put(u.id, newAccess);
  }

  public void toggleUserToOwner(User u, boolean flag){
    Integer access = accessControls.computeIfAbsent(u.id, newAccess -> 0);
    Integer newAccess;

    // If user is to be set as an Owner, it will also be it's children controls
    if(flag){
      newAccess = access | OWNER;
      toggleUserToMember(u, true);
    }
    // If user is not an Owner anymore, it is also not it's parent controls anymore
    else {
      newAccess = access & ~OWNER;
      toggleUserToCreator(u, false);
    }
    accessControls.put(u.id, newAccess);
  }

  public void toggleUserToCreator(User u, boolean flag){
    Integer access = accessControls.computeIfAbsent(u.id, newAccess -> 0);
    Integer newAccess;

    // If user is to be set as a Creator, it will also be it's children controls
    if(flag) {
      newAccess = access | CREATOR;
      toggleUserToOwner(u, true);
      toggleUserToMember(u, true);
    }
    else
      newAccess = access & ~CREATOR;

    accessControls.put(u.id, newAccess);
  }

  // Flag is set to true if the user is removed from a conversation.
  // Flag stays true once it's set.
  public void toggleRemoved(User u){
    Integer access = accessControls.computeIfAbsent(u.id, newAccess -> 0);
    Integer newAccess;

    newAccess = access | REMOVED;

    accessControls.put(u.id, newAccess);
  }

  public boolean isMember(User u){
    return accessControls.get(u.id) != null && (accessControls.get(u.id) & MEMBER) != 0;
  }

  public boolean isOwner(User u){
    return accessControls.get(u.id) != null && (accessControls.get(u.id) & OWNER) != 0;
  }

  public boolean isCreator(User u){
    return accessControls.get(u.id) != null && (accessControls.get(u.id) & CREATOR) != 0;
  }

  public boolean hasBeenRemoved(User u){
    return accessControls.get(u.id) != null && (accessControls.get(u.id) & REMOVED) != 0;
  }
}