package com.somewhereelse.dreamcore;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.view.ScaleGestureDetector;
import android.widget.*;
import android.util.LruCache;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    private static final int REQ_MEDIA=402;
    private final int bg=Color.rgb(16,13,37), panel=Color.rgb(29,23,53), ink=Color.rgb(248,240,255), muted=Color.rgb(198,185,217);
    private final ExecutorService workers=Executors.newFixedThreadPool(3);
    private final LruCache<String,Bitmap> thumbs=new LruCache<String,Bitmap>(24*1024*1024){protected int sizeOf(String k,Bitmap b){return b.getByteCount();}};
    private final Set<String> loading=Collections.synchronizedSet(new HashSet<String>());
    private final ArrayList<MediaItem> all=new ArrayList<>(), shown=new ArrayList<>();
    private GridView grid; private MediaAdapter adapter; private TextView count, empty;
    private EditText search; private Spinner albums, sorting; private String type="all", album="all", order="newest", query="";
    private DialogState viewer; private DreamBackdrop backdrop;
    private static class MediaItem { Uri uri; String name,bucket,bucketId,mime,type; long date; String key(){return uri.toString();} }
    private static class DialogState { Dialog dialog; int index; }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(30,22,52)); getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(0);
        buildScreen(); if(hasPermission())scan(); else requestPermission();
    }
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable glass(int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xB832294B,0xA817142F});
        d.setCornerRadius(dp(radius)); d.setStroke(dp(1),0x44F4DFFF); return d;
    }
    private TextView text(String s,int size,int color){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private void buildScreen(){
        FrameLayout root=new FrameLayout(this);
        backdrop=new DreamBackdrop(); root.addView(backdrop,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18),dp(8),dp(18),dp(12));
        root.addView(page,new FrameLayout.LayoutParams(-1,-1));
        DreamHeader hero=new DreamHeader(); page.addView(hero,new LinearLayout.LayoutParams(-1,dp(274)));
        LinearLayout section=new LinearLayout(this);section.setGravity(Gravity.CENTER_VERTICAL);section.setPadding(dp(2),dp(4),dp(2),dp(8));
        LinearLayout titles=new LinearLayout(this);titles.setOrientation(LinearLayout.VERTICAL);
        TextView eyebrow=text("✦  THE PRIVATE UNIVERSE",9,0xFFDCC8EC);eyebrow.setLetterSpacing(.22f);titles.addView(eyebrow);
        TextView heading=text("Your memories",25,ink);heading.setTypeface(Typeface.SERIF,Typeface.NORMAL);titles.addView(heading);
        section.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        count=text("0 MEMORIES",9,muted);count.setLetterSpacing(.12f);section.addView(count);
        page.addView(section);
        LinearLayout tools=new LinearLayout(this);tools.setOrientation(LinearLayout.VERTICAL);
        LinearLayout filters=new LinearLayout(this);filters.setGravity(Gravity.CENTER_VERTICAL);filters.setPadding(dp(5),dp(4),dp(5),dp(4));filters.setBackground(glass(30));
        addFilter(filters,"All","all",true);addFilter(filters,"Photos","photo",false);addFilter(filters,"Videos","video",false);
        TextView rescan=text("↻  SCAN",9,0xFFEADAF5);rescan.setGravity(Gravity.CENTER);rescan.setLetterSpacing(.1f);rescan.setPadding(dp(12),0,dp(12),0);rescan.setOnClickListener(v->scan());
        filters.addView(rescan,new LinearLayout.LayoutParams(-2,dp(34)));
        tools.addView(filters,new LinearLayout.LayoutParams(-1,dp(46)));
        search=new EditText(this);search.setSingleLine(true);search.setTextColor(ink);search.setHintTextColor(0xFFB9A9CB);search.setHint("Tìm một ký ức…");search.setTextSize(13);search.setPadding(dp(16),0,dp(16),0);search.setBackground(glass(24));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(46));sp.topMargin=dp(10);tools.addView(search,sp);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
        albums=new Spinner(this);sorting=new Spinner(this);
        row.addView(albums,new LinearLayout.LayoutParams(0,dp(42),1));row.addView(sorting,new LinearLayout.LayoutParams(0,dp(42),1));
        tools.addView(row);page.addView(tools);
        count.setText("ĐANG ĐÁNH THỨC VŨ TRỤ…");
        grid=new GridView(this);grid.setNumColumns(2);grid.setHorizontalSpacing(dp(12));grid.setVerticalSpacing(dp(15));grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);grid.setSelector(android.R.color.transparent);grid.setClipToPadding(false);grid.setPadding(0,dp(8),0,dp(12));
        LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,0,1);gp.topMargin=dp(4);page.addView(grid,gp);
        adapter=new MediaAdapter();grid.setAdapter(adapter);grid.setOnItemClickListener((p,v,pos,id)->openViewer(pos));
        empty=text("A quiet little universe\nThư viện của bạn sẽ xuất hiện tại đây",14,muted);empty.setGravity(Gravity.CENTER);empty.setVisibility(View.GONE);page.addView(empty,new LinearLayout.LayoutParams(-1,dp(68)));
        setContentView(root);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int c){query=s.toString().toLowerCase(new Locale("vi"));filter();}public void afterTextChanged(Editable e){}});
        albums.setOnItemSelectedListener(new SimpleSelect(){public void onItemSelected(AdapterView<?>p,View v,int pos,long id){Object x=albums.getTag();if(x!=null){album=pos==0?"all":((String[])x)[pos];filter();}}});
        sorting.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Mới nhất","Cũ nhất","Tên A–Z"}));
        sorting.setOnItemSelectedListener(new SimpleSelect(){public void onItemSelected(AdapterView<?>p,View v,int pos,long id){order=pos==1?"oldest":pos==2?"name":"newest";filter();}});
    }
    private abstract class SimpleSelect implements AdapterView.OnItemSelectedListener {public void onNothingSelected(AdapterView<?>p){}}
    private void addFilter(LinearLayout row,String label,String value,boolean selected){
        TextView b=text(label,10,selected?Color.WHITE:0xFFD0C1DF);b.setGravity(Gravity.CENTER);b.setPadding(dp(15),0,dp(15),0);
        b.setBackground(selected?pill(true):pill(false));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(34));p.rightMargin=dp(3);row.addView(b,p);
        b.setOnClickListener(v->{type=value;for(int i=0;i<row.getChildCount();i++){View child=row.getChildAt(i);if(child instanceof TextView&&child!=row.getChildAt(row.getChildCount()-1)){child.setBackground(pill(false));((TextView)child).setTextColor(0xFFD0C1DF);}}b.setBackground(pill(true));b.setTextColor(Color.WHITE);filter();});
    }
    private GradientDrawable pill(boolean active){GradientDrawable d=new GradientDrawable();d.setCornerRadius(dp(22));d.setColor(active?0x55E7C5E5:0x00000000);if(active)d.setStroke(dp(1),0x66F4DFFF);return d;}
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
        count.setText("ĐANG ĐỌC KÝ ỨC TRÊN THIẾT BỊ…");
        workers.execute(()->{ArrayList<MediaItem> found=new ArrayList<>();query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,"photo",found);query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,"video",found);
            runOnUiThread(()->{all.clear();all.addAll(found);populateAlbums();filter();});});
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
        if(adapter!=null)adapter.notifyDataSetChanged();if(count!=null)count.setText(shown.size()+" MEMORIES  ·  LOCAL COLLECTION");if(empty!=null)empty.setVisibility(shown.isEmpty()?View.VISIBLE:View.GONE);
    }
    private class MediaAdapter extends BaseAdapter {
        public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return p;}
        public View getView(int pos,View convert,ViewGroup parent){
            FrameLayout card=convert instanceof FrameLayout?(FrameLayout)convert:new FrameLayout(MainActivity.this);card.removeAllViews();card.setBackground(glass(13));card.setClipToOutline(true);
            MediaItem item=shown.get(pos);
            ImageView image=new ImageView(MainActivity.this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(0xFF28213D);image.setTag(item.key());card.addView(image,new FrameLayout.LayoutParams(-1,-1));
            Bitmap cached=thumbs.get(item.key());if(cached!=null)image.setImageBitmap(cached);else loadThumb(item,image);
            View shade=new View(MainActivity.this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x22080519,0x00080519,0xDD090718}));card.addView(shade,new FrameLayout.LayoutParams(-1,-1));
            TextView badge=text(item.type.equals("video")?"▶  MOVING MEMORY":"✧  STILL MEMORY",8,0xFFF4E8FF);badge.setLetterSpacing(.1f);badge.setPadding(dp(9),dp(6),dp(9),dp(6));badge.setBackground(glass(20));FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-2,dp(27),Gravity.TOP|Gravity.LEFT);bp.setMargins(dp(9),dp(9),0,0);card.addView(badge,bp);
            TextView caption=text(item.name,12,Color.WHITE);caption.setSingleLine(true);caption.setEllipsize(android.text.TextUtils.TruncateAt.END);caption.setPadding(dp(11),dp(7),dp(10),dp(9));caption.setShadowLayer(dp(8),0,dp(2),Color.BLACK);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(43),Gravity.BOTTOM);card.addView(caption,cp);
            card.setLayoutParams(new AbsListView.LayoutParams(-1,dp(218)));return card;
        }
    }
    private void loadThumb(MediaItem item,ImageView target){
        String key=item.key();if(loading.contains(key))return;loading.add(key);
        workers.execute(()->{Bitmap bmp=null;try{bmp=getContentResolver().loadThumbnail(item.uri,new android.util.Size(dp(420),dp(520)),null);}catch(Exception ignored){}
            final Bitmap result=bmp;loading.remove(key);if(result!=null){thumbs.put(key,result);runOnUiThread(()->{if(key.equals(target.getTag()))target.setImageBitmap(result);});}});
    }
    private void openViewer(int pos){
        if(pos<0||pos>=shown.size())return;MediaItem item=shown.get(pos);Dialog dialog=new Dialog(this,android.R.style.Theme_Black_NoTitleBar_Fullscreen);viewer=new DialogState();viewer.dialog=dialog;viewer.index=pos;
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xFF080615);
        if(item.type.equals("video")){VideoView video=new VideoView(this);video.setVideoURI(item.uri);video.setMediaController(new MediaController(this));root.addView(video,new FrameLayout.LayoutParams(-1,-1));video.setOnPreparedListener(MediaPlayer::start);dialog.setOnDismissListener(d->video.stopPlayback());}
        else{ZoomImage image=new ZoomImage(this);image.setBackgroundColor(0xFF080615);root.addView(image,new FrameLayout.LayoutParams(-1,-1));workers.execute(()->{Bitmap b=null;try{b=getContentResolver().loadThumbnail(item.uri,new android.util.Size(1800,2200),null);}catch(Exception ignored){}Bitmap result=b;if(result!=null)runOnUiThread(()->image.setImageBitmap(result));});}
        TextView close=text("×",34,ink);close.setGravity(Gravity.CENTER);close.setBackground(glass(28));FrameLayout.LayoutParams closeP=new FrameLayout.LayoutParams(dp(50),dp(50),Gravity.TOP|Gravity.RIGHT);closeP.setMargins(0,dp(22),dp(18),0);root.addView(close,closeP);close.setOnClickListener(v->dialog.dismiss());
        TextView name=text(item.name,13,ink);name.setGravity(Gravity.CENTER);name.setBackgroundColor(0x880D0920);root.addView(name,new FrameLayout.LayoutParams(-1,dp(52),Gravity.BOTTOM));
        dialog.setContentView(root);dialog.show();
    }
    private class ZoomImage extends ImageView {
        private ScaleGestureDetector detector;private float scale=1;
        ZoomImage(Activity c){super(c);setScaleType(ScaleType.FIT_CENTER);detector=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector d){scale=Math.max(1,Math.min(5,scale*d.getScaleFactor()));setScaleX(scale);setScaleY(scale);return true;}});}
        public boolean onTouchEvent(android.view.MotionEvent e){detector.onTouchEvent(e);return true;}
    }
    @Override public void onBackPressed(){if(viewer!=null&&viewer.dialog.isShowing()){viewer.dialog.dismiss();viewer=null;}else super.onBackPressed();}
    @Override protected void onDestroy(){workers.shutdownNow();if(backdrop!=null)backdrop.stop();super.onDestroy();}

    private class DreamHeader extends View {
        private final Paint p=new Paint(3);private final Typeface serif=Typeface.create("serif",Typeface.NORMAL),italic=Typeface.create("serif",Typeface.ITALIC);
        DreamHeader(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2f,cy=h*.48f;
            p.setShader(new RadialGradient(cx,cy,dp(155),new int[]{0x55E8C7F4,0x1C9CB9E8,0x00000000},null,Shader.TileMode.CLAMP));c.drawCircle(cx,cy,dp(155),p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(0x35F5DFFF);c.save();c.rotate(-18,cx,cy);c.drawOval(cx-dp(130),cy-dp(78),cx+dp(130),cy+dp(78),p);c.rotate(39,cx,cy);c.drawOval(cx-dp(145),cy-dp(54),cx+dp(145),cy+dp(54),p);c.restore();p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);p.setColor(0xFFEADAF5);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(dp(9));p.setLetterSpacingCompat(.24f);c.drawText("✧  GPT DREAMCORE  ✧",cx,dp(24),p);
            p.setTypeface(serif);p.setColor(0xFFFFF7FF);p.setTextSize(dp(53));p.setShadowLayer(dp(20),0,0,0x99DDB5F2);c.drawText("Somewhere",cx,cy-dp(2),p);p.clearShadowLayer();p.setTextSize(dp(57));c.drawText("Else.",cx,cy+dp(53),p);
            p.setTypeface(italic);p.setColor(0xFFD8C9E7);p.setTextSize(dp(12));c.drawText("A little universe inside your phone",cx,cy+dp(84),p);
            p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));p.setTextSize(dp(8));p.setColor(0xFFBDAECD);c.drawText("YOUR PERSONAL MEMORY SPACE",cx,h-dp(18),p);
        }
    }
    private class DreamBackdrop extends View {
        private final Paint p=new Paint(3);private final Random random=new Random(19);private final float[] sx=new float[85],sy=new float[85],sr=new float[85];private boolean running=true;
        DreamBackdrop(){super(MainActivity.this);for(int i=0;i<sx.length;i++){sx[i]=random.nextFloat();sy[i]=random.nextFloat();sr[i]=.6f+random.nextFloat()*1.5f;}setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        protected void onDraw(Canvas c){float w=getWidth(),h=getHeight();p.setShader(new LinearGradient(0,0,w,h,new int[]{0xFF493765,0xFF211B3C,0xFF100D25},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
            p.setShader(new RadialGradient(w*.2f,h*.2f,w*.65f,new int[]{0x35E5B8EB,0x009D7AD1},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
            p.setShader(new RadialGradient(w*.92f,h*.62f,w*.52f,new int[]{0x286FC0E0,0x007FC8E8},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
            long t=System.currentTimeMillis();for(int i=0;i<sx.length;i++){float y=(sy[i]*h+(t*.006f*(i%3+1)))%(h+dp(10));p.setColor((i%5==0)?0xBBDDCBF5:0x77FFFFFF);c.drawCircle(sx[i]*w,y,dp(sr[i]),p);}
            if(running)postInvalidateDelayed(45);
        }
        void stop(){running=false;}
    }
}
