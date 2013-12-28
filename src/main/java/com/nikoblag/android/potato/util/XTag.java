package com.nikoblag.android.potato.util;

public class XTag {
    public static final int INNER = 0;
    public static final int ACROSS_DOWN = 1;
    public static final int ACROSS = 2;
    public static final int DOWN = 3;

    public final int type;
    public final String answer;
    public final String definitionA;
    public final String definitionD;

    public XTag(int type, String answer, String definitionA, String definitionD) {
        this.type = type;
        this.answer = answer;
        this.definitionA = definitionA;
        this.definitionD = definitionD;
    }
}
