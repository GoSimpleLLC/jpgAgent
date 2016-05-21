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

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to parse annotations from strings.
 */
class AnnotationUtil
{
    private static final Pattern annotation_pattern = Pattern.compile("^@([A-Z_]+)=(.+);$", Pattern.MULTILINE);
    private static final String value_regex = "^([0-9]*)+ ([a-z]*)+$";
    /**
     * Parses annotations that match a pattern from an incoming String.
     * Annotations will be in the format of: @JOB_TIMEOUT=42 min;
     * @param string
     * @return
     */
    public static Map<String, String> parseAnnotations(final String string)
    {
        final Map<String, String> annotations = new HashMap<>();
        if (string == null)
        {
            return annotations;
        }

        final Matcher matcher = annotation_pattern.matcher(string);

        while(matcher.find())
        {
            annotations.put(matcher.group(1), matcher.group(2));
        }

        return annotations;
    }

    /**
     * Parse the value and return it.
     *
     * Returns null if unable to parse or cast to the correct type.
     * @param annotation
     * @param value
     * @return
     * @throws IllegalArgumentException
     */
    public static <T> T parseValue(final AnnotationDefinition annotation, final String value, final Class<T> type)
    {
        if (!annotation.getAnnotationValueType().equals(type))
        {
            return null;
        }

        @SuppressWarnings("unchecked")
        final T return_value;

        try
        {
            if (Long.class.equals(type))
            {
                Long long_value;
                if (value.matches(value_regex))
                {
                    final String[] vals = value.split(" ");
                    long_value = Long.valueOf(vals[0]) * AnnotationUtil.getMultiplier(vals[1]);
                }
                else
                {
                    long_value = Long.valueOf(value);
                }

                return_value = (T) long_value;
            }
            else if (Integer.class.equals(type))
            {
                Integer integer_value;
                if (value.matches(value_regex))
                {
                    final String[] vals = value.split(" ");
                    integer_value = Integer.valueOf(vals[0]) * AnnotationUtil.getMultiplier(vals[1]);
                }
                else
                {
                    integer_value = Integer.valueOf(value);
                }

                return_value = (T) integer_value;
            }
            else if (Boolean.class.equals(type))
            {
                return_value = (T) Boolean.valueOf(value);
            }
            else if (String.class.equals(type))
            {
                return_value = (T) value;
            }
            else
            {
                throw new IllegalArgumentException();
            }
        }
        catch (Exception e)
        {
            return null;
        }
        return return_value;
    }

    /**
     * Returns the multiplier for a certain value suffix.
     * @param arg
     * @return
     */
    private static int getMultiplier(final String arg)
    {
        switch (arg)
        {
            case "MS":
            case "ms":
            {
                return 1;
            }
            case "SEC":
            case "S":
            case "sec":
            case "s":
            {
                return 1000;
            }
            case "MIN":
            case "M":
            case "min":
            case "m":
            {
                return 1000 * 60;
            }
            case "HR":
            case "H":
            case "hr":
            case "h":
            {
                return 1000 * 60 * 60;
            }
            default:
            {
                throw new IllegalArgumentException();
            }
        }
    }
}
