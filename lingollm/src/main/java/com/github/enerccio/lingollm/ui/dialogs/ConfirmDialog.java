package com.github.enerccio.lingollm.ui.dialogs;

import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.web.util.HtmlUtils;

@Configurable
public class ConfirmDialog extends Dialog {

	@Autowired
	private Localization loc;

	private final String message;
	private final Runnable yes;
	private final Runnable no;
	private final boolean showCancel;
	private final String yesLabel;
	private final String noLabel;

	public static void show(String message, Runnable yes) {
		show(message, yes, () -> {});
	}
	
	public static void show(String message, Runnable yes, Runnable no) {
		new ConfirmDialog(message, yes, no, false).create();
	}

	public static void show(String message, Runnable yes, Runnable no, boolean showCancel) {
		new ConfirmDialog(message, yes, no, showCancel).create();
	}

	/**
	 * Choice between two actions, buttons are labeled with the given texts instead of yes and no.
	 */
	public static void show(String message, String yesLabel, String noLabel, Runnable yes, Runnable no, boolean showCancel) {
		new ConfirmDialog(message, yes, no, showCancel, yesLabel, noLabel).create();
	}

	private ConfirmDialog(String message, Runnable yes, Runnable no, boolean showCancel) {
		this(message, yes, no, showCancel, null, null);
	}

	private ConfirmDialog(String message, Runnable yes, Runnable no, boolean showCancel, String yesLabel, String noLabel) {
		this.message = message;
		this.yes = yes;
		this.no = no;
		this.showCancel = showCancel;
		this.yesLabel = yesLabel;
		this.noLabel = noLabel;
	}
	
	public void create() {
		setCloseOnEsc(false);
		setCloseOnOutsideClick(false);
		
		HorizontalLayout hl = new HorizontalLayout();
		hl.setWidthFull();
		
		Button yes = new Button(yesLabel != null ? yesLabel : loc.getValue(L.LABEL_YES), event -> {
			this.yes.run();
			close();
		});
		
		Button no = new Button(noLabel != null ? noLabel : loc.getValue(L.LABEL_NO), event -> {
			this.no.run();
			close();
		});

		hl.add(yes, no);

		if (showCancel)
			hl.add(new Button(loc.getValue(L.LABEL_CANCEL), event -> close()));

		hl.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

		VerticalLayout vl = new VerticalLayout();
		vl.setSizeFull();
		vl.setPadding(true);
		vl.setSpacing(true);

		Html msg = new Html("<h3 class='" + CssStyles.TEXT_CENTER + "'>" + getMessage(message) + "</h3>");

		vl.add(msg);
		vl.add(hl);
		vl.setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, msg);
		vl.setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, hl);

		add(vl);
		open();
	}

	private String getMessage(String message) {
		message = HtmlUtils.htmlEscape(message);
		message = message.replace("\n", "<br/>");

		return message;
	}
}
