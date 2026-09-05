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

import io.github.nsyee.quintconnect.driver.Actions;
import io.github.nsyee.quintconnect.driver.Driver;
import io.github.nsyee.quintconnect.driver.DriverConfig;
import io.github.nsyee.quintconnect.driver.Step;
import io.github.nsyee.quintconnect.examples.twophasecommit.Node.Coordinator;
import io.github.nsyee.quintconnect.examples.twophasecommit.Node.Message;
import io.github.nsyee.quintconnect.examples.twophasecommit.Node.Participant;
import io.github.nsyee.quintconnect.examples.twophasecommit.Node.Stage;
import io.github.nsyee.quintconnect.itf.mapper.ItfMapper;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Connects the {@link Node} state machines to {@code spec/two_phase_commit.qnt}, a specification
 * written with the <a href="https://github.com/informalsystems/choreo/">Choreo</a> library.
 *
 * <p>Choreo keeps the whole system in one variable {@code two_phase_commit::choreo::s} and records
 * the action taken as a sum type under {@code extensions.actionTaken}, so the driver narrows the
 * checked state with {@link DriverConfig#statePath} and reads the action from that sum type with
 * {@link DriverConfig#nondetPath} instead of the {@code mbt::*} variables.
 *
 * <p>The checked state is {@code { system: NodeId -> { stage } }}: the record only declares the
 * fields to compare, the other fields of the specification's local state ({@code process_id},
 * {@code role}) and of the global state ({@code messages}, {@code events}, {@code extensions}) are
 * ignored when mapping.
 */
final class TwoPhaseCommitDriver implements Driver<TwoPhaseCommitDriver.SpecState> {

  record ProcState(Stage stage) {}

  record SpecState(Map<String, ProcState> system) {}

  private static final String COORDINATOR = "c";
  private static final List<String> PARTICIPANTS = List.of("p1", "p2", "p3");

  private final ItfMapper mapper = ItfMapper.defaultMapper();
  private final Map<String, Node> processes = new TreeMap<>();
  private final Map<String, Set<Message>> messagesSent = new TreeMap<>();

  @Override
  public DriverConfig config() {
    return DriverConfig.statePath("two_phase_commit::choreo::s")
        .nondetPath("two_phase_commit::choreo::s", "extensions", "actionTaken");
  }

  @Override
  public void step(Step step) throws Exception {
    Actions.on(step)
        .action("Init", this::init)
        .action("SpontaneouslyPrepares", s -> spontaneouslyPrepares(node(s)))
        .action("SpontaneouslyAborts", s -> spontaneouslyAborts(node(s)))
        .action("AbortsAsInstructed", s -> abortsAsInstructed(node(s)))
        .action("CommitsAsInstructed", s -> commitsAsInstructed(node(s)))
        .action("DecidesOnCommit", s -> decidesOnCommit(node(s)))
        .action("DecidesOnAbort", s -> decidesOnAbort(node(s)))
        .run();
  }

  /** Every Choreo action of the spec carries {@code { node: Node }} as its payload. */
  private String node(Step step) {
    return step.pick("node", mapper.to(String.class));
  }

  @Override
  public SpecState state() {
    Map<String, ProcState> system = new TreeMap<>();
    processes.forEach((id, node) -> system.put(id, new ProcState(node.stage())));
    return new SpecState(system);
  }

  @Override
  public Class<SpecState> stateType() {
    return SpecState.class;
  }

  private void init() {
    processes.clear();
    messagesSent.clear();
    Coordinator coordinator = new Coordinator(PARTICIPANTS.size());
    processes.put(COORDINATOR, coordinator);
    messagesSent.put(COORDINATOR, EnumSet.of(coordinator.start()));
    for (String id : PARTICIPANTS) {
      processes.put(id, new Participant());
      messagesSent.put(id, EnumSet.noneOf(Message.class));
    }
  }

  private void spontaneouslyPrepares(String node) {
    deliver(node, sentBy(COORDINATOR, Message.Prepare));
  }

  private void spontaneouslyAborts(String node) {
    recordReply(node, processes.get(node).timeout());
  }

  private void abortsAsInstructed(String node) {
    deliver(node, sentBy(COORDINATOR, Message.Abort));
  }

  private void commitsAsInstructed(String node) {
    deliver(node, sentBy(COORDINATOR, Message.Commit));
  }

  private void decidesOnCommit(String node) {
    for (String participant : PARTICIPANTS) {
      if (messagesSent.get(participant).contains(Message.Prepared)) {
        deliver(node, Message.Prepared);
      }
    }
  }

  private void decidesOnAbort(String node) {
    recordReply(node, processes.get(node).timeout());
  }

  private void deliver(String node, Message msg) {
    recordReply(node, processes.get(node).receive(msg));
  }

  private void recordReply(String node, Optional<Message> reply) {
    reply.ifPresent(messagesSent.get(node)::add);
  }

  private Message sentBy(String node, Message msg) {
    if (!messagesSent.get(node).contains(msg)) {
      throw new IllegalStateException(node + " never sent " + msg);
    }
    return msg;
  }
}
