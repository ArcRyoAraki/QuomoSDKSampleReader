package jp.co.tikyus.QuomoSDKSampleReader;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import jp.co.tikyus.quomo.sdk;
import android.Manifest;
import android.content.pm.PackageManager;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.Exclude;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity implements CameraView.OnListener {
    static {
        System.loadLibrary("opencv_java3");
        System.loadLibrary("quomosdk");
    }

    private sdk _quomosdk;

    static private final int permission_camera = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);

        if (Build.VERSION.SDK_INT >= 23) {
            //カメラ利用許可を取得
            permissionOfCamera();
        } else {
            start();
        }
    }

    @Override
    public void onDecode(CameraView cameraView, long id) {
        ToneGenerator toneGenerator
                = new ToneGenerator(AudioManager.STREAM_SYSTEM, ToneGenerator.MAX_VOLUME);
        if (id == 1) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP);
            //qq
            ImageView view = (ImageView) findViewById(R.id.imageViewGreen);
            AlphaAnimation animation = new AlphaAnimation(1.0f, 0.0f);
            animation.setDuration(100);
            view.startAnimation(animation);
        }
        if (id == 2) {
            String text = "";
            toneGenerator.startTone(ToneGenerator.TONE_CDMA_ANSWER);
            int count = _quomosdk.QuomoGetDecodeCodeCount();
            for (int i = 0; i < count; i++) {
                long code = _quomosdk.QuomoGetDecodeCode(i);
                Log.d("MAIN", "code:" + code);
                text = text + code + "\n";
            }
            ImageView view = (ImageView) findViewById(R.id.imageViewBlue);
            AlphaAnimation animation = new AlphaAnimation(1.0f, 0.0f);
            animation.setDuration(1000);
            view.startAnimation(animation);

            TextView textView = (TextView) findViewById(R.id.textView);
            textView.setText(text);
            Toast.makeText(this, "社員番号："+text+"の出席を確認しました", Toast.LENGTH_LONG).show();

            //改行コードを取り除く
            text = text.replace("\n","");

            //firebaseのDBに書き込み
            FirebaseDatabase database = FirebaseDatabase.getInstance();
            String Token = FirebaseInstanceId.getInstance().getToken();
            DatabaseReference refemp = database.getReference("EmployeeData");
            refemp.child(text).child("Attendflg").setValue(true);
            refemp.child(text).child("Token").setValue(Token);

            //FCMで通知作りかけ。一旦中止。
            DatabaseReference todosRef = FirebaseDatabase.getInstance().getReference("EmployeeData");
            todosRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot snapshot) {
                    for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                        String key = dataSnapshot.getKey();
                        String token = (String) dataSnapshot.child("token").getValue();
                        Boolean Attendflg = (Boolean) dataSnapshot.child("Attendflg").getValue();

                        // このforループで、Todoごとのkey, title, isDoneが取得できているので、
                        // Todoクラスを利用し、Hashmapに追加するなどして保存する。
                    }
                    // 保存した情報を用いた処理などを記載する。

                }

                @Override
                public void onCancelled(DatabaseError databaseError) {
                    Log.d("seit", "ValueEventListener#onCancelled");
                }
            });

        }

    }

    private void start() {
        setContentView(R.layout.activity_main);
        //
        //Quomo初期設定
        _quomosdk = new sdk();
        _quomosdk.QuomoInitialize();
        boolean licenseResult;

        //Quomo証明書読み込み
        //証明書が無効である場合falseが返ってきます。有効である場合trueが返ってきます。
        licenseResult = _quomosdk.QuomoSetProductKey("e545491bd88ff5a5152f39327b283c2f04019245240a245c81c323cf033a80fbc3e4aec48af40561dbca2e7c9fb91568fdba6ed47a31251da09cce145b31fcb9");


        //リーダー用カメラ設定
        CameraView cameraView = (CameraView) findViewById(R.id.viewCamera);
        cameraView.setOnListener(this);
        cameraView._quomosdk = _quomosdk;
    }

    @TargetApi(23)
    private void permissionOfCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            // permissionが許可されていません
            if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            }
            // 許可ダイアログの表示
            requestPermissions(new String[]{Manifest.permission.CAMERA},
                    MainActivity.permission_camera);

            return;
        }
    }

    @TargetApi(23)
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MainActivity.permission_camera: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    start();
                } else {
                    Toast.makeText(this, "カメラ使用権限を許可してください", Toast.LENGTH_SHORT).show();
                }
                return;
            }
        }
    }

    public class Todo {
        private String title;
        private Boolean isDone;

        public Todo(String title, Boolean isDone) {
            this.title = title;
            this.isDone = isDone;
        }

        public String getTitle() {
            return title;
        }

        public Boolean isDone() {
            return isDone;
        }

        public void setDone() {
            this.isDone = true;
        }

        @Exclude
        public Map<String, Object> toMap() {
            HashMap<String, Object> hashmap = new HashMap<>();
            hashmap.put("title", title);
            hashmap.put("isDone", isDone);
            return hashmap;
        }
    }
}
