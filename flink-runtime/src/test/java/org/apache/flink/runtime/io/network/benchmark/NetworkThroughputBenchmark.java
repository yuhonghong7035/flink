/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.io.network.benchmark;

import org.apache.flink.types.LongValue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Network throughput benchmarks executed by the external
 * <a href="https://github.com/dataArtisans/flink-benchmarks">flink-benchmarks</a> project.
 */
public class NetworkThroughputBenchmark {
	private static final long RECEIVER_TIMEOUT = 30_000;

	private NetworkBenchmarkEnvironment<LongValue> environment;
	private ReceiverThread receiver;
	private LongRecordWriterThread[] writerThreads;

	/**
	 * Executes the throughput benchmark with the given number of records.
	 *
	 * @param records
	 * 		records to pass through the network stack
	 */
	public void executeBenchmark(long records) throws Exception {
		final LongValue value = new LongValue();
		value.setValue(0);

		long lastRecord = records / writerThreads.length;
		CompletableFuture<?> recordsReceived = receiver.setExpectedRecord(lastRecord);

		for (LongRecordWriterThread writerThread : writerThreads) {
			writerThread.setRecordsToSend(lastRecord);
		}

		recordsReceived.get(RECEIVER_TIMEOUT, TimeUnit.MILLISECONDS);
	}

	/**
	 * Initializes the throughput benchmark with the given parameters.
	 *
	 * @param recordWriters
	 * 		number of senders, i.e.
	 * 		{@link org.apache.flink.runtime.io.network.api.writer.RecordWriter} instances
	 * @param channels
	 * 		number of outgoing channels / receivers
	 */
	public void setUp(int recordWriters, int channels) throws Exception {
		environment = new NetworkBenchmarkEnvironment<>();
		environment.setUp(recordWriters, channels);
		receiver = environment.createReceiver();
		writerThreads = new LongRecordWriterThread[recordWriters];
		for (int writer = 0; writer < recordWriters; writer++) {
			writerThreads[writer] = new LongRecordWriterThread(environment.createRecordWriter(writer));
			writerThreads[writer].start();
		}
	}

	/**
	 * Shuts down a benchmark previously set up via {@link #setUp}.
	 *
	 * <p>This will wait for all senders to finish but timeout with an exception after 5 seconds.
	 */
	public void tearDown() throws Exception {
		for (LongRecordWriterThread writerThread : writerThreads) {
			writerThread.shutdown();
			writerThread.sync(5000);
		}
		environment.tearDown();
		receiver.shutdown();
	}
}
