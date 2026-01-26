package org.mushare.httper;

import org.json.JSONArray;
import org.json.JSONException;
import org.mushare.httper.entity.DaoSession;
import org.mushare.httper.entity.RequestRecord;
import org.mushare.httper.entity.RequestRecordDao;
import org.mushare.httper.utils.HttpUtils;
import org.mushare.httper.utils.MyApp;
import org.mushare.httper.utils.MyPair;
import org.mushare.httper.utils.RestClient;
import org.mushare.httper.utils.WakeLockMgr;

import okhttp3.Call;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.HashMap;
import java.util.List;

public class PeriodicRequestsService extends Service {
  public  static final String ACTION_START = "START";
  private static final String ACTION_STOP  = "STOP";
  private static final int NOTIFICATION_ID_NETWORKING_SERVICE = 1;
  private static final int MINIMUM_SECONDS_INTERVAL = 10;
  private static final HashMap<Long, Call> requestsMap = new HashMap<Long, Call>();

  private static boolean isRunning = true;

  private RequestRecordDao requestRecordDao;

  @Override
  public void onCreate() {
    super.onCreate();

    WakeLockMgr.acquire(/* context= */ PeriodicRequestsService.this);
    showNotification();

    DaoSession daoSession = ((MyApp) getApplication()).getDaoSession();
    this.requestRecordDao = daoSession.getRequestRecordDao();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    onStart(intent, startId);
    return START_STICKY;
  }

  @Override
  public void onStart(Intent intent, int startId) {
    processIntent(intent);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    shutdown();
  }

  private void shutdown() {
    isRunning = false;

    WakeLockMgr.release();
    hideNotification();

    for (Call call : requestsMap.values()) {
      RestClient.cancel(call);
    }
    requestsMap.clear();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  // -------------------------------------------------------------------------
  // foregrounding..

  private String getNotificationChannelId() {
    return getPackageName();
  }

  private void createNotificationChannel() {
    if (Build.VERSION.SDK_INT >= 26) {
      String channelId       = getNotificationChannelId();
      NotificationManager NM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      NotificationChannel NC = new NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_HIGH);

      NC.setDescription(channelId);
      NC.setSound(null, null);
      NM.createNotificationChannel(NC);
    }
  }

  private int getNotificationId() {
    return NOTIFICATION_ID_NETWORKING_SERVICE;
  }

  private void showNotification() {
    Notification notification = getNotification();
    int NOTIFICATION_ID = getNotificationId();

    if (Build.VERSION.SDK_INT >= 5) {
      createNotificationChannel();
      startForeground(NOTIFICATION_ID, notification);
    }
    else {
      NotificationManager NM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      NM.notify(NOTIFICATION_ID, notification);
    }
  }

  private void hideNotification() {
    if (Build.VERSION.SDK_INT >= 5) {
      stopForeground(true);
    }
    else {
      NotificationManager NM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      int NOTIFICATION_ID    = getNotificationId();
      NM.cancel(NOTIFICATION_ID);
    }
  }

  private Notification getNotification() {
    Notification notification;

    if (Build.VERSION.SDK_INT >= 26) {
      Notification.Builder builder = new Notification.Builder(/* context= */ PeriodicRequestsService.this, /* channelId= */ getNotificationChannelId());

      if (Build.VERSION.SDK_INT >= 31) {
        builder.setContentTitle(getString(R.string.notification_service_content_line2));
        builder.setContentText (getString(R.string.notification_service_content_line3));
        builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
      }

      notification = builder.build();
    }
    else {
      notification = new Notification();
    }

    notification.when          = System.currentTimeMillis();
    notification.flags         = 0;
    notification.flags        |= Notification.FLAG_ONGOING_EVENT;
    notification.flags        |= Notification.FLAG_NO_CLEAR;
    notification.icon          = R.mipmap.ic_launcher;
    notification.tickerText    = getString(R.string.notification_service_ticker);
    notification.contentIntent = getPendingIntent_StopService();
    notification.deleteIntent  = getPendingIntent_StopService();

    if (Build.VERSION.SDK_INT >= 16) {
      notification.priority    = Notification.PRIORITY_HIGH;
    }
    else {
      notification.flags      |= Notification.FLAG_HIGH_PRIORITY;
    }

    if (Build.VERSION.SDK_INT >= 21) {
      notification.visibility  = Notification.VISIBILITY_PUBLIC;
    }

    RemoteViews contentView    = new RemoteViews(getPackageName(), R.layout.service_notification);
    contentView.setImageViewResource(R.id.notification_icon, R.mipmap.ic_launcher);
    contentView.setTextViewText(R.id.notification_text_line1, getString(R.string.notification_service_content_line1));
    contentView.setTextViewText(R.id.notification_text_line2, getString(R.string.notification_service_content_line2));
    contentView.setTextViewText(R.id.notification_text_line3, getString(R.string.notification_service_content_line3));

    if (Build.VERSION.SDK_INT < 31)
      notification.contentView = contentView;
    if (Build.VERSION.SDK_INT >= 16)
      notification.bigContentView = contentView;
    if (Build.VERSION.SDK_INT >= 21)
      notification.headsUpContentView = contentView;

    return notification;
  }

  private PendingIntent getPendingIntent_StopService() {
    Intent intent = new Intent(PeriodicRequestsService.this, PeriodicRequestsService.class);
    intent.setAction(ACTION_STOP);

    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
    if (Build.VERSION.SDK_INT >= 23)
      flags |= PendingIntent.FLAG_IMMUTABLE;

    return PendingIntent.getService(PeriodicRequestsService.this, 0, intent, flags);
  }

  // -------------------------------------------------------------------------
  // process inbound intents

  private void processIntent(Intent intent) {
    if (intent == null) return;

    String action = intent.getAction();
    if (action == null) return;

    switch (action) {
      case ACTION_STOP : {
        stopSelf();
        break;
      }
      case ACTION_START : {
        long requestRecordId = intent.getLongExtra("requestRecordId", -1L);
        int  interval        = intent.getIntExtra("interval_seconds", -1);

        if ((requestRecordId != -1L) && (interval != -1)) {
          if (interval < MINIMUM_SECONDS_INTERVAL) {
            interval = MINIMUM_SECONDS_INTERVAL;
          }

          RequestRecord requestRecord = requestRecordDao.queryBuilder().where(RequestRecordDao.Properties.Id.eq(requestRecordId)).build().unique();
          if (requestRecord != null) {
            String method = requestRecord.getMethod();

            String url = requestRecord.getHttp() + requestRecord.getUrl();
            try {
              List<MyPair> params = HttpUtils.jsonArrayToPairList(new JSONArray(requestRecord.getParameters()));
              url = HttpUtils.combineUrl(url, params);
            }
            catch(JSONException e) {}

            List<MyPair> headers = null;
            try {
              headers = HttpUtils.jsonArrayToPairList(new JSONArray(requestRecord.getHeaders()));
            }
            catch(JSONException e) {}

            String body = requestRecord.getBody();

            new PeriodicRequestThread(requestRecordId, interval, method, url, headers, body).start();
          }
        }
        break;
      }
    }
  }

  // -------------------------------------------------------------------------
  // initiate periodic network request

  private class PeriodicRequestThread extends Thread {
    private Long requestsMapKey;
    private long interval_ms;
    private String method;
    private String url;
    private List<MyPair> headers;
    private String body;

    public PeriodicRequestThread(long requestRecordId, int interval, String method, String url, List<MyPair> headers, String body) {
      this.requestsMapKey = Long.valueOf(requestRecordId);
      this.interval_ms    = interval * 1000L;
      this.method         = method;
      this.url            = url;
      this.headers        = headers;
      this.body           = body;
    }

    public void run() {
      Call call;

      while (PeriodicRequestsService.isRunning && !Thread.interrupted()) {
        try {
          call = requestsMap.get(requestsMapKey);
          RestClient.cancel(call);

          call = null;
          switch (method) {
              case "GET":
                  call = RestClient.get(url, headers, /* callback= */ null);
                  break;
              case "POST":
                  call = RestClient.post(url, headers, body, /* callback= */ null);
                  break;
              case "HEAD":
                  call = RestClient.head(url, headers, /* callback= */ null);
                  break;
              case "PUT":
                  call = RestClient.put(url, headers, body, /* callback= */ null);
                  break;
              case "DELETE":
                  call = RestClient.delete(url, headers, body, /* callback= */ null);
                  break;
              case "PATCH":
                  call = RestClient.patch(url, headers, body, /* callback= */ null);
                  break;
          }

          if (call != null)
            requestsMap.put(requestsMapKey, call);
        }
        catch(Exception e) {}

        try {
          Thread.sleep(interval_ms);
        }
        catch(InterruptedException e) {}
      }
    }
  }

}
