package com.tophyr.coverflow;

import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;

public class CoverFlowDemo extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cover_flow_demo);
        
        CoverFlow cp = (CoverFlow)findViewById(R.id.coverflow);
        cp.setFocusable(true);
        cp.setFocusableInTouchMode(true);
        cp.requestFocus();
        
        final ArrayList<Integer> images = new ArrayList<Integer>();
        images.addAll(Arrays.asList(new Integer[] {
    		R.drawable.img0,
    		R.drawable.img1,
    		R.drawable.img2,
    		R.drawable.img3,
    		R.drawable.img4,
    		R.drawable.img5,
    		R.drawable.img6,
    		R.drawable.img7
        }));
        
        cp.setAdapter(new MutableAdapter<Integer>() {
        	@Override
        	public View getView(int position, View convert, ViewGroup parent) {
        		ImageView v;
        		if (convert instanceof ImageView)
        			v = (ImageView)convert;
        		else {
        			v = new ImageView(CoverFlowDemo.this);
        			v.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        			v.setScaleType(ScaleType.FIT_CENTER);
        			v.setBackgroundColor(Color.LTGRAY);
        		}
        		
        		v.setImageDrawable(getResources().getDrawable(getItem(position)));
        		
        		return v;
        	}

			@Override
			public int getCount() {
				return images.size();
			}

			@Override
			public Integer getItem(int position) {
				return images.get(position);
			}

			@Override
			public long getItemId(int position) {
				return position;
			}

			@Override
			public void onAdd(Integer added) { }

			@Override
			public void onRemove(int position) {
				images.remove(position);
			}

			@Override
			public void onInsert(Integer added, int position) {
				images.add(added, position);
			}

			@Override
			public void onReplace(Integer replacing, int position) { }

			@Override
			public void onSwap(int pos1, int pos2) { }
			
			@Override
			public void onMove(int oldPos, int newPos) {
				Integer i = getItem(oldPos);
				images.remove(oldPos);
				if (oldPos < newPos)
					images.add(i, newPos);
				else
					images.add(i, newPos - 1);
			}
        });
        
        cp.setPosition(3);
    }
}
