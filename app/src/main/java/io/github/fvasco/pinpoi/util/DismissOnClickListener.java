package io.github.fvasco.pinpoi.util;

import android.content.DialogInterface;

/**
 * Dismiss dialog
 *
 * @author Francesco Vasco
 */
public class DismissOnClickListener implements DialogInterface.OnClickListener {
    @Override
    public void onClick(DialogInterface dialog, int which) {
        dialog.dismiss();
    }
}
