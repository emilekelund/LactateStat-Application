package com.example.lactatestat.utilities;

import android.content.Context;
import android.widget.TextView;

import com.example.lactatestat.R;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;

public class MyMarkerView extends MarkerView {

    private TextView tvContent;

    public MyMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);
        // this marker view only displays a textview
        tvContent = findViewById(R.id.tvContent);
    }

// callbacks everytime the MarkerView is redrawn, can be used to update the
// content (user-interface)

    @Override
    public void refreshContent(Entry e, Highlight highlight)
    {
        // here you can change whatever you want to show in following line as x/y or both
        tvContent.setText("x: " + Math.round(e.getX()) + "\ny: " + Math.round(e.getY())); // set the entry-value as the display text
    }

}