package com.grain.grain.io;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import static com.grain.grain.io.Columns.COLUMN_NAME_DELETED;
import static com.grain.grain.io.Columns.COLUMN_NAME_FINISHED;
import static com.grain.grain.io.Columns.COLUMN_NAME_ORIGINAL;
import static com.grain.grain.io.Columns.COLUMN_NAME_ORIGINAL_0;
import static com.grain.grain.io.Columns.COLUMN_NAME_ORIGINAL_1;
import static com.grain.grain.io.Columns.COLUMN_NAME_ORIGINAL_2;
import static com.grain.grain.io.Columns.COLUMN_NAME_ORIGINAL_3;
import static com.grain.grain.io.Columns.COLUMN_NAME_ORIGINAL_4;
import static com.grain.grain.io.Columns.COLUMN_NAME_ORIGINAL_5;
import static com.grain.grain.io.Columns.COLUMN_NAME_ORIGINAL_6;
import static com.grain.grain.io.Columns.COLUMN_NAME_ORIGINAL_7;
import static com.grain.grain.io.Columns.COLUMN_NAME_ORIGINAL_8;
import static com.grain.grain.io.Columns.COLUMN_NAME_ORIGINAL_9;
import static com.grain.grain.io.Columns.COLUMN_NAME_SAMPLE;
import static com.grain.grain.io.Columns.COLUMN_NAME_SAMPLE_0;
import static com.grain.grain.io.Columns.COLUMN_NAME_SAMPLE_1;
import static com.grain.grain.io.Columns.COLUMN_NAME_SAMPLE_2;
import static com.grain.grain.io.Columns.COLUMN_NAME_SAMPLE_3;
import static com.grain.grain.io.Columns.COLUMN_NAME_SAMPLE_4;
import static com.grain.grain.io.Columns.COLUMN_NAME_SAMPLE_5;
import static com.grain.grain.io.Columns.COLUMN_NAME_SAMPLE_6;
import static com.grain.grain.io.Columns.COLUMN_NAME_SAMPLE_7;
import static com.grain.grain.io.Columns.COLUMN_NAME_SAMPLE_8;
import static com.grain.grain.io.Columns.COLUMN_NAME_SAMPLE_9;
import static com.grain.grain.io.Columns.COLUMN_NAME_SSIM_0;
import static com.grain.grain.io.Columns.COLUMN_NAME_SSIM_1;
import static com.grain.grain.io.Columns.COLUMN_NAME_SSIM_2;
import static com.grain.grain.io.Columns.COLUMN_NAME_SSIM_3;
import static com.grain.grain.io.Columns.COLUMN_NAME_SSIM_4;
import static com.grain.grain.io.Columns.COLUMN_NAME_SSIM_5;
import static com.grain.grain.io.Columns.COLUMN_NAME_SSIM_6;
import static com.grain.grain.io.Columns.COLUMN_NAME_SSIM_7;
import static com.grain.grain.io.Columns.COLUMN_NAME_SSIM_8;
import static com.grain.grain.io.Columns.COLUMN_NAME_SSIM_9;
import static com.grain.grain.io.Columns.COLUMN_NAME_SURF_0;
import static com.grain.grain.io.Columns.COLUMN_NAME_SURF_1;
import static com.grain.grain.io.Columns.COLUMN_NAME_SURF_2;
import static com.grain.grain.io.Columns.COLUMN_NAME_SURF_3;
import static com.grain.grain.io.Columns.COLUMN_NAME_SURF_4;
import static com.grain.grain.io.Columns.COLUMN_NAME_SURF_5;
import static com.grain.grain.io.Columns.COLUMN_NAME_SURF_6;
import static com.grain.grain.io.Columns.COLUMN_NAME_SURF_7;
import static com.grain.grain.io.Columns.COLUMN_NAME_SURF_8;
import static com.grain.grain.io.Columns.COLUMN_NAME_SURF_9;
import static com.grain.grain.io.Columns.COLUMN_NAME_TIME_END;
import static com.grain.grain.io.Columns.COLUMN_NAME_TIME_START;
import static com.grain.grain.io.Columns.TABLE_NAME;
import static com.grain.grain.io.Columns._COUNT;
import static com.grain.grain.io.Columns._ID;

public class PaperGrainDBHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "PaperGrain.db";
    public static final Integer DATABASE_VERSION = 1;
    public static final String SQL_CREATE_ENTRIES =
            " CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                    _ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    _COUNT + " INTEGER," +
                    COLUMN_NAME_ORIGINAL + " TEXT," +
                    COLUMN_NAME_SAMPLE + " TEXT," +
                    COLUMN_NAME_TIME_START + " TEXT," +
                    COLUMN_NAME_TIME_END + " TEXT," +
                    COLUMN_NAME_SURF_0 + " TEXT," +
                    COLUMN_NAME_SURF_1 + " TEXT," +
                    COLUMN_NAME_SURF_2 + " TEXT," +
                    COLUMN_NAME_SURF_3 + " TEXT," +
                    COLUMN_NAME_SURF_4 + " TEXT," +
                    COLUMN_NAME_SURF_5 + " TEXT," +
                    COLUMN_NAME_SURF_6 + " TEXT," +
                    COLUMN_NAME_SURF_7 + " TEXT," +
                    COLUMN_NAME_SURF_8 + " TEXT," +
                    COLUMN_NAME_SURF_9 + " TEXT," +
                    COLUMN_NAME_ORIGINAL_0 + " TEXT," +
                    COLUMN_NAME_ORIGINAL_1 + " TEXT," +
                    COLUMN_NAME_ORIGINAL_2 + " TEXT," +
                    COLUMN_NAME_ORIGINAL_3 + " TEXT," +
                    COLUMN_NAME_ORIGINAL_4 + " TEXT," +
                    COLUMN_NAME_ORIGINAL_5 + " TEXT," +
                    COLUMN_NAME_ORIGINAL_6 + " TEXT," +
                    COLUMN_NAME_ORIGINAL_7 + " TEXT," +
                    COLUMN_NAME_ORIGINAL_8 + " TEXT," +
                    COLUMN_NAME_ORIGINAL_9 + " TEXT," +
                    COLUMN_NAME_SAMPLE_0 + " TEXT," +
                    COLUMN_NAME_SAMPLE_1 + " TEXT," +
                    COLUMN_NAME_SAMPLE_2 + " TEXT," +
                    COLUMN_NAME_SAMPLE_3 + " TEXT," +
                    COLUMN_NAME_SAMPLE_4 + " TEXT," +
                    COLUMN_NAME_SAMPLE_5 + " TEXT," +
                    COLUMN_NAME_SAMPLE_6 + " TEXT," +
                    COLUMN_NAME_SAMPLE_7 + " TEXT," +
                    COLUMN_NAME_SAMPLE_8 + " TEXT," +
                    COLUMN_NAME_SAMPLE_9 + " TEXT," +
                    COLUMN_NAME_SSIM_0 + " FLOAT," +
                    COLUMN_NAME_SSIM_1 + " FLOAT," +
                    COLUMN_NAME_SSIM_2 + " FLOAT," +
                    COLUMN_NAME_SSIM_3 + " FLOAT," +
                    COLUMN_NAME_SSIM_4 + " FLOAT," +
                    COLUMN_NAME_SSIM_5 + " FLOAT," +
                    COLUMN_NAME_SSIM_6 + " FLOAT," +
                    COLUMN_NAME_SSIM_7 + " FLOAT," +
                    COLUMN_NAME_SSIM_8 + " FLOAT," +
                    COLUMN_NAME_SSIM_9 + " FLOAT," +
                    COLUMN_NAME_FINISHED + " BOOLEAN," +
                    COLUMN_NAME_DELETED + " BOOLEAN)";

    private static final String SQL_DELETE_ENTRIES =
            " DROP TABLE IF EXISTS " + TABLE_NAME;

    public PaperGrainDBHelper(Context context) {
        super(
                context,
                context.getDatabasePath(DATABASE_NAME).getAbsolutePath(),
                null,
                DATABASE_VERSION
        );
    }

    public static void updateCount(SQLiteDatabase database) {
        String sql = "SELECT " +
                _ID + "," +
                _COUNT + "," +
                COLUMN_NAME_DELETED +
                " FROM " +
                TABLE_NAME +
                " WHERE " +
                COLUMN_NAME_DELETED + "=" + 0 +
                " ORDER BY " +
                _ID;
        Cursor cursor = database.rawQuery(sql, null);
        if (cursor.moveToFirst()) {
            int index = 1;
            do {
                String _count = " UPDATE " + TABLE_NAME +
                        " SET " + _COUNT + "=" + index +
                        " WHERE " + _ID + "=" + cursor.getInt(0);
                database.execSQL(_count);
                index++;
            } while (cursor.moveToNext());
        }
        cursor.close();
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(SQL_CREATE_ENTRIES);
    }
}

