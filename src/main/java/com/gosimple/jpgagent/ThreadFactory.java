/*
 * Copyright (c) 2016, Adam Brusselback
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.gosimple.jpgagent;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;

public enum ThreadFactory
{
    INSTANCE;

    private final ThreadPoolExecutor generalThreadPool;

    ThreadFactory()
    {

        Executors.newFixedThreadPool(Config.INSTANCE.thread_pool_size);
        generalThreadPool = new CancellableExecutor(
                Config.INSTANCE.thread_pool_size,
                Config.INSTANCE.thread_pool_size,
                300L,
                SECONDS,
                new LinkedBlockingQueue<>(),
                new PriorityThreadFactory("GeneralPool", Thread.NORM_PRIORITY));
    }


    public void executeTask(Runnable r)
    {
        generalThreadPool.execute(r);
    }

    public Future<?> submitTask(Runnable r)
    {
        return generalThreadPool.submit(r);
    }

    public <T> Future<T> submitTask(Callable<T> c)
    {
        return generalThreadPool.submit(c);
    }

    private class CancellableExecutor extends ThreadPoolExecutor
    {
        public CancellableExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue)
        {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        }

        public CancellableExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, java.util.concurrent.ThreadFactory threadFactory)
        {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        }

        public CancellableExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler)
        {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
        }

        public CancellableExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, java.util.concurrent.ThreadFactory threadFactory, RejectedExecutionHandler handler)
        {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        }

        /**
         * Returns a {@code RunnableFuture} for the given runnable and default
         * value.
         *
         * @param runnable the runnable task being wrapped
         * @param value    the default value for the returned future
         * @return a {@code RunnableFuture} which, when run, will run the
         * underlying runnable and which, as a {@code Future}, will yield
         * the given value as its result and provide for cancellation of
         * the underlying task
         * @since 1.6
         */
        @Override
        protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value)
        {
            if (runnable instanceof CancellableRunnable)
            {
                return new FutureTask<T>(runnable, value)
                {
                    @Override
                    public boolean cancel(boolean mayInterruptIfRunning)
                    {
                        boolean return_value = super.cancel(mayInterruptIfRunning);
                        CancellableRunnable.class.cast(runnable).cancelTask();
                        return return_value;
                    }
                };
            }
            else
            {
                return super.newTaskFor(runnable, value);
            }
        }

        /**
         * Returns a {@code RunnableFuture} for the given callable task.
         *
         * @param callable the callable task being wrapped
         * @return a {@code RunnableFuture} which, when run, will call the
         * underlying callable and which, as a {@code Future}, will yield
         * the callable's result as its result and provide for
         * cancellation of the underlying task
         * @since 1.6
         */
        @Override
        protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable)
        {
            if (callable instanceof CancellableCallable)
            {
                return new FutureTask<T>(callable)
                {
                    @Override
                    public boolean cancel(boolean mayInterruptIfRunning)
                    {
                        CancellableCallable.class.cast(callable).cancelTask();
                        return super.cancel(mayInterruptIfRunning);
                    }
                };
            }
            else
            {
                return super.newTaskFor(callable);
            }
        }
    }

    private class PriorityThreadFactory implements java.util.concurrent.ThreadFactory
    {
        private final int prio;
        private final String name;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final ThreadGroup group;

        public PriorityThreadFactory(String name, int prio)
        {
            this.prio = prio;
            this.name = name;
            group = new ThreadGroup(this.name);
        }

        /* (non-Javadoc)
         * @see java.util.concurrent.ThreadFactory#newThread(java.lang.Runnable)
         */
        public Thread newThread(Runnable r)
        {
            Thread t = new Thread(group, r);
            t.setName(name + "-" + threadNumber.getAndIncrement());
            t.setPriority(prio);
            return t;
        }

        public ThreadGroup getGroup()
        {
            return group;
        }
    }
}
