package com.sam_chordas.android.stockhawk.rest;

import android.content.ContentProviderOperation;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.annotation.IntDef;
import android.util.Log;

import com.db.chart.model.LineSet;
import com.sam_chordas.android.stockhawk.R;
import com.sam_chordas.android.stockhawk.data.QuoteColumns;
import com.sam_chordas.android.stockhawk.data.QuoteProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Created by sam_chordas on 10/8/15.
 */
public class Utils {

  private static String LOG_TAG = Utils.class.getSimpleName();

  public static boolean showPercent = true;

  // date for the line chart
  private static String createdDate;

  // error codes
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({QUOTES_STATUS_OK, QUOTES_STATUS_NO_DATA, QUOTES_STATUS_SERVER_INVALID,
          QUOTES_STATUS_UNKNOWN, QUOTES_STATUS_BAD_QUOTES_URL, QUOTES_STATUS_SERVER_DOWN,
          QUOTES_STATUS_DB_ERROR})
  public @interface QuotesStatus {}

  public static final int QUOTES_STATUS_OK              = 0;
  public static final int QUOTES_STATUS_NO_DATA         = 1;
  public static final int QUOTES_STATUS_SERVER_INVALID  = 2;
  public static final int QUOTES_STATUS_UNKNOWN         = 3;
  public static final int QUOTES_STATUS_BAD_QUOTES_URL  = 4;
  public static final int QUOTES_STATUS_SERVER_DOWN     = 5;
  public static final int QUOTES_STATUS_DB_ERROR        = 6;

  public static ArrayList quoteJsonToContentVals(Context c, String JSON){
    ArrayList<ContentProviderOperation> batchOperations = new ArrayList<>();
    JSONObject jsonObject = null;
    JSONArray resultsArray = null;

    try{
      jsonObject = new JSONObject(JSON);
      if (jsonObject != null && jsonObject.length() != 0){
        jsonObject = jsonObject.getJSONObject("query");
        createdDate = jsonObject.getString("created");
        int count = Integer.parseInt(jsonObject.getString("count"));
        if (count == 1){
          jsonObject = jsonObject.getJSONObject("results")
              .getJSONObject("quote");
          ContentProviderOperation cpo = buildBatchOperation(c, jsonObject);
          if(cpo != null)
            batchOperations.add(cpo);
        } else{
          resultsArray = jsonObject.getJSONObject("results").getJSONArray("quote");

          if (resultsArray != null && resultsArray.length() != 0){
            for (int i = 0; i < resultsArray.length(); i++){
              jsonObject = resultsArray.getJSONObject(i);
              ContentProviderOperation cpo = buildBatchOperation(c, jsonObject);
              if(cpo != null)
                batchOperations.add(cpo);
            }
          }
        }
      }
    } catch (JSONException e){
      // server responded with invalid data format
      Log.e(LOG_TAG, "String to JSON failed: " + e);
      setQuotesStatus(c, QUOTES_STATUS_SERVER_INVALID);
    }
    return batchOperations;
  }

  public static String truncateBidPrice(String bidPrice){
    bidPrice = String.format("%.2f", Float.parseFloat(bidPrice));
    return bidPrice;
  }

  public static String truncateChange(String change, boolean isPercentChange){
    // check for 'null'
    if(change.equals("null"))
        return "+0.00";

    String weight = change.substring(0,1);
    String ampersand = "";
    if (isPercentChange){
      ampersand = change.substring(change.length() - 1, change.length());
      change = change.substring(0, change.length() - 1);
    }
    change = change.substring(1, change.length());
    double round = (double) Math.round(Double.parseDouble(change) * 100) / 100;
    change = String.format("%.2f", round);
    StringBuffer changeBuffer = new StringBuffer(change);
    changeBuffer.insert(0, weight);
    changeBuffer.append(ampersand);
    change = changeBuffer.toString();
    return change;
  }

  public static ContentProviderOperation buildBatchOperation(Context c, JSONObject jsonObject){
    ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(
        QuoteProvider.Quotes.CONTENT_URI);
    try {
      String symbol = jsonObject.getString("symbol");
      String bidPrice = jsonObject.getString("Bid");
      // check for 'null' value
      if(bidPrice.equals("null")) {
          setQuotesStatus(c, QUOTES_STATUS_NO_DATA);
          Log.e(LOG_TAG, "Bidprice for symbol " + symbol + " could not be retrieved.");
          return null;
      }
      String change = jsonObject.getString("Change");
      builder.withValue(QuoteColumns.SYMBOL, symbol);
      builder.withValue(QuoteColumns.BIDPRICE, truncateBidPrice(bidPrice));
      builder.withValue(QuoteColumns.CREATED, createdDate);
      builder.withValue(QuoteColumns.PERCENT_CHANGE, truncateChange(
          jsonObject.getString("ChangeinPercent"), true));
      builder.withValue(QuoteColumns.CHANGE, truncateChange(change, false));
      builder.withValue(QuoteColumns.ISCURRENT, 1);
      if (change.charAt(0) == '-'){
        builder.withValue(QuoteColumns.ISUP, 0);
      }else{
        builder.withValue(QuoteColumns.ISUP, 1);
      }
    } catch (JSONException e){
      // bad or invalid JSON format
      setQuotesStatus(c, QUOTES_STATUS_SERVER_INVALID);
      e.printStackTrace();
    }
    return builder.build();
  }

    public static int getChartMax(float[] values) {
        int max;
        int i;

        max = (int) values[0] + 1; // round up
        for(i=1; i<values.length; i++)
            if(max < values[i])
                max = (int) values[i] + 1; // round up

        return max;
    }

    public static int getChartMin(float[] values) {
        int min;
        int i;

        min = (int) values[0] - 1;
        for(i=1; i<values.length; i++)
            if(min > values[i])
                min = (int) values[i] - 1;

        if(min < 10)
            min = 0;

        return min;
    }

    public static int getChartStep(float[] values) {
        int step;
        int max = getChartMax(values);
        int min = getChartMin(values);
        int distance = max - min;

        // find axis step value
        step = distance / 10;
        while(step > 1) {
            if(distance % step == 0)
                break;
            step--;
        }

        // make sure it is > 0
        if(step == 0)
            step = 1;

        return step;
    }

    /* returns data set for up to 10 hours stored in the DB in one hour steps */
    public static LineSet getDayLineSet(String[] dates, float[] values) {
        LineSet lineSet;

        List<String> dayLabels = new ArrayList<>();
        List<Float> dayValues = new ArrayList<>();
        int numValues = values.length;
        int i;

        // dates are in format YYYY-MM-DDTHH:MM:00Z
        String lastKnownDate = dates[numValues - 1].substring(0, 10);
        String lastKnownHour = dates[numValues - 1].substring(11, 13);

        dayLabels.add(utcToLocalTZ(lastKnownHour) + "h");
        dayValues.add(values[numValues - 1]);
        // find all data points matching the last known date
        for(i=numValues - 1; i>=0; i--) {
            String date = dates[i];

            if(date.substring(0,10).equals(lastKnownDate)) {
                // use this data if it's older than last known hour
                String hour = date.substring(11,13);
                if(!hour.equals(lastKnownHour)) {
                    lastKnownHour = hour;
                    // convert the hour from UTC to local TZ
                    dayLabels.add(0, utcToLocalTZ(lastKnownHour) + "h");
                    dayValues.add(0, values[i]);
                }
            } else {
                // all done
                break;
            }

            // up to 10 data points
            if(dayLabels.size() == 10)
                break;
        }
        // convert lists to arrays
        float[] dayValuesArray = new float[dayValues.size()];
        i=0;
        for(Float f: dayValues) {
            dayValuesArray[i++] = f;
        }
        String[] dayLabelsArray = dayLabels.toArray(new String[0]);

        lineSet = new LineSet(dayLabelsArray, dayValuesArray);

        return lineSet;
    }

    /* returns data set for the last 7 days stored in the DB in one day steps */
    public static LineSet getWeekLineSet(String[] dates, float[] values, Context context) {
        LineSet lineSet;

        List<String> labels = new ArrayList<>();
        List<Float> dayValues = new ArrayList<>();
        int numValues = values.length;
        int i;

        // dates are in format YYYY-MM-DDTHH:MM:00Z
        String lastKnownDate = dates[numValues - 1].substring(0, 10);

        labels.add(formatDayAndMonthName(lastKnownDate, context));
        dayValues.add(values[numValues - 1]);
        // find more data points for different days
        for(i=numValues - 1; i>=0; i--) {
            String date = dates[i];

            // use this data if it's older than last known day
            if(!date.substring(0,10).equals(lastKnownDate)) {
                lastKnownDate = date.substring(0,10);

                labels.add(0, formatDayAndMonthName(lastKnownDate, context));
                dayValues.add(0, values[i]);
            }

            // up to 7 data points
            if(labels.size() == 7)
                break;
        }
        // convert lists to arrays
        float[] dayValuesArray = new float[dayValues.size()];
        i=0;
        for(Float f: dayValues) {
            dayValuesArray[i++] = f;
        }
        String[] dayLabelsArray = labels.toArray(new String[0]);

        lineSet = new LineSet(dayLabelsArray, dayValuesArray);

        return lineSet;
    }

    /* returns data set for the last 6 months stored in the DB in one month steps */
    public static LineSet getYearLineSet(String[] dates, float[] values, Context context) {
        LineSet lineSet;

        List<String> labels = new ArrayList<>();
        List<Float> monthValues = new ArrayList<>();
        int numValues = values.length;
        int i;

        // dates are in format YYYY-MM-DDTHH:MM:00Z
        String lastKnownMonth = dates[numValues - 1].substring(5, 7);
        labels.add(getMonthName(lastKnownMonth, context));
        monthValues.add(values[numValues - 1]);
        // find more data points for different months up to 12 months
        for(i=numValues - 1; i>=0; i--) {
            String date = dates[i];

            // use this data if it's older than last known month
            if(!date.substring(5, 7).equals(lastKnownMonth)) {
                lastKnownMonth = date.substring(5, 7);

                labels.add(0, getMonthName(lastKnownMonth, context));
                monthValues.add(0, values[i]);
            }

            // up to 6 data points
            if(labels.size() == 6)
                break;
        }
        // convert lists to arrays
        float[] dayValuesArray = new float[monthValues.size()];
        i=0;
        for(Float f: monthValues) {
            dayValuesArray[i++] = f;
        }
        String[] dayLabelsArray = labels.toArray(new String[0]);

        lineSet = new LineSet(dayLabelsArray, dayValuesArray);

        return lineSet;
    }

    public static String utcToLocalTZ(String hour) {
        Calendar c = Calendar.getInstance();
        TimeZone tz = c.getTimeZone();
        int hourTZ = Integer.parseInt(hour) + tz.getRawOffset()/3600000;
        return Integer.toString(hourTZ);
    }

    public static String getMonthName(String monthNumber, Context context) {
        if(monthNumber.equals("01"))
            return context.getString(R.string.january);
        else if(monthNumber.equals("02"))
            return context.getString(R.string.february);
        else if(monthNumber.equals("03"))
            return context.getString(R.string.march);
        else if(monthNumber.equals("04"))
            return context.getString(R.string.april);
        else if(monthNumber.equals("05"))
            return context.getString(R.string.may);
        else if(monthNumber.equals("06"))
            return context.getString(R.string.june);
        else if(monthNumber.equals("07"))
            return context.getString(R.string.july);
        else if(monthNumber.equals("08"))
            return context.getString(R.string.august);
        else if(monthNumber.equals("09"))
            return context.getString(R.string.september);
        else if(monthNumber.equals("10"))
            return context.getString(R.string.october);
        else if(monthNumber.equals("11"))
            return context.getString(R.string.november);
        else if(monthNumber.equals("12"))
            return context.getString(R.string.december);
        return null;
    }

    // converts YYYY-MM-DD to DD Month
    public static String formatDayAndMonthName(String date, Context context) {
        String day = date.substring(8);
        String monthName = getMonthName(date.substring(5, 7), context);

        return day + " " + monthName;
    }

    static public void setQuotesStatus(Context c, @QuotesStatus int quotesStatus){
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);
        SharedPreferences.Editor spe = sp.edit();
        spe.putInt(c.getString(R.string.pref_quotes_status_key), quotesStatus);
        Log.d("set status", "status: " + quotesStatus);
        spe.commit();
    }

    static public void resetQuotesStatus(Context c){
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);
        SharedPreferences.Editor spe = sp.edit();
        spe.putInt(c.getString(R.string.pref_quotes_status_key), QUOTES_STATUS_UNKNOWN);
        spe.commit();
    }

    @SuppressWarnings("ResourceType")
    static public @QuotesStatus int getQuoteStatus(Context c) {
        @QuotesStatus int status;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(c);
        status = sp.getInt(c.getString(R.string.pref_quotes_status_key), QUOTES_STATUS_UNKNOWN);

        return status;
    }
}
