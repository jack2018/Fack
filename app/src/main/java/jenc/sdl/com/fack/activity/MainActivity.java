package jenc.sdl.com.fack.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.iflytek.cloud.Setting;

import jenc.sdl.com.fack.R;
import jenc.sdl.com.fack.until.PermissionsUtils;

public class MainActivity extends Activity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        PermissionsUtils.checkPermissions(this, Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE);
        findViewById(R.id.btn_online_demo).setOnClickListener(MainActivity.this);
        findViewById(R.id.btn_offline_demo).setOnClickListener(MainActivity.this);
        findViewById(R.id.btn_video_demo).setOnClickListener(MainActivity.this);

        Setting.setShowLog(true);
    }

    @Override
    public void onClick(View v) {
        Intent intent = null;
        switch (v.getId()) {
            case R.id.btn_online_demo:
                intent = new Intent(MainActivity.this, OnlineFaceDemo.class);
                startActivity(intent);
                break;
            case R.id.btn_offline_demo:
                intent = new Intent(MainActivity.this, OfflineFaceDemo.class);
                startActivity(intent);
                break;
            case R.id.btn_video_demo:
                intent = new Intent(MainActivity.this, VideoDemo.class);
                startActivity(intent);
                break;
            default:
                break;
        }
    }
}
