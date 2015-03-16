package bander.notepad;

import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import bander.provider.Note;

public class NoteImport extends Activity {
	private static final String[] PROJECTION = new String[] { 
		Note._ID, Note.TITLE, Note.BODY
	};
	private static final int COLUMN_ID = 0;

	private static final int DIALOG_IMPORT = 1;

	private TextView mProgressLabel;
	private ProgressBar mProgressBar;
	private Button mActionButton;

	private ImportTask mImportTask;

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
		if (action.equals("bander.notepad.action.ACTION_IMPORT")) {
			showDialog(DIALOG_IMPORT);
		}
	}
	
    @Override
    protected void onPause() {
        super.onPause();
        cancelImport();
    }
    	
	@Override
	protected Dialog onCreateDialog(int id) {
	    Dialog dialog;
	    switch(id) {
		    case DIALOG_IMPORT:
		        dialog = new AlertDialog.Builder(this)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setTitle(R.string.dialog_import)
					.setMessage(R.string.import_confirmation)
					.setPositiveButton(getString(R.string.dialog_confirm),
						new DialogInterface.OnClickListener() {					
							// OnClickListener
							public void onClick(DialogInterface dialog, int which) {
								startImport();
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

	private void startImport() {
		if (mImportTask == null || mImportTask.getStatus() == AsyncTask.Status.FINISHED) {
			mActionButton.setVisibility(View.VISIBLE);
			mActionButton.setText(R.string.dialog_cancel);
			mActionButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					cancelImport();
				}
			});
			mProgressBar.setVisibility(View.VISIBLE);
			mImportTask = (ImportTask) new ImportTask().execute();
		}
	}
	private void cancelImport() {
		if (mImportTask != null && mImportTask.getStatus() == AsyncTask.Status.RUNNING) {
			mImportTask.cancel(true);
			mImportTask = null;
			mActionButton.setVisibility(View.VISIBLE);
			mActionButton.setText(R.string.dialog_back);
			mActionButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					finish();
				}
			});
        }
	}
	
	private class ImportTask extends AsyncTask<Void, Integer, Integer> {
		private final File mDirectory;
		
		ImportTask() {
			File root = Environment.getExternalStorageDirectory();
			mDirectory = new File(root.getAbsolutePath() + "/notepad");
		}

		@Override
		public void onPreExecute() {
			mProgressLabel.setText(R.string.import_progress);
			mProgressBar.setProgress(0);
		}

		public Integer doInBackground(Void... params) {
			int imported = 0;
			try {
				mDirectory.mkdirs();
				
				File[] files = mDirectory.listFiles(new FileFilter() {
					public boolean accept(File file) {
						return (file.isFile() && file.canRead() && file.getName().endsWith(".txt"));
					}
				});
				
				final int count = files.length;
				for (int i = 0; i < count; i++) {
					publishProgress(i, count);
					if (isCancelled()) return null;
					
					File file = files[i];
					
					String title = file.getName();
					title = title.substring(0, title.length()-4); 	// strip off ".txt"
					
					char[] buffer = new char[(int) file.length()];
					FileReader reader = new FileReader(file);
					reader.read(buffer);
					reader.close();
					final String body = new String(buffer);

					ContentValues values = new ContentValues();
					values.put(Note.TITLE, title);
					values.put(Note.BODY, body);
					
					Cursor cursor = managedQuery(
						Note.CONTENT_URI, PROJECTION, Note.TITLE + "=?", new String[] { title }, Note.DEFAULT_SORT_ORDER
					);
					
					if ((cursor.getCount() == 1) && cursor.moveToFirst()) {
						long id = cursor.getLong(COLUMN_ID);
						Uri uri = ContentUris.withAppendedId(Note.CONTENT_URI, id);
						getContentResolver().update(uri, values, null, null);
					} else {
						getContentResolver().insert(Note.CONTENT_URI, values);
					}
					cursor.close();
					
					imported++;
				}

				publishProgress(count, count);
			} catch (IOException e) {
				return null;
			}
			return imported;
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
		public void onPostExecute(Integer importCount) {
			if (importCount == null) {
				mProgressLabel.setText(R.string.import_failed);
			} else {
				mProgressLabel.setText(
					getResources().getQuantityString(
						R.plurals.import_done, importCount, 
						new Object[] { importCount, mDirectory.getAbsolutePath() }
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
