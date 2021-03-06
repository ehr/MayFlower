/**
 * Copyright (C) 2011 Erhu Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.paladin.common;

import com.google.common.base.Strings;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.highlight.*;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.IOException;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.*;

/**
 * 常用的方法都放在这里:-)
 * Life is good!
 */
public class Tools {

    /**
     * 压缩字符串中的空白字符
     *
     * @param str 单词间有多余空格的字符串
     * @return 返回单词单没有多余空格的字符串
     */
    public static String compressBlank(String str) {
        str = str.trim();
        if (Strings.isNullOrEmpty(str))
            return "";
        StringBuilder str_bu = new StringBuilder();
        char[] str_arr = str.toCharArray();
        for (int i = 0; i < str_arr.length; i++) {
            if (!isBlank(str_arr[i]))
                str_bu.append(str_arr[i]);
            else if (isBlank(str_arr[i]) && i + 1 < str_arr.length && !isBlank(str_arr[i + 1]))
                str_bu.append((char) 32);
        }
        return str_bu.toString();
    }

    /**
     * 判断某字符是否是空白字符(其实，空白字符还有许多，自己骗自己一下吧！)
     */
    public static boolean isBlank(char c) {
        return (int) c == 9 || (int) c == 32;
    }

    /**
     * Check tag to make sure it is right
     *
     * @param _tags 标签
     * @return 正确的 标签
     */
    public static String checkTag(String _tags) {
        if (Strings.isNullOrEmpty(_tags))
            return "";
        // 替换 全角 逗号 分隔符
        _tags = _tags.replace("，", ",");
        // 删除 重复的 tag
        List<String> tag_list = new ArrayList<String>();
        for (String tag : _tags.split(",")) {
            String _tag = Tools.compressBlank(tag);// 压缩 标签 中的 空格
            if (!Strings.isNullOrEmpty(_tag) && !tag_list.contains(_tag))
                tag_list.add(_tag);
        }
        String tag = Arrays.toString(tag_list.toArray()).replace(", ", ",");
        return tag.substring(1, tag.length() - 1);
    }

    /**
     * U know this method by it's name :-)
     *
     * @param _str just a str
     * @return UTF-8 str
     */
    public static String ISO885912UTF8(String _str) {
        try {
            return new String(_str.getBytes("ISO-8859-1"), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * Just swap
     *
     * @param _arr array
     * @param i    the first index
     * @param j    the second index
     */
    public static void swap(Object[] _arr, int i, int j) {
        Object t = _arr[i];
        _arr[i] = _arr[j];
        _arr[j] = t;
    }

    /**
     * null to String
     *
     * @param obj just object, shit!
     * @return string
     */
    public static String null2String(Object obj) {
        return obj == null ? "" : obj.toString().trim();
    }

    /**
     * 快速 排序 tag_Desc
     * 根据 MAP 中 TAG 的 出现 次数，对 数组 进行 排序
     *
     * @param _map   KEY 对应 TAG 出现 的 次数
     * @param _arr   保存 所有 TAG
     * @param _start start index
     * @param _end   end index
     */
    public static void quickSort(Map<String, Integer> _map, String[] _arr, int _start, int _end) {
        if (_start < _end) {
            int part = _map.get(_arr[_start]);
            int i = _start;
            int j = _end + 1;
            while (true) {
                while (i < _end && _map.get(_arr[++i]) >= part) {
                }
                while (j > _start && _map.get(_arr[--j]) <= part) {
                }
                if (i < j)
                    Tools.swap(_arr, i, j);
                else
                    break;
            }
            Tools.swap(_arr, _start, j);
            quickSort(_map, _arr, _start, j - 1);
            quickSort(_map, _arr, j + 1, _end);
        }
    }

    public static String standOutStr(String _str) {
        return Constants.HIGHLIGHT_STYLE + _str + "</span>";
    }

    public static String[] q2qArr(String _q) {
        _q = Tools.compressBlank(_q.replaceAll("<[^>]*>", ""));
        String[] q_arr = _q.split(" ");
        for (int i = 0; i < q_arr.length; i++)
            q_arr[i] = "%".concat(q_arr[i]).concat("%");
        return q_arr;
    }

    /**
     * 在[_from, _to] 区间 取 一 随机 整数
     *
     * @param _from 起始值
     * @param _to   结束值
     * @return 随机数
     */
    public static long random(long _from, long _to) {
        if (_from > _to)
            return random(_to, _from);
        else
            return _from + (int) (Math.random() * (_to - _from + 1));
    }

    /**
     * U will know this function from her name:-)
     *
     * @return sysTime
     */
    public static String getSysTime() {
        GregorianCalendar lgc = new GregorianCalendar();
        String hour = String.valueOf(lgc.get(Calendar.HOUR_OF_DAY));
        if (hour.length() == 1)
            hour = "0" + hour;
        String minute = String.valueOf(lgc.get(Calendar.MINUTE));
        if (minute.length() == 1)
            minute = "0" + minute;
        //String second = String.valueOf(lgc.get(Calendar.SECOND));
        // if (second.length() == 1)
        //    second = "0" + second;
        //String millisecond = String.valueOf(lgc.get(Calendar.MILLISECOND));

        return hour + ":" + minute;// + second + "." + millisecond + "秒";
    }

    /**
     * 获取时间差（秒）
     *
     * @param gc1 时间点1
     * @param gc2 时间点2
     * @return life is good:-)
     */
    public static final double getSecondsBetweenTwoDate(GregorianCalendar gc1, GregorianCalendar gc2) {
        long milliSeconds = Math.abs(gc1.getTimeInMillis() - gc2.getTimeInMillis());
        return milliSeconds / 1000.0;
    }

    /**
     * 高亮 显示 搜索 关键字
     *
     * @param _query
     * @param _field
     * @param _content
     * @return
     */
    public static String highlight(final Query _query, final String _field, final String _content) {
        // 高亮
        Scorer scorer = new QueryScorer(_query);
        SimpleHTMLFormatter formatter = new SimpleHTMLFormatter(Constants.HIGHLIGHT_STYLE, "</span>");
        Highlighter hl = new Highlighter(formatter, scorer);
        TokenStream tokens = new IKAnalyzer().tokenStream(_field, new StringReader(_content));
        try {
            return hl.getBestFragment(tokens, _content);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InvalidTokenOffsetsException e) {
            e.printStackTrace();
        }
        return null;
    }
}
