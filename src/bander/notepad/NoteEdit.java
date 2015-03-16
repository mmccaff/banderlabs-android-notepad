package bander.notepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import bander.provider.Note;

/** Secondary activity for Notepad, shows details of a single note and allows editing it. */
public class NoteEdit extends Activity {
	private static final int REVERT_ID 		= Menu.FIRST + 0;
	private static final int DELETE_ID 		= Menu.FIRST + 1;
	private static final int SEND_ID 		= Menu.FIRST + 2;
	private static final int PREFS_ID 		= Menu.FIRST + 3;

	private static final int STATE_EDIT 	= 0;
	private static final int STATE_INSERT 	= 1;

	private static final String[] PROJECTION = new String[] { 
		Note._ID, Note.TITLE, Note.BODY, Note.CURSOR, Note.SCROLL_Y
	};

	private static final String ORIGINAL_NOTE = "originalNote";

	private int mState;
	private Uri mUri;

	private EditText mBodyText;
	private Note mOriginalNote;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null) {
			final Object note = savedInstanceState.get(ORIGINAL_NOTE);
			if (note != null) mOriginalNote = (Note) note;
		}

		final Intent intent = getIntent();
		final String action = intent.getAction();
		if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action)) {
			mState = STATE_EDIT;
			mUri = intent.getData();
		} else if (Intent.ACTION_INSERT.equals(action)) {
			mState = STATE_INSERT;
			if (mOriginalNote == null) {
				mUri = getContentResolver().insert(intent.getData(), null);
			} else {
				mUri = mOriginalNote.getUri();
			}

			setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));
		}

		if (mUri == null) {
			finish();
			return;
		}

		setContentView(R.layout.edit);

		mBodyText = (EditText) findViewById(R.id.body);

		Button confirmButton = (Button) findViewById(R.id.confirm);
		confirmButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				finish();
			}
		});
		Button cancelButton = (Button) findViewById(R.id.cancel);
		cancelButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				cancelNote();
			}
		});
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putParcelable(ORIGINAL_NOTE, mOriginalNote);
	}

	@Override
	protected void onResume() {
		super.onResume();

		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

		float textSize = Float.valueOf(preferences.getString("textSize", "16"));
		mBodyText.setTextSize(textSize);

		Cursor cursor = managedQuery(mUri, PROJECTION, null, null, null);
		Note note = Note.fromCursor(cursor);
		cursor.close();

		if (note != null) {
			if (mOriginalNote == null) mOriginalNote = note;
			mBodyText.setTextKeepState(note.getBody());
			
			Boolean rememberPosition = preferences.getBoolean("rememberPosition", true);
			if (rememberPosition == true) {
				mBodyText.setSelection(note.getCursor());
				mBodyText.scrollTo(0, note.getScrollY());
			}
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		if (mUri != null) {
			String bodyText = mBodyText.getText().toString();
			int length = bodyText.length();

			if ((mState == STATE_INSERT) && isFinishing() && (length == 0)) {
				// If inserting and finishing and no text then delete the note.
				setResult(RESULT_CANCELED);
				deleteNote();
			} else {
				ContentValues values = mOriginalNote.getContentValues();
				if (values.containsKey(Note._ID)) values.remove(Note._ID);

				if (mState == STATE_INSERT) {
					String[] lines = bodyText.split("[\n\\.]");
					String title = 
						(lines.length > 0) ? lines[0] : getString(android.R.string.untitled);
					if (title.length() > 30) {
						int lastSpace = title.lastIndexOf(' ');
						if (lastSpace > 0) {
							title = title.substring(0, lastSpace);
						}
					}
					values.put(Note.TITLE, title);
				}
				values.put(Note.BODY, bodyText);
				values.put(Note.CURSOR, mBodyText.getSelectionStart());
				values.put(Note.SCROLL_Y, mBodyText.getScrollY());

				getContentResolver().update(mUri, values, null, null);
			}
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);

		if (mState == STATE_EDIT) {
			menu.add(0, REVERT_ID, 0, R.string.menu_revert)
				.setIcon(android.R.drawable.ic_menu_revert);
			menu.add(0, DELETE_ID, 0, R.string.menu_delete)
				.setIcon(android.R.drawable.ic_menu_delete);
		}

		menu.add(0, SEND_ID, 0, R.string.menu_send)
			.setIcon(android.R.drawable.ic_menu_send);

		menu.add(0, PREFS_ID, 0, R.string.menu_prefs)
			.setIcon(android.R.drawable.ic_menu_preferences);

		return result;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case DELETE_ID:
				deleteNote(this, mUri);
				return true;
			case REVERT_ID:
				mBodyText.setTextKeepState(mOriginalNote.getBody());
				return true;
			case SEND_ID:
				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, mBodyText.getText().toString());
				startActivity(Intent.createChooser(intent, getString(R.string.menu_send)));
				return true;
			case PREFS_ID:
				Intent prefsActivity = new Intent(this, Preferences.class);
				startActivity(prefsActivity);
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/** Cancels the current edit, finishes the activity. */
	private final void cancelNote() {
		if (mUri != null) {
			if (mState == STATE_EDIT) {
				ContentValues values = mOriginalNote.getContentValues();
				getContentResolver().update(mUri, values, null, null);
				mUri = null;
			} else if (mState == STATE_INSERT) {
				// Empty note was inserted on startup, clean up.
				deleteNote();
			}
		}
		setResult(RESULT_CANCELED);
		finish();
	}

	/** Deletes the current note. */
	private final void deleteNote() {
		if (mUri != null) {
			getContentResolver().delete(mUri, null, null);
			mUri = null;
		}
	}

	/** Delete a note, confirm when preferred.
	 * @param context Context to use.
	 * @param id ID of the note to delete.
	 */
	private void deleteNote(Context context, Uri uri) {
		final Uri noteUri = uri;
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		Boolean deleteConfirmation = preferences.getBoolean("deleteConfirmation", true);
		if (deleteConfirmation) {
			AlertDialog alertDialog = new AlertDialog.Builder(context)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.dialog_delete)
				.setMessage(R.string.delete_confirmation)
				.setPositiveButton(R.string.dialog_confirm,
					new DialogInterface.OnClickListener() {
						// OnClickListener
						public void onClick(DialogInterface dialog, int which) {
							getContentResolver().delete(noteUri, null, null);
							finish();
						}
					})
				.setNegativeButton(R.string.dialog_cancel, null)
				.create();
			alertDialog.show();
		} else {
			getContentResolver().delete(noteUri, null, null);
			finish();
		}
	}

}
