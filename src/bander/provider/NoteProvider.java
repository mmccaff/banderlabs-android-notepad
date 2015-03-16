package bander.provider;

import java.util.HashMap;

import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

/** <code>ContentProvider</code> implementation for <code>bander.provider.Note</code> objects. */
public class NoteProvider extends ContentProvider {
	private static final String DATABASE_NAME = "notepad.db";
	private static final String DATABASE_TABLE = "notes";
	private static final int DATABASE_VERSION = 6;

	private static final int SEARCH 	= 1;
	private static final int NOTES 		= 2;
	private static final int NOTE_ID 	= 3;

	private static final UriMatcher sUriMatcher;
	static {
		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		sUriMatcher.addURI(Note.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, SEARCH);
		sUriMatcher.addURI(Note.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", SEARCH);
		sUriMatcher.addURI(Note.AUTHORITY, "notes", NOTES);
		sUriMatcher.addURI(Note.AUTHORITY, "notes/#", NOTE_ID);
	}
	
	private static HashMap<String, String> sNotesProjectionMap;
	static {
		sNotesProjectionMap = new HashMap<String, String>();
		sNotesProjectionMap.put(Note._ID, Note._ID);
		sNotesProjectionMap.put(Note.TITLE, Note.TITLE);
		sNotesProjectionMap.put(Note.BODY, Note.BODY);
		sNotesProjectionMap.put(Note.CREATED, Note.CREATED);
		sNotesProjectionMap.put(Note.CURSOR, Note.CURSOR);
		sNotesProjectionMap.put(Note.SCROLL_Y, Note.SCROLL_Y);
	}
	private static HashMap<String, String> sSuggestionProjectionMap;
	static {
		sSuggestionProjectionMap = new HashMap<String, String>();
		sSuggestionProjectionMap.put(SearchManager.SUGGEST_COLUMN_TEXT_1,
			Note.TITLE + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_1);
		sSuggestionProjectionMap.put(SearchManager.SUGGEST_COLUMN_TEXT_2,
			Note.BODY + " AS " + SearchManager.SUGGEST_COLUMN_TEXT_2);
		sSuggestionProjectionMap.put(SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID, 
			Note._ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID);
		sSuggestionProjectionMap.put(Note._ID, Note._ID);
	}

	/** Helper class to open, create, and upgrade the database file. */
	private static class DatabaseHelper extends SQLiteOpenHelper {

		DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL("CREATE TABLE " + DATABASE_TABLE + " (" 
				+ Note._ID + " INTEGER PRIMARY KEY," 
				+ Note.TITLE + " TEXT,"
				+ Note.BODY + " TEXT," 
				+ Note.CREATED + " INTEGER,"
				+ Note.CURSOR + " INTEGER,"			// v6 
				+ Note.SCROLL_Y + " INTEGER"		// v6 
				+ ");"
			);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			if ((oldVersion == 5) && (newVersion == 6)) {
				db.execSQL("ALTER TABLE " + DATABASE_TABLE + " " 
					+ "ADD COLUMN " + Note.CURSOR + ";"
				);
				db.execSQL("ALTER TABLE " + DATABASE_TABLE + " " 
					+ "ADD COLUMN " + Note.SCROLL_Y + ";"
				);
			}
		}
	}

	private DatabaseHelper mOpenHelper;

	@Override
	public boolean onCreate() {
		mOpenHelper = new DatabaseHelper(getContext());
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		qb.setTables(DATABASE_TABLE);

		switch (sUriMatcher.match(uri)) {
			case SEARCH:
				qb.setProjectionMap(sSuggestionProjectionMap);
				String query = uri.getLastPathSegment();
				if (!TextUtils.isEmpty(query)) {
					qb.appendWhere(Note.TITLE + " LIKE ");
					qb.appendWhereEscapeString('%' + query + '%');
					qb.appendWhere(" OR ");
					qb.appendWhere(Note.BODY + " LIKE ");
					qb.appendWhereEscapeString('%' + query + '%');
				}
				break;

			case NOTES:
				qb.setProjectionMap(sNotesProjectionMap);
				break;

			case NOTE_ID:
				qb.setProjectionMap(sNotesProjectionMap);
				qb.appendWhere(Note._ID + "=" + uri.getPathSegments().get(1));
				break;

			default:
				throw new IllegalArgumentException("Unsupported URI " + uri);
		}

		// If no sort order is specified use the default
		String orderBy;
		if (TextUtils.isEmpty(sortOrder)) {
			orderBy = Note.DEFAULT_SORT_ORDER;
		} else {
			orderBy = sortOrder;
		}

		// Get the database and run the query
		SQLiteDatabase db = mOpenHelper.getReadableDatabase();
		Cursor cursor = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);

		// Tell the cursor what uri to watch, so it knows when its source data changes
		cursor.setNotificationUri(getContext().getContentResolver(), uri);
		return cursor;
	}

	@Override
	public String getType(Uri uri) {
		switch (sUriMatcher.match(uri)) {
			case NOTES:
				return Note.CONTENT_TYPE;
			case NOTE_ID:
				return Note.CONTENT_ITEM_TYPE;
			default:
				throw new IllegalArgumentException("Unsupported URI " + uri);
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {
		// Validate the requested uri
		if (sUriMatcher.match(uri) != NOTES) {
			throw new IllegalArgumentException("Unsupported URI " + uri);
		}

		ContentValues values;
		if (initialValues != null) {
			values = new ContentValues(initialValues);
		} else {
			values = new ContentValues();
		}

		if (values.containsKey(Note.TITLE) == false) {
			Resources r = Resources.getSystem();
			values.put(Note.TITLE, r.getString(android.R.string.untitled));
		}

		if (values.containsKey(Note.BODY) == false) {
			values.put(Note.BODY, "");
		}

		if (values.containsKey(Note.CREATED) == false) {
			Long now = Long.valueOf(System.currentTimeMillis());
			values.put(Note.CREATED, now);
		}
		
		if (values.containsKey(Note.CURSOR) == false) {
			values.put(Note.CURSOR, 0L);
		}
		if (values.containsKey(Note.SCROLL_Y) == false) {
			values.put(Note.SCROLL_Y, 0L);
		}
		
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		long rowId = db.insert(DATABASE_TABLE, Note.BODY, values);
		if (rowId > 0) {
			Uri noteUri = ContentUris.withAppendedId(Note.CONTENT_URI, rowId);
			getContext().getContentResolver().notifyChange(noteUri, null);
			return noteUri;
		}

		throw new SQLException("Failed to insert row into " + uri);
	}

	@Override
	public int delete(Uri uri, String where, String[] whereArgs) {
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();
		int count;
		switch (sUriMatcher.match(uri)) {
			case NOTE_ID:
				String noteId = uri.getPathSegments().get(1);
				count = db.delete(DATABASE_TABLE, Note._ID + "=" + noteId
					+ (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs
				);
				break;

			default:
				throw new IllegalArgumentException("Unsupported URI " + uri);
		}

		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

	@Override
	public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
		SQLiteDatabase db = mOpenHelper.getWritableDatabase();

		int count;
		switch (sUriMatcher.match(uri)) {
			case NOTE_ID:
				String noteId = uri.getPathSegments().get(1);
				count = db.update(DATABASE_TABLE, values, Note._ID + "=" + noteId
					+ (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
				break;

			default:
				throw new IllegalArgumentException("Unsupported URI " + uri);
		}

		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

}
