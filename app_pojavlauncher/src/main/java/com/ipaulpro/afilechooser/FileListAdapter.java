/*
 * Copyright (C) 2012 Paul Burke
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ipaulpro.afilechooser;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import git.artdeell.mojo.R;

/**
 * List adapter for Files.
 *
 * @version 2013-12-11
 * @author paulburke (ipaulpro)
 *
 * @addDate 2018-08-08
 * @addToMyProject khanhduy032
 */
public class FileListAdapter extends BaseAdapter {

    private static final int ICON_FOLDER = R.drawable.ic_px_folder;
    private static final int ICON_FILE = R.drawable.ic_px_file;

    private final LayoutInflater mInflater;

    private List<File> mData = new ArrayList<>();

    public FileListAdapter(Context context) {
        mInflater = LayoutInflater.from(context);
    }

    public void add(File file) {
        if (file == null) {
            return;
        }

        mData.add(file);
        notifyDataSetChanged();
    }

    public void remove(File file) {
        if (file == null) {
            return;
        }

        mData.remove(file);
        notifyDataSetChanged();
    }

    public void insert(File file, int index) {
        if (file == null) {
            return;
        }

        if (index < 0 || index > mData.size()) {
            throw new IndexOutOfBoundsException(
                    "Index: " + index + ", Size: " + mData.size()
            );
        }

        mData.add(index, file);
        notifyDataSetChanged();
    }

    public void clear() {
        mData.clear();
        notifyDataSetChanged();
    }

    @Override
    public File getItem(int position) {
        return mData.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getCount() {
        return mData.size();
    }

    public List<File> getListItems() {
        return mData;
    }

    /**
     * Set the list items without notifying on the clear.
     * This prevents unnecessary changes to the current scroll position.
     *
     * @param data list of files
     */
    public void setListItems(List<File> data) {
        if (data == null) {
            mData = new ArrayList<>();
        } else {
            mData = data;
        }

        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;

        if (row == null) {
            row = mInflater.inflate(
                    android.R.layout.simple_list_item_1,
                    parent,
                    false
            );
        }

        TextView view = (TextView) row;

        File file = getItem(position);

        if (file == null) {
            view.setText("");
            view.setCompoundDrawablesWithIntrinsicBounds(
                    ICON_FILE,
                    0,
                    0,
                    0
            );
            return row;
        }

        view.setText(file.getName());

        int icon = file.isDirectory() ? ICON_FOLDER : ICON_FILE;

        view.setCompoundDrawablesWithIntrinsicBounds(
                icon,
                0,
                0,
                0
        );

        // Use density-independent spacing instead of a fixed pixel value.
        int padding = (int) (
                8 * view.getResources()
                        .getDisplayMetrics()
                        .density
        );

        view.setCompoundDrawablePadding(padding);

        return row;
    }
}
