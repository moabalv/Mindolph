package com.mindolph.base.plugin;

import javafx.scene.control.MenuItem;
import javafx.scene.layout.StackPane;

import java.util.function.Consumer;

/**
 * @author mindolph.com@gmail.com
 */
public interface Generator {

    MenuItem contextMenuItem(String selectedText);

    StackPane inputDialog(Object editorId);

    void onCancel(Consumer<Object> consumer);

    /**
     * On completed with whether keep the generated text or not.
     * @param consumer
     */
    void onComplete(Consumer<Boolean> consumer);

    // should be called in a new thread
    void onGenerated(Consumer<String> consumer);

    StackPane reframeDialog(Object editorId);

}
