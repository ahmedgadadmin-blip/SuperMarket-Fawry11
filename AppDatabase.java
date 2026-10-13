package com.example.supermarketfawry;

import android.content.*;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

public class AppDatabase extends SQLiteOpenHelper {
    public AppDatabase(Context c, String key){
        super(c,"shop_accounts.db",null,1);
        SQLiteDatabase.loadLibs(c);
        setKey(key);
    }
    public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, pass_hash TEXT, salt TEXT, role TEXT, can_sales INTEGER, can_accessories INTEGER, can_fawry INTEGER, can_reports INTEGER, can_backup INTEGER, created_at TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sales(id INTEGER PRIMARY KEY AUTOINCREMENT, product TEXT, price REAL, qty INTEGER, created_at TEXT, username TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS accessories(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, cost REAL, sell REAL, created_at TEXT, username TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS fawry(id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, amount REAL, fee REAL, created_at TEXT, username TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS expenses(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, amount REAL, created_at TEXT, username TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS customers(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE, phone TEXT, address TEXT, created_at TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS debts(id INTEGER PRIMARY KEY AUTOINCREMENT, customer_id INTEGER, type TEXT, amount REAL, note TEXT, created_at TEXT, username TEXT)");
    }
    public void onUpgrade(SQLiteDatabase db,int oldV,int newV){ onCreate(db); }
}