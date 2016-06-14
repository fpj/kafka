/**
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this file
 * to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.apache.kafka.clients.producer;


/**
 * The session identifier guarantees that applications can restart from a consistent
 * state. Say that we produce atomically a set of messages, but we crash in the middle.
 * Upon restart, we may want to know whether producing those messages has been rolled
 * forward or backward. If we don't have any form of identification, then upon restarting,
 * the application doesn't know what happened to the previous atomic produce, and cannot
 * decide whether to produce again or move forward. For such applications that require
 * to restart from a safe state, we need to make sure that any set of messages atomically
 * produced comes to a resolution: stable or discarded.
 *
 * To make such a guarantee, we require the application to persist the session id and
 * give it back to the producer upon instantiation.
 */
public interface SessionId {
    public byte[] toBytes();
}
