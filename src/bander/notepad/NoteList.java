package bander.notepad;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Toast;
import bander.provider.Note;

/** Main activity for Notepad, shows a list of notes. */
public class NoteList extends ListActivity {
	public static final int INSERT_ID 	= Menu.FIRST + 0;
	public static final int SEARCH_ID 	= Menu.FIRST + 1;
	public static final int PREFS_ID 	= Menu.FIRST + 2;

	public static final int DELETE_ID 	= Menu.FIRST + 3;
	public static final int SEND_ID 	= Menu.FIRST + 4;

	private static final String[] PROJECTION = new String[] { 
		Note._ID, Note.TITLE 
	};

	private static final int COLUMN_INDEX_ID 		= 0;
	private static final int COLUMN_INDEX_TITLE 	= 1;
	
	private static boolean GIVEN_HINT				= false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.list);

		Intent intent = getIntent();
		if (intent.getData() == null) {
			intent.setData(Note.CONTENT_URI);
		}

		registerForContextMenu(getListView());
	}

	@Override
	public void onResume() {
		super.onResume();

		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		boolean largeListItems = preferences.getBoolean("listItemSize", true);

		int sortOrder = Integer.valueOf(preferences.getString("sortOrder", "1"));
		boolean sortAscending = preferences.getBoolean("sortAscending", true);
		String sorting = Note.SORT_ORDERS[sortOrder] + ((sortAscending ? " ASC" : " DESC"));

		Cursor cursor = managedQuery(getIntent().getData(), PROJECTION, null, null, sorting);
		SimpleCursorAdapter adapter = new SimpleCursorAdapter(this,
			(largeListItems) ? R.layout.row_large : R.layout.row_small,
			cursor, 
			new String[] { Note.TITLE }, new int[] { android.R.id.text1 }
		);
		setListAdapter(adapter);
		
		if ((GIVEN_HINT == false) && (adapter.getCount() == 1)) {
			Toast.makeText(this, R.string.hint_longpress, Toast.LENGTH_LONG).show();
			GIVEN_HINT = true;
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		menu.add(0, INSERT_ID, 0, R.string.menu_insert)
			.setIcon(android.R.drawable.ic_menu_add);

		menu.add(0, SEARCH_ID, 0, R.string.menu_search)
			.setIcon(android.R.drawable.ic_menu_search);

		menu.add(0, PREFS_ID, 0, R.string.menu_prefs)
			.setIcon(android.R.drawable.ic_menu_preferences);
/*
		Intent intent = new Intent(null, getIntent().getData());
		intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
		menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0, new
		ComponentName(this, NoteList.class), null, intent, 0, null);
*/
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case INSERT_ID:
				startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData()));
				return true;
			case SEARCH_ID:
				onSearchRequested();
				return true;
			case PREFS_ID:
				Intent prefsActivity = new Intent(this, Preferences.class);
				startActivity(prefsActivity);
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		AdapterView.AdapterContextMenuInfo info;
		try {
			info = (AdapterView.AdapterContextMenuInfo) menuInfo;
		} catch (ClassCastException e) {
			return;
		}

		Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
		if (cursor == null) {
			return;
		}

		menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

		Uri uri = ContentUris.withAppendedId(getIntent().getData(), cursor.getInt(COLUMN_INDEX_ID));

		Intent[] specifics = new Intent[1];
		specifics[0] = new Intent(Intent.ACTION_EDIT, uri);
		MenuItem[] items = new MenuItem[1];

		Intent intent = new Intent(null, uri);
		intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
		menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0, null, specifics, intent, 0, items);

		menu.add(0, DELETE_ID, 0, R.string.menu_delete);
		menu.add(0, SEND_ID, 0, R.string.menu_send);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		AdapterView.AdapterContextMenuInfo info;
		try {
			info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		} catch (ClassCastException e) {
			return false;
		}
		switch (item.getItemId()) {
			case DELETE_ID:
				deleteNote(this, info.id);
				return true;
			case SEND_ID:
				Uri uri = ContentUris.withAppendedId(Note.CONTENT_URI, info.id);
				Cursor cursor = managedQuery(
					uri, new String[] { Note._ID, Note.TITLE, Note.BODY }, null, null, null
				);
				Note note = Note.fromCursor(cursor);
				cursor.close();

				Intent intent = new Intent(Intent.ACTION_SEND);
				intent.setType("text/plain");
				intent.putExtra(Intent.EXTRA_TEXT, note.getBody());
				startActivity(Intent.createChooser(intent, getString(R.string.menu_send)));
				return true;
		}
		return false;
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);

		String action = getIntent().getAction();
		if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
			setResult(RESULT_OK, new Intent().setData(uri));
		} else {
			startActivity(new Intent(Intent.ACTION_EDIT, uri));
		}
	}

	/** Delete a note, confirm when preferred.
	 * @param context Context to use.
	 * @param id ID of the note to delete.
	 */
	private void deleteNote(Context context, long id) {
		final long noteId = id;
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
		boolean deleteConfirmation = preferences.getBoolean("deleteConfirmation", true);
		if (deleteConfirmation) {
			AlertDialog alertDialog = new AlertDialog.Builder(context)
				.setIcon(android.R.drawable.ic_dialog_alert)
				.setTitle(R.string.dialog_delete)
				.setMessage(R.string.delete_confirmation)
				.setPositiveButton(R.string.dialog_confirm,
					new DialogInterface.OnClickListener() {
						// OnClickListener
						public void onClick(DialogInterface dialog, int which) {
							Uri noteUri = ContentUris.withAppendedId(Note.CONTENT_URI, noteId);
							getContentResolver().delete(noteUri, null, null);
						}
					}
				)
				.setNegativeButton(R.string.dialog_cancel, null)
				.create();
			alertDialog.show();
		} else {
			Uri noteUri = ContentUris.withAppendedId(Note.CONTENT_URI, noteId);
			getContentResolver().delete(noteUri, null, null);
		}
	}

}
