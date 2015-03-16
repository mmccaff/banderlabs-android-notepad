package bander.notepad;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import bander.provider.Note;

public class NoteExport extends Activity {
	private static final String[] PROJECTION = new String[] { 
		Note._ID, Note.TITLE, Note.BODY
	};
	private static final int COLUMN_TITLE = 1;
	private static final int COLUMN_BODY = 2;

	private static final int DIALOG_EXPORT = 1;

	private TextView mProgressLabel;
	private ProgressBar mProgressBar;
	private Button mActionButton;

	private ExportTask mExportTask;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.export);

		mProgressLabel = (TextView) findViewById(R.id.progress_label);
		mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
		mActionButton = (Button) findViewById(R.id.action);
	}

	@Override
	protected void onResume() {
		super.onResume();

		mProgressBar.setVisibility(View.GONE);

		if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			mProgressLabel.setText(R.string.error_nomedia);
			mActionButton.setVisibility(View.VISIBLE);
			mActionButton.setText(R.string.dialog_back);
			mActionButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					finish();
				}
			});
			return;
		}
		mProgressLabel.setText("");
		mActionButton.setVisibility(View.GONE);
		
		final String action = getIntent().getAction();
		if (action.equals("bander.notepad.action.ACTION_EXPORT")) {
			showDialog(DIALOG_EXPORT);
		}
	}
	
    @Override
    protected void onPause() {
        super.onPause();
        cancelExport();
    }
    	
	@Override
	protected Dialog onCreateDialog(int id) {
	    Dialog dialog;
	    switch(id) {
		    case DIALOG_EXPORT:
		        dialog = new AlertDialog.Builder(this)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(R.string.dialog_export)
					.setMessage(R.string.export_confirmation)
					.setPositiveButton(getString(R.string.dialog_confirm),
						new DialogInterface.OnClickListener() {					
							// OnClickListener
							public void onClick(DialogInterface dialog, int which) {
								startExport();
							}
						}
					)
					.setNegativeButton(getString(R.string.dialog_cancel),
						new DialogInterface.OnClickListener() {					
							// OnClickListener
							public void onClick(DialogInterface dialog, int which) {
								finish();
							}
						}
					)
					.create();
		        break;
		    default:
		        dialog = null;
		    }
	    return dialog;
	}

	private void startExport() {
		if (mExportTask == null || mExportTask.getStatus() == AsyncTask.Status.FINISHED) {
			mActionButton.setVisibility(View.VISIBLE);
			mActionButton.setText(R.string.dialog_cancel);
			mActionButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					cancelExport();
				}
			});
			mProgressBar.setVisibility(View.VISIBLE);
			mExportTask = (ExportTask) new ExportTask().execute();
		}
	}
	private void cancelExport() {
		if (mExportTask != null && mExportTask.getStatus() == AsyncTask.Status.RUNNING) {
			mExportTask.cancel(true);
			mExportTask = null;
			mActionButton.setVisibility(View.VISIBLE);
			mActionButton.setText(R.string.dialog_back);
			mActionButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					finish();
				}
			});
        }
	}
	
	private class ExportTask extends AsyncTask<Void, Integer, Integer> {
		private final File mDirectory;
		
		ExportTask() {
			File root = Environment.getExternalStorageDirectory();
			mDirectory = new File(root.getAbsolutePath() + "/notepad");
		}

		@Override
		public void onPreExecute() {
			mProgressLabel.setText(R.string.export_progress);
			mProgressBar.setProgress(0);
		}

		public Integer doInBackground(Void... params) {
			int exported = 0;
			try {
				mDirectory.mkdirs();
				
				Cursor cursor = managedQuery(Note.CONTENT_URI, PROJECTION, null, null, Note.DEFAULT_SORT_ORDER);
				final int count = cursor.getCount();
				
				cursor.moveToFirst();
				
				for (int i = 0; i < count; i++) {
					publishProgress(i, count);
					if (isCancelled()) return null;
				
					cursor.moveToPosition(i);
					String title = cursor.getString(COLUMN_TITLE);
					title = title.replaceAll("[^\\w ]", "");
					File file = new File(
						mDirectory.getAbsolutePath() + File.separator + title + ".txt"
					);
					file.createNewFile();
					
					FileWriter writer = new FileWriter(file);
					writer.write(cursor.getString(COLUMN_BODY));
					writer.flush();
					writer.close();
					
					exported++;
				}
				publishProgress(count, count);
			}  catch (IOException e) {
				return null;
			}
			return exported;
		}

		@Override
		public void onProgressUpdate(Integer... values) {
			final ProgressBar progress = mProgressBar;
			progress.setMax(values[1]);
			progress.setProgress(values[0]);
		}

		@Override
		public void onCancelled() {
			//
		}

		@Override
		public void onPostExecute(Integer backupCount) {
			if (backupCount == null) {
				mProgressLabel.setText(R.string.export_failed);
			} else {
				mProgressLabel.setText(
					getResources().getQuantityString(
						R.plurals.export_done, backupCount, 
						new Object[] { backupCount, mDirectory.getAbsolutePath() }
					)
				);
			}
			mActionButton.setVisibility(View.VISIBLE);
			mActionButton.setText(R.string.dialog_back);
			mActionButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					finish();
				}
			});

		}
	}

}
