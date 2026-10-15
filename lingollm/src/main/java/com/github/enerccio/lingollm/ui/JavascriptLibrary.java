package com.github.enerccio.lingollm.ui;

import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.component.page.PendingJavaScriptResult;
import com.vaadin.flow.dom.Element;

/**
 * All the JavaScript the application runs in the browser from the server side. Every method wraps one script, the
 * element it works on is always passed as {@code $0}.
 */
public final class JavascriptLibrary {

    private JavascriptLibrary() {
    }

    // --- clipboard ---

    /**
     * Copies the text to the clipboard of the browser.
     */
    public static void copyToClipboard(Page page, String text) {
        page.executeJs("navigator.clipboard.writeText($0);", text);
    }

    // --- page ---

    /**
     * Creates the {@code <style>} element with the given id in the page head, or replaces the content of the existing one.
     */
    public static void setPageStyle(Page page, String id, String css) {
        page.executeJs("""
                let style = document.getElementById($0);
                if (!style) {
                  style = document.createElement('style');
                  style.id = $0;
                  document.head.appendChild(style);
                }
                style.textContent = $1;
                """, id, css);
    }

    public static void removePageStyle(Page page, String id) {
        page.executeJs("document.getElementById($0)?.remove();", id);
    }

    /**
     * Makes the login overlay accept an empty password.
     */
    public static void disableLoginPasswordValidation(Element element) {
        element.executeJs("document.getElementById('vaadinLoginPassword').validate = document.getElementById('vaadinLoginPassword').checkValidity = function () { return true; };");
    }

    // --- element geometry ---

    /**
     * Resolves to {@code x;y;w;h} of the element, x and y relative to the document.
     */
    public static PendingJavaScriptResult getPositionAndSize(Element element) {
        return element.executeJs("""
                return (function () {
                   var w = $0.clientWidth;
                   var h = $0.clientHeight;
                   var bb = $0.getBoundingClientRect();
                   var x = bb.left + window.scrollX;
                   var y = bb.top + window.scrollY;
                   return '' + x + ';' + y + ';' + w + ';' + h;
                } ())""", element);
    }

    // --- scrolling ---

    /**
     * Resolves to the rounded {@code scrollTop} of the element.
     */
    public static PendingJavaScriptResult getScrollTop(Element element) {
        return element.executeJs("return Math.round($0.scrollTop);", element);
    }

    public static void setScrollTop(Element element, int pixelsFromTop) {
        element.executeJs("$0.scrollTop = $1", element, pixelsFromTop);
    }

    /**
     * Scrolls the text area of a Vaadin {@code TextArea} (the element of the field) to its last line, after the value
     * that was just set is rendered.
     */
    public static void scrollTextAreaToBottom(Element textArea) {
        textArea.executeJs("""
                requestAnimationFrame(() => {
                  const input = $0.inputElement;
                  if (input) input.scrollTop = input.scrollHeight;
                });
                """, textArea);
    }

    public static void setScrollLeft(Element element, int pixelsFromLeft) {
        element.executeJs("$0.scrollLeft = $1", element, pixelsFromLeft);
    }

    /**
     * Installs (once) the listener behind the sticky bottom scroll: {@code el._stick} is cleared as soon as the user
     * scrolls up and set again when the bottom is reached. Content growth never changes {@code scrollTop}, so it cannot
     * unstick or restick by itself, unlike measuring the distance to the bottom after the content already grew.
     */
    private static final String INSTALL_STICKY_SCROLL = """
            const installSticky = (el) => {
              if (el._stickInstalled) return;
              el._stickInstalled = true;
              el._lastTop = el.scrollTop;
              el._stick = (el.scrollHeight - el.scrollTop - el.clientHeight) < 8;
              el.addEventListener('scroll', () => {
                const top = el.scrollTop;
                if (top < el._lastTop) {
                  el._stick = false;
                }
                if ((el.scrollHeight - top - el.clientHeight) < 8) {
                  el._stick = true;
                }
                el._lastTop = top;
              }, {passive: true});
            };
            """;

    public static void scrollToBottom(Element element) {
        element.executeJs(INSTALL_STICKY_SCROLL + """
                installSticky($0);
                $0._stick = true;
                $0.scrollTop = $0.scrollHeight;
                $0._lastTop = $0.scrollTop;
                """, element);
    }

    /**
     * Scrolls to the bottom once the layout following the current changes is done, and sticks to the bottom again.
     */
    public static void scrollToBottomAfterLayout(Element element) {
        element.executeJs(INSTALL_STICKY_SCROLL + """
                installSticky($0);
                $0._stick = true;
                requestAnimationFrame(() => {
                  requestAnimationFrame(() => {
                    $0.scrollTop = $0.scrollHeight;
                    $0._lastTop = $0.scrollTop;
                  });
                });
                """, element);
    }

    /**
     * Scrolls to the bottom while the element is stuck to it, i.e. until the user scrolls up; reaching the bottom again
     * sticks it again.
     */
    public static void scrollToBottomIfAtBottom(Element element) {
        element.executeJs(INSTALL_STICKY_SCROLL + """
                const el = $0;
                installSticky(el);
                requestAnimationFrame(() => {
                  if (el._stick) {
                    el.scrollTop = el.scrollHeight;
                    el._lastTop = el.scrollTop;
                  }
                });
                """, element);
    }

    /**
     * Restores the scroll position once the layout is done; to the bottom when there is nothing saved.
     */
    public static void restoreScrollPosition(Element element, Integer savedPosition) {
        element.executeJs(INSTALL_STICKY_SCROLL + """
                installSticky($0);
                requestAnimationFrame(() => {
                  requestAnimationFrame(() => {
                    const el = $0;
                    const target = $1;
                    if (target !== null && target !== undefined) {
                      el.scrollTop = target;
                    } else {
                      el.scrollTop = el.scrollHeight;
                    }
                    el._lastTop = el.scrollTop;
                    el._stick = (el.scrollHeight - el.scrollTop - el.clientHeight) < 8;
                  });
                });
                """, element, savedPosition);
    }

    /**
     * Scrolls the element to the top of its scroll parent and flashes an outline around it.
     */
    public static void scrollToAndHighlight(Element element) {
        element.executeJs("""
                requestAnimationFrame(() => {
                  requestAnimationFrame(() => {
                    $0.scrollIntoView({block: 'start'});
                    $0.animate([{outline: '3px solid var(--lumo-warning-color)'}, {outline: '3px solid transparent'}],
                               {duration: 2000});
                  });
                });
                """, element);
    }

    // --- element events ---

    /**
     * Fires {@code panel-scroll} ({@code detail.scrollTop}) on the element, 300 ms after the user stops scrolling.
     */
    public static void fireDebouncedScrollEvent(Element element) {
        element.executeJs("""
                const el = $0;
                if (!el._hasScrollListener) {
                  el._hasScrollListener = true;
                  let timer;
                  el.addEventListener('scroll', () => {
                    clearTimeout(timer);
                    timer = setTimeout(() => {
                      el.dispatchEvent(new CustomEvent('panel-scroll', { detail: { scrollTop: Math.round(el.scrollTop) } }));
                    }, 300);
                  });
                }
                """, element);
    }

    // --- tab sheet ---

    /**
     * Calls {@code onScrollToEnd()} on the server when the tab sheet content is scrolled to the end.
     */
    public static void notifyTabSheetScrolledToEnd(Element tabSheet) {
        tabSheet.executeJs("""
                var self = $0;
                var el = self.querySelector("vaadin-horizontal-layout");
                if (el) {
                    el.addEventListener("scroll", function(e) {
                        if(el.scrollTop + el.clientHeight === el.scrollHeight) {
                            self.$server.onScrollToEnd();
                        }
                    });
                }""", tabSheet);
    }

    /**
     * Fires {@code myscroll} ({@code detail}: {@code scrollTop,scrollLeft}) on the tab sheet when its content scrolls.
     */
    public static void fireTabSheetScrollEvent(Element tabSheet) {
        tabSheet.executeJs("""
                var el = $0;
                el.querySelector("vaadin-horizontal-layout").addEventListener('scroll', e => {
                    const event = new CustomEvent('myscroll', { detail: e.target.scrollTop + ',' + e.target.scrollLeft });
                    el.dispatchEvent(event);
                })""", tabSheet);
    }
}
