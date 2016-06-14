/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.clients.producer;

public class SessionHandle {
    private long pid;
    private byte[] appState;

    /**
     *  A producer instance calls this constructor to contain
     * the producer id associated to this session.
     *
     * @param pid
     */
    SessionHandle(long pid) {
        this(pid, null);
    }

    /**
     * If the application is restarting and the producer needs
     * to pass the last successfully snapshotted state to the
     * application, then we need to use this constructor. The
     * application obtain its application state for initialization
     * via the session handle.
     *
     * @param pid
     * @param appState
     */
    SessionHandle(long pid, byte[] appState) {
        this.pid = pid;
        this.appState = appState;
    }
    /**
     * An application call this constructor passing the bytes
     * it persisted upon the last time this session was
     * initialized.
     *
     * @param idBytes
     */
    public SessionHandle(byte[] idBytes) {
        restore(idBytes);
    }

    /**
     * Serializes this object and return the bytes so that the
     * application can persist it.
     *
     * @return Serialized version of this object.
     */
    public byte[] getBytes() {
        return new byte[0];
    }

    /**
     * Returns the bytes that the application needs to use to
     * initialize its state upon restart, e.g., these bytes
     * when deserialized can represent the set of input offsets
     * that this application needs to use upon recovery.
     *
     * @return Application state bytes
     */
    public byte[] getInitialAppState() {
        return appState;
    }

    private void restore(byte[] idBytes) {
        // extract pid
    }
}
