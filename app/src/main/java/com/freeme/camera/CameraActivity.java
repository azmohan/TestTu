package com.freeme.camera;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.freeme.camera.tu.GLPreviewSurface;

import org.lasque.tusdk.core.seles.SelesParameters;
import org.lasque.tusdk.core.seles.sources.SelesOutInput;

import java.util.List;


public class CameraActivity extends Activity {
    private ListView mListView;
    private LayoutInflater mLayoutInflater;
    private GLPreviewSurface mGLPreviewSurface;
    private TextView mCaptureView;
    private static String[] mItems = {"smoothing", "mixed", "whitening", "eyeSize", "chinSize"};
    private SelesOutInput mFilter;

    private static class ViewHolder {
        public TextView mItemName;
        public SeekBar mSeekBar;
        public TextView mRate;
    }

    void setFilterArgs(String key, float value) {
        List<SelesParameters.FilterArg> args = mFilter.getParameter().getArgs();
        SelesParameters.FilterArg relatedArg = null;
        for (SelesParameters.FilterArg arg : args) {

            if (arg.equalsKey(key)) {
                relatedArg = arg;
                break;
            }
        }
        if (relatedArg != null) {
            relatedArg.setPrecentValue(value);
            mFilter.submitParameter();
        }

    }


    private class SeekBarChangedListener implements SeekBar.OnSeekBarChangeListener {
        private ViewHolder mHolder;
        private int mPosition;

        public SeekBarChangedListener(ViewHolder holder, int position) {
            mHolder = holder;
            mPosition = position;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            float value = i / 100.0f;
            setFilterArgs((String) mBaseAdapter.getItem(mPosition), value);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }

    }

    private GLPreviewSurface.FilerChangeListener mListener = new GLPreviewSurface.FilerChangeListener() {
        @Override
        public void onFilerChanged(SelesOutInput filter) {
            mFilter = filter;
            mListView.setVisibility(View.VISIBLE);
            mBaseAdapter.notifyDataSetChanged();
        }
    };


    private BaseAdapter mBaseAdapter = new BaseAdapter() {
        @Override
        public int getCount() {
            return mItems.length;
        }

        @Override
        public Object getItem(int i) {
            return mItems[i];
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder holder = null;
            if (view == null) {
                view = mLayoutInflater.inflate(R.layout.filter_adjust_item, null);
                holder = new ViewHolder();
                holder.mItemName = (TextView) view.findViewById(R.id.name);
                holder.mSeekBar = (SeekBar) view.findViewById(R.id.adjust_bar);
                holder.mRate = (TextView) view.findViewById(R.id.rate);
                holder.mSeekBar.setOnSeekBarChangeListener(new SeekBarChangedListener(holder, i));
                view.setTag(holder);
            } else {
                holder = (ViewHolder) view.getTag();
            }

            String name = (String) getItem(i);
            holder.mItemName.setText(name);
            return view;
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_main_layout);
        mListView = (ListView) findViewById(R.id.item_list);
        mListView.setAdapter(mBaseAdapter);
        mListView.setVisibility(View.GONE);
        mGLPreviewSurface = (GLPreviewSurface) findViewById(R.id.preview);
        mGLPreviewSurface.setFilterChangeListener(mListener);
        mLayoutInflater = LayoutInflater.from(this);
        mCaptureView = (TextView) findViewById(R.id.capture);
        mCaptureView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mGLPreviewSurface.takePicture();
            }
        });
    }
}
