package bander.notepad;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import bander.provider.Note;

/** Secondary activity for Notepad, shows title of a single note and allows editing it. */
public class TitleEdit extends Activity {
	public static final String EDIT_TITLE_ACTION = "bander.notepad.action.EDIT_TITLE";

	private static final int REVERT_ID 		= Menu.FIRST + 0;
	private static final int PREFS_ID 		= Menu.FIRST + 1;

	private static final String[] PROJECTION = new String[] { 
		Note._ID, Note.TITLE
	};

	private static final int COLUMN_INDEX_TITLE = 1;

	private static final String ORIGINAL_TITLE = "originalTitle";

	private EditText mTitleText;

	private Uri mUri;
	private Cursor mCursor;
	private String mOriginalTitle;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState != null) {
			mOriginalTitle = savedInstanceState.getString(ORIGINAL_TITLE);
		}

		setContentView(R.layout.edit_title);

		mUri = getIntent().getData();
		mCursor = managedQuery(mUri, PROJECTION, null, null, null);

		mTitleText = (EditText) this.findViewById(R.id.title);

		Button confirmButton = (Button) findViewById(R.id.confirm);
		Button cancelButton = (Button) findViewById(R.id.cancel);

		confirmButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				finish();
			}
		});
		cancelButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View view) {
				cancelEdit();
			}
		});
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(ORIGINAL_TITLE, mOriginalTitle);
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (mCursor != null) {
			mCursor.moveToFirst();
			String title = mCursor.getString(COLUMN_INDEX_TITLE);
			mTitleText.setTextKeepState(title);

			if (mOriginalTitle == null) {
				mOriginalTitle = title;
			}
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		if (mCursor != null) {
			ContentValues values = new ContentValues();
			values.put(Note.TITLE, mTitleText.getText().toString());
			getContentResolver().update(mUri, values, null, null);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		boolean result = super.onCreateOptionsMenu(menu);

		menu.add(0, REVERT_ID, 0, R.string.menu_revert)
			.setIcon(android.R.drawable.ic_menu_revert);

		menu.add(0, PREFS_ID, 0, R.string.menu_prefs)
			.setIcon(android.R.drawable.ic_menu_preferences);

		return result;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case REVERT_ID:
				mTitleText.setTextKeepState(mOriginalTitle);
				return true;
			case PREFS_ID:
				Intent prefsActivity = new Intent(this, Preferences.class);
				startActivity(prefsActivity);
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/** Cancels the current edit, finishes the activity. */
	private final void cancelEdit() {
		if (mCursor != null) {
			mCursor.close();
			mCursor = null;
			ContentValues values = new ContentValues();
			values.put(Note.TITLE, mOriginalTitle);
			getContentResolver().update(mUri, values, null, null);
		}
		setResult(RESULT_CANCELED);
		finish();
	}

}
