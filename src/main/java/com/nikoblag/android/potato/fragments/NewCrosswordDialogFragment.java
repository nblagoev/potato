package com.nikoblag.android.potato.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;

import com.nikoblag.android.potato.R;

public class NewCrosswordDialogFragment extends DialogFragment {
    private OnConfirmCallback onConfirmCallback = null;

    public void setOnConfirmCallback(OnConfirmCallback callback) {
        onConfirmCallback = callback;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.dialog_newcrossword_confirm_title)
                .setMessage(R.string.dialog_newcrossword_confirm)
                .setCancelable(true)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        if (onConfirmCallback != null)
                            onConfirmCallback.onConfirm();
                    }
                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        NewCrosswordDialogFragment.this.getDialog().cancel();
                    }
                });

        return builder.create();
    }

    public interface OnConfirmCallback {
        void onConfirm();
    }
}
