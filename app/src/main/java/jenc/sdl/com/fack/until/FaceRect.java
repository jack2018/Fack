package jenc.sdl.com.fack.until;

import android.graphics.Point;
import android.graphics.Rect;

public class FaceRect {
    public float score;

    public Rect bound = new Rect();
    public Point point[];

    public Rect raw_bound = new Rect();
    public Point raw_point[];

    @Override
    public String toString() {
        return bound.toString();
    }
}
