﻿<%@ page import="com.paladin.common.Tools" %>
<%@ page contentType="text/html;charset=UTF-8" %>
<form action="${contextPath}/login" id="l_fm" method="get">
    <input type="hidden" name="r"/>

    <div>
        <div id=gbar>
            <nobr>&nbsp;&nbsp;<a href="${contextPath}/" class=gb1>主页</a>&nbsp;&nbsp;
                <a href="${contextPath}/blog" class=gb1>博文</a>&nbsp;&nbsp;
                <a href="${contextPath}/code" class=gb1>代码</a>&nbsp;&nbsp;
                <a href="${contextPath}/chat" class=gb1>聊天室</a>&nbsp;&nbsp;
                <a href="${contextPath}/commonAPI" class=gb1>常用API</a>&nbsp;&nbsp;
                <c:if test='${user.role == "admin"}'>
                    <a href="${contextPath}/motto" class=gb1>醒世恒言</a>&nbsp;&nbsp;
                </c:if>
                <!--<a href="${contextPath}/game" class=gb1>游戏</a>&nbsp;&nbsp;<a href="#" class=gb1>游戏</a>&nbsp;
            <a href="#" class=gb1>待办事项</a>&nbsp;
            <a href="#" class=gb1>通讯录</a>&nbsp;
            <a href="#" class=gb1>常用工具</a>--> </nobr>
        </div>
        <div id=guser>
            <nobr>

                <c:if test='${empty user}'>
                    <a onclick="toLogin()" href="#" class='gb4'>登录</a>
                </c:if>
                <c:if test='${!(empty user)}'>
                    <a href="${contextPath}/admin" title="${user.email}">${user.nickname}</a>
                    ||
                    <a href="${contextPath}/blog/toAdd">发博文</a>&nbsp;
                    <a href="${contextPath}/code/toAdd">发代码</a>&nbsp;
                    <a href="${contextPath}/motto/toAdd">发恒言</a>
                    ||&nbsp;
                    <!--<img src="${contextPath}/images/doctor.png"/>-->
                    <a href="${contextPath}/login/exit/" class='gb4'>退 出</a>
                </c:if>
                ||&nbsp;
                <%=Tools.getSysTime()%>
                &nbsp;&nbsp;</nobr>
        </div>
        <div class=gbh style='left:0;'></div>
        <div class=gbh style='right:0;'></div>
    </div>
    <script>
        function toLogin() {
            document.getElementById('l_fm').r.value = window.location;
            document.getElementById('l_fm').submit();
        }
    </script>
</form>
