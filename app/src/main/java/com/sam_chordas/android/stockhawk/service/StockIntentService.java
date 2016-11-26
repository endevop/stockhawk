package com.sam_chordas.android.stockhawk.service;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.gcm.TaskParams;
import com.sam_chordas.android.stockhawk.R;

/**
 * Created by sam_chordas on 10/1/15.
 */
public class StockIntentService extends IntentService {

  public StockIntentService(){
    super(StockIntentService.class.getName());
  }

  public StockIntentService(String name) {
    super(name);
  }

  @Override protected void onHandleIntent(Intent intent) {
    StockTaskService stockTaskService = new StockTaskService(getApplicationContext());
    Bundle args = new Bundle();
    if (intent.getStringExtra(getString(R.string.intent_extra_tag))
            .equals(getString(R.string.intent_extra_add))){
      args.putString(getString(R.string.intent_extra_symbol),
              intent.getStringExtra(getString(R.string.intent_extra_symbol)));
    }
    // We can call OnRunTask from the intent service to force it to run immediately instead of
    // scheduling a task.
    stockTaskService.onRunTask(new TaskParams(intent.
            getStringExtra(getString(R.string.intent_extra_tag)), args));
  }
}
