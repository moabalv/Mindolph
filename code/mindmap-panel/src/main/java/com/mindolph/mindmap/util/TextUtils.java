package com.mindolph.mindmap.util;

import javafx.geometry.Bounds;
import javafx.geometry.Dimension2D;
import javafx.geometry.Insets;
import javafx.scene.control.TextArea;
import javafx.scene.text.Font;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mindolph.base.util.NodeUtils.getTextBounds;

/**
 * @author mindolph.com@gmail.com
 */
public class TextUtils {

    // refactor for different fonts todo
    private static Bounds alphabetBounds;
    private static Bounds chineseBounds;

    /**
     * Calculate bounds of text in TextArea.
     *
     * @param textArea
     * @return
     */
    public static Dimension2D calculateTextBounds(TextArea textArea) {
        Bounds bounds = getTextBounds(textArea.getText(), textArea.getFont());
        double width = bounds.getWidth();
        double height = bounds.getHeight();
        Insets padding = textArea.getPadding();
        width = width + padding.getLeft() + padding.getRight();
        height = height + padding.getTop() + padding.getBottom();// + lineSpacing * lines;
        // System.out.println(String.format("Calculate result: %s lines with %sx%s", lines, width, height));
        return new Dimension2D(width, height);
    }

    public static String substringByMaxPixels(String s, double maxPixels, Font font) {
        if (alphabetBounds == null) {
            alphabetBounds = getTextBounds("a", font);
        }
        if (chineseBounds == null) {
            chineseBounds = getTextBounds("中", font);
        }
        double len = 0;
        char[] chars = s.toCharArray();
        StringBuilder buf = new StringBuilder();
        for (char c : chars) {
            len += CharUtils.isAscii(c) ? alphabetBounds.getWidth() : chineseBounds.getWidth();
            if (len > maxPixels) {
                break;
            }
            buf.append(c);
        }
        return buf.toString();
    }

//    public static Bounds getTextBounds(String s, Font font) {
//        Text text = new Text(s);
//        text.setFont(font);
//        StackPane stackPane = new StackPane(text);
//        stackPane.layout();
//        return text.getLayoutBounds();
//    }

    public static String removeAllISOControlsButTabs(String str) {
        StringBuilder result = new StringBuilder(str.length());
        for (char c : str.toCharArray()) {
            if (c != '\t' && Character.isISOControl(c)) {
                continue;
            }
            result.append(c);
        }
        return result.toString();
    }

    public static int countHeading(String str, char c) {
        char[] chars = str.toCharArray();
        int count = 0;
        for (char aChar : chars) {
            if (aChar != c) {
                return count;
            }
            count++;
        }
        return count;
    }

    /**
     *
     * @param str
     * @param minSpaceSize
     * @return
     */
    public static int countIndent(String str, int minSpaceSize) {
        Pattern p = Pattern.compile("(?<INDENT>\\s*?)(\\S+[\\s\\S]*)");
        Matcher matcher = p.matcher(str);
        if (matcher.matches()) {
            String indent = matcher.group("INDENT");
            return StringUtils.countMatches(indent, "\t")  + StringUtils.countMatches(indent, StringUtils.repeat(' ', minSpaceSize));
        }
        return 0;
    }

}
