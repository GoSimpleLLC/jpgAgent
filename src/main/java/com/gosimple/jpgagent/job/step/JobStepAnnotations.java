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

import com.gosimple.jpgagent.annotation.AnnotationDefinition;

/**
 * @author Adam Brusselback.
 */
public enum JobStepAnnotations implements AnnotationDefinition
{
    RUN_IN_PARALLEL(Boolean.class),
    JOB_STEP_TIMEOUT(Long.class),
    DATABASE_NAME(String.class),
    DATABASE_HOST(String.class),
    DATABASE_LOGIN(String.class),
    DATABASE_PASSWORD(String.class),
    DATABASE_AUTH_QUERY(String.class),
    EMAIL_ON(String.class),
    EMAIL_SUBJECT(String.class),
    EMAIL_BODY(String.class),
    EMAIL_TO(String.class);

    final Class<?> annotation_value_type;

    JobStepAnnotations(final Class<?> annotation_value_type)
    {
        this.annotation_value_type = annotation_value_type;
    }

    @Override
    public Class<?> getAnnotationValueType()
    {
        return this.annotation_value_type;
    }
}
