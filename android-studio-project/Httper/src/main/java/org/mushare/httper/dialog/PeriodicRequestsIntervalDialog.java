package org.mushare.httper.dialog;

import org.mushare.httper.R;
import org.mushare.httper.MainFragment;
import org.mushare.httper.PeriodicRequestsService;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import android.Manifest;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;

import static android.os.Build.VERSION.SDK_INT;

public class PeriodicRequestsIntervalDialog extends DialogFragment {
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final MainFragment fragment = (MainFragment) getTargetFragment();
        View view                   = ScrollView.inflate(getContext(), R.layout.dialog_periodic_requests_interval, null);
        final EditText editText     = view.findViewById(R.id.editText);
        Dialog alertDialog          = new AlertDialog.Builder(getContext())
            .setView(view)
            .setPositiveButton(
                R.string.dialog_ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        try {
                            int interval = Integer.parseInt(editText.getText().toString(), 10);

                            long requestRecordId = fragment.insertOrReplaceRequestRecord();
                            if (requestRecordId == -1L) return;

                            Context context = fragment.getContext().getApplicationContext();

                            Intent intent = new Intent(context, PeriodicRequestsService.class);
                            intent.setAction(PeriodicRequestsService.ACTION_START);
                            intent.putExtra("requestRecordId",  requestRecordId);
                            intent.putExtra("interval_seconds", interval);
                            context.startService(intent);
                        }
                        catch(Exception e) {}
                    }
                }
            )
            .setNegativeButton(
                R.string.dialog_cancel, null
            )
            .create();

        requestPermissions();
        return alertDialog;
    }

    private void requestPermissions() {
        if (SDK_INT >= 33) {
            if (getContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                getActivity().requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 0);
            }
        }
    }
}
