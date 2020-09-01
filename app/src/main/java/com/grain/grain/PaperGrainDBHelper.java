package com.grain.grain;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import static com.grain.grain.Columns.*;

public class PaperGrainDBHelper extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "PaperGrain.db";
    public static final Integer DATABASE_VERSION = 1;

    private static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + TABLE_NAME + " (" +
                    _ID + " INTEGER PRIMARY KEY," +
                    COLUMN_NAME_ORIGINAL + " TEXT," +
                    COLUMN_NAME_SAMPLE + " TEXT," +
                    COLUMN_NAME_TIME_START + " DATE," +
                    COLUMN_NAME_TIME_END + "DATA," +
                    COLUMN_NAME_SSIM + " TEXT," +
                    COLUMN_NAME_DELETED + " BOOLEAN)";

    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + TABLE_NAME;

    public PaperGrainDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 参数说明： 
        // db ： 数据库 
        // oldVersion ： 旧版本数据库 
        // newVersion ： 新版本数据库 

        // 使用 SQL的ALTER语句
        db.execSQL(SQL_DELETE_ENTRIES);
        onCreate(db);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.i("DBCreate", "Data Base Created.");
        // 创建数据库1张表
        // 通过execSQL()执行SQL语句(此处创建了1个名为MatchingRecord的表)
        db.execSQL(SQL_CREATE_ENTRIES);

        // 注：数据库实际上是没被创建 / 打开的(因该方法还没调用)
        // 直到getWritableDatabase() / getReadableDatabase() 第一次被调用时才会进行创建 / 打开 
    }

    public static class GrainEntry implements Columns {
    }
}

