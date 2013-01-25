package com.tophyr.coverflow;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.Vibrator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Adapter;

import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.Animator.AnimatorListener;
import com.nineoldandroids.animation.ValueAnimator;
import com.nineoldandroids.animation.ValueAnimator.AnimatorUpdateListener;

public class CoverFlow extends ViewGroup {
	
	private static enum TouchState {
		NONE,
		DOWN,
		SCROLLING,
		DRAGGING,
		SETTLING,
		DRAG_SETTLING,
		DRAG_SHIFTING;
		
		public float X;
	}
	
	private static class CoverFlowContainerView extends ViewGroup {
		private Camera m_Camera;
		private Matrix m_Transform;
		
		public CoverFlowContainerView(Context context, View item) {
			super(context);
			
			setWillNotDraw(false);
			
			m_Transform = new Matrix();
			m_Camera = new Camera();
			
			setView(item);
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
			// we want to be as wide as we are tall
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
					height = width;
					break;
				case MeasureSpec.AT_MOST:
					// fallthrough!
				case MeasureSpec.EXACTLY:
					// fallthrough!
				default:
					if (MeasureSpec.getSize(heightMeasureSpec) < width) {
						height = MeasureSpec.getSize(heightMeasureSpec);
						if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY)
							width = height;
					} else {
						height = width;
					}
					break;
			}
			
			setMeasuredDimension(width, height);
			
			if (getChildCount() != 0) {			
				final int sizeLimit = MeasureSpec.makeMeasureSpec((int)height, MeasureSpec.AT_MOST);
				getChildAt(0).measure(sizeLimit, sizeLimit);
			}
		}
		
		@Override
		protected void onLayout(boolean changed, int l, int t, int r, int b) {
			if (getChildCount() == 0)
				return;
			
			View v = getChildAt(0);
			
			v.layout((getMeasuredWidth() - v.getMeasuredWidth()) / 2, (getMeasuredHeight() - v.getMeasuredHeight()) / 2,
					 (getMeasuredWidth() + v.getMeasuredWidth()) / 2, (getMeasuredHeight() + v.getMeasuredHeight()) / 2);
		}
		
		public void setView(View item) {
			removeAllViews();
			addView(item);
		}
		
		public View getView() {
			if (getChildCount() == 0)
				return null;
			return getChildAt(0);
		}
		
		public void setRotationY(float rot) {
			m_Camera.save();
			m_Camera.rotateY(rot);
			m_Camera.getMatrix(m_Transform);
			m_Camera.restore();
			
			m_Transform.preTranslate(-getMeasuredWidth() / 2, -getMeasuredHeight() / 2);
			m_Transform.postTranslate(getMeasuredWidth() / 2, getMeasuredHeight() / 2);
			
			invalidate();
		}

		@Override
		public void draw(Canvas canvas) {
			canvas.save();
			canvas.concat(m_Transform);
			super.draw(canvas);
			canvas.restore();
		}
	}
	
	private static final int NUM_VIEWS_ON_SIDE = 3;
	
	private static final double HORIZ_MARGIN_FRACTION = 0.05;
	private static final double DRAG_SENSITIVITY_FACTOR = 2.5; // experimentally derived; lower numbers produce higher drag speeds

	private MutableAdapter<?> m_Adapter;
	private CoverFlowContainerView[] m_Views;
	private ArrayList<Queue<View>> m_RecycledViews;
	private int m_CurrentPosition;
	private View m_SelectedView;
	private int m_SelectedPosition;
	private double m_ScrollOffset;
	private double m_DragShiftOffset;
	private TouchState m_TouchState;
	private ValueAnimator m_Animator;
	
	private final DataSetObserver m_AdapterObserver;
	
	public CoverFlow(Context context) {
		this(context, null);
	}

	public CoverFlow(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		m_Views = new CoverFlowContainerView[1 + 2 * NUM_VIEWS_ON_SIDE];
		m_SelectedPosition = -1;
		
		m_AdapterObserver = new DataSetObserver() {
			@Override
			public void onChanged() {
				for (int i = 0; i < m_Views.length; i++)
					recycleView(i);
				loadAndOrderViews();
			}
			
			@Override
			public void onInvalidated() {
				
			}
		};
	}
	
	@Override
	protected void finalize() {
		if (m_Adapter != null)
			m_Adapter.unregisterDataSetObserver(m_AdapterObserver);
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
		if (m_TouchState != TouchState.DRAGGING && m_TouchState != TouchState.DRAG_SETTLING) {
			for (int i = 0; i < NUM_VIEWS_ON_SIDE; i++) {
				layoutView(i);
				layoutView(m_Views.length - i - 1);
			}
		}
		
		layoutView(NUM_VIEWS_ON_SIDE);
	}
	
	@SuppressLint("NewApi")
	private void layoutView(int viewIndex) {
		CoverFlowContainerView v = m_Views[viewIndex];
		if (v == null)
			return;
		
		double offset;
		if (m_TouchState == TouchState.DRAG_SHIFTING && viewIndex != NUM_VIEWS_ON_SIDE) {
			offset = m_DragShiftOffset;
			if (viewIndex == (NUM_VIEWS_ON_SIDE + (int)Math.signum(m_DragShiftOffset))) {
				offset *= 2;
			}
		}
		else {
			offset = m_ScrollOffset;
		}
		
		final int vWidth = v.getMeasuredWidth();
		final int vHeight = v.getMeasuredHeight();
		final int hMargin = (int)(getMeasuredWidth() * HORIZ_MARGIN_FRACTION);
		final int availWidth = getMeasuredWidth() - 2 * hMargin;
		final int totalHeight = getMeasuredHeight();
		
		double positionRatio = viewIndex / (m_Views.length - 1.0); // gives [0, 1] range
		positionRatio -= offset / availWidth;
		positionRatio = (positionRatio - .5) * 2; // transform to [-1, +1] range
		positionRatio = Math.signum(positionRatio) * Math.sqrt(Math.abs(positionRatio)); // "stretch" position away from center
		positionRatio = positionRatio / 2 + .5; // transform back to [0, 1] range
		v.setRotationY(90f * (1 - (float)positionRatio * 2));
		v.setVisibility((positionRatio > 0 && positionRatio < 1) ? View.VISIBLE : View.GONE);
		
		final int hCenterOffset = (int)(positionRatio * availWidth) + hMargin;
		v.layout(hCenterOffset - vWidth / 2, (totalHeight - vHeight) / 2, hCenterOffset + vWidth / 2, (totalHeight + vHeight) / 2);
	}
	
	private long m_NextDragSwitchAllowed;
	
	private void adjustScrollOffset(double delta) {
		Log.d("CFD", "Adjusting scroll offset by " + delta);
		if (delta == 0)
			return;
		
		m_ScrollOffset += delta;
		
		double crossover = (getMeasuredWidth() - 2 * getMeasuredWidth() * HORIZ_MARGIN_FRACTION) / ((m_Views.length - 1.0) * 2);
		if (m_TouchState == TouchState.SCROLLING) {
			if (m_ScrollOffset >= crossover) {
				int newPosition = m_CurrentPosition + (int)(m_ScrollOffset / crossover);
				if (newPosition >= m_Adapter.getCount()) {
					newPosition = m_Adapter.getCount() - 1;
					m_ScrollOffset = crossover;
				} else {
					m_ScrollOffset = m_ScrollOffset % crossover - crossover;	
				}
				
				setPosition(newPosition);
			} else if (m_ScrollOffset <= -crossover) {
				int newPosition = m_CurrentPosition + (int)(m_ScrollOffset / crossover);
				if (newPosition < 0) {
					newPosition = 0;
					m_ScrollOffset = -crossover;
				} else {
					m_ScrollOffset = m_ScrollOffset % crossover + crossover;
				}
				
				setPosition(newPosition);
			}
		} else if (m_TouchState == TouchState.DRAGGING || m_TouchState == TouchState.DRAG_SHIFTING) {
			if (m_ScrollOffset >= crossover) {
				m_ScrollOffset = crossover - 10;
				
				if (m_TouchState == TouchState.DRAGGING && m_CurrentPosition > 0) {
					shift(-1);
				}
			}
			if (m_ScrollOffset <= -crossover) {
				m_ScrollOffset = 10 - crossover;
				
				if (m_TouchState == TouchState.DRAGGING && m_CurrentPosition < m_Adapter.getCount() - 1) {
					shift(1);
				}
			}
		}
		
		requestLayout();
	}
	
	private void shift(final int shiftdir) {
//		if (SystemClock.uptimeMillis() < m_NextDragSwitchAllowed)
//			return;
//		m_NextDragSwitchAllowed = SystemClock.uptimeMillis() + 300;
//
//		m_TouchState = TouchState.DRAG_SHIFTING;
//		setPosition(m_CurrentPosition + shiftdir, true);

		m_TouchState = TouchState.DRAG_SHIFTING;
		Log.d("CFD", String.format("Shifting %d. Selected: %d, current: %d", shiftdir, m_SelectedPosition, m_CurrentPosition));
		final float shift = shiftdir * (getMeasuredWidth() - 2 * getMeasuredWidth() * (float)HORIZ_MARGIN_FRACTION) / ((m_Views.length - 1) * 2);
		
		if (m_Animator != null)
			m_Animator.cancel();
		m_Animator = ValueAnimator.ofFloat(0, shift);
		//m_Animator.setDuration(2000);
//		m_Animator.setInterpolator(new LinearInterpolator());
		
		final AnimatorUpdateListener aul = new AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator animation) {
				m_DragShiftOffset = (Float)animation.getAnimatedValue();
				requestLayout();
			}
		};
		m_Animator.addUpdateListener(aul);
		m_Animator.addListener(new AnimatorListener() {
			private boolean m_Canceled;
			private boolean m_2ndRun;
			
			@Override
			public void onAnimationStart(Animator animation) {}

			@Override
			public void onAnimationEnd(Animator animation) {
				Log.d("CoverFlow", "Shift animation ended. Canceled: " + m_Canceled + " 2ndRun: " + m_2ndRun);
				if (!m_Canceled && !m_2ndRun) {
					setPosition(m_CurrentPosition + shiftdir, true);
					m_2ndRun = true;
					
					m_Animator = ValueAnimator.ofFloat(-shift, 0);
					m_Animator.addUpdateListener(aul);
					m_Animator.addListener(this);
					m_Animator.start();
				} else {
					Log.d("CoverFlow", "Anim ended.");
					m_TouchState = TouchState.DRAGGING;
				}
			}

			@Override
			public void onAnimationCancel(Animator animation) {
				m_Canceled = true;
			}

			@Override
			public void onAnimationRepeat(Animator animation) {}
		});
		m_Animator.start();
	}
	
//	@Override
//	public View getSelectedView() {
//		return m_Selected ? m_Views[NUM_VIEWS_OFFSCREEN + NUM_VIEWS_ON_SIDE] : null;
//	}
//
//	@Override
//	public void setSelection(int position) {
//		if (position != m_CurrentPosition)
//			setPosition(position);
//		
//		m_Selected = true;
//	}
//	
//	@Override
	public void setAdapter(MutableAdapter<?> adapter) {
		if (m_Adapter != null)
			m_Adapter.unregisterDataSetObserver(m_AdapterObserver);
		
		m_Adapter = adapter;
		
		removeAllViewsInLayout();
		m_CurrentPosition = -1;
		
		if (m_Adapter == null) {
			m_RecycledViews = null; // TODO: introducing possible NPE's! code was written expecing this to never be null.
			m_Views = null; // TODO: possible NPE's !
			return;
		}
		
		m_RecycledViews = new ArrayList<Queue<View>>(m_Adapter.getViewTypeCount());
		for (int i = 0; i < m_Adapter.getViewTypeCount(); i++)
			m_RecycledViews.add(new LinkedList<View>());
		
		for (int i = 0; i < m_Views.length; i++)
			m_Views[i] = null;
		
		m_Adapter.registerDataSetObserver(m_AdapterObserver);
		
		if (m_Adapter.getCount() > 0)
			setPosition(0);
	}
	
	public Adapter getAdapter() {
		return m_Adapter;
	}
	
	private int getAdapterIndex(int viewIndex) {
		return m_CurrentPosition - NUM_VIEWS_ON_SIDE + viewIndex;
	}
	
	private void recycleView(int viewIndex) {
		CoverFlowContainerView v = m_Views[viewIndex];
		if (v != null) {
			m_Views[viewIndex] = null;
			removeView(v);
			final int adapterPosition = getAdapterIndex(viewIndex);
			if (adapterPosition >= 0 && adapterPosition < m_Adapter.getCount())
				m_RecycledViews.get(m_Adapter.getItemViewType(adapterPosition)).add(v.getView());
			v.removeAllViews();
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
		
		m_Views[viewIndex] = new CoverFlowContainerView(getContext(), newView); // TODO: completely defeats recycling
		addView(m_Views[viewIndex]);
	}
	
	private void shiftViews(int shift, boolean skipMiddle) {
		if (Math.abs(shift) >= m_Views.length) {
			// whole m_Views list is invalid
			for (int i = 0; i < m_Views.length; i++) {
				if (skipMiddle && i == NUM_VIEWS_ON_SIDE)
					continue;
				recycleView(i);
			}
		} else if (shift < 0) {
			// we want to scroll left, so we need to move items right
			for (int i = m_Views.length - 1; i >= 0; i--) {
				if (skipMiddle && i == NUM_VIEWS_ON_SIDE)
					continue;
				if (i - shift >= m_Views.length)
					recycleView(i);
				int newIndex = i + shift;
				if (skipMiddle && i > NUM_VIEWS_ON_SIDE && newIndex <= NUM_VIEWS_ON_SIDE)
					newIndex--;
				m_Views[i] = (newIndex < 0) ? null : m_Views[newIndex];
			}
		} else {
			// all other options exhausted, they must want to scroll right, so move items left
			for (int i = 0; i < m_Views.length; i++) {
				if (skipMiddle && i == NUM_VIEWS_ON_SIDE)
					continue;
				if (i - shift < 0)
					recycleView(i);
				int newIndex = i + shift;
				if (skipMiddle && i < NUM_VIEWS_ON_SIDE && newIndex >= NUM_VIEWS_ON_SIDE)
					newIndex++;
				m_Views[i] = (newIndex >= m_Views.length) ? null : m_Views[newIndex];
			}
		}
	}
	
	public void setPosition(int position) {
		setPosition(position, false);
	}
	
	private void setPosition(int position, boolean skipMiddle) {
		if (position < 0 || position >= m_Adapter.getCount())
			throw new IndexOutOfBoundsException("Cannot set position beyond bounds of adapter.");
		if (position == m_CurrentPosition)
			return;
		
		shiftViews(m_CurrentPosition == -1 ? Integer.MAX_VALUE : position - m_CurrentPosition, skipMiddle);
		
		m_SelectedView = null;
		m_CurrentPosition = position;
		
		loadAndOrderViews();
	}
	
	private void loadAndOrderViews() {
		for (int i = 0; i < m_Views.length; i++) {
			if (m_Views[i] == null)
				loadView(i);
		}
		
		for (int i = 0; i < NUM_VIEWS_ON_SIDE; i++) {
			
			if (m_Views[i] != null)
				m_Views[i].bringToFront();
			
			if (m_Views[m_Views.length - i - 1] != null)
				m_Views[m_Views.length - i - 1].bringToFront();
		}
		
		if (m_Views[NUM_VIEWS_ON_SIDE] != null)
			m_Views[NUM_VIEWS_ON_SIDE].bringToFront();
		
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
		if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
			adjustScrollOffset(1);
		}
		if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
			adjustScrollOffset(-1);
		}
		
		return super.onKeyDown(keyCode, event);
	}
	
	private static final int DRAG_DELAY = 500;
	private Timer m_TouchDownTimer;
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		Log.d("CFD", "TouchEvent: " + event.toString());
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN: {
				getParent().requestDisallowInterceptTouchEvent(true);
				
				m_TouchState = TouchState.DOWN;
				m_TouchState.X = event.getX();
				
				if (m_Animator != null)
					m_Animator.cancel();
				
				if (m_TouchDownTimer != null)
					Log.i("CoverFlow", "Received ACTION_DOWN but m_TouchDownTimer wasn't null.");
				m_TouchDownTimer = new Timer();
				m_TouchDownTimer.schedule(new TimerTask() {
					@Override
					public void run() {
						float x = m_TouchState.X;
						m_TouchState = TouchState.DRAGGING; // TODO: race condition possible
						m_TouchState.X = x;
						m_SelectedPosition = m_CurrentPosition;
						Log.d("CFD", "Selected position: " + m_SelectedPosition);
						((Vibrator)getContext().getSystemService(Context.VIBRATOR_SERVICE)).vibrate(200);
						m_TouchDownTimer = null;
					}
				}, DRAG_DELAY);
				
				break;
			}
			case MotionEvent.ACTION_MOVE: {
				if (m_TouchState == TouchState.DOWN) {
					if (Math.abs(m_TouchState.X - event.getX()) >= ViewConfiguration.get(getContext()).getScaledTouchSlop()) {
						if (m_TouchDownTimer != null) {
							m_TouchDownTimer.cancel();
							m_TouchDownTimer = null;
						}
						
						m_TouchState = TouchState.SCROLLING;
						m_TouchState.X = event.getX();
					}
				} else if (m_TouchState == TouchState.SCROLLING || m_TouchState == TouchState.DRAGGING || m_TouchState == TouchState.DRAG_SHIFTING) {
					float x = event.getX();
					adjustScrollOffset((m_TouchState.X - x) / DRAG_SENSITIVITY_FACTOR);
					m_TouchState.X = x;
				}
				else {
					Log.d("CoverPagerDemo", "Uhh, got an ACTION_MOVE but wasn't in DOWN, SCROLLING or DRAGGING.");
				}
				
				break;
			}
			case MotionEvent.ACTION_CANCEL: // fallthrough!
			case MotionEvent.ACTION_UP: {
				getParent().requestDisallowInterceptTouchEvent(false);
				
				if (m_TouchState == TouchState.DRAGGING)
					m_TouchState = TouchState.DRAG_SETTLING;
				else
					m_TouchState = TouchState.SETTLING;
				if (m_TouchDownTimer != null) {
					m_TouchDownTimer.cancel();
					m_TouchDownTimer = null;
				}
				
//				if (m_SelectedPosition != m_CurrentPosition && m_SelectedPosition != -1) {
//					Log.d("CoverFlow", "Swapping.");
//					m_Adapter.move(m_SelectedPosition, m_CurrentPosition);
//				}
//				m_SelectedPosition = -1;
				
				if (m_Animator != null)
					m_Animator.cancel();
				m_Animator = ValueAnimator.ofFloat((float)m_ScrollOffset, 0f);
				//m_Animator.setDuration(2000);
				m_Animator.addUpdateListener(new AnimatorUpdateListener() {
					@Override
					public void onAnimationUpdate(ValueAnimator animation) {
						float delta = (Float)animation.getAnimatedValue() - (float)m_ScrollOffset;
						adjustScrollOffset(delta);
					}
				});
				m_Animator.addListener(new AnimatorListener() {
					@Override
					public void onAnimationStart(Animator animation) {}

					@Override
					public void onAnimationEnd(Animator animation) {
						m_TouchState = TouchState.NONE;
						if (m_SelectedPosition != m_CurrentPosition && m_SelectedPosition != -1) {
							Log.d("CoverFlow", "Swapping " + m_SelectedPosition + " with " + m_CurrentPosition);
							m_Adapter.move(m_SelectedPosition, m_CurrentPosition);
						}
						m_SelectedPosition = -1;
					}

					@Override
					public void onAnimationCancel(Animator animation) {}

					@Override
					public void onAnimationRepeat(Animator animation) {}
				});
				m_Animator.start();
				
				break;
			}
			default:
				return super.onTouchEvent(event);
		}
		
		return true;
	}
}
