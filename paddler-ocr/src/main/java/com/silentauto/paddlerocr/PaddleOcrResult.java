package com.silentauto.paddlerocr;

import android.graphics.Point;
import android.graphics.Rect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PaddleOcrResult {
    private final ArrayList<Point> points = new ArrayList<>();
    private final ArrayList<Integer> wordIndices = new ArrayList<>();
    private String text = "";
    private float confidence;
    private int clsIndex = -1;
    private float clsConfidence;

    void addPoint(int x, int y) {
        points.add(new Point(x, y));
    }

    void addWordIndex(int index) {
        wordIndices.add(index);
    }

    public List<Point> getPoints() {
        return Collections.unmodifiableList(points);
    }

    List<Integer> getWordIndices() {
        return Collections.unmodifiableList(wordIndices);
    }

    public Rect getBounds() {
        if (points.isEmpty()) {
            return new Rect();
        }
        int left = points.get(0).x;
        int top = points.get(0).y;
        int right = left;
        int bottom = top;
        for (Point point : points) {
            left = Math.min(left, point.x);
            top = Math.min(top, point.y);
            right = Math.max(right, point.x);
            bottom = Math.max(bottom, point.y);
        }
        return new Rect(left, top, right, bottom);
    }

    public String getText() {
        return text;
    }

    void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public float getConfidence() {
        return confidence;
    }

    void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public int getClsIndex() {
        return clsIndex;
    }

    void setClsIndex(int clsIndex) {
        this.clsIndex = clsIndex;
    }

    public float getClsConfidence() {
        return clsConfidence;
    }

    void setClsConfidence(float clsConfidence) {
        this.clsConfidence = clsConfidence;
    }
}
