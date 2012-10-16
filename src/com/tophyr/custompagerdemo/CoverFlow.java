package com.tophyr.custompagerdemo;


import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;

public class CoverFlow extends AdapterView<Adapter> {
	
	private static final int NUM_VIEWS_ON_SIDE = 2;
	private static final int NUM_VIEWS_OFFSCREEN = 1;
	
	private Adapter m_Adapter;
	private View[] m_Views;
	private ArrayList<Queue<View>> m_RecycledViews;
	private int m_CurrentPosition;
	private boolean m_Selected;
	
	public CoverFlow(Context context) {
		this(context, null);
	}

	public CoverFlow(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		m_Views = new View[1 + 2 * NUM_VIEWS_ON_SIDE + 2 * NUM_VIEWS_OFFSCREEN];
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		// we want to be twice as wide as we are tall
		int width, height;
		switch (MeasureSpec.getMode(widthMeasureSpec)) {
			case MeasureSpec.UNSPECIFIED:
				width = Integer.MAX_VALUE;
				break;
			case MeasureSpec.AT_MOST:
				// fallthrough!
			case MeasureSpec.EXACTLY:
				// fallthrough!
			default:
				width = MeasureSpec.getSize(widthMeasureSpec);
				break;
		}
		
		switch (MeasureSpec.getMode(heightMeasureSpec)) {
			case MeasureSpec.UNSPECIFIED:
				height = width / 2;
				break;
			case MeasureSpec.AT_MOST:
				// fallthrough!
			case MeasureSpec.EXACTLY:
				// fallthrough!
			default:
				if (MeasureSpec.getSize(heightMeasureSpec) < width / 2) {
					height = MeasureSpec.getSize(heightMeasureSpec);
					if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY)
						width = height * 2;
				} else {
					height = width / 2;
				}
				break;
		}
		
		setMeasuredDimension(width, height);
		
		final int numChildren = getChildCount();
		final int sizeLimit = MeasureSpec.makeMeasureSpec((int)(height * .8), MeasureSpec.AT_MOST);
		for (int i = 0; i < numChildren; i++) {
			View v = getChildAt(i);
			v.measure(sizeLimit, sizeLimit);
		}
	}
 
	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
		final int myWidth = getMeasuredWidth();
		final int myHeight = getMeasuredHeight();
		
		int hOffset, vWidth, vHeight;
		View v;
		for (int i = 0; i < NUM_VIEWS_ON_SIDE; i++) {
			hOffset = i * myWidth / (NUM_VIEWS_ON_SIDE * 4 + 1) - (int)(myHeight * .8 / 4);
			
			v = m_Views[i + NUM_VIEWS_OFFSCREEN];
			if (v != null) {
				vWidth = v.getMeasuredWidth();
				vHeight = v.getMeasuredHeight();
				v.layout(hOffset, (myHeight - vHeight) / 2, hOffset + vWidth, (myHeight + vHeight) / 2);
			}
			
			v = m_Views[m_Views.length - (i + NUM_VIEWS_OFFSCREEN) - 1];
			if (v != null) {
				vWidth = v.getMeasuredWidth();
				vHeight = v.getMeasuredHeight();
				v.layout(myWidth - (vWidth + hOffset), (myHeight - vHeight) / 2, myWidth - hOffset, (myHeight + vHeight) / 2);
			}
		}
		
		v = m_Views[NUM_VIEWS_OFFSCREEN + NUM_VIEWS_ON_SIDE];
		if (v != null) {
			vWidth = v.getMeasuredWidth();
			vHeight = v.getMeasuredHeight();
			v.layout((myWidth - vWidth) / 2, (myHeight - vHeight) / 2, (myWidth + vWidth) / 2, (myHeight + vHeight) / 2);
		}
	}
	
	@Override
	public View getSelectedView() {
		return m_Selected ? m_Views[NUM_VIEWS_OFFSCREEN + NUM_VIEWS_ON_SIDE] : null;
	}

	@Override
	public void setSelection(int position) {
		if (position != m_CurrentPosition)
			setPosition(position);
		
		m_Selected = true;
	}
	
	public void setAdapter(Adapter adapter) {
		m_Adapter = adapter;
		m_RecycledViews = new ArrayList<Queue<View>>(m_Adapter.getViewTypeCount());
		for (int i = 0; i < m_Adapter.getViewTypeCount(); i++)
			m_RecycledViews.add(new LinkedList<View>());
		
		for (int i = 0; i < m_Views.length; i++)
			m_Views[i] = null;
		
		removeAllViewsInLayout();
		
		m_CurrentPosition = -1;
		setPosition(0);
	}
	
	public Adapter getAdapter() {
		return m_Adapter;
	}
	
	private int getAdapterIndex(int viewIndex) {
		return m_CurrentPosition - NUM_VIEWS_ON_SIDE - NUM_VIEWS_OFFSCREEN + viewIndex;
	}
	
	private void recycleView(int viewIndex) {
		View v = m_Views[viewIndex];
		if (v != null) {
			m_Views[viewIndex] = null;
			removeView(v);
			final int adapterPosition = getAdapterIndex(viewIndex);
			if (adapterPosition >= 0 && adapterPosition < m_Adapter.getCount())
				m_RecycledViews.get(m_Adapter.getItemViewType(adapterPosition)).add(v);
		}
	}
	
	private void loadView(int viewIndex) {
		final int position = getAdapterIndex(viewIndex);
		if (position < 0 || position >= m_Adapter.getCount())
			return;
		
		Queue<View> recycleQueue = m_RecycledViews.get(m_Adapter.getItemViewType(position));
		View recycled = recycleQueue.poll();
		View newView = m_Adapter.getView(position, recycled, this);
		
		if (recycled != null && recycled != newView)
			recycleQueue.add(recycled);
		
		m_Views[viewIndex] = newView;
	}
	
	public void setPosition(int position) {
		if (position < 0 || position >= m_Adapter.getCount())
			throw new IndexOutOfBoundsException("Cannot set position beyond bounds of adapter.");
		if (position == m_CurrentPosition)
			return;
		
		if (m_CurrentPosition == -1 || Math.abs(m_CurrentPosition - position) >= m_Views.length) {
			// whole m_Views list is invalid
			for (int i = 0; i < m_Views.length; i++) {
				recycleView(i);
			}
		} else if (position < m_CurrentPosition) {
			// we want to scroll left, so we need to move items right
			for (int i = m_Views.length - 1; i >= 0; i--) {
				if (i + position - m_CurrentPosition >= m_Views.length)
					recycleView(i);
				m_Views[i] = (i + position - m_CurrentPosition < 0) ? null : m_Views[i + position - m_CurrentPosition];
			}
		} else {
			// all other options exhausted, they must want to scroll right, so move items left
			for (int i = 0; i < m_Views.length; i++) {
				if (i - position + m_CurrentPosition < 0)
					recycleView(i);
				m_Views[i] = (i + position - m_CurrentPosition >= m_Views.length) ? null : m_Views[i + position - m_CurrentPosition];
			}
		}
		
		m_Selected = false;
		m_CurrentPosition = position;
		
		for (int i = 0; i < m_Views.length; i++) {
			if (m_Views[i] == null)
				loadView(i);
			
			if (m_Views[i] != null) {
				if (i >= NUM_VIEWS_OFFSCREEN && i < m_Views.length - NUM_VIEWS_OFFSCREEN && m_Views[i].getParent() != this)
					addView(m_Views[i]);
				else if ((i < NUM_VIEWS_OFFSCREEN || i >= m_Views.length - NUM_VIEWS_OFFSCREEN) && m_Views[i].getParent() == this)
					removeView(m_Views[i]);
			}
		}
		
		for (int i = 0; i < NUM_VIEWS_ON_SIDE; i++) {
			int degrees = 75 - i * (30 / NUM_VIEWS_ON_SIDE);
			
			if (m_Views[i + NUM_VIEWS_OFFSCREEN] != null) {
				m_Views[i + NUM_VIEWS_OFFSCREEN].bringToFront();
				m_Views[i + NUM_VIEWS_OFFSCREEN].setRotationY(degrees);
			}
			
			if (m_Views[m_Views.length - (i + NUM_VIEWS_OFFSCREEN) - 1] != null) {
				m_Views[m_Views.length - (i + NUM_VIEWS_OFFSCREEN) - 1].bringToFront();
				m_Views[m_Views.length - (i + NUM_VIEWS_OFFSCREEN) - 1].setRotationY(-degrees);
			}
		}
		
		if (m_Views[NUM_VIEWS_OFFSCREEN + NUM_VIEWS_ON_SIDE] != null) {
			m_Views[NUM_VIEWS_OFFSCREEN + NUM_VIEWS_ON_SIDE].bringToFront();
			m_Views[NUM_VIEWS_OFFSCREEN + NUM_VIEWS_ON_SIDE].setRotationY(0);
		}
		
		requestLayout();
	}
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && m_CurrentPosition > 0) {
			setPosition(m_CurrentPosition - 1);
			return true;
		}
		if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && m_CurrentPosition < m_Views.length) {
			setPosition(m_CurrentPosition + 1);
			return true;
		}
		
		return super.onKeyDown(keyCode, event);
	}
}
