package com.sam_chordas.android.stockhawk.ui;


import android.content.Intent;
import android.database.Cursor;
import android.graphics.Paint;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.db.chart.model.LineSet;
import com.db.chart.renderer.AxisRenderer;
import com.db.chart.view.ChartView;
import com.db.chart.view.LineChartView;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;
import com.sam_chordas.android.stockhawk.rest.Utils;

public class LineGraphActivity extends AppCompatActivity {
    private final String[] SELECT_COLUMNS = {
            QuoteColumns.BIDPRICE,
            QuoteColumns.CREATED
    };

    private final int COLUMN_BIDPRICE   = 0;
    private final int COLUMN_CREATED    = 1;

    private String mDates[];
    private float mValues[];
    private LineChartView mChart;
    private Button mDayButton;
    private Button mWeekButton;
    private Button mYearButton;
    private TextView mChartLabel;
    String mSymbol;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        mSymbol = intent.getStringExtra(getString(R.string.intent_extra_symbol));

        setContentView(R.layout.activity_line_graph);
        mChart = (LineChartView) findViewById(R.id.linechart);

        // setup buttons
        mDayButton = (Button) findViewById(R.id.day_button);
        mWeekButton = (Button) findViewById(R.id.week_button);
        mYearButton = (Button) findViewById(R.id.year_button);
        mChartLabel = (TextView) findViewById(R.id.chart_label);

        Cursor cursor = getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                SELECT_COLUMNS,
                QuoteColumns.SYMBOL + "= ? AND " + QuoteColumns.CREATED + " IS NOT NULL",
                new String[] {mSymbol},
                null);

        // Chart related
        mDates = new String[cursor.getCount()];
        mValues = new float[cursor.getCount()];

        if(cursor.moveToFirst()) {
            do {
                mDates[cursor.getPosition()] = cursor.getString(COLUMN_CREATED);
                mValues[cursor.getPosition()] = cursor.getFloat(COLUMN_BIDPRICE);
            } while (cursor.moveToNext());
            cursor.close();
        }

        updateChart(mWeekButton);
    }

    public void updateChart(View view) {
        LineSet lineSet;

        switch (view.getId()) {
            case R.id.day_button:
                lineSet = Utils.getDayLineSet(mDates, mValues);
                mChartLabel.setText(String.format(getString(R.string.hourly_changes), mSymbol));
                break;

            case R.id.week_button:
                lineSet = Utils.getWeekLineSet(mDates, mValues);
                mChartLabel.setText(String.format(getString(R.string.daily_changes), mSymbol));
                break;

            case R.id.year_button:
                lineSet = Utils.getYearLineSet(mDates, mValues);
                mChartLabel.setText(String.format(getString(R.string.monthly_changes), mSymbol));
                break;

            default:
                // unknown button
                return;
        }

        lineSet.setColor(getResources().getColor(R.color.material_blue_500));
        lineSet.setSmooth(true);
        mChart.reset();

        mChart.addData(lineSet);
        mChart.setContentDescription(getString(R.string.chart_bidprice_over_time));

        mChart.setYLabels(AxisRenderer.LabelPosition.OUTSIDE);
        mChart.setXLabels(AxisRenderer.LabelPosition.OUTSIDE);
        mChart.setLabelsColor(getResources().getColor(R.color.material_yellow_700));
        mChart.setAxisColor(getResources().getColor(R.color.material_red_700));

        Paint paint = new Paint();
        paint.setColor(getResources().getColor(R.color.material_red_700));
        mChart.setGrid(ChartView.GridType.FULL, lineSet.size(), lineSet.size(), paint);

        mChart.setAxisBorderValues(Utils.getChartMin(mValues), Utils.getChartMax(mValues),
                Utils.getChartStep(mValues));

        mChart.show();
    }
}
