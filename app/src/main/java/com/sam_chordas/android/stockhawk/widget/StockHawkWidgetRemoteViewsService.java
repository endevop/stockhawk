package com.sam_chordas.android.stockhawk.widget;


import android.annotation.TargetApi;
import android.content.Intent;
import android.database.Cursor;
import android.os.Binder;
import android.os.Build;
import android.widget.AdapterView;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;

/* this is based on Sunshine widget code
* https://github.com/udacity/Advanced_Android_Development/
* */
@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class StockHawkWidgetRemoteViewsService extends RemoteViewsService {

    private static final String[] STOCK_COLUMNS = {
            QuoteColumns._ID,
            QuoteColumns.SYMBOL,
            QuoteColumns.BIDPRICE,
            QuoteColumns.PERCENT_CHANGE
    };
    // these indices must match the projection
    static final int COLUMN_ID              = 0;
    static final int COLUMN_SYMBOL          = 1;
    static final int COLUMN_BIDPRICE        = 2;
    static final int COLUMN_PERCENT_CHANGE  = 3;

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new RemoteViewsFactory() {
            private Cursor data = null;

            @Override
            public void onCreate() {
                // Nothing to do
            }

            @Override
            public void onDataSetChanged() {
                if (data != null) {
                    data.close();
                }
                // This method is called by the app hosting the widget (e.g., the launcher)
                // However, our ContentProvider is not exported so it doesn't have access to the
                // data. Therefore we need to clear (and finally restore) the calling identity so
                // that calls use our process and permission
                final long identityToken = Binder.clearCallingIdentity();

                data = getContentResolver().query(QuoteProvider.Quotes.CONTENT_URI,
                        new String[]{QuoteColumns._ID, QuoteColumns.SYMBOL, QuoteColumns.BIDPRICE,
                                QuoteColumns.PERCENT_CHANGE, QuoteColumns.CHANGE, QuoteColumns.ISUP},
                        QuoteColumns.ISCURRENT + " = ?",
                        new String[]{"1"},
                        null);
                Binder.restoreCallingIdentity(identityToken);
            }

            @Override
            public void onDestroy() {
                if (data != null) {
                    data.close();
                    data = null;
                }
            }

            @Override
            public int getCount() {
                return data == null ? 0 : data.getCount();
            }

            @Override
            public RemoteViews getViewAt(int position) {
                if (position == AdapterView.INVALID_POSITION ||
                        data == null || !data.moveToPosition(position)) {
                    return null;
                }
                RemoteViews views = new RemoteViews(getPackageName(),
                        R.layout.widget_my_stocks_list_item);

                String symbol = data.getString(COLUMN_SYMBOL);
                String price = data.getString(COLUMN_BIDPRICE);
                String change = data.getString(COLUMN_PERCENT_CHANGE);

                views.setTextViewText(R.id.widget_stock_symbol, symbol);
                views.setTextViewText(R.id.widget_bid_price, price);
                views.setTextViewText(R.id.widget_change, change);
                // make the widget colorful
                if(change.charAt(0) == '-')
                    views.setTextColor(R.id.widget_change, getResources().getColor(R.color.material_red_700));
                else
                    views.setTextColor(R.id.widget_change, getResources().getColor(R.color.material_green_700));

                final Intent fillInIntent = new Intent();
                fillInIntent.setDataAndType(QuoteProvider.Quotes.withSymbol(symbol), symbol);
                views.setOnClickFillInIntent(R.id.widget_stock_symbol, fillInIntent);
                return views;
            }

            @Override
            public RemoteViews getLoadingView() {
                return new RemoteViews(getPackageName(), R.layout.widget_my_stocks_list_item);
            }

            @Override
            public int getViewTypeCount() {
                return 1;
            }

            @Override
            public long getItemId(int position) {
                if (data.moveToPosition(position))
                    return data.getLong(COLUMN_ID);
                return position;
            }

            @Override
            public boolean hasStableIds() {
                return true;
            }
        };
    }
}
