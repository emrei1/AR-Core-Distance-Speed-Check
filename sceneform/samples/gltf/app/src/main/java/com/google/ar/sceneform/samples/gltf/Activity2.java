package com.google.ar.sceneform.samples.gltf;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.io.File;
import java.util.ArrayList;

public class Activity2 extends AppCompatActivity {


    LinearLayout imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_2);

        imageView = findViewById(R.id.biglinear);

        LinearLayout linearLayout = findViewById(R.id.linear_bar);

        Bundle extras = getIntent().getExtras();

        if (extras != null) {
            ArrayList<String> fileNames = (ArrayList<String>) extras.get("filenames");

            for (int i = 0; i < fileNames.size(); i++) {
                String fileName = fileNames.get(i);

                File imgFile = new File(fileName);

                if (imgFile.exists()) {
                    Log.e("TAG", "added view ;;;;");
                    Bitmap bitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());

                    ImageView myImage = new ImageView(this);
                    myImage.setImageBitmap(bitmap);

                    linearLayout.addView(myImage, i);

                    myImage.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            imageView.addView(view);
                        }
                    });
                }
            }
        }

    }
}