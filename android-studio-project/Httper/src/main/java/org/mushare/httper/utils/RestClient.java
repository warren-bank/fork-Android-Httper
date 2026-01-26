package org.mushare.httper.utils;

import java.io.IOException;
import java.util.List;
import java.security.Security;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.internal.Util;
import org.conscrypt.Conscrypt;

import static org.mushare.httper.utils.HttpUtils.phaseHeaders;

/**
 * Created by dklap on 5/3/2017.
 */

public class RestClient {
    static{
        Security.insertProviderAt(Conscrypt.newProvider(), 1);
    }
    private static OkHttpClient client = new OkHttpClient();

    public static final Callback defaultCallback = new Callback() {
        @Override
        public void onResponse(Call call, Response response) {
            RestClient.cancel(call);
        }

        @Override
        public void onFailure(Call call, IOException e) {
            RestClient.cancel(call);
        }
    };

    public static Call get(String url, List<MyPair> headers, Callback callback) {
        Request request = new Request.Builder().url(url).headers(phaseHeaders(headers)).get()
                .build();
        Call call = client.newCall(request);
        call.enqueue((callback != null) ? callback : RestClient.defaultCallback);
        return call;
    }

    public static Call post(String url, List<MyPair> headers, String body, Callback callback) {
        Request request = new Request.Builder().url(url).headers(phaseHeaders(headers)).post
                (body == null ? Util.EMPTY_REQUEST : RequestBody.create(null, body)).build();
        Call call = client.newCall(request);
        call.enqueue((callback != null) ? callback : RestClient.defaultCallback);
        return call;
    }

    public static Call head(String url, List<MyPair> headers, Callback callback) {
        Request request = new Request.Builder().url(url).headers(phaseHeaders(headers)).head()
                .build();
        Call call = client.newCall(request);
        call.enqueue((callback != null) ? callback : RestClient.defaultCallback);
        return call;
    }

    public static Call put(String url, List<MyPair> headers, String body, Callback callback) {
        Request request = new Request.Builder().url(url).headers(phaseHeaders(headers)).put
                (body == null ? Util.EMPTY_REQUEST : RequestBody.create(null, body)).build();
        Call call = client.newCall(request);
        call.enqueue((callback != null) ? callback : RestClient.defaultCallback);
        return call;
    }

    public static Call delete(String url, List<MyPair> headers, String body, Callback callback) {
        Request request = new Request.Builder().url(url).headers(phaseHeaders(headers)).delete
                (body == null ? null : RequestBody.create(null, body)).build();
        Call call = client.newCall(request);
        call.enqueue((callback != null) ? callback : RestClient.defaultCallback);
        return call;
    }

    public static Call patch(String url, List<MyPair> headers, String body, Callback callback) {
        Request request = new Request.Builder().url(url).headers(phaseHeaders(headers)).patch
                (body == null ? Util.EMPTY_REQUEST : RequestBody.create(null, body)).build();
        Call call = client.newCall(request);
        call.enqueue((callback != null) ? callback : RestClient.defaultCallback);
        return call;
    }

    public static boolean cancel(Call call) {
        boolean should_cancel = (call != null) && call.isExecuted() && !call.isCanceled();
        if (should_cancel)
            call.cancel();
        return should_cancel;
    }

}
