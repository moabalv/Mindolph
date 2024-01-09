package com.mindolph.mindmap;

import com.igormaznitsa.mindmap.model.*;
import com.mindolph.base.ShortcutManager;
import com.mindolph.base.constant.ShortcutConstants;
import com.mindolph.base.event.EventBus;
import com.mindolph.base.event.OpenFileEvent;
import com.mindolph.mfx.dialog.DialogFactory;
import com.mindolph.mfx.preference.FxPreferences;
import com.mindolph.mfx.util.DesktopUtils;
import com.mindolph.mfx.util.RectangleUtils;
import com.mindolph.mindmap.constant.MindMapConstants;
import com.mindolph.mindmap.dialog.*;
import com.mindolph.mindmap.event.MindmapEvents;
import com.mindolph.mindmap.extension.MindMapExtensionRegistry;
import com.mindolph.mindmap.extension.api.Extension;
import com.mindolph.mindmap.extension.api.ExtensionContext;
import com.mindolph.mindmap.extension.api.VisualAttributeExtension;
import com.mindolph.mindmap.extension.attribute.ExtraFileExtension;
import com.mindolph.mindmap.extension.attribute.ExtraJumpExtension;
import com.mindolph.mindmap.extension.attribute.ExtraNoteExtension;
import com.mindolph.mindmap.extension.attribute.ExtraURIExtension;
import com.mindolph.mindmap.extension.exporters.*;
import com.mindolph.mindmap.extension.importers.*;
import com.mindolph.mindmap.extension.manipulate.TopicColorExtension;
import com.mindolph.mindmap.model.*;
import com.mindolph.mindmap.util.CryptoUtils;
import com.mindolph.mindmap.util.MmdContextMenuUtils;
import com.mindolph.mindmap.util.TopicUtils;
import javafx.application.Platform;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

import static com.mindolph.core.constant.SceneStatePrefs.MINDOLPH_MMD_FILE_LINK_IS_OPEN_IN_SYS;
import static com.mindolph.core.constant.SceneStatePrefs.MINDOLPH_MMD_FILE_LINK_LAST_FOLDER;
import static com.mindolph.mindmap.constant.MindMapConstants.FILELINK_ATTR_LINE;
import static com.mindolph.mindmap.constant.MindMapConstants.FILELINK_ATTR_OPEN_IN_SYSTEM;
import static com.mindolph.mindmap.constant.StandardTopicAttribute.*;

/**
 * @author mindolph.com@gmail.com
 * @see MindMapView
 * @see ExtraMindMapSkin
 */
public class ExtraMindMapView extends MindMapView implements ExtensionContext {

    private static final Logger log = LoggerFactory.getLogger(ExtraMindMapView.class);

    private ContextMenu contextMenu;

    // current editing note data for determining any changes.
    private NoteEditorData editingNoteData = null;

    static {
        MindMapExtensionRegistry.getInstance().registerExtension(new ExtraFileExtension());
        MindMapExtensionRegistry.getInstance().registerExtension(new ExtraNoteExtension());
        MindMapExtensionRegistry.getInstance().registerExtension(new ExtraJumpExtension());
        MindMapExtensionRegistry.getInstance().registerExtension(new ExtraURIExtension());
        // Importers
        MindMapExtensionRegistry.getInstance().registerExtension(new Text2MindMapImporter());
        MindMapExtensionRegistry.getInstance().registerExtension(new Mindmup2MindMapImporter());
        MindMapExtensionRegistry.getInstance().registerExtension(new Freemind2MindMapImporter());
        MindMapExtensionRegistry.getInstance().registerExtension(new XMind2MindMapImporter());
        MindMapExtensionRegistry.getInstance().registerExtension(new CoggleMM2MindMapImporter());
        MindMapExtensionRegistry.getInstance().registerExtension(new Novamind2MindMapImporter());
        // Exporters
        MindMapExtensionRegistry.getInstance().registerExtension(new FreeMindExporter());
        MindMapExtensionRegistry.getInstance().registerExtension(new MarkdownExporter());
        MindMapExtensionRegistry.getInstance().registerExtension(new ASCIIDocExporter());
//        MindMapExtensionRegistry.getInstance().registerExtension(new MindmupExporter()); not supported for now
        MindMapExtensionRegistry.getInstance().registerExtension(new PNGImageExporter());
//        MindMapExtensionRegistry.getInstance().registerExtension(new ORGMODEExporter()); not supported for now
        MindMapExtensionRegistry.getInstance().registerExtension(new TextExporter());
        MindMapExtensionRegistry.getInstance().registerExtension(new SVGImageExporter());

    }

    public ExtraMindMapView() {
        super();
        this.init();
    }

    public ExtraMindMapView(MindMap<TopicNode> model, MindMapConfig config) {
        super(model, config);
        this.init();
    }

    private void init() {
        this.workspaceDir = SystemUtils.getUserHome(); // user dir as the default project dir;
        this.setOnContextMenuRequested(e -> {
            log.debug("request context menu");
            e.consume();
            super.endEdit(); // commit or cancel if editing.
            Point2D point = withoutViewportPadding(e.getX(), e.getY());
            Point2D sp = new Point2D(e.getScreenX(), e.getScreenY());
            elementUnderMouse = findTopicUnderPoint(point);
            processPopUp(sp, elementUnderMouse);
        });
    }

    protected void onMousePressed(MouseEvent mouseEvent) {
        super.onMousePressed(mouseEvent);
        if (!mouseEvent.isPopupTrigger()) {
            if (contextMenu != null) contextMenu.hide();
        }
    }

    @Override
    protected void onKeyPressed(KeyEvent event) {
        super.onKeyPressed(event);
        if (!event.isConsumed()) {
            if (ShortcutManager.getIns().isKeyEventMatch(event, ShortcutConstants.KEY_SHOW_POPUP)) {
                log.debug("key com: " + ShortcutManager.getIns().getKeyCombination(ShortcutConstants.KEY_SHOW_POPUP));
                TopicNode topic = getFirstSelectedTopic();
                Point2D posElement = new Point2D(0, 0);
                BaseElement element = null;
                if (topic != null) {
                    element = (BaseElement) topic.getPayload();
                    Rectangle2D bounds = element.getBounds();
                    posElement = withoutViewportPadding(RectangleUtils.centerX(bounds), RectangleUtils.centerY(bounds));
                }
                processPopUp(this.localToScreen(posElement), element);
            }
        }
    }

    @Override
    protected void onVisualAttributeClicked(MouseEvent e, BaseElement element) {
        Point2D point = translateMousePos(e);
        VisualAttributeExtension extension = element.getVisualAttributeImageBlock().findExtensionForPoint(point.getX() - element.getBounds().getMinX(), point.getY() - element.getBounds().getMinY());
        boolean processedByExtension = false;
        if (extension != null) {
            if (extension.isClickable(element.getModel())) {
                processedByExtension = true;
                try {
                    log.debug("Clicked on visual attribute");
                    if (extension.onClick(this, element.getModel(), e.isShiftDown() || e.isControlDown(), e.getClickCount())) {
                        onMindMapModelChanged(true);
                    }
                } catch (Exception ex) {
                    log.error("Error during visual attribute processing", ex);
                    DialogFactory.errDialog("Error occurred");
                }
            }
        }
        if (!processedByExtension) {
            removeAllSelection();
            selectAndUpdate(element.getModel(), false);
        }
    }

    protected void processPopUp(Point2D point, BaseElement elementUnderMouse) {
        // ElementPart partUnderMouse = elementUnderMouse == null ? null : elementUnderMouse.findPartForPoint(point);
        TopicNode topic = null;
        if (elementUnderMouse != null) {
            topic = elementUnderMouse.getModel();
            // clear other selection if context requested on another topic.
            if (!this.getSelectedTopics().contains(topic)) {
                this.getSelectedTopics().clear();
                this.selectAndUpdate(topic, false);
            }
        }

        log.debug("Create context menu for element under mouse: " + elementUnderMouse);
        if (contextMenu != null) {
            contextMenu.hide();
            contextMenu = null;
        }
        MindMapViewSkin<MindMapView> skin = getMindMapViewSkin();
        contextMenu = MmdContextMenuUtils.createContextMenu(this, model, this, skin, false, elementUnderMouse);
        if (contextMenu != null) {
            contextMenu.setOnShowing(windowEvent -> {
                mouseDragSelection = null;
            });
            contextMenu.setOnHiding(windowEvent -> {
                mouseDragSelection = null;
            });
            contextMenu.setOnCloseRequest(windowEvent -> {
                mouseDragSelection = null;
            });
            contextMenu.show(this, point.getX(), point.getY());
            contextMenu.requestFocus();
        }
    }

    @Override
    protected void onClickOnExtra(TopicNode topic, int clicks, Extra<?> extra) {
        if (clicks == 2) {
            switch (extra.getType()) {
                case FILE -> {
                    MMapURI uri = (MMapURI) extra.getValue();
                    File theFile = uri.asFile(this.getFile().getParentFile());
                    try {
                        File canonicalFile = theFile.getCanonicalFile();
                        if (canonicalFile.isFile()) {
                            String openInSys = uri.getParameters().getProperty(FILELINK_ATTR_OPEN_IN_SYSTEM, "false");
                            if (Boolean.parseBoolean(openInSys)) {
                                log.debug("Open in System");
                                try {
                                    DesktopUtils.openInSystem(theFile, false);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    DialogFactory.warnDialog("Unable to open file in system");
                                }
                            }
                            else {
                                Platform.runLater(() -> {
                                    EventBus.getIns().notifyOpenFile(new OpenFileEvent(canonicalFile, true));
                                });
                            }
                        }
                        else {
                            Platform.runLater(() -> DialogFactory.warnDialog("File not found: " + canonicalFile));
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                case LINK -> {
                    MMapURI extraUri = (MMapURI) extra.getValue();
                    String url = extraUri.asURI().toString();
                    DesktopUtils.openURL(url);
                }
                case NOTE -> editTopicNote(topic);
                case TOPIC -> {
                    TopicNode theTopic = getModel().findTopicForLink((ExtraTopic) extra);
                    if (theTopic == null) {
                        DialogFactory.warnDialog("No topic found");
                    }
                    else {
                        // detected
                        this.focusTo(theTopic);
                    }
                }
            }
        }
    }

    private void editTopicNote(TopicNode topic) {
        ExtraNote note = (ExtraNote) topic.getExtras().get(Extra.ExtraType.NOTE);
        Consumer<NoteEditorData> callbackForNoteEdit = newNoteData -> {
            log.debug("dialog result: " + newNoteData);
            if (newNoteData != null) {
                boolean changed = false;
                if (newNoteData.getText().isEmpty()) {
                    if (note != null) {
                        topic.removeExtra(Extra.ExtraType.NOTE);
                        changed = true;
                    }
                }
                else {
                    String newNoteText;
                    if (newNoteData.isEncrypted()) {
                        try {
                            newNoteText = CryptoUtils.encrypt(newNoteData.getPassword(), newNoteData.getText());
                        } catch (RuntimeException ex) {
                            DialogFactory.warnDialog("Can't encrypt note!\nCheck JDK security policy for AES-256 support");
                            log.error("Can't encrypt note", ex);
                            return;
                        }
                    }
                    else {
                        newNoteText = newNoteData.getText();
                    }

                    log.debug("Original data: " + editingNoteData);
                    if (editingNoteData == null
                            || !newNoteText.equals(editingNoteData.getText())
                            || editingNoteData.isEncrypted() != newNoteData.isEncrypted()) {
                        topic.setExtra(new ExtraNote(newNoteText, newNoteData.isEncrypted(), newNoteData.getHint()));
                        changed = true;
                    }
                }
                if (changed) {
                    onMindMapModelChanged(true);
                    super.updateStatusBarForTopic(topic);
                }
            }
            else {
                log.warn("No data returned");
            }
        };

        // Handle saving during editing of note.
        MindmapEvents.subscribeNoteSaveEvent(topic, newNoteData -> {
            log.debug(newNoteData.getText());
            callbackForNoteEdit.accept(newNoteData);
            editingNoteData.setText(newNoteData.getText()); // reset original text for closing dialog positively.
            log.debug("Notify mmd editor to save file");
            MindmapEvents.notifyMmdSave(this);
        });

        if (note == null) {
            // create new
            editingNoteData = new NoteEditorData();
            NoteDialog noteDialog = new NoteDialog(topic,
                    String.format("Create Note of '%s'", topic.getText()), editingNoteData, getMindMapConfig().getNoteFont());
            noteDialog.show(callbackForNoteEdit);
        }
        else {
            // edit
            if (note.isEncrypted()) {
                PasswordData passwordData = new PasswordData("", note.getHint());
                passwordData = new PasswordDialog(passwordData).showAndWait();
                if (passwordData != null && StringUtils.isNotBlank(passwordData.getPassword())) {
                    StringBuilder decrypted = new StringBuilder();
                    String pass = passwordData.getPassword().trim();
                    try {
                        if (CryptoUtils.decrypt(pass, note.getValue(), decrypted)) {
                            editingNoteData = new NoteEditorData(decrypted.toString(), note.isEncrypted(), pass, note.getHint());
                        }
                        else {
                            DialogFactory.errDialog("Wrong password!");
                            return;
                        }
                    } catch (RuntimeException ex) {
                        DialogFactory.errDialog("Can't decode encrypted text for error!\nEither broken data or current JDK security policy doesn't support AES-256!");
                        log.error("Can't decode encrypted note", ex);
                        return;
                    }
                }
                else {
                    return;
                }
            }
            else {
                editingNoteData = new NoteEditorData(note.getValue(), note.isEncrypted(), null, null);
            }
            new NoteDialog(topic,
                    String.format("Edit note of '%s'", topic.getText()), editingNoteData, getMindMapConfig().getNoteFont())
                    .show(callbackForNoteEdit);
        }
        // after dialog closed, unsubscribe to avoid conflict with next time note dialog shows.
        MindmapEvents.unsubscribeNoteSaveEvent(topic);
    }

    private void editTopicLink(TopicNode topic) {
        if (topic != null) {
            ExtraLink link = (ExtraLink) topic.getExtras().get(Extra.ExtraType.LINK);
            String url = null;
            if (link != null) {
                url = link.getAsString();
            }

            new UrlDialog(String.format("URL of topic %s", topic.getText()), url).show(resultURL -> {
                log.debug("URL: " + resultURL);
                if (resultURL == null) {
                    return; // cancel
                }
                if (StringUtils.isBlank(resultURL)) {
                    topic.removeExtra(Extra.ExtraType.LINK);
                }
                else {
                    try {
                        topic.setExtra(new ExtraLink(resultURL));
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
                }
                onMindMapModelChanged(true);
                super.updateStatusBarForTopic(topic);
            });
        }
    }

    private void editTopicFileLink(TopicNode topic) {
        if (topic != null) {
            ExtraFile extraFile = (ExtraFile) topic.getExtras().get(Extra.ExtraType.FILE);
            String title;
            FileLink fileLink;
            if (extraFile == null) {
                title = "Add File Link";
                fileLink = null; // the data will be loaded from preferences later in dialog initialization.
            }
            else {
                title = "Edit File Link";
                MMapURI mUri = extraFile.getValue();
                boolean isOpenInSystem = Boolean.parseBoolean(mUri.getParameters().getProperty(FILELINK_ATTR_OPEN_IN_SYSTEM, "false"));
                int line = FilePathWithLine.strToLine(mUri.getParameters().getProperty(FILELINK_ATTR_LINE, null));
                fileLink = new FileLink(mUri.asFile(null) + (line < 0 ? "" : ":" + line), isOpenInSystem);
            }
            Consumer<FileLink> callback = result -> {
                log.debug("result: " + result);
                if (result == null) {
                    return;
                }

                if (result.equals(fileLink)) {
                    return;
                }

                FxPreferences.getInstance().savePreference(MINDOLPH_MMD_FILE_LINK_IS_OPEN_IN_SYS, result.isShowWithSystemTool());
                boolean valueChanged;
                if (result.getFilePathWithLine().isEmptyOrOnlySpaces()) {
                    valueChanged = topic.removeExtra(Extra.ExtraType.FILE);
                }
                else {
                    Properties props = new Properties();
                    if (result.isShowWithSystemTool()) {
                        props.put(FILELINK_ATTR_OPEN_IN_SYSTEM, "true");
                    }
                    if (result.getFilePathWithLine().getLine() >= 0) {
                        props.put(FILELINK_ATTR_LINE, Integer.toString(result.getFilePathWithLine().getLine()));
                    }

                    boolean isRelativePathToWorkspace = FilenameUtils.directoryContains(workspaceDir.getPath(), result.getFilePathWithLine().getPath());
                    MMapURI fileUri = MMapURI.makeFromFilePath(isRelativePathToWorkspace ? workspaceDir : null,
                            result.getFilePathWithLine().getPath(), props);
                    log.info(String.format("Path %s converted to uri: %s", result.getFilePathWithLine(),
                            fileUri.asString(false, true)));

                    File theFile = fileUri.asFile(this.getFile().getParentFile());
                    log.debug("absolute file path: " + theFile);
                    try {
                        File canonicalFile = theFile.getCanonicalFile();
                        if (canonicalFile.exists()) {
                            if (extraFile == null) {
                                FxPreferences.getInstance().savePreference(MINDOLPH_MMD_FILE_LINK_LAST_FOLDER, canonicalFile.getParentFile().toString());
                            }
                            topic.setExtra(new ExtraFile(fileUri));
                            valueChanged = true;
                        }
                        else {
                            DialogFactory.errDialog(String.format("File doesn't exit: %s", result.getFilePathWithLine().getPath()));
                            valueChanged = false;
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        log.error("Failed to save file link", e);
                        valueChanged = false;
                    }
                }

                if (valueChanged) {
                    onMindMapModelChanged(true);
                    super.updateStatusBarForTopic(topic);
                }
            };

            FileLinkDialog flinkDialog = new FileLinkDialog(title, workspaceDir, fileLink);
            flinkDialog.show(callback);
        }
    }

    private void editTopicInternalLink(TopicNode topic) {
        if (topic != null) {
            ExtraTopic remove = new ExtraTopic("_______"); // todo this could be refactored.
            Consumer<TopicNode> callback = selected -> {
                ExtraTopic result = null;
                boolean removeLink = selected == null || selected == topic;// equals to be linked topic means remove the link.
                if (removeLink) {
                    result = remove;
                }
                else {
                    log.debug("Selected: " + selected.getText());
                    result = ExtraTopic.makeLinkTo(getModel(), selected);
                }
                boolean changed = false;
                if (result == remove) {
                    if (topic.getExtras().get(Extra.ExtraType.TOPIC) != null) {
                        topic.removeExtra(Extra.ExtraType.TOPIC);
                        changed = true;
                    }
                }
                else {
                    Object prev = topic.getExtras().get(Extra.ExtraType.TOPIC);
                    if (prev == null) {
                        topic.setExtra(result);
                        changed = true;
                    }
                    else {
                        if (!result.equals(prev)) {
                            topic.setExtra(result);
                            changed = true;
                        }
                    }
                }
                if (!removeLink) {
                    changed = !"true".equals(model.getAttribute(MindMapConstants.MODEL_ATTR_SHOW_JUMPS));
                    model.setAttribute(MindMapConstants.MODEL_ATTR_SHOW_JUMPS, "true");
                }
                if (changed) {
                    onMindMapModelChanged(true);
                    super.updateStatusBarForTopic(topic);
                }
            };
            ExtraTopic link = (ExtraTopic) topic.getExtras().get(Extra.ExtraType.TOPIC);
            TopicNode topicForLink = model.findTopicForLink(link);
            TopicTreeDialog dialog = new TopicTreeDialog("Select Topic", getModel(), topic, topicForLink);
            dialog.show(callback);
        }
    }


    private void processColorDialogForTopics(List<TopicNode> topics) {
        Color borderColor = TopicUtils.extractCommonColorFromTopics(ATTR_BORDER_COLOR.getText(), topics.toArray(new TopicNode[0]));
        Color fillColor = TopicUtils.extractCommonColorFromTopics(ATTR_FILL_COLOR.getText(), topics.toArray(new TopicNode[0]));
        Color textColor = TopicUtils.extractCommonColorFromTopics(ATTR_TEXT_COLOR.getText(), topics.toArray(new TopicNode[0]));

        ColorDialog.ColorSet colorSet = new ColorDialog.ColorSet(borderColor, fillColor, textColor);

        ColorDialog colorDialog = new ColorDialog(String.format("Edit Colors for %d Topic(s)", topics.size()), colorSet, config);
        colorDialog.show(newColorSet -> {

            for (TopicNode topic : topics) {
                log.debug("Change color for: " + topic.getText());
                if (newColorSet.getBorderColor() != null)
                    topic.setAttribute(ATTR_BORDER_COLOR.getText(), newColorSet.getBorderColor().toString());
                if (newColorSet.getFillColor() != null)
                    topic.setAttribute(ATTR_FILL_COLOR.getText(), newColorSet.getFillColor().toString());
                if (newColorSet.getTextColor() != null)
                    topic.setAttribute(ATTR_TEXT_COLOR.getText(), newColorSet.getTextColor().toString());
            }
            this.requestFocus();
            onMindMapModelChanged(true);
        });
    }

    @Override
    public MindMapConfig getMindMapConfig() {
        return config;
    }

    @Override
    public MindMap<TopicNode> getModel() {
        return model;
    }

    @Override
    public void openFile(File file, boolean preferSystemBrowser) {
        if (!file.exists()) {
            DialogFactory.warnDialog("File doesn't exist any more");
            return;
        }
        EventBus.getIns().notifyOpenFile(new OpenFileEvent(file, true));
    }


    public void processExtensionActivation(Extension extension, TopicNode activeTopic) {
        if (extension instanceof ExtraNoteExtension) {
            if (activeTopic != null) {
                editTopicNote(activeTopic);
                requestFocus();
            }
        }
        else if (extension instanceof ExtraURIExtension) {
            editTopicLink(activeTopic);
            requestFocus();
        }
        else if (extension instanceof ExtraFileExtension) {
            editTopicFileLink(activeTopic);
            requestFocus();
        }
        else if (extension instanceof ExtraJumpExtension) {
            editTopicInternalLink(activeTopic);
            requestFocus();
        }
        else if (extension instanceof TopicColorExtension) {
            List<TopicNode> selectedTopics = this.getSelectedTopics();
            processColorDialogForTopics((selectedTopics != null && selectedTopics.size() > 0) ? selectedTopics : List.of(activeTopic));
            requestFocus();
        }
        else {
            throw new Error("Unsupported extension: " + extension.getClass().getName());
        }
    }

    @Override
    public void doNotifyModelChanged(boolean addToHistory) {
        onMindMapModelChanged(addToHistory);
    }

    @Override
    public void collapseOrExpandAll(boolean collapse) {
        endEdit(null, false);
        removeAllSelection();
        TopicNode rootTopic = this.model.getRoot();
        if (rootTopic != null && rootTopic.foldOrUnfoldChildren(collapse, Integer.MAX_VALUE)) {
            rootToCentre();
            onMindMapModelChanged(true);
        }
    }

    @Override
    public boolean cloneTopic(TopicNode topic, boolean cloneSubtree) {
        if (topic == null || topic.getTopicLevel() == 0) {
            return false;
        }
        TopicNode cloned = this.model.cloneTopic(topic, cloneSubtree);
        if (cloned != null) {
            cloned.moveAfter(topic);
            onMindMapModelChanged(true);
        }
        return true;
    }

}
