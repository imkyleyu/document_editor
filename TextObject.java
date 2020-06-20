package editor;

import javafx.scene.text.Text;

public class TextObject{

    private Text _text;
    private int _index;
    private int _line;

    public TextObject(int x, int y, String s, int line) {
        _text = new Text(x, y, s);
        _line = line;
    }

    public TextObject(int x, int y, String s, int line, int index) {
        this(x, y, s, line);
        _index = index;
    }

    public int getIndex() {
        return _index;
    }

    public int getLine() {
        return _line;
    }

    public String getText() {
        return _text.getText();
    }

    public double getX() {
        return _text.getX();
    }

    public double getY() {
        return _text.getY();
    }

    public Text getTextObject() {
        return _text;
    }

    public double getWidth() {
        return _text.getLayoutBounds().getWidth();
    }

    public void setIndex(int index) {
        _index = index;
    }

    public void setLine(int line) {
        _line = line;
    }

    public void setX(double x) {
        _text.setX(x);
    }

    public void setY(double y) {
        _text.setY(y);
    }

    public static double spaceCharWidth() {
        return new Text(" ").getLayoutBounds().getWidth();
    }
}
