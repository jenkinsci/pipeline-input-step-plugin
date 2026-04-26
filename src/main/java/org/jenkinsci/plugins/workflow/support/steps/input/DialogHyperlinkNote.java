/*
 * The MIT License
 *
 * Copyright 2026 Tim Jacomb
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.support.steps.input;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.MarkupText;
import hudson.Util;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

/**
 * Console note that turns its text into a button that opens a
 * <a href="https://www.jenkins.io/doc/developer/design-library/dialogs/">dialog</a>.
 */
public final class DialogHyperlinkNote extends ConsoleNote<Object> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(DialogHyperlinkNote.class.getName());

    public static String encodeTo(String url, String text) {
        // Match HyperlinkNote#encodeTo: collapse newlines so the stored length matches the
        // displayed length (otherwise MarkupText#rangeCheck throws when annotating).
        text = text.replace('\n', ' ').replace('\r', ' ');
        try {
            return new DialogHyperlinkNote(url, text.length()).encode() + text;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to serialize " + DialogHyperlinkNote.class, e);
            return text;
        }
    }

    private final String url;
    private final int length;

    public DialogHyperlinkNote(String url, int length) {
        this.url = url;
        this.length = length;
    }

    @Override
    public ConsoleAnnotator<Object> annotate(Object context, MarkupText text, int charPos) {
        String resolved = url;
        if (resolved.startsWith("/")) {
            StaplerRequest2 req = Stapler.getCurrentRequest2();
            if (req != null) {
                resolved = req.getContextPath() + resolved;
            } else {
                Jenkins j = Jenkins.getInstanceOrNull();
                String rootUrl = j != null ? j.getRootUrl() : null;
                if (rootUrl != null) {
                    resolved = rootUrl + resolved.substring(1);
                } else {
                    LOGGER.warning("You need to define the root URL of Jenkins");
                }
            }
        }
        String open = "<button type=\"button\" class=\"jenkins-button jenkins-button--primary input-step-dialog-opener\""
                + " data-type=\"dialog-opener\" data-dialog-url=\"" + Util.escape(resolved) + "\">";
        text.addMarkup(charPos, charPos + length, open, "</button>");
        return null;
    }

    @Extension public static final class DescriptorImpl extends ConsoleAnnotationDescriptor {
        @NonNull
        @Override public String getDisplayName() {
            return "Dialog Hyperlinks";
        }
    }
}
