package com.github.enerccio.lingollm.ui.dialogs;

import com.github.enerccio.lingollm.CssStyles;
import com.github.enerccio.lingollm.loc.L;
import com.github.enerccio.lingollm.loc.Localization;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.web.util.HtmlUtils;

@Configurable(preConstruction = true)
public class TextInputDialog extends Dialog {

	@Autowired
	private Localization loc;

	private TextField inputMessage;

	private TextInputDialog(String message, MessageAction yes, boolean showCancel,
                            String inputMessageLabel, boolean messageRequired, String initialValue) {
		setCloseOnEsc(true);
		setCloseOnOutsideClick(true);
		setModality(ModalityMode.STRICT);

		HorizontalLayout hl = new HorizontalLayout();
		hl.setWidthFull();

		Button yesButton = new Button(loc.getValue(L.LABEL_YES), event -> {
			if (messageRequired && StringUtils.isBlank(inputMessage.getValue())) {
				inputMessage.setInvalid(true);
				return;
			}

			yes.run(inputMessage.getValue());
			close();
		});

		hl.add(yesButton);

		if (showCancel)
			hl.add(new Button(loc.getValue(L.LABEL_CANCEL), event -> close()));

		hl.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

		VerticalLayout vl = new VerticalLayout();
		vl.setSizeFull();
		vl.setPadding(true);
		vl.setSpacing(true);

		Html msg = new Html("<h3 class='" + CssStyles.TEXT_CENTER + "'>" + getMessage(message) + "</h3>");

		inputMessage = new TextField(inputMessageLabel);
		inputMessage.setWidthFull();
		if (initialValue != null)
			inputMessage.setValue(initialValue);
		vl.add(inputMessage);

		vl.add(msg, inputMessage, hl);
		vl.setHorizontalComponentAlignment(FlexComponent.Alignment.CENTER, msg, inputMessage, hl);

		add(vl);
	}
	
	private String getMessage(String message) {
		message = HtmlUtils.htmlEscape(message);
		message = message.replace("\n", "<br/>");

		return message;
	}

	public static class Builder {
		private final String message;
		private final MessageAction yes;
		private MessageAction no;
		private boolean showCancel;
		private String inputMessageLabel;
		private boolean messageRequired = false;
		private String initialValue;

		public Builder(String message, MessageAction yes) {
			this.message = message;
			this.yes = yes;
		}

		public Builder showCancel(boolean showCancel) {
			this.showCancel = showCancel;

			return this;
		}

		public Builder inputMessageLabel(String inputMessageLabel) {
			this.inputMessageLabel = inputMessageLabel;

			return this;
		}

		public Builder messageRequired() {
			messageRequired = true;

			return this;
		}

		public Builder initialValue(String initialValue) {
			this.initialValue = initialValue;

			return this;
		}

		public TextInputDialog build() {
			return new TextInputDialog(message, yes, showCancel, inputMessageLabel, messageRequired, initialValue);
		}
	}

	@FunctionalInterface
	public interface MessageAction {
		void run(String message);
	}
}
