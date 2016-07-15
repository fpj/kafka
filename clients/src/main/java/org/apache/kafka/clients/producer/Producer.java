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

import java.io.Closeable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.common.Metric;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.TopicPartition;


/**
 * The interface for the {@link KafkaProducer}
 * @see KafkaProducer
 * @see MockProducer
 */
public interface Producer<K, V> extends Closeable {

    /**
     * A commit id is an identifier that is specified by the application
     * that enables producer to:
     *      - Deduplicate messages sent to a topic partition
     *      - Identify and recover incomplete commits
     *
     * A commit corresponds to a set of messages that need to
     * be produced atomically, all or none.
     *
     * The first time an application starts a producer with a given commit
     * id, the producer needs to set it up so that Kafka can deduplicate
     * and recover commits. The setupCid call only needs to be invoked once
     * for a given commit id. The one exception to this rule is when the
     * commit id expires. Brokers timeout commit ids due to inactivity
     * and once it expires, the commit id is no longer valid unless setupCid
     * is called again. The guarantees of deduplication and atomicity no
     * longer hold across calls to setupCid for the same commit id.
     *
     * @param cb A completion callback for this setup call.
     * @return A future for the result of setting up a commit id.
     */
    public Future<Void> setupCid(CompletionCallback<Void> cb);

    /**
     * Recover the state of a given Producer ID if the pid object
     * is not null. If it is null, then assigns a new identifier.
     *
     * The producer identifier enables the producer instance to
     * recover all necessary state to both deduplicate messages
     * and finish incomplete commits. We can have an incomplete
     * commit in the case the application has crashed and needs
     * to restart.
     *
     * In the case the application is interested in the only-once
     * guarantee and it has pending commits, it must call this
     * method with the appropriate pid before resuming its regular
     * execution.
     *
     * @param cb A callback to notify the application that recovery
     *           has finished.
     * @return A future that eventually returns the result of recovery.
     */
    public Future<Void> recoverCid(CompletionCallback<Void> cb);


    /**
     * Make a commit id invalid and removes any internal metadata
     * associated to this commit id. This call gracefully makes the
     * commit id invalid. If this call is never made for a given
     * commit id, brokers invalidate this commit id once it times
     * out. A commit id times out for a given topic partition once
     * a time out period has elapsed and no message has been produced
     * under that commit id.
     *
     * @param cb A callback to notify the application the result of
     *           closing this commit id
     * @return A future that eventually returns the result of
     */
    public Future<Void> closeCid(CompletionCallback<Void> cb);


    /**
     * Send the given record asynchronously and return a future which will eventually contain the response information.
     * 
     * @param record The record to send
     * @return A future which will eventually contain the response information
     */
    public Future<RecordMetadata> send(ProducerRecord<K, V> record);

    /**
     * Send a record and invoke the given callback when the record has been acknowledged by the server
     */
    public Future<RecordMetadata> send(ProducerRecord<K, V> record, Callback callback);

    /**
     * Start a new set of messages to commit. All calls to send through this producer instance
     * are considered to be part of this commit set. We close the commit set once the application
     * calls endCommit.
     *
     * @param callback Invoked when the initialization of the batch completes
     * @return A future to indicate when the operations is complete.
     */
    public Future<Void> beginCommit(CompletionCallback<Void> callback);

    /**
     * Commit the last set of produced messages.
     *
     * @param callback Invoked when the commit completes
     * @return A future to indicate when the operations completes.
     */
    public Future<Void> endCommit(CompletionCallback<Void> callback);

    /**
     * Aborts the current set of messages to commit. The messages that have been
     * successfully produced won't be delivered to consumers that set the commit
     * mode to COMMITTED.
     *
     * @param callback Invoked when the abort operation completes
     * @return A future to indicate when the operation completes.
     */
    public Future<Void> abortCommit(CompletionCallback<Void> callback);

    /**
     * Flush any accumulated records from the producer. Blocks until all sends are complete.
     */
    public void flush();

    /**
     * Get a list of partitions for the given topic for custom partition assignment. The partition metadata will change
     * over time so this list should not be cached.
     */
    public List<PartitionInfo> partitionsFor(String topic);

    /**
     * Return a map of metrics maintained by the producer
     */
    public Map<MetricName, ? extends Metric> metrics();

    /**
     * Close this producer
     */
    public void close();

    /**
     * Tries to close the producer cleanly within the specified timeout. If the close does not complete within the
     * timeout, fail any pending send requests and force close the producer.
     */
    public void close(long timeout, TimeUnit unit);

}
