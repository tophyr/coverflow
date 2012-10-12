package com.tophyr.custompagerdemo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

import android.content.Context;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

public class CustomPager extends ViewPager {
	
	private static class BasePagerAdapter extends PagerAdapter {

		private BaseAdapter m_Adapter;
		private ArrayList<Queue<View>> m_Ready;
		private HashMap<Object, View> m_Associations;
		
		public BasePagerAdapter(BaseAdapter adapter) {
			m_Adapter = adapter;
			
			final int viewTypeCount = adapter.getViewTypeCount();
			m_Ready = new ArrayList<Queue<View>>(viewTypeCount);
			for (int i = 0; i < viewTypeCount; i++)
				m_Ready.add(new LinkedList<View>());
			m_Associations = new HashMap<Object, View>();
		}
		
		@Override
		public int getCount() {
			return m_Adapter.getCount();
		}

		@Override
		public int getItemPosition(Object object) {
			return super.getItemPosition(object);
		}

		@Override
		public boolean isViewFromObject(View view, Object ob) {
			return view.equals(m_Associations.get(ob));
		}

		@Override
		public void startUpdate(ViewGroup container) {
			// TODO Auto-generated method stub
			super.startUpdate(container);
		}
		
		@Override
		public Object instantiateItem(ViewGroup container, int position) {
			Object ob = m_Adapter.getItem(position);
			
			int viewType = m_Adapter.getItemViewType(position);
			View convertView = m_Ready.get(viewType).poll();
			View newView = m_Adapter.getView(position, convertView, container);
			
			if (convertView != newView && convertView != null)
				m_Ready.get(viewType).add(convertView); // if we didn't use the convertView, add it back to the ready queue
			m_Associations.put(ob, newView);
			
			container.addView(newView);
			
			return ob;
		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			View v = m_Associations.remove(object);
			if (v != null) {
				m_Ready.get(m_Adapter.getItemViewType(position)).add(v);
				container.removeView(v);
			}
		}

		@Override
		public void finishUpdate(ViewGroup container) {
			super.finishUpdate(container);
		}

		@Override
		public void setPrimaryItem(ViewGroup container, int position, Object object) {
			// TODO Auto-generated method stub
			super.setPrimaryItem(container, position, object);
		}
	}

	public CustomPager(Context context) {
		super(context);
	}

	public CustomPager(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void setAdapter(BaseAdapter adapter) {
		setAdapter(new BasePagerAdapter(adapter));
	}
	
//	@Override
//	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
//		// we want to be twice as wide as we are tall
//		int width, height;
//		switch (MeasureSpec.getMode(widthMeasureSpec)) {
//			case MeasureSpec.UNSPECIFIED:
//				width = Integer.MAX_VALUE;
//				break;
//			case MeasureSpec.AT_MOST:
//				// fallthrough!
//			case MeasureSpec.EXACTLY:
//				// fallthrough!
//			default:
//				width = MeasureSpec.getSize(widthMeasureSpec);
//				break;
//		}
//		
//		switch (MeasureSpec.getMode(heightMeasureSpec)) {
//			case MeasureSpec.UNSPECIFIED:
//				height = width / 2;
//				break;
//			case MeasureSpec.AT_MOST:
//				// fallthrough!
//			case MeasureSpec.EXACTLY:
//				// fallthrough!
//			default:
//				if (MeasureSpec.getSize(heightMeasureSpec) < width / 2) {
//					height = MeasureSpec.getSize(heightMeasureSpec);
//					if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY)
//						width = height * 2;
//				} else {
//					height = width / 2;
//				}
//				break;
//		}
//		
//		setMeasuredDimension(width, height);
//	}
}
