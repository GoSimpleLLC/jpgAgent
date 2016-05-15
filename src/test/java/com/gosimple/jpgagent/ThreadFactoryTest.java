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

import org.junit.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class ThreadFactoryTest
{

    @Test
    public void testExecuteTask() throws Exception
    {
        Runnable test_1 = new Runnable()
        {
            @Override
            public void run()
            {
                System.out.println("I ran.");
            }
        };
        ThreadFactory.INSTANCE.executeTask(test_1);

        AtomicInteger atomicInteger = new AtomicInteger(0);
        for(int i = 0; i < 100000; i++)
        {
            ThreadFactory.INSTANCE.executeTask(new Runnable()
            {
                @Override
                public void run()
                {
                    atomicInteger.incrementAndGet();
                }
            });
        }

        while (atomicInteger.intValue() < 100000)
        {
            Thread.sleep(10);
        }
    }

    @Test
    public void testSubmitTaskCallable() throws Exception
    {
        Callable<Integer> test = new Callable<Integer>()
        {
            @Override
            public Integer call()
            {
                return 42;
            }
        };

        Future<Integer> future = ThreadFactory.INSTANCE.submitTask(test);

        assertEquals(future.get(), new Integer(42));
    }

    @Test
    public void testSubmitTaskRunnable() throws Exception
    {
        Runnable test_1 = new Runnable()
        {
            @Override
            public void run()
            {
                System.out.println("I ran.");
            }
        };
        Future<?> future = ThreadFactory.INSTANCE.submitTask(test_1);
        future.get();
    }
}