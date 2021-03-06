/**
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
package com.paladin.mvc;

import com.google.common.base.Strings;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * 业务处理方法入口，URI的映射逻辑： /action/xxxxxx/xxxx ->
 * com.dlog4j.action.XxxxxxAction.xxxx(req,res)
 * <p/>
 * 林花谢了春红，
 * 太匆匆，
 * 无奈朝来寒雨晚来风。
 * <p/>
 * 胭脂泪，
 * 相留醉，
 * 几时重，
 * 自是人生长恨水长东。
 *
 * @author Winter Lau (http://my.oschina.net/javayou)<br>
 * @modify Erhu
 */
public final class ActionServlet extends HttpServlet {

    private static final long serialVersionUID = -694945602274634378L;
    private final static String GOTO_PAGE = "index.jsp";
    private static final Log log = LogFactory.getLog(ActionServlet.class);
    private final static String UTF_8 = "utf-8";
    private List<String> actionPackages;

    private final static HashMap<String, Object> actions = new HashMap<String, Object>();
    private final static HashMap<String, Method> methods = new HashMap<String, Method>();

    /**
     * init actions which declared in web.xml
     */
    @Override
    public void init() throws ServletException {
        String packages = getInitParameter("packages");
        actionPackages = Arrays.asList(StringUtils.split(packages, ','));
        String initial_actions = getInitParameter("initial_actions");
        for (String action : StringUtils.split(initial_actions, ','))
            try {
                loadAction(action);
            } catch (Exception e) {
                log.error("Failed to initial action : " + action, e);
            }
    }

    @Override
    public void destroy() {
        for (Object action : actions.values()) {
            try {
                Method dm = action.getClass().getMethod("destroy");
                if (dm != null) {
                    dm.invoke(action);
                    log.info(action.getClass().getSimpleName() + " destroy ~~~~~~~~~");
                }
            } catch (Exception e) {
                //log.error("Unable to destroy action: " + action.getClass().getSimpleName(), e);
            }
        }
        super.destroy();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {
        RequestContext.begin(getServletContext(), req, resp);
        process(RequestContext.get(), false);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException,
            IOException {
        RequestContext.begin(getServletContext(), req, resp);
        process(RequestContext.get(), true);
    }

    /**
     * 执行Action方法并进行返回处理、异常处理
     *
     * @param _reqCtxt
     * @param _is_post
     */
    private void process(RequestContext _reqCtxt, boolean _is_post) {
        try {
            _reqCtxt.request().setCharacterEncoding(UTF_8);
            _reqCtxt.response().setContentType("text/html;charset=utf-8");
            if (!doProcess(_reqCtxt, _is_post)) {// failed to doProcess
                String gp = _reqCtxt.param(GOTO_PAGE);
                if (!Strings.isNullOrEmpty(gp))
                    _reqCtxt.redirect(gp);
            }
        } catch (Exception e) {
            log.info("Exception in action process.", e);
            e.printStackTrace();
        }
    }

    /**
     * 业务逻辑处理
     *
     * @param _reqCtxt
     * @param is_post
     * @return
     */
    private boolean doProcess(RequestContext _reqCtxt, boolean is_post) {
        try {
            String url = _decodeURL(_reqCtxt.uri(), "UTF-8");
            // split uri
            String[] parts = StringUtils.split(url, '/');
            if (parts.length < 1) {
                _reqCtxt.not_found();
                return false;
            }
            // load action
            Object action = this.loadAction(parts[0]);
            if (action == null) {
                _reqCtxt.not_found();
                return false;
            }
            String method_name = (parts.length > 1) ? parts[1] : "index";// 如果url中未传递方法名，则使用index
            Method method_of_action = this.getActionMethod(action, method_name);

            // 指定的 方法 不存在 时，转到 主页
            if (method_of_action == null) {
                method_of_action = this.getActionMethod(action, "index");
            }
            
            // 调用Action方法之准备参数
            int arg_c = method_of_action.getParameterTypes().length;// 参数个数
            switch (arg_c) {
                case 0: // login()
                    method_of_action.invoke(action);
                    break;
                case 1:// login(RequestContext)
                    method_of_action.invoke(action, _reqCtxt);
                    break;
                case 2:// read(RequestContext, id)
                    boolean isLong = method_of_action.getParameterTypes()[1].equals(long.class);
                    // 处理 对象时，未指定 对象 id, 转到 主页
                    if (parts.length < 3) {
                        method_of_action = this.getActionMethod(action, "index");
                        method_of_action.invoke(action, _reqCtxt);
                    } else
                        method_of_action.invoke(action, _reqCtxt, isLong ? NumberUtils.toLong(parts[2], -1L) : parts[2]);
                    break;
                case 3:// search(RequestContext, id, q)
                    // method_of_action.invoke(action, _reqCtxt,
                    // NumberUtils.toLong(parts[2], -1L), parts[3]);
                    break;
                default:
                    _reqCtxt.not_found();
                    return false;
            }
        } catch (Exception
                e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 加载Action类
     *
     * @param _actionName
     * @return
     */

    private Object loadAction(final String _actionName) {
        try {
            Object action = actions.get(_actionName);
            if (action == null)
                for (String pkg : actionPackages) {// 循环多个package,来查找Action
                    String cls = pkg + '.' + StringUtils.capitalize(_actionName/* 首字母大写 */) + "Action";
                    action = loadActionOfFullname(_actionName, cls);
                    if (action != null) break;
                }
            return action;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Object loadActionOfFullname(String _actionName, String _className) throws IllegalAccessException,
            InstantiationException {
        Object action = null;
        try {
            action = Class.forName(_className).newInstance();
            try {
                // 如果Action有init(ServletContext _parm)方法，则调用之。
                Method action_init_method = action.getClass().getMethod("init", ServletContext.class);
                action_init_method.invoke(action, getServletContext());
            } catch (NoSuchMethodException e) {
            } catch (InvocationTargetException excp) {
                excp.printStackTrace();
            }
            if (!actions.containsKey(_actionName)) {
                synchronized (actions) {
                    actions.put(_actionName, action);
                }
            }
        } catch (ClassNotFoundException excp) {
            excp.printStackTrace();
        }
        return action;
    }

    /**
     * 获取名为{method}的方法
     *
     * @param _action
     * @param _method
     * @return
     */
    private Method getActionMethod(final Object _action, final String _method) {
        String key = _action.getClass().getSimpleName() + '.' + _method;
        Method m = methods.get(key);
        if (m != null)
            return m;
        for (Method method : _action.getClass().getMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && method.getName().equals(_method)) {
                synchronized (methods) {
                    methods.put(key, method);
                }
                return method;
            }
        }
        return null;
    }

    /**
     * URL解码
     *
     * @param url
     * @param charset
     * @return
     */
    private static String _decodeURL(String url, String charset) {
        if (StringUtils.isEmpty(url))
            return "";
        try {
            return URLDecoder.decode(url, charset);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return url;
    }
}
