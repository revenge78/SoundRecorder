package by.naxa.soundrecorder.adapters;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.text.Editable;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import by.naxa.soundrecorder.BuildConfig;
import by.naxa.soundrecorder.DBHelper;
import by.naxa.soundrecorder.R;
import by.naxa.soundrecorder.RecordingItem;
import by.naxa.soundrecorder.listeners.OnDatabaseChangedListener;
import by.naxa.soundrecorder.listeners.OnSingleClickListener;
import by.naxa.soundrecorder.util.EventBroadcaster;
import by.naxa.soundrecorder.util.Paths;
import by.naxa.soundrecorder.util.TimeUtils;
import io.fabric.sdk.android.Fabric;

/**
 * Created by Daniel on 12/29/2014.
 */
public class FileViewerAdapter extends RecyclerView.Adapter<FileViewerAdapter.RecordingsViewHolder>
        implements OnDatabaseChangedListener {

    private static final String LOG_TAG = "FileViewerAdapter";

    private DBHelper mDatabase;

    private Context mContext;
    private final LinearLayoutManager llm;

    public FileViewerAdapter(Context context, LinearLayoutManager linearLayoutManager) {
        super();
        mContext = context;
        mDatabase = new DBHelper(mContext);
        DBHelper.setOnDatabaseChangedListener(this);
        llm = linearLayoutManager;
    }

    @Override
    public void onBindViewHolder(@NonNull final RecordingsViewHolder holder, int position) {

        RecordingItem item = getItem(position);
        long itemDuration = item.getLength();

        holder.vName.setText(item.getName());
        holder.vLength.setText(TimeUtils.formatDuration(itemDuration));
        holder.vDateAdded.setText(
                DateUtils.formatDateTime(
                        mContext,
                        item.getTime(),
                        DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR
                )
        );

        // define an on click listener to expand card
        holder.cardView.setOnClickListener(new OnSingleClickListener() {
            @Override
            public void onSingleClick(View v) {
                if (holder.isPopupVisible) {
                    holder.isPopupVisible = false;
                    holder.mSeekBar.setVisibility(View.GONE);
                    holder.mCurrentProgressTextView.setVisibility(View.GONE);
                    holder.mFileLengthTextView.setVisibility(View.GONE);
                    holder.mPlayButton.hide();
                } else {
                    holder.isPopupVisible = true;
                    holder.mSeekBar.setVisibility(View.VISIBLE);
                    holder.mCurrentProgressTextView.setVisibility(View.VISIBLE);
                    holder.mFileLengthTextView.setVisibility(View.VISIBLE);
                    holder.mPlayButton.show();
                }

                // rotate Expand icon
                final ObjectAnimator anim = ObjectAnimator.ofFloat(
                        holder.imageViewExpand, "rotation", holder.rotationAngle, holder.rotationAngle + 180);
                anim.setDuration(500);
                anim.start();
                holder.rotationAngle = (holder.rotationAngle + 180) % 360;
            }
        });

        holder.cardView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {

                final ArrayList<String> entries = new ArrayList<>();
                entries.add(mContext.getString(R.string.dialog_file_share));
                entries.add(mContext.getString(R.string.dialog_file_rename));
                entries.add(mContext.getString(R.string.dialog_file_delete));

                final CharSequence[] items = entries.toArray(new CharSequence[entries.size()]);


                // File delete confirm
                AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                builder.setTitle(mContext.getString(R.string.dialog_title_options));
                builder.setItems(items, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int item) {
                        if (item == 0) {
                            shareFileDialog(holder.getPosition());
                        } else if (item == 1) {
                            renameFileDialog(holder.getPosition());
                        } else if (item == 2) {
                            deleteFileDialog(holder.getPosition());
                        }
                    }
                });
                builder.setCancelable(true);
                builder.setNegativeButton(mContext.getString(R.string.dialog_action_cancel),
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        });

                AlertDialog alert = builder.create();
                alert.show();

                return false;
            }
        });
    }

    @Override
    @NonNull
    public RecordingsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {

        View itemView = LayoutInflater.
                from(parent.getContext()).
                inflate(R.layout.card_view, parent, false);

        mContext = parent.getContext();

        return new RecordingsViewHolder(itemView);
    }

    static class RecordingsViewHolder extends RecyclerView.ViewHolder {
        final TextView vName;
        final TextView vLength;
        final TextView vDateAdded;
        final View cardView;
        final ImageView imageViewExpand;
        final SeekBar mSeekBar;
        final FloatingActionButton mPlayButton;
        final TextView mCurrentProgressTextView;
        final TextView mFileLengthTextView;

        boolean isPopupVisible = false;
        int rotationAngle = 0;

        RecordingsViewHolder(View v) {
            super(v);
            vName = v.findViewById(R.id.file_name_text);
            vLength = v.findViewById(R.id.file_length_text);
            vDateAdded = v.findViewById(R.id.file_date_added_text);
            cardView = v.findViewById(R.id.card_view);
            imageViewExpand = v.findViewById(R.id.expand);
            mSeekBar = v.findViewById(R.id.seekbar);
            mPlayButton = v.findViewById(R.id.fab_play);
            mFileLengthTextView = v.findViewById(R.id.file_length_text_view);
            mCurrentProgressTextView = v.findViewById(R.id.current_progress_text_view);
        }
    }

    @Override
    public int getItemCount() {
        return mDatabase.getCount();
    }

    public RecordingItem getItem(int position) {
        return mDatabase.getItemAt(position);
    }

    @Override
    public void onNewDatabaseEntryAdded() {
        //item added to top of the list
        notifyItemInserted(getItemCount() - 1);
        llm.scrollToPosition(getItemCount() - 1);
    }

    @Override
    //TODO
    public void onDatabaseEntryRenamed() {

    }

    public void remove(int position) {
        //remove item from database, recyclerview and storage

        //delete file from storage
        File file = new File(getItem(position).getFilePath());
        if (!file.delete()) {
            Toast.makeText(mContext,
                    String.format(mContext.getString(R.string.toast_file_delete_failed),
                            getItem(position).getName()),
                    Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(
                mContext,
                String.format(
                        mContext.getString(R.string.toast_file_delete),
                        getItem(position).getName()
                ),
                Toast.LENGTH_SHORT
        ).show();

        mDatabase.removeItemWithId(getItem(position).getId());
        notifyItemRemoved(position);
    }

    //TODO
    public void removeOutOfApp(String filePath) {
        //user deletes a saved recording out of the application through another application
    }

    /**
     * rename a file
     */
    public void rename(int position, String name) {
        final String mFilePath = Paths.combine(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                Paths.SOUND_RECORDER_FOLDER, name);
        final File f = new File(mFilePath);

        if (f.exists() && !f.isDirectory()) {
            //file name is not unique, cannot rename file.
            Toast.makeText(mContext,
                    String.format(mContext.getString(R.string.toast_file_exists), name),
                    Toast.LENGTH_LONG).show();
        } else {
            //file name is unique, rename file
            File oldFilePath = new File(getItem(position).getFilePath());
            if (!oldFilePath.renameTo(f)) {
                Toast.makeText(mContext,
                        String.format(mContext.getString(R.string.toast_file_rename_failed), name),
                        Toast.LENGTH_LONG).show();
                return;
            }
            mDatabase.renameItem(getItem(position), name, mFilePath);
            notifyItemChanged(position);
        }
    }

    private void shareFileDialog(int position) {
        Intent shareIntent = new Intent();
        shareIntent.setAction(Intent.ACTION_SEND);
        final Uri uri = FileProvider.getUriForFile(mContext,
                BuildConfig.APPLICATION_ID + ".fileprovider",
                new File(getItem(position).getFilePath()));
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.setType("audio/mp4");
        mContext.startActivity(Intent.createChooser(shareIntent, mContext.getText(R.string.send_to)));
    }

    private void renameFileDialog(final int position) {
        // File rename dialog
        AlertDialog.Builder renameFileBuilder = new AlertDialog.Builder(mContext);

        LayoutInflater inflater = LayoutInflater.from(mContext);
        View view = inflater.inflate(R.layout.dialog_rename_file, null);

        final TextInputEditText input = view.findViewById(R.id.new_name);

        renameFileBuilder.setTitle(mContext.getString(R.string.dialog_title_rename));
        renameFileBuilder.setCancelable(true);
        renameFileBuilder.setPositiveButton(mContext.getString(R.string.dialog_action_ok),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        try {
                            final Editable editable = input.getText();
                            if (editable == null)
                                return;
                            final String value = editable.toString().trim() + ".mp4";
                            rename(position, value);
                        } catch (Exception e) {
                            if (Fabric.isInitialized()) Crashlytics.logException(e);
                            Log.e(LOG_TAG, "exception", e);
                            EventBroadcaster.send(mContext, mContext.getString(R.string.error_rename_file));
                        }

                        dialog.cancel();
                    }
                });
        renameFileBuilder.setNegativeButton(mContext.getString(R.string.dialog_action_cancel),
                new CancelDialogListener());

        renameFileBuilder.setView(view);
        AlertDialog alert = renameFileBuilder.create();
        final Window window = alert.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        alert.show();
    }

    private void deleteFileDialog(final int position) {
        // File delete confirm
        AlertDialog.Builder confirmDelete = new AlertDialog.Builder(mContext);
        confirmDelete.setTitle(mContext.getString(R.string.dialog_title_delete));
        confirmDelete.setMessage(mContext.getString(R.string.dialog_text_delete));
        confirmDelete.setCancelable(true);
        confirmDelete.setPositiveButton(mContext.getString(R.string.dialog_action_yes_delete),
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        try {
                            //remove item from database, recyclerview, and storage
                            remove(position);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "exception", e);
                        }

                        dialog.cancel();
                    }
                });
        confirmDelete.setNegativeButton(mContext.getString(R.string.dialog_action_no),
                new CancelDialogListener());

        AlertDialog alert = confirmDelete.create();
        alert.show();
    }

    static final class CancelDialogListener implements DialogInterface.OnClickListener {
        @Override
        public void onClick(DialogInterface dialog, int id) {
            dialog.cancel();
        }
    }
}
