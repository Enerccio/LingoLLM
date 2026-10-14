package com.github.enerccio.lingollm.ui.components;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Field;

public class ThreadCopyRequestAttributes extends ServletRequestAttributes {

	private ThreadCopyRequestAttributes(HttpServletRequest request, HttpServletResponse response) {
		super(request, response);
		HttpSession s = getSession(true); // store session
		requestDestructionCallbacks.clear();
		requestCompleted();
		try {
			Field f = ServletRequestAttributes.class.getDeclaredField("session");
			f.setAccessible(true);
			f.set(this, s);
		} catch (Exception e) {
			// ignore
		}
	}

	@Override
	protected void updateAccessedSessionAttributes() {
		// fake copy, do nothing
	}

	public static ThreadCopyRequestAttributes create() {
		RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
		if (attrs instanceof ThreadCopyRequestAttributes existing) {
			return existing;
		}
		if (attrs instanceof ServletRequestAttributes servletAttrs) {
			return new ThreadCopyRequestAttributes(servletAttrs.getRequest(), servletAttrs.getResponse());
		}
		return null;
	}

	public static class InRequestScope implements AutoCloseable {

		private final ThreadCopyRequestAttributes attributes;
		private final RequestAttributes oldAttributes;

		public InRequestScope(ThreadCopyRequestAttributes attributes) {
			this.attributes = attributes;
			oldAttributes = RequestContextHolder.getRequestAttributes();
			if (attributes == null)
				return;
			RequestContextHolder.setRequestAttributes(attributes);
		}

		@Override
		public void close() {
			if (attributes == null)
				return;
			RequestContextHolder.setRequestAttributes(oldAttributes);
		}
	}
	
}