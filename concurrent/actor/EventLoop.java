/*
 * Copyright (C) 2020 Grakn Labs
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package grakn.common.concurrent.actor;

import grakn.common.collection.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static grakn.common.collection.Collections.pair;

public class EventLoop {
    private static final Logger LOG = LoggerFactory.getLogger(EventLoop.class);
    private enum State { READY, RUNNING, STOPPED }

    private final Consumer<Exception> errorHandler = e -> { LOG.error("An unexpected error has occurred.", e); };
    private final TransferQueue<Pair<Runnable, Consumer<Exception>>> jobs = new LinkedTransferQueue<>();
    private final ScheduledJobQueue<Pair<Runnable, Consumer<Exception>>> scheduledJobs = new ScheduledJobQueue<>();
    private final Thread thread;
    private State state;

    public EventLoop(ThreadFactory factory) {
        thread = factory.newThread(this::loop);
        state = State.READY;
        thread.start();
    }

    public void submit(Runnable job, Consumer<Exception> onError) {
        jobs.offer(pair(job, onError));
    }

    public ScheduledJobQueue<Pair<Runnable, Consumer<Exception>>>.Entry submit(long scheduleMs, Runnable job, Consumer<Exception> errorHandler) {
        final AtomicReference<ScheduledJobQueue<Pair<Runnable, Consumer<Exception>>>.Entry> scheduledJob = new AtomicReference<>();
        submit(() -> scheduledJob.set(scheduledJobs.offer(scheduleMs, pair(job, errorHandler))), errorHandler);
        return scheduledJob.get();
    }

    public void await() throws InterruptedException {
        thread.join();
    }

    public void stop() throws InterruptedException {
        submit(() -> state = State.STOPPED, errorHandler);
        await();
    }

    private void loop() {
        LOG.debug("Started");
        state = State.RUNNING;

        while (state == State.RUNNING) {
            long currentTime = EventLoopClock.time();
            Pair<Runnable, Consumer<Exception>> scheduledJob = scheduledJobs.poll(currentTime);
            if (scheduledJob != null) {
                run(scheduledJob);
            } else {
                try {
                    Pair<Runnable, Consumer<Exception>> job = jobs.poll(scheduledJobs.timeToNext(currentTime), TimeUnit.MILLISECONDS);
                    if (job != null) {
                        run(job);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        state = State.STOPPED;
        LOG.debug("stopped");
    }

    private void run(Pair<Runnable, Consumer<Exception>> job) {
        try {
            job.first().run();
        } catch (Exception e) {
            job.second().accept(e);
        }
    }
}