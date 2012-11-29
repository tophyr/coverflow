package com.tophyr.coverflow;

import com.tophyr.coverflow.R;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.ArrayAdapter;
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
        
        Integer[] images = new Integer[] {
    		R.drawable.img0,
    		R.drawable.img1,
    		R.drawable.img2,
    		R.drawable.img3,
    		R.drawable.img4,
    		R.drawable.img5,
    		R.drawable.img6,
    		R.drawable.img7
        };
        
        cp.setAdapter(new ArrayAdapter<Integer>(this, 0, images) {
        	@Override
        	public View getView(int position, View convert, ViewGroup parent) {
        		ImageView v;
        		if (convert instanceof ImageView)
        			v = (ImageView)convert;
        		else {
        			v = new ImageView(getContext());
        			v.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        			v.setScaleType(ScaleType.FIT_CENTER);
        			v.setBackgroundColor(Color.LTGRAY);
        		}
        		
        		v.setImageDrawable(getResources().getDrawable(getItem(position)));
        		
        		return v;
        	}
        });
    }
}
