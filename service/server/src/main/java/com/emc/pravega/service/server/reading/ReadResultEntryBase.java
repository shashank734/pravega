/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.emc.pravega.service.server.reading;

import com.emc.pravega.service.contracts.ReadResultEntry;
import com.emc.pravega.service.contracts.ReadResultEntryContents;
import com.emc.pravega.service.contracts.ReadResultEntryType;
import com.google.common.base.Preconditions;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Base implementation of ReadResultEntry.
 */
abstract class ReadResultEntryBase implements ReadResultEntry {
    //region Members

    private final CompletableFuture<ReadResultEntryContents> contents;
    private final ReadResultEntryType type;
    private final int requestedReadLength;
    private long streamSegmentOffset;
    private CompletionConsumer completionCallback;

    //endregion

    //region Constructor

    /**
     * Creates a new instance of the ReadResultEntry class.
     *
     * @param type                The type of this ReadResultEntry.
     * @param streamSegmentOffset The offset in the StreamSegment that this entry starts at.
     * @param requestedReadLength The maximum number of bytes requested for read.
     * @throws IllegalArgumentException If any of the arguments are invalid.
     */
    ReadResultEntryBase(ReadResultEntryType type, long streamSegmentOffset, int requestedReadLength) {
        Preconditions.checkArgument(streamSegmentOffset >= 0, "streamSegmentOffset must be a non-negative number.");
        Preconditions.checkArgument(requestedReadLength > 0, "requestedReadLength must be a positive integer.");

        this.type = type;
        this.streamSegmentOffset = streamSegmentOffset;
        this.requestedReadLength = requestedReadLength;
        this.contents = new CompletableFuture<>();
    }

    //endregion

    //region ReadResultEntry Implementation

    @Override
    public long getStreamSegmentOffset() {
        return this.streamSegmentOffset;
    }

    @Override
    public int getRequestedReadLength() {
        return this.requestedReadLength;
    }

    @Override
    public ReadResultEntryType getType() {
        return this.type;
    }

    @Override
    public final CompletableFuture<ReadResultEntryContents> getContent() {
        return this.contents;
    }

    @Override
    public void requestContent(Duration timeout) {
        // This method intentionally left blank, to be implemented by derived classes that need it.
    }

    //endregion

    /**
     * Adjusts the offset by the given amount.
     *
     * @param delta The amount to adjust by.
     * @throws IllegalArgumentException If the new offset would be negative.
     */
    void adjustOffset(long delta) {
        long newOffset = this.streamSegmentOffset + delta;
        Preconditions.checkArgument(newOffset >= 0, "Given delta would result in a negative offset.");
        this.streamSegmentOffset = newOffset;
    }

    /**
     * Registers a CompletionConsumer that will be invoked when the content is retrieved, just before the Future is completed.
     *
     * @param completionCallback The callback to be invoked.
     */
    void setCompletionCallback(CompletionConsumer completionCallback) {
        this.completionCallback = completionCallback;
        if (completionCallback != null && this.contents.isDone() && !this.contents.isCompletedExceptionally()) {
            completionCallback.accept(this.contents.join().getLength());
        }
    }

    /**
     * Completes the Future of this ReadResultEntry by setting the given content.
     *
     * @param readResultEntryContents The content to set.
     */
    void complete(ReadResultEntryContents readResultEntryContents) {
        Preconditions.checkState(!this.contents.isDone(), "ReadResultEntry has already had its result set.");
        CompletionConsumer callback = this.completionCallback;
        if (callback != null) {
            callback.accept(readResultEntryContents.getLength());
        }

        this.contents.complete(readResultEntryContents);
    }

    /**
     * Fails the Future of this ReadResultEntry with the given exception.
     *
     * @param exception The exception to set.
     */
    void fail(Throwable exception) {
        Preconditions.checkState(!this.contents.isDone(), "ReadResultEntry has already had its result set.");
        this.contents.completeExceptionally(exception);
    }

    @Override
    public String toString() {
        CompletableFuture<ReadResultEntryContents> contentFuture = this.contents;
        return String.format("%s: Offset = %d, RequestedLength = %d, HasData = %s, Error = %s, Cancelled = %s",
                this.getClass().getSimpleName(),
                getStreamSegmentOffset(),
                getRequestedReadLength(),
                contentFuture.isDone() && !contentFuture.isCompletedExceptionally() && !contentFuture.isCancelled(),
                contentFuture.isCompletedExceptionally(),
                contentFuture.isCancelled());
    }

    //endregion

    //region CompletionConsumer

    @FunctionalInterface
    interface CompletionConsumer extends Consumer<Integer> {
    }

    //endregion
}