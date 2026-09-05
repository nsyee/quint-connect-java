/*
 * Copyright 2026 the quint-connect-java authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.nsyee.quintconnect.examples.twophasecommit;

import java.util.Optional;

/**
 * A node of a simplified two-phase commit protocol: either the {@link Coordinator} or a {@link
 * Participant}. Nodes are state machines driven by {@link #receive(Message)} and {@link
 * #timeout()}; both return the message to send in reply, if any.
 */
public sealed interface Node permits Node.Coordinator, Node.Participant {

  /** The protocol stages a node goes through. */
  enum Stage {
    /** Initial stage; no decision has been made. */
    Working,
    /** A participant that voted to commit and waits for the decision. */
    Prepared,
    /** The transaction was committed. */
    Committed,
    /** The transaction was aborted. */
    Aborted
  }

  /** The messages exchanged between nodes. */
  enum Message {
    /** Coordinator asks the participants to vote. */
    Prepare,
    /** A participant votes to commit. */
    Prepared,
    /** Coordinator decides on commit. */
    Commit,
    /** Coordinator decides on abort. */
    Abort
  }

  /** The stage this node is currently in. */
  Stage stage();

  /** A local timeout fired; a node still working gives up and aborts. */
  Optional<Message> timeout();

  /** Handles a message from another node. */
  Optional<Message> receive(Message msg);

  /** Collects {@link Message#Prepared} votes and commits once a quorum is reached. */
  final class Coordinator implements Node {
    private final int quorum;
    private int prepared;
    private Stage stage = Stage.Working;

    /** Creates a coordinator that commits after {@code quorum} participants prepared. */
    public Coordinator(int quorum) {
      this.quorum = quorum;
    }

    /** The message that opens the protocol. */
    public Message start() {
      return Message.Prepare;
    }

    @Override
    public Stage stage() {
      return stage;
    }

    @Override
    public Optional<Message> timeout() {
      if (stage == Stage.Working) {
        stage = Stage.Aborted;
        return Optional.of(Message.Abort);
      }
      return Optional.empty();
    }

    @Override
    public Optional<Message> receive(Message msg) {
      if (stage == Stage.Working && msg == Message.Prepared) {
        prepared++;
        if (prepared == quorum) {
          stage = Stage.Committed;
          return Optional.of(Message.Commit);
        }
      }
      return Optional.empty();
    }
  }

  /** Votes when asked to prepare and then follows the coordinator's decision. */
  final class Participant implements Node {
    private Stage stage = Stage.Working;

    @Override
    public Stage stage() {
      return stage;
    }

    @Override
    public Optional<Message> timeout() {
      if (stage == Stage.Working) {
        stage = Stage.Aborted;
      }
      return Optional.empty();
    }

    @Override
    public Optional<Message> receive(Message msg) {
      switch (msg) {
        case Prepare -> {
          if (stage == Stage.Working) {
            stage = Stage.Prepared;
            return Optional.of(Message.Prepared);
          }
        }
        case Abort -> stage = Stage.Aborted;
        case Commit -> stage = Stage.Committed;
        case Prepared -> {}
      }
      return Optional.empty();
    }
  }
}
