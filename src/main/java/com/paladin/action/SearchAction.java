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
package com.paladin.action;

import com.google.common.base.Strings;
import com.paladin.bean.Blog;
import com.paladin.bean.Motto;
import com.paladin.common.Constants;
import com.paladin.common.Tools;
import com.paladin.mvc.RequestContext;
import com.paladin.sys.db.QueryHelper;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Search Action
 *
 * @author Erhu
 * @since Mar 12th, 2011
 */
public class SearchAction extends BaseAction {

    public void index(final RequestContext _reqCtxt) {
        redirect(_reqCtxt, "/blog");
    }

    /**
     * Search blog and code and motto
     */
    public void bcm(final RequestContext _reqCtxt) throws UnsupportedEncodingException {
        HttpServletRequest request = _reqCtxt.request();
        String q = request.getParameter("q");
        if (!Strings.isNullOrEmpty(q)) {
            q = Tools.ISO885912UTF8(q).trim();
            log.info("q = " + q);
            request.setAttribute("q", q);
            b(request, q, "blog");// 查找博文
            b(request, q, "code");// 查找代码
            m(request, q);// 查找箴言
            String t = request.getParameter("t");
            // 控制搜索结果页面的样式
            if (!Strings.isNullOrEmpty(t)) {
                if ("code".equals(t)) {// t == code时，搜索结果的翻页直接定位到代码tab
                    request.setAttribute("class_blog", "class=\'u_tab\'");
                    request.setAttribute("class_code", "class=\'u_tab_hover\'");
                    request.setAttribute("class_motto", "class=\'u_tab\'");

                    request.setAttribute("style_blog", "style='display:none;'");
                    request.setAttribute("style_motto", "style='display:none;'");
                } else if ("motto".equals(t)) {
                    request.setAttribute("class_blog", "class=\'u_tab\'");
                    request.setAttribute("class_code", "class=\'u_tab\'");
                    request.setAttribute("class_motto", "class=\'u_tab_hover\'");

                    request.setAttribute("style_blog", "style='display:none;'");
                    request.setAttribute("style_code", "style='display:none;'");
                }
            } else {
                request.setAttribute("class_blog", "class=\'u_tab_hover\'");
                request.setAttribute("class_code", "class=\'u_tab\'");
                request.setAttribute("class_motto", "class=\'u_tab\'");

                request.setAttribute("style_code", "style='display:none;'");
                request.setAttribute("style_motto", "style='display:none;'");
            }
        }
        forward(_reqCtxt, "/html/search/search_bc.jsp");
    }

    /**
     * Search blog and code
     */
    public void b(final HttpServletRequest request, String q, String _table) throws UnsupportedEncodingException {
        List<Blog> blog_list = new ArrayList<Blog>();
        int size = 0;
        for (String qq : Tools.q2qArr(q)) {
            String sql = "SELECT * FROM " + _table.toUpperCase()
                    + " WHERE TITLE LIKE ? OR CONTENT LIKE ? OR TAG LIKE ? ORDER BY HITS DESC";
            for (Blog b : QueryHelper.query(Blog.class, sql, new Object[]{qq, qq, qq})) {
                if (!blog_list.contains(b)) {
                    String title = b.getTitle().trim();
                    String tag = b.getTag().trim();
                    String content = b.getContent().trim();
                    if (title.indexOf(q) >= 0)
                        b.setTitle(title.replaceAll(q, Tools.standOutStr(q)));
                    if (tag.indexOf(q) >= 0)
                        b.setTag(tag.replaceAll(q, Tools.standOutStr(q)));

                    content = content.replaceAll("<[^>]*>", "");
                    int first_index = content.indexOf(q);
                    int last_index = content.lastIndexOf(q);
                    if (first_index >= 0 && content.length() >= last_index + q.length() + 20)
                        content = content.substring(first_index, last_index + q.length() + 20);
                    if (content.length() > Constants.LENGTH_OF_SEARCH_CONTENT)
                        content = content.substring(0, Constants.LENGTH_OF_SEARCH_CONTENT);
                    b.setContent(content.replace(q, Tools.standOutStr(q)));
                    blog_list.add(b);
                }
            }
        }
        size = blog_list.size();
        {//分页
            super.doPage(request, blog_list.size(), Constants.NUM_PER_PAGE_SEARCH, "_" + _table);
            int begin = (page_NO - 1) * Constants.NUM_PER_PAGE_SEARCH;
            begin = begin < 0 ? 0 : begin;
            int end = page_NO * Constants.NUM_PER_PAGE_SEARCH > blog_list.size() ?
                    blog_list.size() : page_NO * Constants.NUM_PER_PAGE_SEARCH;
            blog_list = blog_list.subList(begin, end);
        }
        log.info("get " + _table.toLowerCase() + ":" + size);
        request.setAttribute(_table + "_list", blog_list);
    }

    /**
     * Search motto
     */
    public void m(final HttpServletRequest request, String _q) throws UnsupportedEncodingException {
        List<Motto> motto_list = new ArrayList<Motto>();
        int size = 0;
        for (String qq : Tools.q2qArr(_q)) {
            String sql = "SELECT * FROM MOTTO WHERE CONTENT LIKE ? OR TAG LIKE ?";
            for (Motto m : QueryHelper.query(Motto.class, sql, new Object[]{qq, qq})) {
                if (!motto_list.contains(m)) {
                    String tag = m.getTag().trim();
                    String content = m.getContent().trim();
                    if (content.indexOf(_q) >= 0)
                        m.setContent(content.replaceAll(_q, Tools.standOutStr(_q)));
                    if (tag.indexOf(_q) >= 0)
                        m.setTag(tag.replaceAll(_q, Tools.standOutStr(_q)));
                    motto_list.add(m);
                }
            }
        }
        size = motto_list.size();
        {//分页
            super.doPage(request, motto_list.size(), Constants.NUM_PER_PAGE_SEARCH, "_motto");
            int begin = (page_NO - 1) * Constants.NUM_PER_PAGE_SEARCH;
            begin = begin < 0 ? 0 : begin;
            int end = page_NO * Constants.NUM_PER_PAGE_SEARCH > motto_list.size() ?
                    motto_list.size() : page_NO * Constants.NUM_PER_PAGE_SEARCH;
            motto_list = motto_list.subList(begin, end);
        }
        log.info("get motto:" + size);
        request.setAttribute("motto_list", motto_list);
    }
}
