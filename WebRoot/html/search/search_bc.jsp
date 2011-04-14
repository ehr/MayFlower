﻿<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core_rt" %>
<html>
<head>
<title>搜索 - ${q}</title>
<script type="text/javascript">
		$(function(){
			$('#tabs').tabs();
		});
</script>
</head>
<body>
<c:choose>
    <c:when test='${!(empty blog_list) || !(empty code_list)}'>
    	<div id="tabs">
			<ul>
				<li><a href="#tabs-1">博 文</a></li>
				<li><a href="#tabs-2">代 码</a></li>
			</ul>
			<div id="tabs-1">
            	<table width="100%"> 
                    <c:forEach items='${blog_list}' var='blog'>
                        <tr align="left" valign="middle">
                          <form name="${blog.id}" id="${blog.id}" action="${contextPath}/blog/del" method="post">
                              <input type="hidden" name="id" value="${blog.id}"/>
                              <td width="68%" valign="middle" nowrap class="">
                                  <img src="${contextPath}/images/default_activity.png" width="16">&nbsp;
                                  <a href="${contextPath}/blog/read/${blog.id}/?q=${q}"><font color="#66CCFF" size="+1"><b>${blog.title}</b></font></a>
                              </td>
                              <td width="9%" align="right" nowrap class="">
                                  ${blog.tag}&nbsp;
                              </td> 
                              <td width="15%" align="right" nowrap>
                                  ${blog.create_date}&nbsp;
                              </td>
                              <c:if test='${user.role == "admin"}'>
                                  <td width="4%" align="center" nowrap class="">
                                      <a href="${contextPath}/blog/edit/${blog.id}">编辑</a>
                                  </td> 
                                  <td width="4%" align="center" nowrap class="">
                                      <a href="#" onClick="onDel(${blog.id});">删除</a>
                                  </td>
                              </c:if>
                          </form>
                      </tr>
                      <tr align="left" valign="middle">
                          <td colspan="5" class="border_bottom_green_dotted">${blog.content}</td>
                      </tr>
                    </c:forEach>
		        </table>
            </div>
			<div id="tabs-2">
            	<table width="100%"> 
                    <c:forEach items='${code_list}' var='code'>
                        <tr align="left" valign="middle">
                          <form name="${code.id}" id="${code.id}" action="${contextPath}/code/del" method="post">
                              <input type="hidden" name="id" value="${code.id}"/>
                              <td width="68%" valign="middle" nowrap class="border_bottom_green_dotted">
                                  <img src="${contextPath}/images/default_activity.png" width="16">&nbsp;
                                  <a href="${contextPath}/code/read/${code.id}/?q=${q}"><font color="#FF99CC" size="+1">${code.title}</font></a>
                              </td>
                              <td width="9%" align="right" nowrap class="border_bottom_green_dotted">
                                  ${blog.tag}&nbsp;
                              </td>
                <td width="15%" align="right" nowrap class="border_bottom_green_dotted">
                                  ${code.create_date}&nbsp;
                              </td> 
                              <c:if test='${user.role == "admin"}'>
                                  <td width="4%" align="center" nowrap class="border_bottom_green_dotted">
                                      <a href="${contextPath}/code/edit/${code.id}">编辑</a>
                                  </td> 
                                  <td width="4%" align="center" nowrap class="border_bottom_green_dotted">
                                      <a href="#" onClick="onDel(${code.id});">删除</a>
                                  </td>
                              </c:if>
                          </form>
                      </tr>
                      <tr align="left" valign="middle">
                          <td colspan="5" style="background-color:#666">${code.content}</td> 
                      </tr>
                    </c:forEach>
   			  </table>
            </div>
		</div>
    </c:when>
    <c:otherwise>
		<div align="center"><font color='#FFFF00' size="+4"><c:out value="我们没有搜索到任何博文和代码:("/></font></div>
    </c:otherwise>
</c:choose>
</body>
</html>