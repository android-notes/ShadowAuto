package com.silentauto.controller;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

final class TaskPagerAdapter extends RecyclerView.Adapter<TaskPagerAdapter.TaskPageHolder> {
    private final List<AutomationTaskView> tasks;

    TaskPagerAdapter(List<AutomationTaskView> tasks) {
        this.tasks = tasks;
    }

    @Override
    public TaskPageHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.pager_holder, parent, false);
        return new TaskPageHolder(view);
    }

    @Override
    public void onBindViewHolder(TaskPageHolder holder, int position) {
        AutomationTaskView task = taskAt(position);
        ViewGroup oldParent = (ViewGroup) task.view().getParent();
        if (oldParent != null) {
            oldParent.removeView(task.view());
        }
        holder.frame.removeAllViews();
        holder.frame.addView(task.view(), new FrameLayout.LayoutParams(-1, -1));
    }

    @Override
    public int getItemCount() {
        synchronized (tasks) {
            return tasks.size();
        }
    }

    private AutomationTaskView taskAt(int position) {
        synchronized (tasks) {
            return new ArrayList<>(tasks).get(position);
        }
    }

    static final class TaskPageHolder extends RecyclerView.ViewHolder {
        final FrameLayout frame;

        TaskPageHolder(View view) {
            super(view);
            frame = view.findViewById(R.id.taskHolder);
        }
    }
}
