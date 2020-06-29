package editor;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.geometry.Orientation;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.scene.Cursor;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;


public class Editor extends Application {

    private static File editFile;

    private static int WINDOW_HEIGHT = 500;
    private static int WINDOW_WIDTH = 500;

    private final Rectangle cursor = new Rectangle(0, 0);

    private ArrayList<TextObject> _contents;
    private UndoList _addedCharacters;

    private static final int MARGIN_WIDTH = 5;
    private static final int MARGIN_HEIGHT = 0;
    private static final int DEFAULT_FONT_SIZE = 12;

    private int fontSize = DEFAULT_FONT_SIZE;
    private String fontName = "Verdana";

    private int currX = MARGIN_WIDTH;
    private int currY = MARGIN_HEIGHT;

    private Group _root;
    private int contentsIndex;

    private int totalLines = 1;

    private int currLine = 1;

    private HashMap<Integer, TextObject> firstLetterInLine;
    private ArrayList<Rectangle> highlights;
    private String highlightedText;
    private int highlightStartIndex;
    private int highlightEndIndex;

    private class KeyEventHandler implements EventHandler<KeyEvent> {

        public KeyEventHandler(Group root) {
            _root = root;
            _contents = new ArrayList<>();
            _addedCharacters = new UndoList();
            firstLetterInLine = new HashMap<>();
            contentsIndex = 0;
            cursor.setFill(Color.BLACK);
            cursor.setHeight(fontSize);
            cursor.setWidth(1);
            cursor.setX(MARGIN_WIDTH);
            highlights = new ArrayList<>();
            makeCursorBlink();
            highlightedText = "";
        }

        public KeyEventHandler(Group root, String contents) {
            this(root);
            for (char c : contents.toCharArray()) {
                if (c == '\n') {
                    setNewLine();
                } else {
                    setCharacter(c);
                }
            }
            contentsIndex = 0;
            currX = MARGIN_WIDTH;
            currY = 0;
            format();

            /**
            for (int l : firstLetterInLine.keySet()) {
                TextObject temp = firstLetterInLine.get(l);
                System.out.println(temp.getText() + " x: " + temp.getX() + " y: " + temp.getY() + " line: " + temp.getLine());
            }
            **/
        }

        @Override
        public void handle(KeyEvent keyEvent) {
            if (keyEvent.isShortcutDown()) {
                KeyCode code = keyEvent.getCode();
                if (code == KeyCode.S) {
                    try {
                        FileWriter writer = new FileWriter(editFile.getName());
                        for (TextObject text : _contents) {
                            writer.write(text.getText());
                        }
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (code == KeyCode.Z) {
                    undo();
                } else if (code == KeyCode.Y) {
                    redo();
                } else if (code == KeyCode.PLUS || code == KeyCode.EQUALS) {
                    resize(fontSize + 4);
                } else if (code == KeyCode.MINUS) {
                    if (fontSize - 4 < 1) {
                        return;
                    }
                    resize(fontSize - 4);
                } else if (code == KeyCode.C) {
                    copy();
                } else if (code == KeyCode.V) {
                    paste();
                }
            }
            else if (keyEvent.getEventType() == KeyEvent.KEY_TYPED) {
                String characterTyped = keyEvent.getCharacter();
                if (characterTyped.length() > 0 && characterTyped.charAt(0) != 8) {
                    if (!highlightedText.equals("")) {
                        backspace();
                    }
                    TextObject addText = setCharacter(characterTyped.charAt(0));
                    UndoList nextUndo = new UndoList(contentsIndex - 1, addText, false);
                    _addedCharacters.setTail(nextUndo);
                    if (_addedCharacters.getTail() != null) {
                        nextUndo.setPrev(_addedCharacters);
                        _addedCharacters = nextUndo;
                    }
                    keyEvent.consume();
                }
            } else if (keyEvent.getEventType() == KeyEvent.KEY_PRESSED) {
                KeyCode code = keyEvent.getCode();
                if(code == KeyCode.BACK_SPACE){
                    backspace();
                } else if(code == KeyCode.UP) {
                    if (currY > 0) {
                        TextObject text = findNearestText(currX, currY - fontSize);
                        currX = (int) text.getX();
                        currY = (int) text.getY();
                        currLine = text.getLine();
                        contentsIndex = text.getIndex();
                    } else {
                        currX = MARGIN_WIDTH;
                        contentsIndex = 0;
                    }
                } else if (code == KeyCode.DOWN){
                    if (_contents.size() == 0) {
                        return;
                    }
                    TextObject lastText = _contents.get(_contents.size() - 1);
                    int newY = currY + fontSize;
                    if (newY <= lastText.getY()) {
                        TextObject text = findNearestText(currX, currY + fontSize);
                        currX = (int) text.getX();
                        currY = (int) text.getY();
                        currLine = text.getLine();
                        contentsIndex = text.getIndex();
                    } else {
                        currX = (int) (lastText.getX() + lastText.getWidth() + 0.5);
                        contentsIndex = _contents.size() - 1;
                    }
                } else if (code == KeyCode.LEFT) {
                    if (contentsIndex > 0) {
                        TextObject temp = _contents.get(contentsIndex - 1);
                        if (temp.getText().equals("\n") && contentsIndex > 1) {
                            temp = _contents.get(contentsIndex - 2);
                            if (temp.getText().equals(" ")) {
                                currX = (int) temp.getX();
                            } else {
                                currX = (int) (temp.getX() + temp.getWidth() + 0.5);
                                contentsIndex = temp.getIndex() + 1;
                            }
                            currY -= fontSize;
                        } else {
                            currX = (int) temp.getX();
                        }
                        contentsIndex -= 1;
                    }
                } else if (code == KeyCode.RIGHT) {
                    if (contentsIndex < _contents.size()) {
                        TextObject temp = _contents.get(contentsIndex);
                        if (temp.getText().equals("\n")) {
                            currX = MARGIN_WIDTH;
                            currY += fontSize;
                        } else {
                            currX += temp.getWidth() + 0.5;
                            currY = (int) temp.getY();
                        }
                        contentsIndex += 1;
                    }
                }
            }
            format();
            if (contentsIndex > 0 && contentsIndex < _contents.size()) {
                TextObject currText = _contents.get(contentsIndex);
                if (!currText.getText().equals("\n") && (currText.getY() != currY || currText.getX() != currX)) {
                    currX = (int) (currText.getX() + currText.getWidth() + 0.5);
                    currY = (int) currText.getY();
                }
            }
            setCursor(currX, currY);
        }
    }



    private class CursorBlinkEventHandler implements EventHandler<ActionEvent> {

        private boolean isBlack;

        CursorBlinkEventHandler() {
            isBlack = true;
            cursor.setFill(Color.BLACK);
        }

        private void blinking() {
            if (isBlack) {
                cursor.setFill(Color.WHITE);
                isBlack = false;
            } else {
                cursor.setFill(Color.BLACK);
                isBlack = true;
            }
        }

        @Override
        public void handle(ActionEvent actionEvent) {
            blinking();
        }
    }

    private class MouseEventHandler implements EventHandler<MouseEvent> {

        @Override
        public void handle(MouseEvent mouseEvent) {
            if (_contents.size() == 0) {
                return;
            }
            if (highlightedText.equals("")) {
                setCursor(currX, currY);
            }
            double mousePressedX = mouseEvent.getX();
            double mousePressedY = mouseEvent.getY();
            EventType eventType = mouseEvent.getEventType();
            TextObject text = findNearestText(mousePressedX, mousePressedY);
            mousePressedX = text.getX();
            mousePressedY = text.getY();
            highlightedText = "";
            highlightEndIndex = -1;
            highlightStartIndex = -1;
            if (eventType == MouseEvent.MOUSE_PRESSED) {
                currX = (int) text.getX();
                currY = (int) text.getY();
                currLine = text.getLine();
                contentsIndex = text.getIndex();
                _root.getChildren().removeAll(highlights);
                highlights.clear();
                setCursor(currX, currY);
            } else if (eventType == MouseEvent.MOUSE_DRAGGED) {
                _root.getChildren().remove(cursor);
                _root.getChildren().removeAll(highlights);
                highlights.clear();
                Rectangle newHighlight;
                double yDifference = currY - mousePressedY;
                if (yDifference >= fontSize) {
                    int x = (int) text.getX();
                    int startY = (int) text.getY();
                    int endY = currY;
                    TextObject startText = _contents.get(text.getIndex());
                    TextObject endText;
                    while (startY != endY) {
                        endText = findNearestText(WINDOW_WIDTH, startY);
                        newHighlight = setRectangle(x, startY, endText.getX() - startText.getX(), fontSize);
                        if (endText.getText().equals("\n") || startText.getText().equals("\n")) {
                            newHighlight.setWidth(TextObject.spaceCharWidth());
                        } else if (endText.getIndex() + 1 < _contents.size() && _contents.get(endText.getIndex() + 1).getText().equals("\n")) {
                            newHighlight.setWidth(newHighlight.getWidth() + TextObject.spaceCharWidth());
                        }
                        startY += fontSize;
                        startText = findNearestText(MARGIN_WIDTH, startY);
                        x = MARGIN_WIDTH;
                    }
                    endText = _contents.get(contentsIndex - 1);
                    setRectangle(MARGIN_WIDTH, endY, endText.getX() + endText.getWidth() - MARGIN_WIDTH, fontSize);
                } else if (Math.abs(yDifference) >= fontSize) {
                    int x = currX;
                    int startY = currY;
                    int endY = (int) text.getY();
                    if (contentsIndex >= _contents.size()) {
                        setRectangle(text.getX(), text.getY(), TextObject.spaceCharWidth(), fontSize);
                    } else {
                        TextObject startText = _contents.get(contentsIndex);
                        TextObject endText;
                        while (startY != endY) {
                            endText = findNearestText(WINDOW_WIDTH, startY);
                            newHighlight = setRectangle(x, startY, endText.getX() - startText.getX(), fontSize);
                            if (endText.getText().equals("\n") || startText.getText().equals("\n")) {
                                newHighlight.setWidth(TextObject.spaceCharWidth());
                            }
                            else if (endText.getIndex() + 1 < _contents.size() && _contents.get(endText.getIndex() + 1).getText().equals("\n")) {
                                newHighlight.setWidth(newHighlight.getWidth() + TextObject.spaceCharWidth());
                            }
                            startY += fontSize;
                            startText = findNearestText(MARGIN_WIDTH, startY);
                            x = MARGIN_WIDTH;
                        }
                        endText = findNearestText(text.getX(), endY);
                        newHighlight = setRectangle(MARGIN_WIDTH, endY, text.getX() - MARGIN_WIDTH, fontSize);
                        if (endText.getText().equals("\n")) {
                            newHighlight.setWidth(TextObject.spaceCharWidth());
                        } else if ((endText.getIndex() + 1 < _contents.size() && _contents.get(endText.getIndex() + 1).getText().equals("\n"))
                                || endText.getIndex() >= _contents.size() - 1) {
                            newHighlight.setWidth(newHighlight.getWidth() + TextObject.spaceCharWidth());
                        }
                    }
                } else {
                    if (mousePressedX < currX) {
                        newHighlight = setRectangle(text.getX(), currY, Math.abs(text.getX() - currX), fontSize);
                    } else {
                        newHighlight = setRectangle(currX, currY, Math.abs(text.getX() - currX), fontSize);
                    }
                    if (text.getText().equals("\n")) {
                        newHighlight.setWidth(TextObject.spaceCharWidth());
                    }
                }
                _root.getChildren().addAll(0, highlights);
            } else if (eventType == MouseEvent.MOUSE_RELEASED) {
                highlightStartIndex = Math.min(text.getIndex(), contentsIndex);
                highlightEndIndex = Math.max(text.getIndex(), contentsIndex);
                contentsIndex = highlightStartIndex;
                for (int i = highlightStartIndex; i < highlightEndIndex; i += 1) {
                    TextObject temp = _contents.get(i);
                    if (temp.getText() != null) {
                        highlightedText += temp.getText();
                    }
                }
            }
        }

        public Rectangle setRectangle(double x, double y, double width, double height) {
            Rectangle newHighlight = new Rectangle(x, y, width, height);
            highlights.add(newHighlight);
            newHighlight.setFill(Color.LIGHTBLUE);
            newHighlight.toBack();
            return newHighlight;
        }
    }

    private class UndoList {
        private Node _node;
        private UndoList _prev;
        private UndoList _tail;

        public UndoList() {

        }

        public UndoList(int index, TextObject text, boolean deleted) {
            _node = new Node(index, text, deleted);
        }

        public UndoList(int index, ArrayList<TextObject> text, boolean deleted) {

        }

        public Node getNode() {
            return _node;
        }

        public UndoList getPrev() {
            return _prev;
        }

        public UndoList getTail() {
            return _tail;
        }

        public void setNode(Node node) {
            _node = node;
        }

        public void setPrev(UndoList prev) {
            _prev = prev;
        }

        public void setTail(UndoList tail) {
            _tail = tail;
        }

        private class Node {
            private int _index;
            private TextObject _text;
            private boolean _deleted;

            public Node (int index, TextObject text, boolean deleted) {
                _index = index;
                _text = text;
                _deleted = deleted;
            }

            public int getIndex() {
                return _index;
            }

            public TextObject getText() {
                return _text;
            }

            public boolean isDeleted() {
                return _deleted;
            }
        }
    }

    private void backspace() {
        if (highlightedText.equals("")) {
            if (contentsIndex <= 0) {
                return;
            }
            TextObject deleteText = _contents.remove(contentsIndex - 1);
            if (deleteText.getText().equals("\n")) {
                if (_contents.size() > 1) {
                    TextObject temp = _contents.get(contentsIndex - 2);
                    currX = (int) (temp.getX() + temp.getWidth() + 0.5);
                    currY -= fontSize;
                } else {
                    currX = MARGIN_WIDTH;
                    currY = 0;
                }
            } else {
                currX = (int) deleteText.getX();
                currY = (int) deleteText.getY();
            }
            contentsIndex -= 1;
            UndoList nextUndo = new UndoList(contentsIndex, deleteText, true);
            _addedCharacters.setTail(nextUndo);
            if (_addedCharacters.getTail() != null) {
                nextUndo.setPrev(_addedCharacters);
                _addedCharacters = nextUndo;
            }
            _root.getChildren().remove(deleteText.getTextObject());
        } else {
            TextObject firstText = _contents.get(highlightStartIndex);
            TextObject undoText = new TextObject((int) firstText.getX(), (int) firstText.getY(), highlightedText, firstText.getIndex());
            for (int i = highlightEndIndex; i >= highlightStartIndex; i -= 1) {
                TextObject deleteText = _contents.remove(i);
                if (deleteText.getText().equals("\n")) {
                    if (_contents.size() > 1) {
                        TextObject temp = _contents.get(i - 2);
                        currX = (int) (temp.getX() + temp.getWidth() + 0.5);
                        currY -= fontSize;
                    } else {
                        currX = MARGIN_WIDTH;
                        currY = 0;
                    }
                } else {
                    currX = (int) deleteText.getX();
                    currY = (int) deleteText.getY();
                }
                _root.getChildren().remove(deleteText.getTextObject());
            }
            UndoList nextUndo = new UndoList(contentsIndex, undoText, true);
            _addedCharacters.setTail(nextUndo);
            if (_addedCharacters.getTail() != null) {
                nextUndo.setPrev(_addedCharacters);
                _addedCharacters = nextUndo;
            }
            if (firstText.getText().equals("\n")) {
                currY = (int) firstText.getY() - fontSize;
            } else {
                currY = (int) firstText.getY();
            }
            currX = (int) firstText.getX();
            highlightedText = "";
            highlightStartIndex = highlightEndIndex = 0;
            _root.getChildren().removeAll(highlights);
        }
    }

    private void printContents() {
        for (int i = 0; i < _contents.size(); i += 1) {
            TextObject temp = _contents.get(i);
            if (temp.getText().equals("\n")) {
                System.out.println();
            } else {
                System.out.print(" (" + temp.getText() + "," + temp.getX() + "," + temp.getY() + ")");
            }
        }
    }

    private TextObject setNewLine() {
        currX = MARGIN_WIDTH;
        currY += fontSize;
        currLine += 1;
        TextObject addText = new TextObject(currX, currY, "\n", currLine);
        _contents.add(contentsIndex, addText);
        contentsIndex += 1;
        return addText;
    }

    private TextObject setCharacter(char character) {
        if (character == '*') {
            System.out.println("entered");
        }
        if (character == 13) {
            return setNewLine();
        }
        TextObject addText = new TextObject(currX, currY, String.valueOf(character), currLine);
        addText.getTextObject().setTextOrigin(VPos.TOP);
        addText.getTextObject().setFont(Font.font(fontName, fontSize));
        currX += addText.getWidth() + 0.5;
        _root.getChildren().add(addText.getTextObject());
        _contents.add(contentsIndex, addText);
        contentsIndex += 1;
        if (currX > WINDOW_WIDTH - MARGIN_WIDTH) {
            TextObject temp = wrapWord(contentsIndex - 1);
            if (temp == null) {
                currX = MARGIN_WIDTH;
                currY += fontSize;
            } else {
                currY = (int) temp.getY();
                currX = (int) (temp.getX() + temp.getWidth() + 0.5);
            }
            currLine += 1;
        }
        currLine = addText.getLine();
        return addText;
    }

    public void format() {
        TextObject temp;
        int line = 1;
        int x = MARGIN_WIDTH;
        int y = MARGIN_HEIGHT;
        totalLines = 1;
        for (int i = 0; i < _contents.size(); i += 1) {
            temp = _contents.get(i);
            if (temp.getText().equals("*")) {
                System.out.println(temp.getX() + " " + temp.getY());
            }
            if ((int) (x + temp.getWidth() + 0.5) > WINDOW_WIDTH - MARGIN_WIDTH) {
                wrapWord(i);
                if (temp.getText().equals(" ")) {
                    x = (int)(MARGIN_WIDTH - temp.getWidth() + 0.5);
                } else {
                    x = (int) temp.getX();
                }
                y += fontSize;
                totalLines += 1;
                line += 1;
            } else if (temp.getText().equals("\n")) {
                x = MARGIN_WIDTH;
                y += fontSize;
                totalLines += 1;
                line += 1;
            }
            if (x == MARGIN_WIDTH) {
                firstLetterInLine.put(line, temp);
            }
            temp.setLine(line);
            temp.setIndex(i);
            temp.setX(x);
            temp.setY(y);
            x += temp.getWidth() + 0.5;
        }
    }

    private TextObject wrapWord(int index) {
        ArrayList<TextObject> word = getEntireWord(index);
        if (word.isEmpty()) {
            return null;
        }
        TextObject temp = word.get(0);
        TextObject lastLetter = word.get(word.size() - 1);
        int x = MARGIN_WIDTH;
        int y;
        double width = lastLetter.getX() + lastLetter.getWidth();
        int line;
        if (temp.getLine() == 0) {
            line = 0;
        } else {
            line = temp.getLine() + 1;
        }
        // below needs fixing
        int stuff = (int)(width - temp.getX() + 0.5);
        if (stuff > WINDOW_WIDTH - MARGIN_WIDTH * 2) {
            y = (int) temp.getY();
        } else {
            y = (int) temp.getY() + fontSize;
        }
        //
        for (int i = 0; i < word.size(); i += 1) {
            temp = word.get(i);
            temp.setX(x);
            temp.setY(y);
            temp.setLine(line);
            x += temp.getWidth() + 0.5;
            if (x >= WINDOW_WIDTH - MARGIN_WIDTH * 2) {
                x = MARGIN_WIDTH;
                y += fontSize;
            }
        }
        if (!_contents.get(index).getText().equals(" ")) {
            temp = word.get(0);
            firstLetterInLine.put(temp.getLine(), temp);
        }
        return _contents.get(index);
    }

    private void undo() {
        UndoList.Node node = _addedCharacters.getNode();
        if (node == null) {
            return;
        }
        int index = node.getIndex();
        TextObject text = node.getText();
        if (node.isDeleted()) {
            if (node.getText().getText().length() > 1) {
                String stringContents = text.getText();
                largeTextInsertion(stringContents);
                index += text.getText().length();
            } else {
                _contents.add(index, text);
                currX = (int) (text.getX() + text.getWidth() + 0.5);
                currY = (int) text.getY();
                _root.getChildren().add(text.getTextObject());
            }
        } else {
            if (node.getText().getText().length() > 1) {

            } else {
                _contents.remove(text);
                currX = (int) text.getX();
                currY = (int) text.getY();
                _root.getChildren().remove(text.getTextObject());
            }
        }
        _addedCharacters = _addedCharacters.getPrev();
        contentsIndex = index;
    }

    private void redo() {
        if(_addedCharacters.getTail() == null) {
            return;
        }
        UndoList.Node node = _addedCharacters.getTail().getNode();
        TextObject redoText = node.getText();
        int index = node.getIndex();
        if (node.isDeleted()) {
            _contents.remove(redoText);
            contentsIndex -= 1;
            currX = (int) redoText.getX();
            currY = (int) redoText.getY();
            _root.getChildren().remove(redoText.getTextObject());
        } else {
            _contents.add(index, redoText);
            currX = (int) (redoText.getX() + redoText.getWidth() + 0.5);
            currY = (int) redoText.getY();
            _root.getChildren().add(redoText.getTextObject());
        }
        _addedCharacters = _addedCharacters.getTail();
        contentsIndex = index;
    }

    private void resize(int size) {
        fontSize = size;
        for (int i = 0; i < _contents.size(); i += 1) {
            _contents.get(i).getTextObject().setFont(new Font(fontName, fontSize));
        }
        if (contentsIndex < 0) {
            return;
        } else if (contentsIndex == 0) {
            setCursor(MARGIN_WIDTH, 0);
        } else {
            TextObject temp = _contents.get(contentsIndex - 1);
            setCursor((int) (temp.getX() + temp.getWidth() + 0.5), (int) temp.getY());
        }
    }

    private ArrayList<TextObject> getEntireWord(int index) {
        ArrayList<TextObject> word = new ArrayList<>();
        int wordBeginIndex = index;
        int wordEndIndex = index + 1;
        while (wordBeginIndex >= 0) {
            TextObject temp = _contents.get(wordBeginIndex);
            if (temp.getText().equals(" ")) {
                break;
            }
            word.add(0, temp);
            wordBeginIndex -= 1;
        }
        while (wordEndIndex < _contents.size()) {
            TextObject temp = _contents.get(wordEndIndex);
            if (_contents.get(wordEndIndex).getText().equals(" ")){
                break;
            }
            word.add(temp);
            wordEndIndex += 1;
        }
        return word;
    }

    private void copy() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent newContent = new ClipboardContent();
        newContent.putString(highlightedText);
        clipboard.setContent(newContent);
    }

    private void paste() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        String stringContents = clipboard.getString();
        UndoList nextUndo = largeTextInsertion(stringContents);
        _addedCharacters.setTail(nextUndo);
        if (_addedCharacters.getTail() != null) {
            nextUndo.setPrev(_addedCharacters);
            _addedCharacters = nextUndo;
        }
    }

    private UndoList largeTextInsertion(String s) {
        String stringContents = s;
        TextObject firstText = setCharacter(stringContents.charAt(0));
        if (stringContents != "") {
            String characters = stringContents.substring(1);
            TextObject temp = null;
            for (char c : characters.toCharArray()) {
                if (c == '\n') {
                    temp = setNewLine();
                } else {
                    temp = setCharacter(c);
                }
            }
            UndoList undoList = new UndoList(contentsIndex, firstText, false);
            contentsIndex += stringContents.length();
            currX = (int) (temp.getX() + temp.getWidth() + 0.5);
            currY = (int) temp.getY();
            currLine = temp.getLine();
            return undoList;
        }
        return null;
    }

    private void makeCursorBlink() {
        final Timeline timeline = new Timeline();
        timeline.setCycleCount(Timeline.INDEFINITE);
        CursorBlinkEventHandler cursorChange = new CursorBlinkEventHandler();
        KeyFrame keyFrame = new KeyFrame(Duration.seconds(0.5), cursorChange);
        timeline.getKeyFrames().add(keyFrame);
        timeline.play();
        cursor.toFront();
    }

    /**
     * Start the search the closest spot between the first letter, current letter, and last letter.
     * Find the nearest Text object according to the coordinates.
     * @param x horizontal position
     * @param y vertical position
     * @return
     */
    private TextObject findNearestText(double x, double y) {
        int line = (int) (y / (double) fontSize) + 1;
        TextObject temp;
        if (line > totalLines) {
            temp = _contents.get(_contents.size() - 1);
            return new TextObject((int) (temp.getX() + temp.getWidth() + 0.5), (int) temp.getY(), temp.getText(), temp.getLine(), _contents.size());
        } else if (line < 1) {
            temp = _contents.get(0);
            return temp;
        }
        temp = firstLetterInLine.get(line);
        int index = temp.getIndex();
        while (index < _contents.size() && _contents.get(index).getLine() == line) {
            temp = _contents.get(index);
            if (temp.getX() <= x && (int) (temp.getX() + temp.getWidth() + 0.5) >= x) {
                double middleX = temp.getX() + temp.getWidth() / 2.0;
                if (x - middleX > 0) {
                    temp = new TextObject((int) (temp.getX() + temp.getWidth() + 0.5), (int) temp.getY(), temp.getText(), line, index + 1);
                } else {
                    temp = new TextObject((int) temp.getX(), (int) temp.getY(), temp.getText(), line, index);
                }
                return temp;
            }
            index += 1;
        }
        if (x - (int) (temp.getX() + temp.getWidth() - MARGIN_WIDTH + 0.5) < 0) {
            temp = firstLetterInLine.get(line);
            return new TextObject(MARGIN_WIDTH, (int) temp.getY(), temp.getText(), line, temp.getIndex());
        }
        return new TextObject((int) (temp.getX() + temp.getWidth() + 0.5), (int) temp.getY(), temp.getText(), temp.getLine(), index);
    }

    private void setCursor(int x, int y) {
        currX = x;
        currY = y;
        cursor.setX(x);
        cursor.setY(y);
        cursor.setFill(Color.BLACK);
        cursor.setHeight(fontSize);
        if (!_root.getChildren().contains(cursor)) {
            _root.getChildren().add(cursor);
        }
    }

    @Override
    public void start(Stage primaryStage) {
        Group root = new Group();
        Scene scene = new Scene(root, WINDOW_WIDTH, WINDOW_HEIGHT, Color.WHITE);
        EventHandler<KeyEvent> keyEventHandler = new KeyEventHandler(root);
        try {
            if (editFile.exists()) {
                String readFile = "";
                FileReader reader = new FileReader(editFile);
                BufferedReader bufferedReader = new BufferedReader(reader);
                int intRead;
                while ((intRead = bufferedReader.read()) != -1) {
                    readFile += (char) intRead;
                }
                keyEventHandler =
                        new KeyEventHandler(root, readFile);
            }
        } catch (FileNotFoundException fileNotFoundException) {
        System.out.println("File not found! Exception was: " + fileNotFoundException);
        } catch (IOException ioException) {
        System.out.println("Error when copying; exception was: " + ioException);
        }
        scene.setOnKeyTyped(keyEventHandler);
        scene.setOnKeyPressed(keyEventHandler);
        root.getChildren().add(cursor);

        MouseEventHandler mouseEventHandler = new MouseEventHandler();
        scene.setOnMousePressed(mouseEventHandler);
        scene.setOnMouseDragged(mouseEventHandler);
        scene.setOnMouseReleased(mouseEventHandler);

        scene.widthProperty().addListener(new ChangeListener<Number>() {
            @Override
            public void changed(ObservableValue<? extends Number> observable,
                                Number oldScreenWidth,
                                Number newScreenWidth) {
               WINDOW_WIDTH = newScreenWidth.intValue();
               format();
               TextObject temp;
               if (contentsIndex >= _contents.size()) {
                   temp = _contents.get(_contents.size() - 1);
                   setCursor((int) (temp.getX() + temp.getWidth() + 0.5), (int) temp.getY());
               } else {
                   temp = _contents.get(contentsIndex);
                   setCursor((int) temp.getX(), (int) temp.getY());
               }
            }
        });
        scene.heightProperty().addListener(new ChangeListener<Number>() {
            @Override public void changed(
                    ObservableValue<? extends Number> observableValue,
                    Number oldScreenHeight,
                    Number newScreenHeight) {
                WINDOW_HEIGHT = newScreenHeight.intValue();
                format();
                TextObject temp;
                if (contentsIndex >= _contents.size()) {
                    temp = _contents.get(_contents.size() - 1);
                    setCursor((int) (temp.getX() + temp.getWidth() + 0.5), (int) temp.getY());
                } else {
                    temp = _contents.get(contentsIndex);
                    setCursor((int) temp.getX(), (int) temp.getY());
                }
            }
        });

        /**
        ScrollBar scrollBar = new ScrollBar();
        scrollBar.setOrientation(Orientation.VERTICAL);
        scrollBar.setPrefHeight(WINDOW_HEIGHT);
        **/

        primaryStage.setTitle("Editor");

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("No filename was provided.");
            System.exit(0);
        } else if (args.length == 1) {
            File file = new File(args[0]);
            if (file.exists() && file.listFiles() != null) {
                System.out.println("Unable to open file " + args[0]);
                System.exit(0);
            }
            editFile = file;
            launch(args);
        }
    }
}