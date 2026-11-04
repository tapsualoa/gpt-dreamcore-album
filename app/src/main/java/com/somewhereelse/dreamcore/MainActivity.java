package com.somewhereelse.dreamcore;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.media.MediaPlayer;
import android.widget.VideoView;

import java.util.*;
import java.util.concurrent.*;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQ_MEDIA=402;
    private final int bg=Color.rgb(16,13,37), panel=Color.rgb(35,27,60), ink=Color.rgb(248,240,255), muted=Color.rgb(195,180,215);
    private final ExecutorService workers=Executors.newFixedThreadPool(3);
    private final ArrayList<MediaItem> all=new ArrayList<>(), shown=new ArrayList<>();
    private GridView grid; private MediaAdapter adapter; private TextView count, empty;
    private EditText search; private Spinner albums, sorting; private String type="all", album="all", order="newest", query="";
    private DialogState viewer;
    private static class MediaItem {
        Uri uri; String name, bucket, bucketId, mime, type; long date;
        String key(){return uri.toString();}
    }
    private static class DialogState { android.app.Dialog dialog; int index; }

    @Override public void onCreate(Bundle state){
        super.onCreate(state); getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildScreen();
        if(hasPermission()) scan(); else requestPermission();
    }
    private GradientDrawable shape(int color,int stroke,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{color,Color.rgb(27,21,51)});
        d.setCornerRadius(dp(radius)); if(stroke!=0)d.setStroke(dp(1),stroke); return d;
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,int size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private void buildScreen(){
        android.widget.FrameLayout root=new FrameLayout(this);root.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(66,49,91),bg,bg}));
        LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(18),dp(18),dp(18),dp(12));
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-1,-1);root.addView(page,pp);
        TextView brand=text("✧ GPT DREAMCORE",12,muted);brand.setLetterSpacing(.2f);page.addView(brand);
        TextView title=text("Somewhere Else.",34,ink);title.setTypeface(Typeface.SERIF,Typeface.NORMAL);title.setPadding(0,dp(14),0,dp(3));page.addView(title);
        TextView subtitle=text("A little universe inside your phone",13,muted);subtitle.setTypeface(Typeface.SERIF,Typeface.ITALIC);page.addView(subtitle);
        TextView section=text("YOUR PRIVATE UNIVERSE",10,muted);section.setLetterSpacing(.18f);section.setPadding(0,dp(24),0,dp(8));page.addView(section);
        LinearLayout filters=new LinearLayout(this);filters.setOrientation(LinearLayout.HORIZONTAL);filters.setPadding(0,0,0,dp(10));page.addView(filters);
        addFilter(filters,"Tất cả","all");addFilter(filters,"Ảnh","photo");addFilter(filters,"Video","video");
        search=new EditText(this);search.setSingleLine(true);search.setTextColor(ink);search.setHintTextColor(muted);search.setHint("Tìm theo tên ảnh/video");search.setTextSize(13);search.setPadding(dp(14),0,dp(14),0);search.setBackground(shape(panel,0x44ffffff,24));page.addView(search,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(44));rp.topMargin=dp(8);page.addView(row,rp);
        albums=new Spinner(this);sorting=new Spinner(this);row.addView(albums,new LinearLayout.LayoutParams(0,-1,1));row.addView(sorting,new LinearLayout.LayoutParams(0,-1,1));
        count=text("Đang quét thư viện…",10,muted);count.setPadding(dp(2),dp(8),0,dp(5));page.addView(count);
        grid=new GridView(this);grid.setNumColumns(2);grid.setHorizontalSpacing(dp(12));grid.setVerticalSpacing(dp(12));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setSelector(android.R.color.transparent);grid.setClipToPadding(false);
        LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,0,1);gp.topMargin=dp(4);page.addView(grid,gp);
        adapter=new MediaAdapter();grid.setAdapter(adapter);grid.setOnItemClickListener((p,v,pos,id)->openViewer(pos));
        empty=text("Thư viện của bạn sẽ xuất hiện tại đây",14,muted);empty.setGravity(Gravity.CENTER);empty.setVisibility(View.GONE);page.addView(empty,new LinearLayout.LayoutParams(-1,dp(70)));
        setContentView(root);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString().toLowerCase(new Locale("vi"));filter();}public void afterTextChanged(Editable e){}});
        albums.setOnItemSelectedListener(new SimpleSelect(){public void onItemSelected(android.widget.AdapterView<?>p,View v,int pos,long id){Object x=albums.getTag();if(x!=null){album=pos==0?"all":((String[])x)[pos];filter();}}});
        sorting.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Mới nhất","Cũ nhất","Tên A–Z"}));
        sorting.setOnItemSelectedListener(new SimpleSelect(){public void onItemSelected(android.widget.AdapterView<?>p,View v,int pos,long id){order=pos==1?"oldest":pos==2?"name":"newest";filter();}});
    }
    private abstract class SimpleSelect implements android.widget.AdapterView.OnItemSelectedListener {public void onNothingSelected(android.widget.AdapterView<?>p){}}
    private void addFilter(LinearLayout row,String label,String value){
        TextView b=text(label,12,ink);b.setGravity(Gravity.CENTER);b.setPadding(dp(17),dp(8),dp(17),dp(8));b.setBackground(shape(0x55d9b8ed,0x55ffffff,24));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(38));p.rightMargin=dp(7);row.addView(b,p);
        b.setOnClickListener(v->{type=value;for(int i=0;i<row.getChildCount();i++)row.getChildAt(i).setBackground(shape(0x332d2448,0x33ffffff,24));b.setBackground(shape(0x66d9b8ed,0x99ffffff,24));filter();});
    }
    private boolean hasPermission(){
        if(Build.VERSION.SDK_INT>=34)return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==0||checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)==0||checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==0;
        if(Build.VERSION.SDK_INT>=33)return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==0||checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)==0;
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==0;
    }
    private void requestPermission(){
        if(Build.VERSION.SDK_INT>=34)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED},REQ_MEDIA);
        else if(Build.VERSION.SDK_INT>=33)requestPermissions(new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO},REQ_MEDIA);
        else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},REQ_MEDIA);
    }
    @Override public void onRequestPermissionsResult(int r,String[]p,int[]g){super.onRequestPermissionsResult(r,p,g);if(r==REQ_MEDIA){if(hasPermission())scan();else new AlertDialog.Builder(this).setMessage("Cần quyền truy cập ảnh và video để hiển thị thư viện trên thiết bị.").setPositiveButton("Thử lại",(d,w)->requestPermission()).setNegativeButton("Đóng",null).show();}}
    private void scan(){
        count.setText("Đang đọc thư viện trên thiết bị…");
        workers.execute(()->{
            ArrayList<MediaItem> found=new ArrayList<>();query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,"photo",found);query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,"video",found);
            runOnUiThread(()->{all.clear();all.addAll(found);populateAlbums();filter();});
        });
    }
    private void query(Uri collection,String kind,List<MediaItem> out){
        String[] projection={MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.DATE_ADDED,MediaStore.MediaColumns.MIME_TYPE,"bucket_id","bucket_display_name"};
        try(Cursor c=getContentResolver().query(collection,projection,null,null,MediaStore.MediaColumns.DATE_ADDED+" DESC")){
            if(c==null)return;int id=c.getColumnIndexOrThrow("_id"),name=c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME),date=c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED),mime=c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE),bi=c.getColumnIndex("bucket_id"),bn=c.getColumnIndex("bucket_display_name");
            while(c.moveToNext()){MediaItem m=new MediaItem();m.uri=ContentUris.withAppendedId(collection,c.getLong(id));m.name=c.getString(name)==null?"Không tên":c.getString(name);m.date=c.getLong(date);m.mime=c.getString(mime);m.type=kind;m.bucket=bn<0||c.getString(bn)==null?"Khác":c.getString(bn);m.bucketId=bi<0?"other":c.getString(bi);out.add(m);}
        }catch(Exception ignored){}
    }
    private void populateAlbums(){
        TreeMap<String,String> map=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);for(MediaItem m:all)map.put(m.bucketId,m.bucket);
        String[] labels=new String[map.size()+1],ids=new String[map.size()+1];labels[0]="Tất cả album";ids[0]="all";int i=1;for(Map.Entry<String,String>e:map.entrySet()){ids[i]=e.getKey();labels[i]=e.getValue();i++;}
        albums.setTag(ids);albums.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,labels));
    }
    private void filter(){
        shown.clear();for(MediaItem m:all)if((type.equals("all")||type.equals(m.type))&&(album.equals("all")||album.equals(m.bucketId))&&(query.isEmpty()||m.name.toLowerCase(new Locale("vi")).contains(query)))shown.add(m);
        Collections.sort(shown,(a,b)->order.equals("oldest")?Long.compare(a.date,b.date):order.equals("name")?a.name.compareToIgnoreCase(b.name):Long.compare(b.date,a.date));
        if(adapter!=null)adapter.notifyDataSetChanged();if(count!=null)count.setText(shown.size()+" MỤC · LƯU TRÊN THIẾT BỊ");if(empty!=null)empty.setVisibility(shown.isEmpty()?View.VISIBLE:View.GONE);
    }
    private class MediaAdapter extends android.widget.BaseAdapter {
        public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return p;}
        public View getView(int pos,View convert,ViewGroup parent){
            FrameLayout card=convert instanceof FrameLayout?(FrameLayout)convert:new FrameLayout(MainActivity.this);
            card.setBackground(shape(panel,0x33ffffff,10));card.setClipToOutline(true);card.setForeground(null);
            ImageView image=new ImageView(MainActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(0xff29213f);image.setTag(null);
            card.removeAllViews();card.addView(image,new FrameLayout.LayoutParams(-1,-1));
            TextView caption=text(shown.get(pos).name,12,ink);caption.setSingleLine(true);caption.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.setPadding(dp(10),dp(6),dp(10),dp(6));caption.setBackgroundColor(0x990d0920);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(36),Gravity.BOTTOM);card.addView(caption,cp);
            MediaItem item=shown.get(pos);image.setTag(item.key());final String key=item.key();
            workers.execute(()->{Bitmap bmp=null;try{bmp=getContentResolver().loadThumbnail(item.uri,new android.util.Size(dp(240),dp(300)),null);}catch(Exception ignored){}final Bitmap result=bmp;if(result!=null)runOnUiThread(()->{if(key.equals(image.getTag()))image.setImageBitmap(result);});});
            card.setLayoutParams(new AbsListView.LayoutParams(-1,dp(205)));return card;
        }
    }
    private void openViewer(int pos){
        if(pos<0||pos>=shown.size())return;MediaItem item=shown.get(pos);android.app.Dialog dialog=new android.app.Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);viewer=new DialogState();viewer.dialog=dialog;viewer.index=pos;
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xff080615);
        if(item.type.equals("video")){VideoView video=new VideoView(this);video.setVideoURI(item.uri);video.setMediaController(new android.widget.MediaController(this));root.addView(video,new FrameLayout.LayoutParams(-1,-1));video.setOnPreparedListener(MediaPlayer::start);}
        else{ZoomImage image=new ZoomImage(this);image.setImageURI(item.uri);image.setScaleType(ImageView.ScaleType.FIT_CENTER);root.addView(image,new FrameLayout.LayoutParams(-1,-1));}
        TextView close=text("×",34,ink);close.setGravity(Gravity.CENTER);FrameLayout.LayoutParams closeP=new FrameLayout.LayoutParams(dp(52),dp(52),Gravity.TOP|Gravity.RIGHT);root.addView(close,closeP);close.setOnClickListener(v->dialog.dismiss());
        TextView name=text(item.name,13,ink);name.setGravity(Gravity.CENTER);name.setBackgroundColor(0x66000000);FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-1,dp(46),Gravity.BOTTOM);root.addView(name,np);
        dialog.setContentView(root);dialog.show();
    }
    private class ZoomImage extends ImageView {
        private ScaleGestureDetector detector;private float scale=1;
        ZoomImage(Activity c){super(c);setScaleType(ScaleType.MATRIX);detector=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){scale=Math.max(1,Math.min(5,scale*d.getScaleFactor()));android.graphics.Matrix m=new android.graphics.Matrix();m.setScale(scale,scale,getWidth()/2f,getHeight()/2f);setImageMatrix(m);return true;}});}
        public boolean onTouchEvent(MotionEvent e){detector.onTouchEvent(e);return true;}
    }
    @Override public void onBackPressed(){if(viewer!=null&&viewer.dialog.isShowing()){viewer.dialog.dismiss();viewer=null;}else super.onBackPressed();}
    @Override protected void onDestroy(){workers.shutdownNow();super.onDestroy();}
}
