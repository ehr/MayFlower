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

import com.paladin.mvc.RequestContext;

import java.io.File;

/**
 * 常量
 *
 * @author Erhu
 * @since begin
 */
public class Constants {

    /**
     * 每页显示文章条数
     */
    public static final int NUM_PER_PAGE = 46;

    /**
     * 每页 显示 的 箴言 条数
     */
    public static final int NUM_PER_PAGE_MOTTO = 20;

    /**
     * 每页显示的搜索结果条数
     */
    public static final int NUM_PER_PAGE_SEARCH = 10;

    /**
     * 搜索结果中的文章内容只显示500字
     */
    public static final int LENGTH_OF_SEARCH_CONTENT = 400;

    /**
     * 服务器启动时随机搜索的箴言的条数
     */
    public static final int NUM_RANDOM_MOTTO = 7;

    /**
     * 更新箴言列表的时间间隔(分钟)
     */
    public static final int MINUTE_UPDATE_MOTTO = 1;

    /**
     * 字段内容间的分隔符
     */
    public static final String LUCENE_FIELD_SEP = "!&%@~~@%&!";

    /**
     * 高亮 样式
     */
    public static final String HIGHLIGHT_STYLE = "<span style='background-color:#ff0;color:#006699'>";

    /**
     * lucene 索引 存储 位置
     */
    public static final String LUCENE_INDEX_ROOT = RequestContext.root() + "luceneIndex" + File.separatorChar;
}
