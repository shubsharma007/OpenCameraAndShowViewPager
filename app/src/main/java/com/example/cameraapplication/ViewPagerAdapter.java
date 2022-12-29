package com.example.cameraapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.viewpager.widget.PagerAdapter;

import java.util.ArrayList;
import java.util.Objects;

public class ViewPagerAdapter extends PagerAdapter {

    //Context object
    Context context;
    //Array of image
    ArrayList<String> imagePaths = new ArrayList<>();
    //layout inflater
    LayoutInflater mLayoutInflater;

    public ViewPagerAdapter(Context context, ArrayList<String> imagePaths) {
        this.context = context;
        this.imagePaths = imagePaths;
        mLayoutInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);


    }

    @Override
    public int getCount() {
        //return the number of image
        return imagePaths.size();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        //inflation the item.xml
        View itemView = mLayoutInflater.inflate(R.layout.galleryitem, container, false);
        //referencing the imageview from the item.xml file
        ImageView imageView = itemView.findViewById(R.id.imageViewMain);
        //setting the image in the imageview
        Bitmap myBitMap = BitmapFactory.decodeFile(imagePaths.get(position));
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        Bitmap rotatedBoiMap = Bitmap.createBitmap(myBitMap, 0, 0, myBitMap.getWidth(), myBitMap.getHeight(), matrix, true);
        imageView.setImageBitmap(rotatedBoiMap);

        //Adding the View
        Objects.requireNonNull(container).addView(itemView);
        return itemView;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        container.removeView((LinearLayout)object);
    }
}
