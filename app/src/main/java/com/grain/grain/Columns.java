package com.grain.grain;

import android.provider.BaseColumns;

public interface Columns extends BaseColumns {
    String TABLE_NAME = "grain";
    String COLUMN_NAME_ORIGINAL = "original";
    String COLUMN_NAME_SAMPLE = "sample";
    String COLUMN_NAME_TIME_START = "time_start";
    String COLUMN_NAME_TIME_END = "time_end";
    String COLUMN_NAME_SSIM = "SSIM";
    String COLUMN_NAME_DELETED = "deleted";
}
