package com.nikoblag.android.potato.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.widget.EditText;

import java.util.ArrayList;

public class XwBox extends EditText {
    private ArrayList<OnBackspaceListener> backspaceListeners = new ArrayList<OnBackspaceListener>();

    public XwBox(Context context) {
        super(context);
    }

    public XwBox(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public XwBox(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return new XwInputConnection(super.onCreateInputConnection(outAttrs),
                true);
    }

    public void addOnBackspaceListener(OnBackspaceListener listener) {
        backspaceListeners.add(listener);
    }

    public interface OnBackspaceListener {
        void onBackspace(XwBox xwb);
    }

    private class XwInputConnection extends InputConnectionWrapper {

        public XwInputConnection(InputConnection target, boolean mutable) {
            super(target, mutable);
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            boolean result = super.deleteSurroundingText(beforeLength, afterLength);
            // magic: in latest Android, deleteSurroundingText(1, 0) will be called for backspace
            if (beforeLength == 1 && afterLength == 0) {
                for (OnBackspaceListener listener : backspaceListeners) {
                    listener.onBackspace(XwBox.this);
                }
            }

            return result;
        }

    }
}
