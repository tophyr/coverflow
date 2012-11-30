package com.tophyr.coverflow;

import android.widget.BaseAdapter;

public abstract class MutableAdapter<T> extends BaseAdapter {

	public abstract void onAdd(T added);
	public final void add(T added) {
		onAdd(added);
		notifyDataSetChanged();
	}
	
	public abstract void onRemove(int position);
	public final void remove(int position) {
		onRemove(position);
		notifyDataSetChanged();
	}
	
	public abstract void onInsert(T added, int position);
	public final void insert(T added, int position) {
		onInsert(added, position);
		notifyDataSetChanged();
	}
	
	public abstract void onReplace(T replacing, int position);
	public final void replace(T replacing, int position) {
		onReplace(replacing, position);
		notifyDataSetChanged();
	}
	
	public abstract void onSwap(int pos1, int pos2);
	public final void swap(int pos1, int pos2) {
		onSwap(pos1, pos2);
		notifyDataSetChanged();
	}
}
