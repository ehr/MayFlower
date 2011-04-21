package com.paladin.common;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.base.Strings;

public class Tools {

    /**
     * 压缩字符串中的空白字符
     *
     * @param str
     * @return
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
     * 判断某字符是否是空白字符
     *
     * @param c
     * @return
     */
    public static boolean isBlank(char c) {
        return (int) c == 9 || (int) c == 32;
    }

    /**
     * check tag
     */
    public static String checkTag(String _tags) {
        if (Strings.isNullOrEmpty(_tags))
            return "";
        // 替换全角逗号分隔符
        _tags = _tags.replace("，", ",");
        // 删除重复的tag
        List<String> tag_list = new ArrayList<String>();
        for (String tag : _tags.split(",")) {
            String _tag = Tools.compressBlank(tag);// 压缩标签中的空格
            if (!Strings.isNullOrEmpty(_tag) && !tag_list.contains(_tag))
                tag_list.add(_tag);
        }
        String tag = Arrays.toString((Object[]) tag_list.toArray());
        return tag.substring(1, tag.length() - 1);
    }

    public static String ISO885912UTF8(String _str) {
        try {
            return new String(_str.getBytes("ISO-8859-1"), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static void swap(Object[] _arr, int i, int j) {
        Object t = _arr[i];
        _arr[i] = _arr[j];
        _arr[j] = t;
    }

    public static String null2String(Object obj) {
        return obj == null ? "" : obj.toString().trim();
    }

    /**
     * 快速排序tag
     */
    public static void quickSort(Map<String, Integer> _map, String[] _arr, int _start, int _end) {
        if (_start < _end) {
            int part = _map.get(_arr[_start]);
            int i = _start;
            int j = _end + 1;
            while (true) {
                while (i < _end && _map.get(_arr[++i]) >= part) ;
                while (j > _start && _map.get(_arr[--j]) <= part) ;
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

    public static void main(String[] args) {
        String str = "  as b ";
        System.out.println(str.trim());
    }
}
