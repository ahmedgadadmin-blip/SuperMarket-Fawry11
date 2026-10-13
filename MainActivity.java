package com.example.supermarketfawry;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import net.sqlcipher.database.SQLiteDatabase;
import android.content.SharedPreferences;
import android.util.Base64;
import android.content.pm.PackageManager;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import android.view.Window;
import android.view.WindowManager;
import java.util.concurrent.Executor;
import java.io.*;
import android.net.Uri;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.*;

public class MainActivity extends Activity {
    LinearLayout root, content;
    ArrayList<String> fawryOps = new ArrayList<>();
    double fawryTotal=0, fawryFees=0;
    AppDatabase dbHelper; SQLiteDatabase db; SharedPreferences prefs; String dbKey; long lastActivity; String currentUser=""; String currentRole="user"; static final long LOCK_MS=5*60*1000;
    String now(){ return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()); }

    TextView tv(String s, int size){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setPadding(24,20,24,20); return t; }
    Button btn(String s){ Button b=new Button(this); b.setText(s); return b; }

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("security",MODE_PRIVATE);
        if(!prefs.getBoolean("password_set",false)){ setupPassword(); } else { login(); }
    }
    String hash(String p, String salt){
        try{
            PBEKeySpec spec=new PBEKeySpec(p.toCharArray(),Base64.decode(salt,Base64.DEFAULT),120000,256);
            byte[] out=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.encodeToString(out,Base64.NO_WRAP);
        }catch(Exception e){throw new RuntimeException(e);}
    }
    String salt(){
        byte[] b=new byte[16]; new SecureRandom().nextBytes(b);
        return Base64.encodeToString(b,Base64.NO_WRAP);
    }
    boolean admin(){ return "admin".equals(currentRole); }
    boolean can(String p){
        if(admin()) return true;
        String[] a=prefs.getString("perms","").split(",");
        for(String x:a) if(x.equals(p)) return true;
        return false;
    }
    void showUserLogin(){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        EditText u=new EditText(this); u.setHint("اسم المستخدم"); box.addView(u);
        EditText p=new EditText(this); p.setHint("كلمة المرور"); p.setInputType(0x81); box.addView(p);
        new AlertDialog.Builder(this).setTitle("👤 دخول المستخدم").setView(box).setCancelable(false)
        .setPositiveButton("دخول",(d,w)->{
            String user=u.getText().toString().trim(), pass=p.getText().toString();
            if(user.equals(prefs.getString("master_user","")) && hash(pass,prefs.getString("salt","")).equals(prefs.getString("hash",""))){
                currentUser=user; currentRole="admin"; openDatabase(pass); return;
            }
            // Employee credentials are stored in encrypted DB, but DB requires master key first.
            Toast.makeText(this,"حساب الموظف سيتم تفعيله بعد فتح قاعدة البيانات بحساب المدير في النسخة الحالية.",Toast.LENGTH_LONG).show();
            login();
        }).show();
    }
    void addEmployee(){
        if(!admin()){ Toast.makeText(this,"للمدير فقط",Toast.LENGTH_SHORT).show(); return; }
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        EditText u=new EditText(this); u.setHint("اسم المستخدم"); box.addView(u);
        EditText p=new EditText(this); p.setHint("كلمة المرور"); p.setInputType(0x81); box.addView(p);
        EditText role=new EditText(this); role.setHint("الصلاحية: user"); box.addView(role);
        new AlertDialog.Builder(this).setTitle("➕ إضافة مستخدم").setView(box)
        .setPositiveButton("إضافة",(d,w)->{
            try{
                String un=u.getText().toString().trim(), pw=p.getText().toString(), sa=salt();
                android.content.ContentValues cv=new android.content.ContentValues();
                cv.put("username",un); cv.put("pass_hash",hash(pw,sa)); cv.put("salt",sa); cv.put("role",role.getText().toString().trim());
                cv.put("can_sales",1); cv.put("can_accessories",1); cv.put("can_fawry",1); cv.put("can_reports",0); cv.put("can_backup",0); cv.put("created_at",now());
                db.insertOrThrow("users",null,cv); Toast.makeText(this,"تمت إضافة المستخدم",Toast.LENGTH_SHORT).show();
            }catch(Exception e){ Toast.makeText(this,"اسم المستخدم موجود أو البيانات غير صحيحة",Toast.LENGTH_LONG).show(); }
        }).show();
    }

    void biometricLogin(){
        try{
            BiometricManager bm=BiometricManager.from(this);
            int can=bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG|BiometricManager.Authenticators.DEVICE_CREDENTIAL);
            if(can!=BiometricManager.BIOMETRIC_SUCCESS){ login(); return; }
            Executor ex=ContextCompat.getMainExecutor(this);
            BiometricPrompt bp=new BiometricPrompt(this,ex,new BiometricPrompt.AuthenticationCallback(){
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r){ openDatabase(prefs.getString("session_key","")); }
                public void onAuthenticationError(int e,CharSequence err){ login(); }
            });
            bp.authenticate(new BiometricPrompt.PromptInfo.Builder().setTitle("🔐 فتح حسابات المحل").setSubtitle("استخدم البصمة أو قفل الجهاز").setNegativeButtonText("استخدام كلمة المرور").build());
        }catch(Exception e){ login(); }
    }

    void setupPassword(){
        final EditText p=new EditText(this); p.setHint("كلمة مرور جديدة"); p.setInputType(0x81);
        new AlertDialog.Builder(this).setTitle("🔐 حماية التطبيق").setMessage("أنشئ كلمة مرور لحماية كل البيانات.")
        .setView(p).setCancelable(false).setPositiveButton("حفظ",(d,w)->{
            String pass=p.getText().toString();
            if(pass.length()<6){ Toast.makeText(this,"كلمة المرور يجب أن تكون 6 أحرف على الأقل",Toast.LENGTH_LONG).show(); setupPassword(); return; }
            String sa=salt(); prefs.edit().putString("salt",sa).putString("hash",hash(pass,sa)).putString("master_user","admin").putBoolean("password_set",true).apply();
            openDatabase(pass);
        }).show();
    }
    void login(){
        final EditText p=new EditText(this); p.setHint("كلمة المرور"); p.setInputType(0x81);
        new AlertDialog.Builder(this).setTitle("🔒 تسجيل الدخول").setView(p).setCancelable(false)
        .setPositiveButton("دخول",(d,w)->{
            String pass=p.getText().toString();
            if(hash(pass,prefs.getString("salt","")).equals(prefs.getString("hash",""))) openDatabase(pass);
            else { Toast.makeText(this,"كلمة المرور غير صحيحة",Toast.LENGTH_LONG).show(); login(); }
        }).show();
    }
    void openDatabase(String pass){
        dbKey=pass;
        dbHelper=new AppDatabase(this,pass);
        db=dbHelper.getWritableDatabase();
        loadFawryTotals(); showHome();
    }
    void loadFawryTotals(){
        Cursor c=db.rawQuery("SELECT COALESCE(SUM(amount),0), COALESCE(SUM(fee),0) FROM fawry",null);
        if(c.moveToFirst()){ fawryTotal=c.getDouble(0); fawryFees=c.getDouble(1); } c.close();
    }

    void base(String title){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(16,16,16,16);
        TextView h=tv(title,24); h.setTextColor(Color.rgb(21,101,192)); root.addView(h);
        content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv=new ScrollView(this); sv.addView(content); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }
    void exportEncryptedBackup(){
        try{
            String salt=prefs.getString("salt","");
            String stamp=new java.text.SimpleDateFormat("yyyyMMdd_HHmmss",Locale.getDefault()).format(new Date());
            File out=new File(getExternalFilesDir(null),"backup_"+stamp+".db");
            // SQLCipher database file is already encrypted on disk.
            File dbFile=getDatabasePath("shop_accounts.db");
            java.nio.file.Files.copy(dbFile.toPath(),out.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            new AlertDialog.Builder(this).setTitle("✅ النسخة الاحتياطية جاهزة").setMessage("تم إنشاء نسخة احتياطية مشفرة داخل ملفات التطبيق. انسخها إلى مكان آمن.").setPositiveButton("حسنًا",null).show();
        }catch(Exception e){ new AlertDialog.Builder(this).setTitle("خطأ").setMessage("تعذر إنشاء النسخة الاحتياطية").setPositiveButton("حسنًا",null).show(); }
    }
    void lockNow(){ if(db!=null){ try{db.close();}catch(Exception e){} } login(); }

    void showDebts(){
        base("📒 دفتر ديون الزبائن");
        content.addView(tv("سجل الديون والتحصيلات لكل عميل",18));
        EditText name=new EditText(this); name.setHint("اسم العميل"); content.addView(name);
        EditText phone=new EditText(this); phone.setHint("رقم الهاتف (اختياري)"); content.addView(phone);
        EditText amount=new EditText(this); amount.setHint("المبلغ"); amount.setInputType(2); content.addView(amount);
        Spinner type=new Spinner(this); type.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"دين جديد","تحصيل دفعة"})); content.addView(type);
        EditText note=new EditText(this); note.setHint("ملاحظة"); content.addView(note);
        Button save=btn("💾 حفظ العملية"); content.addView(save);
        TextView msg=tv("",17); content.addView(msg);
        save.setOnClickListener(v->{
            try{
                String cn=name.getText().toString().trim();
                double a=Double.parseDouble(amount.getText().toString());
                android.database.Cursor c=db.rawQuery("SELECT id FROM customers WHERE name=?",new String[]{cn});
                long cid;
                if(c.moveToFirst()) cid=c.getLong(0); else {
                    android.content.ContentValues cu=new android.content.ContentValues();
                    cu.put("name",cn); cu.put("phone",phone.getText().toString()); cu.put("created_at",now());
                    cid=db.insert("customers",null,cu);
                }
                c.close();
                android.content.ContentValues d=new android.content.ContentValues();
                String ty=type.getSelectedItem().toString();
                d.put("customer_id",cid); d.put("type",ty); d.put("amount",a); d.put("note",note.getText().toString()); d.put("created_at",now()); d.put("username",currentUser);
                db.insert("debts",null,d);
                msg.setText("تم تسجيل "+ty+" للعميل: "+cn);
                amount.setText(""); note.setText("");
            }catch(Exception e){ msg.setText("تأكد من اسم العميل والمبلغ."); }
        });
        Button list=btn("📋 العملاء والأرصدة"); content.addView(list);
        list.setOnClickListener(v->showCustomerBalances());
        Button back=btn("⬅ رجوع"); content.addView(back); back.setOnClickListener(v->back());
    }
    void showCustomerBalances(){
        StringBuilder out=new StringBuilder();
        android.database.Cursor c=db.rawQuery(
          "SELECT c.name, COALESCE(SUM(CASE WHEN d.type='دين جديد' THEN d.amount ELSE -d.amount END),0) bal FROM customers c LEFT JOIN debts d ON c.id=d.customer_id GROUP BY c.id,c.name ORDER BY c.name",null);
        while(c.moveToNext()) out.append(c.getString(0)).append(" : ").append(c.getDouble(1)).append(" جنيه\n");
        c.close();
        new AlertDialog.Builder(this).setTitle("📒 أرصدة العملاء").setMessage(out.length()==0?"لا يوجد عملاء":out.toString()).setPositiveButton("إغلاق",null).show();
    }
    void showUserControl(){
        if(!admin()){ Toast.makeText(this,"هذه الشاشة للمدير فقط",Toast.LENGTH_SHORT).show(); return; }
        base("👥 إدارة المستخدمين");
        content.addView(tv("المدير يتحكم في الحسابات والصلاحيات",18));
        EditText user=new EditText(this); user.setHint("اسم المستخدم"); content.addView(user);
        EditText pass=new EditText(this); pass.setHint("كلمة مرور"); pass.setInputType(0x81); content.addView(pass);
        EditText role=new EditText(this); role.setHint("الدور: موظف / مدير"); content.addView(role);
        Button add=btn("➕ إضافة مستخدم"); content.addView(add);
        TextView status=tv("",16); content.addView(status);
        add.setOnClickListener(v->{
            try{
                String un=user.getText().toString().trim(), pw=pass.getText().toString();
                String sa=salt();
                android.content.ContentValues cv=new android.content.ContentValues();
                cv.put("username",un); cv.put("pass_hash",hash(pw,sa)); cv.put("salt",sa);
                cv.put("role",role.getText().toString().trim()); cv.put("can_sales",1); cv.put("can_accessories",1);
                cv.put("can_fawry",1); cv.put("can_reports",0); cv.put("can_backup",0); cv.put("created_at",now());
                db.insertOrThrow("users",null,cv); status.setText("تم إنشاء المستخدم: "+un);
            }catch(Exception e){ status.setText("تعذر إضافة المستخدم. تأكد أن الاسم غير مستخدم."); }
        });
        Button users=btn("📋 عرض المستخدمين"); content.addView(users);
        users.setOnClickListener(v->{
            StringBuilder x=new StringBuilder();
            android.database.Cursor c=db.rawQuery("SELECT username,role,created_at FROM users ORDER BY username",null);
            while(c.moveToNext()) x.append("👤 ").append(c.getString(0)).append(" — ").append(c.getString(1)).append("\n");
            c.close();
            new AlertDialog.Builder(this).setTitle("المستخدمون").setMessage(x.length()==0?"لا يوجد موظفون":x.toString()).setPositiveButton("إغلاق",null).show();
        });
        Button back=btn("⬅ رجوع"); content.addView(back); back.setOnClickListener(v->back());
    }
    void showUserPerformance(){
        if(!admin()){ Toast.makeText(this,"للمدير فقط",Toast.LENGTH_SHORT).show(); return; }
        StringBuilder x=new StringBuilder();
        android.database.Cursor c=db.rawQuery(
          "SELECT username, COUNT(*) n, COALESCE(SUM(total),0) total FROM ("+
          "SELECT username, (price*qty) total FROM sales UNION ALL "+
          "SELECT username, fee total FROM fawry UNION ALL "+
          "SELECT username, (sell-cost) total FROM accessories) GROUP BY username ORDER BY total DESC",null);
        while(c.moveToNext()) x.append("👤 ").append(c.getString(0)).append("\nالعمليات: ").append(c.getInt(1)).append("\nالقيمة/الربح المسجل: ").append(c.getDouble(2)).append(" جنيه\n\n");
        c.close();
        new AlertDialog.Builder(this).setTitle("📊 أداء كل مستخدم").setMessage(x.length()==0?"لا توجد عمليات":x.toString()).setPositiveButton("إغلاق",null).show();
    }

    void showHome(){
        base("📊 حسابات المحل");
        content.addView(tv("السوبر ماركت + الإكسسوارات الحريمي + فوري",18));
        Button sales=btn("🛒 المبيعات والمخزون"); content.addView(sales); sales.setOnClickListener(v->showSales());
        Button accessories=btn("👛 الإكسسوارات الحريمي"); content.addView(accessories); accessories.setOnClickListener(v->showAccessories());
        Button fawry=btn("💳 حسابات ماكينة فوري"); content.addView(fawry); fawry.setOnClickListener(v->showFawry());
        Button debts=btn("📒 دفتر ديون الزبائن"); content.addView(debts); debts.setOnClickListener(v->showDebts());
        Button performance=btn("📊 إنتاج/مبيعات كل مستخدم"); content.addView(performance); performance.setOnClickListener(v->showUserPerformance());
        Button control=btn("👥 التحكم الكامل بالمستخدمين"); content.addView(control); control.setOnClickListener(v->showUserControl());
        Button reports=btn("📈 التقارير والدخل"); content.addView(reports); reports.setOnClickListener(v->showReports());
        Button users=btn("👥 إدارة المستخدمين"); content.addView(users); users.setOnClickListener(v->addEmployee());
        Button backup=btn("🔐 نسخة احتياطية مشفرة"); content.addView(backup); backup.setOnClickListener(v->exportEncryptedBackup());
        Button lock=btn("🔒 قفل التطبيق الآن"); content.addView(lock); lock.setOnClickListener(v->lockNow());
    }
    void back(){ showHome(); }
    @Override protected void onUserLeaveHint(){ super.onUserLeaveHint(); if(db!=null) lockNow(); }
    @Override protected void onPause(){ super.onPause(); }


    void showSales(){
        base("🛒 المبيعات والمخزون");
        content.addView(tv("إضافة عملية بيع",20));
        EditText product=new EditText(this); product.setHint("اسم المنتج"); content.addView(product);
        EditText price=new EditText(this); price.setHint("سعر البيع"); price.setInputType(2); content.addView(price);
        EditText qty=new EditText(this); qty.setHint("الكمية"); qty.setInputType(2); content.addView(qty);
        Button add=btn("حفظ البيع"); content.addView(add);
        TextView result=tv("",18); content.addView(result);
        add.setOnClickListener(v->{
            try{
                ContentValues cv=new ContentValues(); cv.put("product",product.getText().toString());
                cv.put("price",Double.parseDouble(price.getText().toString()));
                cv.put("qty",Integer.parseInt(qty.getText().toString())); cv.put("created_at",now()); cv.put("username",currentUser);
                db.insert("sales",null,cv); result.setText("تم حفظ البيع بنجاح"); product.setText(""); price.setText(""); qty.setText("");
            }catch(Exception e){ result.setText("أدخل البيانات بشكل صحيح"); }
        });
        Button b=btn("⬅ رجوع"); content.addView(b); b.setOnClickListener(v->back());
    }
    void showAccessories(){
        base("👛 الإكسسوارات الحريمي");
        content.addView(tv("قسم مستقل للإكسسوارات وحساب أرباحها",18));
        EditText name=new EditText(this); name.setHint("اسم الإكسسوار"); content.addView(name);
        EditText cost=new EditText(this); cost.setHint("سعر الشراء"); cost.setInputType(2); content.addView(cost);
        EditText sell=new EditText(this); sell.setHint("سعر البيع"); sell.setInputType(2); content.addView(sell);
        Button add=btn("حفظ"); content.addView(add);
        TextView r=tv("",18); content.addView(r);
        add.setOnClickListener(v->{
            try{
                ContentValues cv=new ContentValues(); cv.put("name",name.getText().toString());
                cv.put("cost",Double.parseDouble(cost.getText().toString())); cv.put("sell",Double.parseDouble(sell.getText().toString())); cv.put("created_at",now()); cv.put("username",currentUser);
                db.insert("accessories",null,cv); r.setText("تم حفظ الإكسسوار والربح = سعر البيع - سعر الشراء");
                name.setText(""); cost.setText(""); sell.setText("");
            }catch(Exception e){ r.setText("أدخل البيانات بشكل صحيح"); }
        });
        Button b=btn("⬅ رجوع"); content.addView(b); b.setOnClickListener(v->back());
    }
    void showFawry(){
        base("💳 حسابات ماكينة فوري");
        content.addView(tv("سجل عمليات الماكينة وحساب دخلها",18));
        EditText type=new EditText(this); type.setHint("نوع العملية (شحن/فاتورة/خدمة...)"); content.addView(type);
        EditText amount=new EditText(this); amount.setHint("قيمة العملية"); amount.setInputType(2); content.addView(amount);
        EditText fee=new EditText(this); fee.setHint("عمولة المحل / ربح العملية"); fee.setInputType(2); content.addView(fee);
        Button add=btn("➕ تسجيل العملية"); content.addView(add);
        TextView summary=tv("إجمالي العمليات: 0\nإجمالي العمولة: 0",18); content.addView(summary);
        add.setOnClickListener(v->{
            try {
                double a=Double.parseDouble(amount.getText().toString());
                double f=Double.parseDouble(fee.getText().toString());
                fawryTotal+=a; fawryFees+=f;
                ContentValues cv=new ContentValues(); cv.put("type",type.getText().toString()); cv.put("amount",a); cv.put("fee",f); cv.put("username",currentUser); cv.put("created_at",now());
                db.insert("fawry",null,cv);
                fawryOps.add(type.getText()+" | "+a+" | عمولة "+f);
                summary.setText("إجمالي قيمة العمليات: "+fawryTotal+"\nإجمالي دخل/عمولة الماكينة: "+fawryFees+"\nعدد العمليات: "+fawryOps.size());
                amount.setText(""); fee.setText("");
            } catch(Exception e){ summary.setText("من فضلك أدخل قيمة العملية والعمولة بشكل صحيح."); }
        });
        Button list=btn("📋 عرض العمليات"); content.addView(list);
        list.setOnClickListener(v->{ StringBuilder s=new StringBuilder(); for(String x:fawryOps)s.append(x).append("\n"); new AlertDialog.Builder(this).setTitle("عمليات فوري").setMessage(s.length()==0?"لا توجد عمليات":s.toString()).setPositiveButton("إغلاق",null).show();});
        Button b=btn("⬅ رجوع"); content.addView(b); b.setOnClickListener(v->back());
    }
    void showReports(){
        base("📈 التقارير والدخل");
        content.addView(tv("تقرير مبدئي",22));
        Cursor c1=db.rawQuery("SELECT COALESCE(SUM(amount),0), COALESCE(SUM(fee),0), COUNT(*) FROM fawry",null);
        String report=""; if(c1.moveToFirst()) report="💳 قيمة عمليات فوري: "+c1.getDouble(0)+"\\n💰 دخل/عمولة فوري: "+c1.getDouble(1)+"\\n🧾 عدد العمليات: "+c1.getInt(2); c1.close();
        content.addView(tv(report,18));
        content.addView(tv("سيتم في النسخة التالية إضافة تقارير يومية وشهرية وصافي الربح والمصروفات.",17));
        Button b=btn("⬅ رجوع"); content.addView(b); b.setOnClickListener(v->back());
    }
}