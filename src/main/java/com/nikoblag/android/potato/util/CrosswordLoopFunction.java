package com.nikoblag.android.potato.util;

public interface CrosswordLoopFunction<A, B, C> {
    void execute(A view, B row, C col);
}

