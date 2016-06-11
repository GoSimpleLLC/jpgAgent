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

import com.gosimple.jpgagent.annotation.AnnotationUtil;
import com.gosimple.jpgagent.job.JobAnnotations;
import com.gosimple.jpgagent.job.step.JobStepAnnotations;
import org.junit.Assert;
import org.junit.Test;

import java.util.Map;

public class AnnotationUtilTest
{

    @Test
    public void testParseAnnotations() throws Exception
    {
        final String test_1 = "@JOB_TIMEOUT=42 min;";
        final String test_2 = "@JOB_TIMEOUT=42min;";
        final String test_3 = "@JOB_TIMEOUT=42 m/in;";
        final String test_4 = "@JOB_TIMEOUT=4 2 min;";
        final String test_5 = "@RUN_IN_PARALLEL=true;";
        final String test_6 = "@RUN_IN_PARALLEL=false;";
        final String test_7 = "@RUN_IN_PARALLEL= false;";
        final String test_8 = "@RUN_IN_PARALLEL=bob24;";
        final String test_9 = "@RUN_IN_PARALLEL= bob;";
        final String test_10 = "@RUN_NI_PARALLEL=true;";
        final String test_11 = "@run_in_parallel=true;";
        final String test_12 = "@Run_in_parallel=true;";

        Map<String, String> test_map;

        test_map = AnnotationUtil.parseAnnotations(test_1);
        Assert.assertTrue(test_map.containsKey("JOB_TIMEOUT"));
        Assert.assertEquals(test_map.get("JOB_TIMEOUT"), "42 min");
        test_map = AnnotationUtil.parseAnnotations(test_2);
        Assert.assertTrue(test_map.containsKey("JOB_TIMEOUT"));
        Assert.assertEquals(test_map.get("JOB_TIMEOUT"), "42min");
        test_map = AnnotationUtil.parseAnnotations(test_3);
        Assert.assertTrue(test_map.containsKey("JOB_TIMEOUT"));
        Assert.assertEquals(test_map.get("JOB_TIMEOUT"), "42 m/in");
        test_map = AnnotationUtil.parseAnnotations(test_4);
        Assert.assertTrue(test_map.containsKey("JOB_TIMEOUT"));
        Assert.assertEquals(test_map.get("JOB_TIMEOUT"), "4 2 min");
        test_map = AnnotationUtil.parseAnnotations(test_5);
        Assert.assertTrue(test_map.containsKey("RUN_IN_PARALLEL"));
        Assert.assertEquals(test_map.get("RUN_IN_PARALLEL"), "true");
        test_map = AnnotationUtil.parseAnnotations(test_6);
        Assert.assertTrue(test_map.containsKey("RUN_IN_PARALLEL"));
        Assert.assertEquals(test_map.get("RUN_IN_PARALLEL"), "false");
        test_map = AnnotationUtil.parseAnnotations(test_7);
        Assert.assertTrue(test_map.containsKey("RUN_IN_PARALLEL"));
        Assert.assertEquals(test_map.get("RUN_IN_PARALLEL"), " false");
        test_map = AnnotationUtil.parseAnnotations(test_8);
        Assert.assertTrue(test_map.containsKey("RUN_IN_PARALLEL"));
        Assert.assertEquals(test_map.get("RUN_IN_PARALLEL"), "bob24");
        test_map = AnnotationUtil.parseAnnotations(test_9);
        Assert.assertTrue(test_map.containsKey("RUN_IN_PARALLEL"));
        Assert.assertEquals(test_map.get("RUN_IN_PARALLEL"), " bob");
        test_map = AnnotationUtil.parseAnnotations(test_10);
        Assert.assertTrue(test_map.containsKey("RUN_NI_PARALLEL"));
        test_map = AnnotationUtil.parseAnnotations(test_11);
        Assert.assertTrue(test_map.isEmpty());
        test_map = AnnotationUtil.parseAnnotations(test_12);
        Assert.assertTrue(test_map.isEmpty());
    }

    @Test
    public void testParseValue() throws Exception
    {
        final String test_1 = "42 min";
        final String test_2 = "42min";
        final String test_3 = "42 m/in";
        final String test_4 = "4 2 min";
        final String test_5 = "true";
        final String test_6 = "false";
        final String test_7 = " false";
        final String test_8 = "bob24";
        final String test_9 = " bob";

        Assert.assertEquals(AnnotationUtil.parseValue(JobAnnotations.JOB_TIMEOUT, test_1, Long.class), new Long(42 * 60 * 1000));
        Assert.assertNull(AnnotationUtil.parseValue(JobAnnotations.JOB_TIMEOUT, test_1, Double.class));
        Assert.assertNull(AnnotationUtil.parseValue(JobAnnotations.JOB_TIMEOUT, test_2, Long.class));
        Assert.assertNull(AnnotationUtil.parseValue(JobAnnotations.JOB_TIMEOUT, test_3, Long.class));
        Assert.assertNull(AnnotationUtil.parseValue(JobAnnotations.JOB_TIMEOUT, test_4, Long.class));
        Assert.assertEquals(AnnotationUtil.parseValue(JobStepAnnotations.RUN_IN_PARALLEL, test_5, Boolean.class), true);
        Assert.assertEquals(AnnotationUtil.parseValue(JobStepAnnotations.RUN_IN_PARALLEL, test_6, Boolean.class), false);
        Assert.assertEquals(AnnotationUtil.parseValue(JobStepAnnotations.RUN_IN_PARALLEL, test_7, Boolean.class), false);
        Assert.assertEquals(AnnotationUtil.parseValue(JobStepAnnotations.RUN_IN_PARALLEL, test_8, Boolean.class), false);
        Assert.assertEquals(AnnotationUtil.parseValue(JobStepAnnotations.RUN_IN_PARALLEL, test_9, Boolean.class), false);

    }
}