# TransformativeImageView

A custom ImageView that can rotate, pan, and scale the image

[introduction](http://www.jianshu.com/p/938ca88fb16a)

## Screenshot
![screenshot](https://raw.githubusercontent.com/cnlkl/TransformativeImageView/master/screenshot/transmative_image_view_screenshot.gif)

## Add dependency
### **Step 1.** Add the JitPack repository to your build file
### Add it in your root build.gradle at the end of repositories:   

```gradle
allprojects {
	repositories {
		...
		maven { url 'https://jitpack.io' }
	}
}
```

### **Step 2.** Add the dependency
```gradle
dependencies {
	compile 'com.github.cnlkl:TransformativeImageView:1.1.2'
}
```

## How to use
### Activity
```java
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TransformativeImageView transformativeImageView1 =
                (TransformativeImageView) findViewById(R.id.multi_touch_view1);
		// Use glid to load image
        GlideApp.with(this)
                .load(R.drawable.cat)
                .into(transformativeImageView1);
    }
}
```

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    tools:context="cn.lkllkllkl.transformativeimageviewsample.MainActivity">

    <cn.lkllkllkl.transformativeimageview.TransformativeImageView
        android:background="@color/gray"
        android:id="@+id/transformative_image_view"
        android:layout_gravity="center"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:open_rotate_revert="true"
        app:open_scale_revert="true"
        app:open_translate_revert="true"
        app:revert_duration="300"
        app:max_scale="4"
        app:min_scale="1"
        app:scale_center="finger_center"
        />

</FrameLayout>
```
