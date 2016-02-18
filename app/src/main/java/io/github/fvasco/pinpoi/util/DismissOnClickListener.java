package io.github.fvasco.pinpoi.util;

import android.content.DialogInterface;

/**
 * Dismiss dialog
 *
 * @author Francesco Vasco
 */
public enum DismissOnClickListener implements DialogInterface.OnClickListener {
    INSTANCE;

    @Override
    public void onClick(DialogInterface dialog, int which) {
        dialog.dismiss();
    }
}
