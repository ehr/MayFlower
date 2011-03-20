package com.paladin.action;

import com.paladin.mvc.RequestContext;

/**
 * Chat Action
 * 
 * @author Erhu
 * @since Mar 17th, 2011
 */
public class ChatAction extends BaseAction {

	public void index(final RequestContext _reqCtxt) {
		if (_reqCtxt.session().getAttribute("user") != null) {
			forward(_reqCtxt, "/html/chat/room.jsp");
		} else {
			redirect(_reqCtxt, "/login/auto");
		}
	}
}
