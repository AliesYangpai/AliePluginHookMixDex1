package org.alie.playchess;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.Toast;

public class LogoActivity extends BaseActivity implements OnClickListener {

    private ImageView iv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logo);
        initView();
        initListener();
    }

    private void initView() {
        iv = findViewById(R.id.iv);
    }

    private void initListener() {
        iv.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()){
            case R.id.iv:
                Toast.makeText(LogoActivity.this,"点击图片",Toast.LENGTH_SHORT).show();
                break;
        }
    }
}
