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

package com.gosimple.jpgagent.job.step;

/**
 * @author Adam Brusselback.
 */
public enum StepStatus
{
    RUNNING("r"),
    FAIL("f"),
    SUCCEED("s"),
    ABORTED("d"),
    IGNORE("i");

    private final String db_representation;

    StepStatus(final String db_representation)
    {
        this.db_representation = db_representation;
    }

    public static StepStatus convertTo(final String db_string)
    {
        for (final StepStatus step_status : StepStatus.values())
        {
            if (db_string.equals(step_status.db_representation))
            {
                return step_status;
            }
        }
        return null;
    }

    public String getDbRepresentation()
    {
        return db_representation;
    }
}
