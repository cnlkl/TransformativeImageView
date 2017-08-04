package cn.lkllkllkl.transformativeimageviewsample;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import cn.lkllkllkl.transformativeimageview.TransformativeImageView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TransformativeImageView transformativeImageView =
                (TransformativeImageView) findViewById(R.id.transformative_image_view);
        GlideApp.with(this)
                .load(R.drawable.cat)
                .into(transformativeImageView);
    }
}
